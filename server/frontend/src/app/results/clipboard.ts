import { InjectionToken } from '@angular/core';

/** The one clipboard call the result screen makes (DFS-049 decision 10): write plain text, resolve on success. */
export interface ClipboardWriter {
  writeText(text: string): Promise<void>;
}

/**
 * The browser's clipboard behind a token (DFS-041 decision 5, checklist item 4): jsdom has none, so a spec provides a
 * fake that resolves or rejects, or `null` for a clipboard that is unavailable. `null` never means success.
 */
export const CLIPBOARD = new InjectionToken<ClipboardWriter | null>('CLIPBOARD', {
  providedIn: 'root',
  factory: browserClipboard,
});

/**
 * The default: `navigator.clipboard` when the browser offers `writeText` (a secure context such as
 * `http://127.0.0.1`), otherwise null.
 */
export function browserClipboard(): ClipboardWriter | null {
  const clipboard = typeof navigator === 'undefined' ? undefined : navigator.clipboard;
  return clipboard && typeof clipboard.writeText === 'function' ? clipboard : null;
}
