# DFS-005 progress

## design (architect, attempt 1)

- Wrote `design.md` and compilable stubs, each throwing `not implemented: DFS-005`.
- New: `domain/model/{ScanConfig,ScanResult,ScanEvent,ScanPhase,ScanListener}.java`, `infra/port/LocationResolver.java`, `infra/adapter/NioLocationResolver.java`, `application/ScanPipeline.java`.
- Changed: `application/{PartialHashFilter,FullHashConfirmer,HardLinkDetector}.java`. Only a new stubbed `onDrop` constructor was added to each; the existing constructors and methods are untouched.
- `module-info.java` is unchanged. `./gradlew :file-scanner:compileJava :file-scanner:compileTestJava :file-scanner:test` is green, so the existing tests still pass.
- Expected commit (made by the orchestrator): `feat(file-scanner): design scan pipeline stubs [DFS-005]`.
