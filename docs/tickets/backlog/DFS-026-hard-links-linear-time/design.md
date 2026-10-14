# DFS-026: Detect hard links in linear time per duplicate set — design

## Summary

The hard-link stage stops comparing pairwise: `FileIdentity` hands out one opaque key per path, and `HardLinkDetector` groups each set's files by key (one lookup per file). The key is `fileKey()` off Windows and a volume-serial + 128-bit file-ID record from FFM on Windows.

- **AC-1** one lookup per file: `HardLinkDetector.regroup` (stub) calls `identity.key(path)` once per file, in path order, no pairwise loop.
- **AC-2** same output as the pairwise detector: equal keys form one copy, grouped in a `LinkedHashMap` filled in path order, so copies come out by first link; sets under two copies dropped.
- **AC-3** `fileKey()` off Windows: `NioFileIdentity.key` (stub) returns `Files.readAttributes(...).fileKey()` (no open).
- **AC-4** NTFS file ID: new package-private `WindowsFileId.key` (stub) returns `record Key(volumeSerialNumber, fileIdLow, fileIdHigh)` from `GetFileInformationByHandleEx(FileIdInfo)`.
- **AC-5** unreadable file: `IOException` from `key` is reported to `onDrop` as `Skipped(path, UNREADABLE, "hard-link check failed: <detail>")`, the file is left out and the rest of the set is still grouped. The `onDrop` handling is the same as before.
- **AC-6** single method: `FileIdentity` now declares only `Object key(Path) throws IOException` (done in this design commit).
- **AC-7** native access: build config only (no stub). `jvmArgs` on every `Test` task, `applicationDefaultJvmArgs` and the `bootJar` manifest attribute; the implementer adds these.
- **AC-8** refused native call: `WindowsFileId` maps `IllegalCallerException` to `IllegalStateException` naming `--enable-native-access`.

Placement: port in `infra.port`, adapter plus FFM helper in `infra.adapter`, stage in `application`. There are no `domain.model` changes, no new exports and `module-info.java` is unchanged.

Not applicable: checklist 1 (finished input list), 5 (paths already unique since DFS-001), 6 (no tunable), 12 (no content read).

Needs a gate decision. The AC-7 tests need a new `cli` test file, which is not in Scope's touches. The ticket's frontmatter `touches:` is empty, though Scope lists the paths. Test sources stop compiling until the test-author rewrites the four `FileIdentity` fakes.

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
- Build: root `build.gradle.kts` (`Test` tasks) and `cli/build.gradle.kts` (`application`, `bootJar`, `test`); see Configuration.
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
- Native access (AC-7; the implementer adds this, since a stub cannot hold it):
  - Root `build.gradle.kts` `subprojects { tasks.withType<Test>().configureEach { jvmArgs("--enable-native-access=ALL-UNNAMED") } }`.
  - `cli/build.gradle.kts`:
    - `application { applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED") }`.
    - `tasks.bootJar { manifest { attributes("Enable-Native-Access" to "ALL-UNNAMED") } }`.
    - For the manifest test: `tasks.test { dependsOn(tasks.bootJar); systemProperty("dfs.bootJar", <bootJar archiveFile absolute path>) }`. The property name `dfs.bootJar` is the contract the test reads.

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
- **Test files named for the test-author.** `HardLinkDetectorTest`, `ScanPipelineTest`, `StageDropReportingTest` (fakes now hand out keys: default key = the path itself, configurable equal keys, configurable `IOException`, a call counter) and `NioFileIdentityTest` (AC-3, AC-4, AC-6, AC-7's module check, AC-8), all under `file-scanner/src/test/java/us/pochaev/dfs/scanner/`.
  - `ScanServiceRealThreadTest` and `ScanPipelineEndToEndTest` use the real `NioFileIdentity` and need no change.
  - **`compileTestJava` fails after this design commit** until those four fakes are rewritten. The reshaped port is in the design, so this cannot be avoided.
- **AC-8 child JVM.** Start `ProcessHandle.current().info().command()` with `--illegal-native-access=deny`, `-cp System.getProperty("java.class.path")`, and a test-source `main` class. The class reads one key and prints the exception's class and message, and the parent asserts on them. Skip off Windows.
- **AC-7 needs a cli test file outside Scope's touches.** The cli test JVM check and the `bootJar` manifest check need e.g. `cli/src/test/java/us/pochaev/dfs/cli/NativeAccessTest.java`. It should read `ScannerFactories.class.getModule()` (allowed by cli's `ArchitectureTest`; every class is in the unnamed module on the classpath) and open the jar named by `dfs.bootJar`. `:cli:run` is confirmed by the real-run done-item: no scanner "restricted method" warning on stderr. Gradle's own warning is unrelated.
- **Red-gate shape.** AC-7's tests cannot fail on a `not implemented:` stub, because no code path reaches one: they fail on assertions until the implementer edits the build. AC-6's "single method" assertion already passes after this commit, because the port reshape is the design itself.

## Stubs

- Files stubbed:
  - `file-scanner/src/main/java/us/pochaev/dfs/scanner/infra/port/FileIdentity.java` (reshaped, abstract).
  - `file-scanner/src/main/java/us/pochaev/dfs/scanner/infra/adapter/NioFileIdentity.java` (`key`).
  - `file-scanner/src/main/java/us/pochaev/dfs/scanner/infra/adapter/WindowsFileId.java` (new; `key`).
  - `file-scanner/src/main/java/us/pochaev/dfs/scanner/application/HardLinkDetector.java` (`regroup`).
- Each throws `new UnsupportedOperationException("not implemented: DFS-026")` per D2.
- Commit: filled in by the orchestrator after the design commit.
