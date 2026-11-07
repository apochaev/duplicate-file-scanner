import { InjectionToken } from '@angular/core';
import { ScanProgress } from '../api/models';

/**
 * The page's clock in milliseconds, read when a progress update arrives (DFS-057 decision 2); a spec provides a fake
 * through this token so no test waits on real time.
 */
export const CLOCK = new InjectionToken<() => number>('CLOCK', {
  providedIn: 'root',
  factory: () => () => Date.now(),
});

/**
 * Estimates the time left in the full-hash phase from the bytes rate over the updates received in the last 30 s
 * (DFS-057.AC-6). One estimator per scan row; it is handed every progress the row holds, in order, and times each one
 * with the clock it was given (the stream carries no times).
 *
 * The window holds the FULL_HASH updates received since the phase started (the first FULL_HASH update this estimator
 * saw), keeping those received at or after `now - 30 s`; the rate is the bytes hashed between the window's first and
 * last update divided by the time between them. Any other phase or a terminal status empties the window, so a new
 * FULL_HASH phase starts it afresh.
 *
 * Stubs (design phase): every method throws until implemented.
 */
export class EtaEstimator {
  constructor(private readonly now: () => number) {}

  /**
   * Records one progress update at the clock's current time and returns the ETA text for it: null outside FULL_HASH
   * (and for a terminal status); "estimating…" in FULL_HASH until the phase has run 3 s with some bytes hashed, and
   * while the recent rate is 0; otherwise `formatEta((hashBytesTotal - hashedBytes) / rate)` (DFS-057.AC-6).
   */
  record(progress: ScanProgress): string | null {
    throw new Error('not implemented: DFS-057');
  }
}

/**
 * A time left in words (DFS-057.AC-6): "about 45 s left" under a minute, "about 4 min left" (whole minutes) under an
 * hour, "about 1 h 20 min left" from there, each rounded to the nearest unit shown; the unit is chosen after rounding
 * (59.6 s reads "about 1 min left").
 */
export function formatEta(seconds: number): string {
  throw new Error('not implemented: DFS-057');
}
