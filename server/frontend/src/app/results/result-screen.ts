import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { SCAN_NOT_FINISHED, failureMessage, unavailableMessage } from './result-error';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTabsModule } from '@angular/material/tabs';
import { FolderNode, ScanEntry, SetPage } from '../api/models';
import { ResultsService, ScansService } from '../api/services';
import { FolderTree } from './folder-tree';
import { OverlapsTab } from './overlaps-tab';
import { DENSITY_STORAGE, Density, readDensity } from './result-density';
import { headerFigures, spaceSegments } from './result-format';
import { SetsTab } from './sets-tab';

/** The Sets tab's first page size, the API's default (DFS-049 decision 5). */
export const DEFAULT_PAGE_SIZE = 50;

/**
 * The result screen of one scan at `#/scans/<id>` (DFS-049, DFS-060, `ui.md` U3): a header with "Back to scans", the
 * scan's folders, its start time, the Compact/Comfortable toggle, four figures and a three-segment bar; the folder tree
 * on the left; on the right the Sets tab (selected on open, DFS-060 decision 2) and the Overlaps tab second.
 *
 * Loading order (DFS-049 decision 9, AC-4): `getScan(id)` first; a `404 UNKNOWN_SCAN` or a status other than COMPLETED
 * shows its alert and nothing else is requested. For a COMPLETED scan, `listFolders(id)` (the roots) and the first page
 * of `listSets(id)` are requested together; a `409 SCAN_NOT_COMPLETED` from either shows "This scan has not finished
 * yet". The tree and the tabs appear once the roots have loaded; the Sets tab shows its own progress bar until its page
 * has.
 *
 * Header totals (DFS-060 decision 1): `totals` holds the latest page answered for a request without `under`, so the
 * figures and the bar always describe the whole scan, whatever folder or filter the table shows. The first page of a
 * scan is always unfiltered, so the header fills as soon as it answers.
 *
 * Stubs (design phase): `chooseDensity`, `setFilter`, and the helpers `readDensity`, `headerFigures` and
 * `spaceSegments` throw; `loadSets` and `selectFolder` still need decision 1's `totals` and decision 4's filter (see
 * their comments). Inputs, outputs, injected services, the template's structure and its classes are final.
 */
@Component({
  selector: 'app-result-screen',
  imports: [
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatButtonToggleModule,
    MatProgressBarModule,
    MatTabsModule,
    FolderTree,
    OverlapsTab,
    SetsTab,
  ],
  templateUrl: './result-screen.html',
  styleUrl: './result-screen.css',
})
export class ResultScreen implements OnInit {
  /** The scan, from `getScan`; its `locations` and `startedAt` fill the header (DFS-060 AC-2). */
  protected readonly scan = signal<ScanEntry | null>(null);
  /** The scan's roots; null until loaded. */
  protected readonly roots = signal<FolderNode[] | null>(null);
  /** The roots' paths, which the Sets table shortens paths against (DFS-060 AC-7). */
  protected readonly rootPaths = computed(() => (this.roots() ?? []).map((root) => root.path));
  /** The alert that replaces the tree and the tab (AC-4, or a failed `getScan`/roots load); null when there is none. */
  protected readonly fatalError = signal<string | null>(null);

  /** The selected folder's path, null for "All folders" (AC-6). */
  protected readonly selectedFolder = signal<string | null>(null);
  /**
   * Whether the selected folder filters the sets (DFS-060 decision 4, AC-8): true by default and again on every
   * selection; "All sets" sets it false. Without a selected folder it has no effect.
   */
  protected readonly filterBySelection = signal(true);
  protected readonly page = signal<SetPage | null>(null);
  /** The latest unfiltered sets page, for the header's figures and bar (DFS-060 decision 1); null until one answers. */
  protected readonly totals = signal<SetPage | null>(null);
  protected readonly pageIndex = signal(0);
  protected readonly pageSize = signal(DEFAULT_PAGE_SIZE);
  protected readonly setsLoading = signal(false);
  protected readonly setsError = signal<string | null>(null);
  /**
   * The folder pinned for comparison, null when none (DFS-050 decision 3, AC-5): shared by the tree (which pins) and
   * the Overlaps tab (which shows and unpins it). Lasts while the screen shows this scan; not in the URL.
   */
  protected readonly pinnedFolder = signal<string | null>(null);

  private readonly storage = inject(DENSITY_STORAGE);
  /** The Sets table's row density, restored from `DENSITY_STORAGE` when the screen is created (DFS-060 AC-9). */
  protected readonly density = signal<Density>(readDensity(this.storage));

  protected readonly headerFigures = headerFigures;
  protected readonly spaceSegments = spaceSegments;

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
   * `failureMessage`, into `fatalError`. DFS-060: also resets `totals` to null and `filterBySelection` to true.
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
   *
   * DFS-060: `under` is the selected folder only while `filterBySelection` is true, else left out (decision 4, AC-8);
   * every page answered for a request without `under` is also stored in `totals` (decision 1, AC-2).
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

  /**
   * Selects a folder (null for "All folders"), returns to page 0 and loads it (AC-6, AC-9). DFS-060: also sets
   * `filterBySelection` back to true, the default for a newly selected folder (decision 4).
   */
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

  /**
   * The Sets tab's filter toggle (DFS-060 decision 4, AC-8): sets `filterBySelection`, returns to page 0 and loads the
   * page, with `under` for "Copies under <folder>" (true) and without for "All sets" (false). The selection stays.
   */
  protected setFilter(filtered: boolean): void {
    throw new Error('not implemented: DFS-060');
  }

  /** The density toggle (DFS-060 decision 6, AC-9): sets `density` and stores it with `writeDensity(storage, …)`. */
  protected chooseDensity(density: Density): void {
    throw new Error('not implemented: DFS-060');
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
