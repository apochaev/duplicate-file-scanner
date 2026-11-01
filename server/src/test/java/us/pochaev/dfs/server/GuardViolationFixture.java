package us.pochaev.dfs.server;

import us.pochaev.dfs.scanner.infra.adapter.NioFileTreeWalker;

/** Test-only: depends on a scanner infra adapter on purpose, so ArchitectureTest can show the guard fires. */
class GuardViolationFixture {

    Object walker() {
        return new NioFileTreeWalker();
    }
}
