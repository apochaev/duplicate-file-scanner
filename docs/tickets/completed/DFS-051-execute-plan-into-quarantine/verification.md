# DFS-051: Execute a deletion plan into quarantine — verification

## Product owner review

### What you can do now

The scanner core can now carry out a checked deletion plan. It moves each planned duplicate into a `.dfs-quarantine/<run ID>/` folder on the file's own volume, and records every step in a manifest file, `<manifest folder>/<run ID>.jsonl`. Just before each file moves, it is checked again against the scan and compared byte for byte with the copy being kept. Nothing user-facing calls this yet: the server's Execute button is milestone M-5, and undo and purge are follow-up tickets.

### Acceptance criteria

- **AC-1** met: every link of every operation moves to the mirrored quarantine path, the keeper stays, and the run ends COMPLETED.
- **AC-2** met: a plan with conflicts is refused, with no job recorded, no manifest and no file moved.
- **AC-3** met: a plan built from another scan is refused and leaves no trace.
- **AC-4** met: a candidate or keeper that is missing, or has a changed size or time, is skipped with a reason that names the path. The run continues.
- **AC-5** met: content that differs is skipped as DIFFERS.
- **AC-6** met for symlinks. Both symlink tests ran and passed on this machine; they did not skip (details below). The junction case is covered by the same check, which DFS-034 already tests on a real junction, but no DFS-051 test uses a junction.
- **AC-7** met: a failed move is skipped with its reason ("access denied"), and the next operation still moves.
- **AC-8** met: every hard link moves and the content is compared once per operation. If a later link fails, the outcome is "partly moved", listing which links moved.
- **AC-9** met: the manifest holds `run`, then for each path `moving` (forced to disk before the rename) and `moved` or `skipped`, then `end`.
- **AC-10** met: an unwritable manifest folder refuses the run before anything is recorded. A manifest line that fails mid-run ends the run FAILED before the next rename.
- **AC-11** met: a quarantine folder that cannot be created is skipped as quarantine-not-writable.
- **AC-12** met: a cancel while QUEUED moves nothing. A cancel while RUNNING finishes the current operation, lists the rest as not attempted, and writes `end` as CANCELLED.
- **AC-13** met: cancel against completion is tested in both orders, and the manifest's `end` line matches the recorded status.
- **AC-14** met: an ID that was never issued gives UNKNOWN_PLAN_EXECUTION, naming the ID. Asking for the result early gives PLAN_EXECUTION_NOT_FINISHED.
- **AC-15** met: an executor rejection gives FAILED with a generic message, for plan verify too.
- **AC-16** met: an `Error` ends the run FAILED and is rethrown on the executor's thread.
- **AC-17** met: the listener's events arrive on the executor's thread and the status snapshot matches them. A listener that throws is detached and its failure recorded.
- **AC-18** met: the report lists outcomes in plan order, with totals.
- **AC-19** met: an empty plan completes with a manifest holding only `run` and `end`. A null argument throws a NullPointerException.
- **AC-20** met: the factory and the Spring beans both work. The `dfs.execution.manifest-folder` property works, and its default sits under the user's home folder. An app's own executor or start bean replaces the default.
- **AC-21** met: an end-to-end run on real virtual threads and real files succeeds.
- **AC-22** met: a plan edited after its preview (same ID, conflicts emptied) is refused with PLAN_EDITED.
- **AC-23** met: a keeper that is not another physical copy is refused with KEEPER_NOT_SEPARATE_COPY, for all 3 examples.

### Try it

You cannot run anything yourself yet: there is no CLI or server entry point (M-5). To see it working, run:

```
./gradlew :file-scanner:test --tests "*PlanExecution*" --tests "*NioQuarantineDeleterTest" --tests "*JsonLinesManifestWriterTest"
./gradlew :scanner-spring:test --tests "*ScannerAutoConfigurationTest"
```

All tests should pass: `PlanExecutionServiceTest` 36, `PlanExecutionRealThreadTest` 1, `NioQuarantineDeleterTest` 8, `JsonLinesManifestWriterTest` 4, `ScannerAutoConfigurationTest` 21.

### Decided without you

- **Quarantine anchors.** `dfs.execution.quarantine-anchors` (`ExecutionConfig.quarantineAnchors`, empty by default) lets a configured folder stand in for a volume root. You accepted this at the design gate.
- **Early `moving` line.** The `moving` line is written before a file's checks, not only right before its rename. You accepted this at the design gate.
- **Manifest of a run that never started.** A run cancelled while QUEUED, or rejected by the executor, leaves a manifest with only its `run` line. You accepted this at the design gate.
- **`end` line timing.** The `end` line comes from the recorded final status, through a new `JobRunner` settle hook. This was your own gate feedback. A status query can show the final status a moment before the `end` line is on disk.
- **Failed compare.** If the byte compare fails with a read error, that link and every later link of the same operation are skipped as UNREADABLE. That is cautious: no link moves unverified. The design says a compare result applies to the operation's later links, and this follows it.
- **Drift wording.** The implementer chose the drift details: "size was X, now Y", "last-modified time changed" (without the old and new times), "no longer exists", and "is a symlink" / "is a junction". You may want the times in the manifest for auditing.
- **Where the compare runs.** The compare runs on the first link that passes its drift check, not literally the first link.
- **`bytesMoved`.** It counts only operations moved whole; a partly moved operation's size counts as skipped.

### Known limits

- There is no undo, no purge, no permanent delete and no UI or CLI entry point (all later tickets).
- **Later links of a hard-linked file are not compared.** Each one is checked only for size and time. If one was replaced after the scan by a different file with the same size and time, it moves without a compare. It goes to quarantine, not deletion, and the keeper is untouched. This is ticket decision 8.
- **Overlapping runs at the same time are not coordinated.** Run 1 keeping Y and quarantining X, and run 2 keeping X and quarantining Y, could both pass their checks and then both move, because a check and its rename are not one atomic step. Quarantine is reversible, but M-5 should serialize executions, or a follow-up should add a lock. This is not covered by any criterion.
- **Junctions have no DFS-051 test** (see AC-6).
- **Volume roots without anchors.** With no anchors set on Linux or macOS, the quarantine would sit at `/`, which is usually not writable, so files would be skipped. Anchors are the workaround.
- **Quarantined files would show up in a later scan.** Design note 7: a later scan of a volume root would walk `.dfs-quarantine` and offer its files as duplicates. A follow-up should make the walker skip it.

### Evidence

- **Deterministic gate on c7c737b:** all checks PASS. Green with 607 passed and 3 skipped; disabled, freeze, touches, stubs, traceability (23 criteria, 30 scenarios) and consistency all pass.
- **My rerun:** I reran the DFS-051 file-scanner test classes with `--rerun`. All 49 pass with 0 skipped, including both AC-6 symlink tests.
- **The 3 skipped tests in the full build** are elsewhere: `NioFileTreeWalkerTest`, `NioFileIdentityTest` and one more.
- **Full build:** `./gradlew build` at HEAD reports up to date and successful.
- **Unchanged code:** `module-info.java` is unchanged. `PreviewDeletionPlanServiceTest` is unchanged and green, so the plan IDs stay the same after the encoding moved to `PlanIds`. Plan verify's only change is the rejection message.
- **Nothing created at startup:** after the gate's builds, which start CLI and server test contexts, neither `~/.dfs` nor `C:\.dfs-quarantine` exists.

### How it went

- **Design:** 2 attempts. The first was interrupted by a usage limit and resumed, with the ticket amended mid-run (AC-22, AC-23). The second was rework after your gate feedback on the `end` line.
- **Tests:** 1 attempt. 54 story tests were red against the stubs.
- **Implement:** 1 attempt, green at the first try.
- **Defects and escalations:** no defects were routed back and there were no escalations.

Ready for the user's approval.

## Summary

- **Gate:** passed on c7c737b (607 passed, 3 skipped). I reran the DFS-051 classes: 49 pass with 0 skipped.
- **AC-1 to AC-5:** met.
- **AC-6:** met for symlinks. Both tests ran and passed here; they did not skip. Junctions are covered only by the shared predicate, which DFS-034 tests, and not by a DFS-051 test.
- **AC-7 to AC-23:** met.
- **Test quality:** good. The tests run on real files in a `@TempDir` behind spies, assert outcomes, causes, subjects, manifest line order and disk state, and keep the manual executor's tasks off the test thread. No new `Thread.sleep`: the one in `ScannerAutoConfigurationTest` predates this ticket.
- **Design conformance:** the code follows `design.md`, including the gate rework (the settle hook) and all 14 checklist dispositions.
- **Invariants hold:**
  - Never the last copy: conflicts are refused, keepers are checked as separate copies against the scan, and a missing keeper is skipped per file.
  - Re-verified at execution: drift and a byte compare before every move.
  - No link following: NOFOLLOW reads and compares, and links are skipped.
  - Hard links are handled as one physical copy.
- **Residual notes:** later hard links get no compare (decision 8). Concurrent overlapping runs are not coordinated (for M-5 or a follow-up). The drift detail for a changed time omits the times.
- **Verdict:** ready for the human done gate.
