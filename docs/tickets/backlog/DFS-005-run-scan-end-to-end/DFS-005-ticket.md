---
id: DFS-005
title: Run a scan end to end
status: ready
type: feature
scope: [file-scanner]
archetype: pipeline-stage
priority: high
depends_on: [DFS-004]
touches: [us.pochaev.dfs.scanner.domain.model, us.pochaev.dfs.scanner.application, us.pochaev.dfs.scanner.infra.port, us.pochaev.dfs.scanner.infra.adapter]
blocked_reason:
created: 2026-09-21
updated: 2026-09-27
completed:
---

# DFS-005: Run a scan end to end

## Story

As a caller of the scanner library (the async scan lifecycle in DFS-018, and through it the CLI)
I want one call that walks the start locations and runs every detection stage in order
So that I get the confirmed duplicate sets for those locations, and what was skipped, from a single pipeline I can trust

## Context

Split on 2026-09-27 from the original DFS-005 ("Start scans and wire the factories"), which was too large for one agent. This ticket is the synchronous core. The async lifecycle (`StartScan`, `CancelScan`, `GetScanStatus`, `ListDuplicateSets`, `ScanRepository`, the caller-supplied executor, concurrent scans) moved to DFS-018. The factories, the `module-info` exports and reconsidering `allowEmptyShould(true)` moved to DFS-019.

It composes what DFS-001 to DFS-004 built, in the order of CLAUDE.md design decision 3: the walker (`FileTreeWalker.walk(Path, WalkListener)`, emitting `WalkEvent.FileFound` and `WalkEvent.Skipped`) feeds `SizeIndex.add`; then `SizeIndex.groups()` goes to `PartialHashFilter.filter`, then `FullHashConfirmer.confirm`, then `HardLinkDetector.detect`, which yields the final `List<DuplicateSet>`. Spring-friendliness rules apply: progress goes through a listener and is never printed by the core, and a config record in `domain.model` is what `@ConfigurationProperties` can later map onto. The listener returning `false` is how the walker is told to stop (CLAUDE.md, `FileTreeWalker`), so cancellation reaches this ticket as a stop signal from the caller; the lifecycle that sets it is DFS-018's.

Decisions (refinement, 2026-09-27):

1. **Archetype `pipeline-stage`, stretched.** That archetype describes one stage and excludes use cases. This ticket is neither: it is the service that composes the stages. Its edge-case checklist still fits most items (1 accumulating into `SizeIndex`, 3 deterministic order, 4 immutable results, 6 config defaults, 8 groups of one dropped), and every type here is new, so the red gate works (no existing type is reshaped). The skeletons (`FilterStage`, `Accumulator`) do not fit a composing service; the architect writes its stub freehand. Whether use cases need their own archetype is decided when DFS-018 is refined.
2. **Result: `ScanResult(List<DuplicateSet> sets, List<WalkEvent.Skipped> skipped)` in `domain.model`, covering stage drops too.** `skipped` lists every path left out of the scan and why: the walker's own `Skipped` events, and every file a stage drops on an `IOException` (DFS-002.AC-7, DFS-003.AC-7, DFS-004.AC-8), which DFS-004 left unreported "until DFS-005". A stage drop is reported as `Skipped(path, SkipReason.UNREADABLE, detail)`, with `detail` naming the stage and the exception's message (for example `"full hash failed: Access is denied"`); no new type or reason. `PartialHashFilter`, `FullHashConfirmer` and `HardLinkDetector` each gain an extra constructor taking a sink for these drops. Their existing constructors keep working unchanged, with a no-op sink, so the tests of DFS-002 to DFS-004 stay as they are and still pass. For the red gate, the architect stubs only the new constructors and must not make the existing ones delegate to them (a stubbed delegate would fail those earlier tests as regressions); the implementer may add the delegation once the new constructors are real. A dropped file only ever means fewer copies are reported, never more, so this is about informing the user, not about safety.
3. **Progress: phase events plus progress per group.** A `ScanListener` with one method, `void onEvent(ScanEvent event)`, and a sealed `ScanEvent`, both in `domain.model` (the caller supplies the listener, so it must be exported): `PhaseStarted(ScanPhase phase)` with `ScanPhase` one of `WALK`, `PARTIAL_HASH`, `FULL_HASH`, `HARD_LINKS`; `FileWalked(long filesSoFar)` for each regular file the walker yields; `GroupDone(ScanPhase phase, int done, int total)` after each group a hashing or hard-link phase finishes; `Skipped(WalkEvent.Skipped skipped)` as each skip or stage drop happens. The pipeline feeds each stage one group at a time (every stage is a pure function over its list of groups), which is what makes per-group progress possible without changing the stages again. The core never prints; every progress report goes through the listener.
4. **Config: `ScanConfig(long minSize, int sampleSize, long partialHashThreshold)` in `domain.model`, only the fields this ticket uses.** `ScanConfig.defaults()` returns `(1, 65536, 131072)`, the documented entry point. The canonical constructor rejects `minSize < 1` and a non-positive `sampleSize` or `partialHashThreshold` with `IllegalArgumentException`, so a bad value fails before the walk starts. `minSize` goes to `SizeIndex(long minSize)` (DFS-001), the other two to `PartialHashFilter(hasher, sampleSize, skipThreshold)` (DFS-002). `domain.model` cannot depend on `application`, so `ScanConfig` states its own default values; a test pins them equal to `SizeIndex.DEFAULT_MIN_SIZE`, `PartialHashFilter.DEFAULT_SAMPLE_SIZE` and `PartialHashFilter.DEFAULT_SKIP_THRESHOLD`. Later tickets add their own fields (parallelism in DFS-006, the quarantine folder with deletion). The symlink policy of CLAUDE.md is not a field: the walker never follows links and there is no behaviour to configure.
5. **Start locations: a `List<Path>`, resolved to real paths, equal and nested locations dropped, the rest walked in ascending path order.** Each location is resolved with `Path.toRealPath()`, which on Windows folds case differences (`c:/Photos` and `C:/photos`) and resolves a link or junction given as a location, the same way the walker resolves a root link. Locations equal to another, or inside another, are dropped, so no file is walked twice; the remaining ones are walked one after another, in ascending path order, regardless of the order the caller gave them. Example: `[C:/photos/2024, c:/Photos, D:/backup]` walks `C:/photos` then `D:/backup`. Because `application` is tested against fakes (CLAUDE.md), resolving goes through a new secondary port, `LocationResolver.realPath(Path) throws IOException` in `infra.port`, with `NioLocationResolver` (`Path.toRealPath()`) in `infra.adapter`; its test runs on real files in a `@TempDir`, creating a link root at test time (on Windows a junction via `Fixtures.createJunction`, which needs no administrator rights; elsewhere a symbolic link via `Fixtures.createSymlinkOrSkip`) and skipping through `Assumptions` when that is not possible. JimFS is not a dependency of `file-scanner`.
6. **Bad locations: fail fast before the walk, skip once it is under way.** An empty location list throws `IllegalArgumentException`. A location that cannot be resolved (`NoSuchFileException`) or is not a directory (`NotDirectoryException`, thrown by `LocationResolver.realPath` itself) makes the scan throw that `IOException`, naming the location, before any event is sent and before any location is walked; a CLI user who types `C:/phtos` gets an error, not "0 duplicates". A root whose walk throws after resolution succeeded (a drive unplugged mid-scan) is reported as `Skipped(root, UNREADABLE, detail)`, and the remaining locations are still walked and the scan completes. DFS-018 maps a thrown exception to `FAILED`.
7. **Stopping: a caller-supplied `BooleanSupplier` stop signal; a stopped scan throws `java.util.concurrent.CancellationException` and returns no partial result.** The signal is checked at every walk event (the `WalkListener` then returns `false`, which is how the walker already stops) and before each group in each of the partial-hash, full-hash and hard-link phases. Once it reads `true`, the scan stops at the next check and throws. A partial result is not returned: a walk cut short can leave a set missing copies that exist, which misleads a deletion decision. Thread interruption is deliberately not used: an interrupt closes an NIO `FileChannel` mid-read with `ClosedByInterruptException`, which the stages would drop as `UNREADABLE`, reporting healthy files as unreadable. A single group already being hashed finishes before the stop takes effect. DFS-018's `CancelScan` sets the signal and maps the exception to `CANCELLED`.
8. **Output order: the pipeline's own order (archetype checklist item 3).** `sets` keeps the order the stages leave them in: `SizeIndex.groups()` order (largest file size first, DFS-001; read from `SizeIndex.groups()`, which sorts by `CandidateGroup::size` descending), which every later stage preserves, with paths ascending inside each set. `skipped` is in the order things happened: first the walk's skips, location by location in path order, then each phase's drops in the order its groups were processed; it matches the order of the listener's `Skipped` events. No sorting is added here; a user-facing order (for example most wasted space first) belongs to DFS-018's `ListDuplicateSets`.
9. **One end-to-end test on real files (archetype checklist item 12).** Besides the fake-backed tests, one test class wires the real adapters (`NioLocationResolver`, `NioFileTreeWalker`, `NioContentHasher`, `NioFileIdentity`) into the scan over a `@TempDir` holding: the committed fixtures, copied with `Fixtures.copyInto`; a 200 KB pair generated in code that differs only in its middle byte; a 200 KB identical pair; and a hard link created at test time (the link-specific assertion skipped through `Assumptions` when it cannot be created). It follows the precedent of `PartialHashFilterRealFileTest`; ArchUnit imports main classes only (`DO_NOT_INCLUDE_TESTS`), so a test may use the adapters. `Fixtures` is package-private in the `infra.adapter` test package today: the test either lives in that package or makes `Fixtures` and the methods it uses public (both inside `touches`). Expected sets are derived from the fixtures' real contents (`Fixtures.FILES`), not assumed.
10. **A stateless, reusable service (archetype checklist item 13).** The pipeline's fields are its ports only. All per-scan state (the `SizeIndex`, the skip collector, the phase counters) is created inside the scan call and never escapes it, so one instance can run several scans at once and DFS-019's factory may hand out a singleton. `SizeIndex` (DFS-001, not thread-safe) is used by one thread within one scan. This leaves DFS-006 free to give each walker worker its own `SizeIndex` inside the scan and merge them at the end.

## Acceptance criteria

The full ID of each criterion is `DFS-005.AC-<n>`.

- **AC-1** Scanning a start location returns a `ScanResult` whose `sets` are the confirmed duplicate sets that walking and running every stage in order produce.
- **AC-2** The walker's `Skipped` events for the scan appear in `ScanResult.skipped`.
- **AC-3** A file that `PartialHashFilter`, `FullHashConfirmer` or `HardLinkDetector` drops on an `IOException` appears in `ScanResult.skipped` as `UNREADABLE`, with a detail naming the stage.
- **AC-4** The listener receives, in order: `PhaseStarted(WALK)`, one `FileWalked` per regular file with a count rising by one, then for each of `PARTIAL_HASH`, `FULL_HASH` and `HARD_LINKS` a `PhaseStarted` followed by one `GroupDone` per group entering that phase, `done` rising from 1 to `total`.
- **AC-5** Every path that ends up in `ScanResult.skipped` is also sent to the listener as a `Skipped` event when it is skipped or dropped.
- **AC-6** Running a scan writes nothing to `System.out` or `System.err`.
- **AC-7** `ScanConfig.defaults()` equals `(1, 65536, 131072)`, the same values as `SizeIndex.DEFAULT_MIN_SIZE`, `PartialHashFilter.DEFAULT_SAMPLE_SIZE` and `PartialHashFilter.DEFAULT_SKIP_THRESHOLD`, and a scan applies the config's `minSize`, so files smaller than it never reach a duplicate set.
- **AC-8** `ScanConfig` rejects a `minSize` below 1, and a `sampleSize` or `partialHashThreshold` of zero or less, with `IllegalArgumentException`.
- **AC-9** A scan resolves every start location through `LocationResolver`, drops any location equal to or inside another, and walks the rest once each, in ascending path order.
- **AC-10** `NioLocationResolver.realPath` returns `Path.toRealPath()` for a directory: a location given through a link resolves to its target (tested on real files, skipped through `Assumptions` when the link cannot be created), a missing path throws `NoSuchFileException`, and a regular file throws `NotDirectoryException`.
- **AC-11** A scan over an empty location list throws `IllegalArgumentException`.
- **AC-12** If any start location fails to resolve, the scan throws that `IOException` before sending any event and before walking any location.
- **AC-13** If walking a resolved location throws, that location is reported as `Skipped` with reason `UNREADABLE`, and the remaining locations are still walked.
- **AC-14** When the stop signal reads `true` during the walk, the `WalkListener` returns `false` at the next walk event, no later location is walked, and the scan throws `CancellationException`.
- **AC-15** When the stop signal reads `true` during a hashing or hard-link phase, no further group is passed to a stage and the scan throws `CancellationException`.
- **AC-16** `ScanResult.sets` is in the order `SizeIndex.groups()` produced (largest file size first), and `ScanResult.skipped` is in the order the skips and drops happened, the same order as the listener's `Skipped` events.
- **AC-17** A scan wired with the real NIO adapters over real files finds an identical 200 KB pair as one set, does not group a same-size pair differing only in its middle byte, counts two hard links to one file as one physical copy, and puts no empty file in any set.
- **AC-18** Two scans running at the same time on one pipeline instance each return only the duplicate sets and skips of their own locations.

## Scenarios

```gherkin
Feature: Running a scan end to end

  @AC-1
  Scenario: Identical files in two folders form one duplicate set
    Given a start location "C:/a" holding "C:/a/x/photo.jpg" and "C:/a/y/photo.jpg" with identical 10-byte content, and "C:/a/x/notes.txt" of 30 bytes
    When the scan runs with ScanConfig.defaults()
    Then the result has exactly one duplicate set, with "C:/a/x/photo.jpg" and "C:/a/y/photo.jpg" as two physical copies
    And the skipped list is empty

  @AC-2
  Scenario: A path the walker skips is reported in the result
    Given a start location where the walker reports "root/link" as skipped with reason SYMLINK
    When the scan runs
    Then the result's skipped list contains "root/link" with reason SYMLINK

  @AC-3
  Scenario: A file a stage cannot read is reported in the result
    Given three identical files "a", "b" and "c" whose full hash fails for "c" with "Access is denied"
    When the scan runs
    Then the result has one duplicate set with "a" and "b"
    And the skipped list contains "c" with reason UNREADABLE and a detail naming the full-hash stage

  @AC-14
  Scenario: Stopping during the walk
    Given locations "C:/a" and "D:/b", where "C:/a" holds 3 files and the stop signal turns true after the 2nd file is walked
    When the scan runs
    Then the walk listener returns false for the 3rd walk event
    And "D:/b" is never walked
    And a CancellationException is thrown

  @AC-15
  Scenario: Stopping between groups of the full hash
    Given a location with two duplicate pairs, and a stop signal that turns true once the first full-hash group is done
    When the scan runs
    Then the full-hash stage received exactly one group
    And the hard-link stage received none
    And a CancellationException is thrown

  @AC-4
  Scenario: Progress is reported per phase and per group
    Given a start location with identical 10-byte files "a1" and "a2", identical 20-byte files "b1" and "b2", and a unique 30-byte file "c"
    When the scan runs with a recording listener
    Then the listener receives PhaseStarted(WALK) and FileWalked 1, 2, 3, 4, 5
    And then PhaseStarted(PARTIAL_HASH), GroupDone(PARTIAL_HASH, 1, 2), GroupDone(PARTIAL_HASH, 2, 2)
    And then PhaseStarted(FULL_HASH), GroupDone(FULL_HASH, 1, 2), GroupDone(FULL_HASH, 2, 2)
    And then PhaseStarted(HARD_LINKS), GroupDone(HARD_LINKS, 1, 2), GroupDone(HARD_LINKS, 2, 2)

  @AC-5
  Scenario: Skips reach the listener as they happen
    Given the location from the AC-3 scenario, where the full hash of "c" fails
    When the scan runs with a recording listener
    Then the listener receives a Skipped event for "c" after PhaseStarted(FULL_HASH) and before PhaseStarted(HARD_LINKS)

  @AC-7
  Scenario: The default config matches the stages' own defaults
    When ScanConfig.defaults() is read
    Then it is (1, 65536, 131072)
    And those equal SizeIndex.DEFAULT_MIN_SIZE, PartialHashFilter.DEFAULT_SAMPLE_SIZE and PartialHashFilter.DEFAULT_SKIP_THRESHOLD

  @AC-7
  Scenario: Empty files are excluded by default
    Given a start location with two empty files "e1" and "e2" and identical 10-byte files "a1" and "a2"
    When the scan runs with ScanConfig.defaults()
    Then the result has one duplicate set, with "a1" and "a2"

  @AC-7
  Scenario: A larger minimum size excludes small duplicates
    Given a start location with identical 10-byte files "a1" and "a2" and identical 5000-byte files "b1" and "b2"
    When the scan runs with a minSize of 4096
    Then the result has one duplicate set, with "b1" and "b2"

  @AC-8
  Scenario Outline: Invalid config values are rejected
    When a ScanConfig is built with minSize <minSize>, sampleSize <sampleSize> and partialHashThreshold <threshold>
    Then an IllegalArgumentException is thrown

    Examples:
      | minSize | sampleSize | threshold |
      | 0       | 65536      | 131072    |
      | 1       | 0          | 131072    |
      | 1       | 65536      | 0         |

  @AC-9
  Scenario: Equal and nested start locations are walked once
    Given a resolver that maps "C:/photos/2024" to itself, "c:/Photos" to "C:/photos" and "D:/backup" to itself
    When a scan runs over ["C:/photos/2024", "c:/Photos", "D:/backup"]
    Then the walker is called with "C:/photos" and then "D:/backup", and nothing else

  @AC-9
  Scenario: Locations are walked in path order whatever the order given
    Given a resolver that maps every location to itself
    When a scan runs over ["D:/b", "C:/a"]
    Then the walker is called with "C:/a" and then "D:/b"

  @AC-10
  Scenario: A location given through a link resolves to its target
    Given a folder "target" in a temporary directory and a link "alias" to it, created at test time
    When NioLocationResolver resolves "alias"
    Then the result equals the real path of "target"

  @AC-10
  Scenario: A missing path or a file is not a valid location
    Given a temporary directory holding a regular file "f.txt" and no entry "missing"
    When NioLocationResolver resolves "missing"
    Then a NoSuchFileException is thrown
    And resolving "f.txt" throws a NotDirectoryException

  @AC-11
  Scenario: An empty location list is rejected
    When a scan runs over an empty list of locations
    Then an IllegalArgumentException is thrown

  @AC-12
  Scenario: A mistyped location fails the scan before anything runs
    Given a resolver that resolves "C:/a" and throws NoSuchFileException for "C:/phtos"
    When a scan runs over ["C:/a", "C:/phtos"] with a recording listener
    Then a NoSuchFileException naming "C:/phtos" is thrown
    And the walker was never called
    And the listener received no events

  @AC-13
  Scenario: A location that fails mid-scan is skipped and the rest still run
    Given locations "C:/a" and "D:/usb", both resolving, where walking "D:/usb" throws "The device is not ready"
    And "C:/a" holds identical files "x1" and "x2"
    When the scan runs
    Then the result has one duplicate set, with "x1" and "x2"
    And the skipped list contains "D:/usb" with reason UNREADABLE

  @AC-16
  Scenario: Result order follows the pipeline and the order of events
    Given a location with identical 10-byte files "a1", "a2" and "a3", identical 20-byte files "b1", "b2" and "b3", and a link "l" the walker skips
    And the full hash of "b3" fails
    When the scan runs with a recording listener
    Then the result's sets are, in order, the set of "b1", "b2" (20 bytes) and then the set of "a1", "a2", "a3" (10 bytes)
    And the skipped list is "l" and then "b3"
    And the listener's Skipped events name "l" and then "b3"

  @AC-17
  Scenario: The real pipeline on real files
    Given a temporary directory holding the committed fixtures
    And "big/same1.bin" and "big/same2.bin", identical 200 KB files generated in code
    And "big/near1.bin" and "big/near2.bin", 200 KB files identical except for the middle byte
    And "big/link.bin", a hard link to "big/same1.bin" created at test time
    When a scan runs over the directory with the real adapters and ScanConfig.defaults()
    Then one set holds "big/same1.bin", "big/link.bin" and "big/same2.bin", with "same1.bin" and "link.bin" as one physical copy and "same2.bin" as another
    And no set holds "big/near1.bin" or "big/near2.bin"
    And no set holds an empty file

  @AC-18
  Scenario: Two concurrent scans on one instance do not share state
    Given one pipeline instance with a fake walker where "C:/a" holds identical files "a1" and "a2" and a skipped link "la", and "D:/b" holds identical files "b1" and "b2" and a skipped link "lb"
    And the fake walker blocks each walk until both have started
    When a scan over "C:/a" and a scan over "D:/b" run at the same time on two threads
    Then the first result has one set with "a1" and "a2" and skipped only "la"
    And the second result has one set with "b1" and "b2" and skipped only "lb"

  @AC-6
  Scenario: The core prints nothing
    Given System.out and System.err are captured
    When a scan runs over a location with duplicates and a skipped path
    Then nothing was written to either stream
```

## Scope

**Touches:**
- `us.pochaev.dfs.scanner.domain.model`: `ScanConfig`, `ScanResult`, `ScanListener`, `ScanEvent`, `ScanPhase`
- `us.pochaev.dfs.scanner.application`: the scan pipeline service; a drop-sink constructor on `PartialHashFilter`, `FullHashConfirmer` and `HardLinkDetector`
- `us.pochaev.dfs.scanner.infra.port` and `.adapter`: `LocationResolver` and `NioLocationResolver`; in the `infra.adapter` test package, `Fixtures` may be made public for the end-to-end test

**Out of scope:**
- `StartScan`, `CancelScan`, `GetScanStatus`, `ListDuplicateSets`, `ScanRepository`, executors, concurrent scans (DFS-018)
- `ScannerFactories`, `module-info` exports, `allowEmptyShould(true)` (DFS-019)
- Parallel walking (DFS-006)
- Location overlaps, deletion plans, quarantine and undo/purge
- Any CLI or Spring wiring in the `cli` subproject

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] No files changed outside the paths listed under Touches
- [ ] The tests of DFS-001 to DFS-004 and DFS-017 are unchanged and pass
- [ ] Every item of the `pipeline-stage` edge-case checklist is addressed in the design doc or marked not applicable with a one-line reason
- [ ] Links in the real-file tests (AC-10, AC-17) are created at test time and skipped through `Assumptions` when the platform does not allow them; nothing platform-specific is committed to `fixtures/`
- [ ] `module-info.java` is unchanged: every new exported type is in `domain.model`, which is already exported, and `ModuleExportsTest` stays green

## Open questions

None.
