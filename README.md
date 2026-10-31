# duplicate-file-scanner

Finds duplicate files under one or more folders and reports them, so you can decide what to delete safely.

Today the tool **only reports**: it never changes or deletes anything. Organizing duplicates by folder ("every file in `folder2` that has a copy in `folder1`") and a reversible, quarantine-based delete are planned; see [Roadmap](#roadmap).

## How it finds duplicates

1. **Walk** the folders, collecting regular files. Symlinks and junctions are not followed, empty files are left out, and so are `.DS_Store` and `._*` files unless you pass `--no-default-excludes`.
2. **Group by size.** A file whose size no other file has cannot have a copy, so it is never read.
3. **Sample** files over 128 KB: hash the first and last 64 KB, and drop files whose samples differ.
4. **Hash** the remaining files in full with SHA-256. Only files with identical hashes are reported.
5. **Hard links** (several paths to the same physical file) are recognized and not counted as copies of each other.

A sample can only rule files out. Every reported duplicate is confirmed by a full-content hash.

## Requirements

- JDK 25. The Gradle wrapper downloads Gradle itself.
- Windows, Linux or macOS. On Windows the scanner calls native code to identify hard links, so the JVM needs `--enable-native-access=ALL-UNNAMED`. The launchers below already pass it.

## Usage

### Windows: `dfs.bat`

`dfs.bat` builds the CLI, then runs it with the arguments you give it:

```bat
dfs.bat D:\Photos E:\Backup --format=text --output=C:\reports\photos.txt
```

Progress is redrawn live on the console. With `--output` the report is written to that file as UTF-8; without it the report is printed to the console.

### Any platform

```sh
./gradlew :cli:bootJar
java --enable-native-access=ALL-UNNAMED -jar cli/build/libs/cli-1.0-SNAPSHOT.jar /data/photos /backup --format=text
```

### Options

| Option | Meaning |
|---|---|
| `<folder>...` | One or more folders to scan. Overlapping folders are fine: a file is counted once. |
| `--format=jsonl\|text` | `jsonl` (the default) is JSON Lines for other programs; `text` is a readable report. |
| `--output=<file>` | Write the report to a file (UTF-8) instead of stdout. An existing file is replaced only by a complete report. |
| `--progress=live\|log\|off` | Progress on stderr: redrawn in place (default), plain lines for a log file, or none. |
| `--min-size=<bytes>` | Ignore files smaller than this (default 1, which leaves out empty files). |
| `--no-default-excludes` | Also scan `.DS_Store` and `._*` files. |

Exit codes: `0` the scan completed, `1` the scan could not start or failed, `2` usage error.

### Text report

A two-line header says what was scanned, when and with which settings. Duplicate sets are then listed by reclaimable space, largest first:

```
Scan of D:\Photos, E:\Backup
  2026-10-31 09:12:03Z to 11:47:55Z, minimum size 1 B, default excludes on
#1  27 B reclaimable  (2 copies x 27 B, sha256 c2b8e254e569)
    D:\Photos\folder1\photo.jpg
    E:\Backup\folder2\photo-copy.jpg
#2  18 B reclaimable  (2 copies x 18 B, sha256 3a53eb2e7e61)
    D:\Photos\basic\a.txt
    D:\Photos\basic\b.txt
Summary: 2 sets, 4 files, 45 B reclaimable, 0 skipped, 0 excluded
```

"Reclaimable" is the space freed by keeping one copy of each set.

### JSONL report

One JSON object per line, in this order: a `scan` header (`schemaVersion`, the scanned roots, the settings, start and finish times), a `folder` line per folder with its direct file count and bytes, a `set` line per duplicate set (each copy path an object with `path` and `modified`), a `skipped` line per skipped path, and a `summary` line. This holds everything an analysis tool needs to work out which folders overlap; `tools/analysis/overlaps.sql` does that in DuckDB. `schemaVersion` is currently 2 and goes up only for breaking changes, so readers should ignore fields and line types they do not know.

## Building and testing

```sh
./gradlew build                      # compile and run every test
./gradlew :file-scanner:test         # the core library's tests only
```

Use `gradlew.bat` instead of `./gradlew` in cmd or PowerShell.

## Project layout

- `file-scanner/`: the core library. Spring-free, a Java module, hexagonal architecture; layering is enforced by ArchUnit tests.
- `scanner-spring/`: Spring Boot autoconfiguration that wires the library's ports as beans, shared by the apps.
- `cli/`: a Spring Boot command-line app that wires the library together and prints the report.
- `server/`: the local web server for the planned UI (`docs/design/ui.md`); for now a skeleton you start with `./gradlew :server:bootRun`.
- `docs/tickets/`: the backlog, written as user stories with Gherkin acceptance criteria.
- `.claude/` and `tools/story-gate/`: the Claude Code workflow this project is built with (agents, skills, ticket archetypes and the pipeline's gate checks).

[`CLAUDE.md`](CLAUDE.md) records the architecture, the design decisions and the safety rules in detail.

## Roadmap

- **Folder overlaps:** show which folders hold copies of each other, fully or partly.
- **Deletion plans:** preview which files a rule such as "delete everything in `folder2` that has a copy in `folder1`" would remove, while never removing the last copy of a file.
- **Quarantine delete:** move files to a quarantine folder with a manifest instead of deleting them, so a whole run can be undone. Before moving, each file is compared byte for byte against the copy being kept.

## License

[Apache License 2.0](LICENSE)
