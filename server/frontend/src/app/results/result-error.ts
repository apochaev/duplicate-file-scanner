/** A result URL kept across a server restart: `404 UNKNOWN_SCAN` from `getScan` (DFS-049 decision 2, AC-4). */
export const SCAN_NOT_IN_THIS_RUN = 'This scan is not in this server run (the server may have restarted)';

/** A scan that is not COMPLETED, or a `409 SCAN_NOT_COMPLETED` from a result call (DFS-049 decision 9, AC-4). */
export const SCAN_NOT_FINISHED = 'This scan has not finished yet';

/** The text of a failed load the server gave no message for (a network failure, a `5xx` without a body). */
export const LOAD_FAILED = 'The results could not be loaded; reload the page';

/**
 * The screen-wide message for a failure that means there is no result to show (AC-4): `SCAN_NOT_IN_THIS_RUN` for a
 * `404` whose `code` is `UNKNOWN_SCAN`, `SCAN_NOT_FINISHED` for a `409` whose `code` is `SCAN_NOT_COMPLETED`, otherwise
 * null.
 */
export function unavailableMessage(failure: unknown): string | null {
  throw new Error('not implemented: DFS-049');
}

/**
 * The message to show for any other failed load (AC-10): the server's `{code, message}` `message` when the failure is
 * an `HttpErrorResponse` carrying one, otherwise `LOAD_FAILED`.
 */
export function failureMessage(failure: unknown): string {
  throw new Error('not implemented: DFS-049');
}
