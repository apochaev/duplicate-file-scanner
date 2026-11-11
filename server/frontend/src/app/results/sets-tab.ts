import { Component, ElementRef, effect, input, output, signal, viewChild } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { CopyView, DuplicateSetView, SetPage } from '../api/models';
import { formatBytes } from '../scans/scan-format';
import { PathCopy } from './path-copy';
import { Density } from './result-density';
import { ShortPath, displayPath, fileName, isPathUnderFolder } from './result-path';
import { barPercent, copiesText } from './result-format';

/** Under "All folders", a scan with no sets (AC-10). */
export const NO_DUPLICATES = 'This scan found no duplicates';

/** Under a selected folder with no sets (AC-10). */
export const NO_DUPLICATES_UNDER_FOLDER = 'No duplicates under this folder';

/**
 * The character budget of the table's path column (DFS-060 decision 5): what the File column holds at 0.75rem
 * monospace in the app shell's ~960 px page, about 430 px, with room left for a "<n> links" badge. The prototype had
 * ~62 characters in a wider column; 62 did not fit (DFS-060 verification).
 */
export const PATH_BUDGET = 56;

/** The fewest characters a measured path budget allows (DFS-065.AC-8). */
export const MIN_PATH_BUDGET = 24;
/** Characters kept free for a "<n> links" badge, and for an "in selected" badge (DFS-065.AC-8). */
export const LINKS_BADGE_CHARS = 9;
export const SELECTED_BADGE_CHARS = 13;

/**
 * A copy row's path budget from the File column's measured width (DFS-065.AC-8): `contentWidth / charWidth` rounded
 * down, less the badges the row carries, never below `MIN_PATH_BUDGET`; `PATH_BUDGET` when either width is not a
 * positive number (nothing measured).
 */
export function pathBudget(contentWidth: number, charWidth: number, links: boolean, selected: boolean): number {
  if (!(contentWidth > 0) || !(charWidth > 0)) {
    return PATH_BUDGET;
  }
  const chars = Math.floor(contentWidth / charWidth) - (links ? LINKS_BADGE_CHARS : 0) - (selected ? SELECTED_BADGE_CHARS : 0);
  return Math.max(MIN_PATH_BUDGET, chars);
}

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

  /** The File column's heading and a ten-character probe in the path font, measured for the path budget (AC-8). */
  private readonly fileHeader = viewChild<ElementRef<HTMLElement>>('fileHeader');
  private readonly charProbe = viewChild<ElementRef<HTMLElement>>('charProbe');
  /** The File column's content width and one path character's width in px; zero until measured (DFS-065.AC-8). */
  private readonly columnWidth = signal(0);
  private readonly charWidth = signal(0);

  constructor() {
    effect((onCleanup) => {
      const header = this.fileHeader()?.nativeElement;
      const probe = this.charProbe()?.nativeElement;
      if (header === undefined || probe === undefined || typeof ResizeObserver === 'undefined') {
        return;
      }
      const observer = new ResizeObserver(() => this.measure(header, probe));
      observer.observe(header);
      onCleanup(() => observer.disconnect());
    });
  }

  private measure(header: HTMLElement, probe: HTMLElement): void {
    const style = getComputedStyle(header);
    this.columnWidth.set(header.clientWidth - parseFloat(style.paddingLeft || '0') - parseFloat(style.paddingRight || '0'));
    this.charWidth.set(probe.getBoundingClientRect().width / 10);
  }
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
    return this.folder() === null || !this.filtered() ? NO_DUPLICATES : NO_DUPLICATES_UNDER_FOLDER;
  }

  /** Emits the paginator's event as `pageChanged` (AC-9). */
  protected changePage(event: PageEvent): void {
    this.pageChanged.emit(event);
  }

  /** The section's density class: `density-compact` or `density-comfortable` (DFS-060 AC-9). */
  protected densityClass(): string {
    return `density-${this.density()}`;
  }

  /** A set header's name: the file name of the set's first link (`fileName`) (DFS-060 AC-5). */
  protected setName(set: DuplicateSetView): string {
    return fileName(set.copies[0].links[0].path);
  }

  /**
   * A copy row's path: `displayPath(path, roots(), budget)` (DFS-060 AC-7), the budget from the measured column and the
   * row's badges (`pathBudget`, DFS-065.AC-8).
   */
  protected shortPath(path: string, copy: CopyView): ShortPath {
    const budget = pathBudget(this.columnWidth(), this.charWidth(), copy.links.length > 1, this.copyInSelectedFolder(copy));
    return displayPath(path, this.roots(), budget);
  }

  /**
   * The width of a set's reclaimable bar, in percent: `barPercent` of its `reclaimableBytes` over the largest
   * `reclaimableBytes` on the page, so the largest set's bar is full (DFS-060 AC-5).
   */
  protected reclaimableWidth(set: DuplicateSetView): number {
    const largest = Math.max(0, ...(this.page()?.items ?? []).map((item) => item.reclaimableBytes));
    return barPercent(set.reclaimableBytes, largest);
  }

  /** The "<n> links" badge's tooltip: every link's full path, one per line, in the API's order (DFS-060 AC-6). */
  protected linksTitle(copy: CopyView): string {
    return copy.links.map((link) => link.path).join('\n');
  }

  /** Whether any of the copy's links is under the selected folder (`inSelectedFolder`) (DFS-060 AC-6). */
  protected copyInSelectedFolder(copy: CopyView): boolean {
    return copy.links.some((link) => this.inSelectedFolder(link.path));
  }

  /** The selected folder's last name element (`fileName`), for "Copies under <folder name>" (DFS-060 AC-8). */
  protected folderName(): string {
    const folder = this.folder();
    return folder === null ? '' : fileName(folder);
  }

  /** Emits the filter toggle's choice as `filterChanged` (DFS-060 AC-8). */
  protected chooseFilter(filtered: boolean): void {
    this.filterChanged.emit(filtered);
  }
}
