import { Component, computed, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { CLIPBOARD } from './clipboard';
import { containingDirectory } from './result-path';

/** Shown when no clipboard is available (`CLIPBOARD` is null) (AC-11). */
export const CLIPBOARD_UNAVAILABLE = 'Clipboard access is not available; select the folder path below and copy it';

/** Shown when the clipboard refused the write (its Promise rejected) (AC-11). */
export const CLIPBOARD_REJECTED = 'The folder path could not be copied; select it below and copy it';

/**
 * The "Copy folder path" action of one file link (DFS-049 decision 10, AC-11): copies the link's immediate parent
 * directory as plain text through `CLIPBOARD`. "Folder path copied" appears only once the write has resolved; a
 * missing clipboard or a refused write shows an alert and the folder path, selectable, for copying by hand. Changes no
 * scan or disk state.
 *
 * Stubs (design phase): `copy()` and `containingDirectory` throw; the template's structure and classes are final.
 */
@Component({
  selector: 'app-path-copy',
  imports: [MatButtonModule],
  templateUrl: './path-copy.html',
  styleUrl: './path-copy.css',
})
export class PathCopy {
  /** The file link's path, as the scan holds it. */
  readonly path = input.required<string>();

  /** The text copied, and shown for a manual copy after a failure. */
  protected readonly folder = computed(() => containingDirectory(this.path()));
  protected readonly copied = signal(false);
  protected readonly failure = signal<string | null>(null);

  private readonly clipboard = inject(CLIPBOARD);

  /**
   * Writes `folder()` to the clipboard: on success sets `copied` and clears `failure`; with no clipboard or a rejected
   * write clears `copied` and sets `failure` to `CLIPBOARD_UNAVAILABLE` or `CLIPBOARD_REJECTED` (AC-11).
   */
  protected async copy(): Promise<void> {
    if (this.clipboard === null) {
      this.copied.set(false);
      this.failure.set(CLIPBOARD_UNAVAILABLE);
      return;
    }
    try {
      await this.clipboard.writeText(this.folder());
    } catch {
      this.copied.set(false);
      this.failure.set(CLIPBOARD_REJECTED);
      return;
    }
    this.failure.set(null);
    this.copied.set(true);
  }
}
