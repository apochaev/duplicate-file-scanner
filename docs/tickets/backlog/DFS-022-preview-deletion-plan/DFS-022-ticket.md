---
id: DFS-022
title: Preview a deletion plan
status: ready
type: feature
scope: [file-scanner]
archetype: use-case
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
gates: [design, done]
autorun: true
priority: medium
lane: backend
depends_on: [DFS-019]
touches:
  - us.pochaev.dfs.scanner.domain.usecases.query
  - us.pochaev.dfs.scanner.domain.model
  - us.pochaev.dfs.scanner.application
  - us.pochaev.dfs.scanner.domain.factory
  - scanner-spring/src/main/java/us/pochaev/dfs/scannerspring/ScannerAutoConfiguration.java
  - scanner-spring/src/test/java/us/pochaev/dfs/scannerspring/ScannerAutoConfigurationTest.java
blocked_reason:
created: 2026-10-13
updated: 2026-11-01
completed:
---

# DFS-022: Preview a deletion plan

## Story

As a user of the duplicate file scanner
I want to apply deletion rules to a completed scan and get back the plan they produce: which files would be removed, which copy each one keeps, and where rules conflict
So that I can review a bulk delete before anything touches the disk, with the guarantee that no plan ever removes the last copy of a file

## Context

Split from DFS-021 (its decision 1): this ticket holds the plan logic in `file-scanner`, run through the pipeline as `use-case`; DFS-021 is the CLI command (`wiring`) that depends on it.

CLAUDE.md applies directly:

- **Never delete the last copy.** Every `DeletionPlan` must leave at least one verified surviving copy per `DuplicateSet`. Rules can conflict (folder1's copy may itself be marked for deletion by another rule), so the plan is validated as a whole, not rule by rule.
- **Preview is a pure query; execution is a separate command.** `PreviewDeletionPlan` never touches disk state. Executing (quarantine, decision 2) and the byte-for-byte re-verification before it (decision 4) are later tickets.
- **Hard links are not duplicates.** A `DuplicateSet` already groups paths into `PhysicalCopy`s (DFS-017): deleting one link of a copy frees no space while another link of that copy remains.
- **Location model** (decision 1): rules are stated by source location, e.g. "delete every file in folder2 that has a confirmed duplicate in folder1", where a folder also covers its subfolders. `LocationOverlap(A, B)` is the second view of a result; it is not built yet.
- Provisional port sketch: `PreviewDeletionPlan` is a query (`domain.usecases.query`); `DeletionPlan` is the type it returns.

The local UI (`docs/design/ui.md`, U5–U7; the frontend lane's plan editor) is the main caller and asks for a specific plan model:

- **U5.** A plan is a list of typed operations; the first version has only `Quarantine`. `Move` and a `Consolidate` macro may be added later without reshaping the model. The preview lists the unique files the plan leaves behind.
- **U6.** A plan is built from *rules* ("keep A, quarantine in B everything that has a copy in A", recursive), *exceptions* (files or folders excluded from a rule) and *single-set picks* (the chosen keeper for one set). Every preview entry records which rule or pick produced it.
- **U7.1.** The plan check reports totals (files, bytes, sets touched), entries grouped by rule with their keeper, the files left behind, and last-copy conflicts, which block confirming.
- **U7.** A previewed plan is frozen and has an ID; a later execution runs exactly that plan and refuses if it was edited. DFS-033 (drift check) and DFS-034 (content verify) take a previewed plan as input.

What exists: a completed scan's `ScanResult(roots, folders, sets, skipped, excluded)` through `GetScanResult` (DFS-018, DFS-030), `DuplicateSet(size, digest, files, copies)`, `PhysicalCopy(links)`, `FileEntry(path, size, lastModified)`, `Result`/`ErrorCode` for expected failures, and `ScannerFactories.scanUseCases(Executor)` returning `ScanUseCases` (DFS-019), which a new port would join.

### Decisions

1. **A pure function of a `ScanResult`, with a content-derived plan ID.** `PreviewDeletionPlan.preview(ScanResult scan, PlanRequest request)` returns `Result<DeletionPlan>`. It reads only the `ScanResult` value, never the disk and never any scanner state, so the server (after `GetScanResult`) and the CLI (from a JSONL report, DFS-021) can both call it. The plan's `id` is the lowercase hex SHA-256 of its entries in a fixed canonical order (the exact encoding is the architect's, written in `design.md`): the same request on the same scan always yields the same ID, and any change in the plan's entries yields a different one, so a later execution can detect an edited plan by recomputing it (`ui.md` U7). No plan repository and no `PlanId` lookup; the caller keeps the plan it previewed.
2. **A request is rules, global exceptions and single-set picks; picks win for their set.** `PlanRequest(rules, exceptions, picks)`:
   - **Rule** `KeepAndQuarantine(Path keep, Path quarantineIn)`: in every set that has a copy under `keep`, each copy under `quarantineIn` and not under `keep` becomes a `Quarantine` entry whose keeper is a copy under `keep`, attributed to the rule. A set with no copy under `keep` gets nothing from that rule.
   - **Exception** (a file or folder `Path`): no rule quarantines a copy at or under it. Exceptions only ever reduce what is quarantined.
   - **Pick** `KeepCopy(String digest, Path keep)`: in the set with that digest, `keep` is the keeper and every other copy is quarantined, attributed to the pick; rules and exceptions are ignored for that set (the pick is the user's explicit choice for it).
   - "Under" is `Path.startsWith` on the paths as the scan recorded them: it covers subfolders, matches whole components only (`C:/A` does not cover `C:/AB`) and uses the platform's case rule.
3. **A set is in conflict when any of its entries' keepers is itself quarantined.** The plan is computed per set, then validated as a whole: if any entry's keeper is also quarantined (by another rule, as in "keep A, quarantine in B" with "keep B, quarantine in A"), the set contributes no entries and is listed in `plan.conflicts` with its digest and the sources (rules or pick) involved. The rest of the plan stands. So every entry's keeper survives the plan, and no set can lose its last copy (CLAUDE.md, "Never delete the last copy"). When several rules quarantine the same copy and every keeper survives, the copy gets one entry, attributed to the earliest rule in request order. A later execution refuses a plan with conflicts (`ui.md` U7.1: conflicts block confirming).
4. **The unit is the physical copy; the keeper is the first qualifying copy in path order.** A `Quarantine` entry names one `PhysicalCopy` and lists all its links, which move together; it frees the set's size once. For quarantining, a copy is under a folder only when **all** its links are under it, and a copy with any link at or under an exception is never quarantined by a rule: so a rule never moves a link outside the folder it names, and a copy only partly inside `quarantineIn` is left alone (moving part of it frees nothing; CLAUDE.md, "Hard links are not duplicates"). For keeping, a copy with **any** link under `keep` qualifies; among qualifying copies the keeper is the one whose first link (links sorted by path) sorts first, so the choice, and with it the plan ID, is deterministic. A pick's `keep` path names the copy that has that link.
5. **Plan shape: typed operations ordered by source.** Records in `domain.model`: `DeletionPlan(String id, List<Operation> operations, List<Conflict> conflicts, PlanTotals totals, List<LeftBehind> leftBehind)` (decision 6). `Operation` is a sealed interface whose only variant is `Quarantine(String digest, long size, List<Path> links, Path keeper, Source source)`: `links` are the copy's links sorted by path, `keeper` is the keeper copy's first link. `Source` is a sealed interface: `FromRule(int index, KeepAndQuarantine rule)` (index in request order) or `FromPick(KeepCopy pick)`. `Conflict(String digest, List<Source> sources)`. `PlanTotals(long files, long bytes, int sets)`: links moved, bytes freed (each set's size once per quarantined copy), and sets with at least one operation. Operations are ordered by source (rules by index, then picks by digest), then by first link; conflicts by digest; the ID is computed over that order. A later `Move` or `Consolidate` is a new `Operation` variant (`ui.md` U5).
6. **Left behind: counts per rule.** A scan records only duplicated files by name, and every scanned file in a folder's direct `FolderTotal(folder, files, bytes)` count (DFS-030). So `leftBehind` holds one `LeftBehind(int ruleIndex, Path folder, long files, long bytes)` per rule, in rule order: `folder` is the rule's `quarantineIn`; `files` and `bytes` are the sums of `FolderTotal` over every folder at or under it, minus the links and bytes the plan quarantines at or under it (from any source). These are *scanned* files (at least the minimum size, not excluded by name); the UI gets their names from `ListDirectory` (DFS-032). A rule whose folder has no `FolderTotal` reports 0 files and 0 bytes.
7. **An invalid request is refused whole, with the first problem found.** Before any planning, `preview` checks the rules in request order, then the picks in request order, and returns `Failure` for the first problem, with a message naming the rule index or pick digest; nothing is planned from a half-valid request. New `ErrorCode`s: `INVALID_RULE` (a rule's `quarantineIn` equals its `keep` or lies under it, so it could never quarantine anything), `UNKNOWN_SET` (a pick's digest is not a set of the scan), `PATH_NOT_IN_SET` (a pick's `keep` is not a link of that set), `DUPLICATE_PICK` (a second pick for the same digest). A `keep` under `quarantineIn` ("keep `C:/B/best`, quarantine the rest of `C:/B`") is valid. Exceptions are not validated: one that matches nothing has no effect. An empty request is valid and gives an empty plan (no operations, conflicts or left-behind entries, totals 0) with a stable ID. A `null` argument, list or list element throws `NullPointerException` (`Result`'s contract, DFS-018 decision 4).
8. **Exposure: its own factory method and Spring bean, not a `ScanUseCases` component.** The preview holds no scan state and needs no executor, so it does not join `ScanUseCases` (whose components share one scan repository, DFS-019 decision 2, and whose constructor every override would have to change). `ScannerFactories.previewDeletionPlan()` returns a `PreviewDeletionPlan`; `ScannerAutoConfiguration` (DFS-031) gains a `PreviewDeletionPlan` bean from it with `@ConditionalOnMissingBean`, so the server can inject it and an app can replace it (CLAUDE.md decision 6). All new types go in already exported packages (`domain.model`, `domain.usecases.query`); `module-info.java` is unchanged. Use-case checklist items 1, 3, 4, 6, 7, 8, 9, 11, 12 do not apply: the preview takes no ID, has no lifecycle or per-request state, calls no caller code and runs synchronously on the caller's thread. Item 13 reduces to plain value tests.

## Acceptance criteria

<!-- Filled during refinement. State the full ID once, then one testable statement per line, numbered from 1. Never renumber or delete: retire with `- **AC-<n> (retired)** <statement>. Retired <date>: <reason>`. -->

The full ID of each criterion is `DFS-022.AC-<n>`.

- **AC-1** `preview` returns `Success` with a `DeletionPlan` whose `id` is 64 lowercase hex characters; previewing the same request on the same `ScanResult` twice gives equal IDs, and a request that changes the plan's entries gives a different ID.
- **AC-2** `preview` touches no disk state: it succeeds on a `ScanResult` whose paths do not exist on disk, and creates, changes or deletes no file.
- **AC-3** A rule `KeepAndQuarantine(A, B)` quarantines, in every set with a copy under `A`, each copy under `B` and not under `A`, with a copy under `A` as keeper and the rule as the entry's source; a set with no copy under `A` gets no entry, and `A` does not cover a sibling folder whose name merely starts with `A`'s name.
- **AC-4** No rule quarantines a copy at or under an exception path, whether the exception names the file itself or a folder above it.
- **AC-5** A pick `KeepCopy(digest, P)` makes `P` the keeper of that set and quarantines every other copy of it, with the pick as source, regardless of any rule or exception touching that set.
- **AC-6** When an entry's keeper would itself be quarantined, that set contributes no entries and appears in `conflicts` with its digest and every source involved, while other sets' entries are unaffected; so in every previewed plan, no entry's keeper is quarantined and every set keeps at least one copy.
- **AC-7** A copy quarantined by several rules whose keepers all survive appears in exactly one entry, attributed to the earliest of those rules in request order.
- **AC-8** A rule quarantines a physical copy only when all its links are under `quarantineIn`; the entry lists every link of that copy and the plan counts the set's size once for it, and a copy with only some links under `quarantineIn` gets no entry.
- **AC-9** The recorded keeper is, among the copies with any link under `keep`, the one whose first link sorts first by path.
- **AC-10** Every operation is a `Quarantine` carrying digest, size, sorted links, keeper and source; operations are ordered by source (rules in request order, then picks by digest) and then by first link, conflicts by digest; and `totals` gives the links moved, the bytes freed (each set's size once per quarantined copy) and the number of sets with an operation.
- **AC-11** `leftBehind` has one entry per rule, in rule order, giving the rule's `quarantineIn` folder and the scanned files and bytes at or under it (summed from the scan's folder totals) minus those the plan quarantines there.
- **AC-12** An empty request returns `Success` with no operations, conflicts or left-behind entries, totals of 0 files, 0 bytes and 0 sets, and the same ID every time; a `null` scan, request, list or list element throws `NullPointerException`.
- **AC-13** An invalid request returns `Failure` with the code of the first problem in request order (rules, then picks) and no plan: `INVALID_RULE` when a rule's `quarantineIn` equals its `keep` or lies under it, `UNKNOWN_SET` for a pick whose digest is not in the scan, `PATH_NOT_IN_SET` for a pick whose `keep` is not a link of that set, `DUPLICATE_PICK` for a second pick of one digest; a rule whose `keep` lies under its `quarantineIn` is valid.
- **AC-14** `ScannerFactories.previewDeletionPlan()` returns a working `PreviewDeletionPlan`, and `ScannerAutoConfiguration` provides a `PreviewDeletionPlan` bean that backs off when the application declares its own.

## Scenarios

```gherkin
Feature: Previewing a deletion plan

  @AC-1
  Scenario: The same request on the same scan gives the same plan ID
    Given a ScanResult with one set of two copies, C:/A/x.jpg and C:/B/x.jpg
    And a request that keeps C:/A and quarantines its copies in C:/B
    When the plan is previewed twice
    Then both plans have the same 64-character lowercase hex id

  @AC-1
  Scenario: A request that changes the plan's entries changes the plan ID
    Given the same ScanResult
    And a second request that keeps C:/B and quarantines its copies in C:/A
    When both requests are previewed
    Then the two plans have different ids

  @AC-2
  Scenario: Preview reads only the scan result
    Given a ScanResult whose paths C:/A/x.jpg and C:/B/x.jpg do not exist on disk
    And a temp folder whose file listing is recorded
    When the plan is previewed
    Then preview returns Success
    And the temp folder's listing is unchanged

  @AC-3
  Scenario: A rule quarantines the copies under B that have a copy under A
    Given a ScanResult with set S1 (C:/A/x.jpg, C:/B/x.jpg, C:/B/sub/x2.jpg) and set S2 (C:/B/y.jpg, C:/C/y.jpg)
    And a request with rule R1 = KeepAndQuarantine(C:/A, C:/B)
    When the plan is previewed
    Then it has exactly two Quarantine entries, C:/B/x.jpg and C:/B/sub/x2.jpg, each with keeper C:/A/x.jpg and source R1
    And no entry touches set S2

  @AC-3
  Scenario: A rule folder covers whole path components only
    Given a ScanResult with one set (C:/A/x.jpg, C:/AB/x.jpg)
    And a request with rule KeepAndQuarantine(C:/A, C:/AB)
    When the plan is previewed
    Then C:/AB/x.jpg is quarantined with keeper C:/A/x.jpg
    And with rule KeepAndQuarantine(C:/AB, C:/A) instead, C:/A/x.jpg is quarantined with keeper C:/AB/x.jpg

  @AC-4
  Scenario: An exception keeps a file a rule would quarantine
    Given set S1 (C:/A/x.jpg, C:/B/x.jpg, C:/B/old/x.jpg)
    And rule KeepAndQuarantine(C:/A, C:/B) and exception C:/B/old
    When the plan is previewed
    Then C:/B/x.jpg is quarantined
    And C:/B/old/x.jpg is not in any entry

  @AC-4
  Scenario: An exception naming the file itself
    Given set S1 (C:/A/x.jpg, C:/B/x.jpg) and rule KeepAndQuarantine(C:/A, C:/B)
    And exception C:/B/x.jpg
    When the plan is previewed
    Then the plan has no entries

  @AC-5
  Scenario: A pick overrides the rules for its set
    Given set S1 with digest d1 (C:/A/x.jpg, C:/B/x.jpg, D:/x.jpg)
    And rule KeepAndQuarantine(C:/A, C:/B), exception C:/B/x.jpg and pick KeepCopy(d1, D:/x.jpg)
    When the plan is previewed
    Then C:/A/x.jpg and C:/B/x.jpg are quarantined with keeper D:/x.jpg and the pick as source

  @AC-6
  Scenario: Two rules in opposite directions conflict instead of removing both copies
    Given set S1 with digest d1 (C:/A/x.jpg, C:/B/x.jpg) and set S2 (C:/A/y.jpg, C:/C/y.jpg)
    And rules R1 = KeepAndQuarantine(C:/A, C:/B), R2 = KeepAndQuarantine(C:/B, C:/A) and R3 = KeepAndQuarantine(C:/A, C:/C)
    When the plan is previewed
    Then no entry touches S1
    And conflicts holds one item for d1 naming R1 and R2
    And C:/C/y.jpg is quarantined with keeper C:/A/y.jpg and source R3

  @AC-6
  Scenario: A keeper quarantined by another rule is a conflict even if a third copy survives
    Given set S1 with digest d1 (C:/A/x.jpg, C:/B/x.jpg, D:/x.jpg)
    And rules R1 = KeepAndQuarantine(C:/A, C:/B) and R2 = KeepAndQuarantine(D:/, C:/A)
    When the plan is previewed
    Then no entry touches S1
    And conflicts holds one item for d1 naming R1 and R2

  @AC-7
  Scenario: Two rules quarantining the same copy give one entry
    Given set S1 (C:/A/x.jpg, D:/A/x.jpg, C:/B/x.jpg)
    And rules R1 = KeepAndQuarantine(C:/A, C:/B) and R2 = KeepAndQuarantine(D:/A, C:/B)
    When the plan is previewed
    Then C:/B/x.jpg appears in exactly one entry, with source R1 and keeper C:/A/x.jpg

  @AC-8
  Scenario: Hard links of one copy are quarantined together and counted once
    Given a set of size 5000 with copy K (C:/A/x.jpg) and copy Q with links C:/B/x.jpg and C:/B/sub/x-link.jpg
    And rule KeepAndQuarantine(C:/A, C:/B)
    When the plan is previewed
    Then there is one Quarantine entry, for copy Q, listing C:/B/sub/x-link.jpg and C:/B/x.jpg
    And the plan frees 5000 bytes

  @AC-8
  Scenario: A copy only partly inside the quarantine folder is left alone
    Given a set with copy K (C:/A/x.jpg) and copy Q with links C:/B/x.jpg and C:/Other/x.jpg
    And rule KeepAndQuarantine(C:/A, C:/B)
    When the plan is previewed
    Then the plan has no entries

  @AC-9
  Scenario: The keeper is the first qualifying copy by path
    Given a set with copies C:/A/z.jpg, C:/A/sub/a.jpg and C:/B/x.jpg
    And rule KeepAndQuarantine(C:/A, C:/B)
    When the plan is previewed
    Then C:/B/x.jpg is quarantined with keeper C:/A/sub/a.jpg

  @AC-10
  Scenario: Operations are grouped by source and the totals add up
    Given set S1 of size 100 (C:/A/x.jpg, C:/B/x.jpg, C:/B/y/x.jpg) and set S2 of size 40 with digest d2 (D:/k.txt, C:/C/k.txt)
    And rules R0 = KeepAndQuarantine(C:/Z, C:/C) and R1 = KeepAndQuarantine(C:/A, C:/B), and pick KeepCopy(d2, D:/k.txt)
    When the plan is previewed
    Then the operations are, in order, C:/B/x.jpg (FromRule 1), C:/B/y/x.jpg (FromRule 1), C:/C/k.txt (FromPick d2)
    And totals are 3 files, 240 bytes and 2 sets

  @AC-11
  Scenario: Left behind counts what stays in the quarantine folder
    Given folder totals C:/B (3 files, 300 bytes), C:/B/sub (2 files, 50 bytes) and C:/A (1 file, 100 bytes)
    And set S1 of size 100 (C:/A/x.jpg, C:/B/x.jpg)
    And rule R0 = KeepAndQuarantine(C:/A, C:/B)
    When the plan is previewed
    Then leftBehind is one entry for rule 0, folder C:/B, 4 files and 250 bytes

  @AC-11
  Scenario: A rule folder without scanned files leaves nothing behind
    Given the same scan and a second rule R1 = KeepAndQuarantine(C:/A, C:/Empty)
    When the plan is previewed
    Then leftBehind's entry for rule 1 has folder C:/Empty, 0 files and 0 bytes

  @AC-12
  Scenario: An empty request gives an empty plan with a stable ID
    Given a ScanResult with one set (C:/A/x.jpg, C:/B/x.jpg)
    And a request with no rules, exceptions or picks
    When the plan is previewed twice
    Then both return Success with no operations, conflicts or leftBehind entries
    And totals are 0 files, 0 bytes and 0 sets
    And both plans have the same id

  @AC-12
  Scenario: A null argument is a caller bug
    When preview is called with a null ScanResult, a null request, or a request whose rule list contains null
    Then each call throws NullPointerException

  @AC-13
  Scenario: A rule that could never quarantine anything is refused
    Given a ScanResult with one set (C:/A/x.jpg, C:/B/x.jpg)
    When a request with rule KeepAndQuarantine(C:/A, C:/A) is previewed
    Then it returns Failure with code INVALID_RULE and a message naming rule 0
    And a request with rule KeepAndQuarantine(C:/A, C:/A/sub) also returns Failure with code INVALID_RULE

  @AC-13
  Scenario: Keeping a subfolder of the quarantine folder is valid
    Given a ScanResult with one set (C:/B/best/x.jpg, C:/B/x.jpg)
    And a request with rule KeepAndQuarantine(C:/B/best, C:/B)
    When the plan is previewed
    Then C:/B/x.jpg is quarantined with keeper C:/B/best/x.jpg

  @AC-13
  Scenario: Picks that do not match the scan are refused
    Given a ScanResult with set d1 (C:/A/x.jpg, C:/B/x.jpg)
    When a request with pick KeepCopy(d9, C:/A/x.jpg) is previewed
    Then it returns Failure with code UNKNOWN_SET
    And a request with pick KeepCopy(d1, C:/C/x.jpg) returns Failure with code PATH_NOT_IN_SET
    And a request with picks KeepCopy(d1, C:/A/x.jpg) and KeepCopy(d1, C:/B/x.jpg) returns Failure with code DUPLICATE_PICK

  @AC-13
  Scenario: The first problem in request order is reported
    Given a ScanResult with set d1 (C:/A/x.jpg, C:/B/x.jpg)
    And a request with rules KeepAndQuarantine(C:/A, C:/B) and KeepAndQuarantine(C:/C, C:/C), and pick KeepCopy(d9, C:/A/x.jpg)
    When the plan is previewed
    Then it returns Failure with code INVALID_RULE and a message naming rule 1

  @AC-14
  Scenario: The factory and the Spring wiring expose the preview
    Given ScannerFactories.previewDeletionPlan()
    When it previews rule KeepAndQuarantine(C:/A, C:/B) on a ScanResult with set (C:/A/x.jpg, C:/B/x.jpg)
    Then it returns a plan quarantining C:/B/x.jpg
    And an application context with ScannerAutoConfiguration has one PreviewDeletionPlan bean
    And a context that declares its own PreviewDeletionPlan bean uses that one instead
```

## Scope

**Touches:**
- `domain.usecases.query`: the `PreviewDeletionPlan` port
- `domain.model`: `DeletionPlan`, `PlanRequest`, the rule, pick, operation, source, conflict, totals and left-behind types, and the new `ErrorCode` values
- `application`: the service that validates a request and builds the plan
- `domain.factory`: `ScannerFactories.previewDeletionPlan()`
- `scanner-spring/src/main/java/us/pochaev/dfs/scannerspring/ScannerAutoConfiguration.java`: the `PreviewDeletionPlan` bean
- `scanner-spring/src/test/java/us/pochaev/dfs/scannerspring/ScannerAutoConfigurationTest.java`: its bean and back-off tests

**Out of scope:**
- Executing a plan: quarantine, permanent delete, undo, purge (CLAUDE.md decision 2)
- Re-verification at execution time (decision 4)
- The CLI command (DFS-021)
- `LocationOverlap` as its own query: rules are stated by folder directly (decision 2)
- Storing plans or looking them up by ID (decision 1); the drift check (DFS-033) and content verify (DFS-034)
- `Move` and `Consolidate` operations (`ui.md` U5; later variants of `Operation`)
- The server endpoint for the plan (frontend lane)

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] No files changed outside the paths listed under Touches
- [ ] `design.md` addresses every item of the `use-case` edge-case checklist or marks it not applicable with a one-line reason (decision 8 lists the ones expected not to apply)
- [ ] `design.md` gives the exact canonical encoding the plan ID is computed over (decision 1)
- [ ] The service implementing `PreviewDeletionPlan` has no fields: it is a pure function of its arguments
- [ ] No test uses `Thread.sleep`, and no test reads or writes outside a `@TempDir`
- [ ] `module-info.java` is unchanged and `ModuleExportsTest` stays green

## Open questions

None.
