# DFS-052: Skip quarantine folders when scanning — verification

## Product owner review

### What you can do now

Scan a drive or folder that holds an app quarantine (`.dfs-quarantine`) and the quarantined files no longer show up as duplicates of the files they were checked against. The report lists each quarantine folder once under "Skipped" with reason `QUARANTINE`. If you point a scan at a folder inside a quarantine, it is refused with `LOCATION_IN_QUARANTINE`, in the CLI and in the browser server.

### Acceptance criteria

- **DFS-052.AC-1** met: the walker never enters a folder named exactly `.dfs-quarantine`, at any depth, and reports it once as skipped.
- **DFS-052.AC-2** met: only that exact name on a real folder is skipped. `.DFS-Quarantine`, `.dfs-quarantine-old` and a file named `.dfs-quarantine` are scanned as before, and a symlink or junction with that name is still reported as a link and not followed.
- **DFS-052.AC-3** met: a quarantined copy is not a duplicate, no folder total counts it, `skipped` holds the folder, and `excluded` stays 0. The same holds with default excludes off.
- **DFS-052.AC-4** met: a location inside a quarantine is refused before any disk lookup, also when it is one location among several or does not exist. Nothing is started. `.DFS-Quarantine` is not refused.
- **DFS-052.AC-5** met, with the exit code you chose: JSON Lines and the text report show the skipped line, counted in the summary. A location inside a quarantine exits **1** (`ScanCommand.ERROR`, like every other refused start), with the message on stderr and nothing on stdout. The ticket text still says exit 2 (decision 3, AC-5 and the "CLI refuses" scenario), so its owner needs to correct that wording.
- **DFS-052.AC-6** met: `POST /api/scans` answers `400` `LOCATION_IN_QUARANTINE` and starts no scan.

### Try it

```
mkdir -p C:/tmp/q/A C:/tmp/q/.dfs-quarantine/run-1/A
echo hello > C:/tmp/q/A/photo.jpg; cp C:/tmp/q/A/photo.jpg C:/tmp/q/.dfs-quarantine/run-1/A/
./gradlew -q :cli:run --args="C:/tmp/q --format=text"
```
What to look for: no sets, `Skipped (1):` with `C:\tmp\q\.dfs-quarantine  QUARANTINE  quarantine folder, not scanned`, and a summary ending `1 skipped, 0 excluded`. Then `./gradlew -q :cli:run --args="C:/tmp/q/.dfs-quarantine/run-1"` prints `error: LOCATION_IN_QUARANTINE: location is in a quarantine folder: ...` and exits 1.

### Decided without you

- The CLI exit code for a location inside a quarantine is 1, not 2. You confirmed this at design. The ticket text is not yet updated.
- The skipped line's detail text is `quarantine folder, not scanned`.
- All locations are checked for quarantine before any is looked up, so `LOCATION_IN_QUARANTINE` wins over a missing location earlier in the list.
- The check normalizes the path first, so `<x>/.dfs-quarantine/..` (which means `<x>`) is not refused.
- `NioQuarantineDeleter.QUARANTINE_FOLDER` is kept and now points to the shared `QuarantineFolder.NAME`. It was not removed.
- The scan root itself is never pruned by the walker. `StartScan` refuses such a root instead.

### Known limits

- A location that is a symlink or junction pointing into a quarantine is not refused, because the check uses only the path's names. Its contents are scanned (design open note 1). This needs a link you made yourself, so it is rare.
- Any folder of yours named exactly `.dfs-quarantine` is skipped too. The report shows where.
- The scanner cannot look inside a quarantine. That is left to undo and purge (DFS-051's follow-ups).
- The server's folder picker still lists `.dfs-quarantine` folders (out of scope).

### Evidence

- 18 DFS-052 tests, all passing and none skipped (from the JUnit XML): `NioFileTreeWalkerQuarantineTest` (7, including the symlink and Windows junction cases, both run on this machine), `ScanQuarantineEndToEndTest` (2, real pipeline on real files), `ScanServiceQuarantineTest` (5, with a resolver fake that records disk lookups), `ScanCommandTest` (3 added), `ScanApiTest` (1 added).
- `./gradlew build` green on the final tree (re-run by the verifier, tasks up to date with HEAD `da8a103`). The implement gate reported 839 passed and 3 skipped, all 3 skips from before this ticket.
- Deterministic story-gate passed: freeze, touches (19 files), no stubs left, traceability (6 criteria, 11 scenarios).

### How it went

One attempt per phase, no retries, defects or escalations (`plan.json`). Design (opus, 52 tool uses) found the exit-code conflict, and you settled it as exit 1. Tests (sonnet, 28 tool uses) left 18 story tests red on the stubs. Implement (sonnet, 11 tool uses) turned them green.

Ready for the user's approval. The ticket's "exits 2" wording (decision 3, AC-5, the CLI refusal scenario) should be corrected to exit 1 by the ticket owner at close.

## Summary

- DFS-052.AC-1 met: plain `.dfs-quarantine` directories are pruned at any depth and reported once (`NioFileTreeWalkerQuarantineTest`, 2 tests).
- DFS-052.AC-2 met: exact name, plain directories only. Casing and suffix variants and a regular file are walked, and a symlink or junction stays `SYMLINK`/`SPECIAL_FILE` (5 tests, symlink and junction both run).
- DFS-052.AC-3 met: real pipeline, no sets, no folder total under the quarantine, `skipped` holds it as `QUARANTINE`, `excluded` 0, also with default excludes off (2 tests).
- DFS-052.AC-4 met: refusal by path names only (the resolver fake records zero lookups), no record and no task, all 4 outline rows plus the casing row (5 tests).
- DFS-052.AC-5 met with the user-approved deviation: exit 1 (`ScanCommand.ERROR`), not the ticket's 2. JSON Lines and text output correct (3 tests).
- DFS-052.AC-6 met: `400` `LOCATION_IN_QUARANTINE`, the message names the location, no scan listed (1 test).
- Design conformance: the diff matches `design.md`, including all 14 checklist dispositions. CLAUDE.md is updated as design note 5 asked.
- Invariants: link and junction handling is untouched, and the junction check still runs before the quarantine check. Other invariants are not affected.
- Ready for the human gate. The ticket text needs its exit-code wording corrected (owner action, not a phase rework).

## Criterion verdicts

- **AC-1: met.** `preVisitDirectory` emits `Skipped(dir, QUARANTINE, QUARANTINE_DETAIL)` and returns `SKIP_SUBTREE` for any non-root directory whose file name equals `QuarantineFolder.NAME`. The tests assert the yielded list is exactly `A/photo.jpg`, one skip with the path, reason and detail, and no event mentioning `run-1`. The deeper test asserts both quarantine folders skipped and `x.bin` not yielded.
- **AC-2: met.** The match is `String.equals`, so it is exact and case-sensitive. The `SPECIAL_DIRECTORY` check still comes first (a junction stays `SPECIAL_FILE`), and symlinks reach `visitFile` (stay `SYMLINK`). The `.DFS-Quarantine` test deletes the Background quarantine first, so it is meaningful on case-insensitive NTFS. Both link tests ran (not skipped) on Windows.
- **AC-3: met.** No pipeline change was needed. `ScanQuarantineEndToEndTest` runs the real `ScanPipeline` with NIO adapters and asserts `sets()` empty, `folders()` exactly `[A]`, one `skipped` with the quarantine path and `QUARANTINE`, and `excluded` 0. It also runs with `defaultExcludes=false`. It uses `ScanPipeline` rather than `StartScan`, which the design allowed.
- **AC-4: met.** `firstInQuarantine` makes one pass over `toAbsolutePath().normalize()` name elements, after `NO_LOCATIONS` and before the resolve loop. The tests use a never-existing absolute base, a recording resolver (zero lookups asserted), and an empty repository with an empty executor. The message must contain the named location. The casing row returns `Success` with one record.
- **AC-5: met (with the user's exit-1 decision; ticket text says 2).** JSONL: exactly `skipped` and `summary` lines, `reason` `QUARANTINE`, summary `skipped` 1, `sets` 0. Text: `Skipped (1):`, the line `    <path>  QUARANTINE  ...`, and the summary line asserted verbatim. Refusal: `ScanCommand.ERROR`, stderr starting `error: LOCATION_IN_QUARANTINE: ` and containing the location, stdout `""`. No CLI source change was needed, as the Scope said.
- **AC-6: met.** It runs on a real server through `TestServer`: `400`, `code`, the message contains the location, and `GET /api/scans` is empty afterwards. No server source change.

## Test quality

- The tests assert observable output (yielded paths, skip events, `ScanResult` fields, CLI stdout/stderr and exit code, HTTP body). None restates the implementation.
- "Before any disk lookup" is proven by a recording `LocationResolver`, not inferred. "No scan created" is proven by the repository and executor fakes, and on the server by the scan list.
- Fixtures are generated at test time in `@TempDir` (2 MB content in the core tests), and nothing is committed under `fixtures/`, as CLAUDE.md requires.
- Minor: the AC-1 "nothing under the quarantine visited" check uses `toString().contains("run-1")` on events. It is coarse but sufficient, since no other path in the tree contains `run-1`.
- Minor: AC-3's "excluded is unchanged" is checked as `0` in both configurations, not as a before/after comparison. It is equivalent here, since the fixture holds no metadata files.

## Design conformance

- `QuarantineFolder.NAME`, `SkipReason.QUARANTINE` (last value), `ErrorCode.LOCATION_IN_QUARANTINE` (after `LOCATION_IS_LINK`) and the `StartScan` Javadoc are as designed. `module-info.java` is unchanged, since `domain.model` was already exported.
- `NioQuarantineDeleter.QUARANTINE_FOLDER = QuarantineFolder.NAME` (open note 4).
- The check order in `ScanService.begin` and the message text match the Behavior section.
- Checklist dispositions 1 to 14: the applicable ones (2, 5, 7, 13, 14) hold in the diff. The not-applicable ones are correct, since there is no state, ID, executor or query change.
- CLAUDE.md: `QUARANTINE` is added to the walker's skip-reason list, and a new safety invariant "Scans leave quarantine folders out" is added (design note 5).
- DFS-030.L-2: the test and main trees were searched for `SkipReason.values`, `ErrorCode.values` and enumerations of reason or code strings. None exist. The only `SkipReason` consumers outside the core print `reason().name()` (`ScanCommand`, `TextReport`), so the new value needs no change there.

## Safety invariants

- **Do not follow symlinks or junctions:** preserved. The quarantine check is placed after the `SPECIAL_DIRECTORY` check and applies only to plain directories. Links are still handled in `visitFile`. Tested for both.
- **Never delete the last copy, re-verify at execution, preview is pure, one execution at a time:** not touched by this diff. This ticket strengthens the plan side indirectly, since quarantined files can no longer enter a scan result that a plan is built from (except the link-location gap, open note 1).
- **Hard links are not duplicates:** unchanged (no pipeline change).
- **Empty files excluded:** unchanged.
- **Layering:** `application` uses only `domain.model.QuarantineFolder`, not `infra.adapter`. The ArchUnit tests pass in the build.

## Done-items

All seven ticked by the verifier. The scenarios are automated and pass (18 invocations, JUnit XML, 0 skipped). The display names start with `DFS-052.AC-n`. The build is green. The checklist is addressed in `design.md`. The DFS-030.L-2 search was re-done by the verifier with no hits. CLAUDE.md was read and holds both statements. The story-gate touches check passed, and the diff shows only touches paths plus the story folder.
