## design (architect) — 2026-11-01

- Wrote design.md (Summary first) and the compilable stubs: ListDirectory, DirectoryListing, DirectoryEntry (FileItem/FolderItem/SkippedItem), ErrorCode.LOCATION_IS_LINK (+ widened Javadoc), ListDirectoryService (stub list), DirectoryReader, LinkNotFollowedException, NioDirectoryReader (stub read), DirectoryAccess seam (stub NIO), EntryKind (stub of), ScannerFactories.listDirectory() and the ScannerAutoConfiguration bean (real one-liners).
- NioFileTreeWalker left unchanged; the implementer moves it onto EntryKind.
- `./gradlew :file-scanner:compileJava :scanner-spring:compileJava` passes.
- Expected commit: `docs(file-scanner): design DFS-032 list directory [DFS-032]` (design.md, progress.md, learnings.md, stubs).

## tests (test-author) - 2026-11-01

- Wrote ListDirectoryRealDiskTest (infra.adapter), ListDirectoryServiceTest (application), two AC-9 tests in ScannerAutoConfigurationTest, and test-plan.md. All 20 fail with the stub UnsupportedOperationException; none skipped on this platform (symlinks and junctions available).
- Expected commit: test(file-scanner): add DFS-032 list directory tests [DFS-032]

## Implement (attempt 1)
Implemented ListDirectoryService.list, NioDirectoryReader.read, EntryKind.of, DirectoryAccess.NIO, record checks; moved NioFileTreeWalker onto EntryKind (same behavior). Build green. Commit expected: feat(file-scanner): list one directory level [DFS-032]
