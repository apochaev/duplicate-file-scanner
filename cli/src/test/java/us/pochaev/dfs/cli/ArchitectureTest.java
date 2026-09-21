package us.pochaev.dfs.cli;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureTest {

    private static final String SCANNER = "us.pochaev.dfs.scanner";

    /**
     * Spring Boot runs on the classpath, where the scanner's module-info is not enforced.
     * The compiler would let this app use scanner internals, so this rule is the guard.
     */
    @Test
    void usesOnlyScannerDomainPackages() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("us.pochaev.dfs.cli");

        noClasses()
                .should().dependOnClassesThat(
                        resideInAPackage(SCANNER + "..").and(resideOutsideOfPackage(SCANNER + ".domain..")))
                .because("file-scanner exposes only its domain packages; application and infra are internal")
                .check(classes);
    }
}
