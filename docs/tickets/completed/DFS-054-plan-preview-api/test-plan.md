# DFS-054: Preview a deletion plan through the server API — test plan

All tests are in `server/src/test/java/us/pochaev/dfs/server/PlanApiTest.java`, through a real server over HTTP.

## Criterion → scenario → test

| Criterion | Scenario | Test (`@DisplayName`) | Level | Timing-sensitive |
|---|---|---|---|---|
| `DFS-054.AC-1` | A rule previews to a kept plan | `DFS-054.AC-1 A rule previews to a kept plan with 201, Location and the summary` (planId compared with the id of the core's `PreviewDeletionPlan` bean on the same scan) | real server over HTTP | no |
| `DFS-054.AC-2` | Every previewed plan stays readable | `DFS-054.AC-2 Every previewed plan stays readable, an earlier one after a later preview` | real server over HTTP | no |
| `DFS-054.AC-2` | The same request previews to the same plan | `DFS-054.AC-2 The same request previews to the same plan` | real server over HTTP | no |
| `DFS-054.AC-2` | A plan of another scan is unknown here | `DFS-054.AC-2 A plan of another scan is unknown here (UNKNOWN_PLAN)` | real server over HTTP | no |
| `DFS-054.AC-3` | Operations come largest first with their source | `DFS-054.AC-3 Operations come largest first with their keeper, links and source` | real server over HTTP | no |
| `DFS-054.AC-4`, `DFS-054.AC-6` | Crossing rules become conflicts, never a lost last copy | `DFS-054.AC-4, DFS-054.AC-6 Crossing rules become conflicts, never a lost last copy` | real server over HTTP | no |
| `DFS-054.AC-6` | (invariant over every answered plan) | `DFS-054.AC-6 Every plan the API answers leaves each set one copy no operation moves` (5 requests) | real server over HTTP | no |
| `DFS-054.AC-5` | Operations and conflicts are paged like sets | `DFS-054.AC-5 Operations and conflicts are paged like sets` | real server over HTTP | no |
| `DFS-054.AC-7` | A hard-linked copy moves with all its links | `DFS-054.AC-7 A hard-linked copy moves with all its links, and its keeper is another copy` (real hard link; aborts where refused) | real server over HTTP, real temp files | no |
| `DFS-054.AC-8` | Previewing touches nothing on disk | `DFS-054.AC-8 Previewing, answered or refused, touches nothing on disk` | real server over HTTP, disk snapshot | no |
| `DFS-054.AC-9` | Request paths are checked first (outline, 4 rows) | `DFS-054.AC-9 {field} = {kind} is refused with {status} {code}` | real server over HTTP | no |
| `DFS-054.AC-10` | The core's refusals pass through (outline, 4 rows) | `DFS-054.AC-10 {scenario} is refused by the core as {code}` (message compared with the core bean's own) | real server over HTTP | no |
| `DFS-054.AC-10` | Only the first problem is reported | `DFS-054.AC-10 Only the first problem is reported` | real server over HTTP | no |
| `DFS-054.AC-10` | An empty request previews an empty plan | `DFS-054.AC-10 An empty request previews an empty plan` | real server over HTTP | no |
| `DFS-054.AC-11` | Only a finished, known scan has plans | `DFS-054.AC-11 A scan that is not COMPLETED answers 409 before the body or any parameter` (QUEUED scan) | real server over HTTP | no |
| `DFS-054.AC-11` | same | `DFS-054.AC-11 Scan ID {0} is unknown to all four endpoints` (not-a-uuid, random UUID) | real server over HTTP | no |
| `DFS-054.AC-12` | The plan endpoints are behind the server's guards | `DFS-054.AC-12 The plan endpoints answer 403 without the cookie, with a foreign Host or Origin` (plan previewed first; refused POST proven to keep nothing via the core's id -> 404) | real server over HTTP and raw socket | no |
| `DFS-054.AC-13` | Concurrent previews are each kept | `DFS-054.AC-13 Concurrent previews of different requests are each kept` (latches, no sleep) | real server over HTTP | no |
| `DFS-054.AC-14` | The spec describes the plan endpoints | `DFS-054.AC-14 The committed spec describes the four plan operations and their schemas` (calls the endpoints first, then reads `dfs.openapi.file`) | real server over HTTP | no |

`PlansService` in the generated client: checked through the spec (tag `plans` on all four operations, which names the service); the generated file is git-ignored and is verified by `:server:ngBuild` in `build`.

## Fixtures and test configurations

- Folders: `@TempDir` with A, B, C: photo 2 MB in A and B, song 5 MB in A, B, C, note 1 KB in B (bytes generated in code). AC-7 adds C/photo.jpg and a hard link A/photo-link.jpg.
- Bean overrides: `HoldingExecutorConfiguration` (QUEUED scan) for AC-11.
- Session: `TestServer.start(...)`, `server.login(server.token(output.getOut()))`.
- Streams: none.

## Notes for the implementer

- **AC-7 deviates from the ticket's scenario, deliberately.** The scenario puts the link in B and quarantines A, but the core (`PreviewDeletionPlanService.allUnder`) only quarantines a copy when every link lies under `quarantineIn`, so the literal scenario yields no operation. The test links two files inside A. Ticket wording needs a decision (refine the scenario, or core change, out of scope).
- AC-4 asserts photo's conflict exactly (copy A: `quarantinedBy [1]`, `keptBy [0]`; copy B the reverse; sources rule 0 and 1) and that `conflictCount` equals the conflicts page's `totalItems`; it does not pin whether song (A, B, C) is also a conflict.
- AC-10 compares the message with the core bean's own `Failure.message()`, so the server must pass it unchanged.
- AC-12: the refused POST's would-be plan ID is computed by the core; it must answer `404 UNKNOWN_PLAN` afterwards. A POST without `Origin` is also expected to be 403.
- AC-9 messages must contain the field text (`rules[0].keep`, `rules[0].quarantineIn`, `exceptions[0]`, `picks[0].keep`).
- Operations' `links` are plain path strings; `ConflictCopy.links` too.
