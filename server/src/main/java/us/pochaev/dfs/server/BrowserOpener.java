package us.pochaev.dfs.server;

/** Opens a link in the user's browser (DFS-036 decision 5). Tests replace it with a fake. */
public interface BrowserOpener {

    void open(String url) throws Exception;
}
