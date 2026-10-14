---
id: DFS-026
title: Detect hard links in linear time per duplicate set
status: in-review
type: fix
scope: [file-scanner, cli, build]
archetype: pipeline-stage
models: {implementer: {model: opus, effort: high}}
priority: high
depends_on: [DFS-024]
touches: [CLAUDE.md, build.gradle.kts, cli/build.gradle.kts, us.pochaev.dfs.scanner.application, us.pochaev.dfs.scanner.infra.port, us.pochaev.dfs.scanner.infra.adapter, cli/src/test/java/us/pochaev/dfs/cli/NativeAccessTest.java]
blocked_reason:
created: 2026-10-13
updated: 2026-10-14
completed:
---

# DFS-026: Detect hard links in linear time per duplicate set

## Story

As a user scanning large media drives
I want the hard-link phase to finish in time proportional to the number of files
So that a scan with large sets of identical files does not stall for hours after hashing is done

## Context

`HardLinkDetector.regroup` (DFS-004, DFS-017) takes the files of a set in path order and checks each with `FileIdentity.isSameFile(file, firstLink)` against the first link of every copy formed so far. When the files are ordinary copies (no hard links, the usual case), every file starts a new copy, so a set of n files costs about n²/2 calls, and `NioFileIdentity` (`Files.isSameFile`) opens both files on every call on Windows. The pairwise port exists because `BasicFileAttributes.fileKey()` is null on Windows (CLAUDE.md, "Hard links are not duplicates").

Observed on 2026-10-13 (`C:/Media D:/Media E:/Media F:/Media`, 7.3 TB, 700,488 files, 11,590 duplicate sets): hashing finished at 3:00:29; the `Hard links:` line reached `7,163 / 11,590 sets` by 3:02:31 and then stopped moving for at least 5 minutes. A thread dump showed the scan inside `HardLinkDetector.regroup` → `Files.isSameFile`, and the process was making about 323,000 file-system operations per second with no data reads: one very large set of identical files. The scan completed at `Done in 3:18:40`, so the hard-link phase took 18:11 (hashing ended at 3:00:29). The report shows the cause: the largest sets were 7,993 copies of a 4.0 KB macOS AppleDouble file (`._*` in a `.photoslibrary`), about 32 million `isSameFile` calls on their own, and 4,920 copies of `._.DS_Store`; every other set had at most 300 copies. None of the 46,567 files in sets was a hard link.

The progress line shows nothing during such a set, because `GroupDone` is sent only per finished set (DFS-024), so the scan looks frozen.

The scanner is meant to run on Windows, Linux and macOS.

**Decisions:**

1. **Identity source: a key per file, grouped, on every platform.** Each file's identity is read once and the files of a set are grouped by it, so a set of n files costs n lookups, not about n²/2 comparisons. Where `BasicFileAttributes.fileKey()` is not null (Linux and macOS, where it holds the device and inode numbers and has value `equals`/`hashCode`), that is the key, with no native code. Where it is null (Windows), the key is the 64-bit volume serial number and the 128-bit file ID from `GetFileInformationByHandleEx(FileIdInfo)`, called through the FFM API (final since Java 22), one open per file; `FileIdInfo` rather than `GetFileInformationByHandle`, whose 64-bit file index is not guaranteed unique on ReFS. Rejected: pre-grouping by timestamps (Windows directory entries can show stale times for one of two links, so links could be counted as duplicates) and a link-count filter in front of `isSameFile` (same native code, still quadratic for real hard-link trees). Copy-on-write clones (APFS, btrfs/XFS reflinks, ReFS block cloning) are distinct files with distinct keys and stay out of scope; network shares that synthesize IDs are as unreliable as with `isSameFile` today.
2. **Port shape: `FileIdentity` becomes one method, `Object key(Path path) throws IOException`,** replacing `isSameFile`. The key is opaque, with the `fileKey()` contract: two paths reach the same physical file exactly when their keys are equal (`equals`/`hashCode`). The Windows lookup returns a private record of the volume serial number and the two halves of the 128-bit file ID; elsewhere the key is `fileKey()` itself. A later pairwise check (the deletion tickets' "is the candidate the keeper?") is `key(a).equals(key(b))`. Rejected: a typed `FileKey` wrapper (a type with no behavior, guarding against little with two implementers and the test fakes) and keeping `isSameFile` beside the key (two ways to do one thing, the pairwise one being the cause of this bug). The test fakes of `FileIdentity` (in `HardLinkDetectorTest`, `ScanPipelineTest`, `StageDropReportingTest`, and `NioFileIdentityTest` for the adapter) change from answering pairs to handing out keys.
3. **Native access: enabled in every launcher; a refusal fails the scan with a clear error, no fallback.** Since Java 24 the first native call prints a "restricted method" warning on stderr unless the launcher allows it, and a later release will block it by default. All current launchers run the scanner on the classpath, so the value is `ALL-UNNAMED`: `application { applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED") }` in `cli/build.gradle.kts` (`:cli:run` and the start scripts), the attribute `Enable-Native-Access: ALL-UNNAMED` in the `bootJar` manifest (`java -jar`), and `jvmArgs("--enable-native-access=ALL-UNNAMED")` on every `Test` task in the root `build.gradle.kts` `subprojects {}` block. If the Windows lookup is refused anyway (`IllegalCallerException`, e.g. the library embedded without the flag), `key` throws an unchecked `IllegalStateException` whose message names `--enable-native-access`, so the scan fails (exit 1, `error: scan failed: ...`) rather than every file being dropped as unreadable (an `IOException` would hit AC-5's path). Gradle's own startup warning (from `net.rubygrapefruit`, in the Gradle JVM) is unrelated and stays. Rejected: falling back to pairwise `Files.isSameFile` (a silent return to the quadratic path decision 2 removes) and leaving the warning (noise next to the progress line, and breakage once Java denies by default).
4. **The `Hard links:` line keeps counting sets.** The stall came from quadratic work, not from the display: once lookups are linear, the 7,993-file set is 7,993 opens on Windows (metadata likely still cached from hashing) and none on Linux and macOS, so seconds, not minutes. `GroupDone`, `ScanEvent` and `ProgressDisplay` stay unchanged. If a real run still shows a visible pause inside one set, a file-level count belongs to DFS-025 (progress accuracy). Rejected: counting files in this ticket (touches `domain.model`, `application` and `cli`, and reopens a format DFS-024 just settled) and a separate ticket now (the need is unproven after the fix).
5. **CLAUDE.md: the invariant is rewritten and Commands gains a native-access sentence.** The "Hard links are not duplicates" bullet becomes: "**Hard links are not duplicates.** Paths that are the same physical file must not count as copies of each other. Identify them by a key per file (`FileIdentity.key`), grouped within a candidate set: `fileKey()` (device and inode) on Linux and macOS; on Windows, where `fileKey()` is null (verified), the volume serial number and 128-bit file ID from `GetFileInformationByHandleEx(FileIdInfo)` through the FFM API. Never compare pairwise: a set of n plain copies would cost n²/2 checks (DFS-026)." Commands gains: "The scanner calls native code on Windows; every launcher passes `--enable-native-access=ALL-UNNAMED` (or the `Enable-Native-Access` manifest attribute), and an embedding app must do the same." The `FileTreeWalker` paragraph and Current state are updated where they name `isSameFile`. Rejected: the invariant only (the planned server would meet the flag first through AC-8's error).
6. **Real runs: Windows now, Linux and macOS deferred.** The stall is reproduced without a 3-hour scan by a synthetic folder shaped like the real set: 7,993 byte-identical files of 4,096 bytes named `._f00000` to `._f07992`, spread over 50 subfolders `d00` to `d49` (file i in `d<i mod 50>`), generated by a script, on this machine's C: drive. Before the change it is scanned once with the current code (`--format=text --progress=log`) and its `Summary:` line and `Hard links:` timing are recorded in this ticket; after the change, the same scan must report the same `Summary:` and finish the hard-link phase in under a minute. (Updated 2026-10-13: the real folder `E:/Media/iPhone/2018-03 Photoslibrary ` cannot be passed: its name ends in a space, which `Path.of` on Windows rejects with `InvalidPathException`, also as a middle component; its 8.3 short name `2018-0~1` fails as `NOT_A_DIRECTORY`; and its parent `E:/Media/iPhone` holds 891.9 GB, hours of hashing.) A real run on Linux or macOS is deferred until the user has such a machine (WSL is not installed here); until then the `fileKey()` branch is covered only by AC-3's test, which skips on Windows. Rejected now: installing WSL and a JDK on this machine.
7. **Archetype `pipeline-stage`, with the implementer on Opus.** DFS-004 built this stage under the same archetype, which names hard-link detection among its stages. The `models` override is for the native FFM lookup and the child-JVM test (AC-8), harder than usual stage work. Rejected: `none` (no independent verifier on a change to a safety invariant).

**Pipeline-stage checklist** (`.claude/archetypes/pipeline-stage/archetype.md`):

1. Accumulation and merging: not applicable; the stage consumes `FullHashConfirmer`'s finished `List<DuplicateSet>`, as in DFS-004.
2. Inter-stage currency invariants: unchanged; `DuplicateSet` and `PhysicalCopy` keep their constructors' checks.
3. Deterministic output order: unchanged and pinned by AC-2: sets in input order, files ascending by path, copies ordered by their first link, links ascending inside a copy. Grouping by key must not let hash-map iteration order leak into the output.
4. Immutable results: unchanged; returned sets and lists stay immutable.
5. Repeated input: not applicable here; equal paths already collapse in `SizeIndex` (DFS-001), so one path cannot reach this stage twice. (If it did, its two entries would share a key and form one copy, not a false duplicate.)
6. Configuration: none; the stage has no tunable.
7. Performance, measured: the stage holds one set at a time, so memory is not measured; time is. AC-1 bounds the lookups; the real-run done-items record the hard-link phase before and after on the 7,993-copy folder, and a done-item records the time of `NioFileIdentity.key` over 10,000 real files in a `@TempDir` on this Windows machine.
8. Groups of one are dropped: yes, a set left with fewer than two copies is dropped (AC-2, third scenario).
9. Path-order processing: files are still taken in ascending path order; no content is read, so there is no size threshold.
10. Failure mid-stage: AC-5 (an `IOException` drops the file, reported through `onDrop` as `UNREADABLE`, "hard-link check failed: ..."); AC-8 (a refused native call fails the scan instead).
11. New secondary ports: none new; `FileIdentity` changes shape (decision 2). `HardLinkDetector` is tested against a fake that hands out keys; only `NioFileIdentity` is tested against real files.
12. Large and near-duplicate fixtures: not applicable; no content is read.
13. Reused across concurrent scans: `HardLinkDetector` and `NioFileIdentity` stay stateless; the Windows lookup holds no per-scan state (a static FFM method handle is shared and thread-safe).
14. Input/output currency: `List<DuplicateSet>` in and out, as in DFS-004.

Tests of DFS-004 and DFS-005 that fake `FileIdentity` or assert the order of `isSameFile` calls (`HardLinkDetectorTest`, `ScanPipelineTest`, `StageDropReportingTest`, `NioFileIdentityTest`) are rewritten to keys; their criteria (DFS-004's groupings, DFS-005.AC-3's drop reporting) must still hold.

## Acceptance criteria

The full ID of each criterion is `DFS-026.AC-<n>`.

- **AC-1** `HardLinkDetector` asks the identity port once per file of a set, so a set of n files takes n lookups whatever its copies and links.
- **AC-2** Paths that are hard links of one file get equal keys and form one physical copy; distinct files get different keys and form separate copies; the output (sets, copies, link order, dropped sets) is the same as the pairwise detector's for the same input.
- **AC-3** Where `fileKey()` is not null (Linux, macOS), the adapter's key is the `fileKey()` of the path, read without opening the file.
- **AC-4** On Windows, two hard links to one NTFS file get equal keys and a byte-identical copy of it gets a different key, from the volume serial number and the 128-bit file ID.
- **AC-5** A file whose key cannot be read is dropped from its set and reported as `Skipped(path, UNREADABLE, "hard-link check failed: <message>")`, as today (DFS-005.AC-3); the rest of the set is still grouped.
- **AC-6** `FileIdentity` has the single method `key(Path)`, with no pairwise method; `NioFileIdentity` returns equal keys for one path read twice.
- **AC-7** The test JVMs of both projects and `:cli:run` run with native access enabled for the unnamed module (`Module.isNativeAccessEnabled()` is true for the module of `NioFileIdentity`), and the `bootJar` manifest carries `Enable-Native-Access: ALL-UNNAMED`.
- **AC-8** On Windows, when native access is refused, `NioFileIdentity.key` throws `IllegalStateException` whose message contains `--enable-native-access`, not `IOException`, so the scan fails instead of dropping files.

## Scenarios

```gherkin
Feature: Linear-time hard-link detection

  @AC-1 @AC-2
  Scenario: A large set of ordinary copies takes one lookup per file
    Given a duplicate set of 10,000 files of 4,096 bytes, none hard-linked, and a fake identity port that counts calls
    When the hard-link stage regroups the set
    Then the port was asked 10,000 times
    And the set has 10,000 copies of one link each

  @AC-1 @AC-2
  Scenario: Hard links collapse into one copy
    Given a duplicate set of a.mp4, b.mp4 and c.mp4, where the fake port gives a.mp4 and c.mp4 the same key
    When the hard-link stage regroups the set
    Then the port was asked 3 times
    And the set has 2 copies: [a.mp4, c.mp4] and [b.mp4]

  @AC-2
  Scenario: A set that is all links of one file is dropped
    Given a duplicate set of a.mp4 and b.mp4 with the same key
    When the hard-link stage regroups the set
    Then the set is not in the output

  @AC-3
  Scenario: Linux and macOS use the file key
    Given a regular file x.bin and a hard link y.bin to it, on a platform where fileKey() is not null
    When the adapter reads the keys of x.bin, y.bin and a copy z.bin
    Then x.bin and y.bin have equal keys equal to their fileKey()
    And z.bin has a different key
    # skipped on Windows (Assumptions)

  @AC-4
  Scenario: Windows reads the NTFS file ID
    Given on Windows a file x.bin in a temporary folder, a hard link y.bin to it made with Files.createLink, and a byte-identical copy z.bin
    When the adapter reads the keys of x.bin, y.bin and z.bin
    Then x.bin and y.bin have equal keys
    And z.bin has a different key
    # skipped off Windows, or where the file system refuses hard links (Assumptions)

  @AC-6
  Scenario: The port hands out keys
    Given a regular file x.bin
    When NioFileIdentity reads the key of x.bin twice
    Then the two keys are equal and have equal hash codes
    And FileIdentity declares key(Path) as its only method

  @AC-7
  Scenario: Native access is enabled where the scanner runs
    Given the test JVM started by Gradle
    When a test reads NioFileIdentity.class.getModule().isNativeAccessEnabled()
    Then it is true
    And the manifest of the jar built by bootJar has Enable-Native-Access: ALL-UNNAMED

  @AC-8
  Scenario: A refused native call fails loudly
    Given on Windows a child JVM started on the test classpath with --illegal-native-access=deny and no --enable-native-access
    When it reads the key of a regular file through NioFileIdentity
    Then it gets an IllegalStateException whose message contains "--enable-native-access"
    # skipped off Windows (Assumptions)

  @AC-5
  Scenario: An unreadable file is dropped, the rest grouped
    Given a duplicate set of a.mp4, b.mp4 and c.mp4, where the fake port throws IOException "access denied" for b.mp4
    When the hard-link stage regroups the set
    Then b.mp4 is reported as Skipped(b.mp4, UNREADABLE, "hard-link check failed: access denied")
    And the set has 2 copies: [a.mp4] and [c.mp4]
```

## Scope

**Touches:**
- `infra.port` (`FileIdentity`): `isSameFile` replaced by `key(Path)`
- `infra.adapter` (`NioFileIdentity` and a Windows FFM helper beside it): the key lookup; `NioFileIdentityTest`
- `CLAUDE.md`: the hard-link invariant, a native-access sentence in Commands, Current state (decision 5)
- `build.gradle.kts` (root, `subprojects {}` `Test` tasks) and `cli/build.gradle.kts` (`applicationDefaultJvmArgs`, `bootJar` manifest): native access
- `application` (`HardLinkDetector`): group by key; its test and the other `FileIdentity` fakes (`ScanPipelineTest`, `StageDropReportingTest`)
- `cli/src/test/java/us/pochaev/dfs/cli/NativeAccessTest.java` (new): AC-7's check of the cli test JVM and the `bootJar` manifest (added 2026-10-13 at the design phase: the frontmatter `touches` was left empty at refinement and is now filled from this section)

**Out of scope:**
- Hashing and sampling speed (the user has deferred speed work).
- The ETA and percentage of the `Hashing:` line (DFS-025).
- Progress inside one set on the `Hard links:` line (decision 4; DFS-025 if a real run shows the need).
- A real run on Linux or macOS (decision 6, deferred until such a machine is available).
- Copy-on-write clones (APFS, btrfs/XFS reflinks, ReFS block cloning), which share blocks without being hard links.

## Definition of done

- [x] Every scenario above is an automated test and passes
- [x] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [x] No files changed outside the paths listed under Touches
- [x] CLAUDE.md carries decision 5's invariant wording and native-access sentence, and no longer tells readers to use `Files.isSameFile`
- [x] Every item of the pipeline-stage checklist is addressed in `design.md` or marked not applicable with a one-line reason
- [x] The time of `NioFileIdentity.key` over 10,000 real files in a `@TempDir` on Windows is recorded in this ticket: 2026-10-14, JDK 25.0.4.1, `NioFileIdentityKeyBenchmark` (10,000 files of 4,096 bytes in a temp dir), verifier's runs: pass 1 393.9 / 377.7 ms (~38 us/file, handle linking included), pass 2 222.2 / 219.9 ms (~22 us/file), 10,000 distinct keys
- [x] Baseline recorded before the change: `--format=text --progress=log` scan of the synthetic 7,993-file folder (decision 6) with the current code, its `Summary:` line and the time its `Hard links:` phase took: 2026-10-13 at commit `6abe6c9`, exit 0; walk, sampling and hashing done by 0:00, then `Hard links: 1 / 1 sets  20:55` and `Done in 20:55: 7,993 files found, 7,993 compared, 1 duplicate sets`; `Summary: 1 sets, 7,993 files, 31.2 MB reclaimable, 0 skipped`. So the hard-link phase took about 20 minutes 55 seconds for one set of 7,993 files.
- [x] The same scan after the change reports the same `Summary:` line and its `Hard links:` phase takes under a minute; both times recorded here: 2026-10-14 at commit `98a52d4` (verifier's run, folder regenerated), exit 0; `Hashing: ... 0:03`, `Hard links: 1 / 1 sets  0:04`, `Done in 0:04: 7,993 files found, 7,993 compared, 1 duplicate sets`; `Summary: 1 sets, 7,993 files, 31.2 MB reclaimable, 0 skipped` (same as the baseline). Hard-link phase about 1 second (baseline 20:55); stderr carries no scanner native-access warning

## Open questions

None.
