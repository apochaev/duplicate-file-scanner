import { Component, input, output } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { CopyView, DuplicateSetView, SetPage } from '../api/models';
import { formatBytes } from '../scans/scan-format';
import { PathCopy } from './path-copy';
import { Density } from './result-density';
import { ShortPath, isPathUnderFolder } from './result-path';
import { copiesText } from './result-format';

/** Under "All folders", a scan with no sets (AC-10). */
export const NO_DUPLICATES = 'This scan found no duplicates';

/** Under a selected folder with no sets (AC-10). */
export const NO_DUPLICATES_UNDER_FOLDER = 'No duplicates under this folder';

/** The character budget of the table's path column (DFS-060 decision 5; the prototype's ~62-character column). */
export const PATH_BUDGET = 62;

/**
 * The Sets tab (DFS-049 decisions 4 and 5, DFS-060 decision 4, `ui.md` U3): one page of duplicate sets as one grouped
 * table in the API's order, a `mat-paginator` (25, 50, 100), and its loading, empty and error states. Presentational:
 * the result screen requests the pages and passes them in; a page or size change and the filter toggle are emitted.
 *
 * Each set is a `tbody.duplicate-set`: a `tr.set-header` (its first link's file name, "<n> copies", "<size> each", the
 * reclaimable bytes with a bar scaled to the largest on the page), then one `tr.copy-row` per physical copy (its first
 * link's short path, modified date and copy button). A copy with several links carries a "<n> links" badge whose
 * tooltip lists every link's full path; a copy with a link under the selected folder carries "in selected" (AC-5,
 * AC-6). With a folder selected, a toggle above the table chooses "All sets" or "Copies under <folder name>" (AC-8).
 *
 * Stubs (design phase): `densityClass`, `setName`, `shortPath`, `reclaimableWidth`, `linksTitle`,
 * `copyInSelectedFolder`, `folderName`, `chooseFilter` and the helper `copiesText` throw; inputs, outputs, the
 * template's structure and its classes are final.
 */
@Component({
  selector: 'app-sets-tab',
  imports: [DatePipe, MatButtonToggleModule, MatPaginatorModule, MatProgressBarModule, PathCopy],
  templateUrl: './sets-tab.html',
  styleUrl: './sets-tab.css',
})
export class SetsTab {
  /** The page shown; null before the first page has loaded. */
  readonly page = input<SetPage | null>(null);
  /** The selected folder's path, or null for "All folders". */
  readonly folder = input<string | null>(null);
  /** Whether the selected folder filters the page ("Copies under <folder>") or not ("All sets") (DFS-060 AC-8). */
  readonly filtered = input(true);
  /** The scan's roots, which paths are shown relative to (DFS-060 AC-7). */
  readonly roots = input<readonly string[]>([]);
  /** The table's row density (DFS-060 AC-9). */
  readonly density = input<Density>('compact');
  /** A page is being requested. */
  readonly loading = input(false);
  /** The server's message for a failed page load. */
  readonly error = input<string | null>(null);
  readonly pageIndex = input(0);
  readonly pageSize = input(50);
  /** The paginator's page or page-size change. */
  readonly pageChanged = output<PageEvent>();
  /** The filter toggle's choice: true for "Copies under <folder>", false for "All sets" (DFS-060 AC-8). */
  readonly filterChanged = output<boolean>();

  protected readonly pageSizeOptions = [25, 50, 100];
  protected readonly copiesText = copiesText;
  protected readonly formatBytes = formatBytes;

  /** Whether the link's path is under the selected folder; always false with "All folders" (DFS-049 AC-8). */
  protected inSelectedFolder(path: string): boolean {
    const folder = this.folder();
    return folder !== null && isPathUnderFolder(path, folder);
  }

  /**
   * `NO_DUPLICATES` with "All folders", `NO_DUPLICATES_UNDER_FOLDER` with a folder selected (AC-10). DFS-060: with a
   * folder selected but `filtered` false ("All sets"), `NO_DUPLICATES`.
   */
  protected emptyMessage(): string {
    return this.folder() === null ? NO_DUPLICATES : NO_DUPLICATES_UNDER_FOLDER;
  }

  /** Emits the paginator's event as `pageChanged` (AC-9). */
  protected changePage(event: PageEvent): void {
    this.pageChanged.emit(event);
  }

  /** The section's density class: `density-compact` or `density-comfortable` (DFS-060 AC-9). */
  protected densityClass(): string {
    throw new Error('not implemented: DFS-060');
  }

  /** A set header's name: the file name of the set's first link (`fileName`) (DFS-060 AC-5). */
  protected setName(set: DuplicateSetView): string {
    throw new Error('not implemented: DFS-060');
  }

  /** A copy row's path: `displayPath(path, roots(), PATH_BUDGET)` (DFS-060 AC-7). */
  protected shortPath(path: string): ShortPath {
    throw new Error('not implemented: DFS-060');
  }

  /**
   * The width of a set's reclaimable bar, in percent: `barPercent` of its `reclaimableBytes` over the largest
   * `reclaimableBytes` on the page, so the largest set's bar is full (DFS-060 AC-5).
   */
  protected reclaimableWidth(set: DuplicateSetView): number {
    throw new Error('not implemented: DFS-060');
  }

  /** The "<n> links" badge's tooltip: every link's full path, one per line, in the API's order (DFS-060 AC-6). */
  protected linksTitle(copy: CopyView): string {
    throw new Error('not implemented: DFS-060');
  }

  /** Whether any of the copy's links is under the selected folder (`inSelectedFolder`) (DFS-060 AC-6). */
  protected copyInSelectedFolder(copy: CopyView): boolean {
    throw new Error('not implemented: DFS-060');
  }

  /** The selected folder's last name element (`fileName`), for "Copies under <folder name>" (DFS-060 AC-8). */
  protected folderName(): string {
    throw new Error('not implemented: DFS-060');
  }

  /** Emits the filter toggle's choice as `filterChanged` (DFS-060 AC-8). */
  protected chooseFilter(filtered: boolean): void {
    throw new Error('not implemented: DFS-060');
  }
}
