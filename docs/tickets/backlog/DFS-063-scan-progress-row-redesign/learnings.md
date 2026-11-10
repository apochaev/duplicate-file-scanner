# DFS-063 learnings

- component archetype: the architect cannot delete a module whose spec still exists (scan-phase.ts and scan-phase.spec.ts), since one broken import fails the whole Vitest bundle. The archetype could say so: "a module the ticket retires stays until the test-author deletes its spec; the implementer then deletes it".
- component archetype, checklist item 3: a CSS-only criterion (AC-15, reduced motion) needs a spec that first renders something reaching a stub, or the red gate rejects it; worth one line in the checklist.
