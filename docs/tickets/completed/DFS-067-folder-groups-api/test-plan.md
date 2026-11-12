# DFS-067: Folder groups in the overlaps API — test plan

## Criterion → scenario → test

Files: `GroupApiTest` (server, `server/src/test/java/us/pochaev/dfs/server`), `FolderGroupsTest` and `FolderGroupsScaleTest` (`.../analysis`). Names are shortened to the part after the ID.

| Criterion | Scenario | Test (`@DisplayName`) | Level | Timing-sensitive |
|---|---|---|---|---|
| `DFS-067.AC-1`, `DFS-067.AC-2`, `DFS-067.AC-3` | Five RAW files shared by a folder and a bundle subfolder form one group | `FolderGroupsTest` Five RAW files shared by a folder and a bundle subfolder form one group (190 and 12 files, 41 MB, in-between folders hold files yet are not listed) | in-memory `ScanResult` | no |
| `DFS-067.AC-1`, `DFS-067.AC-3` | Two copies in one folder and one in its parent | `FolderGroupsTest` Two copies in one folder and one in its parent | in-memory | no |
| `DFS-067.AC-1` | Same folders but different copy counts are different groups | `FolderGroupsTest` Same folders but different copy counts are different groups; equal shapes merge | in-memory | no |
| `DFS-067.AC-1`, `DFS-067.AC-2`, `DFS-067.AC-3` | Copies side by side in one folder form a one-folder group | `FolderGroupsTest` Copies side by side in one folder form a one-folder group; `GroupApiTest` same, over HTTP on real files | in-memory; real server over HTTP | no |
| `DFS-067.AC-1`, `DFS-067.AC-2`, `DFS-067.AC-3`, `DFS-067.AC-5` | (end to end of the above and "Heaviest group first") | `GroupApiTest` Every group of the tree, its folders and figures, heaviest first (all fields, sets[], otherGroups) | real server over HTTP | no |
| `DFS-067.AC-3` | A folder in two groups counts the other group | `FolderGroupsTest` A folder in two groups counts the other group | in-memory | no |
| `DFS-067.AC-4` | Hard links add no copy | `FolderGroupsTest` Hard links add no copy ...; Two links of one copy in one folder ...; `GroupApiTest` Hard links add no copy ... (real hard link, aborts via Assumptions where not creatable) | in-memory; real server over HTTP | no |
| `DFS-067.AC-5` | Heaviest group first | `FolderGroupsTest` Heaviest group first, then first folder path, then the folder lists (206 MB first, tie by first folder, then by list); `GroupApiTest` A page of the ordered list; a page past the end is empty with the same total | in-memory; HTTP | no |
| `DFS-067.AC-6` | Filtering by folder | `GroupApiTest` parameterized "Groups under a folder, with totalItems and hiddenSmall counted over them only" (9 rows: subfolder, ancestor, root, empty folder, default minBytes, none) | real server over HTTP | no |
| `DFS-067.AC-7` | Small groups are hidden by default | `GroupApiTest` parameterized "Groups below minBytes are left out and counted" (default, 0, exact boundary 2 MiB, one above) with a 2 MiB group and three ~100 KB groups | real server over HTTP | no |
| `DFS-067.AC-8` | A group lists its first 20 sets | `FolderGroupsTest` A group of 25 sets lists its first 20 ... (name, size, copies, digest); `GroupApiTest` A group of 25 sets answers its first 20 sets through the API | in-memory; HTTP | no |
| `DFS-067.AC-9` | Refusals match the summaries endpoint | `GroupApiTest` Refused group queries (RELATIVE_PATH, PATH_OUTSIDE_ROOTS, INVALID_PAGE, and their order against a negative minBytes); A scan that is not completed answers 409 (HoldingExecutorConfiguration); Unknown or malformed scan IDs answer 404; The groups endpoint is secured (no cookie, foreign Host, positive control) | real server over HTTP / raw socket | no |
| `DFS-067.AC-10` | A negative minBytes is refused | `GroupApiTest` A negative minBytes is refused with INVALID_MIN_BYTES; 0 is accepted | real server over HTTP | no |
| `DFS-067.AC-5` | (design decision 4: structure built once) | `GroupApiTest` Concurrent and repeated requests answer the same groups (4 concurrent requests, then a repeat) | real server over HTTP | no |
| (DoD scale test, tagged `DFS-067.AC-1`, `DFS-067.AC-2`) | 150,000-file case | `FolderGroupsScaleTest` A drive-sized scan result is grouped in under 3 seconds (own copy of the generator; checks about 50,000 groups and the 500-folder group) | in-memory, 150,000 files | yes: 3 s budget fixed by the architect (0.6 s cold measured by them) |

## Fixtures and test configurations

- Folders: `OverlapApiTest.tree` (a.jpg in Photos/2019, Backup/2019, Old/Pictures; b.jpg in two; 300 and 200 KB) for most HTTP tests; a 2 MiB group plus three ~100 KB groups for AC-7; 25 two-copy sets for the HTTP AC-8 check; a real hard link for AC-4 (aborts where it cannot be created).
- Bean overrides: `HoldingExecutorConfiguration` for the queued scan (409).
- Session: `TestServer.start(...)`, `server.login(server.token(output.getOut()))` with `OutputCaptureExtension`.
- Streams: none.

## Notes for the implementer

- Scenario sizes are scaled where a scenario names 206 MB or 41 MB over HTTP (real files): the HTTP tests use KB and a few MiB; the in-memory tests use the scenario sizes.
- `GroupFolder.otherGroups` counts across every group of the scan, whatever `under` and `minBytes` ask for (design note); `FolderGroupsTest` pins it.
- `totalItems` counts matching groups at or above `minBytes`; `hiddenSmall` those below (`GroupApiTest` AC-6/AC-7 rows).
- Check order pinned: path (`RELATIVE_PATH`/`PATH_OUTSIDE_ROOTS`) before paging before `minBytes`, and `409`/`404` before all.
- Order of sets inside a group: reclaimable descending; `GroupSet.digest` is `"%064x"` of the set's sequence in `ScanResults`.
- Not testable within the ticket's `touches`: `GroupState` and `GroupIndex` are package-private in `result` (no test path allowed there), so "built exactly once", "a failed build is not kept and retried", and "a refused request builds nothing" have no unit test; over HTTP only that concurrent and repeated requests agree is checked. The implementer should honour those contracts from design.md.
