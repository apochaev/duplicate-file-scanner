# DFS-064: Keep each scan phase's totals and the finish time in the progress stream — design

## Summary

Changes the existing scan API in `us.pochaev.dfs.server.scan`; no new endpoint, no new error code, no new security rule.

- AC-1 (full-hash ends at 100% before HARD_LINKS): already what the core sends; the registry copies `HashProgress`. Pinned through `Counters.phases(...)` (stub) because every progress read now goes through it.
- AC-2 (`phases` in run order, totals kept, `finished` flags): new record `PhaseTotals`; `ScanProgress.phases`; filled by stub `Counters.phases(ScanStatus)`.
- AC-3 (flat fields unchanged): flat components and `Counters` fields untouched; `phases` is appended last.
- AC-4 (`finishedAt` fixed at the end, null while running, `phases` kept on COMPLETED): `ScanEntry.finishedAt`; stubs `ScanRegistry.finishedAt(Tracked, String)` and `ScanRegistry.sweep()`; injectable `Clock`.
- AC-5 (always present, never missing): both are `REQUIRED` in the spec; `finishedAt` also `nullable`; `phases` is an empty list, never null.
- Placement: `us.pochaev.dfs.server.scan` only (one new public record, `PhaseTotals`); no export or module change (the server is not a module).
- Spec: `server/frontend/openapi.json` regenerated (new schema `PhaseTotals`, `ScanProgress.phases`, `ScanEntry.finishedAt`, both required).
- Frontend: three literals gain the two fields (decision 4): `result-test-data.ts` and `scan-screen.spec.ts` (the only places that build a `ScanProgress` or `ScanEntry`; the other specs use `result-test-data.ts`). Vitest: 200 tests pass.
- **Look closely at**: (1) the core sends no event when a scan ends, so `finishedAt` is stamped by a 50 ms sweep plus a read-time safety net, accurate to about 50 ms, not to the instant (see State and threads); (2) AC-5 says progress events include `finishedAt`, but decision 3 and AC-4 put it on `ScanEntry` only and the stream sends `ScanProgress`: this design follows decision 3, so the event carries `phases` and the entry carries `finishedAt` (see Open design notes); (3) because every progress read reaches the stubbed `phases(...)`, all scan-reading existing tests fail red with the stub.
- Checklist items not applicable: 2 (no path parameter), 11 (no new endpoint, state-changing method or stream; the existing stream's data only grows).
- Expected breakage: `BrowserFlowTest` only (an `e2e`-tagged class that `:server:test` does not run). The failing classes of `:server:test` (`ScanApiTest`, `ResultApiTest`, `PlanApiTest`, `OverlapApiTest`) all fail with `not implemented: DFS-064` in every failure, so they need no declaration.
- Tests read for the design: none ("No tests read.").

## Endpoints

No new operation. Changed responses (tag `scans`, operation IDs unchanged):

| Method | Path | Tag | Operation ID | Request body | Success | Errors (status: code) |
|---|---|---|---|---|---|---|
| `POST` | `/api/scans` | `scans` | `startScan` | `StartScanRequest` | `201 ScanEntry` (now with `finishedAt`, `progress.phases`) | unchanged |
| `GET` | `/api/scans` | `scans` | `listScans` | none | `200 ScanEntry[]` | unchanged |
| `GET` | `/api/scans/{id}` | `scans` | `getScan` | none | `200 ScanEntry` | unchanged |
| `POST` | `/api/scans/{id}/cancel` | `scans` | `cancelScan` | none | `200 ScanEntry` | unchanged |
| `GET` | `/api/scans/{id}/events` | `scans` | `scanEvents` | none | `text/event-stream`, `progress` events of `ScanProgress` (now with `phases`) | unchanged |

## Security matrix

Not changed: no new path and no new method. The existing cookie, `Host` and `Origin` tests of DFS-040 and `SecurityTest` cover these paths. A refused request still starts or cancels nothing.

## API records

- `PhaseTotals(phase, finished, directories, files, bytes, groups)`: new, public; from the registry's `Counters` (core events `PhaseStarted`, `DirectoryWalked`, `FileWalked`, `HashProgress`, `GroupDone`). All six required. `phase` has `allowableValues` WALK, PARTIAL_HASH, FULL_HASH, HARD_LINKS. Components that do not apply to a phase are 0, never null: WALK uses `directories`, `files`, `bytes` (found so far); PARTIAL_HASH and FULL_HASH use `files` and `bytes` (the `HashProgress` input totals, 0 until its first event, which a page reads as unknown); HARD_LINKS uses `groups` (the `GroupDone` total, 0 until its first event). `groups` is the phase's own total and is not replaced by the set count on COMPLETED, unlike the flat `groupsTotal`.
- `ScanProgress`: gains `phases: List<PhaseTotals>`, last component, required, never null, empty before the first `PhaseStarted`. The flat components keep their names, order, values and meaning.
- `ScanEntry`: gains `finishedAt: Instant`, after `startedAt`; `requiredMode = REQUIRED` and `nullable = true` (the spec says `["string","null"]` and lists it in `required`). Null while QUEUED, RUNNING or CANCELLING; set once at the end for COMPLETED, CANCELLED, FAILED; same on every later read; never before `startedAt`.
- `finished` per entry: true when a later phase has started, or the scan's status is COMPLETED. A CANCELLED or FAILED scan leaves its last entry `finished` false (the phase did not finish).

## State and threads

- `Counters` (one per scan, created before `StartScan.start`, as today) keeps, besides the flat volatile fields, one totals entry per started phase. Only the scanning thread writes it in `onEvent`; any thread reads it through `phases(ScanStatus)`, which returns an immutable snapshot (a copy-on-write array or a `volatile` immutable list). The flat fields keep being reset at `PhaseStarted` (AC-3); the per-phase entries are not.
- Per event: `PhaseStarted(p)` opens an entry for `p` (the previous one is thereby finished). `DirectoryWalked`, `FileWalked` update the WALK entry (walk totals are final once the next phase starts; the entry may simply be written while `WALK` is the open one). `HashProgress(p, ...)` sets the open hash entry's `files` = `filesTotal` and `bytes` = `bytesTotal`. `GroupDone(HARD_LINKS, done, total)` sets the open entry's `groups` = `total`. The listener only writes memory and never throws.
- `finishedAt`: `Tracked` gains `AtomicReference<Instant> finished`. The core sends no event when a scan ends and has no finish time, and `ScanRegistry` does not run the scan's task, so the hook is observation, not a callback: `ScanRegistry.sweep()` runs on a daemon scheduler every 50 ms (the implementation creates it in the constructor, `@PreDestroy` shuts it down) and for each scan with `finished == null` reads the core's status and, if it is COMPLETED, CANCELLED or FAILED, does `compareAndSet(null, max(clock.instant(), startedAt))`. `finishedAt(Tracked, String status)` (called from `entry`) does the same stamp when the status is terminal, so a read never sees a terminal status with a null `finishedAt`; whichever of sweep and read sees the end first wins, so the value is fixed. Rejected: wrapping `scanExecutor` (the server does not own the bean, tests replace it by name) and a per-read stamp alone (the ticket rejects it: the stamp would be taken when someone first reads).
- `Clock`: the Spring constructor takes `ObjectProvider<Clock>` (the context's `Clock` bean, else `Clock.systemUTC()`); a second package-private constructor takes a `Clock` directly. A test supplies a fake either way. `startedAt` also comes from the clock now (was `Instant.now()`).
- Stream: unchanged mechanics (event `progress`, `ScanProgress` data, first event on connect, at most every 250 ms while the record changes, terminal event last, then complete). The record's `equals` now includes `phases`, so a phase total that changes sends an event. `finishedAt` is not in the stream data (see Open design notes). Lists in the registry: newest first, unchanged.

## Layer and package placement

- `us.pochaev.dfs.server.scan`: `PhaseTotals` (new), `ScanProgress`, `ScanEntry`, `ScanRegistry` (changed). `ScanController`, `ScanEventStreams`: unchanged.
- `us.pochaev.dfs.server.api` (shared): unchanged.
- `server/src/main/resources/application.properties`: none.
- `server/frontend/openapi.json`: regenerated in this phase (commit below).
- Frontend code changed to keep the build green with the regenerated client: `server/frontend/src/app/results/result-test-data.ts` (`progress` gains `phases: []`, `scanEntry` gains `finishedAt: null`) and `server/frontend/src/app/scans/scan-screen.spec.ts` (same two, in its local `progress` and `entry`). No behaviour change; the DFS-057 row is untouched. The ticket's `touches` names both directories.
- `CLAUDE.md`: the `server` entry naming `phases` and `finishedAt` is left to the implementer (it is a done-item the verifier reads); not edited in this phase.

## Spec and client

- `./gradlew :server:updateOpenApi` run after the stubs. Added: schema `PhaseTotals`; `ScanProgress.phases` (array of `PhaseTotals`, required); `ScanEntry.finishedAt` (`string|null`, `date-time`, required). No operation changed.
- `ng-openapi-gen`: model `PhaseTotals`; `ScanProgress.phases: PhaseTotals[]`; `ScanEntry.finishedAt: string | null` (both non-optional, so a literal that builds them must list both). The services are unchanged; the stream is still read by `ScanEventsService` with `EventSource`, not by a generated method.
- Order trap, for the implementer too: `updateOpenApi` runs on the test classpath, which includes the frontend build, so it fails while frontend literals already carry fields the old spec lacks. The order that works: stubs, `updateOpenApi` with the old literals, then add the literals (done here).

## Checklist disposition

1. Security on every new path: not changed, no new path or method; covered by the existing DFS-040 tests and `SecurityTest`.
2. Paths inside a scan: not applicable: no endpoint of this ticket takes a path.
3. Input validation and error codes: no input and no code changes; the existing refusals are untouched.
4. Status codes and headers: unchanged; a COMPLETED or ended scan still answers `200`, a `201` still carries `Location`.
5. The API records: `PhaseTotals` new, `ScanProgress.phases` and `ScanEntry.finishedAt` required, `finishedAt` nullable, times as `Instant`, `phase` as a string with `allowableValues`; the per-phase meaning of each component is on the record (above).
6. The spec: regenerated; tag and operation IDs unchanged; `OpenApiSpecTest` passes (it ran in the red run: 3 of 3). Spec assertions stay out of the story tests.
7. The generated client: see Spec and client; the three literals are fixed and the Vitest suite passes (200 tests).
8. State kept in `server`: per-scan `Counters` and `Tracked.finished`, for this server run, written by the scanning thread (`Counters`) and by the sweep or any reader (`finished`, compare-and-set); the `Counters` exist before `StartScan.start`; the registry scheduler is the only new thread.
9. Streams: no change to the mechanics; only the data grows by `phases`; no `finishedAt` in the data.
10. Asynchronous states in tests: no timing assertion is needed. Drive the registry with fakes of the four core ports and a fake `Clock` (the package-private constructor), set the fake status to COMPLETED, set the clock, call `registry.sweep()` directly, move the clock, read: deterministic (the test must be in package `us.pochaev.dfs.server.scan`). Phase totals: feed `ScanEvent`s to the listener passed to the fake `StartScan.start`. Through HTTP, a held executor keeps a scan QUEUED (AC-5 queued scenario), and a real small scan reaches COMPLETED by a bounded poll.
11. The dev proxy: not applicable: no new endpoint, no state-changing method and no new stream.
12. Docs: `CLAUDE.md`'s `server` entry names `phases` and `finishedAt` (implementer's done-item); `docs/design/ui.md` is not in `touches` and is not changed.

## Expected breakage

- `BrowserFlowTest`: `e2e`-tagged, run only by `:server:e2eTest`, not by `:server:test` (so the red run does not show it); it drives scans through the page and the API, and every scan read fails over HTTP with the stub's `500`, which the page shows as an error, not as `not implemented`.

Every other class that fails in `:server:test` (`ScanApiTest` 17 of 28, `ResultApiTest` 31 of 33, `PlanApiTest` 24 of 26, `OverlapApiTest` 36 of 36) carries `not implemented: DFS-064` in each failure, so it needs no declaration. Their number is large because every read of a scan's progress now reaches the stubbed `Counters.phases`.

## Open design notes (as of the design phase)

- **AC-5 versus decision 3.** AC-5 says "the progress events always include `phases` ... and `finishedAt`". Decision 3 and AC-4 put `finishedAt` on `ScanEntry`, and the stream sends `ScanProgress`, which has no `finishedAt`. Following decision 3, the event carries `phases` and the two GET endpoints carry both. A page that wants the duration after the terminal event reads `GET /api/scans/{id}` once. If the stream should carry `finishedAt` too, `ScanProgress` would need a second `finishedAt` (or the stream would send `ScanEntry`): a change for the design gate to decide before the test phase.
- The hook for `finishedAt` is the sweep described above (decision 3 left the hook to the architect); the stamp is accurate to the 50 ms sweep interval.
- `phases(ScanStatus)` takes the core status so a COMPLETED scan finishes its last entry; it is the one stub that every progress read reaches. `groups` of the HARD_LINKS entry is the phase's own total, not the set count.

## Stubs (as of the design phase)

- Files stubbed: `server/src/main/java/us/pochaev/dfs/server/scan/ScanRegistry.java`: `ScanRegistry.sweep()`, `ScanRegistry.finishedAt(Tracked, String)` (private), `ScanRegistry.Counters.phases(ScanStatus)`. Records `PhaseTotals`, `ScanProgress` and `ScanEntry` are not stubbed (components and `@Schema` only). The `Clock` constructors, `Tracked.finished` and `startedAt` from the clock are real.
- Each stub throws `new UnsupportedOperationException("not implemented: DFS-064")`. Over HTTP the client sees a `500`; `TestServer` rethrows the recorded exception, so a story test fails with the stub's own exception (`StubSurfacingConfiguration`). `entry(...)` reaches `finishedAt` and `progress(...)` reaches `phases`, so AC-1 to AC-5 all fail red through a stub; AC-4's fake-clock scenarios also reach `sweep()`.
- Commit (stubs and the regenerated spec): filled in after the design commit.
