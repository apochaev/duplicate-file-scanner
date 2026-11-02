package us.pochaev.dfs.scanner.infra.adapter;

import us.pochaev.dfs.scanner.infra.port.ManifestLine;
import us.pochaev.dfs.scanner.infra.port.ManifestWriter;

import java.io.IOException;
import java.nio.file.Path;

/**
 * {@link ManifestWriter} writing UTF-8 JSON Lines through a {@code FileChannel} (DFS-051 decision 6): one JSON object
 * per line in the field order {@link ManifestLine} documents, paths as absolute strings, instants in ISO-8601 UTC, each
 * line followed by {@code force(true)}. Hand-written JSON escaping (the scanner has no JSON dependency). Stateless.
 */
public final class JsonLinesManifestWriter implements ManifestWriter {

    @Override
    public Path create(Path folder, ManifestLine.Run header) throws IOException {
        throw new UnsupportedOperationException("not implemented: DFS-051");
    }

    @Override
    public Session open(Path manifest) throws IOException {
        throw new UnsupportedOperationException("not implemented: DFS-051");
    }
}
