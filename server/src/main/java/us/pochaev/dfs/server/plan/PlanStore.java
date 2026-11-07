package us.pochaev.dfs.server.plan;

import us.pochaev.dfs.scanner.domain.model.DeletionPlan;
import us.pochaev.dfs.scanner.domain.model.PlanRequest;
import us.pochaev.dfs.scanner.domain.model.Result;
import us.pochaev.dfs.scanner.domain.model.ScanResult;
import us.pochaev.dfs.scanner.domain.usecases.query.PreviewDeletionPlan;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The plans previewed for one COMPLETED scan (DFS-054 decisions 1, 2): kept beside the scan's entry by
 * {@code ScanRegistry.completedState(id, PlanStore.class, PlanStore::new)}, so it lives exactly as long as the scan
 * (this server run) and needs no change to {@code ScanRegistry}. Built at once: the constructor only holds the result.
 * <p>
 * Contract: every plan previewed here stays readable by its ID for the store's life, with no limit; previewing a
 * request whose plan ID is already kept keeps the first copy and answers it (the core's ID is content-derived, so both
 * are equal); a refused request keeps nothing. Thread-safe: concurrent previews of different requests are each kept
 * (a concurrent map, {@code putIfAbsent} by plan ID), and a reader sees a plan only once it is fully built.
 */
final class PlanStore {

    private final ScanResult result;
    private final ConcurrentHashMap<String, KeptPlan> plans = new ConcurrentHashMap<>();

    /** @param result the COMPLETED scan's whole result */
    PlanStore(ScanResult result) {
        this.result = Objects.requireNonNull(result, "result");
    }

    /**
     * Whether {@code path} (absolute, normalized) is one of the scan's roots or lies under one, by
     * {@link Path#startsWith} (the platform's rules: case-insensitive on Windows), as {@code ResultIndex.insideRoots}.
     */
    boolean insideRoots(Path path) {
        return result.roots().stream().anyMatch(path::startsWith);
    }

    /**
     * Previews {@code request} on the scan's result with {@code preview}, then maps the plan with
     * {@link PlanViews#keptPlan} and keeps it under its ID (the existing copy when one is kept already).
     *
     * @param request the core request, every path already checked ({@link PlanRequests#toCore})
     * @param preview the core's {@code PreviewDeletionPlan} bean
     * @return {@code Success} with the kept plan; or the core's {@code Failure} ({@code INVALID_RULE},
     *         {@code UNKNOWN_SET}, {@code PATH_NOT_IN_SET}, {@code DUPLICATE_PICK}) unchanged, nothing kept
     */
    Result<KeptPlan> preview(PlanRequest request, PreviewDeletionPlan preview) {
        return switch (preview.preview(result, request)) {
            case Result.Failure<DeletionPlan> f -> new Result.Failure<>(f.code(), f.message());
            case Result.Success<DeletionPlan> ok -> {
                KeptPlan mapped = PlanViews.keptPlan(ok.value(), request, result);
                KeptPlan existing = plans.putIfAbsent(ok.value().id(), mapped);
                yield new Result.Success<>(existing != null ? existing : mapped);
            }
        };
    }

    /**
     * The plan kept under {@code planId}; empty for any ID never previewed here, a malformed one included (so a plan of
     * another scan is unknown here).
     */
    Optional<KeptPlan> find(String planId) {
        return planId == null ? Optional.empty() : Optional.ofNullable(plans.get(planId));
    }
}
