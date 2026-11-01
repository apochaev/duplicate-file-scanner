# CCW-019 dry run: the endpoint archetype against DFS-040

CCW-019 decision 2: walk the archetype (`.claude/archetypes/endpoint/`, version 1) against DFS-040's ticket and code, and against the surprises met while building DFS-038 and DFS-040. Each row names what the archetype would have raised it with: a checklist item of `archetype.md` ("item N"), a section of `archetype.md`, or a section of a guide or template. A row with nothing to trace to would have been a gap; a checklist item no row traces to would be cut. This is a reading, not a pipeline run: the first live use is the next endpoint ticket.

## Decisions and surprises traced

| Row | Event (source) | Traced to |
|---|---|---|
| D1 | DFS-040 decision 1: split the API from the screen (DFS-041) | `archetype.md` "Applies to" (an Angular screen is not this archetype; a ticket that only regenerates the client belongs to its screen) |
| D2 | DFS-040 decision 2: a `ScanRegistry` in `server` lists this run's scans | item 8 (state kept in `server`: what, how long, thread safety, ordering) |
| D3 | DFS-040 decision 3: one SSE stream per scan, a throttled `ScanProgress`, current state on connect, ends at the terminal status | item 9 (streams); `impl-guide.md` "Conventions", streams |
| D4 | DFS-040 decision 4: `POST /api/scans` body, optional settings with core defaults, `201` with `Location`, `400` with `{code, message}` and the core's codes passed through | item 3 (input validation and error codes); item 4 (status codes and headers); `impl-guide.md` "Validate, then call the core" |
| D5 | DFS-040 decision 5: the dev proxy rewrites `Origin` so `POST` works through `ng serve` | item 11 (the dev proxy) |
| D6 | DFS-040 decision 6: `archetype: none`, built by hand, because no archetype fit | `archetype.md` "Applies to" (this archetype now fits such a ticket); first live use on the next endpoint ticket |
| D7 | DFS-040 decision 7: `scanner-spring`, a feature package, the API's own records, tag and operation IDs, `SseEmitter`, `HoldingExecutorConfiguration` and a large tree for the cancel tests | `archetype.md` "Where files go"; item 5 (the API records); item 6 (the spec); item 10 (asynchronous states in tests); `test-guide.md` "Asynchronous states" |
| S1 | DFS-038: with server URL `/` the generated client called `//api/session` | item 6 (the server URL stays empty) |
| S2 | DFS-038: the token page `/` appeared in the spec | item 6 (only `/api/**`, `springdoc.paths-to-match`) |
| S3 | DFS-038: `ng-openapi-gen` wrote only functions until `--services true` | `archetype.md` "Where files go" (tag → `<Feature>Service` with `--services true`); item 6 (tag and operation IDs name the generated service) |
| S4 | DFS-038: a deleted `src/app/api/` was not regenerated (fixed by declaring it an output of `ngBuild`) | item 7 (keep `src/app/api/` an output of `ngBuild` if the ticket touches the frontend build) |
| S5 | DFS-038: the Vitest helper read the page before the generated service's Promise settled | item 7 (Promise-returning service methods; a component test lets a Promise settle) |
| S6 | DFS-040: the stream spacing and cancel-while-hashing tests depend on timing | item 10 (timing assertions, margins, measured once); `test-plan.md.tmpl` "Timing-sensitive" column; `test-guide.md` "Asynchronous states" |
| S7 | DFS-038 AC-2: a stale spec lacking a field the page used failed the frontend compile before `OpenApiSpecTest` ran | item 7 (does existing frontend code use a changed record); `archetype.md` "Where files go" (the frontend build must stay green with the regenerated client) |
| S8 | DFS-040 AC-8 read the committed spec in a story test; with the spec regenerated at design time it would pass against stubs and fail the red gate (found writing this archetype) | item 6 (spec assertions never in a story test); `test-guide.md` "The spec"; `test-plan.md.tmpl` (Level `spec (OpenApiSpecTest)`) |
| S9 | DFS-036/DFS-040: a stubbed controller shows the client only a `500`, which `RedCheck` does not count as a stub failure (found reading `RedCheck` for CCW-019 decision 3) | `test-guide.md` "How a stubbed endpoint fails its test" (`TestServer` and `StubSurfacingConfiguration`); `design.md.tmpl` "Stubs" |
| S10 | DFS-040 AC-7: a security test that only asserts `403` passes against stubs (found writing this archetype) | item 1 (a refused request leaves no side effect, and a test checks that); `test-guide.md` "How a stubbed endpoint fails its test" |
| F1 | DFS-040 AC-7: every new path refused without the cookie, with a foreign `Host`, and `POST`s without a matching `Origin` | item 1 (security on every new path); `design.md.tmpl` "Security matrix" |
| F2 | DFS-040 "Out of scope": the path-inside-roots rule does not apply to choosing a scan's roots | item 2 (paths inside a scan) |
| F3 | DFS-040 AC-10: `CLAUDE.md` and `ui.md` describe the scan API | item 12 (docs) |

## Checklist items and the rows they trace to

| Item | Rows |
|---|---|
| item 1 | S10, F1 |
| item 2 | F2 |
| item 3 | D4 |
| item 4 | D4 |
| item 5 | D7 |
| item 6 | D7, S1, S2, S3, S8 |
| item 7 | S4, S5, S7 |
| item 8 | D2 |
| item 9 | D3 |
| item 10 | D7, S6 |
| item 11 | D5 |
| item 12 | F3 |

No row traced to nothing, so the dry run found no gap the archetype does not ask about; and every checklist item traces to at least one real event, so none is cut. Item 2 traces only to an out-of-scope note: it is kept because U10 makes it a criterion of the first endpoint that reads a path inside a scan (DFS-036 decision 1), which no endpoint has done yet.

## What the dry run changed in the archetype

Writing these rows changed the archetype before this file was finished:
- S8 moved spec assertions out of story tests (item 6 and `test-guide.md` "The spec"); DFS-040's AC-8 test would have failed the red gate under the pipeline.
- S10 added the "no side effect after a refusal" step to every security test (`test-guide.md`), since a bare `403` assertion passes against stubs.
- S9 became the shared helper in `server`'s test tree (CCW-019 decision 5), checked against the real `RedCheck` (`PASS red CCW-019: 1 story tests fail with stubs`).
