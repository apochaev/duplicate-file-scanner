-- Folder overlaps from a duplicate-file-scanner JSONL report (today's format, DFS-020).
--
--   duckdb -cmd "SET VARIABLE report = 'C:/reports/media.jsonl'" -f tools/analysis/overlaps.sql
--   or, inside the duckdb shell:  SET VARIABLE report = 'C:/reports/media.jsonl';
--                                 .read tools/analysis/overlaps.sql
-- Then explore:  SELECT * FROM folders;  SELECT * FROM pairs;
--                SELECT * FROM overlap('F:/Media/Backup', 'F:/Media/Photos');
-- Paths are shown with forward slashes. Needs DuckDB 1.3 or later (lambda syntax).
--
-- What today's report cannot give (DFS-030 adds it): each folder's TOTAL file count, so there
-- is no "340 of 400" and no full/partial; and the scanned roots, so roll-up also shows the
-- ancestors above the folder you scanned (e.g. F:/).
--
-- Rules (docs/design/folder-overlap-analysis.md): a "copy elsewhere" is a path in another
-- folder that is a different physical copy, so hard links never count; for a folder's whole
-- subtree, "elsewhere" means outside that subtree. File counts count paths; byte counts count
-- each physical copy once, so hard links do not inflate the space figures.

-- One row per path: its set, its physical copy (hard links share one), size, folder.
CREATE OR REPLACE TABLE files AS
WITH sets AS (
    SELECT row_number() OVER () AS set_id, size, digest, copies
    FROM read_json(getvariable('report'), format = 'newline_delimited',
                   columns = {type: 'VARCHAR', size: 'BIGINT', digest: 'VARCHAR', copies: 'VARCHAR[][]'})
    WHERE type = 'set'
), c AS (
    SELECT set_id, size, digest, unnest(copies) AS links, generate_subscripts(copies, 1) AS copy_id
    FROM sets
), p AS (
    SELECT set_id, size, digest, copy_id, replace(unnest(links), '\', '/') AS path FROM c
)
SELECT *, regexp_replace(path, '/[^/]+$', '') AS folder FROM p;

-- Directed: files in `folder` that have a separate copy in folder `other`.
CREATE OR REPLACE TABLE directed AS
WITH hits AS (
    SELECT DISTINCT b.folder, a.folder AS other, b.path, b.size, b.set_id, b.copy_id
    FROM files a JOIN files b
      ON a.set_id = b.set_id AND a.folder <> b.folder AND a.copy_id <> b.copy_id
)
SELECT folder, other, sum(paths) AS files, sum(size) AS bytes
FROM (SELECT folder, other, set_id, copy_id, any_value(size) AS size, count(*) AS paths
      FROM hits GROUP BY ALL)
GROUP BY ALL;

-- Pairs of immediate folders, both directions in one row (a < b).
CREATE OR REPLACE VIEW pairs AS
SELECT d1.folder AS a, d1.other AS b,
       d1.files AS a_files_with_copy_in_b, d1.bytes AS a_bytes,
       d2.files AS b_files_with_copy_in_a, d2.bytes AS b_bytes
FROM directed d1 JOIN directed d2 ON d1.folder = d2.other AND d1.other = d2.folder
WHERE d1.folder < d1.other
ORDER BY greatest(d1.bytes, d2.bytes) DESC, a, b;

-- Every folder and its ancestors, for subtree roll-up.
CREATE OR REPLACE TABLE files_anc AS
SELECT f.*, anc
FROM files f,
     unnest(list_transform(range(1, len(string_split(f.folder, '/')) + 1),
                           lambda i: array_to_string(string_split(f.folder, '/')[1:i], '/'))) AS t(anc);

-- Per folder subtree: files with a copy outside the subtree, and internal duplicates
-- (k physical copies of one set inside the subtree: k - 1 extra copies).
CREATE OR REPLACE VIEW folders AS
WITH outside AS (
    SELECT k.set_id, k.anc, count(DISTINCT f.copy_id) AS n_out, min(f.copy_id) AS one_out
    FROM (SELECT DISTINCT set_id, anc FROM files_anc) k
    JOIN files f ON f.set_id = k.set_id AND NOT starts_with(f.path, k.anc || '/')
    GROUP BY ALL
), redundant AS (
    SELECT anc, sum(paths) AS files_elsewhere, sum(size) AS bytes_elsewhere
    FROM (SELECT p.anc, p.set_id, p.copy_id, any_value(p.size) AS size, count(*) AS paths
          FROM files_anc p JOIN outside o ON o.set_id = p.set_id AND o.anc = p.anc
          WHERE o.n_out >= 2 OR o.one_out <> p.copy_id
          GROUP BY ALL)
    GROUP BY ALL
), internal AS (
    SELECT anc, sum(k - 1) AS internal_files, sum((k - 1) * size) AS internal_bytes
    FROM (SELECT anc, set_id, any_value(size) AS size, count(DISTINCT copy_id) AS k
          FROM files_anc GROUP BY ALL)
    WHERE k >= 2
    GROUP BY ALL
)
SELECT coalesce(r.anc, i.anc) AS folder,
       coalesce(files_elsewhere, 0) AS files_elsewhere, coalesce(bytes_elsewhere, 0) AS bytes_elsewhere,
       coalesce(internal_files, 0) AS internal_files, coalesce(internal_bytes, 0) AS internal_bytes
FROM redundant r FULL JOIN internal i ON r.anc = i.anc
ORDER BY bytes_elsewhere DESC, internal_bytes DESC, folder;

-- Any two folders, recursively:  SELECT * FROM overlap('F:/Backup', 'F:/Photos');
CREATE OR REPLACE MACRO overlap(x, y) AS TABLE
WITH hits AS (
    SELECT DISTINCT
           CASE WHEN starts_with(b.path, x || '/') THEN x ELSE y END AS side,
           b.path, b.size, b.set_id, b.copy_id
    FROM files a JOIN files b ON a.set_id = b.set_id AND a.copy_id <> b.copy_id
    WHERE (starts_with(b.path, x || '/') AND starts_with(a.path, y || '/'))
       OR (starts_with(b.path, y || '/') AND starts_with(a.path, x || '/'))
)
SELECT side AS folder, sum(paths) AS files_with_copy_in_other, sum(size) AS bytes
FROM (SELECT side, set_id, copy_id, any_value(size) AS size, count(*) AS paths FROM hits GROUP BY ALL)
GROUP BY ALL;

-- Quick look.
SELECT 'top folders by space with a copy elsewhere' AS report;
SELECT * FROM folders LIMIT 25;
SELECT 'top folder pairs' AS report;
SELECT * FROM pairs LIMIT 25;
