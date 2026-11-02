package us.pochaev.dfs.server.result;

import us.pochaev.dfs.scanner.domain.model.ScanResult;

import us.pochaev.dfs.scanner.domain.model.DuplicateSet;
import us.pochaev.dfs.scanner.domain.model.FolderTotal;
import us.pochaev.dfs.scanner.domain.model.PhysicalCopy;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A COMPLETED scan's result, indexed once for the result API (DFS-047 decision 6): the folder tree of the scan's roots
 * with subtree totals rolled up from {@code ScanResult.folders}, and the duplicate sets sorted once by reclaimable bytes
 * (largest first), then size (largest first), then digest. Immutable once built, so any request thread may read it;
 * {@link us.pochaev.dfs.server.scan.ScanRegistry#completedState} builds it at most once per scan and keeps it for this
 * server run.
 * <p>
 * Paths given to it are absolute and normalized; {@link #insideRoots} is checked before {@link #children} and
 * {@link #sets} are asked. A path inside a root that the scan holds nothing for answers an empty list or page, never
 * an error, so the API does not reveal whether a folder exists (decision 5).
 */
final class ResultIndex {

    private static final Comparator<Node> BY_NAME = Comparator
            .comparing((Node n) -> n.name.toLowerCase(java.util.Locale.ROOT))
            .thenComparing(n -> n.path.toString());

    private final List<Path> rootPaths;
    private final Map<Path, Node> nodes;
    private final List<Node> rootNodes;
    private final List<IndexedSet> sets;

    private ResultIndex(List<Path> rootPaths, Map<Path, Node> nodes, List<Node> rootNodes, List<IndexedSet> sets) {
        this.rootPaths = rootPaths;
        this.nodes = nodes;
        this.rootNodes = rootNodes;
        this.sets = sets;
    }

    /** Builds the index: the folder tree with rolled-up totals and the sets in their one order. */
    static ResultIndex of(ScanResult result) {
        List<Path> roots = List.copyOf(result.roots());
        Map<Path, Node> nodes = new HashMap<>();
        for (Path root : roots) {
            node(nodes, root);
        }
        for (FolderTotal total : result.folders()) {
            Node own = node(nodes, total.folder());
            own.directFiles += total.files();
            own.directBytes += total.bytes();
            for (Path p = total.folder(); p != null; p = p.getParent()) {
                if (!inside(roots, p)) {
                    break;
                }
                Node n = node(nodes, p);
                n.files += total.files();
                n.bytes += total.bytes();
            }
        }
        for (Node n : nodes.values()) {
            // Link each node to its parent node (a root's parent is not a node unless roots are nested).
            Path parent = n.path.getParent();
            Node parentNode = parent == null ? null : nodes.get(parent);
            if (parentNode != null && (n.files > 0)) {
                parentNode.children.add(n);
            }
        }
        for (Node n : nodes.values()) {
            n.children.sort(BY_NAME);
        }
        List<Node> rootNodes = new ArrayList<>();
        for (Path root : roots) {
            Node n = nodes.get(root);
            if (!rootNodes.contains(n)) {
                rootNodes.add(n);
            }
        }
        rootNodes.sort(BY_NAME);
        List<IndexedSet> sets = new ArrayList<>();
        for (DuplicateSet set : result.sets()) {
            sets.add(indexed(set));
        }
        sets.sort(Comparator.comparingLong((IndexedSet s) -> s.view.reclaimableBytes()).reversed()
                .thenComparing(Comparator.comparingLong((IndexedSet s) -> s.view.size()).reversed())
                .thenComparing(s -> s.view.digest()));
        return new ResultIndex(roots, Map.copyOf(nodes), List.copyOf(rootNodes), List.copyOf(sets));
    }

    /**
     * Whether {@code path} (absolute, normalized) is a root of the scan or lies under one, by {@link Path#startsWith}
     * (the platform's rules: case-insensitive on Windows).
     */
    boolean insideRoots(Path path) {
        return inside(rootPaths, path);
    }

    /** The scan's roots as folders, ordered by name, case-insensitively. */
    List<FolderNode> roots() {
        return rootNodes.stream().map(Node::view).toList();
    }

    /**
     * The immediate subfolders of {@code parent} that hold at least one counted file in their subtree, ordered by
     * name, case-insensitively; empty when the scan holds nothing for {@code parent}.
     */
    List<FolderNode> children(Path parent) {
        Node n = nodes.get(parent);
        return n == null ? List.of() : n.children.stream().map(Node::view).toList();
    }

    /**
     * One page of the sets with at least one link inside {@code under} (every set when {@code under} is null), each set
     * with all its copies; the totals cover every matching set.
     *
     * @param under absolute, normalized and inside a root, or null
     * @param page  0 or more
     * @param size  1 to 500
     */
    SetPage sets(Path under, int page, int size) {
        List<DuplicateSetView> matching = new ArrayList<>();
        long reclaimable = 0;
        for (IndexedSet s : sets) {
            if (under == null || s.paths.stream().anyMatch(p -> p.startsWith(under))) {
                matching.add(s.view);
                reclaimable += s.view.reclaimableBytes();
            }
        }
        long from = (long) page * size;
        List<DuplicateSetView> items = from >= matching.size()
                ? List.of()
                : List.copyOf(matching.subList((int) from, (int) Math.min(matching.size(), from + size)));
        return new SetPage(items, page, size, matching.size(), reclaimable);
    }

    private static boolean inside(List<Path> roots, Path path) {
        for (Path root : roots) {
            if (path.startsWith(root)) {
                return true;
            }
        }
        return false;
    }

    private static Node node(Map<Path, Node> nodes, Path path) {
        return nodes.computeIfAbsent(path, Node::new);
    }

    private static IndexedSet indexed(DuplicateSet set) {
        List<CopyView> copies = set.copies().stream()
                .map(ResultIndex::copy)
                .sorted(Comparator.comparing(c -> c.links().get(0).path()))
                .toList();
        List<Path> paths = set.files().stream().map(f -> f.path()).toList();
        long reclaimable = set.size() * (copies.size() - 1);
        return new IndexedSet(new DuplicateSetView(set.digest(), set.size(), reclaimable, copies), paths);
    }

    private static CopyView copy(PhysicalCopy copy) {
        return new CopyView(copy.links().stream()
                .sorted(Comparator.comparing(f -> f.path().toString()))
                .map(f -> new LinkView(f.path().toString(), f.lastModified()))
                .toList());
    }

    private record IndexedSet(DuplicateSetView view, List<Path> paths) {
    }

    /** A folder while the tree is built; not modified afterwards. */
    private static final class Node {
        final Path path;
        final String name;
        final List<Node> children = new ArrayList<>();
        long files;
        long bytes;
        long directFiles;
        long directBytes;

        Node(Path path) {
            this.path = path;
            this.name = path.getFileName() == null ? path.toString() : path.getFileName().toString();
        }

        FolderNode view() {
            return new FolderNode(path.toString(), name, files, bytes, directFiles, directBytes, !children.isEmpty());
        }
    }
}
