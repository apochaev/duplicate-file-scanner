import { Injectable, signal } from '@angular/core';

/** The notice shown in place of a screen when the login ended, or when the page was opened without one (DFS-066.AC-2, AC-3). */
export const SESSION_ENDED_NOTICE = 'Your session ended, or the server was restarted. Open the link printed in the console.';

/**
 * Whether a failed HTTP call means the login session ended: an `HttpErrorResponse` with status 403 for a URL under
 * `/api/` (DFS-066.AC-2). Any other failure (a `404 UNKNOWN_SCAN`, a `500`, a network error, a non-HTTP error) is
 * not (DFS-066.AC-4).
 *
 * Stub (design phase): throws the not-implemented error; the signature is final.
 */
export function isSessionEnded(failure: unknown): boolean {
  throw new Error('not implemented: DFS-066');
}

/**
 * The one signal that the session ended (DFS-066 decision 3): the interceptor sets it, the shell reads it and replaces
 * the routed screen with `SESSION_ENDED_NOTICE`. Root-provided, so one instance serves the whole page.
 *
 * Stub (design phase): `markEnded` throws the not-implemented error; `ended` and the signatures are final.
 */
@Injectable({ providedIn: 'root' })
export class SessionEnded {
  /** True once any `/api/**` call answered 403. Never reset: the page is reloaded with a fresh link. */
  readonly ended = signal(false);

  /** Records that the session ended (DFS-066.AC-2). */
  markEnded(): void {
    throw new Error('not implemented: DFS-066');
  }
}
