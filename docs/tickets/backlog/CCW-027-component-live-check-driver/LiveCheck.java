import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import java.util.*;

/** Drives the built jar's scan screen in Edge and logs the scan row's DOM in each phase (DFS-057 verifier live check). */
public class LiveCheck {
    public static void main(String[] args) throws Exception {
        String link = args[0];
        String folder = args[1];
        List<String> consoleErrors = new ArrayList<>();
        try (Playwright pw = Playwright.create()) {
            Browser browser = pw.chromium().launch(new BrowserType.LaunchOptions().setChannel("msedge").setHeadless(true));
            Page page = browser.newContext().newPage();
            page.onConsoleMessage(m -> { if (m.type().equals("error") || m.type().equals("warning")) consoleErrors.add(m.type() + ": " + m.text()); });
            page.onPageError(e -> consoleErrors.add("pageerror: " + e));
            page.navigate(link);
            page.getByText("Connected").waitFor();
            Locator input = page.getByPlaceholder("Type or paste an absolute path, then press Enter");
            input.fill(folder);
            input.press("Enter");
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Start").setExact(true)).click();
            long start = System.currentTimeMillis();
            String last = "";
            Locator row = page.locator("app-scan-row").first();
            while (System.currentTimeMillis() - start < 30 * 60_000) {
                String snap = (String) row.evaluate("r => { const q = s => r.querySelector(s)?.textContent?.trim() ?? '-';"
                        + " const b = r.querySelector('mat-progress-bar');"
                        + " return [q('.scan-status'), q('.scan-phase-line'), q('.scan-eta'), q('.scan-found'),"
                        + " b ? (b.getAttribute('mode') + ' aria-valuenow=' + b.getAttribute('aria-valuenow') + ' label=' + b.getAttribute('aria-label')) : 'no bar',"
                        + " q('.scan-counts'), q('.scan-failure'), r.querySelector('.open-results') ? 'open-results' : ''].join(' | '); }");
                if (!snap.equals(last)) {
                    System.out.printf("%7.1fs %s%n", (System.currentTimeMillis() - start) / 1000.0, snap);
                    last = snap;
                }
                if (snap.startsWith("Completed") || snap.startsWith("Failed") || snap.startsWith("Cancelled")) break;
                Thread.sleep(250);
            }
            System.out.println("CONSOLE ERRORS/WARNINGS: " + consoleErrors.size());
            consoleErrors.forEach(System.out::println);
            browser.close();
        }
    }
}
