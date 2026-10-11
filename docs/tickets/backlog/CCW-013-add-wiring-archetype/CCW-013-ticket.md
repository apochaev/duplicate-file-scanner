---
id: CCW-013
title: Add a wiring archetype
status: in-review
type: feature
scope: [docs]
archetype: none
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
priority: medium
depends_on: [DFS-019, DFS-020, CCW-014]
touches: [.claude/archetypes/wiring/, docs/tickets/backlog/CCW-013-add-wiring-archetype/dry-run.md, docs/tickets/completed/DFS-019-wire-scanner-factories/learnings.md]
blocked_reason:
created: 2026-10-09
updated: 2026-10-11
completed:
---

# CCW-013: Add a wiring archetype

## Story

As the person running tickets through the story pipeline
I want an archetype for wiring tickets (factories, and the composition root that turns them into a running app)
So that the next wiring ticket gets a checklist that asks the questions DFS-019 and DFS-020 only met during implementation, instead of a `use-case` checklist that mostly does not apply

## Context

DFS-019.L-5 (proposed, 2 recurrences) asks for this. Two real wiring tickets are its evidence:

- **DFS-019** (wire `ScannerFactories.scanUseCases`), run through the pipeline as `use-case`: `design.md` marked 9 of 14 `use-case` checklist items not applicable, and the shared-versus-fresh-instances question was in no checklist.
- **DFS-020** (scan from the CLI and write JSONL), built by hand as `archetype: none`: the factory returns one `ScanService` behind all four ports, so Spring injection by type failed ("expected single matching bean but found 2") and every `@Bean` had to wrap its port in a delegate of that interface alone (commit 73d444f). Refinement also had to discover that Spring Boot's banner, startup log and Gradle's lifecycle lines all go to stdout by default, that `ExecutorService.close()` as a bean destroy method would block Ctrl+C on a running scan, and that `ScanListener` has no completion event.

L-5's Change lists the checklist items so far: shared versus fresh instances per build, a null or missing executor, default adapters versus overrides, the exported return type; its re-proposal adds "does the returned object implement more than the port it is returned as?" and "how the consumer's container (Spring) sees the returned types".

Existing archetypes to model on: `.claude/archetypes/use-case/` (version 3: `archetype.md`, `design.md.tmpl`, `test-plan.md.tmpl`, `test-guide.md`, `impl-guide.md`) and `pipeline-stage` (version 12). CLAUDE.md already expects CLI wiring to get its own archetype. Applying L-5 is part of this ticket: mark it `applied` via `/learn`, pointing here.

**Decisions (refinement, 2026-10-09):**

1. **One `wiring` archetype for both shapes.** It covers a domain factory (a static method in `domain.factory` that builds services from adapters and returns ports, as in DFS-019) and a composition root in a consuming app (a Spring `@Configuration` calling the factories, plus the runner that turns a process invocation into a run, as in DFS-020). `archetype.md` has a shared checklist section (instances, executor, how the returned types look to a container) and one section per shape; a ticket skips the other shape's section with one line. Two examples make one archetype better than two; DFS-020's surprise sat on the boundary between factory and root, so one checklist must see both sides. Splitting later stays cheap if the sections drift apart.
2. **The red gate learns to see a wrapped stub.** Read from `tools/story-gate`: `TestResult.failedWithStub()` accepts a story test's failure only when its outermost exception is exactly `java.lang.UnsupportedOperationException` with a message starting `not implemented:` (`Junit.java` reads the `<failure>` element's `type` attribute). Spring wraps a stub: a stubbed `@Bean` fails the context with `BeanCreationException`, a stubbed runner makes `SpringApplication.run` throw `IllegalStateException`, with the stub only as a `Caused by:` in the trace. So every Spring-level story test of a composition root would fail the red gate as written. The fix: a failure also counts as a stub failure when its trace (the `<failure>` element's text) has a `Caused by: java.lang.UnsupportedOperationException: not implemented:` line. This also covers other wrappers (`ExecutionException`). `green` still requires real passes later, so the looser red check lets nothing through to the end.
3. **The gate change is its own ticket, CCW-014 ("Recognise wrapped stubs in the red gate"), which this ticket depends on.** `SelfTest.coverage` requires that every scenario of a ticket with a selftest case is claimed by one of its cases; the gate change's evidence is selftest cases (a real JUnit report with a `Caused by:` stub line, which case folders already support as `reports`), but this ticket's archetype scenarios are checked by reading and cannot be claimed by a case. Split, each ticket keeps evidence of its own kind: CCW-014 touches only `tools/story-gate/` and is proven by selftest; CCW-013 relies on it, and its archetype tells composition-root tickets that a Spring-wrapped stub passes the red gate.
4. **The checklist: 11 items, testing levels in the test guide.** Each item names the ticket or CLAUDE.md rule it traces to.
   - *Shared:* (1) instances: fresh per factory call or shared, and what the returned ports share (DFS-019); (2) returned objects as a container sees them: does an object implement more than the port it is returned as, how does by-type injection see it, wrap each port when one object backs several (DFS-020); (3) executor: required or optional, a null one, who owns its lifecycle, whether shutting it down blocks on running work (`close` vs `shutdownNow`) (DFS-019, DFS-020); (4) defaults and overrides of secondary ports (CLAUDE.md factory rule; DFS-019.L-5); (5) exported surface: only `domain` types cross, return records in `domain.factory`, `ModuleExportsTest` and the consuming app's ArchUnit rule stay green (DFS-019; CLAUDE.md classpath caveat).
   - *Domain factory:* (6) guard tests: which `ArchitectureTest` rules become non-empty (`allowEmptyShould`) and that the test-author edits them (DFS-019).
   - *Composition root:* (7) streams: stdout vs stderr, and every default that writes to stdout (framework banner and logs, the build tool's lifecycle lines) (DFS-020); (8) exit codes: values, how they reach the process (`ExitCodeGenerator`), how the build tool reacts (Gradle `run` fails on non-zero) (DFS-020); (9) waiting for async work: completion event or polling, and what is written when the work did not complete (DFS-020); (10) interruption: Ctrl+C and shutdown hooks vs the container's shutdown, and whether the work has side effects needing an orderly stop (DFS-020); (11) arguments: options, usage errors, nothing starts before the arguments are valid (DFS-020).
   - Testing levels (a Spring-free command class tested with fake ports, plus the few Spring-level tests only a real context proves: a real start, output capture) go in the test guide, not the checklist: they are how to test, not what to decide (DFS-020 decision 8).
5. **Files: the five every agent reads, plus one skeleton.** Read from the agent definitions: the architect reads `archetype.md`, `design.md.tmpl` and every file under `skeletons/`; the test-author `test-plan.md.tmpl` and `test-guide.md`; the implementer `impl-guide.md`. So all five are required. One skeleton, `skeletons/PortBeansConfiguration.java.tmpl`, drawn from DFS-020's `ScannerConfiguration` with `<...>` placeholders: a `@Configuration(proxyBeanMethods = false)` with an executor bean closed by `destroyMethod = "shutdownNow"`, one bean for the factory's result, and one delegate bean per port (an anonymous class for a port with several methods, a method reference for a single-method port). It designs checklist items 2 and 3 in rather than leaving them to be remembered. No skeleton for the command class (it varies most between tickets) or for a domain factory (simple enough without one).
6. **Proof: a dry run against both examples; the first live run is a follow-up.** `dry-run.md` in this ticket's folder applies the checklist to DFS-019 and DFS-020 after the fact: for each of the 11 items, the decision in which ticket answered it, or "not asked; found during implementation" with the commit. It must show that item 2 would have raised DFS-020's bean-injection failure at refinement. The skeleton, filled with DFS-020's names, is compared with the real `cli/.../ScannerConfiguration.java`: the same beans in the same shapes. This proves the checklist asks the right questions, not that the pipeline runs a wiring ticket; the first live `/story run` of a wiring ticket is out of scope, and its retro (`/story learn`) is that check.
7. **Archetype `none`.** Built by hand, like every earlier CCW ticket (CCW-002 and CCW-011 created the other archetypes the same way): the pipeline needs Java story tests that go red against stubs, and this ticket writes Markdown and a template. Each done-item is ticked with its evidence.

## Acceptance criteria

<!-- Filled during refinement. State the full ID once, then one testable statement per line, numbered from 1. Never renumber or delete: retire with `- **AC-<n> (retired)** <statement>. Retired <date>: <reason>`. -->

The full ID of each criterion is `CCW-013.AC-<n>`.

- **AC-1** `.claude/archetypes/wiring/archetype.md` exists with frontmatter `name: wiring`, `version: 1`, and `phases`, `gates` and `models` in the shape the story skill's preflight reads to build `plan.json` (as in `use-case`: `phases: [design, test, implement, verify]`, `gates: [design, done]`, a `models` entry per agent), says it applies to domain factories and to composition roots in a consuming app, and has a shared checklist section plus one section for each of the two shapes (decision 1).
- **AC-2** `archetype.md`'s "Where files go" says how a composition root is stubbed (each `@Bean` method and the runner's command method throw the standard `not implemented: <TICKET-ID>` stub) and that its Spring-level story tests are red through the wrapped stub, which the red gate accepts since CCW-014 (decisions 2, 3).
- **AC-3** `archetype.md`'s edge-case checklist has the 11 items of decision 4, numbered 1 to 11 across its three sections (shared 1-5, domain factory 6, composition root 7-11), each naming the ticket or CLAUDE.md rule it traces to (decision 4).
- **AC-4** The archetype's `test-guide.md` describes the two testing levels of a composition root: a Spring-free command class tested with hand-written fake ports, and a few Spring-level tests (a real `SpringApplication` start, stdout and stderr captured apart with `OutputCaptureExtension`) for what only a real context proves (decision 4).
- **AC-5** `.claude/archetypes/wiring/` holds `archetype.md`, `design.md.tmpl`, `test-plan.md.tmpl`, `test-guide.md` and `impl-guide.md`, each written for wiring tickets (no text left over from `use-case` that names use-case-only concepts such as CQS ports or the scan pipeline as this archetype's subject) (decision 5).
- **AC-6** `skeletons/PortBeansConfiguration.java.tmpl` is a `@Configuration(proxyBeanMethods = false)` with an executor bean declared `destroyMethod = "shutdownNow"`, one bean for the factory's result, and one delegate bean per port: an anonymous class for a port with several methods and a method reference for a single-method port (decision 5).
- **AC-7** `dry-run.md` has one row per checklist item (1 to 11) giving, for DFS-019 and DFS-020 each, the decision that answered it, "not applicable" with a reason, or "not asked; found during implementation" with the commit; item 2's DFS-020 row names commit 73d444f and the "expected single matching bean" failure (decision 6).
- **AC-8** `dry-run.md` shows the skeleton filled with DFS-020's names (the executor, `ScanUseCases`, the four ports) next to the real `ScannerConfiguration`, and states each difference, if any, with a reason (decision 6).

## Scenarios

```gherkin
Feature: The wiring archetype

  # check: mechanical (grep the frontmatter and section headings), then reading (the reach paragraph)
  @AC-1
  Scenario: The archetype covers both shapes in one file
    When ".claude/archetypes/wiring/archetype.md" is read
    Then its frontmatter has "name: wiring" and "version: 1"
    And it has "phases: [design, test, implement, verify]", "gates: [design, done]" and a models entry for each of architect, test-author, implementer, verifier and retro
    And it has a shared checklist section, a section for domain factories and a section for composition roots
    And its opening paragraph names DFS-019 and DFS-020 as the examples it was drawn from

  # check: reading ("Where files go")
  @AC-2
  Scenario: A composition root is stubbed so its Spring tests go red through the gate
    When the "Where files go" section of ".claude/archetypes/wiring/archetype.md" is read
    Then it says each @Bean method and the runner's command method throw UnsupportedOperationException("not implemented: <TICKET-ID>")
    And it says a Spring-level story test fails with that stub wrapped (BeanCreationException, IllegalStateException) and that the red gate accepts it, naming CCW-014

  # check: mechanical (count the numbered items per section), then reading (each names its source)
  @AC-3
  Scenario: The checklist holds the 11 decided items in three sections
    When the edge-case checklist of ".claude/archetypes/wiring/archetype.md" is read
    Then it has items numbered 1 to 11, none missing or repeated
    And items 1-5 are under the shared section, item 6 under the domain-factory section and items 7-11 under the composition-root section
    And every item names DFS-019, DFS-020, DFS-019.L-5 or a CLAUDE.md rule in parentheses

  # check: reading
  @AC-4
  Scenario: The test guide describes a composition root's two testing levels
    When ".claude/archetypes/wiring/test-guide.md" is read
    Then it says the command logic lives in a Spring-free class tested with hand-written fake ports
    And it says only what a real context proves (bean wiring, stdout purity) is tested with a real SpringApplication start and OutputCaptureExtension

  # check: mechanical (ls the folder; grep each file for "use-case" as its own subject)
  @AC-5
  Scenario: Every file an agent reads exists and is written for wiring
    When ".claude/archetypes/wiring/" is listed
    Then it holds archetype.md, design.md.tmpl, test-plan.md.tmpl, test-guide.md and impl-guide.md
    And none of them calls itself the use-case archetype or names CQS ports or the scan pipeline as its subject

  # check: mechanical (grep the skeleton), then reading
  @AC-6
  Scenario: The skeleton carries the port-wrapping pattern
    When ".claude/archetypes/wiring/skeletons/PortBeansConfiguration.java.tmpl" is read
    Then it contains "@Configuration(proxyBeanMethods = false)" and "destroyMethod = \"shutdownNow\""
    And it has a delegate bean written as an anonymous class and one written as a method reference
    And its placeholders are in <...> form

  # check: mechanical (count the item rows), then reading (item 2's DFS-020 row)
  @AC-7
  Scenario: The dry run walks every checklist item through both examples
    When "docs/tickets/backlog/CCW-013-add-wiring-archetype/dry-run.md" is read
    Then it has a row for each checklist item 1 to 11, each with a DFS-019 and a DFS-020 entry
    And item 2's DFS-020 entry is "not asked; found during implementation", naming commit 73d444f and "expected single matching bean but found 2"

  # check: reading (side-by-side comparison)
  @AC-8
  Scenario: The filled-in skeleton matches DFS-020's real configuration
    When the skeleton filled with DFS-020's names in "dry-run.md" is compared with "cli/src/main/java/us/pochaev/dfs/cli/ScannerConfiguration.java"
    Then both declare the executor bean with destroyMethod "shutdownNow", a ScanUseCases bean, a StartScan bean as an anonymous class and CancelScan, GetScanStatus and GetScanResult beans as method references
    And every difference between them is listed with a reason
```

## Scope

**Touches:**
- `docs/tickets/backlog/CCW-013-add-wiring-archetype/dry-run.md`: the dry run of decision 6
- `.claude/archetypes/wiring/`: the new archetype (`archetype.md` with its checklist, templates and guides)
- `docs/tickets/completed/DFS-019-wire-scanner-factories/learnings.md`: DFS-019.L-5 marked `applied` (via `/learn apply`)

**Out of scope:**
- Changing the `use-case` or `pipeline-stage` archetypes
- Changing the story skill or the agents
- Changing `tools/story-gate`: the wrapped-stub change is CCW-014 (decision 3)
- Product code
- A live `/story run` of a wiring ticket: the next wiring ticket's own run, whose retro (`/story learn`) checks the archetype in use (decision 6)

## Definition of done

- [x] Every scenario above is checked the way it says (mechanical, reading or live) and passes, with the evidence noted beside this item:
  - AC-1 (mechanical + reading): frontmatter has `name: wiring`, `version: 1`, `phases: [design, test, implement, verify]`, `gates: [design, done]`, and a `models` entry for all five agents; `### Shared`, `### Domain factory`, `### Composition root` present; the opening bullets name DFS-019 and DFS-020.
  - AC-2 (reading): "Where files go" lists the factory method, each `@Bean` method and the command's run method as stubs, and names `BeanCreationException`, `IllegalStateException` and CCW-014.
  - AC-3 (mechanical + reading): items numbered 1-5 under Shared, 6 under Domain factory, 7-11 under Composition root; 0 items without a `(DFS-0…` or `(CLAUDE.md…` source.
  - AC-4 (reading): `test-guide.md`, "Testing a composition root: two levels", covers the Spring-free command class with hand-written fakes and the Spring level with `AnnotationConfigApplicationContext`, `SpringApplication` and `OutputCaptureExtension`.
  - AC-5 (mechanical): the folder holds the five files plus `skeletons/`; `grep -ciE "CQS|scan pipeline|# use-case|use-case archetype"` gives 0 in each.
  - AC-6 (mechanical + reading): the skeleton has `proxyBeanMethods = false`, `destroyMethod = "shutdownNow"`, an anonymous-class delegate and a method-reference delegate, all names as `<...>` placeholders.
  - AC-7 (mechanical + reading): `dry-run.md` has rows 1-11, each with a DFS-019 and a DFS-020 cell; item 2's DFS-020 cell names `73d444f` and "expected single matching bean but found 2".
  - AC-8 (reading): `dry-run.md` shows the skeleton filled with DFS-020's names beside the real `ScannerConfiguration`: same six beans and shapes; two differences listed (parameter/local names, Javadoc wording), neither affecting behavior.
- [x] No files changed outside the paths listed under Touches: `git status` shows only `.claude/archetypes/wiring/`, `docs/tickets/backlog/CCW-013-add-wiring-archetype/dry-run.md`, DFS-019's `learnings.md` and this ticket
- [x] DFS-019.L-5 is marked `applied` with a pointer to this ticket (via `/learn`): `/learn apply DFS-019.L-5` on 2026-10-11, Decision names CCW-013 and the checklist items 1-5

## Open questions

None.
