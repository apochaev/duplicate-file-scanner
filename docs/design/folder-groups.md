# Folder groups: the Overlaps tab, redesigned

Status: G5 and G6 implemented (DFS-067, 2026-11-12); G2, G3 (tab) and G4 are DFS-068, G7 later. Ticketed as DFS-067 (API: G1, G3, G5, G6) and DFS-068 (tab: G2, G3, G4); open questions are tracked in those tickets. Replaces the Folders and Folder pairs tables of the Overlaps tab (DFS-050, `docs/design/ui.md` U3). Decisions are numbered G1–G7 so the ticket can cite them. Mockup: `docs/design/mockups/folder-groups-2026-11-12/index.html`.

## Goal

Sets answers "which files are duplicated, heaviest first". Overlaps answers the folder question: **these folders share these files; which folder keeps them?** Each row shows the folders, what they share, and how much of each folder that is. A folder whose every file is shared can go, as long as another folder in the row keeps the copies.

## What is wrong today (scan of `d:/`, 2026-11-12)

- **The same finding is shown four times.** `_AVP3481.auh`, `\Contents`, `\History` and `\resources` are four of the top ten Folders rows, each "5 files · 206.2 MB".
- **Most folder pairs are a folder paired with its own subfolder.** They repeat the Folders rows.
- **"Partial" says nothing about what is left over.**

## Decisions

### G1. A group is the duplicate sets whose copies sit in exactly the same folders

For each duplicate set, its **folders** are the folders that directly hold its paths, with how many copies each holds. Sets with the same folders (and the same copy count per folder) form one **group**. Only folders that directly hold files appear, never their parents, so a finding never repeats up the tree.

Hard links follow the Sets rules: a set's copies are its physical copies, and a hard link of the same copy adds no copy.

### G2. What a group shows

- **Files:** how many distinct contents are shared, and how many copies there are in total.
- **Reclaimable:** for each set, (physical copies − 1) × size, summed. This is the same figure Sets uses.
- **One line per folder**, with:
  - the short path (Sets' path rule, `displayPath`) and a copy-path button;
  - "×2" when the folder holds more than one copy of each shared file;
  - "**N of M files**", where N is the folder's paths in this group and M is the files directly in the folder (`ScanResult.folders`);
  - "**fully shared**" when N = M;
  - "**also in K other groups**" when some of the folder's other files are shared with different folders. Without this, a folder covered by two groups would look only partly shared in each.
- **Expanded:** the group's sets (file name, size, copies), first 20, then "and N more".
- **Two or more fully shared folders:** the group says "Fully shared folders: any one can go, not all."

### G3. Order, filtering and small groups

Groups are sorted by reclaimable bytes, largest first, then by their first folder's path. With a folder selected in the browser, only groups with at least one folder at or under it are shown (`under=`, as today). Groups under 1 MiB reclaimable are hidden behind "Show N small groups"; in the `d:/` scan that is every group but the first.

### G4. App-managed tag: frontend only

A folder line whose path contains an element matching a fixed frontend list gets a small **app** tag, with the tooltip "Managed by an application; deleting here can break it". The list starts as `*.auh`, `Adobe Premiere Pro Auto-Save`, `*.lrdata`, `*.photoslibrary` and `*.fcpbundle`, as a constant in the Overlaps component. There is no server change and no effect on sorting.

### G5. API

One new read-only endpoint in `us.pochaev.dfs.server.result`. It answers `409` `SCAN_NOT_COMPLETED` for an unfinished scan and follows the same path rules as `/summaries`:

`GET /api/scans/{id}/groups?under=&minBytes=&page=&size=` returns a page of groups in G3 order, plus `hiddenSmall` (the groups under `minBytes`; the default is 1 MiB, and 0 shows all). Each group has:

- `files`, `copies`, `reclaimableBytes`;
- `folders[]`: `folder`, `copiesEach`, `sharedFiles`, `folderFiles`, `sharedBytes`, `folderBytes`, `otherGroups`;
- `sets[]`: `digest`, `name`, `size`, `copies`; the first 20, plus `totalSets`.

`/summaries` and `/pairs` stay served, but the tab stops calling them. Removing them is a later cleanup. The Comparison block (pin a folder, select another, `/pair`) stays unchanged.

### G6. Computation

The groups are built once per scan, next to `OverlapAnalysis`. One pass over the sets computes each set's folder key (a sorted list of folder and copy count) and adds the set to its group. `otherGroups` is counted per folder while doing so. This costs one map entry per set, far less than the pairs table. JUnit cases come from the examples below.

### G7. Later

- **Roll-up of mirrored trees.** `Backup\2019` mirroring `Photos\2019` month by month gives 12 groups. A later ticket merges groups that are the same pair one level down into "`Backup\2019` ↔ `Photos\2019` · 12 subfolders" (interview, 2026-11-12: later).
- **Plan action per group:** "keep in ▼ folder, remove from the others", which maps to the plan API's `{keep, quarantineIn}` rule. This comes with the plan screen.

## Examples (from the `d:/` scan)

1. **5 files · 206.2 MB reclaimable**, the RAW files `_AVP3477`–`_AVP3481.ARW`.
   - `Photo\Import\2020-07 Camper Van\2020-07-09 Gemini Bridges`: 5 of 190 files.
   - `…\_AVP3481.auh\Contents\History\resources`: 5 of 12 files, app tag.
2. **1 file · 3 copies · 175.4 KB reclaimable**, `2020-08-21 Sunset GGBr.prproj`.
   - `Video\Processing\2020-08-20 Sunset GGBr`: 1 of 1 file, fully shared.
   - `…\Sunset GGBr\Adobe Premiere Pro Auto-Save` ×2: 2 of 7 files, app tag.
3. **1 file · 3.6 KB reclaimable**, the Garmin file.
   - `Video\Import\2020-06-23 Garmin Road trip\Garmin`: 1 of 1, fully shared.
   - `Video\Import\2020-07-28 Virb Road Trip\Garmin`: 1 of 1, fully shared.

   Each line is fully shared, but only one of the two folders can go.
4. **1 file · 319 B reclaimable.**
   - `Video\Import\2020-06-23 Garmin Road trip`: 1 of 2 files.
   - `Video\Import\2020-07-28 Virb Road Trip`: 1 of 1 file, fully shared.

## Open

Tracked in DFS-067 and DFS-068: the 1 MiB threshold, whether copies only inside one folder form a group, and hard links of one copy spread over two folders.
