package us.pochaev.dfs.scanner.domain.model;

import java.nio.file.Path;

/**
 * The content check of one {@link Operation.Quarantine} of a plan (DFS-034 decision 3): its first link compared with
 * its keeper.
 *
 * @param digest    the operation's set digest
 * @param candidate the operation's first link
 * @param keeper    the operation's keeper
 * @param outcome   what the check found
 * @param detail    for {@code NOT_A_REGULAR_FILE} which side and what it is now; for {@code UNREADABLE} the
 *                  exception's message (its {@code toString()} when blank); for {@code DIFFERS}
 *                  {@code sizes differ: <candidate> vs <keeper>} or {@code first difference at byte <n>}; empty
 *                  otherwise
 */
public record OperationCheck(String digest, Path candidate, Path keeper, Outcome outcome, String detail) {

    public OperationCheck {
    }

    /** The outcome of one check, in precedence order: the first that applies is reported. */
    public enum Outcome {
        /** The candidate no longer exists. */
        CANDIDATE_MISSING,
        /** The keeper no longer exists. */
        KEEPER_MISSING,
        /** The candidate or the keeper is now a symlink, directory, junction or other special file. */
        NOT_A_REGULAR_FILE,
        /** The candidate's or the keeper's attributes or content cannot be read. */
        UNREADABLE,
        /** The sizes differ, or the content differs at some byte. */
        DIFFERS,
        /** Both are regular files with byte-identical content. */
        IDENTICAL
    }
}
