---
id: DFS-039
title: Report a clear error when the server starts on an old Java
status: draft
type: feature
scope: [build]
archetype:
# models: {architect: {model: opus, effort: high, maxTurns: 25}}
# gates: [done]        # pipeline tickets: decided in refine, required from ready on
priority: low
lane: frontend
depends_on: []
touches: []
blocked_reason:
created: 2026-11-01
updated: 2026-11-08
completed:
---

# DFS-039: Report a clear error when the server starts on an old Java

## Story

As the user starting the DFS server from its start script or `java -jar`
I want a one-line message naming the Java version it needs and the one it found
So that I know to point `JAVA_HOME` at a Java 25 instead of reading a stack trace

## Context

Found at DFS-037's product-owner review (2026-11-01): `bin\dfs-server.bat` from `:server:installBootDist` ran Java 17 from the window's `JAVA_HOME` and failed with `java.lang.UnsupportedClassVersionError: us/pochaev/dfs/server/Main has been compiled by a more recent version of the Java Runtime (class file version 69.0), this version of the Java Runtime only recognizes class file versions up to 61.0`, followed by a stack trace. `docs/design/ui.md` U11 says the server requires Java 25. The `cli` start scripts likely have the same gap.

## Acceptance criteria

The full ID of each criterion is `DFS-039.AC-<n>`.

- **AC-1** <to be refined>

## Scenarios

```gherkin
Feature: Clear error on an old Java

  @AC-1
  Scenario: <to be refined>
    Given <concrete state>
    When <action>
    Then <observable outcome>
```

## Scope

**Touches:**
- <to be refined>

**Out of scope:**
- Bundling a runtime (`jpackage`, U11 "Later").

## Definition of done

- [ ] Every scenario above is an automated test and passes
- [ ] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [ ] No files changed outside the paths listed under Touches
- [ ] <ticket-specific check>

## Open questions

- Where the check lives: a tiny launcher class compiled for an old release (`--release 8`) that checks `Runtime.version()` and then calls `Main`, a check in the generated start scripts, or both.
- Does it cover `java -jar dfs-server.jar` too, and the `cli` start scripts?
- The exact message and exit code.
- Archetype: `none` or a pipeline archetype?
