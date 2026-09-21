package us.pochaev.dfs.scanner.infra.port;

import us.pochaev.dfs.scanner.domain.model.WalkEvent;

@FunctionalInterface
public interface WalkListener {

    /**
     * @return {@code true} to keep walking, {@code false} to stop (for example, on cancellation)
     */
    boolean onEvent(WalkEvent event);
}
