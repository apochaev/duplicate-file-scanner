# DFS-044 verification

## Product owner review

What to look at:
- `server/frontend/angular.json`: the `initial` budget is now 1MB warning, 2MB error. `anyComponentStyle` is unchanged.
- `server/frontend/src/styles.css`: a new `html` block sets Material's 15 `--mat-sys-*-font` tokens to `system-ui, sans-serif`.
- To see it yourself, run `./gradlew :server:bootRun`, open the link it logs, and check that the Start button, Settings and "Folders to scan" are in the system font, not a serif.

Verdict per criterion: AC-1 pass (checked mechanically), AC-2 pass (checked by hand), AC-3 pass (checked by the build).

## Checks

All on branch `tickets/DFS-044-frontend-budget-and-font`, commit 7669276, 2026-11-01.

### AC-1: no budget warning (mechanical)

- I ran `./gradlew build --rerun-tasks`; it succeeded.
- The ngBuild output reports `Initial total | 560.70 kB`, under the new 1MB warning.
- I searched the build log for "budget": 4 hits, every one the worktree's own path (`...-frontend-budget-and-font`). With that path removed from the log, the count is 0.
- Between `Task :server:ngBuild` and the next task there is no line matching `warn` or `exceed`.
- `angular.json` reads `"maximumWarning": "1MB"`, `"maximumError": "2MB"` for the `initial` budget.

Note for anyone repeating this check: a plain search for "budget" will hit this worktree's path. Search with the path removed, or run the check from `main`.

### AC-2: Material text uses the system font (by hand)

I ran the built jar (`server/build/libs/dfs-server.jar`, with `--dfs.server.open-browser=false`), opened the session link in Chrome, and read computed styles through the page's DOM (not from a screenshot). The page showed "Connected".

| Element | Text | Computed `font-family` |
|---|---|---|
| Start button label | Start | `system-ui, sans-serif` |
| Settings panel title | Settings | `system-ui, sans-serif` |
| Folder field label | Folders to scan | `system-ui, sans-serif` |

- `--mat-sys-body-medium-font` on `html` reads `system-ui, sans-serif`.
- All 15 `--mat-sys-{body,display,headline,label,title}-{large,medium,small}-font` tokens read the same.
- No element on the page has a computed `font-family` containing `Roboto` (count 0).

### AC-3: the unit tests still pass (build)

Vitest in the same build: 2 test files, 21 tests, all passed.

## Definition of done

- [x] Each scenario checked as the note above the scenarios says, recorded here as mechanical or by hand
- [x] `./gradlew build` is green (includes the ArchUnit and module-export tests)
- [x] No files changed outside the paths listed under Touches (`git diff main --stat` on code: `angular.json`, `styles.css`; plus this ticket's own folder)
- [x] `verification.md` opens with the `## Product owner review` section
