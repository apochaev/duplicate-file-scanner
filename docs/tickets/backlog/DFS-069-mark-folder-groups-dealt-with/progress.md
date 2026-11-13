# DFS-069 progress

## design (architect)

Wrote `design.md`; added stub `server/frontend/src/app/results/result-done-groups.ts`; changed `overlaps-tab.ts`, `.html`, `.css` (checkbox, counts line, `done-groups` expander, `#groupCard` template; stub methods `loadMarks`, `isDone`, `toggleDone`, `countsText`, `doneTitle`). `:server:ngBuild` is green; `:server:npmTest` shows the Expected breakage listed in `design.md`. Expected commit: `feat(server): stub the dealt-with folder groups [DFS-069]`.

## implement

Implemented the five helpers in `result-done-groups.ts` and the five stubbed methods in `overlaps-tab.ts` (group key uses `GroupFolderView.folder`); updated the Overlaps sentence in `CLAUDE.md` and added a DFS-069 entry to `docs/design/ui.md` U3. `./gradlew build` green. Expected commit: `feat(server): mark folder groups as dealt with [DFS-069]`.
