# DFS-069 learnings

- DFS-069.L-1 (component archetype, checklist item 3): a stub reached from a lazily rendered component's template makes specs that never render it fail too. Here `overlaps-tab.spec.ts` failing 22 tests left the shared test environment so bad that `app.routes.spec.ts` and `session-ended.spec.ts` (which pass alone) timed out in the full run. The checklist asks to list specs that "fail with another error"; it should also say to run the whole suite once and compare with a run that excludes the stubbed spec, to tell collateral timeouts from real ones.
