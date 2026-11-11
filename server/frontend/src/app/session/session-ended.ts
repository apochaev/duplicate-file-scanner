import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, signal } from '@angular/core';

/** The notice shown in place of a screen when the login ended, or when the page was opened without one (DFS-066.AC-2, AC-3). */
export const SESSION_ENDED_NOTICE = 'Your session ended, or the server was restarted. Open the link printed in the console.';

/**
 * Whether a failed HTTP call means the login session ended: an `HttpErrorResponse` with status 403 for a URL under
 * `/api/` (DFS-066.AC-2). Any other failure (a `404 UNKNOWN_SCAN`, a `500`, a network error, a non-HTTP error) is
 * not (DFS-066.AC-4).
 */
export function isSessionEnded(failure: unknown): boolean {
  return failure instanceof HttpErrorResponse && failure.status === 403 && pathOf(failure.url).startsWith('/api/');
}

function pathOf(url: string | null): string {
  if (!url) {
    return '';
  }
  try {
    return new URL(url, 'http://localhost').pathname;
  } catch {
    return url;
  }
}

/**
 * The one signal that the session ended (DFS-066 decision 3): the interceptor sets it, the shell reads it and replaces
 * the routed screen with `SESSION_ENDED_NOTICE`. Root-provided, so one instance serves the whole page.
 */
@Injectable({ providedIn: 'root' })
export class SessionEnded {
  /** True once any `/api/**` call answered 403. Never reset: the page is reloaded with a fresh link. */
  readonly ended = signal(false);

  /** Records that the session ended (DFS-066.AC-2). */
  markEnded(): void {
    this.ended.set(true);
  }
}
