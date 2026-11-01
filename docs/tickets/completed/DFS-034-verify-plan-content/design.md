# DFS-034: Verify a deletion plan's content against its keepers — design

## Summary

A synchronous, read-only query `VerifyPlanContent.verify(plan, listener, cancelled)` (in `domain.usecases.query`) that checks each `Operation.Quarantine` once: its first link against its keeper. It reads both paths' attributes without following links through DFS-033's `FileAttributesReader`. It byte-compares content through a new `ContentComparator` port (`infra.port`, adapter `NioContentComparator` in `infra.adapter`, `FileChannel` with `NOFOLLOW_LINKS`, 1 MiB chunks by default, chunk size injectable) only when both are regular files of the same size. New `domain.model` types: `VerifyReport`, `OperationCheck` (with a nested `Outcome` enum), `VerifyEvent` (`BytesCompared`, `EntryChecked`) and `VerifyListener`. Service: `VerifyPlanContentService` (`application`). No lifecycle and no state: the job model is DFS-042's. The result is always `Success`, with no new `ErrorCode`. `module-info.java` is unchanged.

Every criterion goes through the stub `VerifyPlanContentService.verify`:
- AC-1 identical copies give `IDENTICAL`, one check per operation in plan order (`verify`, `NioContentComparator.compare`).
- AC-2 `DIFFERS`: from attribute sizes (`sizes differ: c vs k`) or from `Comparison.Differs(offset)` (`first difference at byte n`).
- AC-3 a `NoSuchFileException` from the attributes read gives `CANDIDATE_MISSING`/`KEEPER_MISSING`, and the loop goes on.
- AC-4 `NOT_A_REGULAR_FILE` is decided from the NOFOLLOW attributes before the comparator is called. `detail` names the side (`candidate is a symlink`, ...).
- AC-5 an `IOException` from the attributes read or from `ContentComparator.compare` gives `UNREADABLE`. The test uses a fake `ContentComparator` handed to `new VerifyPlanContentService(...)`.
- AC-6 `BytesCompared`/`EntryChecked` come from the service. The listener is detached on its first throw (state is local to the call) and `listenerFailure` records the message.
- AC-7 `cancelled` is read before each operation by the service and before each chunk by the comparator. `Comparison.Cancelled` gives `cancelled=true` and the interrupted operation is dropped.
- AC-8 empty plan gives no checks. Null arguments throw `NullPointerException`, checked at the top of `verify`.
- AC-9 read-only: the reader and the comparator only read (the adapter opens with `READ` only).
- AC-10 `ScannerFactories.verifyPlanContent()` and the `ScannerAutoConfiguration` bean are real code. Each test first calls `verify` through the factory or bean, so it goes red on the stub.

Checklist items 1, 3, 4, 6, 9, 11 and 12 are not applicable: there is no ID, no lifecycle, no stored state and no executor. Expected breakage: none.

Look closely at: the `NOT_A_REGULAR_FILE` detail strings, the `BytesCompared` arithmetic (clamped to each operation's plan size, the remainder added at once), and `Outcome` nested in `OperationCheck` rather than a top-level type.

## Public API

- `VerifyPlanContent` in `domain.usecases.query`: `Result<VerifyReport> verify(DeletionPlan plan, VerifyListener listener, BooleanSupplier cancelled)`. It always returns `Success`. A null argument throws `NullPointerException`.
- `VerifyReport` in `domain.model`: `(String planId, List<OperationCheck> checks, boolean cancelled, String listenerFailure)`.
- `OperationCheck` in `domain.model`: `(String digest, Path candidate, Path keeper, Outcome outcome, String detail)`, with nested `enum Outcome { CANDIDATE_MISSING, KEEPER_MISSING, NOT_A_REGULAR_FILE, UNREADABLE, DIFFERS, IDENTICAL }` in precedence order.
- `VerifyEvent` in `domain.model`: sealed, with `BytesCompared(long bytesDone, long bytesTotal)` and `EntryChecked(OperationCheck check, int done, int total)`.
- `VerifyListener` in `domain.model`: `@FunctionalInterface void onEvent(VerifyEvent event)`.
- `ContentComparator` in `infra.port`: `Comparison compare(Path candidate, Path keeper, LongConsumer bytesCompared, BooleanSupplier cancelled) throws IOException`, `int DEFAULT_CHUNK_SIZE = 1 MiB`. Nested `sealed interface Comparison { record Identical(); record Differs(long offset); record Cancelled(); }`.
- `NioContentComparator` in `infra.adapter`: `NioContentComparator()` (1 MiB) and `NioContentComparator(int chunkSize)` (`IllegalArgumentException` if < 1). Both constructors are real code.
- `VerifyPlanContentService` in `application`: `VerifyPlanContentService(FileAttributesReader reader, ContentComparator comparator)`. The constructor is real code.
- `ScannerFactories.verifyPlanContent()`: real code, `new VerifyPlanContentService(new NioFileAttributesReader(), new NioContentComparator())`.
- `ScannerAutoConfiguration.verifyPlanContent()`: real code, `@Bean @ConditionalOnMissingBean`, returns the factory's instance. It needs no delegate because the service implements this one port only.

No first-slice type is replaced.

**Record checks for the implementer.** The records are left without checks at design; add these:
- `VerifyReport`: `planId` and `listenerFailure` non-null; `checks` becomes `List.copyOf`.
- `OperationCheck`: every component non-null.
- `BytesCompared`: `0 <= bytesDone <= bytesTotal`.
- `EntryChecked`: `check` non-null and `1 <= done <= total`.
- `Comparison.Differs`: `offset >= 0`.

### Behaviour of `verify` (the contract the tests pin)

1. `requireNonNull` on `plan`, `listener` and `cancelled`. Then `bytesTotal` = sum of `op.size()`, and `total` = number of operations.
2. For each operation `q`, in plan order:
   1. If `cancelled.getAsBoolean()`, return `Success(report(cancelled=true))` with the checks so far. A verify cancelled before the start returns no checks.
   2. Set `candidate = q.links().get(0)` and `keeper = q.keeper()`.
   3. Read the attributes of both paths with `reader.read`, always both. `NoSuchFileException` marks the path missing. Any other `IOException` marks it unreadable and keeps the exception.
   4. Decide in precedence order:
      - candidate missing: `CANDIDATE_MISSING`, then keeper missing: `KEEPER_MISSING`. Their `detail` is empty.
      - candidate not regular, then keeper not regular: `NOT_A_REGULAR_FILE`. A path is regular when `isRegularFile() && !isOther() && !isSymbolicLink()`, the same test as DFS-033. The detail is `"<side> is <what>"`, where `<side>` is `candidate` or `keeper`. `<what>` is the first of these that applies: `a symlink` (`isSymbolicLink`), `a junction` (`isDirectory && isOther`), `a directory` (`isDirectory`), `a special file` (anything else, e.g. a reparse-point placeholder or a device).
      - candidate unreadable, then keeper unreadable: `UNREADABLE`, with the exception's message as detail, or its `toString()` when the message is null or blank.
      - attribute sizes differ: `DIFFERS`, detail `sizes differ: <candidateSize> vs <keeperSize>`. Nothing is opened.
      - otherwise call `comparator.compare(candidate, keeper, n -> emit(BytesCompared(before + min(n, q.size()), bytesTotal)), cancelled)`:
        - `Identical` gives `IDENTICAL` with an empty detail.
        - `Differs(o)` gives `DIFFERS` with `first difference at byte <o>`.
        - `Cancelled` returns `Success(report(cancelled=true))` without this operation.
        - an `IOException` gives `UNREADABLE`, with detail as above.
   5. Set `before += q.size()`. Emit `BytesCompared(before, bytesTotal)` unless the last emitted `bytesDone` already equals `before`, so it is not sent twice. Then emit `EntryChecked(check, i + 1, total)`.
3. Return `Success(new VerifyReport(plan.id(), checks, false, listenerFailure))`.

**Emitting events.** A local holder wraps the listener. The first exception it throws is caught and recorded as its message (`toString()` when the message is null or blank), and no later event reaches the listener. `listenerFailure` is `""` when the listener never threw. An exception thrown by `cancelled` itself propagates: that is a caller bug.

**`min(n, q.size())`.** Both files may have grown by the same amount since the scan, while `bytesTotal` uses the plan's sizes. The clamp keeps `bytesDone <= bytesTotal`, and the record check enforces it.

**What `NioContentComparator.compare` does:**
- It opens both files with `FileChannel.open(p, READ, LinkOption.NOFOLLOW_LINKS)`. This narrows the race when a file becomes a link between the attributes read and the open: on Unix this is `O_NOFOLLOW`, on Windows `FILE_FLAG_OPEN_REPARSE_POINT`.
- It allocates two buffers of `chunkSize` per call.
- In a loop: if `cancelled.getAsBoolean()`, it returns `Cancelled`. Otherwise it fills a chunk from each file (reading until the buffer is full or EOF) and compares them byte by byte. The first mismatch returns `Differs(offset)`. If one file ends before the other, it returns `Differs(offset of the shorter one's end)`.
- After each chunk it calls `bytesCompared.accept(totalComparedSoFar)`.
- When both files end together, it returns `Identical`.
- A zero-byte pair returns `Identical` and reports nothing.

## Layer placement

- `domain.usecases.query` (exported): `VerifyPlanContent`
- `domain.model` (exported): `VerifyReport`, `OperationCheck` (+ `OperationCheck.Outcome`), `VerifyEvent`, `VerifyListener`
- `application`: `VerifyPlanContentService`, with no seam: its two ports are the seams
- `infra.port` / `infra.adapter`: `ContentComparator` (with nested `Comparison`) / `NioContentComparator`. `FileAttributesReader` and `NioFileAttributesReader` (DFS-033) are reused unchanged.
- `domain.factory`: `ScannerFactories.verifyPlanContent()`. `scanner-spring`: `ScannerAutoConfiguration.verifyPlanContent()`.

## State and transitions

None: this use case has no lifecycle. One call runs to completion or to a cancel on the caller's thread and returns its report (DFS-034 decision 1; the job model is DFS-042).

## Concurrency and atomicity

- Which thread runs what: everything runs on the caller's thread, the listener events included. The comparator calls back on the same thread.
- Per-request state is local variables of `verify`: checks, byte counters, the detached-listener holder. The service's only fields are its two ports. `NioContentComparator` holds only `chunkSize`, and its buffers are per call, so concurrent calls share nothing.
- Races: none inside the use case. A file changing between the attributes read and the open is reported by the compare: a different length gives `Differs` at the shorter end, a link refused by `NOFOLLOW_LINKS` gives an `IOException` and so `UNREADABLE`. Execution re-verifies anyway (CLAUDE.md decision 4).

## Checklist disposition

1. Unknown or stale IDs: not applicable. The plan is passed by value, and the use case takes no ID (decision 7).
2. Results as values, bugs as exceptions: every per-entry problem is an `Outcome` value. The call always returns `Success`, so no new `ErrorCode` is needed. A `null` plan, listener or cancel signal throws `NullPointerException` (AC-8). No optional argument, so no overload.
3. Status model: not applicable. Synchronous, no lifecycle (decision 1, DFS-042).
4. Races and who wins: not applicable. Single-threaded. A disk change mid-verify is reported, not raced (see Concurrency).
5. Validation before side effects: only null checks, done before any read. There are no side effects at all (read-only, AC-9). The plan is not checked against a scan: that is DFS-033's job (decision 2).
6. State placement: not applicable. No per-request state outlives the call, and everything is a local of `verify`.
7. Progress and observation: pushed only, through `VerifyListener`: `BytesCompared` after each chunk and after each operation, `EntryChecked` after each operation (decision 5, AC-6). There is no snapshot query; DFS-042 adds a pull model.
8. Caller-supplied callbacks: the listener is detached at its first exception. `VerifyReport.listenerFailure` holds the message, or `toString()` when it is blank, and `""` when none (decision 5, as DFS-018 decision 7). An exception from `cancelled` propagates.
9. Failures off the caller's thread: not applicable. There is no executor. Per-entry I/O failures are `UNREADABLE` values.
10. Query results: the whole report, checks in plan order, one per operation. It is partial only when cancelled, in which case `cancelled=true` and the interrupted operation is absent (decision 6, AC-7).
11. Executor contract: not applicable. There is no executor (decision 1).
12. Retention: not applicable. Nothing is kept after the call returns.
13. Testability:
    - Plain `@TempDir` files and the real adapters cover AC-1–4, 6–10.
    - AC-5 uses a hand-written fake `ContentComparator` passed to `new VerifyPlanContentService(new NioFileAttributesReader(), fake)`. The fake throws `AccessDeniedException` for one candidate and delegates to `NioContentComparator` otherwise.
    - Cancel is set from the listener: an `AtomicBoolean` read through `cancelled::get`, so no timing is involved and no `Thread.sleep`.
    - Adapter tests may use `new NioContentComparator(smallChunk)` to span several chunks with small files.
14. Exported surface: the new types are in the already exported `domain.model` and `domain.usecases.query`. `infra.port`/`infra.adapter` stay unexported. `module-info.java` is unchanged, and no first-slice type is replaced.

## Configuration

- Chunk size: 1 MiB (`ContentComparator.DEFAULT_CHUNK_SIZE`), set in `NioContentComparator`'s constructor. The factory uses the default, and there is no app-wide setting.

## Expected breakage

None.

## Open design notes (as of the design phase)

- `Outcome` is nested in `OperationCheck` (`OperationCheck.Outcome`), following DFS-033's `PathDrift.Role`, instead of being a top-level `domain.model.Outcome`: the bare name is too generic for an exported package. It is still in `domain.model`, as the ticket's Touches says.
- `ContentComparator.compare` returns a nested sealed `Comparison` (`Identical`, `Differs(offset)`, `Cancelled`), so the port can say "cancelled" without a sentinel value. The size check sits in the service, from the attributes, before the comparator is called. The comparator still handles a length mismatch it meets itself.
- `NOT_A_REGULAR_FILE` detail strings: `candidate|keeper is a symlink|a junction|a directory|a special file` (see Behaviour). The ticket only said "detail says which".
- `listenerFailure` uses the bare message (the AC-6 scenario expects `"boom"`), unlike `ScanService`'s `ClassName: message`. When the message is blank it uses `toString()`, so a failure is never recorded as `""`.
- A `NoSuchFileException` thrown by the comparator (the file vanished after its attributes were read) is reported as `UNREADABLE`, not as missing: the precedence is decided from the attributes read.
- Test-author:
  - Add the AC-10 bean tests to `ScannerAutoConfigurationTest` beside the DFS-033 ones. Each test calls `verify` through the factory or the bean before asserting the wiring, so it goes red on the stub.
  - Story tests of the service and the adapter go in `file-scanner/src/test/java/us/pochaev/dfs/scanner/application/` and `.../infra/adapter/`.
  - `NioContentComparator.compare` is a second stub: an adapter test reaches it directly.

## Stubs (as of the design phase)

- Files stubbed: `application/VerifyPlanContentService.java` (`verify`) and `infra/adapter/NioContentComparator.java` (`compare`).
- Written for real: the records and interfaces (`VerifyPlanContent`, `VerifyReport`, `OperationCheck`, `VerifyEvent`, `VerifyListener`, `ContentComparator`), both constructors, `ScannerFactories.verifyPlanContent()` and the `ScannerAutoConfiguration.verifyPlanContent()` bean.
- Each stub throws `new UnsupportedOperationException("not implemented: DFS-034")` per D2.
- Commit: filled in after the design commit.
