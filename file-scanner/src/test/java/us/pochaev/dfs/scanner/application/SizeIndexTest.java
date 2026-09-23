package us.pochaev.dfs.scanner.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import us.pochaev.dfs.scanner.domain.model.FileEntry;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Stage 1 of the detection pipeline: {@link SizeIndex} groups {@link FileEntry} values by size.
 *
 * <p>Invalid-input tests (DFS-001.AC-7) use try/catch rather than {@code assertThrows}: the stub
 * throws {@code UnsupportedOperationException("not implemented: DFS-001")} unconditionally,
 * before any precondition check, and {@code assertThrows} against a narrower expected type would
 * report its own {@code AssertionFailedError} instead of letting the stub's exception propagate.
 * A try/fail/catch block lets the stub's exception surface uncaught, which is what the red gate
 * requires.
 */
class SizeIndexTest {

    private static final Instant WHEN = Instant.parse("2024-01-01T10:00:00Z");
    private static final Instant LATER = Instant.parse("2024-01-01T11:00:00Z");

    // --- AC-1: unique sizes dropped, equal sizes grouped, across folders ---

    @Test
    @DisplayName("DFS-001.AC-1 Files with a unique size are dropped")
    void filesWithUniqueSizeAreDropped() {
        SizeIndex index = new SizeIndex();
        index.add(entry("a.txt", 10));
        index.add(entry("b.txt", 20));
        index.add(entry("c.txt", 30));

        assertEquals(List.of(), index.groups());
    }

    @Test
    @DisplayName("DFS-001.AC-1 Files of equal size form one candidate group")
    void filesOfEqualSizeFormOneGroup() {
        SizeIndex index = new SizeIndex();
        index.add(entry("a.txt", 10));
        index.add(entry("b.txt", 10));
        index.add(entry("c.txt", 20));

        List<CandidateGroup> groups = index.groups();

        assertEquals(1, groups.size());
        assertEquals(new CandidateGroup(10, List.of(entry("a.txt", 10), entry("b.txt", 10))), groups.getFirst());
    }

    @Test
    @DisplayName("DFS-001.AC-1 Files are grouped across folders")
    void filesAreGroupedAcrossFolders() {
        SizeIndex index = new SizeIndex();
        index.add(entry("folder1/a.txt", 10));
        index.add(entry("folder2/b.txt", 10));

        List<CandidateGroup> groups = index.groups();

        assertEquals(1, groups.size());
        assertEquals(
                List.of(Path.of("folder1/a.txt"), Path.of("folder2/b.txt")),
                paths(groups.getFirst()));
    }

    // --- AC-2: empty files excluded by default, configurable minimum size ---

    @Test
    @DisplayName("DFS-001.AC-2 Empty files are excluded by default")
    void emptyFilesAreExcludedByDefault() {
        SizeIndex index = new SizeIndex();
        index.add(entry("a.dat", 0));
        index.add(entry("b.dat", 0));

        assertEquals(List.of(), index.groups());
    }

    @Test
    @DisplayName("DFS-001.AC-2 A minimum size excludes smaller files")
    void minimumSizeExcludesSmallerFiles() {
        SizeIndex index = new SizeIndex(100);
        index.add(entry("a.txt", 99));
        index.add(entry("b.txt", 99));
        index.add(entry("c.txt", 100));
        index.add(entry("d.txt", 100));

        List<CandidateGroup> groups = index.groups();

        assertEquals(1, groups.size());
        assertEquals(
                List.of(Path.of("c.txt"), Path.of("d.txt")),
                paths(groups.getFirst()));
    }

    // --- AC-3: merge combines partial indexes, leaves the other unchanged, rejects mismatched minimums ---

    @Test
    @DisplayName("DFS-001.AC-3 Merging indexes with different minimum sizes is rejected")
    void mergingIndexesWithDifferentMinimumSizesIsRejected() {
        SizeIndex index1 = new SizeIndex(1);
        SizeIndex index2 = new SizeIndex(100);

        try {
            index1.merge(index2);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    @DisplayName("DFS-001.AC-3 Merging partial indexes equals one index that received both files")
    void mergingPartialIndexesEqualsOneIndex() {
        SizeIndex index1 = new SizeIndex();
        index1.add(entry("folder1/a.txt", 10));
        SizeIndex index2 = new SizeIndex();
        index2.add(entry("folder2/b.txt", 10));

        assertEquals(List.of(), index1.groups());
        assertEquals(List.of(), index2.groups());

        index1.merge(index2);

        SizeIndex single = new SizeIndex();
        single.add(entry("folder1/a.txt", 10));
        single.add(entry("folder2/b.txt", 10));

        assertEquals(single.groups(), index1.groups());
    }

    @Test
    @DisplayName("DFS-001.AC-3 Merging leaves the other index unchanged")
    void mergingLeavesOtherIndexUnchanged() {
        SizeIndex index1 = new SizeIndex();
        index1.add(entry("a.txt", 10));
        SizeIndex index2 = new SizeIndex();
        index2.add(entry("b.txt", 10));

        List<CandidateGroup> beforeAdd = index2.groups();
        index1.merge(index2);

        index1.add(entry("c.txt", 10));
        index1.add(entry("d.txt", 999));

        assertEquals(beforeAdd, index2.groups());
    }

    @Test
    @DisplayName("DFS-001.AC-3 Merging an index with an equal one changes nothing")
    void mergingAnEqualIndexChangesNothing() {
        SizeIndex index1 = new SizeIndex();
        index1.add(entry("a.txt", 10));
        index1.add(entry("b.txt", 10));
        List<CandidateGroup> before = index1.groups();

        SizeIndex index2 = new SizeIndex();
        index2.add(entry("a.txt", 10));
        index2.add(entry("b.txt", 10));
        index1.merge(index2);

        assertEquals(before, index1.groups());
    }

    // --- AC-4: repeated path counts once, later modification time wins ---

    @Test
    @DisplayName("DFS-001.AC-4 The same path added twice counts once")
    void samePathAddedTwiceCountsOnce() {
        SizeIndex index = new SizeIndex();
        index.add(entry("a.txt", 10));
        index.add(entry("a.txt", 10));

        assertEquals(List.of(), index.groups());
    }

    @Test
    @DisplayName("DFS-001.AC-4 A repeated path does not inflate a group")
    void repeatedPathDoesNotInflateGroup() {
        SizeIndex index = new SizeIndex();
        index.add(entry("a.txt", 10));
        index.add(entry("a.txt", 10));
        index.add(entry("b.txt", 10));

        List<CandidateGroup> groups = index.groups();

        assertEquals(1, groups.size());
        assertEquals(
                List.of(Path.of("a.txt"), Path.of("b.txt")),
                paths(groups.getFirst()));
    }

    @Test
    @DisplayName("DFS-001.AC-4 Equal paths keep the later modification time, regardless of insertion order")
    void equalPathsKeepLaterModificationTime() {
        SizeIndex earlyThenLate = new SizeIndex();
        earlyThenLate.add(entry("a.txt", 10, WHEN));
        earlyThenLate.add(entry("a.txt", 10, LATER));
        earlyThenLate.add(entry("b.txt", 10, WHEN));

        SizeIndex lateThenEarly = new SizeIndex();
        lateThenEarly.add(entry("a.txt", 10, LATER));
        lateThenEarly.add(entry("a.txt", 10, WHEN));
        lateThenEarly.add(entry("b.txt", 10, WHEN));

        FileEntry expectedA = entry("a.txt", 10, LATER);
        assertTrue(earlyThenLate.groups().getFirst().files().contains(expectedA));
        assertEquals(earlyThenLate.groups(), lateThenEarly.groups());
    }

    // --- AC-5: deterministic output order ---

    @Test
    @DisplayName("DFS-001.AC-5 Output does not depend on insertion order")
    void outputDoesNotDependOnInsertionOrder() {
        SizeIndex forward = new SizeIndex();
        forward.add(entry("a.txt", 10));
        forward.add(entry("b.txt", 10));
        forward.add(entry("c.txt", 20));
        forward.add(entry("d.txt", 20));

        SizeIndex backward = new SizeIndex();
        backward.add(entry("d.txt", 20));
        backward.add(entry("c.txt", 20));
        backward.add(entry("b.txt", 10));
        backward.add(entry("a.txt", 10));

        assertEquals(forward.groups(), backward.groups());
        for (CandidateGroup group : forward.groups()) {
            List<Path> paths = paths(group);
            assertEquals(paths.stream().sorted().toList(), paths);
        }
    }

    @Test
    @DisplayName("DFS-001.AC-5 Groups are returned largest first")
    void groupsAreReturnedLargestFirst() {
        SizeIndex index = new SizeIndex();
        index.add(entry("a10.txt", 10));
        index.add(entry("b10.txt", 10));
        index.add(entry("a50.txt", 50));
        index.add(entry("b50.txt", 50));
        index.add(entry("a30.txt", 30));
        index.add(entry("b30.txt", 30));

        List<CandidateGroup> groups = index.groups();

        assertEquals(List.of(50L, 30L, 10L), groups.stream().map(CandidateGroup::size).toList());
    }

    // --- AC-6: immutable results ---

    @Test
    @DisplayName("DFS-001.AC-6 Returned groups are immutable after later additions")
    void returnedGroupsAreImmutable() {
        SizeIndex index = new SizeIndex();
        index.add(entry("a.txt", 10));
        index.add(entry("b.txt", 10));

        List<CandidateGroup> before = index.groups();
        CandidateGroup beforeGroup = before.getFirst();

        index.add(entry("c.txt", 10));
        index.add(entry("d.txt", 999));

        // The snapshot taken before the later additions must not reflect them.
        assertEquals(before, List.of(beforeGroup));
        assertEquals(2, beforeGroup.files().size());
        assertEquals(
                List.of(Path.of("a.txt"), Path.of("b.txt")),
                paths(beforeGroup));
    }

    // --- AC-7: invalid input rejected ---

    @Test
    @DisplayName("DFS-001.AC-7 A negative minimum size is rejected")
    void negativeMinimumSizeIsRejected() {
        try {
            new SizeIndex(-1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    @DisplayName("DFS-001.AC-7 A null entry is rejected")
    void nullEntryIsRejected() {
        SizeIndex index = new SizeIndex();

        try {
            index.add(null);
            fail("expected NullPointerException");
        } catch (NullPointerException expected) {
            // expected
        }
    }

    private static List<Path> paths(CandidateGroup group) {
        return group.files().stream().map(FileEntry::path).toList();
    }

    private static FileEntry entry(String path, long size) {
        return entry(path, size, WHEN);
    }

    private static FileEntry entry(String path, long size, Instant lastModified) {
        return new FileEntry(Path.of(path), size, lastModified);
    }
}
