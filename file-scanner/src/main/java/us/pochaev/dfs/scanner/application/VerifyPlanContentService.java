package us.pochaev.dfs.scanner.application;

import us.pochaev.dfs.scanner.domain.model.DeletionPlan;
import us.pochaev.dfs.scanner.domain.model.Result;
import us.pochaev.dfs.scanner.domain.model.VerifyListener;
import us.pochaev.dfs.scanner.domain.model.VerifyReport;
import us.pochaev.dfs.scanner.domain.usecases.query.VerifyPlanContent;
import us.pochaev.dfs.scanner.infra.port.ContentComparator;
import us.pochaev.dfs.scanner.infra.port.FileAttributesReader;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Verifies a previewed plan's content against its keepers (DFS-034) through a {@link FileAttributesReader} and a
 * {@link ContentComparator}: for each operation, both paths' attributes without following links, then a chunked
 * compare only for two regular files of the same size. Its only fields are the ports; the detached-listener state
 * and the byte counters are local to each call, so concurrent calls share nothing.
 */
public final class VerifyPlanContentService implements VerifyPlanContent {

    private final FileAttributesReader reader;
    private final ContentComparator comparator;

    public VerifyPlanContentService(FileAttributesReader reader, ContentComparator comparator) {
        this.reader = Objects.requireNonNull(reader, "reader");
        this.comparator = Objects.requireNonNull(comparator, "comparator");
    }

    @Override
    public Result<VerifyReport> verify(DeletionPlan plan, VerifyListener listener, BooleanSupplier cancelled) {
        throw new UnsupportedOperationException("not implemented: DFS-034");
    }
}
