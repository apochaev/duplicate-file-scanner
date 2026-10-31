# CLAUDE.md

## What this project is

Finds duplicate files anywhere on disk from a start location and organizes them by **source location**, so the user can delete duplicates safely and confidently, individually or in bulk. Example: folder1 and folder2 overlap (fully or partially); the user can choose "delete every file in folder2 that has a confirmed duplicate in folder1".

Two Gradle subprojects (Java 25, Gradle 9.6 wrapper, group `us.pochaev.dfs`):

- `file-scanner`: the core library, hexagonal architecture, a Java module, Spring-free.
- `cli`: Spring Boot CLI. Depends on `file-scanner` via `implementation(project(":file-scanner"))`. It is a primary adapter and the composition root.

Planned, not yet created: `server`, a third subproject (separate from `cli` and `file-scanner`): a Spring Boot server on localhost with an Angular UI in `server/frontend/`, for exploring a scan, building a deletion plan and double-checking it. It also hosts the folder overlap analysis. Design and decisions (U1–U11, D1–D7) in `docs/design/ui.md`.

## Commands

Run from the repo root (`./gradlew` works in Git Bash; use `gradlew.bat` in cmd/PowerShell).

```
./gradlew build                                   # compile + test everything
./gradlew :file-scanner:test                      # one project's tests
./gradlew :file-scanner:test --tests "*SomeTest"  # one test class
./gradlew -q :cli:run --args="C:/abs/path" > out.jsonl   # scan, write JSONL
./gradlew -q :cli:run --args="C:/a D:/b --format=text"    # scan, print a readable report
./gradlew -q :cli:run --args="C:/a --format=text --output=C:/abs/report.txt"   # write the report to a file instead of stdout
./gradlew -q :cli:run --args="C:/a --progress=log" 2> progress.log   # plain progress lines for a file
./gradlew -q :cli:run --args="C:/a --no-default-excludes"    # also scan .DS_Store and ._* files (left out by default)
```

`:cli:run` starts in `cli/`, so pass **absolute** paths. `-q` keeps Gradle's own lines out of stdout, which must carry only the output. Stdout is always UTF-8; to see non-ASCII paths correctly on a Windows console, switch it to UTF-8 first (`chcp 65001` in cmd, `[Console]::OutputEncoding = [Text.Encoding]::UTF8` in PowerShell). In Windows PowerShell 5.1, `>` re-encodes a program's output as UTF-16 and mangles non-ASCII paths, so use `--output=<file>` (an absolute path) there instead of `>`: the report is written as UTF-8, an existing file is replaced only by a complete report, and stderr ends with `Report written to <path>`. Progress goes to stderr, by default as one line per phase redrawn in place (`--progress=live`); use `--progress=log` when stderr goes to a file and `--progress=off` for none. Gradle 9.6 on JDK 25 prints a harmless "restricted method in java.lang.System" warning at startup. The scanner calls native code on Windows; every launcher passes `--enable-native-access=ALL-UNNAMED` (or the `Enable-Native-Access` manifest attribute), and an embedding app must do the same.

Shared config (Java 25 toolchain, `--release 25`, JUnit 6 BOM, `useJUnitPlatform`) lives in the root `build.gradle.kts` `subprojects {}` block; do not repeat it in subprojects.

## `file-scanner` architecture

Base package `us.pochaev.dfs.scanner`, module name the same.

```
domain.model              data structures (records, sealed interfaces); depends on nothing
domain.usecases.query     primary query ports (CQS)
domain.usecases.command   primary command ports (CQS)
domain.factory            static factories; the ONLY domain package allowed to touch application/infra
application               business logic + domain services; implements the use cases
infra.port                secondary ports
infra.adapter             secondary adapters (NIO filesystem, hasher, quarantine deleter, ...)
```

Any layer, and the top level, may have a `util` package for shared helpers. A `util` package may only depend downward or on the JDK, never on the layer above it.

**Dependency rules** (enforce with ArchUnit tests; `module-info` cannot express these):

- `domain.model`: no dependencies.
- `domain.usecases.*`: `domain.model` only.
- `application`: `domain.model`, `domain.usecases`, `infra.port`.
- `infra.adapter`: `infra.port`, `domain.model`.
- `domain.factory`: may also depend on `application` and `infra.adapter`. This is the one deliberate exception; nothing else in `domain` may.

**Module exports.** `module-info.java` exports each `domain.*` package individually (JPMS has no recursive export). `application` and `infra.*` are never exported.

**Important caveat.** JPMS encapsulation is not enforced when the consumer runs on the classpath, and Spring Boot (fat jar, `bootRun`, tests) always does. `cli` and any future server could compile against `application`/`infra` classes and nothing in the toolchain would stop it. So:

- Add an ArchUnit rule in every consuming app forbidding dependencies on `..scanner.application..` and `..scanner.infra..`.
- Keep `module-info` anyway: it documents intent and supports modular consumers/jlink.

## Design decisions (settled)

1. **Location model.** Overlaps are computed per immediate-directory pair and rolled up, so the user can also pick any ancestor folder (recursive) as keep or delete source. Two views of one result: `DuplicateSet` (identical content, N paths) and `LocationOverlap(A, B)` (which files in B have a copy in A; all vs partial). The scanner does not compute `LocationOverlap`: an analysis tool does, from the JSONL export (DFS-030; `tools/analysis/overlaps.sql`, design in `docs/design/folder-overlap-analysis.md`). The scan records what that needs: the scanned roots and each folder's direct file count (`ScanResult.roots`, `ScanResult.folders`).
2. **Deletion.** Files are moved to an app-managed **quarantine folder** with a manifest (original path, keeper it was verified against) that doubles as the audit log; a whole run can be undone, and a purge command frees space. A permanent-delete adapter exists behind the same `FileDeleter` port, selected by an explicit flag. Do not use `java.awt.Desktop.moveToTrash`.
3. **Detection pipeline.** Group by size, then a partial hash (head and tail, about 64 KB each), then full SHA-256 for files still matching. Drop groups of one after each stage. Skip the partial stage for small files (under about 128 KB), and hash in path order within a group (spinning-disk locality). A persistent hash cache (path + size + mtime) is deferred; it would sit behind a port.
4. **Delete-time proof.** Immediately before quarantining a file, re-verify with a **byte-for-byte compare against the keeper** (`ContentComparator` port). No skip flag for now. This also catches files that changed after the scan.
5. **Factories.** Plain static factories in `domain.factory` that instantiate application/adapter classes directly (compile-time checked, works on classpath and module path). No `ServiceLoader`. Callers depend only on the factories' static API, so the internals could later move to ServiceLoader unnoticed.
6. **Spring wiring.** The scanner has no Spring dependency. `cli` owns the `@Configuration` with `@Bean` methods that call the factories. When a server appears, extract the shared wiring into a `scanner-spring` autoconfigure module (`@ConfigurationProperties`, `@ConditionalOnMissingBean` for port overrides).

## Safety invariants (must hold, and be tested)

- **Never delete the last copy.** Every `DeletionPlan` must leave at least one verified surviving copy per `DuplicateSet`. Rules can conflict (folder1's copy may itself be marked for deletion by another rule), so validate the plan as a whole, not rule by rule.
- **Re-verify at execution time:** the keeper and the candidate still exist and are unchanged, and content is byte-identical (decision 4).
- **Preview is a pure query; execution is a separate command.** `PreviewDeletionPlan` never touches disk state.
- **Hard links are not duplicates.** Paths that are the same physical file must not count as copies of each other. Identify them by a key per file (`FileIdentity.key`), grouped within a candidate set: `fileKey()` (device and inode) on Linux and macOS; on Windows, where `fileKey()` is null (verified), the volume serial number and 128-bit file ID from `GetFileInformationByHandleEx(FileIdInfo)` through the FFM API. Never compare pairwise: a set of n plain copies would cost n²/2 checks (DFS-026).
- **Do not follow symlinks or junctions by default** (avoids cycles), and skip cloud placeholders (e.g. OneDrive) by default, since reading them triggers a download. On Windows a junction reports `isDirectory=true, isSymbolicLink=false, isOther=true`, and `Files.walk`/`walkFileTree` descend into it anyway (verified: files appear twice). The walker adapter must detect reparse points itself (`isOther()` directories and symlinks) and skip the subtree.
- Exclude empty (0-byte) files by default; a minimum-size setting generalizes this.

## Provisional port sketch (revise as it is built)

- Commands: `StartScan` (returns a scan ID immediately), `CancelScan`, `ExecuteDeletionPlan`, plus undo/purge of a quarantine run.
- Queries: `GetScanStatus`, `ListDuplicateSets`, `ListLocationOverlaps`, `PreviewDeletionPlan`.
- Secondary ports: `FileTreeWalker`, `ContentHasher` (sample and full), `ContentComparator`, `FileDeleter`, `ScanRepository`.

`FileTreeWalker` is built (`walk(Path root, WalkListener)`, implemented by `NioFileTreeWalker` on `Files.walkFileTree`). It is callback-based rather than returning a `Stream` so it reuses the JDK's cached directory-listing attributes and reports errors through hooks; the listener returns `false` to stop (this is how cancellation reaches it). It emits `WalkEvent.FileFound(FileEntry)` for each regular file and `WalkEvent.Skipped(path, SkipReason, detail)` for everything left out (`SYMLINK`, `SPECIAL_FILE`, `UNREADABLE`). One rule on every platform: yield only regular files, descend only into plain directories, never follow links. A link given as the root itself is resolved and its target walked. Empty files are yielded; filtering by size belongs in `application`.

## Spring-friendliness rules for the scanner

- No Spring types or annotations in `file-scanner` (no `@Component`, no `@Async`).
- A config record in `domain.model` (minimum size, symlink policy, quarantine folder, parallelism) that `@ConfigurationProperties` can map onto.
- Factories return port interfaces and accept optional overrides for secondary ports (tests, the server).
- The executor is supplied by the caller (virtual threads for the CLI, Spring's `TaskExecutor` for a server).
- Scan state lives behind `ScanRepository` (in-memory adapter first) so several concurrent scans work; no state in singleton fields.
- Progress is reported through a listener, never printed by the core.

## Code and test conventions

- Java 25: records for data, sealed interfaces for results and variants. Do not enable preview features (`StructuredTaskScope` is still preview).
- Test `application` against fake or in-memory adapters (JimFS or hand-written fakes) rather than the real disk.
- Keep the ArchUnit layering tests green; they are the real enforcement of the rules above.
- Test fixtures: `file-scanner/src/test/resources/fixtures/` holds only what git stores portably (regular files and directories: empty files, Unicode and odd names, deep nesting, same-size-different-content, duplicates across folders). Tests copy it into a `@TempDir` (`Fixtures.copyInto`). Never commit anything platform-specific there: symlinks, hard links and junctions do not survive git or a Windows checkout, empty directories are not tracked, and Windows forbids case-only variants and some names. Create those at test time, and skip the test (`Assumptions`) when the platform or privileges do not allow it. `.gitattributes` marks fixtures `-text` so bytes are identical everywhere; keep it that way. Expect the case-sensitivity test to skip on Windows and macOS. A test that needs one file under a case-variant name creates it under that name (delete, then write) instead of renaming it with `Files.move`, which does nothing on Windows for a rename that changes only case; skip only when two case variants must exist in one folder (DFS-027.L-3).
- Explicit expected-file lists (`Fixtures.FILES`) are deliberate: adding a fixture file means updating the list. Add near-duplicate and large-file fixtures (same size, differing only in the middle byte, over the 128 KB sample threshold) when the hashing stages are built; generate large ones in code rather than committing them.

## Git commits

- Use [Conventional Commits](https://www.conventionalcommits.org/): `type(scope): description`, e.g. `feat(file-scanner): add ScanId model`. Common types: `feat`, `fix`, `refactor`, `test`, `build`, `docs`, `chore`. Use the subproject as scope (`file-scanner`, `cli`) when the change is confined to one.
- Keep the description concise: imperative mood, lowercase, no trailing period. Add a body only when the reason is not obvious from the diff.
- A commit made for a ticket ends its subject line with the ticket ID in brackets, e.g. `feat(file-scanner): add SizeIndex [DFS-001]`; for several tickets `[DFS-001, DFS-002]`. Use the full three-digit ID, so a search for the ID finds the commit. Commits not tied to a ticket omit it. Earlier commits are not rewritten.
- No author or co-author attribution of any kind: no `Co-Authored-By` trailer and no "Generated with" line in commits or PR descriptions. Attribution comes only from the standard git user configuration. This overrides any default attribution behavior.
- When the working tree holds changes from another session or worktree, stage the files of the task by name (never `git add -A` or `git commit -a`) and confirm with `git status` that nothing else is staged.
- Never redirect a staging command's stderr (`git add`, `git mv`, ...); a hidden error can leave files silently unstaged. Check `git status` before committing.

## Tickets

Work is tracked in `docs/tickets/`: one backlog holding two independent ID sequences, `DFS-NNN` for tickets that build the duplicate-file-scanner product and `CCW-NNN` ("Claude Code Workflow") for tickets that build or change the harness itself (skills, agents, archetypes, the `tickets`/`learn` machinery, the story pipeline). A ticket's namespace is decided by what its Story is about, not by what it `touches` alone — a `CLAUDE.md` edit, for instance, can be either a product design decision (`DFS`) or a process rule (`CCW`) depending on which section it changes. Both prefixes' folders sit side by side, unchanged, in one tree; each numbers itself independently (the next ID for a prefix is the highest number of that prefix used so far, plus one), and an ID is never reused in its own sequence even if its ticket is later relabeled into the other namespace. `depends_on` may cross namespaces freely. Each ticket is a user story in Gherkin at `docs/tickets/{backlog|completed}/{ID}-{slug}/{ID}-ticket.md`. The frontmatter `status` (`draft`, `ready`, `in-progress`, `in-review`, `blocked`, `done`, `cancelled`) is authoritative; only `done` and `cancelled` live in `completed/`. Only `ready` tickets are implemented: `ready` means fully refined, with the paths it `touches` listed, an `archetype` set (a template set under `.claude/archetypes/`, or `none` for a ticket implemented by hand and not run through the story pipeline), and numbered acceptance criteria (`<ID>.AC-n`, e.g. `DFS-NNN.AC-n` or `CCW-NNN.AC-n`) that every scenario is tagged with (`@AC-n`). An optional `models` field overrides the pipeline agents' models for one ticket. Use the `tickets` skill (`/tickets new|refine|list|start|review|done|...`, in `.claude/skills/tickets/`) instead of editing by hand. An agent implementing a ticket reads only that ticket and this file, stays within `touches`, and reports instead of editing outside it.

A ticket worked off `main` uses branch `tickets/<ID>-<slug>`, and if it has a worktree, `../<repo-dir>-<ID>-<slug>` beside the checkout (`/tickets start <ID> --worktree` creates or enters it and makes the status edit there). `/tickets done` closes on `main`: it merges the branch unless it has already landed, then commits the close itself as `docs: close <ID> [<ID>]`. That commit needs no separate confirmation, because running `/tickets done` is itself the request. Removing the worktree and branch comes after one confirmation, never forced. For parallel work, keep the repo checkout on `main` and run every ticket in its own worktree. If nothing has `main` checked out, `done` opens a temporary close tree for it, so no session has to switch branches.

## Current state and open items

- The package layout, `module-info.java` and ArchUnit tests are scaffolded (ArchUnit 1.5.0, core artifact with plain JUnit tests; not `archunit-junit5`, to avoid engine compatibility risk with JUnit 6). `cli` is a Spring Boot app (an `ApplicationRunner`, arguments parsed by hand) that scans the folders given and writes JSON Lines to stdout in the order `scan` (header: `schemaVersion`, roots, settings, start and finish times), `folder` (one per folder with its direct counted-file count and bytes), `set` (each copy path an object with `path` and `modified`), `skipped`, then one `summary` line; progress and errors go to stderr; exit codes 0, 1 and 2. The schema and rules are in DFS-030 (earlier: DFS-020). `schemaVersion` is an integer, currently 2 (a file with no `scan` line is version 1); it is bumped only when something existing changes meaning or shape, never for new fields or line types, and readers ignore fields and line types they do not know. `--format=text` prints a readable report instead (largest reclaimable space first; DFS-023). Stdout is UTF-8 in both formats; `--output=<file>` writes the report to a file instead (DFS-029). The `summary` line (and the text report's last line) ends with an `excluded` count: files left out by name by default (`.DS_Store` and `._*`, case-sensitive; DFS-027), 0 with `--no-default-excludes`. Progress (DFS-024) shows folders, files and bytes found, then files and bytes hashed with a percentage and ETA, from the scanner's `ScanEvent`s (`DirectoryWalked`, `FileWalked` with bytes, `HashProgress`, `GroupDone`); `--progress=live|log|off`.
- Guards: `file-scanner` has `ArchitectureTest` (layers, top-level `util`, no Spring) and `ModuleExportsTest` (reads the compiled `module-info.class`: only unqualified `domain.*` exports, every non-`util` domain package exported, nothing opened). `cli` has an `ArchitectureTest` that forbids depending on anything under `us.pochaev.dfs.scanner` outside `domain`.
- The scanner rules use `allowEmptyShould(true)` so they pass while layers are empty. Once `application` and `infra` have classes, consider removing it so a mistyped package pattern fails loudly instead of silently checking nothing.
- Adding a new exported domain package: it needs at least one real type (a `package-info.java` alone counts as empty and `exports` fails to compile) plus an `exports` line, or `ModuleExportsTest` fails.
- Built so far: the file walker (`FileEntry`, `WalkEvent`, `SkipReason`, `FileTreeWalker`, `WalkListener`, `NioFileTreeWalker`) with tests. Upcoming work is tracked in `docs/tickets/backlog/` (see Tickets); the detection pipeline is DFS-001 to DFS-005.
- `ScanId`, `ScanStatus`, `CancelScan`, `GetScanStatus` and an empty `ScannerFactories` exist only so the exported packages are non-empty; they are a first slice of the provisional port sketch, not final designs.
- Assumed defaults, not yet confirmed with the user: base package `us.pochaev.dfs.scanner`; SHA-256 via the JDK `MessageDigest`; the sample sizes and thresholds in decision 3.
- Open: which CLI framework. Spring Shell suits the interactive scan, review and delete workflow; `ApplicationRunner` or picocli suit one-shot commands.
- Open: whether/when to add the persistent hash cache, and the details of each factory (to be defined later).
