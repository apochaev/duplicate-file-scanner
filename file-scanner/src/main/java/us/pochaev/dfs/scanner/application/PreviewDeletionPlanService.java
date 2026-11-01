package us.pochaev.dfs.scanner.application;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import us.pochaev.dfs.scanner.domain.model.Conflict;
import us.pochaev.dfs.scanner.domain.model.DeletionPlan;
import us.pochaev.dfs.scanner.domain.model.DuplicateSet;
import us.pochaev.dfs.scanner.domain.model.ErrorCode;
import us.pochaev.dfs.scanner.domain.model.FileEntry;
import us.pochaev.dfs.scanner.domain.model.FolderTotal;
import us.pochaev.dfs.scanner.domain.model.KeepAndQuarantine;
import us.pochaev.dfs.scanner.domain.model.KeepCopy;
import us.pochaev.dfs.scanner.domain.model.LeftBehind;
import us.pochaev.dfs.scanner.domain.model.Operation;
import us.pochaev.dfs.scanner.domain.model.PhysicalCopy;
import us.pochaev.dfs.scanner.domain.model.PlanRequest;
import us.pochaev.dfs.scanner.domain.model.PlanTotals;
import us.pochaev.dfs.scanner.domain.model.Result;
import us.pochaev.dfs.scanner.domain.model.ScanResult;
import us.pochaev.dfs.scanner.domain.model.Source;
import us.pochaev.dfs.scanner.domain.usecases.query.PreviewDeletionPlan;

/**
 * Validates a {@link PlanRequest} and builds the {@link DeletionPlan} it produces on a {@link ScanResult} (DFS-022).
 * Has no fields: a pure function of its arguments. The algorithm and the plan ID's canonical encoding are in the
 * story's {@code design.md}.
 */
public final class PreviewDeletionPlanService implements PreviewDeletionPlan {

    /** A copy of a set with its links sorted. */
    private record Copy(List<Path> links) {
        Path first() {
            return links.getFirst();
        }
    }

    /** One rule's claim on a copy: the rule index and the index of the copy that rule keeps. */
    private record Candidate(int rule, int keeper) {
    }

    @Override
    public Result<DeletionPlan> preview(ScanResult scan, PlanRequest request) {
        Objects.requireNonNull(scan, "scan");
        Objects.requireNonNull(request, "request");

        Result.Failure<DeletionPlan> failure = validate(scan, request);
        if (failure != null) {
            return failure;
        }

        Map<String, KeepCopy> picks = new TreeMap<>();
        for (KeepCopy pick : request.picks()) {
            picks.put(pick.digest(), pick);
        }

        List<Operation.Quarantine> operations = new ArrayList<>();
        List<Conflict> conflicts = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (DuplicateSet set : scan.sets()) {
            if (!seen.add(set.digest())) {
                continue;
            }
            List<Copy> copies = copiesOf(set);
            KeepCopy pick = picks.get(set.digest());
            if (pick != null) {
                planPick(set, copies, pick, operations);
            } else {
                planRules(set, copies, request, operations, conflicts);
            }
        }

        operations.sort(Comparator.<Operation.Quarantine>comparingInt(PreviewDeletionPlanService::sourceKey)
                .thenComparing(PreviewDeletionPlanService::sourceDigest)
                .thenComparing(op -> op.links().getFirst()));
        conflicts.sort(Comparator.comparing(Conflict::digest));

        long files = 0;
        long bytes = 0;
        Set<String> digests = new HashSet<>();
        for (Operation.Quarantine op : operations) {
            files += op.links().size();
            bytes += op.size();
            digests.add(op.digest());
        }
        PlanTotals totals = new PlanTotals(files, bytes, digests.size());

        List<LeftBehind> leftBehind = new ArrayList<>();
        List<KeepAndQuarantine> rules = request.rules();
        for (int i = 0; i < rules.size(); i++) {
            Path q = rules.get(i).quarantineIn();
            long left = 0;
            long leftBytes = 0;
            for (FolderTotal folder : scan.folders()) {
                if (folder.folder().startsWith(q)) {
                    left += folder.files();
                    leftBytes += folder.bytes();
                }
            }
            for (Operation.Quarantine op : operations) {
                for (Path link : op.links()) {
                    if (link.startsWith(q)) {
                        left--;
                        leftBytes -= op.size();
                    }
                }
            }
            leftBehind.add(new LeftBehind(i, q, Math.max(0, left), Math.max(0, leftBytes)));
        }

        String id = planId(operations, conflicts);
        return new Result.Success<>(new DeletionPlan(id, List.copyOf(operations), conflicts, totals, leftBehind));
    }

    private static Result.Failure<DeletionPlan> validate(ScanResult scan, PlanRequest request) {
        List<KeepAndQuarantine> rules = request.rules();
        for (int i = 0; i < rules.size(); i++) {
            KeepAndQuarantine rule = rules.get(i);
            if (rule.quarantineIn().startsWith(rule.keep())) {
                return new Result.Failure<>(ErrorCode.INVALID_RULE, "rule " + i + ": quarantineIn "
                        + rule.quarantineIn() + " equals or lies under keep " + rule.keep());
            }
        }
        Set<String> picked = new HashSet<>();
        for (KeepCopy pick : request.picks()) {
            DuplicateSet set = null;
            for (DuplicateSet candidate : scan.sets()) {
                if (candidate.digest().equals(pick.digest())) {
                    set = candidate;
                    break;
                }
            }
            if (set == null) {
                return new Result.Failure<>(ErrorCode.UNKNOWN_SET,
                        "pick " + pick.digest() + ": no set with this digest in the scan");
            }
            boolean found = false;
            for (FileEntry file : set.files()) {
                if (file.path().equals(pick.keep())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return new Result.Failure<>(ErrorCode.PATH_NOT_IN_SET,
                        "pick " + pick.digest() + ": " + pick.keep() + " is not a link of this set");
            }
            if (!picked.add(pick.digest())) {
                return new Result.Failure<>(ErrorCode.DUPLICATE_PICK,
                        "pick " + pick.digest() + ": a second pick for this digest");
            }
        }
        return null;
    }

    private static List<Copy> copiesOf(DuplicateSet set) {
        List<Copy> copies = new ArrayList<>();
        for (PhysicalCopy copy : set.copies()) {
            copies.add(new Copy(copy.links().stream().map(FileEntry::path).sorted().toList()));
        }
        return copies;
    }

    private static void planPick(DuplicateSet set, List<Copy> copies, KeepCopy pick,
                                 List<Operation.Quarantine> out) {
        Copy keeper = null;
        for (Copy copy : copies) {
            if (copy.links().contains(pick.keep())) {
                keeper = copy;
                break;
            }
        }
        for (Copy copy : copies) {
            if (copy != keeper) {
                out.add(new Operation.Quarantine(set.digest(), set.size(), copy.links(), keeper.first(),
                        new Source.FromPick(pick)));
            }
        }
    }

    private static void planRules(DuplicateSet set, List<Copy> copies, PlanRequest request,
                                  List<Operation.Quarantine> out, List<Conflict> conflicts) {
        List<KeepAndQuarantine> rules = request.rules();
        Map<Integer, Candidate> chosen = new TreeMap<>();
        Set<Integer> rulesWithCandidates = new TreeSet<>();
        List<Candidate> all = new ArrayList<>();
        for (int i = 0; i < rules.size(); i++) {
            KeepAndQuarantine rule = rules.get(i);
            int keeper = -1;
            for (int c = 0; c < copies.size(); c++) {
                if (anyUnder(copies.get(c), rule.keep())
                        && (keeper < 0 || copies.get(c).first().compareTo(copies.get(keeper).first()) < 0)) {
                    keeper = c;
                }
            }
            if (keeper < 0) {
                continue;
            }
            for (int c = 0; c < copies.size(); c++) {
                Copy copy = copies.get(c);
                if (allUnder(copy, rule.quarantineIn()) && !anyUnder(copy, rule.keep())
                        && request.exceptions().stream().noneMatch(e -> anyUnder(copy, e))) {
                    Candidate candidate = new Candidate(i, keeper);
                    all.add(candidate);
                    rulesWithCandidates.add(i);
                    chosen.putIfAbsent(c, candidate);
                }
            }
        }
        for (Candidate candidate : all) {
            if (chosen.containsKey(candidate.keeper())) {
                List<Source> sources = new ArrayList<>();
                for (int i : rulesWithCandidates) {
                    sources.add(new Source.FromRule(i, rules.get(i)));
                }
                conflicts.add(new Conflict(set.digest(), sources));
                return;
            }
        }
        for (Map.Entry<Integer, Candidate> entry : chosen.entrySet()) {
            Copy copy = copies.get(entry.getKey());
            Candidate candidate = entry.getValue();
            out.add(new Operation.Quarantine(set.digest(), set.size(), copy.links(),
                    copies.get(candidate.keeper()).first(),
                    new Source.FromRule(candidate.rule(), rules.get(candidate.rule()))));
        }
    }

    private static boolean anyUnder(Copy copy, Path folder) {
        return copy.links().stream().anyMatch(l -> l.startsWith(folder));
    }

    private static boolean allUnder(Copy copy, Path folder) {
        return copy.links().stream().allMatch(l -> l.startsWith(folder));
    }

    private static int sourceKey(Operation op) {
        return switch (op.source()) {
            case Source.FromRule r -> r.index();
            case Source.FromPick p -> Integer.MAX_VALUE;
        };
    }

    private static String sourceDigest(Operation op) {
        return switch (op.source()) {
            case Source.FromRule r -> "";
            case Source.FromPick p -> p.pick().digest();
        };
    }

    private static String planId(List<Operation.Quarantine> operations, List<Conflict> conflicts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        str(out, "dfs-deletion-plan/1");
        num(out, operations.size());
        for (Operation.Quarantine q : operations) {
            str(out, "quarantine");
            str(out, q.digest());
            num(out, q.size());
            num(out, q.links().size());
            for (Path link : q.links()) {
                str(out, link.toString());
            }
            str(out, q.keeper().toString());
            source(out, q.source());
        }
        num(out, conflicts.size());
        for (Conflict conflict : conflicts) {
            str(out, conflict.digest());
            num(out, conflict.sources().size());
            for (Source s : conflict.sources()) {
                source(out, s);
            }
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(out.toByteArray()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void source(ByteArrayOutputStream out, Source source) {
        switch (source) {
            case Source.FromRule r -> {
                str(out, "rule");
                num(out, r.index());
                str(out, r.rule().keep().toString());
                str(out, r.rule().quarantineIn().toString());
            }
            case Source.FromPick p -> {
                str(out, "pick");
                str(out, p.pick().digest());
                str(out, p.pick().keep().toString());
            }
        }
    }

    private static void num(ByteArrayOutputStream out, long n) {
        str(out, Long.toString(n));
    }

    private static void str(ByteArrayOutputStream out, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        out.writeBytes((bytes.length + ":").getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(bytes);
    }
}
