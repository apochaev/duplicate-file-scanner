# DFS-026: Detect hard links in linear time per duplicate set — design

## Summary

The hard-link stage stops comparing pairwise: `FileIdentity` hands out one opaque key per path, and `HardLinkDetector` groups each set's files by key (one lookup per file). The key is `fileKey()` off Windows and a volume-serial + 128-bit file-ID record from FFM on Windows. Every story test reaches a `not implemented: DFS-026` stub before any assertion or assumption.

- **AC-1** one lookup per file: `HardLinkDetector.regroup` (stub) calls `identity.key(path)` once per file, in path order. Test: `detect` with a counting fake hits the stub.
- **AC-2** same output as the pairwise detector: equal keys form one copy (`LinkedHashMap` filled in path order, copies by first link); sets under two copies dropped. Test: `detect` hits the stub.
- **AC-3** `fileKey()` off Windows: `NioFileIdentity.key` (stub). Test calls `key` on a temp file **before** the "fileKey() not null" assumption, so it fails on the stub on Windows too.
- **AC-4** NTFS file ID: package-private `WindowsFileId.key` (stub), `record Key(volumeSerialNumber, fileIdLow, fileIdHigh)`. Test calls `key` before the Windows and `createLink` assumptions.
- **AC-5** unreadable file: `IOException` from `key` goes to `onDrop` as `Skipped(path, UNREADABLE, "hard-link check failed: <detail>")`, rest of the set grouped. Test: `detect` hits the stub.
- **AC-6** single method: `FileIdentity` declares only `Object key(Path) throws IOException` (already in place). Test reads a key twice **first**, then checks equality, hash codes and the declared methods.
- **AC-7** native access: the three build settings are in place now (root `Test` `jvmArgs`, cli `applicationDefaultJvmArgs`, `bootJar` manifest `Enable-Native-Access: ALL-UNNAMED`; manifest verified). Story test in `file-scanner` calls `key` on a temp file first, then asserts `isNativeAccessEnabled()`. The cli test-JVM and manifest checks are a plain test with no story ID, `cli/src/test/java/us/pochaev/dfs/cli/NativeAccessTest.java` (test-author), green at the red gate.
- **AC-8** refused native call: `WindowsFileId` maps `IllegalCallerException` to `IllegalStateException` naming `--enable-native-access`. Test calls `key` in-process first, then the Windows assumption and the child JVM.

Placement: port in `infra.port`, adapter plus FFM helper in `infra.adapter`, stage in `application`. No `domain.model` changes, no new exports, `module-info.java` unchanged.

Not applicable: checklist 1 (finished input list), 5 (paths already unique since DFS-001), 6 (no tunable), 12 (no content read).

**Needs a gate decision: pre-existing tests will fail at the red gate.** `RedCheck` treats every non-story failure as a regression, and any existing test that forms a duplicate set reaches the `regroup` stub (through the real adapter, also the `key` stub). Likely affected: `HardLinkDetectorTest`, `ScanPipelineTest`, `StageDropReportingTest`, `NioFileIdentityTest`, `ScanPipelineEndToEndTest`, `ScanServiceRealThreadTest`, `ScannerFactoriesTest`, `ScanProgressSnapshotTest` (file-scanner), `ScanCommandTest`, `ScanRunnerTest` (cli). A throwing stub cannot avoid this when an existing, wired stage is reshaped; options in Open design notes.

## Public API

- `FileIdentity` in `infra.port`: `Object key(Path path) throws IOException`. It replaces `boolean isSameFile(Path, Path)` and is the interface's only method. The key is opaque and follows the `fileKey()` contract (equal exactly when same physical file; value `equals`/`hashCode`). It throws `NullPointerException` for a null path.
- `NioFileIdentity` in `infra.adapter` (public final, unchanged constructor): `Object key(Path path) throws IOException`. It throws `IllegalStateException` (message contains `--enable-native-access`) when Windows refuses native access.
- `WindowsFileId` in `infra.adapter` (package-private final, no instances): `static Object key(Path path) throws IOException`, with a private `record Key(long volumeSerialNumber, long fileIdLow, long fileIdHigh)`.
- `HardLinkDetector` in `application`: constructors and `List<DuplicateSet> detect(List<DuplicateSet>)` unchanged. The private `regroup(DuplicateSet)` is re-stubbed and its algorithm documented in its Javadoc.

Unchanged: `ScanPipeline`, `ScannerFactories` (still `new NioFileIdentity()`), every `domain.model` type, `module-info.java`, every `cli` source.

## Layer placement

- `application`: `HardLinkDetector` groups by key.
- `domain.model` (exported, added to `module-info.java`): none.
- `infra.port` / `infra.adapter`: `FileIdentity` (reshaped), implemented by `NioFileIdentity`, which delegates to `WindowsFileId` on Windows. `java.lang.foreign` is in `java.base`, so `module-info` needs no `requires`.
- Build: root `build.gradle.kts` (`Test` tasks) and `cli/build.gradle.kts` (`application`, `bootJar`, `test`), done in this design phase; see Configuration.
- `CLAUDE.md`: decision 5's wording, written by the implementer. It covers the invariant bullet, the Commands sentence, the `FileTreeWalker` paragraph and Current state wherever they name `isSameFile`.

## Checklist disposition

1. Accumulation and merging: not applicable: the stage consumes `FullHashConfirmer`'s finished `List<DuplicateSet>` (as in DFS-004).
2. Inter-stage currency invariants: unchanged. `DuplicateSet` and `PhysicalCopy` keep their constructors' checks.
3. Deterministic output order: sets in input order; files ascending by path; copies ordered by their first link; links ascending inside a copy. Grouping uses a `LinkedHashMap<Object, List<FileEntry>>` filled in path order, so hash order never leaks into the output (AC-2).
4. Immutable results: unchanged. `PhysicalCopy`/`DuplicateSet` take immutable copies, and `detect` returns `List.copyOf`.
5. Repeated input: not applicable: equal paths collapse in `SizeIndex` (DFS-001). Even if one path arrived twice, both entries would share a key and form one copy, not a false duplicate.
6. Configuration and its defaults: not applicable: the stage has no tunable.
7. Memory or performance, measured: memory is not measured because the stage holds one set at a time. Time is covered three ways. AC-1 bounds lookups to n per set. The real-run done-items record the hard-link phase before (20:55, recorded) and after (must be under a minute) on the 7,993-file folder. A done-item records `NioFileIdentity.key` over 10,000 real files in a `@TempDir` on this Windows machine. These figures go in the ticket.
8. Groups of one are dropped: yes, a set with fewer than two copies left (all links of one file, or drops) returns null and is removed (AC-2, third scenario).
9. Path-order processing: files are still taken in ascending path order. No content is read, so there is no size threshold.
10. Failure mid-stage: an `IOException` from `key` drops the file, reported via `onDrop` as `Skipped(path, UNREADABLE, "hard-link check failed: " + PartialHashFilter.detail(e))`, and the rest of the set is grouped (AC-5). An `IllegalStateException` (refused native access) is not caught, so the scan fails with exit 1 (AC-8).
11. New secondary ports: none new. `FileIdentity` changes shape (decision 2). `HardLinkDetector` is tested against a key-handing fake; only `NioFileIdentity` is tested against real files.
12. Large and near-duplicate fixtures: not applicable: no content is read.
13. Reused across concurrent scans: `HardLinkDetector`, `NioFileIdentity` and `WindowsFileId` hold no per-scan state. The FFM method handles are static, immutable and thread-safe, and each call uses its own confined `Arena`.
14. Input/output currency named: `List<DuplicateSet>` in and out, as in DFS-004.

## Configuration

- Tunable(s) this stage reads, their default, and where they are enforced: none.
- Native access (AC-7), added in this design phase so the build is ready before the tests:
  - Root `build.gradle.kts`, `subprojects { tasks.withType<Test>().configureEach { jvmArgs("--enable-native-access=ALL-UNNAMED") } }`.
  - `cli/build.gradle.kts`: `application { applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED") }` and `tasks.bootJar { manifest { attributes("Enable-Native-Access" to "ALL-UNNAMED") } }` (checked: the built jar's manifest carries the attribute).
  - `cli/build.gradle.kts`, for `NativeAccessTest`: `tasks.test` depends on `bootJar` and passes `-Ddfs.bootJar=<absolute path of the bootJar archive>` through a lazy `jvmArgumentProviders` entry. The property name `dfs.bootJar` is the contract the test reads.

## Open design notes

- **Platform branch in `NioFileIdentity.key`.** On Windows (decided once, e.g. a static `os.name` check), for a path whose `getFileSystem()` is `FileSystems.getDefault()`, it calls `WindowsFileId.key`. Otherwise it uses `Files.readAttributes(path, BasicFileAttributes.class).fileKey()` (links followed, like `isSameFile` today).
  - A null `fileKey()` (a provider without keys) throws `IOException("no file key: <path>")`, so the file is dropped and reported. It never falls back to the path as key, which would count hard links as duplicates.
  - Deciding by OS rather than "try `fileKey()` first" avoids one extra attribute read per file on Windows, where it is always null (verified).
- **`WindowsFileId` mechanics, for the implementer.** Per call:
  - `CreateFileW(path, FILE_READ_ATTRIBUTES (0x80), FILE_SHARE_READ|WRITE|DELETE, NULL, OPEN_EXISTING, FILE_FLAG_BACKUP_SEMANTICS (0x02000000), NULL)`. Do not set `FILE_FLAG_OPEN_REPARSE_POINT`, so links are followed, consistent with `fileKey()`.
  - Then `GetFileInformationByHandleEx(h, FileIdInfo = 18, buf, 24)`, where `FILE_ID_INFO` is a `ULONGLONG` volume serial followed by 16 ID bytes. Read the halves as two `JAVA_LONG` at offsets 8 and 16.
  - `CloseHandle` in `finally`.
  - Capture `GetLastError` with `Linker.Option.captureCallState("GetLastError")`. A failed open or query throws `IOException` naming the path and the Win32 error code.
  - Pass the absolute path as a NUL-terminated UTF-16LE string, with the `\\?\` (or `\\?\UNC\`) prefix so paths of 260+ characters work.
  - Load `kernel32` with `SymbolLookup.libraryLookup("kernel32", Arena.global())`.
  - Initialise the handles lazily in a holder class that is only touched on Windows. **Catch `IllegalCallerException` inside the holder's initialisation and keep it**, rather than letting it escape a static initializer, which would surface as `ExceptionInInitializerError` and then `NoClassDefFoundError`. Every `key` call then throws `IllegalStateException("native access refused; run the JVM with --enable-native-access=ALL-UNNAMED (or the module name)", cause)`.
  - Check whether `--illegal-native-access=deny` raises on lookup/linking or on the first downcall, and map it on both paths.
- **Test files named for the test-author.** `HardLinkDetectorTest`, `ScanPipelineTest`, `StageDropReportingTest` (fakes now hand out keys: default key = the path itself, configurable equal keys, configurable `IOException`, a call counter) and `NioFileIdentityTest` (AC-3, AC-4, AC-6, AC-7's module check, AC-8), all under `file-scanner/src/test/java/us/pochaev/dfs/scanner/`. `ScanServiceRealThreadTest` and `ScanPipelineEndToEndTest` use the real `NioFileIdentity` and need no source change. `compileTestJava` fails until the four fakes are rewritten, because the port reshape is the design itself.
- **Red-gate order for every story test (design gate feedback).** `RedCheck` accepts a story test only if it fails with `UnsupportedOperationException("not implemented: ...")`; a pass or a skip is rejected. So each story test reaches a stub before any assertion or `Assumptions` call:
  - AC-1, AC-2, AC-5: call `HardLinkDetector.detect` with the key-handing fake; `regroup` throws.
  - AC-3: create a temp file, call `new NioFileIdentity().key(file)`, then `assumeTrue(fileKey != null)`, then the hard-link and copy checks.
  - AC-4: create `x.bin`, call `key(x)`, then assume Windows, then `Files.createLink` (assume it succeeds), then compare keys.
  - AC-6: read `key(x)` twice and assert equal keys and hash codes, then assert `FileIdentity.class.getDeclaredMethods()` is just `key(Path)`.
  - AC-7 (`file-scanner`): call `key` on a temp file, then assert `NioFileIdentity.class.getModule().isNativeAccessEnabled()`.
  - AC-8: call `key` on a temp file in-process (native access is enabled in the test JVM), then assume Windows, then run the child JVM.
- **AC-7's cli part is a plain test, no story ID.** `cli/src/test/java/us/pochaev/dfs/cli/NativeAccessTest.java` (written by the test-author; listed in the ticket's `touches`) asserts `ScannerFactories.class.getModule().isNativeAccessEnabled()` (allowed by cli's `ArchitectureTest`; on the classpath every class is in the unnamed module) and opens the jar named by `System.getProperty("dfs.bootJar")` to assert `Enable-Native-Access: ALL-UNNAMED`. Its display names must not start with `DFS-026.AC-`, so it counts as an ordinary test and passes at the red gate. `:cli:run` is confirmed by the real-run done-item: no scanner "restricted method" warning on stderr (Gradle's own warning is unrelated).
- **AC-8 child JVM.** Start `ProcessHandle.current().info().command()` with `--illegal-native-access=deny` (and no `--enable-native-access`), `-cp System.getProperty("java.class.path")`, and a test-source `main` class. It reads one key and prints the exception's class and message; the parent asserts on them.
- **Pre-existing tests at the red gate (open, for the gate).** Any non-story test that forms a duplicate set reaches the `regroup` stub, and any using the real adapter also reaches the `key` stub; `RedCheck` reports each as a regression. Candidates are listed in the Summary. Options, not decided here: (a) the test-author relabels the DFS-004/DFS-005 stage tests whose behavior AC-2 and AC-5 restate as `DFS-026.AC-2`/`AC-5` story tests, which still leaves the end-to-end and cli tests failing; (b) an orchestrator override of the red gate for this ticket, after checking that every non-story failure is a `not implemented: DFS-026` stub; (c) a CCW change to `RedCheck` accepting a non-story failure when it is the current ticket's stub. Keeping the old logic alive in a stub is not an option: every stub throws, and the pairwise method is gone from the port.

## Stubs

- Files stubbed:
  - `file-scanner/src/main/java/us/pochaev/dfs/scanner/infra/port/FileIdentity.java` (reshaped, abstract).
  - `file-scanner/src/main/java/us/pochaev/dfs/scanner/infra/adapter/NioFileIdentity.java` (`key`).
  - `file-scanner/src/main/java/us/pochaev/dfs/scanner/infra/adapter/WindowsFileId.java` (new; `key`).
  - `file-scanner/src/main/java/us/pochaev/dfs/scanner/application/HardLinkDetector.java` (`regroup`).
- Build settings (real, not stubs): `build.gradle.kts`, `cli/build.gradle.kts` (see Configuration).
- Each throws `new UnsupportedOperationException("not implemented: DFS-026")` per D2.
- Commit: filled in by the orchestrator after the design commit.
