# DFS-035 verification

## Product owner review

### What you can do now

Run `./gradlew :server:bootRun`. The server starts on your machine only (`127.0.0.1`) on a fresh port each time, and prints `DFS server listening on http://127.0.0.1:<port>/`. Opening that URL shows a placeholder page. `./gradlew :server:installBootDist` gives you `bin/dfs-server.bat` (and `bin/dfs-server` for macOS/Linux), which starts the same server. `./gradlew build` now builds and tests three subprojects.

### Acceptance criteria

- **AC-1** met (build): `./gradlew build` is green with `:server` included. `server/build.gradle.kts` repeats none of the root project's toolchain, release or JUnit settings.
- **AC-2** met (test): two starts get two different ports, neither 8080. Each is reachable on 127.0.0.1 and refused on this machine's non-loopback IPv4 address. The machine has one, so the refusal was really checked.
- **AC-3** met (test): exactly one `DFS server listening on http://127.0.0.1:<port>/` line, with the bound port.
- **AC-4** met (test): `GET /` returns 200 with "DFS server is running", and `GET /api/anything` returns 404.
- **AC-5** met (test, 2 cases): the guard passes on the server's classes and reports a violation naming `NioFileTreeWalker` for the test-only fixture class.
- **AC-6** met (test): `dfs-server.jar` exists and its manifest says `Enable-Native-Access: ALL-UNNAMED`. `bootRun` gets the flag from `applicationDefaultJvmArgs`.
- **AC-7** met (test): `bin/dfs-server.bat` started the server, which printed the listening line within 60 seconds. The test then stopped every process in the tree, and none was left running.
- **AC-8** met (read): `CLAUDE.md` now says three subprojects, with a `server` entry and its three commands. `README.md` has a `server/` line, and `ui.md` U11 names `bin/dfs-server.bat`.

### Try it

```
./gradlew :server:bootRun
```

Open the URL it prints, then stop it with Ctrl+C.

### Decided without you

- **Web starter:** `spring-boot-starter-webmvc`, which is Spring Boot 4's name for the servlet web starter.
- **The script test sets `JAVA_HOME`** to the test's own Java 25, so it doesn't depend on which `java` is first on your `PATH`.
- **The listening line** is logged by a small listener (`StartupUrlLogger`) when the web server has bound its port. The address in the line is the literal `127.0.0.1` from the configuration.
- **Spring banner off**, so the URL line is easy to spot.

### Known limits

- **Stopping `bootRun`:** stopping the Gradle task from outside (not with Ctrl+C in a terminal) left the server's Java process running during my manual check, and I killed it by PID. Ctrl+C in a terminal normally stops both, but I couldn't test that here.
- **Linux/macOS untested:** the script test runs only the script for the current platform, so `bin/dfs-server` (the `sh` script) has not been run on this Windows machine.
- **Bind address in the test:** "bound only to loopback" is checked by connecting through this machine's non-loopback addresses. A machine with none checks loopback reachability only.
- **No security yet:** U10 (token, cookie, `Host`/`Origin`) is the next ticket. Until then the server has no API, by design.

### Evidence

- `./gradlew build`: BUILD SUCCESSFUL (worktree, 2026-10-31).
- `:server:test`: 7 tests, 0 skipped, 0 failures (from `server/build/test-results`):
  - DFS-035.AC-2, AC-3, AC-4 (`ServerApplicationTest`)
  - AC-5 ×2 (`ArchitectureTest`)
  - AC-6 (`NativeAccessTest`)
  - AC-7 (`LaunchScriptTest`)
- Manual `bootRun`:
  - log line `DFS server listening on http://127.0.0.1:61011/`
  - `curl` returned 200 with the placeholder page, and 404 for `/api/anything`
  - the page was opened in the default browser
- `git status`: only paths listed under Touches.

### How it went

Implemented by hand in one session. The tests passed on the first run, and the full build was green the first time. The one surprise was the `bootRun` process outliving its stopped Gradle task, which is noted above.

## Summary

8 criteria met: 6 by automated tests (7 tests), AC-1 by the green build, AC-8 by reading. Manual `bootRun` check done. Scope respected.
