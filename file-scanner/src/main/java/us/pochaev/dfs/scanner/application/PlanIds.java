package us.pochaev.dfs.scanner.application;

import us.pochaev.dfs.scanner.domain.model.Conflict;
import us.pochaev.dfs.scanner.domain.model.Operation;
import us.pochaev.dfs.scanner.domain.model.Source;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * A {@link us.pochaev.dfs.scanner.domain.model.DeletionPlan}'s content-derived ID (DFS-022 decision 1): lowercase hex
 * SHA-256 of its operations and conflicts in their canonical encoding. Shared by {@code PreviewDeletionPlanService},
 * which computes it, and {@code PlanExecutionService}, which recomputes it to refuse an edited plan (DFS-051
 * decision 14a). The implementer moves {@code PreviewDeletionPlanService.planId} and its encoding helpers here
 * unchanged; the preview's golden plan-ID tests prove the move.
 */
public final class PlanIds {

    private PlanIds() {
    }

    /**
     * @return the 64-character lowercase hex ID of a plan holding the operations and conflicts, in the order given
     * @throws NullPointerException if an argument is or holds null
     */
    public static String of(List<? extends Operation> operations, List<Conflict> conflicts) {
        java.util.Objects.requireNonNull(operations, "operations");
        java.util.Objects.requireNonNull(conflicts, "conflicts");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        str(out, "dfs-deletion-plan/1");
        num(out, operations.size());
        for (Operation operation : operations) {
            Operation.Quarantine q = (Operation.Quarantine) java.util.Objects.requireNonNull(operation, "operation");
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
