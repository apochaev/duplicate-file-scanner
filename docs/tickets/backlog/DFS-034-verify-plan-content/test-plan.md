# DFS-034: Verify a deletion plan's content against its keepers — test plan

## Criterion → scenario → test

Files: S = `file-scanner/.../application/VerifyPlanContentServiceTest`, N = `file-scanner/.../infra/adapter/NioContentComparatorTest`, C = `scanner-spring/.../ScannerAutoConfigurationTest`.

| Criterion | Scenario | Test (`@DisplayName`) |
|---|---|---|
| `DFS-034.AC-1` | Identical copies verify as identical | S: `DFS-034.AC-1 Identical copies verify as identical` |
| `DFS-034.AC-1` | (adapter) identical, empty | N: `DFS-034.AC-1 Identical files over several chunks are identical, progress rises to the size`; `DFS-034.AC-1 Two empty files are identical` |
| `DFS-034.AC-2` | A difference is found and located | S: `DFS-034.AC-2 A difference is found and located, a size change is told apart` |
| `DFS-034.AC-2` | (adapter) offset, discrimination, shorter file | N: `DFS-034.AC-2 The first differing byte is located in a later chunk, and other inputs differ`; `DFS-034.AC-2 A file that ends first differs at the offset where it ends` |
| `DFS-034.AC-3` | Missing files are reported and the rest still checked | S: `DFS-034.AC-3 Missing files are reported and the rest still checked` |
| `DFS-034.AC-4` | A file replaced by a link or a folder is not followed | S: `DFS-034.AC-4 A file replaced by a link or a folder is not followed`; `DFS-034.AC-4 A candidate replaced by a junction is not followed` (Windows only) |
| `DFS-034.AC-4` | (adapter) link not opened | N: `DFS-034.AC-4 A symlink is not opened` |
| `DFS-034.AC-5` | An unreadable file does not stop the verify | S: `DFS-034.AC-5 An unreadable file does not stop the verify` |
| `DFS-034.AC-6` | Progress reaches the total | S: `DFS-034.AC-6 Progress reaches the total` |
| `DFS-034.AC-6` | A failing listener is detached | S: `DFS-034.AC-6 A failing listener is detached` |
| `DFS-034.AC-7` | Cancel takes effect inside a large file | S: `DFS-034.AC-7 Cancel takes effect inside a large file`; N: `DFS-034.AC-7 A cancel is honoured before the next chunk` |
| `DFS-034.AC-7` | Cancelled before the start | S: `DFS-034.AC-7 Cancelled before the start` |
| `DFS-034.AC-8` | An empty plan and null inputs | S: `DFS-034.AC-8 An empty plan has no checks`; `DFS-034.AC-8 Null inputs throw NullPointerException` |
| `DFS-034.AC-9` | Verifying changes nothing | S: `DFS-034.AC-9 Verifying changes nothing` |
| `DFS-034.AC-10` | The factory and the Spring wiring expose the verify | S: `DFS-034.AC-10 The factory exposes a working verify`; C: `DFS-034.AC-10 The autoconfiguration provides one working VerifyPlanContent bean`; `DFS-034.AC-10 An app's own VerifyPlanContent bean replaces the default` |

## Fakes and fixtures used

- `ContentComparator`: AC-5 uses a hand-written fake that throws `AccessDeniedException` for `x.jpg` and delegates to `NioContentComparator` otherwise.
- Executor: none (synchronous). Cancel is an `AtomicBoolean` set from the listener, no timing.
- Fixture files: generated in code in a `@TempDir` (3 MiB file for multi-chunk cases; small chunk sizes for adapter tests). Symlinks and junction created at test time, skipped through `Assumptions`.

## Notes for the implementer

- Plans are built with the real `ScannerFactories.previewDeletionPlan()`; tests assume operations come in the order x.jpg then y.bin, candidate = the B link, keeper = the A file.
- AC-7 cancels on the first `BytesCompared` after the first `EntryChecked`, so `BytesCompared(5, total)` must precede `EntryChecked` for x (design behaviour step 5).
- AC-6 failing listener: its first event is that `BytesCompared` for x; it must be called exactly once.
- `NioContentComparator(16)` adapter test expects progress `[16]` after a cancel set in the first progress callback.
