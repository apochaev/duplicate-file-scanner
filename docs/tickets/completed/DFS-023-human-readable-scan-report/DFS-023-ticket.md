---
id: DFS-023
title: Print a human-readable scan report
status: done
type: feature
scope: [cli, docs]
archetype: none
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
priority: high
depends_on: [DFS-020]
touches: [CLAUDE.md, us.pochaev.dfs.cli]
blocked_reason:
created: 2026-10-13
updated: 2026-10-13
completed: 2026-10-13
---

# DFS-023: Print a human-readable scan report

## Story

As a user of the duplicate file scanner
I want to give the CLI several folders and get a readable report of every duplicate found anywhere beneath them
So that I can see at a glance what is duplicated, where every copy lives and how much space I could free, without processing JSON

## Context

The CLI (DFS-020) already scans any number of folders in one run, and finds duplicates within and across them: a file under folder A with a copy under folder B is one `DuplicateSet`. It writes JSON Lines only: `set` lines (`size`, `digest`, `copies`, one inner list per `PhysicalCopy`, holding its links' paths), then `skipped` lines, then a `summary` line (`sets`, `files`, `skipped`, `reclaimableBytes` = the sum of `size * (copies - 1)`). Arguments are parsed by hand in `ScanCommand` (folders, `--min-size=<bytes>`); stdout carries only the output, progress and errors go to stderr; exit codes 0, 1, 2 (DFS-020 decisions 3 to 6).

The user wants this report as soon as possible (2026-10-13), ahead of the delete workflow (DFS-021, DFS-022).

**Decisions (2026-10-13):**

1. **A `--format=text` option on the existing scan command; JSONL stays the default.** Chosen over a separate `report` command that reads saved JSONL (that needs a JSONL reader, which DFS-021 may build later) and over a one-off script. Changing format means re-running the scan.
2. **A file present in N places is one entry listing all N copies**, with `size * (N - 1)` reclaimable; hard links of one copy are shown under that copy, not as extra copies (CLAUDE.md "hard links are not duplicates"). This is what `ScanResult` already models; the report only renders it.
3. **Archetype `none`: built by hand**, like DFS-020. The ticket only formats output inside the existing command and changes no wiring, so `wiring`'s checklist would mostly not apply (DFS-021 stays its first live run). Tests carry `DFS-023.AC-n` in their `@DisplayName`, and the implementing session ticks each done-item with its evidence.
4. **Order: sets by reclaimable bytes (`size * (copies - 1)`), largest first; ties by larger size, then by the first copy's path. Copies within a set in path order** (links within a copy too). The space worth freeing comes first, and a file copied many times rises by its total, not its size alone; path order keeps each entry stable across runs. Grouping by folder is left to a later `LocationOverlap` view.
5. **Sizes in 1024-based units with one decimal (`B` as a whole number, then `KB`, `MB`, `GB`, `TB`, matching Windows Explorer); counts with thousands separators; the digest shortened to its first 12 hex characters.** Heading: `#1  4.2 GB reclaimable  (4 copies x 1.4 GB, sha256 9f2c1ab07e33)`. Formatting uses `Locale.ROOT`, so the output does not depend on the system language and tests match it exactly. Only ASCII is added by the renderer (`x`, not `×`); paths are printed as they are. The full digest stays in the JSONL format.
6. **Layout: sets, then skipped, then one summary line last** (the order of the JSONL format). The `Skipped (N):` section lists each skipped path with its reason and detail, in `ScanResult.skipped` order, and is left out when nothing was skipped. The last line is always `Summary: <sets> sets, <files> files, <reclaimable> reclaimable, <skipped> skipped` (counts as in the JSONL `summary`). With no duplicate sets the report is `No duplicates found.` followed by the skipped section, if any, and the summary.
7. **Stdout is always UTF-8, in both formats.** The JVM here defaults to `stdout.encoding = Cp1252` (checked 2026-10-13, `java -XshowSettings:properties`), and `ScanRunner` passes `System.out` as is, so a path outside Cp1252 (`C:/фото/a.jpg`) is written as `?`: a redirected JSONL file is then not valid UTF-8 JSON for such paths, and no later step could find the file. `ScanRunner` wraps stdout in a UTF-8 `PrintStream` instead; `ScanCommand` is unchanged in this respect. Shown directly on a Windows console, non-ASCII paths then need the console in UTF-8 mode (`chcp 65001`, or `[Console]::OutputEncoding = [Text.Encoding]::UTF8` in PowerShell); CLAUDE.md "Commands" gains that tip and the `--format=text` run, and "Current state" mentions the text format and UTF-8 output. Whether Gradle's `:cli:run` relays the bytes unchanged is not verified; the real-run done-item checks it.
8. **`--format=text|jsonl`, default `jsonl`.** Any other value, an empty value, a wrongly cased value (`TEXT`), or `--format` given twice is a usage error like a bad `--min-size` (DFS-020 decision 5): `error: <problem>` and the usage line on stderr, nothing on stdout, no scan started, exit 2. The usage line becomes `usage: duplicate-file-scanner <folder>... [--min-size=<bytes>] [--format=jsonl|text]`.
9. **Text layout details.** A copy is one line: 4 spaces, then its first link's path; each further hard link of that copy follows on its own line: 6 spaces, `(hard link) `, the path. A skipped path is 4 spaces, the path, 2 spaces, the reason, 2 spaces, the detail. Words are not pluralised by count (`1 sets` is accepted for simplicity). Sizes: below 1024 bytes a whole number and ` B`; otherwise the largest of `KB`, `MB`, `GB`, `TB` (powers of 1024) that the value reaches, rounded half-up to one decimal (`1536` is `1.5 KB`, `1048576` is `1.0 MB`). Counts use `,` thousands separators.
10. **Testing at the same two levels as DFS-020** (its decision 8): rendering and ordering with `ScanResult` values built in the test; `ScanCommand` with real scanning on `@TempDir` folders and the existing fake ports; UTF-8 output on `ScanRunner` with `System.out` replaced by a byte-capturing stream, so bytes, not a decoded string, are checked. Hand-written fakes, no Mockito. The real-run done-items cover what tests cannot: a real console, and Gradle relaying the bytes.

Sketch of the output (exact format in decisions 4 to 9 and the scenarios):

```
#1  4.2 GB reclaimable  (4 copies x 1.4 GB, sha256 9f2c...)
    C:/path/one/videos/a.mp4
    C:/path/two/backup/a.mp4
    D:/path/three/old/a (1).mp4
    D:/path/three/old/a (2).mp4
...
Skipped (4):
    C:/path/one/link  SYMLINK
Summary: 3,330 sets, 9,812 files, 18.6 GB reclaimable, 4 skipped
```

## Acceptance criteria

<!-- Filled during refinement. State the full ID once, then one testable statement per line, numbered from 1. Never renumber or delete: retire with `- **AC-<n> (retired)** <statement>. Retired <date>: <reason>`. -->

The full ID of each criterion is `DFS-023.AC-<n>`.

- **AC-1** `--format=text` writes the text report to stdout instead of JSONL; with `--format=jsonl` or no `--format`, stdout is the DFS-020 JSONL as before; in both, progress stays on stderr (decisions 1, 8).
- **AC-2** Each duplicate set is a heading `#<n>  <reclaimable> reclaimable  (<copies> copies x <size>, sha256 <first 12 hex of digest>)` followed by one line per physical copy; a file present in N places is one set of N copies with `size * (N - 1)` reclaimable (decisions 2, 5, 9).
- **AC-3** Hard links of one copy appear under that copy as `(hard link)` lines and are not counted as copies, neither in the heading nor in reclaimable bytes (decision 2; CLAUDE.md "hard links are not duplicates").
- **AC-4** Sets are numbered from 1 in order of reclaimable bytes, largest first, ties by larger size, then by first copy path; copies, and the links within a copy, are in path order (`Path.compareTo`) (decision 4).
- **AC-5** Sizes are formatted in 1024-based units with one decimal (`B` as a whole number), counts with `,` separators, the same whatever the default locale (decisions 5, 9).
- **AC-6** Skipped paths follow the sets under `Skipped (<n>):`, one line each with path, reason and detail, in scan order; the section is absent when nothing was skipped (decision 6).
- **AC-7** The last line is `Summary: <sets> sets, <files> files, <reclaimable> reclaimable, <skipped> skipped`; with no sets, the report starts with `No duplicates found.` (decision 6).
- **AC-8** Stdout is UTF-8 in both formats: a path containing characters outside the platform's default code page is written as its UTF-8 bytes, never as `?` (decision 7).
- **AC-9** An unknown, empty, wrongly cased or repeated `--format` is a usage error: message and usage line on stderr, nothing on stdout, no scan started, exit 2 (decision 8).

## Scenarios

```gherkin
Feature: Human-readable scan report

  @AC-1 @AC-2 @AC-7
  Scenario: A file in three places across two folders is one set of three copies
    Given a temporary folder "a" holding "x.txt" with content "hello duplicate" and "u.txt" with content "unique"
    And a temporary folder "b" holding "y.txt" and "sub/z.txt", both with content "hello duplicate"
    When the CLI scans "a" and "b" with "--format=text"
    Then stdout is exactly these 5 lines, <...> being the real absolute paths:
      | #1  30 B reclaimable  (3 copies x 15 B, sha256 <first 12 hex of SHA-256 of "hello duplicate">) |
      |     <a/x.txt>                                                                                  |
      |     <b/sub/z.txt>                                                                              |
      |     <b/y.txt>                                                                                  |
      | Summary: 1 sets, 3 files, 30 B reclaimable, 0 skipped                                          |
    And stderr has the scan's progress lines
    And the exit code is 0

  @AC-1
  Scenario Outline: JSONL stays the default and is unchanged
    Given a temporary folder holding "x.txt" and "y.txt", both with content "hello duplicate"
    When the CLI scans that folder with <options>
    Then stdout is exactly 2 lines: one "set" line and {"type":"summary","sets":1,"files":2,"skipped":0,"reclaimableBytes":15}
    And the exit code is 0

    Examples:
      | options                 |
      | no option               |
      | "--format=jsonl"        |

  @AC-3
  Scenario: Hard links are shown under their copy, not as copies
    Given a temporary folder holding "x.txt" with content "hello duplicate", a hard link "x-link.txt" to it, and "y.txt" with the same content
    And the test is skipped when the file system cannot create a hard link
    When the CLI scans that folder with "--format=text"
    Then the heading is "#1  15 B reclaimable  (2 copies x 15 B, sha256 <first 12 hex>)"
    And the lines under it are "    <x-link.txt>", "      (hard link) <x.txt>" and "    <y.txt>", in that order
    And the last line is "Summary: 1 sets, 3 files, 15 B reclaimable, 0 skipped"

  @AC-4
  Scenario: Sets are ordered by reclaimable bytes, then size, then first path
    Given a scan result built in the test, with these sets:
      | set | size | copy paths, in this order |
      | A   | 1000 | /m/a2, /m/a1              |
      | B   | 100  | /z/b1, /z/b2, /z/b3, /z/b4 |
      | C   | 300  | /n/c2, /n/c1, /n/c4, /n/c3 |
      | D   | 900  | /p/d1, /p/d2              |
      | E   | 900  | /k/e1, /k/e2              |
    When it is rendered as text
    Then the headings are, in order: #1 A (1000 B reclaimable), #2 E (900 B, size 900), #3 D (900 B, size 900), #4 C (900 B, size 300), #5 B (300 B)
    And set A's copies are listed /m/a1 then /m/a2, and set C's /n/c1, /n/c2, /n/c3, /n/c4

  @AC-5
  Scenario Outline: Sizes and counts are formatted the same in every locale
    Given the default locale is set to <locale> for the test
    When a size of <bytes> bytes is formatted
    Then it reads "<text>"
    And a count of 3330 reads "3,330"

    Examples:
      | locale  | bytes         | text      |
      | ROOT    | 1             | 1 B       |
      | ROOT    | 1023          | 1023 B    |
      | ROOT    | 1024          | 1.0 KB    |
      | GERMANY | 1536          | 1.5 KB    |
      | ROOT    | 1048576       | 1.0 MB    |
      | FRANCE  | 4509715660    | 4.2 GB    |
      | ROOT    | 1099511627776 | 1.0 TB    |

  @AC-6 @AC-7
  Scenario: Skipped paths are listed after the sets, before the summary
    Given a temporary folder holding "x.txt" and "y.txt", both with content "hello duplicate", and a symbolic link "link.txt" to "x.txt"
    And the test is skipped when the platform or privileges do not allow creating a symbolic link
    When the CLI scans that folder with "--format=text"
    Then after the set's lines come "Skipped (1):" and "    <link.txt>  SYMLINK  <detail>"
    And the last line is "Summary: 1 sets, 2 files, 15 B reclaimable, 1 skipped"

  @AC-6 @AC-7
  Scenario: No duplicates and nothing skipped
    Given a temporary folder holding "a.txt" with content "one" and "b.txt" with content "two"
    When the CLI scans that folder with "--format=text"
    Then stdout is exactly "No duplicates found." and "Summary: 0 sets, 0 files, 0 B reclaimable, 0 skipped"
    And there is no "Skipped" line
    And the exit code is 0

  @AC-8
  Scenario Outline: Non-ASCII paths are written as UTF-8
    Given a temporary folder "фото" holding "x.txt" and "y.txt", both with content "hello duplicate"
    And System.out is replaced by a stream that captures raw bytes
    When ScanRunner runs on that folder with <options>
    Then the captured bytes decode as UTF-8 to output naming the path segment "фото"
    And the output contains no "?" where the folder name stands

    Examples:
      | options          |
      | no option        |
      | "--format=text"  |

  @AC-9
  Scenario Outline: A bad --format starts no scan
    When the CLI is run with arguments <args>
    Then stderr has an error naming the problem, and the usage line, which mentions "--format=jsonl|text"
    And stdout is empty
    And no scan was started
    And the exit code is 2

    Examples:
      | args                                     |
      | "<folder> --format=txt"                  |
      | "<folder> --format="                     |
      | "<folder> --format=TEXT"                 |
      | "<folder> --format=text --format=jsonl"  |
```

## Scope

**Touches:**
- `us.pochaev.dfs.cli`: the `--format` option in `ScanCommand`, the text renderer, and UTF-8 stdout in `ScanRunner`
- `CLAUDE.md`: "Commands" (the text run, the UTF-8 console tip) and "Current state" (decision 7)

**Out of scope:**
- Any change to `file-scanner` (the report renders `ScanResult` as it is)
- Reading a saved JSONL scan back in (a separate `report` command)
- Deletion, preview of a deletion plan (DFS-021, DFS-022)
- HTML or other formats

## Definition of done

- [x] Every scenario above is an automated test and passes. 9 test methods for 9 scenarios (`grep -c 'DisplayName("DFS-023'`: 6 in `ScanCommandTest`, 2 in `TextReportTest`, 1 in `ScanRunnerTest`); all 40 `cli` test runs pass, none skipped (hard and symbolic links were available). `ScanRunnerTest` (AC-8) fails 2 of 2 against the pre-change `ScanRunner` and passes with the fix; it replaces `System.out` with an ISO-8859-1 stream, since Gradle's test JVM gives `System.out` UTF-8 and would hide the bug.
- [x] `./gradlew build` is green (includes the ArchUnit and module-export tests), 2026-10-13
- [x] No files changed outside the paths listed under Touches: `CLAUDE.md`, `ScanCommand`, `ScanRunner`, new `TextReport`, and tests in `cli` (plus this ticket file)
- [x] A real `./gradlew -q :cli:run --args="... --format=text"` on two or more real folders prints a readable report: `velvetapp` and `velvet-vortex` under `C:/Users/apoch/Projects/us.pochaev`, exit 0 in 1 min 59 s, 16,290 lines ending `Summary: 2,680 sets, 13,609 files, 36.5 MB reclaimable, 0 skipped`; #1 is `4.4 MB reclaimable  (18 copies x 266.5 KB, ...)`, 18 copies of one `source-map.debug.js`
- [x] A real `:cli:run` redirected to a file, on a folder whose name is Cyrillic, writes that path as valid UTF-8 in both formats (decision 7): a scratch folder `utf/фото` with two equal files, both runs exit 0, both files decode as strict UTF-8, contain `фото` and no `?`. Gradle relays the bytes unchanged.
- [x] CLAUDE.md "Commands" (text run, UTF-8 console tip) and "Current state" (text format, UTF-8) updated as in decision 7

## Open questions

None.
