# DFS-033: Check a deletion plan for drift since the scan — design

## Summary

There is one query port, `CheckPlanDrift.check(ScanResult, DeletionPlan) -> Result<DriftReport>`, in `domain.usecases.query`. It is implemented by `application.CheckPlanDriftService`, whose only field is the new secondary port `infra.port.FileAttributesReader`. That port's adapter is `infra.adapter.NioFileAttributesReader`. New `domain.model` types (the package is already exported, so `module-info.java` is unchanged): `DriftReport`, `PathDrift` with the nested enum `PathDrift.Role`, and the top-level enum `DriftKind`. `ErrorCode` gains `PLAN_NOT_FROM_SCAN`. State model: none (a synchronous read on the caller's thread).

- AC-1, AC-2, AC-3, AC-6, AC-8: real `@TempDir` files, checked through `ScannerFactories.checkPlanDrift()` (or `new CheckPlanDriftService(new NioFileAttributesReader())`). They reach the stub `CheckPlanDriftService.check`.
- AC-4: the same, with a symlink, a folder and (Windows only) a junction made at test time through `Fixtures`. Reaches `check`.
- AC-5: a fake `FileAttributesReader` that throws `AccessDeniedException` for one path and reads every other path for real. Reaches `check`.
- AC-7: a recording fake `FileAttributesReader`, with an assertion that it recorded no call. The empty plan, and the NPEs written with try/catch, not `assertThrows`. All reach `check`.
- AC-9: `ScannerFactories.checkPlanDrift()` and the `ScannerAutoConfiguration` bean are real one-liners, as in DFS-022 and DFS-032. The tests check a plan through the factory and through the bean before any back-off assertion, so they reach `check`.
- `NioFileAttributesReader.read` is also stubbed. Every story test reaches `check` first.
- Checklist items not applicable: 1, 3, 4, 6, 7, 8, 9, 11, 12 (no ID, no state, no callbacks, no executor; ticket decision 7).
- Decisions settled here, since this ticket has no design gate:
  - (a) `Role` is nested as `PathDrift.Role`, because `Role` alone is too generic for the package. `DriftKind` is top-level.
  - (b) The whole plan is validated before the first read, operation by operation (digest, then links in order, then keeper). The failure messages are fixed (listed below).
  - (c) A path is regular only if `isRegularFile() && !isOther() && !isSymbolicLink()`. Anything else is NOT_A_REGULAR_FILE.
  - (d) Only an `IOException` from the reader becomes a drift. Any other throwable is a bug and propagates.
  - (e) A path's recorded values come from the `FileEntry` with that path in the operation's set. Path equality is `Path.equals`.
  - (f) "A scan of T" in tests is built by hand from real files, with no scan run (see Testability).
- Expected breakage: none.

## Public API

- `CheckPlanDrift` in `domain.usecases.query`: `Result<DriftReport> check(ScanResult scan, DeletionPlan plan)`.
- `DriftReport` in `domain.model`: `(String planId, int checked, List<PathDrift> drifts)`.
- `PathDrift` in `domain.model`: `(Path path, PathDrift.Role role, String digest, DriftKind kind, String detail)`.
- `PathDrift.Role` in `domain.model`: `CANDIDATE`, `KEEPER`.
- `DriftKind` in `domain.model`: `MISSING`, `NOT_A_REGULAR_FILE`, `UNREADABLE`, `SIZE_CHANGED`, `MODIFIED_CHANGED`, in precedence order.
- `ErrorCode` in `domain.model`: adds `PLAN_NOT_FROM_SCAN`.
- `ScannerFactories.checkPlanDrift()` in `domain.factory`: `static CheckPlanDrift checkPlanDrift()`, which returns `new CheckPlanDriftService(new NioFileAttributesReader())`.
- Secondary (not exported): `FileAttributesReader` in `infra.port`: `BasicFileAttributes read(Path path) throws IOException`.

No first-slice type is replaced. `DeletionPlan`, `Operation`, the plan-ID encoding, `ScanUseCases` and `PreviewDeletionPlan` are unchanged.

Record checks the implementer adds (left unchecked at design, per the archetype):

- `DriftReport`: `planId` non-null; `checked >= 0` (IAE otherwise); `drifts = List.copyOf(drifts)` (NPE on a null list or element).
- `PathDrift`: `path`, `role`, `digest`, `kind`, `detail` non-null.

## Layer placement

- `domain.usecases.query` (exported): `CheckPlanDrift`.
- `domain.model` (exported): `DriftReport`, `PathDrift` (with `Role`), `DriftKind`, `ErrorCode.PLAN_NOT_FROM_SCAN`.
- `application`: `CheckPlanDriftService`, public final. Its public constructor takes `(FileAttributesReader)` (it already rejects null), and its only field is that reader. It needs no seam: the port is the seam.
- `infra.port` / `infra.adapter`:
  - `infra.port`: `FileAttributesReader`.
  - `infra.adapter`: `NioFileAttributesReader`, public final and stateless, with an implicit no-arg constructor.
- `scanner-spring`: `ScannerAutoConfiguration.checkPlanDrift()`, `@Bean @ConditionalOnMissingBean`, returns the factory's result directly. The service implements this one port only, so it needs no delegate.

## Behavior (the contract the implementer follows)

**`NioFileAttributesReader.read(path)`:** `Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS)`. Exceptions propagate.

**`CheckPlanDriftService.check(scan, plan)`:**

1. `Objects.requireNonNull(scan, "scan")`, then `Objects.requireNonNull(plan, "plan")`.
2. **Validate the whole plan, reading nothing.** For each operation `i` (0-based) in `plan.operations()` order, switch on `Operation` (only `Operation.Quarantine q` exists):
   - Find the first set in `scan.sets()` whose `digest` equals `q.digest()`. If there is none, return `Failure(PLAN_NOT_FROM_SCAN, "operation <i>: no set with digest <digest> in the scan")`.
   - Build that set's recorded entries: `Path` to `FileEntry` over every link of every copy in `set.copies()`.
   - For each path of `q.links()` in order, then `q.keeper()`: if the path is not among those entries, return `Failure(PLAN_NOT_FROM_SCAN, "operation <i>: <path> is not a copy of set <digest> in the scan")`.
   - Collect `(path, role, digest, recorded FileEntry)` in this order: links as `CANDIDATE`, then the keeper as `KEEPER`. Skip any path already collected (`Path.equals`), so its first occurrence fixes its role, digest and recorded entry.
3. `plan.conflicts()` is not read. A plan with no operations ends step 2 with nothing collected, and gives `Success(new DriftReport(plan.id(), 0, List.of()))`.
4. **Read each collected path once, in order,** through `reader.read(path)`, and find the first kind that applies:

   | Reading | Kind | `detail` |
   |---|---|---|
   | throws `NoSuchFileException` | `MISSING` | `""` |
   | attributes where `!(isRegularFile() && !isOther() && !isSymbolicLink())` | `NOT_A_REGULAR_FILE` | `""` |
   | throws any other `IOException` `e` | `UNREADABLE` | `e.getMessage()`, or `e.toString()` if the message is null or blank |
   | `attrs.size() != recorded.size()` | `SIZE_CHANGED` | `""` |
   | `!attrs.lastModifiedTime().toInstant().equals(recorded.lastModified())` | `MODIFIED_CHANGED` | `""` |
   | otherwise | no drift | |

   The table's order is the precedence. A reading throws or returns attributes, so `UNREADABLE` never competes with `NOT_A_REGULAR_FILE`. A throwable that is not an `IOException` propagates (decision d).
5. Return `Success(new DriftReport(plan.id(), collected.size(), drifts))`, with the drifts in collection order.

## State and transitions

None: this use case has no lifecycle.

## Concurrency and atomicity

- Which thread runs what: everything runs synchronously on the caller's thread (ticket decision 5).
- Where per-request state lives: only in local variables. The service's only field is the reader, and the adapter has no fields.
- Races: the disk can change while it is being checked. Each path is read once and reported as that one reading found it. No shared state means no race to settle.

## Checklist disposition

1. Unknown or stale IDs: not applicable. The check takes no ID; the plan is passed by value (DFS-022 decision 1).
2. Results as values, bugs as exceptions:
   - A plan that does not match the scan is `Failure(PLAN_NOT_FROM_SCAN)`, a new named code.
   - A drifted path is data in the `Success` value, never a failure.
   - A `null` scan or plan throws `NullPointerException`.
   - A non-`IOException` from the reader propagates as a bug.
   - There are no optional arguments, so no overloads.
3. Status model: not applicable, no lifecycle.
4. Races and who wins: not applicable, no shared state. A disk change during the check is handled per path, as item 5 says.
5. Validation before side effects: the check has no side effects, and it validates before it reads. The whole plan is validated against the scan (step 2) before the first attribute read, so a refused plan causes no I/O (AC-7). If a file changes between the scan and the check, that is the drift being reported. If a file changes after its reading, that is caught at execution, by the byte compare (CLAUDE.md decision 4).
6. State placement: not applicable, no per-request state. The service holds only its port (a Definition of done item).
7. Progress and observation: not applicable. The check is synchronous, a few thousand attribute reads, and reports no progress (ticket decision 5).
8. Caller-supplied callbacks: not applicable, no caller code is called.
9. Failures off the caller's thread: not applicable, nothing runs off the caller's thread.
10. Query results:
    - The whole report, unpaged. `drifts` are in plan order, at each path's first position.
    - `checked` counts distinct paths read, drifted ones included.
    - It is never partial: either every collected path was read, or the result is a `Failure` and nothing was read. One unreadable path is an `UNREADABLE` entry; it does not stop the check.
11. Executor contract: not applicable, no executor.
12. Retention: not applicable, nothing is retained. Storing plans is out of scope (DFS-022 decision 1).
13. Testability: no timing is involved, and no `Thread.sleep`. See "For the test-author" below.
14. Exported surface:
    - The port and the new types are in the already-exported `domain.usecases.query` and `domain.model`.
    - `checkPlanDrift()` is in the exported `domain.factory`.
    - The port and the adapter are in the non-exported `infra`.
    - `module-info.java` is unchanged, and no first-slice type is replaced.

## Configuration

- None. The check reads no `ScanConfig` or other setting.

## Expected breakage

None.

## Open design notes (as of the design phase)

- **Validation messages** (decision b): `operation <i>: no set with digest <d> in the scan` and `operation <i>: <path> is not a copy of set <d> in the scan`. Tests check the code, plus the digest or path as a substring.
- **Regular-file test** (decision c): the same order as `EntryKind` (a symlink, then other, then anything not regular). `EntryKind` is package-private in `infra.adapter`, so `application` uses the `BasicFileAttributes` predicates directly.
- **Non-`IOException` throwables** (decision d) propagate, as in `ListDirectoryService`. "One bad path never fails the check" is read as one path's I/O failure.
- **For the test-author:**
  - **Where the tests go.** Put the `file-scanner` story tests in `file-scanner/src/test/java/us/pochaev/dfs/scanner/infra/adapter/` (for example `CheckPlanDriftTest`), where the package-private `Fixtures` (`createSymlinkOrSkip`, `createJunction`, `isWindows`) is visible. `CheckPlanDriftService`, `NioFileAttributesReader` and `FileAttributesReader` are all public. The AC-9 Spring tests go in `ScannerAutoConfigurationTest`.
  - **"A scan of T"** is built by hand from the real files, with no scan run:
    - For each file `p`, read `a = Files.readAttributes(p, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS)` and record `new FileEntry(p, a.size(), a.lastModifiedTime().toInstant())`. This is the same NIO call the adapter makes, so an unchanged file compares equal.
    - Group the entries into `new DuplicateSet(size, "d1", List.of(entryA, entryB))` (the three-argument constructor, files in ascending path order; a literal digest is enough).
    - Build `new ScanResult(List.of(T), List.of(), sets, List.of(), 0)`.
    - Use T as the `@TempDir` gives it, with no `toRealPath()`. The service resolves nothing and reports paths exactly as the scan holds them, so expected drift paths are those same `Path`s.
  - **"The plan"** comes from `((Result.Success<DeletionPlan>) ScannerFactories.previewDeletionPlan().preview(scan, new PlanRequest(List.of(new KeepAndQuarantine(T.resolve("A"), T.resolve("B"))), List.of(), List.of()))).value()`. In the first scenario, the plan order is B/x.jpg, A/x.jpg, B/y.jpg, A/y.jpg, which gives `checked` 4. In the shared-keeper scenario it is B/x.jpg, K/x.jpg, C/x.jpg (K/x.jpg is skipped the second time), which gives `checked` 3.
  - **AC-3 without sleeping:**
    - Append with `Files.write(p, new byte[]{1}, StandardOpenOption.APPEND)`.
    - Set times with `Files.setLastModifiedTime(p, FileTime.from(...))`: the recorded instant for x.jpg, and `recorded.plusSeconds(10)` for y.jpg and z.jpg.
    - Never rely on a write moving the time forward.
    - The scenario's third set (A/z.jpg, B/z.jpg as d3) uses a size different from d1 and d2, or the same size with different bytes. Either works, since the digest is a literal.
  - **AC-4:** use three methods, so a skipped symlink does not skip the other two:
    - (1) Symlink: delete B/x.jpg, write a target outside A and B with different bytes of the same length, and set its last-modified time to B/x.jpg's recorded one. Then `Fixtures.createSymlinkOrSkip(B/x.jpg, target)`. A link-following read would see no drift, so NOT_A_REGULAR_FILE proves the link was not followed.
    - (2) Folder: delete B/y.jpg, then `Files.createDirectory` at that path.
    - (3) Junction: start with `assumeTrue(Fixtures.isWindows())`. Delete B/x.jpg, then `Fixtures.createJunction(B/x.jpg, someFolder)`.
  - **AC-5:** `new CheckPlanDriftService(fake)`. The fake throws `new AccessDeniedException(p.toString(), null, "denied")` when `p.equals(T/B/x.jpg)`. For every other path it returns `Files.readAttributes(p, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS)` and records it. Assert the UNREADABLE entry, a non-empty `detail`, `checked` 4, and that the fake recorded the other three paths.
  - **AC-7, plan from another scan:**
    - Build U with the same names and sizes and the same literal digests (`d1`, `d2`), so the digest lookup succeeds and the path check is what refuses the plan.
    - Check T's plan against U's scan through `new CheckPlanDriftService(recorder)`. The recorder is a `FileAttributesReader` that adds every path to a list (then delegates, or throws).
    - Assert `PLAN_NOT_FROM_SCAN`, a message containing `T.resolve("B").resolve("x.jpg").toString()` (the first link of the first operation), and an empty recorder list.
  - **AC-7, empty plan:** `new PlanRequest(List.of(), List.of(), List.of())`. The NPE cases use `try { check(null, plan); fail(); } catch (NullPointerException expected) {}`, never `assertThrows` (DFS-019.L-1).
  - **AC-8:** before and after the check, walk T and record each file's relative name, size and last-modified time through `Files` calls in the test.
  - **AC-9 back-off:** first check through `ScannerFactories.checkPlanDrift()` (the DFS-022.L-1 pattern). The bean test calls `check` on the bean, with the scan and plan built from files under the test class's `tmp`.

## Stubs (as of the design phase)

- Files stubbed:
  - `file-scanner/src/main/java/us/pochaev/dfs/scanner/application/CheckPlanDriftService.java` (`check`)
  - `file-scanner/src/main/java/us/pochaev/dfs/scanner/infra/adapter/NioFileAttributesReader.java` (`read`)
- Written without checks or bodies:
  - `domain/usecases/query/CheckPlanDrift.java`
  - `domain/model/{DriftReport,PathDrift,DriftKind}.java`
  - `infra/port/FileAttributesReader.java`
- Edited:
  - `domain/model/ErrorCode.java` (`PLAN_NOT_FROM_SCAN`)
  - `domain/factory/ScannerFactories.java` (`checkPlanDrift()`, real)
  - `scanner-spring/src/main/java/us/pochaev/dfs/scannerspring/ScannerAutoConfiguration.java` (`checkPlanDrift` bean, real)
- Each stub throws `new UnsupportedOperationException("not implemented: DFS-033")` per D2.
- Commit: filled in after the design commit.
