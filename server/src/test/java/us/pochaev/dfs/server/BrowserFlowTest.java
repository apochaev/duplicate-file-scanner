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
    @DisplayName("DFS-053.AC-3 Open results shows the Sets tab first, and Overlaps with the fixture's folders")
    void resultScreenOpensOnSets() {
        openResults();

        assertThat(page).hasURL(Pattern.compile(".*#/scans/[^/]+$"));
        assertThat(tab("Sets")).hasAttribute("aria-selected", "true");
        tab("Overlaps").click();
        assertThat(tab("Overlaps")).hasAttribute("aria-selected", "true");
        showSmallGroups();
        // The fixture's two duplicate sets are small, so they form one group of the folders keep and extra.
        assertThat(page.locator("div.group")).hasCount(1);
        assertThat(page.locator(".group-files")).hasText("2 files");
        assertThat(page.locator(".group-meta")).containsText("shared by 2 folders");
        assertThat(folderLine("keep")).hasCount(1);
        assertEquals(fixture.keep().toString(), folderLine("keep").locator(".short-path").getAttribute("title"));
        assertThat(folderLine("extra")).hasCount(1);
        assertEquals(fixture.extra().toString(), folderLine("extra").locator(".short-path").getAttribute("title"));
        assertThat(page.locator(".shared-count").first()).hasText(Pattern.compile("2 of \\d+ files"));
        assertThat(page.locator(".app-tag")).hasCount(0);
    }

    @Test
    @DisplayName("DFS-068.AC-1, DFS-068.AC-2 The group header, a folder line's path and its share figure are laid out with a width")
    void groupLayoutIsVisible() {
        openResults();
        tab("Overlaps").click();
        showSmallGroups();

        for (String selector : List.of(".group-header .group-files", "li.folder-line .short-path", "li.folder-line .shared-count")) {
            Locator element = page.locator(selector).first();
            assertThat(element).isVisible();
            com.microsoft.playwright.options.BoundingBox box = element.boundingBox();
            assertTrue(box != null && box.width > 0 && box.height > 0, selector + " has no size: " + box);
        }
    }

    @Test
    @DisplayName("DFS-053.AC-4, DFS-065.AC-3 The folder browser opens the single root's subfolders and selecting one filters the views")
    void folderBrowserFiltersTheViews() {
        openResults();

        assertThat(folderSelect("keep")).isVisible();
        folderSelect("extra").click();

        tab("Overlaps").click();
        showSmallGroups();
        // Only the folders of the group under extra remain: the group's two lines still list keep and extra.
        assertThat(page.locator("div.group")).hasCount(1);
        assertThat(folderLine("extra")).hasCount(1);
        assertEquals(fixture.extra().toString(), folderLine("extra").locator(".short-path").getAttribute("title"));
        assertThat(page.locator(".summaries-table")).hasCount(0);
        tab("Sets").click();
        // Both sets have a copy under extra, and only those are marked.
        assertThat(page.locator(".selected-badge")).hasCount(2);
    }

    @Test
    @DisplayName("DFS-053.AC-5 Sets lists both duplicate sets and marks a copy with a hard link by a links badge")
    void setsShowCopiesAndHardLink() {
        openResults();

        assertThat(page.locator(".duplicate-set")).hasCount(2);
        assertThat(page.locator(".set-count").first()).hasText("2 copies");
        assertThat(page.locator(".set-count").last()).hasText("2 copies");
        assertThat(page.locator(".set-size")).hasCount(2);
        // Two physical copies per set; the hard link is no third row.
        assertThat(page.locator("tr.copy-row")).hasCount(2 * 2);

        assumeTrue(fixture.hardLinked(), "this platform or its privileges do not allow a hard link");
        assertThat(page.locator(".links-badge")).hasCount(1);
        assertThat(page.locator(".links-badge")).hasText("2 links");
        assertThat(page.getByText("hard link (same file)")).hasCount(0);
        // Which of the two names the scan lists first is its choice; the badge's tooltip lists both.
        String tooltip = page.locator(".links-badge").getAttribute("title");
        assertTrue(tooltip.contains(fixture.keep().resolve("shared-a.bin").toString()), tooltip);
        assertTrue(tooltip.contains(fixture.keep().resolve("shared-a-link.bin").toString()), tooltip);
    }

    @Test
    @DisplayName("DFS-053.AC-6 Pinning a folder and selecting another shows the comparison, and Unpin removes it")
    void pinCompareAndUnpin() {
        openResults();

        page.getByLabel("Pin for comparison: keep").click();
        tab("Overlaps").click();
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
    @DisplayName("DFS-053.AC-7 Copy puts the file's absolute path on the browser clipboard")
    void copyPathReachesTheClipboard() {
        openResults();
        page.bringToFront();
        String file = fixture.sharedAIn(fixture.extra()).toString();

        page.getByLabel("Copy path of " + file, new Page.GetByLabelOptions().setExact(true)).click();

        assertThat(page.locator(".copy-success")).isVisible();
        assertEquals(file, page.evaluate("navigator.clipboard.readText()"));
    }

    @Test
    @DisplayName("DFS-053.AC-8 Reloading the result URL shows the same result screen without logging in again")
    void reloadKeepsTheResultScreen() {
        openResults();
        String url = page.url();

        page.reload();

        assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Scan results"))).isVisible();
        assertEquals(url, page.url());
        assertThat(tab("Sets")).hasAttribute("aria-selected", "true");
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
        assertThat(page.locator(".sets-table")).isVisible();
    }

    /** The fixture's groups are under the 1 MiB default, so the tab offers them behind "Show N small groups". */
    private void showSmallGroups() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("Show \\d+ small groups?"))).click();
        assertThat(page.locator("div.group").first()).isVisible();
    }

    /** The folder line whose path ends in the given folder name. */
    private Locator folderLine(String name) {
        return page.locator("li.folder-line").filter(new Locator.FilterOptions().setHas(
                page.locator(".path-name").getByText(name, new Locator.GetByTextOptions().setExact(true))));
    }

    private Locator folderSelect(String name) {
        return page.locator(".folder-select").filter(new Locator.FilterOptions().setHas(
                page.locator(".folder-name").getByText(name, new Locator.GetByTextOptions().setExact(true))));
    }

    private Locator tab(String label) {
        return page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(label));
    }
}
