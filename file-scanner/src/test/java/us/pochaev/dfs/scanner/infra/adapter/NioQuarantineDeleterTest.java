package us.pochaev.dfs.scanner.infra.adapter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import us.pochaev.dfs.scanner.domain.model.PlanExecutionId;
import us.pochaev.dfs.scanner.infra.port.FileDeleter;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The NIO quarantine adapter on real files (DFS-051 decision 5): mirrored paths and same-volume renames. */
class NioQuarantineDeleterTest {

    @TempDir
    Path tmp;

    private static PlanExecutionId run() {
        return new PlanExecutionId(UUID.randomUUID());
    }

    private Path file(String rel, String content) throws Exception {
        Path p = tmp.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content, StandardCharsets.UTF_8);
        return p;
    }

    @Test
    @DisplayName("DFS-051.AC-1 The quarantine path mirrors the path below the anchor")
    void quarantinePathMirrorsThePathBelowTheAnchor() throws Exception {
        NioQuarantineDeleter deleter = new NioQuarantineDeleter(List.of(tmp));
        PlanExecutionId run = run();
        Path file = tmp.resolve("lib").resolve("B").resolve("photo.jpg");

        Path target = deleter.quarantinePath(file, run);

        assertEquals(tmp.resolve(".dfs-quarantine").resolve(run.value().toString()).resolve("lib").resolve("B")
                .resolve("photo.jpg"), target);
    }

    @Test
    @DisplayName("DFS-051.AC-1 The deepest anchor that holds the file is the base")
    void deepestAnchorIsTheBase() throws Exception {
        Path inner = tmp.resolve("inner");
        NioQuarantineDeleter deleter = new NioQuarantineDeleter(List.of(tmp, inner));
        PlanExecutionId run = run();

        Path deep = deleter.quarantinePath(inner.resolve("x").resolve("a.txt"), run);
        Path shallow = deleter.quarantinePath(tmp.resolve("other").resolve("a.txt"), run);

        assertEquals(inner.resolve(".dfs-quarantine").resolve(run.value().toString()).resolve("x").resolve("a.txt"),
                deep);
        assertEquals(tmp.resolve(".dfs-quarantine").resolve(run.value().toString()).resolve("other")
                .resolve("a.txt"), shallow);
    }

    @Test
    @DisplayName("DFS-051.AC-1 Different files and different runs get different quarantine paths")
    void differentInputsGetDifferentPaths() throws Exception {
        NioQuarantineDeleter deleter = new NioQuarantineDeleter(List.of(tmp));
        PlanExecutionId one = run();
        PlanExecutionId two = run();
        Path a = tmp.resolve("lib/a.txt");
        Path b = tmp.resolve("lib/b.txt");

        assertNotEquals(deleter.quarantinePath(a, one), deleter.quarantinePath(b, one));
        assertNotEquals(deleter.quarantinePath(a, one), deleter.quarantinePath(a, two));
    }

    @Test
    @DisplayName("DFS-051.AC-1 Without an anchor the path stays under a quarantine folder named for the run")
    void withoutAnAnchorThePathIsUnderTheQuarantineFolder() throws Exception {
        NioQuarantineDeleter deleter = new NioQuarantineDeleter(List.of());
        PlanExecutionId run = run();
        Path file = tmp.resolve("lib").resolve("photo.jpg");

        Path target = deleter.quarantinePath(file, run);

        assertEquals(file.getFileName(), target.getFileName());
        assertEquals("lib", target.getParent().getFileName().toString());
        List<String> names = new java.util.ArrayList<>();
        target.forEach(n -> names.add(n.toString()));
        int at = names.indexOf(".dfs-quarantine");
        assertTrue(at >= 0, target.toString());
        assertEquals(run.value().toString(), names.get(at + 1));
    }

    @Test
    @DisplayName("DFS-051.AC-1 A move lands the file at the quarantine path and leaves nothing behind")
    void moveLandsTheFileAtTheQuarantinePath() throws Exception {
        NioQuarantineDeleter deleter = new NioQuarantineDeleter(List.of(tmp));
        Path file = file("lib/B/photo.jpg", "twenty-seven bytes of photo!");
        PlanExecutionId run = run();
        Path target = deleter.quarantinePath(file, run);

        FileDeleter.MoveResult result = deleter.move(file, target);

        assertInstanceOf(FileDeleter.MoveResult.Moved.class, result);
        assertFalse(Files.exists(file));
        assertEquals("twenty-seven bytes of photo!", Files.readString(target, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("DFS-051.AC-7 A move onto an existing quarantine path fails and leaves the file in place")
    void moveOntoAnExistingPathFails() throws Exception {
        NioQuarantineDeleter deleter = new NioQuarantineDeleter(List.of(tmp));
        Path file = file("lib/B/photo.jpg", "new");
        PlanExecutionId run = run();
        Path target = deleter.quarantinePath(file, run);
        Files.createDirectories(target.getParent());
        Files.writeString(target, "old", StandardCharsets.UTF_8);

        FileDeleter.MoveResult result = deleter.move(file, target);

        FileDeleter.MoveResult.Failed failed = assertInstanceOf(FileDeleter.MoveResult.Failed.class, result);
        assertFalse(failed.detail().isBlank());
        assertEquals("new", Files.readString(file, StandardCharsets.UTF_8));
        assertEquals("old", Files.readString(target, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("DFS-051.AC-7 A move of a file that is gone fails")
    void moveOfAMissingFileFails() throws Exception {
        NioQuarantineDeleter deleter = new NioQuarantineDeleter(List.of(tmp));
        Path file = tmp.resolve("lib/gone.txt");
        Path target = deleter.quarantinePath(file, run());

        FileDeleter.MoveResult result = deleter.move(file, target);

        assertInstanceOf(FileDeleter.MoveResult.Failed.class, result);
        assertFalse(Files.exists(target));
    }

    @Test
    @DisplayName("DFS-051.AC-11 A quarantine folder that cannot be created is not writable")
    void unwritableQuarantineFolder() throws Exception {
        NioQuarantineDeleter deleter = new NioQuarantineDeleter(List.of(tmp));
        Files.writeString(tmp.resolve(".dfs-quarantine"), "a regular file in the way", StandardCharsets.UTF_8);
        Path file = file("lib/B/photo.jpg", "content");
        Path target = deleter.quarantinePath(file, run());

        FileDeleter.MoveResult result = deleter.move(file, target);

        FileDeleter.MoveResult.QuarantineNotWritable notWritable =
                assertInstanceOf(FileDeleter.MoveResult.QuarantineNotWritable.class, result);
        assertFalse(notWritable.detail().isBlank());
        assertTrue(Files.exists(file));
    }
}
