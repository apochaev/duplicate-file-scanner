# CLAUDE.md

## What this project is

Finds duplicate files anywhere on disk from a start location and organizes them by **source location**, so the user can delete duplicates safely and confidently, individually or in bulk. Example: folder1 and folder2 overlap (fully or partially); the user can choose "delete every file in folder2 that has a confirmed duplicate in folder1".

Two Gradle subprojects (Java 25, Gradle 9.6 wrapper, group `us.pochaev.dfs`):

- `file-scanner`: the core library, hexagonal architecture, a Java module, Spring-free.
- `cli`: Spring Boot CLI (a Spring Boot server may follow). Depends on `file-scanner` via `implementation(project(":file-scanner"))`. It is a primary adapter and the composition root.

## Commands

Run from the repo root (`./gradlew` works in Git Bash; use `gradlew.bat` in cmd/PowerShell).

```
./gradlew build                                   # compile + test everything
./gradlew :file-scanner:test                      # one project's tests
./gradlew :file-scanner:test --tests "*SomeTest"  # one test class
./gradlew :cli:run --args="C:/abs/path"           # run the CLI
```

`:cli:run` starts in `cli/`, so pass **absolute** paths. Gradle 9.6 on JDK 25 prints a harmless "restricted method in java.lang.System" warning at startup.

Shared config (Java 25 toolchain, `--release 25`, JUnit 6 BOM, `useJUnitPlatform`) lives in the root `build.gradle.kts` `subprojects {}` block; do not repeat it in subprojects.

## `file-scanner` architecture

Base package `us.pochaev.dfs.scanner`, module name the same.

```
domain.model              data structures (records, sealed interfaces); depends on nothing
domain.usecases.query     primary query ports (CQS)
domain.usecases.command   primary command ports (CQS)
domain.factory            static factories; the ONLY domain package allowed to touch application/infra
application               business logic + domain services; implements the use cases
infra.port                secondary ports
infra.adapter             secondary adapters (NIO filesystem, hasher, quarantine deleter, ...)
```

Any layer, and the top level, may have a `util` package for shared helpers. A `util` package may only depend downward or on the JDK, never on the layer above it.

**Dependency rules** (enforce with ArchUnit tests; `module-info` cannot express these):

- `domain.model`: no dependencies.
- `domain.usecases.*`: `domain.model` only.
- `application`: `domain.model`, `domain.usecases`, `infra.port`.
- `infra.adapter`: `infra.port`, `domain.model`.
- `domain.factory`: may also depend on `application` and `infra.adapter`. This is the one deliberate exception; nothing else in `domain` may.

**Module exports.** `module-info.java` exports each `domain.*` package individually (JPMS has no recursive export). `application` and `infra.*` are never exported.

**Important caveat.** JPMS encapsulation is not enforced when the consumer runs on the classpath, and Spring Boot (fat jar, `bootRun`, tests) always does. `cli` and any future server could compile against `application`/`infra` classes and nothing in the toolchain would stop it. So:

- Add an ArchUnit rule in every consuming app forbidding dependencies on `..scanner.application..` and `..scanner.infra..`.
- Keep `module-info` anyway: it documents intent and supports modular consumers/jlink.

## Design decisions (settled)

1. **Location model.** Overlaps are computed per immediate-directory pair and rolled up, so the user can also pick any ancestor folder (recursive) as keep or delete source. Two views of one result: `DuplicateSet` (identical content, N paths) and `LocationOverlap(A, B)` (which files in B have a copy in A; all vs partial).
2. **Deletion.** Files are moved to an app-managed **quarantine folder** with a manifest (original path, keeper it was verified against) that doubles as the audit log; a whole run can be undone, and a purge command frees space. A permanent-delete adapter exists behind the same `FileDeleter` port, selected by an explicit flag. Do not use `java.awt.Desktop.moveToTrash`.
3. **Detection pipeline.** Group by size, then a partial hash (head and tail, about 64 KB each), then full SHA-256 for files still matching. Drop groups of one after each stage. Skip the partial stage for small files (under about 128 KB), and hash in path order within a group (spinning-disk locality). A persistent hash cache (path + size + mtime) is deferred; it would sit behind a port.
4. **Delete-time proof.** Immediately before quarantining a file, re-verify with a **byte-for-byte compare against the keeper** (`ContentComparator` port). No skip flag for now. This also catches files that changed after the scan.
5. **Factories.** Plain static factories in `domain.factory` that instantiate application/adapter classes directly (compile-time checked, works on classpath and module path). No `ServiceLoader`. Callers depend only on the factories' static API, so the internals could later move to ServiceLoader unnoticed.
6. **Spring wiring.** The scanner has no Spring dependency. `cli` owns the `@Configuration` with `@Bean` methods that call the factories. When a server appears, extract the shared wiring into a `scanner-spring` autoconfigure module (`@ConfigurationProperties`, `@ConditionalOnMissingBean` for port overrides).

## Safety invariants (must hold, and be tested)

- **Never delete the last copy.** Every `DeletionPlan` must leave at least one verified surviving copy per `DuplicateSet`. Rules can conflict (folder1's copy may itself be marked for deletion by another rule), so validate the plan as a whole, not rule by rule.
- **Re-verify at execution time:** the keeper and the candidate still exist and are unchanged, and content is byte-identical (decision 4).
- **Preview is a pure query; execution is a separate command.** `PreviewDeletionPlan` never touches disk state.
- **Hard links are not duplicates.** Paths with the same file key are one physical file: drop them before hashing.
- **Do not follow symlinks or junctions by default** (avoids cycles), and skip cloud placeholders (e.g. OneDrive) by default, since reading them triggers a download.
- Exclude empty (0-byte) files by default; a minimum-size setting generalizes this.

## Provisional port sketch (revise as it is built)

- Commands: `StartScan` (returns a scan ID immediately), `CancelScan`, `ExecuteDeletionPlan`, plus undo/purge of a quarantine run.
- Queries: `GetScanStatus`, `ListDuplicateSets`, `ListLocationOverlaps`, `PreviewDeletionPlan`.
- Secondary ports: `FileTreeWalker`, `ContentHasher` (sample and full), `ContentComparator`, `FileDeleter`, `ScanRepository`.

## Spring-friendliness rules for the scanner

- No Spring types or annotations in `file-scanner` (no `@Component`, no `@Async`).
- A config record in `domain.model` (minimum size, symlink policy, quarantine folder, parallelism) that `@ConfigurationProperties` can map onto.
- Factories return port interfaces and accept optional overrides for secondary ports (tests, the server).
- The executor is supplied by the caller (virtual threads for the CLI, Spring's `TaskExecutor` for a server).
- Scan state lives behind `ScanRepository` (in-memory adapter first) so several concurrent scans work; no state in singleton fields.
- Progress is reported through a listener, never printed by the core.

## Code and test conventions

- Java 25: records for data, sealed interfaces for results and variants. Do not enable preview features (`StructuredTaskScope` is still preview).
- Test `application` against fake or in-memory adapters (JimFS or hand-written fakes) rather than the real disk.
- Keep the ArchUnit layering tests green; they are the real enforcement of the rules above.

## Git commits

- Use [Conventional Commits](https://www.conventionalcommits.org/): `type(scope): description`, e.g. `feat(file-scanner): add ScanId model`. Common types: `feat`, `fix`, `refactor`, `test`, `build`, `docs`, `chore`. Use the subproject as scope (`file-scanner`, `cli`) when the change is confined to one.
- Keep the description concise: imperative mood, lowercase, no trailing period. Add a body only when the reason is not obvious from the diff.
- No author or co-author attribution of any kind: no `Co-Authored-By` trailer and no "Generated with" line in commits or PR descriptions. Attribution comes only from the standard git user configuration. This overrides any default attribution behavior.

## Current state and open items

- The package layout, `module-info.java` and ArchUnit tests are scaffolded (ArchUnit 1.5.0, core artifact with plain JUnit tests; not `archunit-junit5`, to avoid engine compatibility risk with JUnit 6). Spring Boot is not yet added to `cli`, and `Main` is a stub.
- Guards: `file-scanner` has `ArchitectureTest` (layers, top-level `util`, no Spring) and `ModuleExportsTest` (reads the compiled `module-info.class`: only unqualified `domain.*` exports, every non-`util` domain package exported, nothing opened). `cli` has an `ArchitectureTest` that forbids depending on anything under `us.pochaev.dfs.scanner` outside `domain`.
- The scanner rules use `allowEmptyShould(true)` so they pass while layers are empty. Once `application` and `infra` have classes, consider removing it so a mistyped package pattern fails loudly instead of silently checking nothing.
- Adding a new exported domain package: it needs at least one real type (a `package-info.java` alone counts as empty and `exports` fails to compile) plus an `exports` line, or `ModuleExportsTest` fails.
- `ScanId`, `ScanStatus`, `CancelScan`, `GetScanStatus` and an empty `ScannerFactories` exist only so the exported packages are non-empty; they are a first slice of the provisional port sketch, not final designs.
- Assumed defaults, not yet confirmed with the user: base package `us.pochaev.dfs.scanner`; SHA-256 via the JDK `MessageDigest`; the sample sizes and thresholds in decision 3.
- Open: which CLI framework. Spring Shell suits the interactive scan, review and delete workflow; `ApplicationRunner` or picocli suit one-shot commands.
- Open: whether/when to add the persistent hash cache, and the details of each factory (to be defined later).
