# DFS-043 verification

Implemented by hand (`archetype: none`) by the dfs-backend session, 2026-11-01.

## Product owner review

### What you can do now

Install the CLI once and run it as `dfs` from any folder, next to the server's `dfs-server`, without clashing with other tools called `cli`. README now explains the install, the `PATH` and `JAVA_HOME` setup, and how to update.

### Acceptance criteria

- **DFS-043.AC-1** met: `installBootDist` writes `cli/build/install/dfs-boot/bin/dfs` and `dfs.bat`, and no `cli` or `cli.bat`.
- **DFS-043.AC-2** met: the installed `dfs.bat`, started from a test with `JAVA_HOME` set to Java 25, scans a temp folder with two identical files, exits 0 and prints one `set` line naming both and one `summary` line.
- **DFS-043.AC-3** met (by reading): README "Installing outside the repo" names both `installBootDist` tasks, the `robocopy /MIR` copy of both folders, both `bin` folders on the user `PATH`, `JAVA_HOME` to Java 25, and stopping a running server before updating.
- **DFS-043.AC-4** met (by reading): README "Usage" opens with when to use `dfs.bat` (rebuilds from source, inside the repo) and when the installed `dfs`; CLAUDE.md Commands lists `./gradlew :cli:installBootDist` with `cli/build/install/dfs-boot/bin/dfs(.bat)`.

### Try it

```sh
./gradlew :cli:installBootDist
cli/build/install/dfs-boot/bin/dfs.bat <a copy of some test files> --format=text
```

Look for the text report ending in a `Summary:` line. For a real install, follow README "Installing outside the repo".

### Decided without you

- The install section is its own `##` section after "Usage" (before "Building and testing"), not a subsection of it.
- The example install folders are `C:\Users\<user>\Apps\dfs\cli` and `...\server`, as in the ticket's context.
- AC-2's test compares the last two path elements (`a\same.txt`, `b\same.txt`), not the full path, since the scanner may report the temp folder through its real path.
- The test keeps stdout and stderr in separate files and passes `--progress=off`, so stdout holds only the JSONL.

### Known limits

- The install is a manual copy; no installer or bundled Java (`jpackage`, out of scope).
- `dfs.bat` at the repo root is unchanged (decision 2).
- The test runs `dfs.bat` on Windows and `sh dfs` elsewhere; only the Windows branch was run here.

### Evidence

- `cli/src/test/java/us/pochaev/dfs/cli/LaunchScriptTest.java`: 2 tests, 0 failures (`DFS-043.AC-1`, `DFS-043.AC-2`; AC-2 took 2.6 s).
- `./gradlew build` green in the worktree: file-scanner 357 tests (2 skipped), scanner-spring 18, cli 119 (1 skipped), server 62, Vitest 21; 0 failures.
- Manual run: the installed `dfs.bat` on a copy of `file-scanner/src/test/resources/fixtures` in the session scratchpad printed 2 sets, 4 files, 45 B reclaimable, matching README's sample report.
- Old-name search: `bin/cli`, `cli.bat`, `cli-boot` appear nowhere outside `docs/tickets` (one unrelated hit inside Node's bundled npm docs under `server/.gradle`).
- Changed files: `cli/build.gradle.kts`, `cli/src/test/java/us/pochaev/dfs/cli/LaunchScriptTest.java`, `README.md`, `CLAUDE.md`, plus this ticket's own folder; all within Touches.

### How it went

One pass, no pipeline phases. The first test run passed; the only rework was the docs layout (the install section moved from a `###` under "Usage" to its own `##` section, as decision 4 says).

Ready for the user's approval.

## Summary

- DFS-043.AC-1 met: the install folder is `dfs-boot` with `bin/dfs` and `bin/dfs.bat`, no `bin/cli`.
- DFS-043.AC-2 met: the installed script scans a temp folder, exits 0, one `set` and one `summary` line.
- DFS-043.AC-3 met by reading: README "Installing outside the repo".
- DFS-043.AC-4 met by reading: README "Usage" launcher sentence and the CLAUDE.md Commands line.
- Build green; changes within Touches. Ready for review and `/tickets done`.
