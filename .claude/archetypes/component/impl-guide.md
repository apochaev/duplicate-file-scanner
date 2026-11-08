# component — implementer guide

You are making the architect's stubs pass the tests the test-author already wrote and froze. The tests do not change (the verifier checks this with `git diff`, and the gate's freeze check covers every `*.spec.ts` and `src/testing/`); if a test looks wrong, stop and report it as a defect instead of editing it.

## Before you start

- Read the ticket, `design.md` (this story's filled-in `design.md.tmpl`), and the failing specs. The design doc's "Components and files", "Page states", "API calls" and "Checklist disposition" sections are your spec.
- Every stub you touch throws `new Error('not implemented: <TICKET-ID>')`. Replace the body; leave no `not implemented:` in any non-spec `.ts` or `.html` file under `server/frontend/src` (`StubsCheck`).
- Do not change a component's selector, an `input()`/`output()`, an injected dependency, or a CSS class or element the tests look for: the tests were written against them. If one must change, report it.

## Conventions

- **Signals for state.** `signal` for what the component owns, `computed` for what derives from it, `linkedSignal` for a value reset by an input but updated locally (DFS-041's row progress). Update with `set`/`update`, never by mutating an array or object in place.
- **The generated client.** Await the injected service's method and handle both outcomes, as DFS-041's `ScanForm` does: `try { const entry = await this.scans.startScan({ body }); ... } catch (failure) { ... }`. A refusal is an `HttpErrorResponse` whose `error` is the server's `{code, message}`; show its `message`, and map a known status or `code` (`503`, `EXECUTOR_REJECTED`) to the design's text. Leave an optional field out of a request when the user left it alone.
- **Streams and wrappers.** Subscribe in `ngOnInit` only when the resource is still changing; unsubscribe on destroy (`inject(DestroyRef).onDestroy(() => this.stream?.unsubscribe())`, as DFS-041's `ScanRow`); the wrapper closes the browser object on terminal status and on unsubscribe (DFS-041's `ScanEventsService`).
- **Templates.** `@if`/`@for (...; track id)`/`@empty`; `role="alert"` on error messages; labels on regions and fields (`aria-label`, `mat-label`); `<time [attr.datetime]>` for times. Text exactly as `design.md`'s "Page states" gives it: the tests compare it.
- **Material.** Import each component's module in the component that uses it (`MatChipsModule`, `MatExpansionModule`, `MatButtonToggleModule`, ...). Text styles come from the theme's typography tokens; do not set a `font-family` (DFS-044).
- **Tabs that load data.** Content placed directly inside a `mat-tab` is created once and only detached when another tab is selected, so a component that loads on input changes keeps requesting while hidden. Wrap it in `<ng-template matTabContent>`: it is created when its tab is shown and destroyed when left (DFS-050.L-4).
- **Formatting.** Pure helpers in a `<feature>-format.ts` (DFS-041's `statusLabel`, `formatBytes`), so a component stays declarative and the helper is testable on its own.

## Writing files

Write `.ts`, `.html` and `.css` files with the editor (Write or Edit), never with a shell heredoc: TypeScript and templates are full of apostrophes and backticks, and a heredoc in this environment's Bash fails on them with "unexpected EOF" (DFS-040, DFS-041).

## When you are done

- `./gradlew build` is green: it builds the frontend (`ngBuild`), runs the Vitest tests (`npmTest`), the server tests, the ArchUnit tests and `ModuleExportsTest`. `./gradlew :server:npmTest` alone runs only the Vitest tests and is quicker while working.
- The build output has no budget warning (search it for "budget").
- No stub markers remain (`StubsCheck`).
- You have not edited a spec, `src/testing/`, `src/app/api/`, or anything outside the ticket's `touches`.
- For a layout fix, measure bounding boxes or take a screenshot of the built jar before reporting it done. "Build green, e2e passing" does not prove a visual defect is fixed (DFS-060.L-1).
- Leave the done-items unticked; the verifier ticks each one it has verified.
- If you deviated from `design.md` for a reason the architect did not anticipate, add a line to `learnings.md` (D6) rather than only fixing it silently.
