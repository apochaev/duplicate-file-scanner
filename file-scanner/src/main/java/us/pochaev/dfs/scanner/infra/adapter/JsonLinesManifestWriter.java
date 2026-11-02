package us.pochaev.dfs.scanner.infra.adapter;

import us.pochaev.dfs.scanner.infra.port.ManifestLine;
import us.pochaev.dfs.scanner.infra.port.ManifestWriter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 * {@link ManifestWriter} writing UTF-8 JSON Lines through a {@code FileChannel} (DFS-051 decision 6): one JSON object
 * per line in the field order {@link ManifestLine} documents, paths as absolute strings, instants in ISO-8601 UTC, each
 * line followed by {@code force(true)}. Hand-written JSON escaping (the scanner has no JSON dependency). Stateless.
 */
public final class JsonLinesManifestWriter implements ManifestWriter {

    @Override
    public Path create(Path folder, ManifestLine.Run header) throws IOException {
        Files.createDirectories(folder);
        Path manifest = folder.resolve(header.runId().value() + ".jsonl").toAbsolutePath();
        try (FileChannel channel = FileChannel.open(manifest, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE)) {
            write(channel, header);
        }
        return manifest;
    }

    @Override
    public Session open(Path manifest) throws IOException {
        FileChannel channel = FileChannel.open(manifest, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        return new Session() {
            @Override
            public void append(ManifestLine line) throws IOException {
                write(channel, line);
            }

            @Override
            public void close() throws IOException {
                channel.close();
            }
        };
    }

    private static void write(FileChannel channel, ManifestLine line) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap((json(line) + "\n").getBytes(StandardCharsets.UTF_8));
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
        channel.force(true);
    }

    private static String json(ManifestLine line) {
        StringBuilder b = new StringBuilder("{");
        switch (line) {
            case ManifestLine.Run r -> {
                field(b, "type", "run");
                field(b, "runId", r.runId().value().toString());
                field(b, "planId", r.planId());
                b.append(",\"roots\":[");
                List<Path> roots = r.roots();
                for (int i = 0; i < roots.size(); i++) {
                    if (i > 0) {
                        b.append(',');
                    }
                    quote(b, roots.get(i).toString());
                }
                b.append(']');
                field(b, "started", r.started().toString());
            }
            case ManifestLine.Moving m -> {
                field(b, "type", "moving");
                field(b, "path", m.path().toString());
                field(b, "quarantine", m.quarantine().toString());
                field(b, "keeper", m.keeper().toString());
                field(b, "digest", m.digest());
                number(b, "size", m.size());
                field(b, "modified", m.modified().toString());
            }
            case ManifestLine.Moved m -> {
                field(b, "type", "moved");
                field(b, "path", m.path().toString());
                field(b, "quarantine", m.quarantine().toString());
            }
            case ManifestLine.Skipped s -> {
                field(b, "type", "skipped");
                field(b, "path", s.path().toString());
                field(b, "reason", s.cause().name());
                field(b, "subject", s.subject().toString());
                field(b, "detail", s.detail());
            }
            case ManifestLine.End e -> {
                field(b, "type", "end");
                field(b, "status", e.status().name());
                number(b, "moved", e.moved());
                number(b, "skipped", e.skipped());
                number(b, "notAttempted", e.notAttempted());
                field(b, "finished", e.finished().toString());
            }
        }
        return b.append('}').toString();
    }

    private static void field(StringBuilder b, String name, String value) {
        if (b.length() > 1) {
            b.append(',');
        }
        quote(b, name);
        b.append(':');
        quote(b, value);
    }

    private static void number(StringBuilder b, String name, long value) {
        b.append(',');
        quote(b, name);
        b.append(':').append(value);
    }

    private static void quote(StringBuilder b, String s) {
        b.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') {
                b.append("\\\"");
            } else if (c == '\\') {
                b.append("\\\\");
            } else if (c < 0x20) {
                b.append(String.format("\\u%04x", (int) c));
            } else {
                b.append(c);
            }
        }
        b.append('"');
    }
}
