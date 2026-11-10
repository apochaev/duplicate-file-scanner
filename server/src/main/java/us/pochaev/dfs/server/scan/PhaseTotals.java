package us.pochaev.dfs.server.scan;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The totals of one scan phase that has started (DFS-064 decision 2), kept after the next phase starts. One of the
 * API's own records: the registry's listener fills it from the core's events, so it is never a core type.
 * <p>
 * Which components apply depends on the phase; the others are 0, never null. {@code WALK}: {@code directories},
 * {@code files} and {@code bytes} found so far (final once finished). {@code PARTIAL_HASH} and {@code FULL_HASH}:
 * {@code files} and {@code bytes} of the phase's input (the core's {@code HashProgress} totals); both are 0 until the
 * phase's first {@code HashProgress} arrives, which a page reads as unknown. {@code HARD_LINKS}: {@code groups}
 * that entered the phase (the {@code GroupDone} total, 0 until the first one). {@code finished} is true once the
 * next phase has started or the scan is COMPLETED; a CANCELLED or FAILED scan leaves its last entry unfinished.
 */
@Schema(name = "PhaseTotals")
public record PhaseTotals(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"WALK", "PARTIAL_HASH", "FULL_HASH", "HARD_LINKS"})
        String phase,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean finished,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long directories,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long files,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long bytes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int groups) {
}
