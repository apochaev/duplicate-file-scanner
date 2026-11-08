import { Component, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { CLIPBOARD } from './clipboard';

/** Shown when no clipboard is available (`CLIPBOARD` is null) (DFS-049 AC-11, DFS-060 AC-7). */
export const CLIPBOARD_UNAVAILABLE = 'Clipboard access is not available; select the path below and copy it';

/** Shown when the clipboard refused the write (its Promise rejected) (DFS-049 AC-11, DFS-060 AC-7). */
export const CLIPBOARD_REJECTED = 'The path could not be copied; select it below and copy it';

/**
 * The copy button of one copy row (DFS-049 decision 10, DFS-060 decision 5): copies the file's full absolute path, as
 * the scan spells it, as plain text through `CLIPBOARD`. "Copied" appears only once the write has resolved; a missing
 * clipboard or a refused write shows an alert and the path, selectable, for copying by hand. Changes no scan or disk
 * state.
 *
 * Stubs (design phase): `copy()` throws; the template's structure and classes are final.
 */
@Component({
  selector: 'app-path-copy',
  imports: [MatButtonModule],
  templateUrl: './path-copy.html',
  styleUrl: './path-copy.css',
})
export class PathCopy {
  /** The file link's full path, as the scan holds it: what is copied, and shown for a manual copy after a failure. */
  readonly path = input.required<string>();

  protected readonly copied = signal(false);
  protected readonly failure = signal<string | null>(null);

  private readonly clipboard = inject(CLIPBOARD);

  /**
   * Writes `path()` to the clipboard: on success sets `copied` and clears `failure`; with no clipboard or a rejected
   * write clears `copied` and sets `failure` to `CLIPBOARD_UNAVAILABLE` or `CLIPBOARD_REJECTED` (DFS-060 AC-7).
   */
  protected async copy(): Promise<void> {
    throw new Error('not implemented: DFS-060');
  }
}
