/**
 * Path helpers for the result screen, working on paths as the scan spells them (`Path.toString()`: backslashes on
 * Windows, slashes elsewhere). Purely lexical: nothing here touches the disk.
 */

const BACKSLASH = '\\';

function isSeparator(char: string): boolean {
  return char === BACKSLASH || char === '/';
}

/**
 * Whether `path` equals `folder` or lies under it: it starts with `folder` followed by a separator, so `C:\Photos2\x`
 * is not under `C:\Photos` (DFS-049 decision 4, AC-8). A folder that already ends in a separator (a root such as
 * `C:\` or `/`) needs none added.
 */
export function isPathUnderFolder(path: string, folder: string): boolean {
  if (path === folder) {
    return true;
  }
  if (!path.startsWith(folder)) {
    return false;
  }
  return isSeparator(folder.charAt(folder.length - 1)) || isSeparator(path.charAt(folder.length));
}

/**
 * A path as the Sets table shows it (DFS-060 decision 5, AC-7): `folders` (every shown folder, "…" included, each
 * followed by the separator; empty for a file directly in its root) in the secondary colour, then `name` in normal
 * ink. `text` is `folders + name`.
 */
export interface ShortPath {
  readonly text: string;
  readonly folders: string;
  readonly name: string;
}

/** The "…" that stands for the folders cut from a path's middle, or for the middle of a file name (AC-7). */
export const ELLIPSIS = '…';

/**
 * A file path shortened for a column of `budget` characters (DFS-060 decision 5, AC-7), as a pure function:
 *
 * 1. The separator is the path's own (a backslash when it has one, else `/`).
 * 2. The root holding it is the longest of `roots` that `isPathUnderFolder` accepts; the path is shown relative to it.
 *    With more than one root, the root's last name element comes first (`D:\Backup\Photos\cat.jpg` →
 *    `Backup\Photos\cat.jpg`); a root without one (`C:\`, `/`) is spelled without its trailing separator. A path under
 *    no root is shown whole, absolute.
 * 3. A path within the budget is shown whole.
 * 4. Otherwise the head (the root's name with several roots, and the first folder under the root) and the file name
 *    stay; the folders between them become one "…" element; trailing folders, nearest the name first, are added back
 *    while the whole still fits, stopping at the first that does not
 *    (`Backups\MacBook-2020\Users\me\Pictures\Iceland 2019\pano.tif` at 36 → `Backups\…\Iceland 2019\pano.tif`).
 *    With no folder between the head and the name, there is no "…" element.
 * 5. When the head, the "…" element and the name alone exceed the budget, the name is cut in its middle to the room
 *    left: the stem's first ceil(n/2) and last floor(n/2) characters around one "…", then the extension (from the last
 *    dot, unless the name starts with it) kept whole
 *    (`Downloads\Screen Recording 2024-03-05 at 12.41.07 long.mov` at 36 → `Downloads\Screen Reco…41.07 long.mov`).
 *    The name never drops below its extension plus three characters, and the head is never cut, so a very long head
 *    can exceed the budget.
 */
export function displayPath(path: string, roots: readonly string[], budget: number): ShortPath {
  throw new Error('not implemented: DFS-060');
}

/**
 * The last name element of a path, either separator (DFS-060 AC-5, AC-8): `C:\Archive\Downloads\ubuntu.iso` →
 * `ubuntu.iso`, `C:\Archive\Backups\old-drive` → `old-drive`. A path without one (`C:\`, `/`) is returned as given.
 */
export function fileName(path: string): string {
  throw new Error('not implemented: DFS-060');
}

const DRIVE_ROOT = /^[A-Za-z]:$/;
const UNC_SHARE_ROOT = /^\\\\[^\\]+\\[^\\]+$/;

/**
 * The immediate parent directory of a file path, spelled as given, without the file name or added quotes (DFS-049
 * decision 10, AC-11): `C:\Photos\a.jpg` → `C:\Photos`, `C:\a.jpg` → `C:\`, `\\nas\share\a.jpg` → `\\nas\share\`,
 * `/photos/a.jpg` → `/photos`, `/a.jpg` → `/`. Spaces and Unicode are kept as they are.
 */
export function containingDirectory(path: string): string {
  const index = Math.max(path.lastIndexOf(BACKSLASH), path.lastIndexOf('/'));
  if (index < 0) {
    return path;
  }
  const parent = path.slice(0, index);
  // A POSIX root (""), a drive root ("C:") and a UNC share root keep their separator.
  if (parent === '' || DRIVE_ROOT.test(parent) || UNC_SHARE_ROOT.test(parent)) {
    return path.slice(0, index + 1);
  }
  return parent;
}
