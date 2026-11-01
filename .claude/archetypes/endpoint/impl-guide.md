# endpoint — implementer guide

You are making the architect's stubs pass the tests the test-author already wrote and froze. The tests do not change (the verifier checks this with `git diff`); if a test looks wrong, stop and report it as a defect instead of editing it.

## Before you start

- Read the ticket, `design.md` (this story's filled-in `design.md.tmpl`), and the failing tests. The design doc's "Endpoints", "Security matrix", "API records", "State and threads" and "Checklist disposition" sections are your spec.
- Every stub you touch throws `new UnsupportedOperationException("not implemented: <TICKET-ID>")`. Replace the body; leave no `not implemented:` anywhere under `src/main` (`StubsCheck`).
- Do not change a mapping, a record component or an annotation that shapes the spec: the architect regenerated `server/frontend/openapi.json` from them, and `OpenApiSpecTest` fails if they drift. If one must change, run `./gradlew :server:updateOpenApi`, commit the file, and add a line to `learnings.md` saying why.

## Conventions

- **Validate, then call the core.** Refuse bad input with `throw new ApiException(HttpStatus.BAD_REQUEST, "<CODE>", "<message>")` before any port is called, so a refused request has no side effect. Map a core `Result.Failure` to `ApiException(<status>, failure.code().name(), failure.message())` with the status the design gives (DFS-040: `400`, `503` for `EXECUTOR_REJECTED`).
- **IDs as strings.** Take a path ID as `@PathVariable String id` and parse it yourself; an unparseable ID is the same `404 UNKNOWN_<THING>` as an unknown one.
- **Map core types to the API's records** in the feature package; never return a core type.
- **Created resources** answer `ResponseEntity.created(URI.create("/api/<feature>/" + id)).body(entry)`.
- **State the server keeps** is thread-safe (concurrent maps, `volatile` fields written by one thread); anything a core callback writes is created before the core can call it (DFS-040: the listener exists before `StartScan.start`).
- **Core listeners** only update memory, never block and never throw (the core detaches a listener that throws).
- **Streams** use Spring MVC's `SseEmitter`: send the current state when the stream opens (complete at once if it is already terminal), then on one shared scheduled tick send when the state changed and the minimum interval has passed, the terminal event last, then `complete()`. Remove a subscription on completion, timeout and error; shut the scheduler down and complete every emitter in a `@PreDestroy`. Send with an event name and JSON data: `SseEmitter.event().name("<name>").data(value, MediaType.APPLICATION_JSON)`.
- **No core types in the spec, no logging of request bodies**, no new security rules (DFS-036's filter and Spring Security configuration cover `/api/**` already).

## When you are done

- `./gradlew build` is green: the server tests, `OpenApiSpecTest`, the frontend build and its Vitest tests, the ArchUnit tests and `ModuleExportsTest`.
- No stub markers remain (`StubsCheck`).
- You have not edited a test file, `TestServer`, `StubSurfacingConfiguration`, or anything outside the ticket's `touches`.
- Leave the done-items unticked; the verifier ticks each one it has verified.
- If you deviated from `design.md` for a reason the architect did not anticipate, add a line to `learnings.md` (D6) rather than only fixing it silently.
