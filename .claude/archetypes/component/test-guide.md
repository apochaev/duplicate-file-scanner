# component — test-author guide

You write every test for this screen ticket against the architect's stubs, before the implementer writes any real logic. Every story test must fail against the stubs for one reason: the stub's `Error` with the message `not implemented: <TICKET-ID>`. Stop once the tests compile and fail that way (`./gradlew :server:npmTest`; the report is `server/build/test-results/vitest/TEST-vitest.xml`).

## How a stubbed component fails its test

A stub reached from a template, from an event handler (a Material harness's `click()`), or in a Promise callback of the generated client does not fail the test by itself: Angular hands the error to its `ErrorHandler`, or the Promise rejects unhandled, and the test goes on to fail on an assertion, which the red gate rejects (CCW-020 decision 7, found with a probe). So every story spec:

- adds `provideStubSurfacing()` to `TestBed.configureTestingModule`'s providers, and
- calls `await settle(fixture)` from `src/testing/stub-surfacing.ts` after rendering and after every step that renders or lets Promises run (a harness action, a `flush`, a fake's event). `settle` lets Promises settle, waits for the fixture to be stable, renders, and throws the first recorded error, so the test fails right there with the stub's own `Error`.

Consequences:
- Never catch the error (`expect(...).toThrow()` around a step swallows the stub).
- Do not write a local settle function; the shared one is frozen with the specs and cannot be weakened by the implementer.
- A test that never reaches a stub passes against the stubs, and the red gate rejects a story test that passes. If a criterion's behavior does not go through a stub, report it to the architect rather than adding an artificial assertion.
- **A criterion satisfied by a configuration property alone** (a `server.*` setting such as the session timeout, DFS-066.L-1) has no stub to reach. Test it with a Java test in the server's test tree that fails on an assertion, and name it in the test plan with Level `java, property`. The architect lists such a criterion in `design.md`, so the red check expects that one test to fail on its assertion, not on `not implemented:`.
- Vitest still lists an error that Angular also threw as an "Uncaught Exception" under "vitest unhandled errors"; when its message is the ticket's stub, the red gate counts it as another test failing on the ticket's stub, which is accepted.

## Naming convention (D11)

The test's own name, given to `it`, starts with the full ID(s) of the criteria it proves, then a readable title. Enclosing `describe` blocks are fine: the report names a test `<describe> > <it>`, and the gate reads only the part after the last ` > ` (CCW-020 decision 6).

```ts
describe('ScanScreen', () => {
  it('DFS-041.AC-1 adds folders as chips, skips a repeated path and removes a chip', async () => { ... });
  it('DFS-041.AC-5, DFS-041.AC-8 lists the scans in the server order with their outcomes', async () => { ... });
});
```

Never `it.skip`, `xit`, `describe.skip` or `xdescribe` (the disabled check fails them).

## The test bed

Start from `skeletons/feature.spec.ts.tmpl`:

- `TestBed.configureTestingModule({ imports: [<Component>], providers: [provideHttpClient(), provideHttpClientTesting(), provideStubSurfacing(), <fakes behind tokens>] })`.
- `http = TestBed.inject(HttpTestingController)`, and `afterEach(() => http.verify())`, so an unexpected or missing request fails the test.
- Render: `fixture = TestBed.createComponent(<Component>)`, `fixture.detectChanges()`, answer the requests the component makes on creation (`http.expectOne('/api/scans').flush([...])`), then `await settle(fixture)`.
- A spec that bootstraps the shell (`App`, or any component hosting the router) also triggers the initial route's requests (e.g. `GET /api/scans`). Flush or match each one before `afterEach`'s `http.verify()`, or every such spec fails on the open request (DFS-066.L-3).
- Answer a request with its method and body checked: `const req = http.expectOne('/api/scans'); expect(req.request.method).toBe('POST'); expect(req.request.body).toEqual({...}); req.flush(body, { status: 201, statusText: 'Created' })`. A refusal: `req.flush({ code: 'RELATIVE_LOCATION', message: '...' }, { status: 400, statusText: 'Bad Request' })`.
- Build API models with small functions and defaults (`entry()`, `progress()` in `scan-screen.spec.ts`), so a test states only what it is about.
- Builders shared by several specs go in a non-spec file in the feature folder (`results/result-test-data.ts`, DFS-049); `tsconfig.app.json` compiles it too, so it uses no Vitest globals (`expect`, `vi`).

## Material harnesses

Drive Material components through their harnesses, never their internal DOM: `const loader = TestbedHarnessEnvironment.loader(fixture)`, then `MatButtonHarness.with({ text: 'Start' })`, `MatChipInputHarness`, `MatChipGridHarness`, `MatExpansionPanelHarness`, `MatInputHarness.with({ selector: '.min-size' })`, `MatButtonToggleHarness`, `MatCheckboxHarness`, `MatProgressBarHarness`, each from `@angular/material/<component>/testing`. `await settle(fixture)` after each harness action. Read plain text from the page by CSS class (`.scan-status`, `.form-error`), the classes `design.md` names.

A spec that selects a tab through `MatTabGroupHarness` and then `settle()`s provides `{provide: MAT_TABS_CONFIG, useValue: {animationDuration: '0ms'}}` in its own `providers`: in jsdom a newly selected tab's content attaches only after about 500 ms of real timers, longer than `settle()` waits. Otherwise the implementer can only turn the animation off in the product's template (DFS-050.L-3).

## Routes (DFS-049)

A spec rendering a component with a `routerLink`, or the routes themselves, provides `provideRouter([...])` with every route it navigates to; `RouterTestingHarness` needs the route in that list (an empty list fails with NG04002, not the stub's error). Call `TestBed.inject(Router).initialNavigation()` in `beforeEach`: the router starts listening to location changes only there, so without it `Location.back()` does not navigate (DFS-049 defect 1). A criterion about opening the page at a URL (a reload) sets the location first (`TestBed.inject(Location).go('/scans/s-1')`), then calls `initialNavigation()`, rather than navigating after the app has started.

## Browser APIs jsdom lacks

A wrapper's token gets a fake in the providers (`{ provide: EVENT_SOURCE, useValue: (url) => new FakeEventSource(url) }`), and the test drives it step by step: open, one event, a reconnect, a failure for good (DFS-041's `FakeEventSource`, with `readyState` following the real one). Assert what the fake was asked (the URL, that it was closed).

## A criterion about CSS only (DFS-063.L-2)

Render a component that reaches a stub first (the red gate rejects a test that reaches none), then read the `<style>` elements from `document`: Angular's test builder keeps the `@media` block there. To assert "no animation" use `/animation(-name)?\s*:(?!\s*none)/`. Do not use `/animation(-name)?\s*:\s*(?!none)/`: the `\s*` backtracks, so it matches `animation: none` and the test can never pass.

## Cover, per scenario in the ticket

- The happy path and every stated edge case, exactly as the scenarios describe them. Do not invent scenarios; report a gap instead.
- Every page state `design.md` lists, with the exact text it gives.
- The requests: method, URL, body (an untouched optional field absent), and that nothing else was sent (`http.verify()`).
- A "nothing is shown" criterion (no link on a RUNNING row) reaches no stub on its own: put its assertions in a test that first renders the positive case, which does reach one (DFS-049).
- A component with an `opacity: 0` or hover-revealed control gets a Playwright flow that repeats one row click at a viewport under 960 px, since `opacity: 0` does not remove an element from hit-testing and the other flows run at desktop width (DFS-060.L-2).
- **Drive the stream to every terminal status.** When a row's fields come from both the entry and the stream, tests drive the stream to each terminal status, not only an entry that starts terminal (DFS-063.L-3: a row completing in view showed an empty duration that only the by-hand run caught).
- **A new request must be answered everywhere.** When a change adds a request, every existing spec that reaches that state through the stream must answer it (a shared helper), or `afterEach` `http.verify()` fails and cascades (about 33 failures in DFS-063).

## What not to do

- Do not stop at "the text is in the DOM" for a layout-bound component. jsdom has no layout, so Vitest cannot see a 0 px column or a clipped name. Add a Playwright flow in `BrowserFlowTest` that asserts `isVisible()` and a bounding-box width above 0 on the component's key elements (the name, the path, the figure the user reads), and list it in the test plan (DFS-060.L-1).
- Do not write `expect(() => stub()).not.toThrow()` for a "never throws" scenario. It turns the stub's `not implemented` error into an AssertionError, and the red gate fails. Call the function bare, then assert on an observable afterwards (DFS-060.L-3).
- Do not test the generated client or Angular itself; test what the screen does with them.
- Do not touch production code, `src/testing/` or `src/app/api/`.
- Do not write files with a shell heredoc: TypeScript is full of apostrophes and backticks, which break it (DFS-040, DFS-041); use the editor.
- Do not assert on a style or a font: jsdom computes neither from the stylesheets. A look is checked by hand on the built jar (checklist item 11), and its criterion listed in the test plan with Level `by hand on the built jar`.
- Do not write a Windows path in a string literal with single backslashes: `'D:\Backup'` is `'D:Backup'` in JavaScript. Double them (`'D:\\Backup'`) or use `String.raw`. The red run cannot catch this, since the test fails on the stub either way; it shows up only at implement's green (DFS-050.L-2).

## The by-hand check without a person (DFS-049)

The verifier can run checklist item 11 headlessly: start Chrome with `chrome --headless=new --remote-debugging-port=<port> --user-data-dir=<scratch folder>`, then drive it from the project's Node under `server/.gradle/nodejs` (it has a global `WebSocket` and `fetch`) over the DevTools protocol, using `Runtime.evaluate` to click and to read `textContent`. Caveats:
- A scan started with `fetch('/api/scans')` does not appear in a list already loaded; reload the page first.
- `navigator.clipboard.writeText` rejects in an unfocused headless page ("Document is not focused"), which exercises the failure path. `Emulation.setFocusEmulationEnabled` plus `Browser.grantPermissions` with `clipboardReadWrite` makes it succeed, and `readText()` confirms the copied text.
