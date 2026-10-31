package us.pochaev.dfs.scannerspring;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import us.pochaev.dfs.scanner.domain.usecases.command.CancelScan;
import us.pochaev.dfs.scanner.domain.usecases.command.StartScan;
import us.pochaev.dfs.scanner.domain.usecases.query.GetScanResult;
import us.pochaev.dfs.scanner.domain.usecases.query.GetScanStatus;

import java.util.List;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ArchitectureTest {

    @Test
    @DisplayName("DFS-031.AC-4 scanner-spring depends only on the scanner's domain")
    void dependsOnlyOnScannerDomain() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("us.pochaev.dfs.scannerspring");

        noClasses().should().dependOnClassesThat(
                        resideInAnyPackage("us.pochaev.dfs.scanner.application..", "us.pochaev.dfs.scanner.infra.."))
                .because("application and infra are internal to file-scanner")
                .check(classes);

        // Runtime side: no bean handed out is an application or infra object.
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ScannerAutoConfiguration.class)).run(context -> {
            List<Object> beans = List.of(context.getBean(StartScan.class), context.getBean(CancelScan.class),
                    context.getBean(GetScanStatus.class), context.getBean(GetScanResult.class));
            for (Object bean : beans) {
                String pkg = bean.getClass().getPackageName() + ".";
                assertFalse(pkg.startsWith("us.pochaev.dfs.scanner.application.")
                        || pkg.startsWith("us.pochaev.dfs.scanner.infra."), "bean from internal package: " + bean.getClass());
            }
        });
    }
}
