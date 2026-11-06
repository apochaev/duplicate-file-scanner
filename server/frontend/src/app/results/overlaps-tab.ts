import { Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { FolderPairView, PairPage, SummaryPage } from '../api/models';
import { OverlapsService } from '../api/services';
import { plural } from '../scans/scan-format';
import { failureMessage } from './result-error';
import {
  copiedElsewhereText,
  duplicatesInsideText,
  isAllCopiedElsewhere,
  pairCoverageText,
  pairSideText,
  summaryTotalText,
} from './result-format';

/** The fixed note above the tables (DFS-050 decision 2, AC-4); the only text on the tab that says "safe" or "deleted". */
export const OVERLAP_NOTE =
  'A copy elsewhere may itself be deleted by another choice; checking what is safe comes with the deletion plan.';

/** The Folders table with no items (AC-10). */
export const NO_FOLDERS = 'No folders with copies here';

/** The Folder pairs table with no items (AC-10). */
export const NO_PAIRS = 'No folder pairs share copies here';

/** The Comparison block with the pinned folder itself or "All folders" selected (AC-7). */
export const COMPARE_HINT = 'Select another folder to compare with the pinned one';

/** Each table's first page size (decision 5, AC-9). */
export const OVERLAPS_PAGE_SIZE = 10;

/** What the Comparison block shows while a folder is pinned (decision 3, AC-6, AC-7). */
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
 * The Overlaps tab, the result screen's first tab (DFS-050, `ui.md` U3): top to bottom, "Pinned: <path>" with "Unpin"
 * and the Comparison block while a folder is pinned, the fixed note, the selected folder's own summary as a header
 * (none under "All folders"), the "Folders" table of summaries and the "Folder pairs" table, each table paged on its
 * own (10, 25, 50) with its own loading, empty and error states.
 *
 * A container: it requests its pages itself through `OverlapsService`, following its inputs. The selection and the pin
 * are owned by the result screen; "Unpin" is emitted. Two effects: (`scanId`, `folder`) → `selectionChanged()`;
 * (`scanId`, `pinned`, `folder`) → `compare()`. Neither tracks what those methods read or write.
 */
@Component({
  selector: 'app-overlaps-tab',
  imports: [MatButtonModule, MatChipsModule, MatPaginatorModule, MatProgressBarModule, MatTableModule],
  templateUrl: './overlaps-tab.html',
  styleUrl: './overlaps-tab.css',
})
export class OverlapsTab {
  readonly scanId = input.required<string>();
  /** The selected folder's path, or null for "All folders". */
  readonly folder = input<string | null>(null);
  /** The pinned folder's path, or null when nothing is pinned. */
  readonly pinned = input<string | null>(null);
  /** The user clicked "Unpin" (AC-5). */
  readonly unpinned = output<void>();

  protected readonly pageSizeOptions = [10, 25, 50];
  protected readonly summaryColumns = ['folder', 'copied', 'inside', 'total'];
  protected readonly pairColumns = ['a', 'b'];

  /** The Folders table's page; null before it has loaded and after a new selection until its page 0 has. */
  protected readonly summaries = signal<SummaryPage | null>(null);
  protected readonly summariesIndex = signal(0);
  protected readonly summariesSize = signal(OVERLAPS_PAGE_SIZE);
  protected readonly summariesLoading = signal(false);
  protected readonly summariesError = signal<string | null>(null);

  /** The Folder pairs table's page; null before it has loaded and after a new selection until its page 0 has. */
  protected readonly pairs = signal<PairPage | null>(null);
  protected readonly pairsIndex = signal(0);
  protected readonly pairsSize = signal(OVERLAPS_PAGE_SIZE);
  protected readonly pairsLoading = signal(false);
  protected readonly pairsError = signal<string | null>(null);

  /** The Comparison block's state; null while nothing is pinned (no block). */
  protected readonly comparison = signal<Comparison | null>(null);

  /** The selected folder's own summary (`SummaryPage.folder`); null under "All folders" (AC-1). */
  protected readonly header = computed(() => this.summaries()?.folder ?? null);

  protected readonly note = OVERLAP_NOTE;
  protected readonly noFolders = NO_FOLDERS;
  protected readonly noPairs = NO_PAIRS;
  protected readonly compareHint = COMPARE_HINT;
  protected readonly plural = plural;
  protected readonly copiedElsewhereText = copiedElsewhereText;
  protected readonly duplicatesInsideText = duplicatesInsideText;
  protected readonly summaryTotalText = summaryTotalText;
  protected readonly isAllCopiedElsewhere = isAllCopiedElsewhere;
  protected readonly pairSideText = pairSideText;
  protected readonly pairCoverageText = pairCoverageText;

  private readonly overlaps = inject(OverlapsService);
  private summariesRequest = 0;
  private pairsRequest = 0;
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
   * A new scan or folder selection (AC-1, AC-9): both tables back to page 0 with their current size, their pages
   * cleared (so no header or row of the previous folder lingers), then `loadSummaries()` and `loadPairs()`.
   */
  protected selectionChanged(): void {
    this.summaries.set(null);
    this.pairs.set(null);
    this.summariesIndex.set(0);
    this.pairsIndex.set(0);
    void this.loadSummaries();
    void this.loadPairs();
  }

  /**
   * Requests the Folders table's current page: `listSummaries({ id: scanId, under, page, size })`, `under` left out for
   * "All folders" (AC-1, AC-9). `summariesLoading` while it runs; on failure `summariesError` gets `failureMessage`
   * (AC-10). An answer to a superseded request is ignored.
   */
  protected async loadSummaries(): Promise<void> {
    const request = ++this.summariesRequest;
    const under = this.folder();
    this.summariesLoading.set(true);
    this.summariesError.set(null);
    try {
      const page = await this.overlaps.listSummaries({
        id: this.scanId(),
        ...(under === null ? {} : { under }),
        page: this.summariesIndex(),
        size: this.summariesSize(),
      });
      if (request === this.summariesRequest) {
        this.summaries.set(page);
        this.summariesLoading.set(false);
      }
    } catch (failure) {
      if (request === this.summariesRequest) {
        this.summariesLoading.set(false);
        this.summariesError.set(failureMessage(failure));
      }
    }
  }

  /** As `loadSummaries`, for the Folder pairs table: `listPairs({ id: scanId, under, page, size })` (AC-1, AC-9, AC-10). */
  protected async loadPairs(): Promise<void> {
    const request = ++this.pairsRequest;
    const under = this.folder();
    this.pairsLoading.set(true);
    this.pairsError.set(null);
    try {
      const page = await this.overlaps.listPairs({
        id: this.scanId(),
        ...(under === null ? {} : { under }),
        page: this.pairsIndex(),
        size: this.pairsSize(),
      });
      if (request === this.pairsRequest) {
        this.pairs.set(page);
        this.pairsLoading.set(false);
      }
    } catch (failure) {
      if (request === this.pairsRequest) {
        this.pairsLoading.set(false);
        this.pairsError.set(failureMessage(failure));
      }
    }
  }

  /**
   * Updates the Comparison block (AC-5, AC-6, AC-7): nothing pinned → null; the pinned folder or "All folders" selected
   * → `hint`, no request; otherwise `loading`, then `getPair({ id: scanId, a: pinned, b: folder })` → `loaded`, or
   * `failed` with `failureMessage` (a `400 NESTED_FOLDERS` shows the server's message). A superseded answer is ignored.
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

  /** The Folders paginator's page or size change: requests that page of this table only (AC-9). */
  protected changeSummariesPage(event: PageEvent): void {
    this.summariesIndex.set(event.pageIndex);
    this.summariesSize.set(event.pageSize);
    void this.loadSummaries();
  }

  /** The Folder pairs paginator's page or size change: requests that page of this table only (AC-9). */
  protected changePairsPage(event: PageEvent): void {
    this.pairsIndex.set(event.pageIndex);
    this.pairsSize.set(event.pageSize);
    void this.loadPairs();
  }

  /** "Unpin" clicked: emits `unpinned` (AC-5). */
  protected unpin(): void {
    this.unpinned.emit();
  }
}
