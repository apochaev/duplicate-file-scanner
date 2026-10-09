---
id: DFS-020
title: Scan from the CLI and write JSONL
status: in-review
type: feature
scope: [cli, docs]
archetype: none
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
priority: medium
depends_on: [DFS-019]
touches: [CLAUDE.md, cli/build.gradle.kts, cli/src/main/resources, us.pochaev.dfs.cli]
blocked_reason:
created: 2026-10-07
updated: 2026-10-08
completed:
---

# DFS-020: Scan from the CLI and write JSONL

## Story

As a user of the duplicate file scanner
I want to run the CLI on one or more folders and get the scan's results as JSON Lines
So that I can see the duplicates it finds on real disks, and process them with other tools (jq, scripts) before any delete workflow exists

## Context

The scanner library is complete for scanning. `ScannerFactories.scanUseCases(Executor)` (DFS-019) returns `ScanUseCases(StartScan start, CancelScan cancel, GetScanStatus status, GetScanResult result)`, wired to the real NIO adapters. A completed scan's `ScanResult` holds `sets` (`DuplicateSet`: size, SHA-256 digest, files, and physical copies with hard links grouped) and `skipped` (`WalkEvent.Skipped`: path, `SkipReason` `SYMLINK`/`SPECIAL_FILE`/`UNREADABLE`, detail). `StartScan` rejects bad locations up front with `Failure(NO_LOCATIONS | LOCATION_NOT_FOUND | NOT_A_DIRECTORY | LOCATION_UNREADABLE)` (DFS-018).

`cli` today: `Main` is a stub that prints "not implemented yet"; `cli/build.gradle.kts` uses the plain `application` plugin; Spring Boot is not yet added (CLAUDE.md, "Current state"). CLAUDE.md says `cli` is a Spring Boot CLI and the composition root, that it owns the `@Configuration` whose `@Bean`s call the factories (decision 6), that the executor is supplied by the caller (virtual threads for the CLI), and that progress is reported through a listener, never printed by the core. `cli`'s `ArchitectureTest` forbids depending on anything under `us.pochaev.dfs.scanner` outside `domain`. The CLI framework (Spring Shell vs `ApplicationRunner` vs picocli) is still open in CLAUDE.md.

**Decisions (refinement, 2026-10-07):**

1. **Archetype `none`.** Built by hand, not through the story pipeline: no archetype fits CLI wiring (`use-case` already fit DFS-019's wiring poorly, DFS-019.L-5), and a `cli` archetype is better drawn later from this ticket and DFS-019 than designed ahead of either. Tests still carry `DFS-020.AC-n` in their `@DisplayName`, and the implementing session ticks each done-item with its evidence.
2. **Spring Boot with an `ApplicationRunner`, arguments parsed by hand.** `cli` becomes a Spring Boot application (Spring Boot 4, Java 25, JUnit 6). One `ApplicationRunner` reads `ApplicationArguments`: plain arguments are the folders, `--name=value` options the settings; the exit code is returned through `ExitCodeGenerator` and `SpringApplication.exit`. No CLI library: one command with a few options does not need one, and whether the delete workflow is interactive (Spring Shell) or a set of one-shot commands (picocli) is not yet known. The CLI framework question in CLAUDE.md stays open; the delete ticket is the natural point to revisit it. Found while implementing: the factory returns one `ScanService` behind all four ports, so four beans of that object would each match every port type and injection fails ("expected single matching bean but found 2"). Each bean therefore wraps its port in a delegate of that interface alone, and AC-1's scenario checks the shared repository by behavior rather than by object identity.
3. **JSONL schema: typed lines in one stream.** Every line is one JSON object (Jackson, from Spring Boot) with a `type` field, in this order: one `set` line per `DuplicateSet`, in the order of `ScanResult.sets`; one `skipped` line per skipped path, in the order of `ScanResult.skipped`; one final `summary` line. Shapes:
   - `{"type":"set","size":<bytes>,"digest":"<sha-256 hex>","copies":[["<path>",...],...]}`: one inner array per `PhysicalCopy`, holding its links' paths, so hard links stay one copy (CLAUDE.md invariant).
   - `{"type":"skipped","path":"<path>","reason":"SYMLINK|SPECIAL_FILE|UNREADABLE","detail":"<detail>"}`.
   - `{"type":"summary","sets":<n>,"files":<n>,"skipped":<n>,"reclaimableBytes":<n>}`: `files` counts every path in every set; `reclaimableBytes` is the sum over sets of `size * (copies - 1)`. The summary's presence marks a completed scan.
   - Paths are written as the scanner reports them (absolute real paths, `Path.toString()`, platform separators). No `lastModified` for now: nothing consumes it yet, and adding a field later does not break a jq consumer.
4. **Output: JSONL on stdout only; progress and errors on stderr.** No `--output` option; a file is made by redirecting. Stdout carries nothing but JSONL lines: Spring Boot's banner and startup log would otherwise land on stdout, so the banner is off and console logging goes to stderr (configuration in `cli/src/main/resources`, hence its place in touches). Progress, from a `ScanListener`, is on by default and written to stderr: one line per `PhaseStarted`, plus walk and group counts throttled to about one line a second. Through Gradle, `./gradlew -q :cli:run` keeps Gradle's own lifecycle lines out of stdout.
5. **Arguments: folders plus `--min-size=<bytes>`.** Every plain (non-option) argument is a folder; all of them go, in the order given, to one `StartScan.start` call, passed as given (`Path.of`; a relative path resolves against the working directory, which under `:cli:run` is `cli/`). `--min-size` must be a whole number of at least 1 and maps onto `ScanConfig.minSize`; the other two `ScanConfig` fields keep their defaults. No folder, an unknown option, or an invalid `--min-size` is a usage error: a message and a usage line on stderr, nothing on stdout, and no scan started. Checking the folders themselves is the scanner's job (`StartScan` failures, decision 6).
6. **Exit codes: `0` completed (with or without duplicates), `2` usage error, `1` refused start or failed scan, `130` cancelled.** A refused start (`StartScan` returns `Failure`) writes `error: <ErrorCode>: <message>` to stderr and nothing to stdout. Whether duplicates were found is read from the `summary` line, not the exit code: a non-zero code for "duplicates found" would make every real `./gradlew :cli:run` end in BUILD FAILED. `130` is the JVM's own exit status on Ctrl+C (decision 7), not something the CLI sets or a test checks.
7. **Waiting: poll `GetScanStatus`; Ctrl+C is not handled.** `ScanListener` has no completion event, so the runner polls `GetScanStatus` (about every 100 ms) until the status is `COMPLETED`, `FAILED` or `CANCELLED`; adding a completion event would change `file-scanner`, which is out of scope. Only then is anything written to stdout: `GetScanResult` answers only for a COMPLETED scan, so a failed scan writes nothing to stdout (no sets, no summary) and `error: scan failed: <failureMessage>` to stderr, and exits 1. Ctrl+C is left to the JVM, which ends the process (status 130) and the scan's threads with it: a scan is read-only, so an abrupt stop leaves nothing to clean up. `CancelScan` stays unused by the CLI; graceful cancellation belongs where interruption has consequences (the delete workflow, the server).
8. **Testing at two levels.** The argument parsing, waiting and output live in a plain class outside Spring, e.g. `ScanCommand(StartScan, GetScanStatus, GetScanResult)` with `int run(String[] args, PrintStream out, PrintStream err)` returning the exit code; the Spring `ApplicationRunner` and `Main` are thin wrappers that pass `System.out`/`System.err` and turn the int into the process exit code. Most scenarios test that class directly: real scanning through `ScannerFactories.scanUseCases(<virtual-thread executor>)` on `@TempDir` folders, and hand-written fake ports (no Mockito) for the failed-scan, wait and "no scan started" scenarios. AC-1, AC-6 and AC-7 start the real `SpringApplication`, with Spring Boot's `OutputCaptureExtension` capturing stdout and stderr separately. Real process exit codes are checked by the `:cli:run` done-item.
9. **CLAUDE.md is updated.** "Current state" replaces "Spring Boot is not yet added to `cli`, and `Main` is a stub" with what is then true (`cli` is a Spring Boot app that scans the folders given and writes JSONL to stdout; schema, exit codes and stream rules as in DFS-020), and "Commands" shows the run as `./gradlew -q :cli:run --args="C:/abs/path" > out.jsonl`, noting that `-q` keeps Gradle's own lines out of stdout. The CLI framework stays listed as open.

## Acceptance criteria

<!-- Filled during refinement. State the full ID once, then one testable statement per line, numbered from 1. Never renumber or delete: retire with `- **AC-<n> (retired)** <statement>. Retired <date>: <reason>`. -->

The full ID of each criterion is `DFS-020.AC-<n>`.

- **AC-1** `cli` starts as a Spring Boot application whose `@Configuration` provides `StartScan`, `CancelScan`, `GetScanStatus` and `GetScanResult` as beans, all four from one `ScannerFactories.scanUseCases` call with a virtual-thread executor (decision 2; CLAUDE.md decision 6).
- **AC-2** A completed scan writes one `set` line per duplicate set, `{"type":"set","size","digest","copies"}`, where each copy is the list of its links' absolute paths; every line written is a single valid JSON object (decision 3).
- **AC-3** Hard links to one file are written as links of one copy in their set's `copies`, never as separate copies (decision 3; CLAUDE.md "hard links are not duplicates").
- **AC-4** Each skipped path is written as a `skipped` line, `{"type":"skipped","path","reason","detail"}`, after every `set` line (decision 3).
- **AC-5** The last line is one `summary` line with `sets`, `files`, `skipped` and `reclaimableBytes` (the sum of `size * (copies - 1)` over the sets), written also when nothing was found (decision 3).
- **AC-6** Stdout holds only the JSONL lines: no Spring Boot banner, no log line and no progress text is written to it (decision 4).
- **AC-7** Progress goes to stderr, on by default: at least one line naming each scan phase as it starts (walk, partial hash, full hash), with walk and group counts at most about once a second (decision 4).
- **AC-8** Every plain argument is a folder, and all of them are scanned together in one scan, so duplicates across folders form one set (decision 5).
- **AC-9** `--min-size=<n>` excludes files smaller than `n` bytes from the result; without it the scanner's default minimum (1 byte) applies (decision 5).
- **AC-10** No folder argument, an unknown option, or a `--min-size` that is not a whole number of at least 1 writes an error and a usage line to stderr, writes nothing to stdout, and starts no scan (decision 5).
- **AC-11** A completed scan exits with code 0, whether or not it found duplicates (decision 6).
- **AC-12** A usage error exits with code 2 (decision 6).
- **AC-13** A start that `StartScan` refuses writes `error: <ErrorCode>: <message>` to stderr, nothing to stdout, and exits with code 1 (decision 6).
- **AC-14** The CLI waits until the scan is COMPLETED, FAILED or CANCELLED before writing to stdout; a scan that ends FAILED writes `error: scan failed: <failureMessage>` to stderr, nothing to stdout, and exits with code 1 (decision 7).

## Scenarios

```gherkin
Feature: Scanning from the CLI

  @AC-1
  Scenario: The Spring context provides the four scan ports
    When the cli Spring application context starts without running a scan
    Then it holds exactly one bean each of type StartScan, CancelScan, GetScanStatus and GetScanResult
    And all four share one ScanUseCases, built by ScannerFactories.scanUseCases: a scan started through the StartScan bean is known to the GetScanStatus, GetScanResult and CancelScan beans

  @AC-2 @AC-5 @AC-8 @AC-11
  Scenario: Duplicates across two folders become one set line and a summary
    Given a temporary folder "a" holding "x.txt" with content "hello duplicate"
    And a temporary folder "b" holding "y.txt" with content "hello duplicate" and "z.txt" with content "unique"
    When the CLI scans "a" and "b"
    Then the output has exactly 2 lines, and each parses as one JSON object
    And line 1 is a "set" line with size 15, the SHA-256 of "hello duplicate" as digest, and copies [[<real path of a/x.txt>], [<real path of b/y.txt>]]
    And line 2 is {"type":"summary","sets":1,"files":2,"skipped":0,"reclaimableBytes":15}
    And the exit code is 0

  @AC-3
  Scenario: Hard links are one copy, not duplicates
    Given a temporary folder holding "x.txt" with content "hello duplicate", a hard link "x-link.txt" to it, and "y.txt" with the same content
    And the test is skipped when the file system cannot create a hard link
    When the CLI scans that folder
    Then the "set" line's copies are [[<x-link.txt>, <x.txt>], [<y.txt>]], each copy's links in ascending path order
    And the summary has "files":3 and "reclaimableBytes":15

  @AC-4
  Scenario: A symbolic link is reported as skipped, after the sets
    Given a temporary folder holding "x.txt" and "y.txt", both with content "hello duplicate", and a symbolic link "link.txt" to "x.txt"
    And the test is skipped when the platform or privileges do not allow creating a symbolic link
    When the CLI scans that folder
    Then the lines are, in order: one "set" line, one {"type":"skipped","path":<path of link.txt>,"reason":"SYMLINK",...} line with a "detail" string, and the summary with "skipped":1
    And no copy in the "set" line names "link.txt"

  @AC-4
  Scenario: A Windows junction is skipped, so its target's files are not counted twice
    Given a temporary folder holding "data/x.txt" and "data/y.txt", both with content "hello duplicate", and a junction "j" to "data" (created with mklink /J)
    And the test is skipped when not on Windows
    When the CLI scans that folder
    Then there is one "set" line whose copies are [[<data/x.txt>], [<data/y.txt>]], and no copy names a path under "j"
    And there is one {"type":"skipped","path":<path of j>,"reason":"SPECIAL_FILE",...} line

  @AC-5 @AC-11
  Scenario: No duplicates still ends with a summary
    Given a temporary folder holding "a.txt" with content "one", "b.txt" with content "two", and an empty file "e1.txt" and an empty file "e2.txt"
    When the CLI scans that folder
    Then the output is exactly one line: {"type":"summary","sets":0,"files":0,"skipped":0,"reclaimableBytes":0}
    And the two empty files are not reported as duplicates (default minimum size)
    And the exit code is 0

  @AC-6 @AC-7
  Scenario: Stdout is pure JSONL and progress goes to stderr
    Given a temporary folder holding "x.txt" and "y.txt", both with content "hello duplicate"
    When the cli application is run end to end (Spring context included) on that folder, with stdout and stderr captured separately
    Then every stdout line parses as one JSON object with a "type" field, and stdout has no other text (no banner, no log lines)
    And stderr has a progress line naming the walk phase and one naming the full-hash phase

  @AC-9
  Scenario: --min-size leaves small duplicates out
    Given a temporary folder holding "s1.txt" and "s2.txt", both with content "abc", and "b1.txt" and "b2.txt", both with content "hello duplicate"
    When the CLI scans that folder with "--min-size=10"
    Then the output has one "set" line, with size 15, and the summary has "sets":1 and "files":2

  @AC-10 @AC-12
  Scenario Outline: A usage error starts no scan
    When the CLI is run with arguments <args>
    Then stderr has an error naming the problem, and a usage line
    And stdout is empty
    And no scan was started
    And the exit code is 2

    Examples:
      | args                            |
      | (none)                          |
      | "--min-size=10"                 |
      | "<folder> --recursive"          |
      | "<folder> --min-size=abc"       |
      | "<folder> --min-size=0"         |
      | "<folder> --min-size=-5"        |

  @AC-13
  Scenario Outline: A refused start exits 1 with the scanner's error code
    Given a temporary folder "a" and a regular file "f.txt"
    When the CLI is run with arguments <args>
    Then stderr has a line starting "error: <code>: "
    And stdout is empty
    And the exit code is 1

    Examples:
      | args                    | code               |
      | "<a>/missing"           | LOCATION_NOT_FOUND |
      | "<f.txt>"               | NOT_A_DIRECTORY    |
      | "<a> <a>/missing"       | LOCATION_NOT_FOUND |

  @AC-14
  Scenario: A failed scan writes no JSONL and exits 1
    Given scan ports whose StartScan succeeds and whose GetScanStatus reports RUNNING twice, then FAILED with failureMessage "java.io.UncheckedIOException: disk gone"
    When the CLI is run on a temporary folder with those ports
    Then stderr has the line "error: scan failed: java.io.UncheckedIOException: disk gone"
    And stdout is empty
    And GetScanResult was never called
    And the exit code is 1

  @AC-14 @AC-2
  Scenario: Output is written only once the scan has completed
    Given scan ports whose GetScanStatus reports QUEUED, then RUNNING, then COMPLETED, and whose GetScanResult holds one duplicate set
    When the CLI is run on a temporary folder with those ports
    Then GetScanStatus was polled until it reported COMPLETED, and GetScanResult was called once, after that
    And stdout has the "set" line and then the "summary" line
```

## Scope

**Touches:**
- `CLAUDE.md`: "Current state" and "Commands" (decision 9)
- `cli/build.gradle.kts`: Spring Boot (and JSON) dependencies
- `cli/src/main/resources`: Spring Boot configuration (banner off, console logging to stderr)
- `us.pochaev.dfs.cli`: `Main`, the Spring configuration and the runner that scans and writes JSONL

**Out of scope:**
- Deleting, quarantine, preview (later tickets)
- An interactive shell
- Graceful Ctrl+C cancellation through `CancelScan` (decision 7)
- A scan-completion event in `ScanListener`
- The `scanner-spring` autoconfigure module (CLAUDE.md decision 6, later)
- Changes to `file-scanner`

## Definition of done

- [x] Every scenario above is an automated test and passes: `ScanCommandTest` (17 runs incl. the two outlines), `ScannerConfigurationTest`, `CliApplicationTest`; 0 skipped on this Windows machine (symlink, hard link and junction all created). AC-6's test was mutation-checked: with `banner-mode=console` it fails
- [x] `./gradlew build` is green (includes the ArchUnit and module-export tests): 196 tests, 0 failures, 1 skipped (file-scanner's case-sensitivity test, expected on Windows)
- [x] No files changed outside the paths listed under Touches: `git status` shows only CLAUDE.md, `cli/build.gradle.kts`, `cli/src/main/resources/`, `cli/src/{main,test}/java/us/pochaev/dfs/cli/` and this ticket
- [x] CLAUDE.md's "Current state" no longer calls `Main` a stub or says Spring Boot is missing, and "Commands" shows the `-q` run with redirection (decision 9): both edited
- [x] `./gradlew -q :cli:run --args="<absolute dir>" > out.jsonl` on a real directory gives a file in which every line parses as JSON and the last is the `summary` line, and the exit status (`$?`) is 0: run 2026-10-08 on `C:/Users/apoch/Projects/us.pochaev`, 4 min 31 s, exit 0, 3335 lines all parsing as JSON (3330 `set`, 4 `skipped`, last `{"type":"summary","sets":3330,"files":14997,"skipped":4,"reclaimableBytes":40950250}`)

## Open questions

None.

