package us.pochaev.dfs.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import us.pochaev.dfs.scanner.domain.model.Result;
import us.pochaev.dfs.scanner.domain.model.ScanConfig;
import us.pochaev.dfs.scanner.domain.model.ScanId;
import us.pochaev.dfs.scanner.domain.model.ScanSnapshot;
import us.pochaev.dfs.scanner.domain.model.ScanStatus;
import us.pochaev.dfs.scanner.domain.usecases.command.CancelScan;
import us.pochaev.dfs.scanner.domain.usecases.command.StartScan;
import us.pochaev.dfs.scanner.domain.usecases.query.GetScanResult;
import us.pochaev.dfs.scanner.domain.usecases.query.GetScanStatus;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ScannerConfigurationTest {

    @TempDir
    Path tmp;

    @Test
    @DisplayName("DFS-020.AC-1 The Spring context provides the four scan ports, sharing one scan repository")
    void contextProvidesTheFourPorts() throws InterruptedException {
        try (var context = new AnnotationConfigApplicationContext(ScannerConfiguration.class)) {
            assertEquals(1, context.getBeansOfType(StartScan.class).size());
            assertEquals(1, context.getBeansOfType(CancelScan.class).size());
            assertEquals(1, context.getBeansOfType(GetScanStatus.class).size());
            assertEquals(1, context.getBeansOfType(GetScanResult.class).size());

            // One ScanUseCases behind all four: a scan started through one bean is known to the others.
            ScanId id = ((Result.Success<ScanId>) context.getBean(StartScan.class)
                    .start(List.of(tmp), ScanConfig.defaults())).value();
            GetScanStatus status = context.getBean(GetScanStatus.class);
            ScanSnapshot snapshot;
            do {
                Thread.sleep(10);
                snapshot = ((Result.Success<ScanSnapshot>) status.status(id)).value();
            } while (snapshot.status() != ScanStatus.COMPLETED && snapshot.status() != ScanStatus.FAILED);
            assertEquals(ScanStatus.COMPLETED, snapshot.status());
            assertInstanceOf(Result.Success.class, context.getBean(GetScanResult.class).result(id));
            assertInstanceOf(Result.Success.class, context.getBean(CancelScan.class).cancel(id));
        }
    }
}
