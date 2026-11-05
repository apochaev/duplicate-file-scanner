# Local web UI

Status: design settled 2026-10-31 (interview), not yet ticketed. A browser UI served by a local server, for exploring a scan, building a deletion plan and double-checking it before anything is deleted. Decisions are numbered U1–U11 and D1–D7 so tickets can cite them.

## Goals

- See where duplicates are, organised by folder, and how far folders overlap.
- See everything in a folder, including files that are not duplicates and are not in the scan result.
- Build a deletion plan in bulk (by folder) with precise exceptions, and double-check it thoroughly before acting.
- Later: execute the plan (quarantine), undo, purge, move and consolidate.

## Decisions

### U1. The first version: explorer plus preview

Explore overlaps, duplicate sets and folders; build a deletion plan; preview and verify it. No executing yet. Execute, undo and purge come in the next version. The preview screen is where the Execute button will go, so nothing is redesigned when it arrives.

### U2. Scans start from the UI

A start screen takes folders and settings, shows live progress from the scanner's `ScanEvent`s, then opens the result. Goes through the core ports: `StartScan` → `GetScanStatus`/`GetScanResult` → `PreviewDeletionPlan` on that scan ID. Results live in memory (the in-memory `ScanRepository`) and are lost on restart. Saving and reopening scans (the JSONL export as storage format) is a later ticket behind `ScanRepository`, with no UI redesign. The API (DFS-040): `POST /api/scans` starts a scan, `GET /api/scans` lists this server run's scans, `GET /api/scans/{id}` and `POST /api/scans/{id}/cancel` read and cancel one, and `GET /api/scans/{id}/events` streams a throttled `ScanProgress` (Server-Sent Events, at most every 250 ms) until the scan ends. The screen (DFS-041) is one page without a router: folders typed as absolute paths and kept as chips, the minimum size and the default exclusion in a Settings panel, Start; below, this server run's scans newest first, a running one with live progress (phase, counts, a hashing bar) and Cancel, a finished one with its totals or failure. Its stream is read with the browser's `EventSource`, the one call outside the generated client.

### U3. Navigation: a folder tree plus Overlaps and Sets tabs

- Left: a lazily loaded folder tree of the scanned roots, one level per call to `GET /api/scans/{id}/folders?parent=` (DFS-047).
- Right: tabs, both filtered to the folder selected in the tree:
  - **Overlaps** (landing tab): folder summaries and folder pairs ranked by reclaimable bytes, paged from `GET /api/scans/{id}/summaries?under=`, `GET /api/scans/{id}/pairs?under=` and, for two chosen folders, `GET /api/scans/{id}/pair?a=&b=` (DFS-048).
  - **Sets**: duplicate sets, largest reclaimable first (catches big single files between unrelated folders), paged from `GET /api/scans/{id}/sets?under=` (DFS-047).
- **As built (DFS-049, M-1.4):** hash routes, `#/scans/<id>` is the result screen (an "Open results" link on every COMPLETED scan row). The tree shows "All folders" and the roots as "<name> · <size> · <n> files" in API order, children on first expand; selecting a folder filters the Sets tab (`under=`), "All folders" clears it. The Sets tab has one card per set ("<n> copies · <size> each · <reclaimable> reclaimable"), hard links indented under their copy, links under the selected folder marked, a paginator (25, 50, 100) and a "Copy folder path" action per link. The Overlaps tab is DFS-050.
- Charts (overlap matrix, treemap) are a later addition to the Overlaps tab, not part of the first version.

### U4. Folder detail view: listed live from disk

Opening a folder lists its current contents one level deep, through a new read-only core query (`ListDirectory`, built on the `FileTreeWalker` port's rules: regular files only, no following links). Each entry is matched against the scan and marked:

| Mark | Meaning |
|---|---|
| duplicate | in a `DuplicateSet`, with its other copies |
| unique | on disk, not in any set |
| skipped | skipped by the scan, with the `SkipReason` |
| excluded | left out by name (`.DS_Store`, `._*`) |
| new since scan | on disk, not seen by the scan |
| missing since scan | in the scan, no longer on disk |

A "unique only" filter answers "what would I lose if this folder went?". Unique files show name, size and date only; nothing is hashed on demand.

### U5. Plan operations: quarantine only, extensible

A plan is a list of typed operations; the first version has only `Quarantine`. `Move` and a `Consolidate` macro ("merge B into A": quarantine duplicates, move unique files, remove empty folders) can be added later without reshaping the model. The preview lists the unique files a plan leaves behind.

### U5b. Overlap analysis runs in the `server` subproject, in Java

The scanner does not compute folder overlaps (CLAUDE.md decision 1). The server does, in its own analysis package `us.pochaev.dfs.server.analysis` (entry point `OverlapAnalysis.of(ScanResult)`, DFS-046), implementing `docs/design/folder-overlap-analysis.md` (folder summaries rolled up to the roots, pairs between immediate folders, a query for any two folders, hard links never count). Inputs: `ScanResult` sets, roots and per-folder totals. JUnit cases are taken from that document's examples so the Java, SQL (`tools/analysis/overlaps.sql`) and JavaScript (`tools/viewer/analysis.js`) implementations stay in agreement. The server answers small paged queries (summary for a folder, pairs under a folder, pair for two chosen folders) rather than sending the whole result. The SQL and the standalone viewer remain as standalone tools.

### U6. Building a plan: rules, exceptions, single-set picks

- **Rules** do the bulk work: from a pair or folder, "keep A, quarantine in B everything that has a copy in A" (recursive).
- **Exceptions** exclude files or folders from a rule ("keep this one"), from the preview or folder view.
- **Single-set picks** choose the keeper for one set, from the Sets tab.

Every preview entry shows which rule or pick produced it. DFS-022 validates the plan as a whole (never the last copy). A keep-priority list ("C:/Photos > E:/Archive > D:/backup") may come later as a shortcut that generates rules.

### U7. Preview and double-check

1. **Plan check** (DFS-022, from the scan result): totals (files, bytes, sets touched), entries grouped by rule with their keeper, files left behind, and last-copy conflicts, which block confirming.
2. **Drift check**, automatic on opening the preview: re-read size and modification time of every candidate and keeper; mark *changed*, *missing* or *new conflict* (e.g. a keeper deleted since the scan) and exclude those entries until rescanned. Reads only metadata.
3. **Content verify**, on request per file, per rule or for the whole plan: the same byte-for-byte compare against the keeper that execution runs (`ContentComparator`, CLAUDE.md decision 4), read-only, with progress. Entries show *verified at <time>* or *differs*.

A previewed plan is **frozen**: it gets an ID, and executing it (later) runs exactly that plan and refuses if it was edited since. Editing creates a new draft. Large plans require typing the file count to confirm. Execution still re-verifies everything (decision 4); the preview checks are for your confidence, not a replacement.

### U8. File previews

1. **Show in Explorer** first: the server opens `explorer /select,<path>` for one copy or both copies side by side (macOS `open -R` and Linux equivalents added later).
2. **Inline previews** next: thumbnails in lists for images; a side-by-side pane for keeper and candidate for images, PDF, text, audio and video. Formats the browser cannot show (RAW, HEIC, Office) fall back to Show in Explorer.

The file-serving endpoint serves only paths inside the scan's roots (U10).

### U9. Frontend: Angular with Angular Material and the CDK

In `server/frontend/`, built by Gradle (a Node Gradle plugin downloads Node; nothing installed globally) and packaged into the server jar, so `./gradlew build` builds everything. Built in DFS-037 with the `com.github.node-gradle.node` plugin 7.1.0, Node 24.22.2 and Angular 22.2 (versions pinned exactly, `npm ci`), Vitest for unit tests. Material and the CDK provide the tree, paged and sorted tables, virtual scrolling, tabs, dialogs and progress bars. During development, the Angular dev server proxies `/api` to a running server for hot reload. Chosen over React because agents write the code and the user reviews it: one sanctioned way per concern, services with dependency injection like the Spring side, and simpler archetypes.

### U10. Security

A server on localhost is reachable from any page in the user's browser (cross-site request forgery; with DNS rebinding a page can also read responses). So:

- Listen on `127.0.0.1` only, on a random port.
- At startup, generate a random token, print `http://127.0.0.1:<port>/?token=…` and open it in the browser. The server exchanges the token for an `HttpOnly`, `SameSite=Strict` session cookie and redirects to `/`, so the token leaves the URL before any script runs; the token stays valid for the server run. Every API request (`/api/**`) needs the cookie; the page itself is public, since it carries no data (DFS-036).
- Reject requests whose `Host` is not `127.0.0.1:<port>` or `localhost:<port>` (blocks DNS rebinding).
- State-changing requests need a matching `Origin`. No CORS.
- Folder listings, previews, file contents and plan entries are accepted only for paths inside the scan's roots. Built with the first endpoint that takes a path, which carries the criterion "a path outside the scan's roots → 403" (DFS-036 decision 1). First applied by `GET /api/scans/{id}/folders` and `GET /api/scans/{id}/sets` (DFS-047): `403` `PATH_OUTSIDE_ROOTS`, `..` resolved first.

Built with Spring Security as the **first server ticket**, before any endpoint that reads the disk, so no endpoint ever exists unprotected.

### U11. Launching

- Development: `./gradlew :server:bootRun`.
- First version: `./gradlew :server:bootJar` produces `dfs-server.jar` with the `Enable-Native-Access: ALL-UNNAMED` manifest attribute (the scanner calls native code on Windows), plus the start scripts `bin/dfs-server.bat` and `bin/dfs-server` (Gradle-generated by `:server:installBootDist`, next to `lib/dfs-server.jar`; DFS-035). Requires Java 25.
- Later: a `jpackage` installer with a bundled runtime (and maybe a tray icon), once the tool is shared with people without Java.

### D1. Shared Spring wiring

Per CLAUDE.md decision 6: extract the scanner wiring from `cli` into a `scanner-spring` autoconfigure module (`@ConfigurationProperties`, `@ConditionalOnMissingBean` for port overrides). Both `cli` and `server` use it. A ticket before the server's scanner wiring.

### D2. API style

JSON REST under `/api`; Server-Sent Events for scan and verify progress. An OpenAPI spec generated from the Spring controllers; the Angular API client and models generated from it, so the frontend and server types cannot drift. Built in DFS-038: springdoc-openapi 3.2.0 produces the spec, committed as `server/frontend/openapi.json` and checked by `OpenApiSpecTest` (`./gradlew :server:updateOpenApi` rewrites it); `ng-openapi-gen` 1.1.0 generates services and models from it on every frontend build, not committed. The spec is not served at runtime.

### D3. Scans in a session

Several scans can exist in memory at once. The start screen lists this session's scans (running or finished) with cancel. Plans belong to a scan and disappear with it until saved scans exist (U2).

### D4. Plan lifecycle in the first version

Draft (editable) → previewed (frozen, has an ID; U7) → editing creates a new draft. With no executing yet, the end point is "export plan as JSON", so the CLI can run it once DFS-021 and the executor exist.

### D5. Guard rails

- `server` has an `ArchitectureTest` with the same rule as `cli`: nothing under `us.pochaev.dfs.scanner` outside `domain`.
- The overlap-analysis package (U5b) depends on neither web nor controller classes.

### D6. Testing

- Server: MockMvc/WebTestClient tests per endpoint, including security (missing token, wrong `Host`, wrong `Origin` → 403; a path outside the roots → 403).
- Overlap analysis: JUnit cases from the examples in `folder-overlap-analysis.md`.
- Frontend: unit tests per component and service; a few Playwright end-to-end flows against a real server (scan a temp fixture folder → overlaps → build a rule → preview → verify).

### D7. Ticket namespaces

Product UI work goes under `DFS`. Harness work goes under `CCW`: archetypes for Angular components and API endpoints, and Node/npm steps in the pipeline gates. The endpoint archetype exists (`.claude/archetypes/endpoint/`, CCW-019, written from DFS-036, DFS-038 and DFS-040); the pipeline gates needed no Node/npm change for it, since the green gate already runs the frontend build and its tests. The component archetype exists too (`.claude/archetypes/component/`, CCW-020, written from DFS-037, DFS-038, DFS-041 and DFS-044): for it the gates now run the Vitest tests at the red step as well (`npmTest`), read Vitest's JUnit report (`server/build/test-results/vitest/`), accept a TypeScript stub (`new Error('not implemented: <ID>')`) and check the frontend's `.ts` and `.html` sources; a story spec surfaces a stub's error through `server/frontend/src/testing/stub-surfacing.ts`.

## What the UI needs from the core (`file-scanner`)

| Need | Status | For |
|---|---|---|
| `StartScan`, `CancelScan`, `GetScanStatus`, `GetScanResult`, `ScanEvent` progress | exists | U2, D3 |
| `ScanResult` roots and per-folder totals | exists (DFS-030) | U5b |
| `PreviewDeletionPlan` with rules, exceptions and single-set picks, as a list of typed operations | DFS-022 (draft); needs the U5/U6 plan model | U6, U7 |
| `ListDirectory(path)`: one level, read-only, same rules as the walker | new | U4 |
| Drift check: re-stat plan entries against the scan | new, read-only | U7.2 |
| Content verify: byte compare plan entries against keepers, with progress and cancel | new, read-only, on `ContentComparator` | U7.3 |
| `ExecuteDeletionPlan`, quarantine manifest, undo, purge | later | next version |

These are `file-scanner` tickets, in the scanner session's area. The UI side is built against these ports; until a port exists, server tickets can use a fake behind the same interface.

## Suggested ticket order (UI side)

1. `scanner-spring` extraction (D1).
2. `server` subproject skeleton, ArchUnit guard, launch scripts (U11, D5).
3. Security: token, cookie, `Host`/`Origin` checks (U10).
4. Angular skeleton built by Gradle, generated API client (U9, D2), plus the CCW archetypes (D7).
5. Scan screen: start, progress over SSE, list, cancel (U2, D3). Done: DFS-040 (API), DFS-041 (screen).
6. Overlap analysis in Java (U5b).
7. Tree plus Overlaps and Sets tabs (U3).
8. Folder detail view (U4), once `ListDirectory` exists.
9. Plan editor and preview (U6, U7.1, D4), once DFS-022 exists.
10. Drift check and content verify (U7.2, U7.3).
11. Show in Explorer, then inline previews (U8).

## Milestones

Planning labels for the suggested order above, set 2026-11-01. Ticket IDs are the backlog's; the steps refer to the list above.

| Milestone | Goal | Steps | Tickets |
|---|---|---|---|
| **M-1** | See the results of a scan: folder tree, Overlaps and Sets tabs | 6, 7 | DFS-046 overlap analysis in Java (M-1.1), DFS-047 result API: tree and sets (M-1.2), DFS-048 overlaps API (M-1.3), DFS-049 result screen: tree and Sets tab (M-1.4, done), DFS-050 Overlaps tab (M-1.5) |
| **M-2** | Inspect a folder: duplicate, unique, new-since-scan marks | 8 | not yet filed |
| **M-3** | Build and check a plan: rules, preview, drift, verify, export as JSON | 9, 10 | not yet filed |
| **M-4** | Show in Explorer | 11 | not yet filed |
| **M-5** | Execute a plan: quarantine, manifest, undo, purge (U1's next version; needs the core executor first) | | DFS-051 core executor and manifest (backend); undo, purge and the UI not yet filed |

M-1 runs in two tracks, DFS-046 → DFS-048 and DFS-047 → DFS-049, joining at DFS-050. DFS-045 (disk usage view) builds on DFS-047 and DFS-049.
