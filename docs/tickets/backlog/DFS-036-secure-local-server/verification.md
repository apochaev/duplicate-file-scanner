# DFS-036 verification

## Product owner review

### What you can do now

Start the server (`./gradlew :server:bootRun` or `bin/dfs-server.bat`). It prints `Open http://127.0.0.1:<port>/?token=…` and opens that link in your browser. The page says "Connected". Any other browser, tab or web page without that link gets nothing from `/api`. A page that opens the server's address without the cookie says "Open the link printed in the console". Requests with a foreign `Host`, or state-changing requests from another origin, are refused before anything else runs.

### Acceptance criteria

- **AC-1** met (test): one `Open http://127.0.0.1:<port>/?token=<t>` line with a 43-character URL-safe token (256 bits). The token appears in no other log line, and the DFS-035 listening line is unchanged.
- **AC-2** met (2 tests):
  - the token gets `302` to `/` and sets `DFS_SESSION` with `HttpOnly` and `SameSite=Strict`
  - a second visit with the same token gets a different session cookie
- **AC-3** met (2 tests): a wrong token gets `403` and no cookie, and `GET /` without a token returns the page.
- **AC-4** met (2 tests):
  - `/api/session` returns `{"authenticated":true}` with the cookie, and `403` without it
  - `/api/anything` without the cookie gets `403`
  - a cookie from an earlier server run gets `403`
- **AC-5** met (5 cases): `evil.example:<port>` on `/` and on `/api/session`, `127.0.0.1:1`, and no `Host` at all all get `403`. `localhost:<port>` and `127.0.0.1:<port>` are accepted.
- **AC-6** met (7 cases plus PUT, PATCH and DELETE): `POST` without an `Origin`, or with `http://evil.example` or `http://127.0.0.1:1`, gets `403` even with the cookie. A matching `Origin` gets past security (`405`, since `/api/session` has no POST). No response carries `Access-Control-Allow-Origin`, an `OPTIONS` preflight included.
- **AC-7** met (3 tests):
  - the opener receives exactly the printed link
  - it is not called with the switch off
  - an opener that throws logs one warning, and the server still serves `/`
- **AC-8** met (by hand, by you): the tab opened at startup showed "Connected", and a private window showed "Open the link printed in the console".
- **AC-9** met (read):
  - `ui.md` U10 describes the redirect exchange, the public page and the deferred path rule
  - `CLAUDE.md`'s `server` entry describes the token link, the cookie and the `Host`/`Origin` filter
  - no server test asserts `404` for `/api`, and every test that starts a server turns the opener off (`TestServer`, `ServerApplicationTest`, and `DFS_SERVER_OPENBROWSER=false` in `LaunchScriptTest`)

### Try it

```
.\gradlew.bat :server:bootRun
```

A browser tab opens and says "Connected". Copy the address without `?token` into a private window: it says "Open the link printed in the console". Stop the server with Ctrl+C.

### Decided without you

- **`GET /` is handled by the token controller.** Mapping "`/` only when a `token` parameter is present" made a plain `GET /` answer 400 instead of falling through to the static page. So the controller serves the page itself when there's no token, and exchanges the token when there is one.
- **Token size:** 256 bits (43 characters), above the 128-bit minimum in AC-1.
- **No default user:** an `AuthenticationManager` that always refuses keeps Spring Boot from creating its default user and logging a generated password. The only way in is the token link.
- **The opener** uses `java.awt.Desktop` first, then the operating system's own command (`rundll32` on Windows, `open` on macOS, `xdg-open` elsewhere).

### Known limits

- **Sessions live in memory.** A restart signs every tab out, by design (AC-4), and the new link has a new port anyway.
- **No path check yet.** The path-inside-roots rule waits for the first endpoint that takes a path (decision 1), and that ticket must carry it.
- **Opener tested on Windows only:** the real browser opener was exercised once there. The macOS and Linux fallbacks have not been run.
- **Long-lived token:** whoever can read your terminal during a run can connect (decision 4, accepted).

### Evidence

- Red run before the implementation: 29 tests, 22 failed. The run is the red commit `59d0203`. The 7 that passed were DFS-035's unchanged tests and two vacuous AC-7 and AC-3 cases.
- Green: `6b1340f`, 29 tests, 0 failed, 0 skipped. `./gradlew build` is green.
- Manual run (port 65255, token redacted here):
  - the log showed the listening line and the `Open` line, with no opener warning
  - `curl`: page `200`; `/api/session` without a cookie `403`; wrong token `403`; `Host: evil.example` `403`; the real token `302` to `/`, then `/api/session` `{"authenticated":true}`
  - the browser checks by the product owner are under AC-8
- `git status`: only paths listed under Touches.

### How it went

Implemented by hand in one session, tests first. Two failures after the first implementation run, both fixed:
- **An implementation bug:** the `params = "token"` mapping gave a plain `GET /` a 400.
- **A test-helper bug:** `TestServer` passed `--dfs.server.open-browser` twice, and Spring joined the values into `false,true`.

A shell heredoc tripped over an apostrophe once, so the files were written with the editor instead.

## Summary

9 criteria met: 7 by automated tests (29 server tests in total), AC-8 by hand in your browser, AC-9 by reading. Tests ran red before the implementation and green after it. Build green, scope respected.
