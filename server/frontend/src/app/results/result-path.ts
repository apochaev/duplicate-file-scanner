/**
 * Path helpers for the result screen, working on paths as the scan spells them (`Path.toString()`: backslashes on
 * Windows, slashes elsewhere). Purely lexical: nothing here touches the disk.
 */

/**
 * Whether `path` equals `folder` or lies under it: it starts with `folder` followed by a separator, so `C:\Photos2\x`
 * is not under `C:\Photos` (DFS-049 decision 4, AC-8). A folder that already ends in a separator (a root such as
 * `C:\` or `/`) needs none added.
 */
export function isPathUnderFolder(path: string, folder: string): boolean {
  throw new Error('not implemented: DFS-049');
}

/**
 * The immediate parent directory of a file path, spelled as given, without the file name or added quotes (DFS-049
 * decision 10, AC-11): `C:\Photos\a.jpg` → `C:\Photos`, `C:\a.jpg` → `C:\`, `\\nas\share\a.jpg` → `\\nas\share\`,
 * `/photos/a.jpg` → `/photos`, `/a.jpg` → `/`. Spaces and Unicode are kept as they are.
 */
export function containingDirectory(path: string): string {
  throw new Error('not implemented: DFS-049');
}
