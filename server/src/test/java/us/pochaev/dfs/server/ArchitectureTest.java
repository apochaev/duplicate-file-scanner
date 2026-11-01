package us.pochaev.dfs.server;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchitectureTest {

    private static final String SCANNER = "us.pochaev.dfs.scanner";

    /**
     * Spring Boot runs on the classpath, where the scanner's module-info is not enforced.
     * The compiler would let this app use scanner internals, so this rule is the guard (docs/design/ui.md D5).
     */
    static final ArchRule ONLY_SCANNER_DOMAIN = noClasses()
            .should().dependOnClassesThat(
                    resideInAPackage(SCANNER + "..").and(resideOutsideOfPackage(SCANNER + ".domain..")))
            .because("file-scanner exposes only its domain packages; application and infra are internal");

    @Test
    @DisplayName("DFS-035.AC-5 The server's own classes use only the scanner's domain packages")
    void serverUsesOnlyScannerDomainPackages() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("us.pochaev.dfs.server");

        ONLY_SCANNER_DOMAIN.check(classes);
    }

    @Test
    @DisplayName("DFS-035.AC-5 The guard reports a class that depends on a scanner infra adapter")
    void guardCatchesAnInfraDependency() {
        JavaClasses violating = new ClassFileImporter().importClasses(GuardViolationFixture.class);

        AssertionError error = assertThrows(AssertionError.class, () -> ONLY_SCANNER_DOMAIN.check(violating));
        assertTrue(error.getMessage().contains("NioFileTreeWalker"), error.getMessage());
    }
}
