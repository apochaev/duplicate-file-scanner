import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { SCAN_NOT_FINISHED, failureMessage, unavailableMessage } from './result-error';
import { MatButtonModule } from '@angular/material/button';
import { PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTabsModule } from '@angular/material/tabs';
import { FolderNode, ScanEntry, SetPage } from '../api/models';
import { ResultsService, ScansService } from '../api/services';
import { FolderTree } from './folder-tree';
import { OverlapsTab } from './overlaps-tab';
import { SetsTab } from './sets-tab';

/** The Sets tab's first page size, the API's default (DFS-049 decision 5). */
export const DEFAULT_PAGE_SIZE = 50;

/**
 * The result screen of one scan at `#/scans/<id>` (DFS-049, `ui.md` U3): a header with the scan's folders and "Back to
 * scans", the folder tree on the left and the Sets tab on the right, filtered to the selected folder.
 *
 * Loading order (decision 9, AC-4): `getScan(id)` first; a `404 UNKNOWN_SCAN` or a status other than COMPLETED shows
 * its alert and nothing else is requested. For a COMPLETED scan, `listFolders(id)` (the roots) and the first page of
 * `listSets(id)` are requested together; a `409 SCAN_NOT_COMPLETED` from either shows "This scan has not finished
 * yet". The tree and the tab appear once the roots have loaded; the tab shows its own progress bar until its page has.
 */
@Component({
  selector: 'app-result-screen',
  imports: [RouterLink, MatButtonModule, MatProgressBarModule, MatTabsModule, FolderTree, OverlapsTab, SetsTab],
  templateUrl: './result-screen.html',
  styleUrl: './result-screen.css',
})
export class ResultScreen implements OnInit {
  /** The scan, from `getScan`; its `locations` fill the header (AC-3). */
  protected readonly scan = signal<ScanEntry | null>(null);
  /** The scan's roots; null until loaded. */
  protected readonly roots = signal<FolderNode[] | null>(null);
  /** The alert that replaces the tree and the tab (AC-4, or a failed `getScan`/roots load); null when there is none. */
  protected readonly fatalError = signal<string | null>(null);

  /** The selected folder's path, null for "All folders" (AC-6). */
  protected readonly selectedFolder = signal<string | null>(null);
  protected readonly page = signal<SetPage | null>(null);
  protected readonly pageIndex = signal(0);
  protected readonly pageSize = signal(DEFAULT_PAGE_SIZE);
  protected readonly setsLoading = signal(false);
  protected readonly setsError = signal<string | null>(null);
  /**
   * The folder pinned for comparison, null when none (DFS-050 decision 3, AC-5): shared by the tree (which pins) and
   * the Overlaps tab (which shows and unpins it). Lasts while the screen shows this scan; not in the URL.
   */
  protected readonly pinnedFolder = signal<string | null>(null);

  private readonly route = inject(ActivatedRoute);
  private readonly scans = inject(ScansService);
  private readonly results = inject(ResultsService);
  private readonly destroyRef = inject(DestroyRef);
  private scanId = '';
  /** Counts `loadScan` calls, so an answer for a replaced scan is ignored. */
  private generation = 0;
  /** Counts `loadSets` calls, so an answer to a superseded request is ignored. */
  private setsRequest = 0;

  /** Follows the route's `id` parameter, calling `loadScan` for each (AC-1). */
  ngOnInit(): void {
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      void this.loadScan(params.get('id') ?? '');
    });
  }

  /**
   * Loads one scan's result in decision 9's order (AC-3, AC-4, AC-5, AC-6): `getScan`, then for a COMPLETED scan the
   * roots and `loadSets()` for page 0 without `under`. Maps a failure through `unavailableMessage`, else
   * `failureMessage`, into `fatalError`.
   */
  protected async loadScan(id: string): Promise<void> {
    const generation = ++this.generation;
    this.scan.set(null);
    this.roots.set(null);
    this.fatalError.set(null);
    this.selectedFolder.set(null);
    this.pinnedFolder.set(null);
    this.page.set(null);
    this.pageIndex.set(0);
    this.pageSize.set(DEFAULT_PAGE_SIZE);
    this.setsLoading.set(false);
    this.setsError.set(null);
    this.scanId = id;
    try {
      const entry = await this.scans.getScan({ id });
      if (generation !== this.generation) {
        return;
      }
      this.scan.set(entry);
      if (entry.progress.status !== 'COMPLETED') {
        this.fatalError.set(SCAN_NOT_FINISHED);
        return;
      }
      const sets = this.loadSets();
      const roots = await this.results.listFolders({ id });
      if (generation !== this.generation) {
        return;
      }
      this.roots.set(roots);
      await sets;
    } catch (failure) {
      if (generation === this.generation) {
        this.fatalError.set(unavailableMessage(failure) ?? failureMessage(failure));
      }
    }
  }

  /**
   * Requests the current page: `listSets({ id, under, page, size })` with `under` left out for "All folders" (AC-6,
   * AC-9); `setsLoading` while it runs; a `409 SCAN_NOT_COMPLETED` sets `fatalError`, any other failure `setsError`
   * (AC-10). An answer to a request that a later one superseded is ignored.
   */
  protected async loadSets(): Promise<void> {
    const generation = this.generation;
    const request = ++this.setsRequest;
    const under = this.selectedFolder();
    this.setsLoading.set(true);
    this.setsError.set(null);
    try {
      const page = await this.results.listSets({
        id: this.scanId,
        ...(under === null ? {} : { under }),
        page: this.pageIndex(),
        size: this.pageSize(),
      });
      if (request === this.setsRequest && generation === this.generation) {
        this.page.set(page);
        this.setsLoading.set(false);
      }
    } catch (failure) {
      if (request !== this.setsRequest || generation !== this.generation) {
        return;
      }
      this.setsLoading.set(false);
      const unavailable = unavailableMessage(failure);
      if (unavailable === SCAN_NOT_FINISHED) {
        this.fatalError.set(unavailable);
      } else {
        this.setsError.set(unavailable ?? failureMessage(failure));
      }
    }
  }

  /** Selects a folder (null for "All folders"), returns to page 0 and loads it (AC-6, AC-9). */
  protected selectFolder(path: string | null): void {
    this.selectedFolder.set(path);
    this.pageIndex.set(0);
    void this.loadSets();
  }

  /** Takes the paginator's page index and size and loads that page (AC-9). */
  protected changePage(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    void this.loadSets();
  }

  /** Pins a folder for comparison, replacing any pinned one; the selection is unchanged (DFS-050 AC-5). */
  protected pinFolder(path: string): void {
    this.pinnedFolder.set(path);
  }

  /** Removes the pin, so the Overlaps tab drops its "Pinned:" label and Comparison block (DFS-050 AC-5). */
  protected unpin(): void {
    this.pinnedFolder.set(null);
  }
}
