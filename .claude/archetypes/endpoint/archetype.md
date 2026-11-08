---
name: endpoint
version: 4
phases: [design, test, implement, verify]
gates: [design, done]
models:
  architect: {model: sonnet, effort: high, maxTurns: 25}
  verifier: {model: opus, effort: medium, maxTurns: 30}
  implementer: {model: sonnet, effort: medium, maxTurns: 40}
  test-author: {model: sonnet, effort: medium, maxTurns: 40}
  retro: {model: sonnet, effort: low, maxTurns: 15}
---

# endpoint

Applies to a ticket that adds or changes HTTP endpoints under `/api` in the `server` subproject: controllers, the API's own request and response records, their error codes, a Server-Sent Events stream, state the server keeps next to the core (such as a registry of scans), and the resulting change to the committed OpenAPI spec. Example: DFS-040, the scan API (start, list, get, cancel, progress stream).

It does not apply to:
- a new core use case or its service (`use-case`); a ticket that needs one *and* exposes it is two tickets, the `use-case` one first, or a draft in the backend lane added to the ticket's `depends_on`;
- Spring wiring or a composition root (`wiring`);
- an Angular screen (`component`, CCW-020); a ticket that only regenerates the client for a screen is part of that screen's ticket;
- the security rules themselves (DFS-036 built them by hand; changing them is its own ticket).

Written from DFS-036 (security), DFS-038 (the spec chain) and DFS-040 (a data endpoint family with errors, a registry and a stream), all built by hand because no archetype fit; proven by a dry run against DFS-040 (CCW-019 `dry-run.md`). Its first live use is the next endpoint ticket.

## Where files go

- **One package per feature** under `us.pochaev.dfs.server`, for example `us.pochaev.dfs.server.scan` (DFS-040): the controller, any state the server keeps (a registry), and the API's records. Classes are package-private except the records, which are `public` so Jackson and springdoc read them. State one feature package needs from another may sit in a public class exposing the narrowest public method (DFS-047: `ScanRegistry.completedState`).
- **The API's own records, never core types.** Request and response bodies are records in the feature package (`StartScanRequest`, `ScanEntry`, `ScanProgress` in DFS-040) that map from the core's `domain.model` types. A core type in a signature puts the core's shape into the spec and the generated client. The server's `ArchitectureTest` (nothing under `us.pochaev.dfs.scanner` outside `domain`) must still pass.
- **Errors:** `ApiError(code, message)` is the body of every refused request, raised as `ApiException(status, code, message)` and mapped by an `@RestControllerAdvice`. They live in `us.pochaev.dfs.server.api` since DFS-047, and the advice is scoped to `basePackages = "us.pochaev.dfs.server"`, so a new feature package needs no edit to it.
- **Naming for the spec and the client:** `@Tag(name = "<feature>")` on the controller names the generated service (`ng-openapi-gen --services true`: tag `scans` → `ScansService`); `@Operation(operationId = "<verbNoun>")` on each method names its method; `@ApiResponse` lists each status with its schema; `produces`/`consumes` say JSON (an endpoint without `produces` shows as `*/*` in the spec, DFS-038).
- **Stubs**, owned by the architect: each controller method's body throws `new UnsupportedOperationException("not implemented: <TICKET-ID>")`; its signature, mapping and every annotation are final, since they define the spec. Records are not stubbed: write their components and `@Schema` annotations. A registry or stream class the design introduces gets the same stub per method. Copy `skeletons/FeatureController.java.tmpl` and `skeletons/ApiRecord.java.tmpl`, replacing every `<...>`.
- **The spec, owned by the architect:** after writing the stubs, run `./gradlew :server:updateOpenApi` and commit the regenerated `server/frontend/openapi.json` with the design. `OpenApiSpecTest` then passes from the red run on, and the spec is part of what the human reviews at the design gate. The frontend build (which runs inside `:server:test`) must stay green with the regenerated client: if a changed record breaks frontend code, the design fixes that code too, and the ticket's `touches` names it.
- **Tests** go in `server/src/test/java/us/pochaev/dfs/server` (see `test-guide.md`); the shared helpers `TestServer` and `StubSurfacingConfiguration` already exist there.
- **Expected breakage run** (CCW-024): after the stubs compile, run `./gradlew :server:test --continue` once (an endpoint reaches only `server`; its test task also builds the frontend), keep only the failing classes from its output, and check each one's report for `not implemented: <TICKET-ID>`; declare the others under `design.md`'s `## Expected breakage` (`architect.md` step 4). Do not search the test trees for callers instead.

## Edge-case checklist

Work through every item; mark one that does not apply with a one-line reason in `design.md`'s "Checklist disposition".

1. **Security on every new path** (DFS-036, DFS-040 AC-7). Is every path under `/api`, so it needs the session cookie? Does every state-changing method (`POST`, `PUT`, `PATCH`, `DELETE`) rely on the `Origin` check, and is that tested? Is the foreign `Host` tested on at least one new path? Does a refused request leave no side effect (nothing started, nothing cancelled), and does a test check that? No CORS headers.
2. **Paths inside a scan** (U10, DFS-036 decision 1). Does an endpoint take a path that must lie inside a scan's roots (a folder listing, a preview, a plan entry)? Then a path outside them answers `403`, with a criterion for it. Choosing the roots themselves (starting a scan) is exempt (DFS-040, out of scope).
3. **Input validation and error codes** (DFS-040 decision 4). Which inputs does the server refuse before calling the core, with which codes (`RELATIVE_LOCATION`, `INVALID_MIN_SIZE`)? Which core `Result.Failure` codes pass through, with the core's message? Which status for each (`400` input, `404` unknown ID, `503` executor rejected)? Optional fields: which defaults, taken from where (`ScanConfig.defaults()`)?
4. **Status codes and headers** (DFS-040 AC-1, AC-5, AC-6). `201` with `Location` for a created resource; what an operation on a finished resource answers (DFS-040: cancel on a completed scan is `200`, unchanged); an unknown *or malformed* ID is `404` with `UNKNOWN_<THING>` (take the ID as a `String` and parse it, so a malformed one is not Spring's `400`).
5. **The API records** (DFS-040 decision 7, DFS-038). Which records, mapped from which core types? Enum-like values as strings with `allowableValues`; nullable fields marked `nullable`; required fields marked `requiredMode = REQUIRED` (springdoc does not mark record components required on its own); times as `Instant`. A field whose meaning changes over a resource's life is documented on the record (DFS-040: `groupsTotal` counts duplicate sets once a scan is COMPLETED). A nullable record-typed component (`@Schema(nullable = true)` on a nested record) comes out as `"type": "null"` beside the `$ref`; `ng-openapi-gen` turns it into an optional field without `| null` while Jackson sends `null`, so a screen reading it uses `?.` (DFS-048).
6. **The spec** (DFS-038, CCW-019 decision 3). Tag and operation IDs (they name the generated service and methods); only `/api/**` appears (`springdoc.paths-to-match`; without it the token page `/` appeared, DFS-038); the server URL stays empty (with `/` the client called `//api/...`, DFS-038); the architect regenerates the spec in the design phase; spec assertions belong in `OpenApiSpecTest` or a non-story test, never in a story test, which would pass against the stubs once the spec is regenerated.
7. **The generated client** (DFS-038). What `ng-openapi-gen` produces for each operation: Promise-returning service methods (a component test must let a Promise settle before checking the page, DFS-038), and for a `text/event-stream` operation a method that cannot read a stream (DFS-040: the screen uses `EventSource` with the generated data type). Does any existing frontend code use a record this ticket changes, and does the design fix it? Is `server/frontend/src/app/api/` still regenerated when missing (it is an output of `ngBuild` since DFS-038's fix; keep it so if the ticket touches the frontend build)?
8. **State kept in `server`** (DFS-040 decision 2). What does the server keep beside the core (a registry), for how long (this server run), and is it thread-safe? Is anything a background thread writes created *before* the core can call it (DFS-040 creates the listener before `StartScan.start`, so no event is lost)? Ordering of lists (newest first)? When the ticket reuses a predecessor's state mechanism (DFS-047's `ScanRegistry.completedState`, in package `scan`), where does it live, and can a criterion on it still fail red from the test location the ticket names? If not, move the test rather than adding a layer only for the red gate (DFS-048).
9. **Streams** (DFS-040 decision 3). For a Server-Sent Events endpoint: the event name and data schema, documented as `text/event-stream` in the spec; the first event on connect (the current state); throttling (at most every 250 ms, only when changed, the terminal event included); the stream ends after the terminal event; reconnect semantics (DFS-040: none needed, the first event is the current state); no timeout or which one; shutdown. A core listener only writes memory and never throws (the core detaches a listener that throws); no client can hold up the scanning thread.
10. **Asynchronous states in tests** (DFS-040 decision 7). How does each test reach each state deterministically: a bean overridden by name in a test configuration (`HoldingExecutorConfiguration` keeps scans QUEUED), a fixture large enough to still be working (a 384 MB tree to cancel while hashing)? Which assertions depend on timing, with what margin, and is it measured once that a timing check is not vacuous (DFS-040: 9 events 250-300 ms apart against a 200 ms floor)? Bounded polls with deadlines only.
11. **The dev proxy** (DFS-037 decision 4, DFS-040 decision 5). Do the new endpoints work through `ng serve` (state-changing requests pass because the proxy rewrites `Origin`; streams pass through)? Checked once by hand, with `bootRun --args=--server.port=8080` and `npm_run_start`.
12. **Docs** (DFS-040 AC-10). The `CLAUDE.md` `server` entry lists the endpoints; `docs/design/ui.md` names them where the screen that uses them is described.

## Extra done-items

Beyond the standard checks (every scenario automated and passing, `./gradlew build` green including ArchUnit and module-export tests, no files changed outside `touches`):

- Every checklist item is addressed in `design.md` or marked not applicable with a one-line reason.
- `server/frontend/openapi.json` was regenerated in the design phase and `OpenApiSpecTest` passes; the generated client compiles and the frontend build is green.
- Every new path has a test for the missing cookie, and every new state-changing method a test for a missing or foreign `Origin` (checklist item 1).
- Started by hand once: each new endpoint exercised with `curl` against `bootRun`, a stream followed to its end, the server log free of warnings and errors; and once through the dev proxy if the ticket adds a state-changing method or a stream (checklist item 11).
