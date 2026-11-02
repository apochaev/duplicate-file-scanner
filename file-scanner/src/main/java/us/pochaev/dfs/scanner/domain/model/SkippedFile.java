package us.pochaev.dfs.scanner.domain.model;

import java.nio.file.Path;

/**
 * One path an execution did not move, and why (DFS-051 decision 4).
 *
 * @param path    the link that was not moved
 * @param cause   the first reason that applied, in {@link SkipCause}'s precedence
 * @param subject the path the reason is about: the link itself, or the keeper for a keeper's drift
 * @param detail  for {@link SkipCause#UNREADABLE}, {@link SkipCause#MOVE_FAILED} and
 *                {@link SkipCause#QUARANTINE_NOT_WRITABLE} the exception's message (its {@code toString()} when
 *                blank); for {@link SkipCause#DIFFERS} {@code first difference at byte <n>}; for
 *                {@link SkipCause#NOT_A_REGULAR_FILE} what the subject is now ({@code a symlink}, {@code a junction},
 *                {@code a directory}, {@code a special file}); empty otherwise
 */
public record SkippedFile(Path path, SkipCause cause, Path subject, String detail) {

    public SkippedFile {
    }
}
