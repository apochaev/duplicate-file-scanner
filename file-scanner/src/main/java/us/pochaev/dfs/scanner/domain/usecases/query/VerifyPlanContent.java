package us.pochaev.dfs.scanner.domain.usecases.query;

import us.pochaev.dfs.scanner.domain.model.DeletionPlan;
import us.pochaev.dfs.scanner.domain.model.Result;
import us.pochaev.dfs.scanner.domain.model.VerifyListener;
import us.pochaev.dfs.scanner.domain.model.VerifyReport;

import java.util.function.BooleanSupplier;

/**
 * Byte-compares each operation of a previewed {@link DeletionPlan} with the copy it is kept against (DFS-034,
 * {@code ui.md} U7.3): a read-only dry run of the delete-time proof (CLAUDE.md decision 4), not a replacement for it.
 * Each operation's first link is compared with its keeper; attributes are read first without following links, and
 * content is opened only for two regular files of the same size. Nothing is written, moved or deleted. Runs
 * synchronously on the caller's thread; the asynchronous job model is DFS-042's.
 */
public interface VerifyPlanContent {

    /**
     * @param plan      the previewed plan; only its operations' links, keepers and sizes are used
     * @param listener  receives {@code BytesCompared} and {@code EntryChecked} events on the caller's thread; detached
     *                  at its first exception, which is recorded in {@link VerifyReport#listenerFailure()}
     * @param cancelled read before each operation and before each chunk is read; once it returns true the verify
     *                  stops and the interrupted operation is not reported
     * @return always {@code Success}: the plan's id, one check per finished operation in plan order, whether the
     *         verify was cancelled, and the listener's failure message (empty when none)
     * @throws NullPointerException if {@code plan}, {@code listener} or {@code cancelled} is null
     */
    Result<VerifyReport> verify(DeletionPlan plan, VerifyListener listener, BooleanSupplier cancelled);
}
