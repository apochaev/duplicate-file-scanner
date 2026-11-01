package us.pochaev.dfs.scanner.domain.model;

import java.nio.file.Path;

/**
 * A deletion rule (DFS-022 decision 2): in every duplicate set that has a copy under {@code keep}, quarantine each
 * copy under {@code quarantineIn} that has no link under {@code keep}, keeping a copy under {@code keep}. "Under" is
 * {@link Path#startsWith(Path)} on the paths as the scan recorded them, so a folder covers its subfolders and whole
 * path components only. A copy is under {@code quarantineIn} only when all its links are (decision 4).
 *
 * @param keep         the folder (or file) whose copies are kept
 * @param quarantineIn the folder whose copies are quarantined; must not equal {@code keep} or lie under it
 */
public record KeepAndQuarantine(Path keep, Path quarantineIn) {
}
