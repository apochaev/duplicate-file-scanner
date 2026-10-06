package us.pochaev.dfs.scanner.application;

import us.pochaev.dfs.scanner.domain.model.ScanConfig;
import us.pochaev.dfs.scanner.domain.model.ScanListener;
import us.pochaev.dfs.scanner.domain.model.ScanResult;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * The seam through which {@link ScanService} runs one scan synchronously (DFS-018 decision 12).
 * Production passes the real {@link ScanPipeline}; {@code application} tests pass a scripted fake.
 * Same contract as {@link ScanPipeline#scan}: events go to {@code listener} on the calling thread,
 * {@code stopSignal} reading true ends the run with a {@link java.util.concurrent.CancellationException}.
 */
@FunctionalInterface
interface ScanRunner {

    ScanResult run(List<Path> locations, ScanConfig config, ScanListener listener,
                   BooleanSupplier stopSignal) throws IOException;
}
