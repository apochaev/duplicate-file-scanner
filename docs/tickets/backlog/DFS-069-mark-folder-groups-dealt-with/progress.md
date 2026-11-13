# DFS-069 progress

## design (architect)

Wrote `design.md`; added stub `server/frontend/src/app/results/result-done-groups.ts`; changed `overlaps-tab.ts`, `.html`, `.css` (checkbox, counts line, `done-groups` expander, `#groupCard` template; stub methods `loadMarks`, `isDone`, `toggleDone`, `countsText`, `doneTitle`). `:server:ngBuild` is green; `:server:npmTest` shows the Expected breakage listed in `design.md`. Expected commit: `feat(server): stub the dealt-with folder groups [DFS-069]`.
