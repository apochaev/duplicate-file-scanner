import { Component, inject, input, linkedSignal, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTreeModule } from '@angular/material/tree';
import { FolderNode } from '../api/models';
import { ResultsService } from '../api/services';
import { failureMessage } from './result-error';
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
 * Selecting a node or "All folders" emits its path, or null (AC-6); the parent owns the selection. Every folder node
 * (not "All folders") has a "Pin for comparison" action that emits `folderPinned` (DFS-050 decision 3).
 *
 * A flat `mat-tree` (`levelAccessor`): `rows` holds only the visible rows, so a collapsed node's descendants are not in
 * it. Expansion goes through the tree's own model (`matTreeNodeToggle`, keyed by path), so `aria-expanded` and
 * `MatTreeNodeHarness.expand()` work; the node's `expandedChange` drives the load.
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
  /** A folder's path when the user clicks its "Pin for comparison" (DFS-050 decision 3, AC-5); the parent owns the pin. */
  readonly folderPinned = output<string>();

  protected readonly rows = linkedSignal(() => this.rootRows(this.roots()));

  protected readonly folderLabel = folderLabel;
  protected readonly levelOf = (row: FolderTreeRow): number => row.level;
  protected readonly keyOf = (row: FolderTreeRow): string => row.folder.path;
  protected readonly trackRow = (_index: number, row: FolderTreeRow): string => row.folder.path;

  private readonly results = inject(ResultsService);
  /** Each loaded folder's children, by its path; a folder is requested at most once. */
  private readonly loadedChildren = new Map<string, FolderNode[]>();
  private readonly expandedPaths = new Set<string>();
  private readonly loadingPaths = new Set<string>();
  private readonly errors = new Map<string, string>();

  /** The roots as level-0 rows, collapsed, in the given order (AC-5). */
  protected rootRows(roots: FolderNode[]): FolderTreeRow[] {
    this.loadedChildren.clear();
    this.expandedPaths.clear();
    this.loadingPaths.clear();
    this.errors.clear();
    return this.visibleRows(roots, 0);
  }

  /**
   * A node was expanded or collapsed through its toggle. Expanding: shows its children beneath it, requesting
   * `listFolders({ id: scanId, parent: path })` the first time with `loading` set (AC-5, AC-10), the server's message
   * in `error` on failure. Collapsing: removes its descendants from `rows`, keeping what was loaded.
   */
  protected async expandedChange(row: FolderTreeRow, expanded: boolean): Promise<void> {
    const path = row.folder.path;
    if (!expanded) {
      this.expandedPaths.delete(path);
      this.refresh();
      return;
    }
    this.expandedPaths.add(path);
    if (this.loadedChildren.has(path) || this.loadingPaths.has(path)) {
      this.refresh();
      return;
    }
    this.loadingPaths.add(path);
    this.errors.delete(path);
    this.refresh();
    try {
      this.loadedChildren.set(path, await this.results.listFolders({ id: this.scanId(), parent: path }));
    } catch (failure) {
      this.errors.set(path, failureMessage(failure));
    }
    this.loadingPaths.delete(path);
    this.refresh();
  }

  /** Emits `folderSelected` with the folder's path, or null for "All folders" (AC-6). */
  protected select(path: string | null): void {
    this.folderSelected.emit(path);
  }

  /**
   * Emits `folderPinned` with the folder's path (DFS-050 AC-5). Offered on every folder node, never on "All folders";
   * it does not change the selection.
   */
  protected pin(path: string): void {
    this.folderPinned.emit(path);
  }

  private refresh(): void {
    this.rows.set(this.visibleRows(this.roots(), 0));
  }

  /** The folders at one level, each followed by its children when it is expanded and they are loaded. */
  private visibleRows(folders: FolderNode[], level: number): FolderTreeRow[] {
    return folders.flatMap((folder) => {
      const path = folder.path;
      const expanded = this.expandedPaths.has(path);
      const row: FolderTreeRow = {
        folder,
        level,
        expanded,
        loading: this.loadingPaths.has(path),
        error: this.errors.get(path) ?? null,
      };
      const children = expanded ? this.loadedChildren.get(path) : undefined;
      return children ? [row, ...this.visibleRows(children, level + 1)] : [row];
    });
  }
}
