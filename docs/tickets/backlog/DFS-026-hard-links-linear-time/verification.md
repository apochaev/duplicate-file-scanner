# DFS-026: verification (attempt 1)

## Summary

- **DFS-026.AC-1** met: `regroup` calls `identity.key` once per file. The tests assert 10,000 calls for 10,000 files, 3 for 3, and the exact path-order call list (DFS-004.AC-4).
- **DFS-026.AC-2** met: a `LinkedHashMap` filled in path order keeps copies in first-link order. Tests cover a collapsed link pair, 10,000 single-link copies and the all-links set being dropped.
- **DFS-026.AC-3** met in code, untested on this machine: `fileKey()` is read via `readAttributes` with no open. The test skips on Windows as the ticket expects; Linux/macOS run deferred (decision 6).
- **DFS-026.AC-4** met: the test ran here, not skipped. `createLink` succeeded, the link had an equal key and the copy a different one. Keys are the volume serial plus the 128-bit `FileIdInfo` ID.
- **DFS-026.AC-5** met: an `IOException` is reported as `Skipped(path, UNREADABLE, "hard-link check failed: access denied")` and the rest of the set is still grouped. Asserted exactly.
- **DFS-026.AC-6** met: `FileIdentity` declares only `key(Path)` (asserted by reflection). Two reads of one path give equal keys and hash codes.
- **DFS-026.AC-7** met: the file-scanner and cli test JVMs have native access enabled (asserted), the bootJar manifest has the attribute (asserted). `:cli:run` and `java -jar` printed no scanner warning when I ran them.
- **DFS-026.AC-8** met: a child JVM with `--illegal-native-access=deny` gets `IllegalStateException ... --enable-native-access`. The test ran and passed, and I reproduced it by hand.
- Build: `./gradlew build --rerun-tasks` passed. file-scanner had 191 tests (2 skipped by design: AC-3 and case sensitivity), cli had 63, with 0 failures. All 44 tests that hit the red-gate stub now pass, and none of their assertions was weakened.
- Real runs, reproduced: the synthetic 7,993-file scan has the same `Summary:` as the baseline, and its hard-link phase took about 1 s against 20:55 before. The 10,000-file key benchmark took 378-394 ms on pass 1 and 220-222 ms on pass 2.
- No invariant or design violations found. Minor notes are below; none blocks.
- **Ready for the human gate.**

## Per-criterion detail

### AC-1 (one lookup per file)
`HardLinkDetector.regroup` loops over files sorted by path and calls `identity.key(file.path())` once per file. There is no inner loop, so the cost is linear by construction. Tests:
- `DFS-026.AC-1, DFS-026.AC-2 A large set of ordinary copies...` asserts exactly 10,000 calls.
- `...Hard links collapse into one copy` asserts 3 calls.
- The rewritten `DFS-004.AC-4` test asserts the exact call list `[x/a, x/b, x/c, y/d, y/e]` across two sets. That pins once per file, the path order and set order together, so a count that happened to be right with the wrong paths would still fail.

### AC-2 (same output as the pairwise detector)
Copies come from `LinkedHashMap.computeIfAbsent` in path order, so copy order is first-link order and links stay ascending within a copy. Hash order cannot leak. Fewer than 2 copies means the set is dropped. The pre-existing DFS-004 grouping and order tests (AC-1..AC-8 of DFS-004 in `HardLinkDetectorTest`) are unchanged apart from the fake, and they all pass. They are the "same output as the pairwise detector" evidence. The new tests compare full `PhysicalCopy` lists by value.

### AC-3 (fileKey off Windows)
`NioFileIdentity.key` uses `Files.readAttributes(path, BasicFileAttributes.class).fileKey()` off Windows (and for non-default file systems on Windows). A null key throws `IOException("no file key: ...")`, never a path fallback. That fallback would count hard links as duplicates, so refusing it is the right call. The test asserts key == `fileKey()`, link equal and copy different. It skips on Windows by design (scenario comment), so this branch has not run on Linux/macOS (decision 6 defers it).

### AC-4 (NTFS file ID)
`WindowsFileId` makes these calls: `CreateFileW` (FILE_READ_ATTRIBUTES, share R/W/D, BACKUP_SEMANTICS, `\\?\` / `\\?\UNC\` prefix), `GetFileInformationByHandleEx(FileIdInfo=18)` into a 24-byte buffer, and `CloseHandle` in `finally`. `GetLastError` is captured. The key is a private record of the volume serial and both ID halves. The test was not skipped in this run: the link had an equal key and the `Files.copy` copy a different one. A manual probe gave `Key[volumeSerialNumber=..., fileIdLow=..., fileIdHigh=0]`. For a missing file it gives `NoSuchFileException ...: CreateFileW failed, Win32 error 3`, which is an `IOException`, so AC-5's path applies.

### AC-5 (unreadable file)
The detail is built with `PartialHashFilter.detail(e)` as before, and the test asserts the exact `Skipped` record and the 2 surviving copies. `StageDropReportingTest` (DFS-005.AC-3) is unchanged except that its fake now throws per path rather than per pair, and it passes.

### AC-6 (single method)
Reflection asserts that `getDeclaredMethods()` is exactly `key[interface java.nio.file.Path]`. Equal keys and equal hash codes are asserted on real files.

### AC-7 (native access)
- Build settings: root `Test` `jvmArgs`, cli `applicationDefaultJvmArgs`, and the `bootJar` manifest attribute.
- Tests: `NioFileIdentityTest` (file-scanner JVM) and the plain `NativeAccessTest` (cli JVM plus the manifest read from `-Ddfs.bootJar`) both pass.
- Manual runs: `./gradlew -q :cli:run` stderr carried only Gradle's own `net.rubygrapefruit` warning. `java -jar cli-1.0-SNAPSHOT.jar` printed no warning. Running the probe with no flag prints the JDK's `SymbolLookup::libraryLookup` restricted-method warning, which shows that the flag is what silences it.

### AC-8 (refused native call)
- **What triggers the refusal:** `Natives` catches `IllegalCallerException` from linking inside its static initializer and keeps it, so a later call does not hit `NoClassDefFoundError`. Each `key` call then throws `IllegalStateException(REFUSED_MESSAGE)`. Downcalls map the same exception too.
- **Test:** starts a child JVM with `--illegal-native-access=deny` and no enable flag. It ran (0.1 s, not skipped) and asserts the class name and the flag text in the output. A child that printed `KEY ...` would fail it, so the assertion has teeth.
- **Manual check:** with deny I got `java.lang.IllegalStateException: native access refused; run the JVM with --enable-native-access=ALL-UNNAMED (or the module name)`.
- **Why the scan fails:** `HardLinkDetector` catches only `IOException`, so the scan fails rather than dropping files.

## Test quality

- The story tests assert on behavior: call lists, full `PhysicalCopy` values, exact `Skipped` records, real-file key equality and inequality, and child-JVM output. None restates the implementation.
- **The 44 tests overridden at the red gate.** All pass now, with 0 failures in either project. I compared each rewritten fake with its predecessor:
  - `ScanPipelineTest`: the fake's only use (`calls().isEmpty()` at line 480, "hard-link stage should never be reached") still asserts the same thing. The removed `pairA` field was unused.
  - `StageDropReportingTest`: the fake throws for a configured path, as before, and the assertions are unchanged.
  - `HardLinkDetectorTest`: the DFS-004 AC-1..AC-8 test bodies are unchanged except for the DFS-004.AC-4 re-expression.
- **The DFS-004.AC-4 re-expression.** It changed from "checked against each copy's first link" to "read once per file, in path order, only inside sets". This is a faithful restatement under the new port. The old assertion described a pairwise mechanism that the ticket explicitly removes. The new one still pins that identity is consulted only for set members, in path order within a set and in set order across sets. A weak point: the input lists are already in path order, so the test does not prove that `regroup` sorts. The DFS-004 output-order tests cover the sort.
- **Lost test name.** `NioFileIdentityTest`'s two `DFS-004.AC-9` tests (hard link recognised, identical copy told apart) were replaced by `DFS-026.AC-4`, which asserts the same two facts on Windows (and AC-3 asserts them off Windows). The behavior is still covered, but no test carries the `DFS-004.AC-9` ID any more. That ticket is closed; this is a traceability note only.
- The AC-3 test reads `fileKey()` after calling `key`, as the red-gate order requires, and then compares. That is fine.

## Design conformance

- Matches `design.md`: the port shape, `NioFileIdentity` choosing by OS once and checking `getFileSystem() == FileSystems.getDefault()`, the `WindowsFileId` mechanics, the holder that keeps `IllegalCallerException`, the error mapping (an extension recorded in learnings, still `IOException`) and the build settings.
- Checklist disposition 1-14: each is addressed or not applicable with a reason. Item 3 (`LinkedHashMap`), 8 (dropped sets), 9 (path order), 10 (IOException versus ISE) and 13 (static immutable handles, a confined `Arena` per call) are visible in the code.
- `HardLinkDetector` adds `Objects.requireNonNull` on the returned key, so a contract-breaking adapter fails loudly. That is reasonable.
- CLAUDE.md:
  - The invariant bullet matches decision 5 word for word, and the Commands sentence matches.
  - `grep isSameFile CLAUDE.md` finds nothing.
  - The `FileTreeWalker` paragraph and Current state never named `isSameFile`, so leaving them unchanged is correct.

## Safety invariants

- **Hard links are not duplicates.** This is the subject of the ticket. Both keys follow links, as `isSameFile` did. A failed lookup drops the file and never falls back to path identity, and an empty `fileKey()` (a provider without keys) also drops it rather than treating it as distinct.
- **No symlink or junction following.** Unaffected: the walker never yields links. `CreateFileW` without `FILE_FLAG_OPEN_REPARSE_POINT` follows a link only for a path the walker already yielded as a regular file.
- **Never delete the last copy, re-verify at execution time, preview is a pure query.** Not touched, since no deletion code exists. For later work: the deletion tickets' pairwise check becomes `key(a).equals(key(b))`, as the port Javadoc says.
- **Empty files excluded.** Unchanged, since filtering happens upstream.
- **Concurrency.** There is no per-scan state, and `Natives.INSTANCE` is immutable.

## Scope

- **Changed files:** `CLAUDE.md`, the two build files, `cli/.../NativeAccessTest.java`, the `application`, `infra.port` and `infra.adapter` main and test packages, and the story folder. All are inside `touches`.
- **`NioFileIdentityKeyBenchmark`.** It is in the `infra.adapter` test package, so it is in scope. It is a plain `main` with no `@Test`, modelled on `SizeIndexHeapBenchmark`, and exists to produce a done-item figure. It is a new file, so no frozen test changed, and it does not run in `build`. I judge it acceptable.
- **Minor wording mismatch.** It uses `Files.createTempDirectory` rather than a JUnit `@TempDir`, which is the same thing in practice.

## Real runs (reproduced by the verifier, 2026-10-14, this Windows machine, JDK 25.0.4.1)

- **Benchmark** (`java --enable-native-access=ALL-UNNAMED -cp main;test ...NioFileIdentityKeyBenchmark`, run twice):
  - pass 1: 393.9 ms and 377.7 ms (about 38-39 us per file, including linking)
  - pass 2: 222.2 ms and 219.9 ms (about 22 us per file)
  - 10,000 distinct keys each time
  - These agree with the implementer's 374-378 ms and 209-213 ms.
- **Synthetic folder:** regenerated in the session scratchpad with 7,993 files of 4,096 identical bytes, `._f00000`..`._f07992`, file i in `d<i mod 50>`.
- **Scan** (`./gradlew -q :cli:run --args="<dir> --format=text --progress=log"`): exit 0, wall time 9.6 s including Gradle.
  - Progress lines: `Hashing: ... 0:03`, `Hard links: 1 / 1 sets  0:04`, `Done in 0:04: 7,993 files found, 7,993 compared, 1 duplicate sets`.
  - `Summary: 1 sets, 7,993 files, 31.2 MB reclaimable, 0 skipped`, identical to the baseline.
  - The hard-link phase took about 1 s, against 20:55 at the baseline.
