---
id: DFS-031
title: Extract the scanner wiring into a scanner-spring module
status: in-review
type: refactor
scope: [cli, build, docs]
archetype: wiring
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
gates: [done]
autorun: true
priority: high
lane: backend
depends_on: []
touches:
  - settings.gradle.kts
  - scanner-spring/build.gradle.kts
  - scanner-spring/src/main/java/us/pochaev/dfs/scannerspring
  - scanner-spring/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
  - scanner-spring/src/test/java/us/pochaev/dfs/scannerspring
  - cli/build.gradle.kts
  - cli/src/main/java/us/pochaev/dfs/cli/ScannerConfiguration.java
  - cli/src/test/java/us/pochaev/dfs/cli/ScannerConfigurationTest.java
  - cli/src/test/java/us/pochaev/dfs/cli/CliApplicationTest.java
  - CLAUDE.md
  - README.md
blocked_reason:
created: 2026-10-31
updated: 2026-10-31
completed:
---

# DFS-031: Extract the scanner wiring into a scanner-spring module

## Story

As a developer of the CLI and the planned local server
I want the scanner's Spring wiring in one shared autoconfigure module
So that `cli` and `server` get the same scanner beans without copying the configuration

## Context

CLAUDE.md decision 6 says that when a server appears, the shared wiring moves out of `cli` into a `scanner-spring` autoconfigure module, using `@ConfigurationProperties` and `@ConditionalOnMissingBean` for port overrides. `docs/design/ui.md` D1 makes this the first ticket of the UI work, ahead of the server's scanner wiring. The frontend session waits on it before wiring the server.

Today the wiring is `cli/src/main/java/us/pochaev/dfs/cli/ScannerConfiguration.java` (DFS-020):
- a virtual-thread `ExecutorService` closed with `shutdownNow`;
- one `ScannerFactories.scanUseCases(executor)` call;
- four port beans (`StartScan`, `CancelScan`, `GetScanStatus`, `GetScanResult`), each wrapped in a delegate of that one interface, so injection by type finds one candidate (DFS-020, commit 73d444f; the `wiring` archetype's checklist item 2).

`ScannerConfigurationTest` (DFS-020.AC-1) asserts one bean per port and a shared scan repository.

This ticket is also the planned live run of CCW-017's autorun (CCW-017.AC-12): from `ready` to the product-owner review with no input in between, if refined with `autorun: true`.

### Decisions

1. **One `scanner-spring` subproject with real autoconfiguration.** It holds `ScannerAutoConfiguration` (`@AutoConfiguration`), listed in `scanner-spring/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. `cli` depends on it with `implementation(project(":scanner-spring"))` and deletes its own `ScannerConfiguration`, so `@SpringBootApplication` gets the beans through autoconfiguration. There is no separate starter module, since nothing is published. Autoconfiguration runs after the app's own beans, so `@ConditionalOnMissingBean` reliably lets an app override a bean by declaring its own. Tests use Spring's `ApplicationContextRunner` with `AutoConfigurations.of(ScannerAutoConfiguration.class)`.
2. **No `@ConfigurationProperties` yet.** The only scanner settings today are per scan (`ScanConfig`, passed to each `StartScan.start` call and built by `cli` from its arguments, by the server from each request), and binding them would give `cli` a second source of defaults. The first ticket that adds a real app-wide setting (for example the quarantine folder) adds a `dfs.scanner.*` properties record to this module. `cli`'s behaviour is unchanged by this ticket.
3. **A dedicated, named executor bean.** The module declares `scanExecutor`, an `ExecutorService` of virtual threads closed with `shutdownNow` (as `cli` does today, DFS-020 decision 7: closing must not wait for a running scan, or Ctrl+C hangs), under `@ConditionalOnMissingBean(name = "scanExecutor")`. `ScanUseCases` takes it by that name (`@Qualifier("scanExecutor")`), never by type, so it cannot clash with Spring Boot's own `applicationTaskExecutor`. An app supplies its own pool by declaring a bean named `scanExecutor`.
4. **Every bean backs off.** `ScanUseCases` and each of the four port beans carry `@ConditionalOnMissingBean` by type (the executor by name, decision 3), per CLAUDE.md decision 6. An app or a test can replace one port, for example with a fake, while the others stay real. A replaced port no longer shares the scan repository with the rest, and `ScannerAutoConfiguration`'s Javadoc says so. Each port bean still wraps its port in a delegate of that interface alone (the `wiring` archetype's checklist item 2), so `@ConditionalOnMissingBean(StartScan.class)` cannot be satisfied by a bean of another port type.
5. **Package `us.pochaev.dfs.scannerspring`, with its own guard.** It sits outside `us.pochaev.dfs.scanner..`, so the `cli` (and later `server`) `ArchitectureTest` rule ("nothing under `us.pochaev.dfs.scanner` outside `domain`") stays unchanged. `scanner-spring` gets its own `ArchitectureTest` forbidding `..scanner.application..` and `..scanner.infra..`. `cli`'s `ScannerConfigurationTest` (DFS-020.AC-1) is deleted with `ScannerConfiguration`, and its assertions move to `scanner-spring` as `ScannerAutoConfigurationTest` (AC-1). `cli` keeps `CliApplicationTest`, which starts the real application and so proves the autoconfigured beans reach it (AC-5). The build: `scanner-spring` applies `java-library` and the Spring Boot BOM (no Boot plugin, it is not an application), with `api(project(":file-scanner"))` and `implementation("org.springframework.boot:spring-boot-autoconfigure")`. `cli` adds `implementation(project(":scanner-spring"))`. No output format changes, so no test asserting the JSONL or text layout changes (DFS-030.L-2). `CLAUDE.md` (the subproject list, decision 6, "Current state") and the README's Layout section name the new module.
6. **`gates: [done]`.** Decisions 1–5 fix the module's shape, beans, executor, overrides and package, so the design needs no separate stop; the pipeline runs to the product-owner review. If refined with `autorun: true`, this is CCW-017's live run (CCW-017.AC-12): record its `plan.json` and the notification text when it reaches review.

## Acceptance criteria

The full ID of each criterion is `DFS-031.AC-<n>`.

- **AC-1** A Spring Boot context with `scanner-spring` on the classpath and no scanner beans of its own has exactly one bean of each of `StartScan`, `CancelScan`, `GetScanStatus` and `GetScanResult`, provided by `ScannerAutoConfiguration` through `AutoConfiguration.imports`, and a scan started through `StartScan` is known to the other three (one shared scan repository).
- **AC-2** With no bean named `scanExecutor` declared by the app, the context has one, which runs tasks on virtual threads; when the app declares its own `ExecutorService` named `scanExecutor`, that bean replaces the default and scans run on it.
- **AC-3** When the app declares its own bean of one port type (`StartScan`, `CancelScan`, `GetScanStatus` or `GetScanResult`) or its own `ScanUseCases`, the context holds exactly that bean for that type and the autoconfiguration still provides the other beans.
- **AC-4** `scanner-spring`'s classes depend on nothing in `us.pochaev.dfs.scanner.application` or `us.pochaev.dfs.scanner.infra`, enforced by an ArchUnit test in `scanner-spring`.
- **AC-5** `cli` declares no scanner beans of its own and still scans: a run of the real CLI application on a temp folder with two identical files writes one `set` line, with exit code 0.

## Scenarios

```gherkin
Feature: Shared scanner wiring

  @AC-1
  Scenario: The autoconfiguration provides the four scan ports
    Given an ApplicationContextRunner with AutoConfigurations.of(ScannerAutoConfiguration.class) and no user beans
    When the context starts
    Then it has exactly one bean of each of StartScan, CancelScan, GetScanStatus and GetScanResult
    And a scan of an empty temp folder started through StartScan reaches COMPLETED through GetScanStatus
    And GetScanResult returns Success for that scan ID

  @AC-1
  Scenario: The autoconfiguration is registered for Spring Boot
    Given the scanner-spring jar's resources
    When META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports is read
    Then it lists us.pochaev.dfs.scannerspring.ScannerAutoConfiguration
  @AC-2
  Scenario: The default scan executor uses virtual threads
    Given an ApplicationContextRunner with the scanner autoconfiguration and no user beans
    When a task is submitted to the bean named scanExecutor
    Then it runs on a virtual thread

  @AC-2
  Scenario: An app's own scanExecutor replaces the default
    Given an ApplicationContextRunner with the scanner autoconfiguration and a user ExecutorService bean named scanExecutor that counts submitted tasks
    When a scan of an empty temp folder is started through StartScan and reaches COMPLETED
    Then the context has exactly one bean named scanExecutor, the user's
    And the user's executor counted at least one submitted task
  @AC-3
  Scenario: An app's own port bean replaces only that port
    Given an ApplicationContextRunner with the scanner autoconfiguration and a user GetScanResult bean that always returns Failure(UNKNOWN_SCAN)
    When the context starts
    Then the context has exactly one GetScanResult bean, the user's
    And it has exactly one bean of each of StartScan, CancelScan and GetScanStatus, from the autoconfiguration

  @AC-3
  Scenario: An app's own ScanUseCases backs the four ports
    Given an ApplicationContextRunner with the scanner autoconfiguration and a user ScanUseCases bean built by ScannerFactories.scanUseCases on a counting executor
    When a scan of an empty temp folder is started through the StartScan bean and reaches COMPLETED through the GetScanStatus bean
    Then the context has exactly one ScanUseCases bean, the user's
    And the counting executor counted at least one submitted task
  @AC-4
  Scenario: scanner-spring depends only on the scanner's domain
    Given the compiled classes of package us.pochaev.dfs.scannerspring
    When the scanner-spring ArchitectureTest runs
    Then no class depends on us.pochaev.dfs.scanner.application or us.pochaev.dfs.scanner.infra

  @AC-5
  Scenario: The CLI scans through the autoconfigured beans
    Given cli has no ScannerConfiguration class and depends on scanner-spring
    And a temp folder holding a.txt and b.txt with the same 10 bytes
    When the CLI application runs with that folder as its only argument
    Then stdout holds exactly one set line, listing a.txt and b.txt
    And the exit code is 0
```

## Scope

**Touches:**
- `settings.gradle.kts`: include `scanner-spring`.
- `scanner-spring/build.gradle.kts`: new subproject build (decision 5).
- `scanner-spring/src/main/java/us/pochaev/dfs/scannerspring`: `ScannerAutoConfiguration` (decisions 1, 3, 4).
- `scanner-spring/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`: registers it.
- `scanner-spring/src/test/java/us/pochaev/dfs/scannerspring`: `ScannerAutoConfigurationTest`, `ArchitectureTest`.
- `cli/build.gradle.kts`: depend on `scanner-spring`.
- `cli/src/main/java/us/pochaev/dfs/cli/ScannerConfiguration.java`: deleted.
- `cli/src/test/java/us/pochaev/dfs/cli/ScannerConfigurationTest.java`: deleted (moved, decision 5).
- `cli/src/test/java/us/pochaev/dfs/cli/CliApplicationTest.java`: gains the AC-5 test.
- `CLAUDE.md`: subproject list, decision 6 and "Current state" name `scanner-spring`.
- `README.md`: Layout names `scanner-spring`.

**Out of scope:**
- The `server` subproject and its own wiring (the frontend session's tickets).
- New use cases (DFS-022, DFS-032 to DFS-034).
- `@ConfigurationProperties` (decision 2): added by the first ticket with an app-wide setting.

## Definition of done

- [x] Every scenario above is an automated test and passes
- [x] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [x] No files changed outside the paths listed under Touches
- [x] Every item of the `wiring` archetype's checklist is addressed in `design.md` or marked not applicable with a reason
- [x] `cli`'s `ArchitectureTest` is unchanged and still forbids the scanner's `application` and `infra` packages
- [x] `dfs.bat` on the 25-file test fixtures folder (never a real drive) prints the same report before and after the change (verifier: 3eed87d vs c4210d3, `--format=text`: identical except the timestamp line; JSONL identical apart from `startedAt`/`finishedAt`; both exit 0)

## Open questions

None.
