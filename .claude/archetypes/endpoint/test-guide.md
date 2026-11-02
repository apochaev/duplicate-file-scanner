# endpoint — test-author guide

You write every test for this endpoint ticket against the architect's stubs, before the implementer writes any real logic. Every story test must fail against the stubs for one reason: the stub's `UnsupportedOperationException("not implemented: <TICKET-ID>")`. Stop once the tests compile and fail that way.

## How a stubbed endpoint fails its test

Story tests go through a real server over HTTP, because security and status codes are what endpoint criteria are about. A stubbed controller method throws inside the server and the client sees `500`. `TestServer` (in `server/src/test/java/us/pochaev/dfs/server`) loads `StubSurfacingConfiguration`, which records the exception behind every `500`; `TestServer`'s request helpers (`get`, `send`, `sendJson`, and `login` through `get`) rethrow it. So a test that sends a request to a stubbed method fails with the stub's exception itself, which the red gate counts (CCW-019, checked against the real `RedCheck`). Consequences:

- Make the request through `TestServer`'s helpers, not a bare `HttpClient`, wherever the stub can be reached. A stream request through a bare `HttpClient` (for line-by-line reading) must first touch a stubbed method through a helper, or its test fails with a status mismatch instead of the stub.
- Never catch the exception. `assertThrows` around a request swallows the stub (CCW-005.L-3).
- Security refuses a request before any controller runs, so a test that only asserts `403` *passes* against the stubs, and the red gate rejects a story test that passes. Make every security test also reach a stubbed method: create its fixture through the API (DFS-040 AC-7 starts a scan first) and check afterwards, through the API, that the refused request had no side effect (the list is unchanged). Both steps hit stubs, so the test fails red with the stub's exception and proves the absence of a side effect once implemented.

## Naming convention (D11)

Every test's `@DisplayName` starts with the full ID(s) of the criteria it proves, then a readable title: `@DisplayName("DFS-040.AC-1 A scan starts with 201 and Location, and completes")`. A `@ParameterizedTest` needs the ID in its `name` as well, so each invocation carries it: `@ParameterizedTest(name = "DFS-040.AC-2 {0} is refused with 400 {1}")`.

## The server and the session

- `try (TestServer server = TestServer.start()) { ... }`, or `TestServer.start(new Class<?>[]{SomeTestConfiguration.class}, "--some.property=value")` for bean overrides and properties. The browser opener is off and the port is random.
- Log in with `server.login(server.token(output.getOut()))`, the test class annotated `@ExtendWith(OutputCaptureExtension.class)` and the method taking a `CapturedOutput`.
- `server.origin()` is the matching `Origin` for state-changing requests; `http://evil.example` is a foreign one.
- A foreign `Host` needs a raw socket, because `HttpClient` does not let a caller set `Host`: `server.rawGet(path, "evil.example:" + server.port(), cookie)` returns the status.
- Parse JSON with Jackson 3 (`tools.jackson.databind.json.JsonMapper`), comparing trees, not strings.

## Asynchronous states

- Reach a state deterministically: override a bean by name in a `@TestConfiguration` (for scans, `HoldingExecutorConfiguration` replaces `scanExecutor` so every scan stays QUEUED; `ScannerAutoConfiguration` backs off for a bean with that name).
- A "still working" state comes from a fixture large enough to outlast the request (DFS-040: 24 files of 16 MB to cancel while hashing), polled with a deadline until RUNNING. The core's own tests pin its state machine; yours only need to land in the state.
- Wait with bounded polls (a deadline of seconds), never an unbounded loop or a bare `Thread.sleep` for a state.
- A timing assertion (stream spacing, "arrives within 1 second") gets a wide margin, and its row in the test plan says so. Measure once that it is not vacuous (DFS-040 printed the event times once: 9 events, 250-300 ms apart, against a 200 ms floor) and record the measurement in the test plan's Timing column; do not leave the print in.

## Streams

- Read a Server-Sent Events response with `HttpClient` and `HttpResponse.BodyHandlers.ofLines()`, a request timeout, and the `Cookie` header; collect each `data:` line under its `event:` name with its arrival time.
- Assert the content type (`text/event-stream`), the first event's timing, the spacing, the terminal event last, and that the stream ends; and the stream of an already finished resource (one event, then the end).

## The spec

A story test never reads `server/frontend/openapi.json` on its own: the architect regenerated it in the design phase, so a test that only reads it passes against stubs and the red gate fails it. A spec criterion gets a story test that first calls the endpoint (which reaches the stub, so the test is red) and then checks the response against the committed schema; `OpenApiSpecTest` (existing) still proves the committed spec matches the controllers (DFS-047).

## Cover, per scenario in the ticket

- The happy path and every stated edge case, exactly as the scenarios describe them. Do not invent scenarios; report a gap instead.
- Every checklist item the design doc's disposition commits to: the security matrix row by row, each error code, each status code and header.
- Files: `@TempDir` trees built in the test (CLAUDE.md, "Test fixtures"); compare paths as the API returns them (the folders as the caller gave them).

## What not to do

- Do not test a controller by calling its methods directly; go through the server.
- Do not touch production code, `TestServer` or `StubSurfacingConfiguration` (shared; changing them is its own ticket).
- Do not assert on log text beyond what a criterion names.
