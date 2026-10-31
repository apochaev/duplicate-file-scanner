---
id: DFS-031
title: Extract the scanner wiring into a scanner-spring module
status: draft
type: refactor
scope: [cli, build]
archetype: wiring
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
# gates: [done]        # pipeline tickets: decided in refine, required from ready on
# autorun: false       # pipeline tickets: true lets Claude start /story run itself
priority: high
lane: backend
depends_on: []
touches: []
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

## Acceptance criteria

The full ID of each criterion is `DFS-031.AC-<n>`.

- **AC-1** A Spring Boot context with `scanner-spring` on the classpath and no scanner beans of its own has exactly one bean of each of `StartScan`, `CancelScan`, `GetScanStatus` and `GetScanResult`, provided by `ScannerAutoConfiguration` through `AutoConfiguration.imports`, and a scan started through `StartScan` is known to the other three (one shared scan repository).

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
    Then it lists us.pochaev.dfs.scannerspring.ScannerAutoConfiguration <package to be decided>
```

## Scope

**Touches:**
- <to be refined: `settings.gradle.kts`, the new `scanner-spring/` subproject, `cli/build.gradle.kts`, `cli`'s `ScannerConfiguration` and its test>

**Out of scope:**
- The `server` subproject and its own wiring (the frontend session's tickets).
- New use cases (DFS-022, DFS-032 to DFS-034).
- `@ConfigurationProperties` (decision 2): added by the first ticket with an app-wide setting.

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] No files changed outside the paths listed under Touches
- [ ] <ticket-specific check>

## Open questions

- Executor: who supplies it? Today the CLI's virtual threads; the server may want Spring's `TaskExecutor` (CLAUDE.md, Spring-friendliness rules).
- Which beans are `@ConditionalOnMissingBean`: the executor, `ScanUseCases`, each port?
- Package name and its ArchUnit guard (nothing under `us.pochaev.dfs.scanner` outside `domain`).
- Where `ScannerConfigurationTest` goes, and what `cli` keeps.
- `gates:` and `autorun:`.
