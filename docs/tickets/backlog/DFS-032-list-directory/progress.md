## design (architect) — 2026-11-01

- Wrote design.md (Summary first) and the compilable stubs: ListDirectory, DirectoryListing, DirectoryEntry (FileItem/FolderItem/SkippedItem), ErrorCode.LOCATION_IS_LINK (+ widened Javadoc), ListDirectoryService (stub list), DirectoryReader, LinkNotFollowedException, NioDirectoryReader (stub read), DirectoryAccess seam (stub NIO), EntryKind (stub of), ScannerFactories.listDirectory() and the ScannerAutoConfiguration bean (real one-liners).
- NioFileTreeWalker left unchanged; the implementer moves it onto EntryKind.
- `./gradlew :file-scanner:compileJava :scanner-spring:compileJava` passes.
- Expected commit: `docs(file-scanner): design DFS-032 list directory [DFS-032]` (design.md, progress.md, learnings.md, stubs).
