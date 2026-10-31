# Folder overlap analysis

Status: design notes, not scheduled. Settled while refining DFS-030 (2026-10-31), before DFS-030 was re-scoped from "show which folders overlap" to "export a complete scan for analysis". Folder-level analysis moved out of the scanner, to a separate tool or app that reads the scan export. These decisions are kept here for that tool.

## Why it moved

A folder-level view ("what does `Backup/` overlap with?", "can `Old-laptop/` go?") is analysis. It wants interactive querying, slicing and possibly graph storage, and it changes faster than the scanner should. The scanner's job is to produce a complete, trustworthy record of a scan (DFS-030's export); the analysis tool computes views from it.

The one piece that must stay in the core is deletion safety: DFS-022's whole-plan "never delete the last copy" check works from `DuplicateSet`s directly and does not need the analysis view.

## Input

DFS-030's JSONL export: a `scan` header (roots, settings, start time, schema version), one `folder` line per folder that directly holds counted files (files and bytes), and `set` lines whose `copies` group hard-linked paths into one physical copy, with each path's size and modification time.

## Decisions

1. **Two views: a per-folder summary, and pair records that carry both directions.**
   - **Folder summary** `(folder, totalFiles, filesWithCopyElsewhere, bytes, internalDuplicateFiles, internalBytes)`, one per folder. It answers "can this folder go?": how many of its files have a copy in *any other* folder. This catches coverage spread over several folders, which no single pair shows. Example: `Old-laptop/Pictures` holds 1,000 files, 600 with a copy in `Photos/2019` and 400 with a copy in `Backup/2019`. It is 1,000 / 1,000 redundant, yet no pair shows more than 600 / 1,000.
   - **Pair** `(a, b, aSide, bSide)`, one per folder pair, with `a` < `b` by path, so each pair appears once. Each side holds that folder's files with a copy in the other folder, its total and the bytes. It answers "where are the copies?". One side is CLAUDE.md decision 1's directed "which files in B have a copy in A". Example: `Photos/2019` (400 files) and `Backup/2019` (340 files, all copies of files in `Photos/2019`) give one record, with `Backup/2019` at 340 / 340 (full) and `Photos/2019` at 340 / 400 (partial).
   - **A file with copies in *n* folders** counts in every one of the *n(n−1)/2* pairs it touches, and once in each of those folders' summaries.
   - **Pairs are never combined into a verdict.** Two folders that are each 100% covered by the other cannot both be deleted. Only a whole-plan check (DFS-022) can say what is safe.
2. **A copy elsewhere** is a path in a different folder that belongs to a different physical copy of the same set. A hard link of the same file in another folder is the same data and does not count (CLAUDE.md, "Hard links are not duplicates").
3. **Roll-up: a summary for every folder counting its whole subtree, pairs only between immediate folders, and a query for any two folders.**
   - **Summaries cover every folder** from each scanned root down, including folders that hold only subfolders, counting the whole subtree. Totals come from the export's direct per-folder counts, summed up the tree.
   - **For a folder's summary, a copy elsewhere means a copy outside its subtree.** Example: `Backup/2019/a/x.jpg` and `Backup/2019/b/x.jpg` are identical. In the summaries of `Backup/2019/a` and `Backup/2019/b`, each file has a copy elsewhere. In the summary of `Backup/2019` they do not, because deleting that folder would remove both copies.
   - **Pairs are listed only between folders that directly hold the files**: the immediate-folder level of CLAUDE.md decision 1. Ancestor pairs are not precomputed, since their number would explode.
   - **A query for any two folders** returns one pair for them, each counted recursively ("`Backup/` against `Photos/`"). Two folders where one contains the other are refused.
   - **Roll-up stops at the scanned roots.** Scanning `F:\Media` reports nothing for `F:\`.
4. **Internal duplicates.** For each set with *k* ≥ 2 physical copies inside a folder's subtree, the extra *k* − 1 copies count as internal duplicates, and (*k* − 1) × size as internal bytes: the space removing them would free while keeping one copy inside the folder. These are counted whether or not the set also has copies outside the subtree. That is a different question from "a copy elsewhere", so the two figures are not added together. Hard links never count. Pairs never pair a folder with itself. Example: `Photos/2019` holds `IMG_001.jpg` and `IMG_001 (1).jpg`, identical, 25 MB each, with no copy anywhere else. Its summary shows 0 files with a copy elsewhere, 1 internal duplicate and 25 MB of internal bytes.
5. **Every non-empty entry is returned in a fixed order; thresholds and top-N limits are display choices.**
   - **Summaries:** a folder is listed only if at least one of its files has a copy elsewhere, or it has at least one internal duplicate. Order: bytes with a copy elsewhere, largest first; then internal bytes, largest first; then path, ascending.
   - **Pairs:** a pair is listed only if at least one side is non-zero. Order: the larger of the two sides' bytes, largest first; then `a`, then `b`, ascending by path.

## Open

- Scale: pairs grow quadratically with the folders a set spans. Measure on a real export (the 2026-10-31 scan of a 3.1 TB drive: 153,028 files, 8,816 folders).
- Where the tool lives: a separate app, a subproject, or queries over DuckDB or SQLite.
