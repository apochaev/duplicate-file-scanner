package us.pochaev.dfs.scanner.infra.adapter;

import java.nio.file.Path;

/** Child-JVM entry point for DFS-026.AC-8: reads one key and prints the failure's class and message. */
public final class NativeAccessProbe {

    private NativeAccessProbe() {
    }

    public static void main(String[] args) {
        try {
            Object key = new NioFileIdentity().key(Path.of(args[0]));
            System.out.println("KEY " + key);
        } catch (Throwable t) {
            System.out.println(t.getClass().getName() + ": " + t.getMessage());
        }
    }
}
