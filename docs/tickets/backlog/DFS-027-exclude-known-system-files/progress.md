## design (architect, 2026-10-14)

- Wrote `design.md` (pipeline-stage template, Summary first, all 14 checklist items disposed).
- Stubs (each throws `not implemented: DFS-027`): `ScanConfig.defaults()` (record gains `boolean defaultExcludes`), `ScanResult` compact constructor (record gains `long excluded`), new `application/DefaultExcludeFilter` (constructor, `keep`, `excluded`).
- Wiring, not stubbed: `ScanPipeline` builds one filter per scan, emits `FileWalked` before the filter, filters before `SizeIndex.add`, passes `excluded()` into `ScanResult`; `ScanCommand` passes `defaults.defaultExcludes()` (compile fix).
- `./gradlew :file-scanner:compileJava :cli:compileJava` passes. Test compilation is broken until the test-author updates the 3-arg `ScanConfig` / 2-arg `ScanResult` calls listed in design.md.
- No `module-info.java` change. Expected breakage: `ScanServiceTest`.
- Expected commit: `feat(file-scanner): design default excludes with stubs [DFS-027]`.

## tests (test-author, 2026-10-14)

- Added `DefaultExcludeFilterTest`, `ScanResultTest`; extended `ScanConfigTest`, `ScanPipelineTest`, `ScanCommandTest`, `TextReportTest`; updated the 3-arg `ScanConfig` / 2-arg `ScanResult` calls and summary assertions in `ScanConfigTest`, `ScanPipelineTest`, `ScanServiceTest`, `ScanCommandTest`, `TextReportTest` (`ScanPipelineEndToEndTest` needed no edit).
- Made `ScanServiceTest.CONFIG` a `config()` method so its tests fail on the stub rather than `NoClassDefFoundError`.
- Ran `:file-scanner:test :cli:test`: every failure carries `not implemented: DFS-027`; no DFS-027 test passes.
- Expected commit: `test(file-scanner): add default-excludes tests [DFS-027]`.
