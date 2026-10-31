// Parses a duplicate-file-scanner report (text or JSONL) and computes folder views.
// Rules: docs/design/folder-overlap-analysis.md. A "copy elsewhere" is a path in another place
// that is a different physical copy (hard links never count); for a folder's subtree,
// "elsewhere" means outside that subtree. File counts count paths; bytes count each physical
// copy once.
(function (global) {
  'use strict';

  const UNITS = { B: 1, KB: 1024, MB: 1024 ** 2, GB: 1024 ** 3, TB: 1024 ** 4 };

  /** Splits a path into segments, accepting \ or /. */
  function segments(path) {
    return path.split(/[\\/]+/).filter(s => s.length > 0);
  }

  /**
   * Returns { format, approximate, sets: [{size, digest, copies: [[path...]...]}], roots, folderTotals }.
   * folderTotals: Map folderPath -> {files, bytes} (JSONL version 2 only), roots: [] when unknown.
   */
  function parse(text) {
    const firstLine = text.slice(0, 2000).split(/\r?\n/).find(l => l.trim().length > 0) || '';
    return firstLine.trim().startsWith('{') ? parseJsonl(text) : parseText(text);
  }

  function parseJsonl(text) {
    const sets = [];
    const folderTotals = new Map();
    let roots = [];
    let version = 1;
    for (const line of text.split(/\r?\n/)) {
      if (!line.trim()) continue;
      const o = JSON.parse(line);
      if (o.type === 'scan') {
        version = o.schemaVersion || 1;
        roots = o.roots || [];
      } else if (o.type === 'folder') {
        folderTotals.set(normal(o.path), { files: o.files, bytes: o.bytes });
      } else if (o.type === 'set') {
        const copies = o.copies.map(links => links.map(l => (typeof l === 'string' ? l : l.path)));
        sets.push({ size: o.size, digest: o.digest, copies });
      }
    }
    return { format: 'jsonl v' + version, approximate: false, sets, roots: roots.map(normal), folderTotals };
  }

  function parseText(text) {
    const sets = [];
    let cur = null;
    const head = /^#\d+\s+\S+ \S+ reclaimable\s+\([\d,]+ copies x ([\d.]+) (B|KB|MB|GB|TB), sha256 (\w+)\)/;
    for (const line of text.split(/\r?\n/)) {
      const m = head.exec(line);
      if (m) {
        cur = { size: Math.round(parseFloat(m[1]) * UNITS[m[2]]), digest: m[3], copies: [] };
        sets.push(cur);
      } else if (cur && line.startsWith('      (hard link) ')) {
        cur.copies[cur.copies.length - 1].push(line.slice('      (hard link) '.length));
      } else if (cur && line.startsWith('    ')) {
        cur.copies.push([line.slice(4)]);
      } else {
        cur = null;
      }
    }
    return { format: 'text report', approximate: true, sets, roots: [], folderTotals: new Map() };
  }

  /** Normalises a path to '/'-separated segments joined, so Windows and POSIX paths compare alike. */
  function normal(path) {
    return segments(path).join('/');
  }

  function parentOf(path) {
    const i = path.lastIndexOf('/');
    return i < 0 ? '' : path.slice(0, i);
  }

  function isUnder(path, folder) {
    return path.startsWith(folder + '/');
  }

  /**
   * Builds the model:
   *  files: [{set, copy, path, folder, size}]
   *  folders: Map path -> node {path, name, parent, children:Set, filesElsewhere, bytesElsewhere,
   *            internalFiles, internalBytes, dupFiles, total:{files,bytes}|null}
   */
  function analyse(report) {
    const files = [];
    report.sets.forEach((s, si) => {
      s.copies.forEach((links, ci) => {
        for (const raw of links) {
          const path = normal(raw);
          files.push({ set: si, copy: ci, path, folder: parentOf(path), size: s.size, raw });
        }
      });
    });

    const folders = new Map();
    function node(path) {
      let n = folders.get(path);
      if (n) return n;
      const parent = path.includes('/') ? parentOf(path) : null;
      n = {
        path, name: path.includes('/') ? path.slice(path.lastIndexOf('/') + 1) : path, parent,
        children: new Set(), filesElsewhere: 0, bytesElsewhere: 0, internalFiles: 0, internalBytes: 0,
        dupFiles: 0, total: null,
      };
      folders.set(path, n);
      if (parent !== null) node(parent).children.add(path);
      return n;
    }

    const bySet = new Map();
    for (const f of files) {
      node(f.folder);
      if (!bySet.has(f.set)) bySet.set(f.set, []);
      bySet.get(f.set).push(f);
    }

    for (const members of bySet.values()) {
      const size = members[0].size;
      // Every folder (with ancestors) touched by this set.
      const ancestors = new Set();
      for (const f of members) {
        for (let a = f.folder; a !== null && a !== ''; a = folders.get(a).parent) ancestors.add(a);
      }
      for (const a of ancestors) {
        const inside = members.filter(f => isUnder(f.path, a));
        const outsideCopies = new Set(members.filter(f => !isUnder(f.path, a)).map(f => f.copy));
        const n = folders.get(a);
        n.dupFiles += inside.length;
        const copiesElsewhere = new Set();
        for (const f of inside) {
          const hasOther = outsideCopies.size >= 2 || (outsideCopies.size === 1 && !outsideCopies.has(f.copy));
          if (hasOther) {
            n.filesElsewhere++;
            copiesElsewhere.add(f.copy);
          }
        }
        n.bytesElsewhere += copiesElsewhere.size * size;
        const k = new Set(inside.map(f => f.copy)).size;
        if (k >= 2) {
          n.internalFiles += k - 1;
          n.internalBytes += (k - 1) * size;
        }
      }
    }

    // Totals (JSONL v2): direct counts summed up the tree.
    if (report.folderTotals.size > 0) {
      for (const [path, t] of report.folderTotals) {
        for (let a = path; ; a = parentOf(a)) {
          const n = node(a);
          if (!n.total) n.total = { files: 0, bytes: 0 };
          n.total.files += t.files;
          n.total.bytes += t.bytes;
          if (!a.includes('/')) break;
        }
      }
    }

    return { files, folders, bySet, sets: report.sets };
  }

  /** Top-level nodes (no parent). */
  function roots(model) {
    return [...model.folders.values()].filter(n => n.parent === null);
  }

  /**
   * For a folder X: where do the copies of X's files live, outside X? Aggregated by the
   * immediate folder of the outside copy. Returns [{folder, files, bytes}] largest first, where
   * files/bytes are X's files (and physical copies) that have a copy in that folder.
   */
  function copiesOutside(model, x) {
    const agg = new Map();
    for (const members of model.bySet.values()) {
      const inside = members.filter(f => isUnder(f.path, x));
      if (inside.length === 0) continue;
      const outside = members.filter(f => !isUnder(f.path, x));
      const byFolder = new Map();
      for (const o of outside) {
        if (!byFolder.has(o.folder)) byFolder.set(o.folder, new Set());
        byFolder.get(o.folder).add(o.copy);
      }
      for (const [folder, outCopies] of byFolder) {
        const hits = inside.filter(f => outCopies.size >= 2 || !outCopies.has(f.copy));
        if (hits.length === 0) continue;
        const a = agg.get(folder) || { folder, files: 0, bytes: 0 };
        a.files += hits.length;
        a.bytes += new Set(hits.map(f => f.copy)).size * members[0].size;
        agg.set(folder, a);
      }
    }
    return [...agg.values()].sort((p, q) => q.bytes - p.bytes || p.folder.localeCompare(q.folder));
  }

  /** Sets with at least one path under x, largest reclaimable first. */
  function setsUnder(model, x) {
    const out = [];
    for (const [si, members] of model.bySet) {
      if (!members.some(f => isUnder(f.path, x))) continue;
      const s = model.sets[si];
      out.push({ set: s, members, reclaimable: s.size * (s.copies.length - 1) });
    }
    return out.sort((p, q) => q.reclaimable - p.reclaimable);
  }

  /** Immediate-folder pairs, both directions in one row. */
  function pairs(model) {
    const directed = new Map(); // "b\u0000a" -> {files, copies:Set}
    for (const members of model.bySet.values()) {
      for (const b of members) {
        const seenA = new Set();
        for (const a of members) {
          if (a.folder === b.folder || a.copy === b.copy || seenA.has(a.folder)) continue;
          seenA.add(a.folder);
          const key = b.folder + '\u0000' + a.folder;
          const d = directed.get(key) || { files: 0, copies: new Set() };
          d.files++;
          d.copies.add(b.set + ':' + b.copy);
          directed.set(key, d);
        }
      }
    }
    const sizeOf = key => {
      let bytes = 0;
      for (const c of directed.get(key).copies) bytes += model.sets[Number(c.split(':')[0])].size;
      return bytes;
    };
    const rows = [];
    for (const key of directed.keys()) {
      const [b, a] = key.split('\u0000');
      if (!(b < a)) continue;
      const back = a + '\u0000' + b;
      if (!directed.has(back)) continue;
      rows.push({
        a: b, b: a,
        aFiles: directed.get(key).files, aBytes: sizeOf(key),
        bFiles: directed.get(back).files, bBytes: sizeOf(back),
      });
    }
    return rows.sort((p, q) => Math.max(q.aBytes, q.bBytes) - Math.max(p.aBytes, p.bBytes)
      || p.a.localeCompare(q.a) || p.b.localeCompare(q.b));
  }

  /**
   * Side-by-side comparison of folders a and b (each counted recursively), for a dir-compare view.
   * Only duplicated files are known to a report, so a row is one of:
   *   same    - a copy in both, at the same relative path
   *   moved   - a copy in both, at different relative paths (renamed or in another subfolder)
   *   left    - in a only; it has copies elsewhere, but none in b
   *   right   - in b only; likewise
   *   extra   - an extra copy on one side of a set both sides hold (an internal duplicate)
   * Copies are paired by equal relative path first, then in path order. Hard links of one
   * physical copy inside a folder count as one entry (the first path; the rest are listed in links).
   * Returns { rows: [{status, left, right, size, digest}], counts, bytes } with rows sorted by the
   * relative path of the left side (right when there is no left).
   */
  function compare(model, a, b) {
    const rel = (path, root) => path.slice(root.length + 1);
    const rows = [];
    for (const members of model.bySet.values()) {
      const pick = root => {
        const byCopy = new Map();
        for (const f of members) {
          if (!isUnder(f.path, root)) continue;
          if (!byCopy.has(f.copy)) byCopy.set(f.copy, { rel: rel(f.path, root), raw: f.raw, links: [] });
          else byCopy.get(f.copy).links.push(f.raw);
        }
        return [...byCopy.entries()].map(([copy, e]) => ({ copy, ...e }))
          .sort((p, q) => p.rel.localeCompare(q.rel));
      };
      const left = pick(a), right = pick(b);
      if (left.length === 0 && right.length === 0) continue;
      const size = members[0].size;
      const digest = model.sets[members[0].set].digest;
      const usedR = new Set();
      const pairedL = new Set();
      // A left and right entry pair only when they are different physical copies.
      left.forEach((l, i) => {
        const j = right.findIndex((r, k) => !usedR.has(k) && r.rel.toLowerCase() === l.rel.toLowerCase() && r.copy !== l.copy);
        if (j >= 0) { usedR.add(j); pairedL.add(i); rows.push({ status: 'same', left: l, right: right[j], size, digest }); }
      });
      left.forEach((l, i) => {
        if (pairedL.has(i)) return;
        const j = right.findIndex((r, k) => !usedR.has(k) && r.copy !== l.copy);
        if (j >= 0) { usedR.add(j); rows.push({ status: 'moved', left: l, right: right[j], size, digest }); }
        else {
          // No partner copy left in b. If b holds this set at all, the extra is an internal duplicate.
          rows.push({ status: right.length > 0 ? 'extra' : 'left', left: l, right: null, size, digest });
        }
      });
      right.forEach((r, k) => {
        if (!usedR.has(k)) rows.push({ status: left.length > 0 ? 'extra' : 'right', left: null, right: r, size, digest });
      });
    }
    const key = r => (r.left ? r.left.rel : r.right.rel).toLowerCase();
    rows.sort((p, q) => key(p).localeCompare(key(q)) || (p.left ? 0 : 1) - (q.left ? 0 : 1));
    const counts = { same: 0, moved: 0, left: 0, right: 0, extra: 0 };
    const bytes = { same: 0, moved: 0, left: 0, right: 0, extra: 0 };
    for (const r of rows) { counts[r.status]++; bytes[r.status] += r.size; }
    return { rows, counts, bytes };
  }

  /** All folder paths, sorted, for pickers. */
  function folderPaths(model) {
    return [...model.folders.keys()].sort();
  }

  function formatBytes(n) {
    if (n < 1024) return n + ' B';
    const units = ['KB', 'MB', 'GB', 'TB'];
    let u = -1;
    do { n /= 1024; u++; } while (n >= 1024 && u < units.length - 1);
    return n.toFixed(1) + ' ' + units[u];
  }

  const api = { parse, analyse, roots, copiesOutside, setsUnder, pairs, compare, folderPaths, formatBytes, normal };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else global.DfsAnalysis = api;
})(this);
