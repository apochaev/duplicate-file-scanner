# DFS-026: Detect hard links in linear time per duplicate set — test plan

## Criterion → scenario → test

| Criterion | Scenario | Test (`@DisplayName`) |
|---|---|---|
| `DFS-026.AC-1`, `DFS-026.AC-2` | A large set of ordinary copies takes one lookup per file | `DFS-026.AC-1, DFS-026.AC-2 A large set of ordinary copies takes one lookup per file` (HardLinkDetectorTest) |
| `DFS-026.AC-1`, `DFS-026.AC-2` | Hard links collapse into one copy | `DFS-026.AC-1, DFS-026.AC-2 Hard links collapse into one copy` (HardLinkDetectorTest) |
| `DFS-026.AC-2` | A set that is all links of one file is dropped | `DFS-026.AC-2 A set that is all links of one file is dropped` (HardLinkDetectorTest) |
| `DFS-026.AC-3` | Linux and macOS use the file key | `DFS-026.AC-3 Linux and macOS use the file key` (NioFileIdentityTest) |
| `DFS-026.AC-4` | Windows reads the NTFS file ID | `DFS-026.AC-4 Windows reads the NTFS file ID` (NioFileIdentityTest) |
| `DFS-026.AC-5` | An unreadable file is dropped, the rest grouped | `DFS-026.AC-5 An unreadable file is dropped, the rest grouped` (HardLinkDetectorTest) |
| `DFS-026.AC-6` | The port hands out keys | `DFS-026.AC-6 The port hands out keys` (NioFileIdentityTest) |
| `DFS-026.AC-7` | Native access is enabled where the scanner runs | `DFS-026.AC-7 Native access is enabled where the scanner runs` (NioFileIdentityTest); cli test JVM and bootJar manifest: plain `NativeAccessTest` (no story ID) |
| `DFS-026.AC-8` | A refused native call fails loudly | `DFS-026.AC-8 A refused native call fails loudly` (NioFileIdentityTest, child JVM via NativeAccessProbe) |

## Fakes and fixtures used

- `FileIdentity`: hand-written key-handing fake (default key = the path; `sameFile` shares a key; `unreadable` throws IOException; call recorder) in HardLinkDetectorTest, ScanPipelineTest, StageDropReportingTest.
- Fixture files: generated in code in a `@TempDir` (real files for the adapter).

## Notes for the implementer

- Each adapter story test calls `key` before any assumption, so it reaches the stub; after implementation the Windows/non-Windows assumptions skip as designed.
- AC-8 runs `NativeAccessProbe` in a child JVM using `java.class.path`; if Gradle's worker classpath is not in that property, adjust the test.
- DFS-004/005 tests were rewritten from `isSameFile` to keys (DFS-004.AC-4 now asserts one lookup per file in path order).
