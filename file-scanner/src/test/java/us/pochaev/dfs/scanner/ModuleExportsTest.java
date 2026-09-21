package us.pochaev.dfs.scanner;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reads the compiled module-info.class and checks that only domain packages are exported.
 * Tests run on the classpath, so this is the only place the module descriptor is verified.
 */
class ModuleExportsTest {

    private static final String BASE = "us.pochaev.dfs.scanner";
    private static final String DOMAIN = BASE + ".domain.";

    private static ModuleDescriptor descriptor;

    @BeforeAll
    static void readDescriptor() {
        Path mainClasses = Path.of(System.getProperty("scanner.main.classes"));
        descriptor = ModuleFinder.of(mainClasses).find(BASE)
                .orElseThrow(() -> new AssertionError("no module " + BASE + " in " + mainClasses))
                .descriptor();
    }

    @Test
    void exportsOnlyDomainPackagesUnqualified() {
        for (ModuleDescriptor.Exports exports : descriptor.exports()) {
            assertTrue(exports.source().startsWith(DOMAIN),
                    "non-domain package exported: " + exports.source());
            assertTrue(!exports.isQualified(),
                    "qualified export is not expected: " + exports);
        }
    }

    @Test
    void exportsEveryDomainPackageExceptUtil() {
        Set<String> exported = descriptor.exports().stream()
                .map(ModuleDescriptor.Exports::source)
                .collect(Collectors.toSet());
        Set<String> missing = descriptor.packages().stream()
                .filter(p -> p.startsWith(DOMAIN))
                .filter(p -> !p.contains(".util"))
                .filter(p -> !exported.contains(p))
                .collect(Collectors.toSet());
        assertTrue(missing.isEmpty(), "domain packages missing from module-info exports: " + missing);
    }

    @Test
    void opensNothingForReflection() {
        assertTrue(descriptor.opens().isEmpty(), "unexpected opens: " + descriptor.opens());
        assertTrue(!descriptor.isOpen(), "module must not be declared open");
    }
}
