# DFS-046 verification

## Product owner review

### What you can do now

The server can now answer the two folder questions from a completed scan, in Java, without the CLI export or the SQL tool. "Can this folder go?" is answered by a summary per folder: how many of its files have a copy outside it, plus its internal duplicates. "Where are the copies?" is answered by pairs of folders, with both directions in one record. You can also ask about any two folders, such as `Backup/` against `Photos/`. This is the engine for the Overlaps API (DFS-048) and the Overlaps tab (DFS-050). Nothing calls it over HTTP yet.

### Acceptance criteria

- **DFS-046.AC-1** met: 1,000 of 1,000 files show a copy elsewhere, while the two pairs show 600 and 400.
- **DFS-046.AC-2** met: one pair per two folders, `a` before `b`, both directions; `full()` is true only for a non-empty, fully covered side.
- **DFS-046.AC-3** met: a file in three folders counts in all three pairs and once in each summary.
- **DFS-046.AC-4** met: hard links in another folder never count. One scenario was tested in a different form; see "Decided without you".
- **DFS-046.AC-5** met: a copy elsewhere means a copy outside the subtree, so sibling copies cancel out in the parent's summary.
- **DFS-046.AC-6** met: internal duplicates (k − 1 per set, and their bytes) are counted alongside the copies elsewhere, not added to them; a folder is never paired with itself.
- **DFS-046.AC-7** met: the listed pairs are only between folders that hold the files directly; `pair(x, y)` counts recursively in either order and refuses equal, nested or outside folders.
- **DFS-046.AC-8** met: summaries roll up through folders that hold no files directly and stop at the root; a folder outside the roots gets no summary.
- **DFS-046.AC-9** met: both lists come in the fixed order and hold only non-empty entries.
- **DFS-046.AC-10** met: the drive-sized synthetic scan took 940 ms against a 10 s budget, with 224,349 pairs.
- **DFS-046.AC-11** met: the ArchUnit rule passes for the package, and fails on a fixture that imports Spring and `server.scan`.
- **DFS-046.AC-12** met (read): `folder-overlap-analysis.md` now names DFS-046, `us.pochaev.dfs.server.analysis` and `OverlapAnalysis.of`, and its "Open" section records the measured scale.

### Try it

Nothing is visible to a user yet, since DFS-048 adds the API. To see it work:

```
./gradlew :server:test --tests "us.pochaev.dfs.server.analysis.*" --tests "us.pochaev.dfs.server.ArchitectureTest" -i
```

Look for `DFS-046.AC-10 OverlapAnalysis.of: <n> ms, <n> pairs, <n> summaries` in the output, and for 22 tests passing.

### Decided without you

- **AC-4, "a set of hard links alone":** this cannot be built as a `DuplicateSet`, because the scanner refuses a set with only one physical copy. Two links of one file with no other copy therefore show up only in folder totals. The test checks that such a set is refused, and that those two files add nothing to any summary or pair.
- **A physical copy with links both inside and outside a folder:** it counts as surviving outside, so the other copies inside have "a copy elsewhere". It also counts as one of the k copies inside, for internal duplicates.
- **`summary(folder)` for a folder inside a root that the scan holds nothing for:** it answers a summary of zeros, not "empty". Only a folder outside every root gets an empty answer.
- **Pairs between two folders that only hard links connect:** for example, the only copy in folder A is linked into folder B. Both sides would be 0, so the pair is dropped, as decision 5 lists only non-empty pairs.
- **Totals in a pair:** the precomputed pairs use each folder's direct totals, since those pairs are between folders that hold the files directly. `pair(x, y)` uses subtree totals, since it counts recursively.
- **One more refusal case:** AC-7 also refuses an outside folder given as the first argument, not only as the second.
- **No index behind `pair(x, y)`:** each call walks every set once (about 150,000 paths on a drive-sized scan). That is fine for one query at a time; DFS-048 can cache it if needed.

### Known limits

- No HTTP, no paging, and no caching per scan (DFS-048); no screen (DFS-050).
- The scale was measured on a synthetic result only; a real export has not been run through it yet. The "Open" section says so.
- Nothing checks that the Java, SQL and JavaScript implementations agree on one export (out of scope).

### Evidence

- `server/src/test/java/us/pochaev/dfs/server/analysis/OverlapAnalysisTest.java`: 17 tests (13 methods, AC-7's refusals run as 5 cases), all passing.
- `OverlapAnalysisScaleTest`: 1 test, passing: `OverlapAnalysis.of: 940 ms, 224349 pairs, 9091 summaries`.
- `ArchitectureTest`: 4 tests passing, 2 of them for DFS-046.AC-11.
- `./gradlew build` green on 2026-11-02, including the Vitest suite (21 tests) and the scanner's ArchUnit and module-export tests.
- Red run before the implementation: below.

### How it went

Hand-implemented (`archetype: none`), tests first. Stubs and 18 failing tests were committed as `c285230`. The implementation passed every test on its first run, with no defects and no retries.

Ready for the user's approval.

## Red run (decision 1)

2026-11-02, on the branch `tickets/DFS-046-overlap-analysis-java`, forked from `main` at `4ef26de`. The tests and stubs are commit `c285230`; the implementation is the commit that follows it on the branch.

`./gradlew :server:test --tests "us.pochaev.dfs.server.analysis.*" --tests "us.pochaev.dfs.server.ArchitectureTest"` ran 22 tests: 18 failed, `BUILD FAILED`.

- **The 18 failures:** every DFS-046 analysis test (AC-1 to AC-10: 12 scenario methods, the 5 AC-7 refusal cases, and the AC-10 scale test). Each threw `java.lang.UnsupportedOperationException` ("not implemented: DFS-046") from the `OverlapAnalysis.of` stub. That is the right reason: the tests compile, and they reach the code under test.
- **The 4 passes:** all in `ArchitectureTest`, the two existing DFS-035.AC-5 tests and the two DFS-046.AC-11 tests. AC-11 is a guard, so it is green from the start: the stub classes obey the rule, and `AnalysisGuardViolationFixture` shows the rule fires on a class that uses `org.springframework.util.Assert` and `us.pochaev.dfs.server.scan.ScanRegistry`.

## Green run

2026-11-02, after the implementation, the same command ran 22 tests and all passed. `./gradlew build` passed too.

## Touches

Files changed on the branch since `4ef26de`: the four classes in `server/src/main/java/us/pochaev/dfs/server/analysis`, the four test files in `server/src/test/java/us/pochaev/dfs/server/analysis`, `server/src/test/java/us/pochaev/dfs/server/ArchitectureTest.java`, `docs/design/folder-overlap-analysis.md`, and this ticket's folder. All of these are inside `touches`.

## Summary

- DFS-046.AC-1 through AC-11: met, each by automated tests in `OverlapAnalysisTest`, `OverlapAnalysisScaleTest` and `ArchitectureTest`; see the list above.
- DFS-046.AC-12: met by reading `folder-overlap-analysis.md`.
- One scenario (AC-4, a set of hard links alone) was tested in the only form the domain model allows; see "Decided without you".
- Ready for the user's approval.
