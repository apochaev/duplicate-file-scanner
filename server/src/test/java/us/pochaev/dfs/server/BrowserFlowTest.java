package us.pochaev.dfs.server;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The scan and results flow in a real Chromium against a real server (DFS-053). Run by {@code :server:e2eTest}, not by
 * {@code test} or {@code build}.
 */
@Tag("e2e")
@ExtendWith(OutputCaptureExtension.class)
class BrowserFlowTest {

    /** A set card's heading with the hard link left out of the count: two copies, one copy's bytes reclaimable. */
    private static final Pattern TWO_COPIES = Pattern.compile("^2 copies · (.+) each · \\1 reclaimable$");

    private static Playwright playwright;
    private static Browser browser;

    @TempDir
    Path temp;

    private BrowserFixture fixture;
    private TestServer server;
    private String openLink;
    private BrowserContext context;
    private Page page;

    @BeforeAll
    static void startBrowser() {
        // Only Chromium is needed: skip Playwright's own download of every browser, then install just that one.
        Playwright.CreateOptions options = new Playwright.CreateOptions()
                .setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1"));
        installChromium();
        playwright = Playwright.create(options);
        browser = playwright.chromium().launch();
    }

    /** Runs Playwright's installer for Chromium alone in a child JVM, since its main method ends the process. */
    private static void installChromium() {
        try {
            Process p = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-cp", System.getProperty("java.class.path"), "com.microsoft.playwright.CLI", "install", "chromium")
                    .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            assertTrue(p.waitFor() == 0, "installing Chromium failed");
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException("could not install Chromium", e);
        }
    }

    @AfterAll
    static void stopBrowser() {
        browser.close();
        playwright.close();
    }

    @BeforeEach
    void startServer(CapturedOutput output) {
        fixture = BrowserFixture.create(temp);
        server = TestServer.start();
        openLink = server.base() + "/?token=" + server.token(output.getAll());
        context = browser.newContext(new Browser.NewContextOptions()
                .setPermissions(List.of("clipboard-read", "clipboard-write")));
        page = context.newPage();
    }

    @AfterEach
    void stopServer() {
        context.close();
        server.close();
    }

    @Test
    @DisplayName("DFS-053.AC-1 The token link connects the page and sets the session cookie")
    void tokenLinkConnects() {
        page.navigate(openLink);

        assertThat(page.getByText("Connected")).isVisible();
        assertTrue(context.cookies().stream().anyMatch(c -> c.name.equals("DFS_SESSION")),
                "cookies: " + List.copyOf(context.cookies()));
    }

    @Test
    @DisplayName("DFS-053.AC-2 A scan of the fixture runs to Completed and offers Open results")
    void scanCompletes() {
        scanFixture();

        assertThat(page.locator(".scan-status.status-completed")).isVisible();
        assertThat(page.locator(".open-results")).isVisible();
    }

    @Test
    @DisplayName("DFS-053.AC-3 Open results shows the Overlaps tab with the fixture's folders")
    void resultScreenOpensOnOverlaps() {
        openResults();

        assertThat(page).hasURL(Pattern.compile(".*#/scans/[^/]+$"));
        assertThat(tab("Overlaps")).hasAttribute("aria-selected", "true");
        Locator folders = page.locator(".summaries-table");
        assertThat(folders).containsText(fixture.keep().toString());
        assertThat(folders).containsText(fixture.extra().toString());
    }

    @Test
    @DisplayName("DFS-053.AC-4 Expanding All folders loads the subfolders and selecting one filters the views")
    void treeFiltersTheViews() {
        openResults();
        expandRoot();

        assertThat(folderSelect("keep")).isVisible();
        folderSelect("extra").click();

        assertThat(page.locator(".folder-header .summary-folder")).hasText(fixture.extra().toString());
        tab("Sets").click();
        // Both sets have a copy under extra, and only those are marked.
        assertThat(page.locator(".selected-label")).hasCount(2);
    }

    @Test
    @DisplayName("DFS-053.AC-5 Sets lists both duplicate sets and marks a hard link under its copy")
    void setsShowCopiesAndHardLink() {
        openResults();
        tab("Sets").click();

        assertThat(page.locator(".duplicate-set")).hasCount(2);
        assertThat(page.locator(".sets-summary")).containsText("2 duplicate sets");
        assertThat(page.locator(".set-heading").first()).hasText(TWO_COPIES);
        assertThat(page.locator(".set-heading").last()).hasText(TWO_COPIES);
        assertThat(page.locator(".physical-copy")).hasCount(2 * 2);

        assumeTrue(fixture.hardLinked(), "this platform or its privileges do not allow a hard link");
        assertThat(page.locator(".hard-link")).hasCount(1);
        assertThat(page.locator(".hard-link-label")).hasText("hard link (same file)");
        // Which of the two names the scan lists first is its choice; the mark sits under one copy that has both.
        Locator linkedCopy = page.locator(".physical-copy").filter(new Locator.FilterOptions().setHas(page.locator(".hard-link")));
        assertThat(linkedCopy).containsText(fixture.keep().resolve("shared-a.bin").toString());
        assertThat(linkedCopy).containsText(fixture.keep().resolve("shared-a-link.bin").toString());
    }

    @Test
    @DisplayName("DFS-053.AC-6 Pinning a folder and selecting another shows the comparison, and Unpin removes it")
    void pinCompareAndUnpin() {
        openResults();
        expandRoot();

        page.getByLabel("Pin for comparison: keep").click();
        assertThat(page.locator(".pinned-folder")).hasText("Pinned: " + fixture.keep());
        folderSelect("extra").click();

        Locator comparison = page.getByRole(AriaRole.REGION, new Page.GetByRoleOptions().setName("Comparison"));
        assertThat(comparison.locator(".comparison-pair")).isVisible();
        assertThat(comparison).containsText(fixture.keep().toString());
        assertThat(comparison).containsText(fixture.extra().toString());

        page.locator(".unpin").click();
        assertThat(page.locator(".pinned-folder")).hasCount(0);
        assertThat(comparison).hasCount(0);
    }

    @Test
    @DisplayName("DFS-053.AC-7 Copy folder path puts the folder's absolute path on the browser clipboard")
    void copyFolderPathReachesTheClipboard() {
        openResults();
        tab("Sets").click();
        page.bringToFront();

        page.getByLabel("Copy folder path of " + fixture.sharedAIn(fixture.extra())).click();

        assertThat(page.locator(".copy-success")).isVisible();
        assertEquals(fixture.extra().toString(), page.evaluate("navigator.clipboard.readText()"));
    }

    @Test
    @DisplayName("DFS-053.AC-8 Reloading the result URL shows the same result screen without logging in again")
    void reloadKeepsTheResultScreen() {
        openResults();
        String url = page.url();

        page.reload();

        assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Scan results"))).isVisible();
        assertEquals(url, page.url());
        assertThat(tab("Overlaps")).hasAttribute("aria-selected", "true");
        assertThat(page.getByText("Open the link printed in the console")).hasCount(0);
    }

    /** Connects, adds the fixture root as a chip and starts the scan, then waits for Open results. */
    private void scanFixture() {
        page.navigate(openLink);
        assertThat(page.getByText("Connected")).isVisible();
        Locator input = page.getByPlaceholder("Type or paste an absolute path, then press Enter");
        input.fill(fixture.root().toString());
        input.press("Enter");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Start").setExact(true)).click();
        assertThat(page.locator(".open-results")).isVisible();
    }

    private void openResults() {
        scanFixture();
        page.locator(".open-results").click();
        assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Scan results"))).isVisible();
        assertThat(page.locator(".summaries-table")).isVisible();
    }

    /** The scanned root is the only tree row with a toggle until it is expanded. */
    private void expandRoot() {
        page.locator(".folder-toggle").first().click();
    }

    private Locator folderSelect(String name) {
        return page.locator(".folder-select").filter(new Locator.FilterOptions().setHasText(name + " ·"));
    }

    private Locator tab(String label) {
        return page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(label));
    }
}
