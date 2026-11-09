package us.pochaev.dfs.scanner.infra.adapter;

/**
 * The names of the operating system's own folders that a scan leaves out (DFS-061 decision 1), decided from the
 * name alone:
 * <ul>
 *   <li>Windows, compared case-insensitively (as NTFS does): {@code $RECYCLE.BIN}, {@code System Volume Information},
 *       {@code Recycled}, {@code RECYCLER};</li>
 *   <li>macOS, compared exactly: {@code .Trashes}, {@code .Spotlight-V100}, {@code .fseventsd},
 *       {@code .TemporaryItems}, {@code .DocumentRevisions-V100};</li>
 *   <li>Linux, compared exactly: {@code .Trash}, {@code lost+found}, and {@code .Trash-} followed by one or more
 *       ASCII digits ({@code .Trash-0}, {@code .Trash-1000}; not {@code .Trash-} or {@code .Trash-abc}).</li>
 * </ul>
 * A name that merely contains one of them ({@code my.Trash}, {@code lost+found2}) does not match.
 */
final class SystemFolderNames {

    private SystemFolderNames() {
    }

    /**
     * Whether {@code name}, a single file-name element, is one of the operating-system folder names above.
     *
     * @throws NullPointerException if {@code name} is null
     */
    static boolean matches(String name) {
        throw new UnsupportedOperationException("not implemented: DFS-061");
    }
}
