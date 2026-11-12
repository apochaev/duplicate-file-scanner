import { Component, effect, inject, input, output, signal, untracked } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { FolderGroupView, FolderPairView, GroupFolderView, GroupPage } from '../api/models';
import { OverlapsService } from '../api/services';
import { formatBytes, plural } from '../scans/scan-format';
import { PathCopy } from './path-copy';
import { Density } from './result-density';
import { failureMessage } from './result-error';
import { pairCoverageText, pairSideText } from './result-format';
import { ShortPath } from './result-path';

/** The list with no groups to show (DFS-068, "Loading, empty and error states"). */
export const NO_GROUPS = 'No folders share files';

/** The line a group shows when two or more of its folders are fully shared (DFS-068.AC-4). */
export const FULLY_SHARED_NOTE = 'Fully shared folders: any one can go, not all.';

/** The "app" tag's tooltip (DFS-068.AC-8). */
export const APP_TAG_TITLE = 'Managed by an application; deleting here can break it';

/**
 * Path elements that mark a folder as managed by an application (DFS-068 G4): `*` stands for any run of characters,
 * everything else is literal, and an element is compared without regard to letter case (DFS-068.AC-8).
 */
export const APP_FOLDER_PATTERNS: readonly string[] = [
  '*.auh',
  'Adobe Premiere Pro Auto-Save',
  '*.lrdata',
  '*.photoslibrary',
  '*.fcpbundle',
];

/**
 * Whether any element of the path (split at `\` and `/`) matches one of `APP_FOLDER_PATTERNS`, compared
 * case-insensitively: `D:\a\Photo.AUH\x` is managed by an application, `D:\a\Photos` is not (DFS-068.AC-8).
 */
export function isAppManaged(path: string): boolean {
  throw new Error('not implemented: DFS-068');
}

/**
 * The character budget of a folder line's path: one fixed number, since the line's width is not measured (the Sets
 * tab measures its File column, this tab does not); the full path is the tooltip (DFS-068.AC-2).
 */
export const FOLDER_PATH_BUDGET = 64;

/** The Comparison block with the pinned folder itself or "All folders" selected (DFS-050 AC-7). */
export const COMPARE_HINT = 'Select another folder to compare with the pinned one';

/** The paginator's first page size (DFS-068 G3: 10, 25 and 50 groups per page). */
export const OVERLAPS_PAGE_SIZE = 10;

/** What the Comparison block shows while a folder is pinned (DFS-050 decision 3, AC-6, AC-7). */
export type Comparison =
  /** The pinned folder itself or "All folders" is selected: `COMPARE_HINT`, no request. */
  | { readonly kind: 'hint' }
  /** `/pair` is being requested. */
  | { readonly kind: 'loading' }
  /** `/pair` answered: one pair row, sides in the API's order. */
  | { readonly kind: 'loaded'; readonly pair: FolderPairView }
  /** `/pair` failed: the server's message (`NESTED_FOLDERS`, ...), shown with `role="alert"`. */
  | { readonly kind: 'failed'; readonly message: string };

/**
 * The Overlaps tab, the result screen's second tab (DFS-050, DFS-068, `ui.md` U3): top to bottom, "Pinned: <path>" with
 * "Unpin" and the Comparison block while a folder is pinned (as DFS-050 built them), then one list of folder groups
 * from `/groups`, a paginator (10, 25, 50) and the "Show N small groups" / "Hide small groups" button.
 *
 * Each group (`div.group`) is a `div.group-header` (files, copies, size, number of folders, the reclaimable bytes with
 * a bar scaled to the page's largest), one `li.folder-line` per folder (short path with the full path as tooltip, "×N",
 * a bar and "<sharedFiles> of <folderFiles> files", "fully shared", "also in N other groups", the "app" tag, the copy
 * button), the `p.fully-shared-note` when two or more folders are fully shared, and a `mat-expansion-panel.group-sets`
 * listing the first sets ("and N more" when the group has more).
 *
 * A container: it requests its pages itself through `OverlapsService`, following its inputs. The selection and the pin
 * are owned by the result screen; "Unpin" is emitted. Two effects: (`scanId`, `folder`) → `selectionChanged()`;
 * (`scanId`, `pinned`, `folder`) → `compare()`. Neither tracks what those methods read or write.
 */
@Component({
  selector: 'app-overlaps-tab',
  imports: [MatButtonModule, MatExpansionModule, MatPaginatorModule, MatProgressBarModule, PathCopy],
  templateUrl: './overlaps-tab.html',
  styleUrl: './overlaps-tab.css',
})
export class OverlapsTab {
  readonly scanId = input.required<string>();
  /** The selected folder's path, or null for "All folders". */
  readonly folder = input<string | null>(null);
  /** The pinned folder's path, or null when nothing is pinned. */
  readonly pinned = input<string | null>(null);
  /** The scan's roots, which paths are shown relative to (as the Sets tab does); empty shows paths whole (DFS-068.AC-2). */
  readonly roots = input<readonly string[]>([]);
  /** The row density: `density-compact` or `density-comfortable` on the section (as the Sets tab does). */
  readonly density = input<Density>('compact');
  /** The user clicked "Unpin" (DFS-050 AC-5). */
  readonly unpinned = output<void>();

  protected readonly pageSizeOptions = [10, 25, 50];

  /** The groups' page; null before it has loaded and after a new selection until its page 0 has. */
  protected readonly groups = signal<GroupPage | null>(null);
  protected readonly pageIndex = signal(0);
  protected readonly pageSize = signal(OVERLAPS_PAGE_SIZE);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  /** True after "Show N small groups": `loadGroups` then sends `minBytes: 0`; false sends no `minBytes` (AC-7). */
  protected readonly smallShown = signal(false);

  /** The Comparison block's state; null while nothing is pinned (no block). */
  protected readonly comparison = signal<Comparison | null>(null);

  protected readonly noGroups = NO_GROUPS;
  protected readonly fullySharedNote = FULLY_SHARED_NOTE;
  protected readonly appTagTitle = APP_TAG_TITLE;
  protected readonly compareHint = COMPARE_HINT;
  protected readonly plural = plural;
  protected readonly formatBytes = formatBytes;
  protected readonly pairSideText = pairSideText;
  protected readonly pairCoverageText = pairCoverageText;
  protected readonly isAppManaged = isAppManaged;

  private readonly overlaps = inject(OverlapsService);
  private groupsRequest = 0;
  private compareRequest = 0;

  constructor() {
    effect(() => {
      this.scanId();
      this.folder();
      untracked(() => this.selectionChanged());
    });
    effect(() => {
      this.scanId();
      this.pinned();
      this.folder();
      untracked(() => void this.compare());
    });
  }

  /**
   * A new scan or folder selection (AC-6): back to page 0 with the current page size, small groups hidden again
   * (`smallShown` false), the page cleared (so no group of the previous folder lingers), then `loadGroups()`.
   */
  protected selectionChanged(): void {
    throw new Error('not implemented: DFS-068');
  }

  /**
   * Requests the current page: `listGroups({ id: scanId, under, minBytes, page, size })`, `under` left out for "All
   * folders" (AC-6), `minBytes` left out unless `smallShown()` (then `0`) (AC-7), `page` and `size` from the paginator.
   * `loading` while it runs; on failure `error` gets `failureMessage`. An answer to a superseded request is ignored
   * (AC-1, AC-6, AC-7, AC-9).
   */
  protected async loadGroups(): Promise<void> {
    throw new Error('not implemented: DFS-068');
  }

  /** "Show N small groups": sets `smallShown`, back to page 0, `loadGroups()` (AC-7). */
  protected showSmallGroups(): void {
    throw new Error('not implemented: DFS-068');
  }

  /** "Hide small groups": clears `smallShown`, back to page 0, `loadGroups()` (AC-7). */
  protected hideSmallGroups(): void {
    throw new Error('not implemented: DFS-068');
  }

  /** The paginator's page or size change: stores both, `loadGroups()` (G3). */
  protected changePage(event: PageEvent): void {
    throw new Error('not implemented: DFS-068');
  }

  /**
   * Updates the Comparison block (DFS-050 AC-5, AC-6, AC-7; unchanged by DFS-068, AC-9): nothing pinned → null; the
   * pinned folder or "All folders" selected → `hint`, no request; otherwise `loading`, then `getPair({ id: scanId, a:
   * pinned, b: folder })` → `loaded`, or `failed` with `failureMessage`. A superseded answer is ignored.
   */
  protected async compare(): Promise<void> {
    const request = ++this.compareRequest;
    const pinned = this.pinned();
    const folder = this.folder();
    if (pinned === null) {
      this.comparison.set(null);
      return;
    }
    if (folder === null || folder === pinned) {
      this.comparison.set({ kind: 'hint' });
      return;
    }
    this.comparison.set({ kind: 'loading' });
    try {
      const pair = await this.overlaps.getPair({ id: this.scanId(), a: pinned, b: folder });
      if (request === this.compareRequest) {
        this.comparison.set({ kind: 'loaded', pair });
      }
    } catch (failure) {
      if (request === this.compareRequest) {
        this.comparison.set({ kind: 'failed', message: failureMessage(failure) });
      }
    }
  }

  /** "Unpin" clicked: emits `unpinned` (DFS-050 AC-5). */
  protected unpin(): void {
    this.unpinned.emit();
  }

  /** The section's density class: `density-compact` or `density-comfortable`. */
  protected densityClass(): string {
    return `density-${this.density()}`;
  }

  /** A group's first words: "<files> file(s)", `plural(group.files, 'file')`, e.g. "5 files" (AC-1). */
  protected filesText(group: FolderGroupView): string {
    throw new Error('not implemented: DFS-068');
  }

  /**
   * A group's muted text after `filesText`: "· <copies> copies · <size> · shared by <n> folders", with `copiesText`
   * for the copies, `groupSizeText` for the size and `plural(group.folders.length, 'folder')` (AC-1).
   */
  protected groupMetaText(group: FolderGroupView): string {
    throw new Error('not implemented: DFS-068');
  }

  /**
   * "<size> each" when every set of the group is listed (`totalSets` equals `sets.length`) and all have one size;
   * otherwise "<total> in all", the sum of the folders' `sharedBytes`, both through `formatBytes` (AC-1).
   */
  protected groupSizeText(group: FolderGroupView): string {
    throw new Error('not implemented: DFS-068');
  }

  /**
   * The width of a group's reclaimable bar, in percent: `barPercent` of its `reclaimableBytes` over the largest
   * `reclaimableBytes` on the page, so the largest group's bar is full (AC-1).
   */
  protected reclaimableWidth(group: FolderGroupView): number {
    throw new Error('not implemented: DFS-068');
  }

  /** A folder line's path: `displayPath(path, roots(), FOLDER_PATH_BUDGET)` (AC-2). */
  protected shortPath(path: string): ShortPath {
    throw new Error('not implemented: DFS-068');
  }

  /** "×<copiesEach>", shown only when `copiesEach` is above 1 (AC-2). */
  protected copiesEachText(folder: GroupFolderView): string {
    throw new Error('not implemented: DFS-068');
  }

  /** "<sharedFiles> of <folderFiles> files", always the plural "files", e.g. "1 of 1 files" (AC-2). */
  protected sharedText(folder: GroupFolderView): string {
    throw new Error('not implemented: DFS-068');
  }

  /** The width of a folder line's bar, in percent: `barPercent(sharedFiles, folderFiles)` (AC-2). */
  protected shareWidth(folder: GroupFolderView): number {
    throw new Error('not implemented: DFS-068');
  }

  /** Whether the folder's `sharedFiles` equals its `folderFiles`: it gets the "fully shared" tag (AC-3). */
  protected isFullyShared(folder: GroupFolderView): boolean {
    throw new Error('not implemented: DFS-068');
  }

  /** "also in <otherGroups> other groups" (`plural(n, 'other group')`: "also in 1 other group") (AC-3). */
  protected otherGroupsText(folder: GroupFolderView): string {
    throw new Error('not implemented: DFS-068');
  }

  /** How many of the group's folders are fully shared; `FULLY_SHARED_NOTE` shows at 2 or more (AC-4). */
  protected fullySharedCount(group: FolderGroupView): number {
    throw new Error('not implemented: DFS-068');
  }

  /** A set row's text: "<size> · <copies>", `formatBytes(size)` and `copiesText(copies)` (AC-5). */
  protected setDetailText(size: number, copies: number): string {
    throw new Error('not implemented: DFS-068');
  }

  /** How many of the group's sets are not listed: `totalSets` less `sets.length`, shown as "and N more" above 0 (AC-5). */
  protected hiddenSets(group: FolderGroupView): number {
    throw new Error('not implemented: DFS-068');
  }
}
