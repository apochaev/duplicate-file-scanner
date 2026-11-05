import { Component, input, output } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { SetPage } from '../api/models';
import { PathCopy } from './path-copy';
import { setHeading, setsSummary } from './result-format';

/** Under "All folders", a scan with no sets (AC-10). */
export const NO_DUPLICATES = 'This scan found no duplicates';

/** Under a selected folder with no sets (AC-10). */
export const NO_DUPLICATES_UNDER_FOLDER = 'No duplicates under this folder';

/**
 * The Sets tab (DFS-049 decisions 4 and 5, `ui.md` U3): one page of duplicate sets as open cards in the API's order,
 * a summary line, a `mat-paginator` (25, 50, 100), and its loading, empty and error states. Presentational: the
 * result screen requests the pages and passes them in; a page or size change is emitted (AC-9).
 *
 * Each physical copy is a `.copy-line` with its first link's path and modified date; the copy's other links are
 * `.hard-link` lines beneath it, marked "hard link (same file)" (AC-7). With a folder selected, every link under it is
 * marked "in selected folder" (AC-8). Every link has its own "Copy folder path" action (AC-11).
 *
 * Stubs (design phase): `inSelectedFolder`, `emptyMessage`, `changePage`, `setHeading` and `setsSummary` throw; inputs,
 * the output, the template's structure and classes are final.
 */
@Component({
  selector: 'app-sets-tab',
  imports: [DatePipe, MatCardModule, MatPaginatorModule, MatProgressBarModule, PathCopy],
  templateUrl: './sets-tab.html',
  styleUrl: './sets-tab.css',
})
export class SetsTab {
  /** The page shown; null before the first page has loaded. */
  readonly page = input<SetPage | null>(null);
  /** The selected folder's path, or null for "All folders". */
  readonly folder = input<string | null>(null);
  /** A page is being requested. */
  readonly loading = input(false);
  /** The server's message for a failed page load. */
  readonly error = input<string | null>(null);
  readonly pageIndex = input(0);
  readonly pageSize = input(50);
  /** The paginator's page or page-size change. */
  readonly pageChanged = output<PageEvent>();

  protected readonly pageSizeOptions = [25, 50, 100];
  protected readonly setHeading = setHeading;
  protected readonly setsSummary = setsSummary;

  /** Whether the link's path is under the selected folder; always false with "All folders" (AC-8). */
  protected inSelectedFolder(path: string): boolean {
    throw new Error('not implemented: DFS-049');
  }

  /** `NO_DUPLICATES` with "All folders", `NO_DUPLICATES_UNDER_FOLDER` with a folder selected (AC-10). */
  protected emptyMessage(): string {
    throw new Error('not implemented: DFS-049');
  }

  /** Emits the paginator's event as `pageChanged` (AC-9). */
  protected changePage(event: PageEvent): void {
    throw new Error('not implemented: DFS-049');
  }
}
