# DFS-047 progress

## design (architect)

- Moved `ApiError`, `ApiException` and `ApiExceptionHandler` from `server/.../scan` to `server/.../api` with `git mv` (staged as renames). `ApiException` and its constructor and `unknownScan` are now public; the advice covers `basePackages = "us.pochaev.dfs.server"`. `ScanController` imports them from `api`.
- `ScanRegistry` is now `public`, with the new stub `completedState(String, Class<T>, Function<ScanResult, ? extends T>)`.
- New package `server/.../result`: the records `FolderNode`, `SetPage`, `DuplicateSetView`, `CopyView` and `LinkView`, and the stubs `ResultController` (`listFolders`, `listSets`) and `ResultIndex` (`of`, `insideRoots`, `roots`, `children`, `sets`).
- Ran `./gradlew :server:updateOpenApi`: `server/frontend/openapi.json` gained 299 lines, none removed.
- `:server:compileJava` passes. `:server:npmTest` passes, and so does `:server:test` filtered to `OpenApiSpecTest`, `ScanApiTest`, `ArchitectureTest` and `SecurityTest`. The generated client has `results.service.ts`.
- Wrote `design.md`; friction is in `learnings.md`.
- Expected commit: `feat(server): design result API stubs and move error types to api [DFS-047]` (stubs, the moved error types, the regenerated spec, design.md, progress.md, learnings.md).
