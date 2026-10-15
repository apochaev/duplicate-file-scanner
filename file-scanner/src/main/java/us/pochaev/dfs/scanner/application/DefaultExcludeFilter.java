package us.pochaev.dfs.scanner.application;

import us.pochaev.dfs.scanner.domain.model.FileEntry;

/**
 * Leaves operating-system metadata files out of the scan by name (DFS-027): a regular file whose
 * name is exactly {@value #DS_STORE}, or starts with {@value #APPLE_DOUBLE_PREFIX} (macOS AppleDouble
 * sidecars), matched case-sensitively on the last path element only. Every file it leaves out is
 * counted, whatever its size.
 * <p>
 * Sits in the walk, after {@code ScanEvent.FileWalked} and before {@link SizeIndex#add(FileEntry)}
 * (DFS-027 decisions 4 and 7): excluded files still count as found, and the name check runs before
 * the size check. A pure per-file decision: it keeps the walk's order, reads no content, does no
 * I/O and holds no per-file state, only the count.
 * <p>
 * <b>Not thread-safe.</b> One instance belongs to one scan run ({@link ScanPipeline#scan} creates
 * it); it is never a shared field.
 */
public final class DefaultExcludeFilter {

    /** Whether the built-in list is on when nothing says otherwise; {@code ScanConfig.defaults()} matches it. */
    public static final boolean DEFAULT_ENABLED = true;

    /** The Finder's folder-settings file, excluded when its name is exactly this. */
    static final String DS_STORE = ".DS_Store";

    /** The AppleDouble sidecar prefix: a file whose name starts with this is excluded. */
    static final String APPLE_DOUBLE_PREFIX = "._";

    /**
     * @param enabled whether the built-in list applies; when {@code false} every entry is kept and
     *                {@link #excluded()} stays 0
     */
    public DefaultExcludeFilter(boolean enabled) {
        throw new UnsupportedOperationException("not implemented: DFS-027");
        // implementer: replace the throw with `this.enabled = enabled;` (and declare the
        // `enabled` field and a `long excluded` counter).
    }

    /** Creates a filter with the built-in list on ({@value #DEFAULT_ENABLED}). */
    public DefaultExcludeFilter() {
        this(DEFAULT_ENABLED);
    }

    /**
     * Decides one walked file. Returns {@code false}, and adds one to {@link #excluded()}, when the
     * filter is enabled and the entry's file name is exactly {@value #DS_STORE} or starts with
     * {@value #APPLE_DOUBLE_PREFIX}; returns {@code true} (pass it on to {@link SizeIndex}) otherwise,
     * including for a path with no file name. Size is not looked at.
     *
     * @throws NullPointerException if {@code entry} is null
     */
    public boolean keep(FileEntry entry) {
        throw new UnsupportedOperationException("not implemented: DFS-027");
        // implementer: add `Objects.requireNonNull(entry, "entry");` as the real body's first
        // line. Deliberately not in the stub: a precondition check before this throw would let
        // an argument-validation test pass against the stub instead of failing red (L-013).
    }

    /** How many entries {@link #keep(FileEntry)} has left out so far; 0 for a disabled filter. */
    public long excluded() {
        throw new UnsupportedOperationException("not implemented: DFS-027");
    }
}
