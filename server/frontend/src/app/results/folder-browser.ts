import { Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { FolderNode } from '../api/models';
import { ResultsService } from '../api/services';
import { formatBytes } from '../scans/scan-format';
import { failureMessage } from './result-error';
import { barPercent } from './result-format';
import { isPathUnderFolder } from './result-path';

/** Rows shown before "Show all <n> folders" (DFS-065.AC-3). */
export const FOLDED_ROWS = 10;

/**
 * One level of the folder list in DFS-060 decision 3's order (AC-4), as a new array: `duplicateBytes` largest first,
 * then by `name` (`localeCompare` with `numeric: true` and `sensitivity: 'base'`), then by `path`, so the order is
 * total. The input is not changed.
 */
export function sortByDuplicateSpace(folders: readonly FolderNode[]): FolderNode[] {
  return [...folders].sort(
    (a, b) =>
      b.duplicateBytes - a.duplicateBytes ||
      a.name.localeCompare(b.name, undefined, { numeric: true, sensitivity: 'base' }) ||
      a.path.localeCompare(b.path),
  );
}

/** One breadcrumb after "All folders": a folder's last name element and its path (DFS-065.AC-5). */
export interface Crumb {
  readonly name: string;
  readonly path: string;
}

/**
 * The crumbs from the root holding `selected` down to `selected` (DFS-065.AC-5), purely lexical: the root's crumb is
 * named by its last name element (a filesystem root such as `C:\` by its whole path) and is left out when the scan
 * has a single root, since "All folders" already stands for it. A path under no root gives no crumbs.
 */
export function crumbsFor(selected: string, roots: readonly string[]): Crumb[] {
  const root = roots
    .filter((candidate) => isPathUnderFolder(selected, candidate))
    .reduce<string | null>((best, candidate) => (best === null || candidate.length > best.length ? candidate : best), null);
  if (root === null) {
    return [];
  }
  const separator = selected.includes('\\') ? '\\' : '/';
  const rootName = root.split(/[\\/]+/).filter((element) => element !== '').pop();
  const crumbs: Crumb[] =
    roots.length > 1 ? [{ name: rootName === undefined || /^[A-Za-z]:$/.test(rootName) ? root : rootName, path: root }] : [];
  let path = root;
  for (const element of selected.slice(root.length).split(/[\\/]+/).filter((part) => part !== '')) {
    path = path.endsWith('\\') || path.endsWith('/') ? path + element : path + separator + element;
    crumbs.push({ name: element, path });
  }
  return crumbs;
}

/**
 * The folder browser of a COMPLETED scan (DFS-065, replacing DFS-060's tree; `ui.md` U3): a breadcrumb ("All folders",
 * then the folders down to the selected one) over a table of the current level, the roots (several roots, nothing
 * selected), the single root's subfolders (one root, nothing selected) or the selected folder's subfolders, in
 * `sortByDuplicateSpace` order, folded to `FOLDED_ROWS` rows. A level is requested with `parent=` the first time it is
 * shown and kept (AC-3). Selecting a row or a crumb emits its path, "All folders" null (AC-5); the parent owns the
 * selection. Every row has a "Pin" button that emits `folderPinned` (AC-6, DFS-050 decision 3).
 */
@Component({
  selector: 'app-folder-browser',
  imports: [MatButtonModule, MatProgressBarModule],
  templateUrl: './folder-browser.html',
  styleUrl: './folder-browser.css',
})
export class FolderBrowser {
  readonly scanId = input.required<string>();
  /** The scan's roots, from `listFolders(id)` without `parent`, loaded by the result screen. */
  readonly roots = input.required<FolderNode[]>();
  /** The selected folder's path, or null for "All folders". */
  readonly selected = input<string | null>(null);
  /** A folder's path when the user selects it, null for "All folders". */
  readonly folderSelected = output<string | null>();
  /** A folder's path when the user presses its "Pin" (DFS-065.AC-6); the parent owns the pin. */
  readonly folderPinned = output<string>();

  protected readonly formatBytes = formatBytes;
  protected readonly foldedRows = FOLDED_ROWS;

  /** Every folder seen so far, by path, so a crumb or the selected folder's totals can be shown (AC-5). */
  private readonly known = signal(new Map<string, FolderNode>());
  /** Each loaded level's folders, by the parent's path. */
  private readonly children = signal(new Map<string, FolderNode[]>());
  private readonly loading = signal(new Set<string>());
  private readonly errors = signal(new Map<string, string>());
  /** Whether the current level shows all its rows; reset when the level changes (AC-3). */
  protected readonly expanded = signal(false);

  /** The path whose subfolders are listed, or null for the roots themselves (several roots, nothing selected). */
  protected readonly levelPath = computed<string | null>(() => {
    const selected = this.selected();
    if (selected !== null) {
      return selected;
    }
    const roots = this.roots();
    return roots.length === 1 ? roots[0].path : null;
  });

  /** The current level in `sortByDuplicateSpace` order; null while it has not loaded (AC-3). */
  protected readonly level = computed<FolderNode[] | null>(() => {
    const path = this.levelPath();
    if (path === null) {
      return sortByDuplicateSpace(this.roots());
    }
    const folder = this.known().get(path);
    if (folder !== undefined && !folder.hasChildren) {
      return [];
    }
    const loaded = this.children().get(path);
    return loaded === undefined ? null : sortByDuplicateSpace(loaded);
  });

  protected readonly rows = computed(() => {
    const level = this.level() ?? [];
    return this.expanded() ? level : level.slice(0, FOLDED_ROWS);
  });

  protected readonly levelLoading = computed(() => {
    const path = this.levelPath();
    return path !== null && this.loading().has(path);
  });

  protected readonly levelError = computed(() => {
    const path = this.levelPath();
    return path === null ? null : (this.errors().get(path) ?? null);
  });

  protected readonly crumbs = computed(() => {
    const selected = this.selected();
    return selected === null ? [] : crumbsFor(selected, this.roots().map((root) => root.path));
  });

  /** The selected folder, when it has been seen in a listing (AC-5). */
  protected readonly current = computed(() => {
    const selected = this.selected();
    return selected === null ? null : (this.known().get(selected) ?? null);
  });

  private readonly results = inject(ResultsService);

  constructor() {
    effect(() => {
      const roots = this.roots();
      this.known.set(new Map(roots.map((root) => [root.path, root])));
      this.children.set(new Map());
      this.loading.set(new Set());
      this.errors.set(new Map());
    });
    effect(() => {
      const path = this.levelPath();
      untracked(() => {
        this.expanded.set(false);
        if (path !== null) {
          void this.load(path);
        }
      });
    });
  }

  /** Emits `folderSelected` with the folder's path, or null for "All folders" (AC-5). */
  protected select(path: string | null): void {
    this.folderSelected.emit(path);
  }

  /** Emits `folderPinned` with the folder's path; the selection is unchanged (AC-6). */
  protected pin(path: string, event: Event): void {
    event.stopPropagation();
    this.folderPinned.emit(path);
  }

  protected toggleExpanded(): void {
    this.expanded.update((value) => !value);
  }

  /** The width of a row's bar, in percent: `barPercent(duplicateBytes, bytes)` (AC-4). */
  protected duplicateWidth(folder: FolderNode): number {
    return barPercent(folder.duplicateBytes, folder.bytes);
  }

  /**
   * Requests one level's subfolders the first time (AC-3, AC-7), unless the folder is known to have none. The server's
   * message is kept as the level's error on failure.
   */
  private async load(path: string): Promise<void> {
    const folder = this.known().get(path);
    if (
      (folder !== undefined && !folder.hasChildren) ||
      this.children().has(path) ||
      this.loading().has(path)
    ) {
      return;
    }
    const scanId = this.scanId();
    this.loading.update((set) => new Set(set).add(path));
    this.errors.update((map) => {
      const next = new Map(map);
      next.delete(path);
      return next;
    });
    try {
      const folders = await this.results.listFolders({ id: scanId, parent: path });
      if (scanId !== this.scanId()) {
        return;
      }
      this.children.update((map) => new Map(map).set(path, folders));
      this.known.update((map) => {
        const next = new Map(map);
        folders.forEach((child) => next.set(child.path, child));
        return next;
      });
    } catch (failure) {
      this.errors.update((map) => new Map(map).set(path, failureMessage(failure)));
    } finally {
      this.loading.update((set) => {
        const next = new Set(set);
        next.delete(path);
        return next;
      });
    }
  }
}
