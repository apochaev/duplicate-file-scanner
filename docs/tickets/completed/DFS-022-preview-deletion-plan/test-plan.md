# DFS-022: Preview a deletion plan — test plan

## Criterion → scenario → test

Tests live in `file-scanner/.../application/PreviewDeletionPlanServiceTest.java` (S), `file-scanner/.../domain/factory/PreviewDeletionPlanFactoryTest.java` (F) and `scanner-spring/.../ScannerAutoConfigurationTest.java` (B).

| Criterion | Scenario | Test (`@DisplayName`) |
|---|---|---|
| `DFS-022.AC-1` | The same request on the same scan gives the same plan ID | S: `DFS-022.AC-1 The same request on the same scan gives the same 64-character hex plan id` |
| `DFS-022.AC-1` | A request that changes the plan's entries changes the plan ID | S: `DFS-022.AC-1 A request that changes the plan's entries changes the plan id` |
| `DFS-022.AC-1` | The plan ID follows the documented encoding (golden values, added at the done gate) | S: `DFS-022.AC-1 The empty plan has the fixed id of the documented encoding`; S: `DFS-022.AC-1 A one-operation plan's id is the SHA-256 of the documented length-prefixed encoding` (expected built by hand with the platform path separator) |
| `DFS-022.AC-2` | Preview reads only the scan result | S: `DFS-022.AC-2 Preview reads only the scan result and leaves the disk alone` |
| `DFS-022.AC-3` | A rule quarantines the copies under B that have a copy under A | S: `DFS-022.AC-3 A rule quarantines the copies under B that have a copy under A` |
| `DFS-022.AC-3` | A rule folder covers whole path components only | S: `DFS-022.AC-3 A rule folder covers whole path components only` |
| `DFS-022.AC-4` | An exception keeps a file a rule would quarantine | S: `DFS-022.AC-4 An exception folder keeps a file a rule would quarantine` |
| `DFS-022.AC-4` | An exception naming the file itself | S: `DFS-022.AC-4 An exception naming the file itself` |
| `DFS-022.AC-5` | A pick overrides the rules for its set | S: `DFS-022.AC-5 A pick overrides the rules and exceptions for its set` |
| `DFS-022.AC-6` | Two rules in opposite directions conflict | S: `DFS-022.AC-6 Two rules in opposite directions conflict instead of removing both copies` |
| `DFS-022.AC-6` | A keeper quarantined by another rule is a conflict even if a third copy survives | S: `DFS-022.AC-6 A keeper quarantined by another rule is a conflict even if a third copy survives` |
| `DFS-022.AC-7` | Two rules quarantining the same copy give one entry | S: `DFS-022.AC-7 Two rules quarantining the same copy give one entry, from the earliest rule` |
| `DFS-022.AC-8` | Hard links of one copy are quarantined together and counted once | S: `DFS-022.AC-8 Hard links of one copy are quarantined together and counted once` |
| `DFS-022.AC-8` | A copy only partly inside the quarantine folder is left alone | S: `DFS-022.AC-8 A copy only partly inside the quarantine folder is left alone` |
| `DFS-022.AC-9` | The keeper is the first qualifying copy by path | S: `DFS-022.AC-9 The keeper is the first qualifying copy by path` |
| `DFS-022.AC-10` | Operations are grouped by source and the totals add up | S: `DFS-022.AC-10 Operations are ordered by source then first link and the totals add up` |
| `DFS-022.AC-11` | Left behind counts what stays in the quarantine folder | S: `DFS-022.AC-11 Left behind counts what stays in the quarantine folder` |
| `DFS-022.AC-11` | A rule folder without scanned files leaves nothing behind | S: `DFS-022.AC-11 A rule folder without scanned files leaves nothing behind` |
| `DFS-022.AC-12` | An empty request gives an empty plan with a stable ID | S: `DFS-022.AC-12 An empty request gives an empty plan with a stable id` |
| `DFS-022.AC-12` | A null argument is a caller bug | S: four tests, `DFS-022.AC-12 A null scan / request / element in the rule list / list in the request is a caller bug` |
| `DFS-022.AC-13` | A rule that could never quarantine anything is refused | S: `DFS-022.AC-13 A rule that could never quarantine anything is refused` |
| `DFS-022.AC-13` | Keeping a subfolder of the quarantine folder is valid | S: `DFS-022.AC-13 Keeping a subfolder of the quarantine folder is valid` |
| `DFS-022.AC-13` | Picks that do not match the scan are refused | S: `DFS-022.AC-13 Picks that do not match the scan are refused` |
| `DFS-022.AC-13` | The first problem in request order is reported | S: `DFS-022.AC-13 The first problem in request order is reported` |
| `DFS-022.AC-14` | The factory exposes the preview | F: `DFS-022.AC-14 ScannerFactories.previewDeletionPlan() returns a working preview` |
| `DFS-022.AC-14` | The Spring wiring provides one bean | B: `DFS-022.AC-14 The autoconfiguration provides one working PreviewDeletionPlan bean` |
| `DFS-022.AC-14` | A context's own bean is used instead | B: `DFS-022.AC-14 An app's own PreviewDeletionPlan bean replaces the default` |

## Fakes and fixtures used

- No ports: the service is a pure function; tests hand-build `ScanResult`/`DuplicateSet` values (paths such as `C:/A/x.jpg` need not exist).
- Executor: none.
- Fixture files: none; AC-2 uses a `@TempDir` with one file to prove its listing is unchanged.

## Notes for the implementer

- Expected-exception tests (AC-12 nulls) use try/fail/catch, not `assertThrows`, so the stub's exception propagates. The `PlanRequest` with a null element/list is built inside the try block with the `preview` call.
- AC-14 back-off test calls the factory's `preview` before the bean assertion, so it is red against the stub.
- Message checks match `rule <i>` / `pick <digest>` as substrings.
- In AC-8 the expected link order is sorted by `Path.compareTo` (`C:/B/sub/x-link.jpg` before `C:/B/x.jpg`).
