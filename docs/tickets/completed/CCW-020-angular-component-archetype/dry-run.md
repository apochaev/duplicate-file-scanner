# CCW-020 dry run: the component archetype against DFS-041

CCW-020 decision 3: walk the archetype (`.claude/archetypes/component/`, version 1) against DFS-041's ticket and code, and against the surprises met building DFS-037, DFS-038 and DFS-041 (listed in CCW-020's Context, S1 to S10, plus S11, found while building this archetype). Each row names what the archetype would have raised it with: a checklist item of `archetype.md` ("item N"), a section of `archetype.md`, or a section of a guide or template. A row with nothing to trace to would have been a gap; a checklist item no row traces to would be cut. This reading is half of decision 3; the other half, the real gate run on a throwaway ticket, is in `verification.md`.

## Decisions and surprises traced

| Row | Event (source) | Traced to |
|---|---|---|
| D1 | DFS-041 decision 1: folders typed as absolute paths, each a chip; the API's codes shown next to the field | item 6 (input and validation: how each value is entered, where a server code shows); item 7 (a `400` with the server's `{code, message}`) |
| D2 | DFS-041 decision 2: one page, no router; routing comes with deep links and needs a server fallback | item 5 (where the screen sits; a route needs routing and the server's fallback); `archetype.md` "Where files go", the page shell; item 13 (`ui.md` describes the screen) |
| D3 | DFS-041 decision 3: both settings in a closed "Settings" panel; an untouched setting left out of the request; a size below 1 byte refused in the form | item 6 (what the form refuses, what it sends for an untouched field); item 1 (an optional setting left alone is left out); item 9 (the expansion panel and button toggle, driven by harnesses) |
| D4 | DFS-041 decision 4: `archetype: none`, built by hand, because no component archetype existed | `archetype.md` "Applies to" (this archetype now fits such a ticket); first live use on the next screen ticket |
| D5 | DFS-041 decision 5: the stream through `ScanEventsService`, its `EventSource` behind the `EVENT_SOURCE` token so Vitest supplies a fake | item 4 (browser APIs jsdom lacks, behind a wrapper with an `InjectionToken`); item 8 (live updates: open, close, reconnect, given up); `archetype.md` "Where files go" (the one way around the generated client); `test-guide.md` "Browser APIs jsdom lacks" |
| D6 | DFS-041 decision 6: the scan screen shows only when connected; without a session no scan call | item 5 (when it shows); item 7 (without a session, `403`) |
| S1 | DFS-038: the generated client returns Promises; the Vitest helper read the page before they settled | item 2 (Promise timing in tests, `settle(fixture)`); `test-guide.md` "The test bed" |
| S2 | DFS-038: `src/app/api` is generated, git-ignored, regenerated on every build, test and serve, never edited | item 1 (the generated client; `src/app/api/` untouched); `archetype.md` "Where files go"; `impl-guide.md` "When you are done" |
| S3 | DFS-041 decision 5: the SSE stream is the one call outside the generated client, through an injectable wrapper, because jsdom has no `EventSource` | item 4; item 8; `archetype.md` "Where files go" |
| S4 | DFS-041: Material component harnesses drive the tests | item 9 (Material components and their harnesses); `test-guide.md` "Material harnesses" |
| S5 | DFS-041: the initial bundle (560 kB) exceeded the 500 kB warning budget, fixed by DFS-044 | item 10 (the bundle budget: a new module or dependency against the 1 MB warning, the build output checked); `archetype.md` "Extra done-items" (no budget warning) |
| S6 | DFS-041: no web font was loaded, so Material fell back to a serif, fixed by DFS-044 | item 10 (the system font through the theme's typography tokens, no web font); `impl-guide.md` "Conventions", Material (no `font-family`) |
| S7 | DFS-041: browser screenshots of a background window lagged behind the DOM, so the live check read the DOM | item 11 (the live check reads the DOM); `archetype.md` "Extra done-items" |
| S8 | DFS-037: `ng serve` outlives its Gradle task and must be killed by PID or port | item 12 (the dev server: stop it by the PID on port 4200) |
| S9 | DFS-040: the dev proxy rewrites `Origin` so `POST`s pass the server's check | item 12 (the proxy rewrites `Origin`; `127.0.0.1`, not `localhost`) |
| S10 | DFS-040, DFS-041: shell heredocs break on apostrophes in TypeScript and templates | `impl-guide.md` "Writing files"; `test-guide.md` "What not to do" |
| S11 | CCW-020 decision 7: a stub reached from a template, an event handler or a Promise callback did not fail its test; it failed on an assertion and the stub showed only as an unhandled error | item 3 (stubs reachable and surfaced); `test-guide.md` "How a stubbed component fails its test" (`provideStubSurfacing()`, `settle(fixture)`); `archetype.md` "Where files go" (`src/testing/` frozen) |
| F1 | DFS-041 AC-4, AC-9: the busy server's text, the load error, "Live progress lost; reload the page" | item 7 (loading, empty and error states); item 8 (what the screen shows when the browser gives up) |
| F2 | DFS-041 docs commit: `CLAUDE.md`'s `server` entry and `ui.md` U2 describe the scan screen | item 13 (docs) |

## Checklist items and the rows they trace to

| Item | Rows |
|---|---|
| item 1 (the generated client) | D3, S2 |
| item 2 (Promise timing in tests) | S1 |
| item 3 (stubs reachable and surfaced) | S11 |
| item 4 (browser APIs jsdom lacks) | D5, S3 |
| item 5 (where the screen sits and when it shows) | D2, D6 |
| item 6 (input and validation) | D1, D3 |
| item 7 (loading, empty and error states) | D1, D6, F1 |
| item 8 (live updates) | D5, S3, F1 |
| item 9 (Material components and their harnesses) | D3, S4 |
| item 10 (the bundle budget and the system font) | S5, S6 |
| item 11 (the live check reads the DOM) | S7 |
| item 12 (the dev server and its proxy) | S8, S9 |
| item 13 (docs) | D2, F2 |

## Result

Every DFS-041 decision (D1 to D6) and every surprise (S1 to S11) traces to at least one checklist item or guide section, and every checklist item (1 to 13) is used by at least one row. A script checks this mechanically (`verification.md`, AC-11). The archetype would have raised all of DFS-041's design questions before its code was written. The one it would have raised that DFS-041 never met is S11: DFS-041 was built by hand, without stubs, so its tests never had to fail on one.
