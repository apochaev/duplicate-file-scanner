import { Component, inject, input, linkedSignal, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTreeModule } from '@angular/material/tree';
import { FolderNode } from '../api/models';
import { ResultsService } from '../api/services';
import { folderLabel } from './result-format';

/** One visible row of the flat tree: a folder at a depth, with the state of its children's load. */
export interface FolderTreeRow {
  readonly folder: FolderNode;
  /** 0 for a root. */
  readonly level: number;
  readonly expanded: boolean;
  /** Its children are being requested. */
  readonly loading: boolean;
  /** The server's message when its children could not be loaded. */
  readonly error: string | null;
}

/**
 * The folder tree of a COMPLETED scan (DFS-049 decision 3, `ui.md` U3): "All folders" above the scan's roots, in the
 * order `listFolders` returns them. A node with `hasChildren` expands; its children are requested with
 * `parent=<its path>` the first time only, then kept, so collapsing and expanding again makes no request (AC-5).
 * Selecting a node or "All folders" emits its path, or null (AC-6); the parent owns the selection.
 *
 * A flat `mat-tree` (`levelAccessor`): `rows` holds only the visible rows, so a collapsed node's descendants are not in
 * it. Expansion goes through the tree's own model (`matTreeNodeToggle`, keyed by path), so `aria-expanded` and
 * `MatTreeNodeHarness.expand()` work; the node's `expandedChange` drives the load.
 *
 * Stubs (design phase): `rootRows`, `expandedChange`, `select` and `folderLabel` throw; inputs, the output, the
 * injected service, the template's structure and classes are final.
 */
@Component({
  selector: 'app-folder-tree',
  imports: [MatButtonModule, MatProgressBarModule, MatTreeModule],
  templateUrl: './folder-tree.html',
  styleUrl: './folder-tree.css',
})
export class FolderTree {
  readonly scanId = input.required<string>();
  /** The scan's roots, from `listFolders(id)` without `parent`, loaded by the result screen. */
  readonly roots = input.required<FolderNode[]>();
  /** The selected folder's path, or null for "All folders". */
  readonly selected = input<string | null>(null);
  /** A folder's path when the user selects it, null for "All folders". */
  readonly folderSelected = output<string | null>();

  protected readonly rows = linkedSignal(() => this.rootRows(this.roots()));

  protected readonly folderLabel = folderLabel;
  protected readonly levelOf = (row: FolderTreeRow): number => row.level;
  protected readonly keyOf = (row: FolderTreeRow): string => row.folder.path;
  protected readonly trackRow = (_index: number, row: FolderTreeRow): string => row.folder.path;

  private readonly results = inject(ResultsService);
  /** Each loaded folder's children, by its path; a folder is requested at most once. */
  private readonly loadedChildren = new Map<string, FolderNode[]>();

  /** The roots as level-0 rows, collapsed, in the given order (AC-5). */
  protected rootRows(roots: FolderNode[]): FolderTreeRow[] {
    throw new Error('not implemented: DFS-049');
  }

  /**
   * A node was expanded or collapsed through its toggle. Expanding: shows its children beneath it, requesting
   * `listFolders({ id: scanId, parent: path })` the first time with `loading` set (AC-5, AC-10), the server's message
   * in `error` on failure. Collapsing: removes its descendants from `rows`, keeping what was loaded.
   */
  protected async expandedChange(row: FolderTreeRow, expanded: boolean): Promise<void> {
    throw new Error('not implemented: DFS-049');
  }

  /** Emits `folderSelected` with the folder's path, or null for "All folders" (AC-6). */
  protected select(path: string | null): void {
    throw new Error('not implemented: DFS-049');
  }
}
