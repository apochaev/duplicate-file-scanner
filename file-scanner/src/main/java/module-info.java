/**
 * Duplicate file scanner core. Only the domain packages are exported; {@code application}
 * and {@code infra} are internal and reachable solely through {@code domain.factory}.
 */
module us.pochaev.dfs.scanner {
    exports us.pochaev.dfs.scanner.domain.model;
    exports us.pochaev.dfs.scanner.domain.usecases.query;
    exports us.pochaev.dfs.scanner.domain.usecases.command;
    exports us.pochaev.dfs.scanner.domain.factory;
}
