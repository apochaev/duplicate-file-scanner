# DFS-022: Preview a deletion plan — design

## Summary

One query port, `PreviewDeletionPlan.preview(ScanResult, PlanRequest) -> Result<DeletionPlan>` (`domain.usecases.query`), implemented by the field-less `application.PreviewDeletionPlanService`; its single stub `preview` is what every criterion AC-1 to AC-13 reaches. New records in `domain.model` (already exported, `module-info.java` unchanged): `PlanRequest`, `KeepAndQuarantine`, `KeepCopy`, `DeletionPlan`, `Operation` (sealed, `Operation.Quarantine`), `Source` (sealed, `Source.FromRule`/`Source.FromPick`), `Conflict`, `PlanTotals`, `LeftBehind`; `ErrorCode` gains `INVALID_RULE`, `UNKNOWN_SET`, `PATH_NOT_IN_SET`, `DUPLICATE_PICK`. State model: none (pure function, no lifecycle).

- AC-1 plan ID: SHA-256 over the length-prefixed canonical encoding below (operations and conflicts only). AC-2: the service reads only its arguments. Both reach `preview`.
- AC-3, AC-4, AC-7, AC-8, AC-9: rule planning per set (step 3 of the algorithm). AC-5: pick planning (step 2). AC-6: conflict check per set (step 4). All reach `preview`.
- AC-10 ordering and totals, AC-11 left-behind counts, AC-12 empty request and NPEs, AC-13 validation codes: reach `preview`. The NPE for a null list or element is added by the implementer to the record constructors; with stubs, the record constructors have no checks, so the call reaches `preview`.
- AC-14: `ScannerFactories.previewDeletionPlan()` and the `ScannerAutoConfiguration` bean are written for real now (one-liners; a throwing factory would fail every autoconfigured context in `scanner-spring` and `cli`). The AC-14 test reaches `preview` because it previews through the factory (and through the bean) before checking back-off.
- Checklist items not applicable: 1, 3, 4, 6 (no state at all), 7, 8, 9, 11, 12, as ticket decision 8 says. 13 is plain value tests.
- Decisions made here, worth a look at the gate: a conflict lists every rule that had a candidate entry in the set (not only the two that collide); a copy with any link under `keep` is never quarantined by that rule; a pick's recorded `keeper` is the first link of the picked copy, which may differ from the pick's `keep` path when the copy has several links; left-behind counts are clamped at 0 per field.
- Expected breakage: none.

## Public API

- `PreviewDeletionPlan` in `domain.usecases.query`: `Result<DeletionPlan> preview(ScanResult scan, PlanRequest request)`.
- `PlanRequest` in `domain.model`: `(List<KeepAndQuarantine> rules, List<Path> exceptions, List<KeepCopy> picks)`.
- `KeepAndQuarantine` in `domain.model`: `(Path keep, Path quarantineIn)`.
- `KeepCopy` in `domain.model`: `(String digest, Path keep)`.
- `DeletionPlan` in `domain.model`: `(String id, List<Operation> operations, List<Conflict> conflicts, PlanTotals totals, List<LeftBehind> leftBehind)`.
- `Operation` in `domain.model`: sealed, `Source source()`; only variant `Operation.Quarantine(String digest, long size, List<Path> links, Path keeper, Source source)`.
- `Source` in `domain.model`: sealed; `Source.FromRule(int index, KeepAndQuarantine rule)`, `Source.FromPick(KeepCopy pick)`.
- `Conflict` in `domain.model`: `(String digest, List<Source> sources)`.
- `PlanTotals` in `domain.model`: `(long files, long bytes, int sets)`.
- `LeftBehind` in `domain.model`: `(int ruleIndex, Path folder, long files, long bytes)`.
- `ErrorCode` in `domain.model`: adds `INVALID_RULE`, `UNKNOWN_SET`, `PATH_NOT_IN_SET`, `DUPLICATE_PICK`.
- `ScannerFactories` in `domain.factory`: adds `static PreviewDeletionPlan previewDeletionPlan()`.

No first-slice type is replaced. `ScanUseCases` is unchanged (decision 8).

Record checks the implementer adds (the archetype leaves data records unchecked at design). Each list is stored with `List.copyOf` (which throws `NullPointerException` on a null list or element):

- `PlanRequest`: all three lists non-null, no null element.
- `KeepAndQuarantine`: `keep`, `quarantineIn` non-null. `KeepCopy`: `digest`, `keep` non-null. Neither validates the INVALID_RULE condition: that is a `Failure`, not an exception.
- `DeletionPlan`: every component non-null; lists copied; `id` matches `[0-9a-f]{64}`.
- `Operation.Quarantine`: every component non-null; `links` copied, non-empty; `size >= 0`.
- `Source.FromRule`: `rule` non-null, `index >= 0`. `Source.FromPick`: `pick` non-null.
- `Conflict`: non-null, `sources` copied and non-empty.
- `PlanTotals`: all three `>= 0`. `LeftBehind`: `folder` non-null, `ruleIndex`, `files`, `bytes` `>= 0`.

## Layer placement

- `domain.usecases.query` (exported): `PreviewDeletionPlan`.
- `domain.model` (exported): the records and sealed interfaces above, the four new `ErrorCode` values.
- `application`: `PreviewDeletionPlanService` (public final, no fields, public no-arg constructor). Private helpers are the implementer's choice; no seam is needed.
- `infra.port` / `infra.adapter`: none.
- `domain.factory`: `ScannerFactories.previewDeletionPlan()` returns `new PreviewDeletionPlanService()`.
- `scanner-spring`: `ScannerAutoConfiguration.previewDeletionPlan()`, `@Bean @ConditionalOnMissingBean`, returns the factory's result directly. It needs no delegate wrapper because the service implements only this port.

## Algorithm (the contract the implementer follows)

"`p` under `f`" means `p.startsWith(f)` on paths as the scan recorded them. A copy's links are always sorted by `Path.compareTo` first, whatever order the scan gives them in.

1. **Validate**, before any planning, and return the first problem as `Failure`:
   - Check each rule `i` in order: `quarantineIn.startsWith(keep)` (equality included) gives `INVALID_RULE`, message `rule <i>: quarantineIn <q> equals or lies under keep <k>`.
   - Then check each pick in order: (a) no set with `digest` gives `UNKNOWN_SET`, message `pick <digest>: no set with this digest in the scan`; (b) `keep` is not equal to any link path of that set gives `PATH_NOT_IN_SET`, message `pick <digest>: <keep> is not a link of this set`; (c) an earlier pick has the same digest: `DUPLICATE_PICK`, message `pick <digest>: a second pick for this digest`.
   - Exceptions are not validated. If two sets share a digest (the scan never produces that), the first one in `scan.sets()` order is used.
2. **Picked sets.** For the set of each pick: the keeper copy is the copy with a link equal to `keep`. Every other copy becomes `Quarantine(digest, size, sortedLinks, keeperFirstLink, FromPick(pick))`, and rules and exceptions are ignored for that set. A picked set never conflicts.
3. **Every other set, per rule `i` in order.** Qualifying copies are those with **any** link under `keep`. If there are none, the rule gives nothing for this set. Otherwise the keeper is the qualifying copy whose first sorted link is smallest. Candidates are copies that meet all three conditions:
   - every link is under `quarantineIn`;
   - no link is under `keep` (so a rule never quarantines one of its own qualifying copies);
   - no link is under any exception.

   Each candidate becomes `(copy, rule i, keeper_i)`.
4. **Conflict check per set.** Let `Q` be the set of candidate copies, across all rules. If any candidate's keeper copy is in `Q`, the set gives no operations and becomes `Conflict(digest, sources)`, where `sources` is every rule with at least one candidate in that set, as `FromRule`, by ascending index, without repeats. Otherwise each copy in `Q` gives one `Quarantine`, from the candidate with the lowest rule index (AC-7).
5. **Order.** Operations are ordered by source (every `FromRule` by `index`, then every `FromPick` by `pick.digest` with `String.compareTo`), then by first link (`Path.compareTo`). Conflicts are ordered by `digest` (`String.compareTo`).
6. **Totals.** `files` = the sum of `links.size()`; `bytes` = the sum of `size` (once per operation); `sets` = the number of distinct digests among operations.
7. **Left behind.** For each rule `i`, with `q = quarantineIn`: `files` = the sum of `FolderTotal.files` over the folders under `q`, minus the number of quarantined links (from all operations) under `q`; `bytes` = the sum of `FolderTotal.bytes` over the same folders, minus `size` for each such link (per link, matching `FolderTotal`'s per-path count). Each field is clamped at 0, which matters only for a hand-built `ScanResult` whose folder totals do not cover its sets. `LeftBehind(i, q, files, bytes)`, in rule order.
8. **ID.** As described in the next section, computed over the ordered operations and conflicts.

## Plan ID canonical encoding

The ID is the lowercase hex (`HexFormat.of().formatHex`) of SHA-256 (`MessageDigest.getInstance("SHA-256")`) over this byte string. Every field is length-prefixed, so the encoding is unambiguous even for paths that contain tabs or newlines:

```
str(s)    = ASCII(decimal(byteLength(UTF8(s)))) ":" UTF8(s)
num(n)    = str(Long.toString(n))                  -- int and long alike, decimal, "-" only for negatives
path(p)   = str(p.toString())
list(xs)  = num(xs.size()) then each element's encoding, in list order
source    = str("rule") num(index) path(rule.keep) path(rule.quarantineIn)
          | str("pick") str(pick.digest) path(pick.keep)
operation = str("quarantine") str(digest) num(size) list(links as path) path(keeper) source
conflict  = str(digest) list(sources as source)

message   = str("dfs-deletion-plan/1") list(operations as operation) list(conflicts as conflict)
```

The input is only the plan's operations and conflicts, in plan order. Totals and `leftBehind` are left out because they are derived (totals from the operations, `leftBehind` from the scan). An empty plan encodes as `19:dfs-deletion-plan/11:01:0`, so its ID is a fixed constant. `Path.toString()` uses the platform's separator, so an ID is stable on one platform and is not meant to compare across platforms.

## State and transitions

None: this use case has no lifecycle.

## Concurrency and atomicity

- Which thread runs what: everything runs on the caller's thread, synchronously.
- Where per-request state lives: nowhere beyond local variables. The service has no fields, so concurrent calls share nothing.
- Races: none.

## Checklist disposition

1. Unknown or stale IDs: not applicable. The preview takes no ID, and the plan ID is checked by a later execution, not here (decision 1).
2. Results as values, bugs as exceptions: an invalid request is `Failure` with one of four new named codes (step 1). A `null` scan, request, list or list element is a `NullPointerException`: `Objects.requireNonNull` in `preview` for the two arguments, and the `PlanRequest` constructor for the lists. There are no optional arguments, so no overloads.
3. Status model: not applicable, no lifecycle.
4. Races and who wins: not applicable. The call is synchronous and has no shared state.
5. Validation before side effects: there are no side effects. All validation (step 1) runs before any planning, so a `Failure` carries no partial plan. Nothing re-validates later.
6. State placement: not applicable, no per-request state. The service has no fields at all (a Definition of done item).
7. Progress and observation: not applicable. The call is synchronous and fast, and reports nothing while it runs.
8. Caller-supplied callbacks: not applicable, it calls no caller code.
9. Failures off the caller's thread: not applicable, nothing runs off the caller's thread.
10. Query results: the whole `DeletionPlan`, unpaged, in the order of step 5. It is never partial: it is either a complete plan or a `Failure` with no plan. Conflicted sets are reported in `conflicts`, not dropped silently.
11. Executor contract: not applicable, no executor (decision 8).
12. Retention: not applicable, nothing is retained. The caller keeps the plan (decision 1). Storing plans is out of scope.
13. Testability: plain value tests on hand-built `ScanResult`/`DuplicateSet` values whose paths need not exist. No timing, fakes or seams. AC-2 records a `@TempDir` listing before and after.
14. Exported surface: the port and every new type are in `domain.usecases.query` and `domain.model`, which are already exported, and the factory method is in exported `domain.factory`. `module-info.java` is unchanged, and no first-slice type is replaced.

## Configuration

- None. The preview reads no `ScanConfig` or other setting.

## Expected breakage

None.

## Open design notes (as of the design phase)

- **Conflict sources** (ticket: "the sources (rules or pick) involved"): every rule with a candidate in the set, since all of their entries are dropped. Both ticket scenarios list exactly R1 and R2 under this rule.
- **"Not under keep"** in decision 2 is read as "no link under `keep`", the same test that makes a copy qualify as a keeper. A rule therefore never quarantines a copy it could keep, and its keeper is never its own candidate.
- **Pick keeper field**: decision 5 says `keeper` is the keeper copy's first link. For a pick whose `keep` is not the first link of its copy, the recorded `keeper` is that first link (the same physical file). AC-5's scenarios use single-link copies.
- **DUPLICATE_PICK precedence**: for a pick, UNKNOWN_SET is checked first, then PATH_NOT_IN_SET, then DUPLICATE_PICK (the ticket's listing order).
- **Clamping** left-behind fields at 0 (step 7) keeps `LeftBehind`'s non-negative check from throwing on hand-built test scans that have no folder totals.
- **Factory and bean are real, not stubbed.** A throwing `ScannerFactories.previewDeletionPlan()` would fail every context that loads `ScannerAutoConfiguration` (all `scanner-spring` and `cli` Spring tests). For the test-author: the red gate rejects a story test that passes against stubs. So the AC-14 test must preview through the factory, and through the autoconfigured bean, before its back-off assertion, either in one test method or by having each method call `preview` first.
- **For the test-author, AC-12**: build the `PlanRequest` with a null element *inside* the `assertThrows` lambda, together with the `preview` call. That way the test is red on the stub now (no constructor check yet) and green once the constructor throws.
- Message tests should match `rule <i>:` and `pick <digest>:` as substrings, not the whole message.

## Stubs (as of the design phase)

- Files stubbed: `file-scanner/src/main/java/us/pochaev/dfs/scanner/application/PreviewDeletionPlanService.java` (`preview`). Port and records written without bodies or checks: `domain/usecases/query/PreviewDeletionPlan.java`, `domain/model/{PlanRequest,KeepAndQuarantine,KeepCopy,DeletionPlan,Operation,Source,Conflict,PlanTotals,LeftBehind}.java`. Edited: `domain/model/ErrorCode.java` (4 values), `domain/factory/ScannerFactories.java` (`previewDeletionPlan()`), `scanner-spring/.../ScannerAutoConfiguration.java` (bean).
- Each stub throws `new UnsupportedOperationException("not implemented: DFS-022")` per D2.
- Commit: filled in after the design commit.
