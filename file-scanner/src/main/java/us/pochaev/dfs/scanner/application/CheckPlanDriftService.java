package us.pochaev.dfs.scanner.application;

import us.pochaev.dfs.scanner.domain.model.DeletionPlan;
import us.pochaev.dfs.scanner.domain.model.DriftReport;
import us.pochaev.dfs.scanner.domain.model.Result;
import us.pochaev.dfs.scanner.domain.model.ScanResult;
import us.pochaev.dfs.scanner.domain.usecases.query.CheckPlanDrift;
import us.pochaev.dfs.scanner.infra.port.FileAttributesReader;

import us.pochaev.dfs.scanner.domain.model.DriftKind;
import us.pochaev.dfs.scanner.domain.model.DuplicateSet;
import us.pochaev.dfs.scanner.domain.model.ErrorCode;
import us.pochaev.dfs.scanner.domain.model.FileEntry;
import us.pochaev.dfs.scanner.domain.model.Operation;
import us.pochaev.dfs.scanner.domain.model.PathDrift;
import us.pochaev.dfs.scanner.domain.model.PhysicalCopy;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Checks a previewed plan for drift since its scan (DFS-033) through a {@link FileAttributesReader}: validates the
 * whole plan against the scan before any read, then reads each distinct path once in plan order and compares it with
 * the recorded {@code FileEntry}. Its only field is the port, so concurrent calls share nothing.
 */
public final class CheckPlanDriftService implements CheckPlanDrift {

    private final FileAttributesReader reader;

    public CheckPlanDriftService(FileAttributesReader reader) {
        this.reader = Objects.requireNonNull(reader, "reader");
    }

    @Override
    public Result<DriftReport> check(ScanResult scan, DeletionPlan plan) {
        Objects.requireNonNull(scan, "scan");
        Objects.requireNonNull(plan, "plan");
        record Item(Path path, PathDrift.Role role, String digest, FileEntry recorded) {
        }
        List<Item> collected = new ArrayList<>();
        Set<Path> seen = new HashSet<>();
        int i = 0;
        for (Operation op : plan.operations()) {
            switch (op) {
                case Operation.Quarantine q -> {
                    DuplicateSet set = null;
                    for (DuplicateSet s : scan.sets()) {
                        if (s.digest().equals(q.digest())) {
                            set = s;
                            break;
                        }
                    }
                    if (set == null) {
                        return new Result.Failure<>(ErrorCode.PLAN_NOT_FROM_SCAN,
                                "operation " + i + ": no set with digest " + q.digest() + " in the scan");
                    }
                    Map<Path, FileEntry> entries = new HashMap<>();
                    for (PhysicalCopy copy : set.copies()) {
                        for (FileEntry link : copy.links()) {
                            entries.putIfAbsent(link.path(), link);
                        }
                    }
                    List<Path> paths = new ArrayList<>(q.links());
                    paths.add(q.keeper());
                    for (Path p : paths) {
                        if (!entries.containsKey(p)) {
                            return new Result.Failure<>(ErrorCode.PLAN_NOT_FROM_SCAN,
                                    "operation " + i + ": " + p + " is not a copy of set " + q.digest()
                                            + " in the scan");
                        }
                    }
                    for (Path p : q.links()) {
                        if (seen.add(p)) {
                            collected.add(new Item(p, PathDrift.Role.CANDIDATE, q.digest(), entries.get(p)));
                        }
                    }
                    if (seen.add(q.keeper())) {
                        collected.add(new Item(q.keeper(), PathDrift.Role.KEEPER, q.digest(),
                                entries.get(q.keeper())));
                    }
                }
            }
            i++;
        }
        List<PathDrift> drifts = new ArrayList<>();
        for (Item it : collected) {
            DriftKind kind = null;
            String detail = "";
            try {
                BasicFileAttributes a = reader.read(it.path());
                if (!(a.isRegularFile() && !a.isOther() && !a.isSymbolicLink())) {
                    kind = DriftKind.NOT_A_REGULAR_FILE;
                } else if (a.size() != it.recorded().size()) {
                    kind = DriftKind.SIZE_CHANGED;
                } else if (!a.lastModifiedTime().toInstant().equals(it.recorded().lastModified())) {
                    kind = DriftKind.MODIFIED_CHANGED;
                }
            } catch (NoSuchFileException e) {
                kind = DriftKind.MISSING;
            } catch (IOException e) {
                kind = DriftKind.UNREADABLE;
                String m = e.getMessage();
                detail = m == null || m.isBlank() ? e.toString() : m;
            }
            if (kind != null) {
                drifts.add(new PathDrift(it.path(), it.role(), it.digest(), kind, detail));
            }
        }
        return new Result.Success<>(new DriftReport(plan.id(), collected.size(), drifts));
    }
}
