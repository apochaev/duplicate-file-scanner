# DFS-033: Check a deletion plan for drift since the scan — test plan

## Criterion → scenario → test

| Criterion | Scenario | Test (`@DisplayName`) |
|---|---|---|
| `DFS-033.AC-1` | An unchanged plan has no drift | `DFS-033.AC-1 An unchanged plan has no drift` |
| `DFS-033.AC-2` | A deleted candidate and a deleted keeper are both reported | `DFS-033.AC-2 A deleted candidate and a deleted keeper are both reported` |
| `DFS-033.AC-3` | Size and time changes are told apart | `DFS-033.AC-3 Size and time changes are told apart` |
| `DFS-033.AC-4` | A file replaced by a link or a folder is not followed | `DFS-033.AC-4 A file replaced by a symlink is not followed`; `DFS-033.AC-4 A file replaced by a folder is not a regular file`; `DFS-033.AC-4 On Windows a candidate replaced by a junction is not a regular file` |
| `DFS-033.AC-5` | An unreadable path does not stop the check | `DFS-033.AC-5 An unreadable path does not stop the check` |
| `DFS-033.AC-6` | A shared keeper is reported once, in plan order | `DFS-033.AC-6 A shared keeper is reported once, in plan order` |
| `DFS-033.AC-6` | Drifts follow plan order | `DFS-033.AC-6 Drifts follow plan order` |
| `DFS-033.AC-7` | A plan from another scan is refused without reading the disk | `DFS-033.AC-7 A plan from another scan is refused without reading the disk`; `DFS-033.AC-7 A digest that is not in the scan is refused without reading the disk` |
| `DFS-033.AC-7` | An empty plan and null inputs | `DFS-033.AC-7 An empty plan has nothing to check and null inputs are rejected` |
| `DFS-033.AC-8` | Checking changes nothing | `DFS-033.AC-8 Checking changes nothing on disk` |
| `DFS-033.AC-9` | The factory and the Spring wiring expose the check | `DFS-033.AC-9 The factory returns a working CheckPlanDrift` (CheckPlanDriftTest); `DFS-033.AC-9 The autoconfiguration provides one working CheckPlanDrift bean`; `DFS-033.AC-9 An app's own CheckPlanDrift bean replaces the default` (ScannerAutoConfigurationTest) |

## Fakes and fixtures used

- `FileAttributesReader`: AC-5 uses a lambda that throws `AccessDeniedException` for B/x.jpg and reads others for real, recording them; AC-7 uses a recording lambda that must see no call. Other tests use `NioFileAttributesReader` on real files.
- Executor: none (synchronous).
- Fixture files: generated in code in `@TempDir` (the scan is built by hand from real files' attributes; no committed fixtures). Symlink and junction are created at test time (`Fixtures.createSymlinkOrSkip`, `createJunction` behind `assumeTrue(isWindows)`). Times are set with `Files.setLastModifiedTime`; no sleeping.
- Tests live in `file-scanner/src/test/java/us/pochaev/dfs/scanner/infra/adapter/CheckPlanDriftTest.java` and `scanner-spring/.../ScannerAutoConfigurationTest.java`.

## Notes for the implementer

- AC-7 "unknown digest" test (a second AC-7 test) checks the failure message mentions `d1` or `d2`; the other checks `T/B/x.jpg` as substring, per design.md.
- AC-6 shared keeper test asserts `checked` 3 and exactly one drift, independent of how the preview groups the two rules into operations.
