# DFS-063 learnings

- component archetype: the architect cannot delete a module whose spec still exists (scan-phase.ts and scan-phase.spec.ts), since one broken import fails the whole Vitest bundle. The archetype could say so: "a module the ticket retires stays until the test-author deletes its spec; the implementer then deletes it".
- component archetype, checklist item 3: a CSS-only criterion (AC-15, reduced motion) needs a spec that first renders something reaching a stub, or the red gate rejects it; worth one line in the checklist.
- test phase: AC-15 (reduced motion) is tested by reading the component's `<style>` elements from `document` after a render; it is unverified that Angular's unit-test builder keeps the media block in those styles (the stub CSS has none), so the implementer may hit a green-phase surprise; if so, the test needs another way to read the stylesheet.
- test phase: the test-guide has no pattern for a CSS-only criterion; the one used here (render first, then read document styles) could be a guide entry once it is proven at green.
