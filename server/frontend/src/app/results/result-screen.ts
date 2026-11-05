import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTabsModule } from '@angular/material/tabs';
import { FolderNode, ScanEntry, SetPage } from '../api/models';
import { ResultsService, ScansService } from '../api/services';
import { FolderTree } from './folder-tree';
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
 *
 * Stubs (design phase): every method below throws; the signals, injected services, the template's structure and its
 * classes are final.
 */
@Component({
  selector: 'app-result-screen',
  imports: [RouterLink, MatButtonModule, MatProgressBarModule, MatTabsModule, FolderTree, SetsTab],
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

  private readonly route = inject(ActivatedRoute);
  private readonly scans = inject(ScansService);
  private readonly results = inject(ResultsService);
  private readonly destroyRef = inject(DestroyRef);

  /** Follows the route's `id` parameter, calling `loadScan` for each (AC-1). */
  ngOnInit(): void {
    throw new Error('not implemented: DFS-049');
  }

  /**
   * Loads one scan's result in decision 9's order (AC-3, AC-4, AC-5, AC-6): `getScan`, then for a COMPLETED scan the
   * roots and `loadSets()` for page 0 without `under`. Maps a failure through `unavailableMessage`, else
   * `failureMessage`, into `fatalError`.
   */
  protected async loadScan(id: string): Promise<void> {
    throw new Error('not implemented: DFS-049');
  }

  /**
   * Requests the current page: `listSets({ id, under, page, size })` with `under` left out for "All folders" (AC-6,
   * AC-9); `setsLoading` while it runs; a `409 SCAN_NOT_COMPLETED` sets `fatalError`, any other failure `setsError`
   * (AC-10). An answer to a request that a later one superseded is ignored.
   */
  protected async loadSets(): Promise<void> {
    throw new Error('not implemented: DFS-049');
  }

  /** Selects a folder (null for "All folders"), returns to page 0 and loads it (AC-6, AC-9). */
  protected selectFolder(path: string | null): void {
    throw new Error('not implemented: DFS-049');
  }

  /** Takes the paginator's page index and size and loads that page (AC-9). */
  protected changePage(event: PageEvent): void {
    throw new Error('not implemented: DFS-049');
  }
}
