package us.pochaev.dfs.scanner;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * Enforces the hexagonal layering documented in CLAUDE.md. module-info cannot express these
 * rules, so this is the real guard. Rules fail on empty matches (a mistyped package pattern must
 * fail loudly), except the top-level util rule, because that package holds no class yet.
 */
class ArchitectureTest {

    private static final String BASE = "us.pochaev.dfs.scanner";

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE);
    }

    @Test
    void layersRespectDependencyRules() {
        layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("Model").definedBy(BASE + ".domain.model..")
                .layer("Query").definedBy(BASE + ".domain.usecases.query..")
                .layer("Command").definedBy(BASE + ".domain.usecases.command..")
                .layer("Factory").definedBy(BASE + ".domain.factory..")
                .layer("Application").definedBy(BASE + ".application..")
                .layer("Port").definedBy(BASE + ".infra.port..")
                .layer("Adapter").definedBy(BASE + ".infra.adapter..")
                .whereLayer("Model").mayOnlyBeAccessedByLayers("Query", "Command", "Factory", "Application", "Port", "Adapter")
                .whereLayer("Query").mayOnlyBeAccessedByLayers("Application", "Factory")
                .whereLayer("Command").mayOnlyBeAccessedByLayers("Application", "Factory")
                .whereLayer("Port").mayOnlyBeAccessedByLayers("Application", "Adapter", "Factory")
                .whereLayer("Application").mayOnlyBeAccessedByLayers("Factory")
                .whereLayer("Adapter").mayOnlyBeAccessedByLayers("Factory")
                .whereLayer("Factory").mayNotBeAccessedByAnyLayer()
                .check(classes);
    }

    @Test
    void topLevelUtilDoesNotDependOnLayers() {
        noClasses().that().resideInAPackage(BASE + ".util..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE + ".domain..", BASE + ".application..", BASE + ".infra..")
                // top-level util holds only package-info.java, so this rule matches no class yet
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void coreDoesNotDependOnSpring() {
        noClasses()
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..")
                .because("the scanner core must stay framework-free; Spring wiring lives in the apps")
                .check(classes);
    }
}
