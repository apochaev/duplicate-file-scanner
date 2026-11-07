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

const WINDOW_MS = 30_000;
const WARM_UP_MS = 3_000;
const ESTIMATING = 'estimating…';

/**
 * Estimates the time left in the full-hash phase from the bytes rate over the updates received in the last 30 s
 * (DFS-057.AC-6). One estimator per scan row; it is handed every progress the row holds, in order, and times each one
 * with the clock it was given (the stream carries no times).
 *
 * The window holds the FULL_HASH updates received since the phase started (the first FULL_HASH update this estimator
 * saw), keeping those received at or after `now - 30 s`; the rate is the bytes hashed between the window's first and
 * last update divided by the time between them. Any other phase or a terminal status empties the window, so a new
 * FULL_HASH phase starts it afresh.
 */
export class EtaEstimator {
  private window: { time: number; bytes: number }[] = [];
  private phaseStart = 0;

  constructor(private readonly now: () => number) {}

  /**
   * Records one progress update at the clock's current time and returns the ETA text for it: null outside FULL_HASH
   * (and for a terminal status); "estimating…" in FULL_HASH until the phase has run 3 s with some bytes hashed, and
   * while the recent rate is 0; otherwise `formatEta((hashBytesTotal - hashedBytes) / rate)` (DFS-057.AC-6).
   */
  record(progress: ScanProgress): string | null {
    const now = this.now();
    if (progress.phase !== 'FULL_HASH' || progress.status === 'COMPLETED' || progress.status === 'CANCELLED' || progress.status === 'FAILED') {
      this.window = [];
      return null;
    }
    if (this.window.length === 0) {
      this.phaseStart = now;
    }
    this.window = [...this.window, { time: now, bytes: progress.hashedBytes }].filter((e) => e.time >= now - WINDOW_MS);
    if (now - this.phaseStart < WARM_UP_MS || progress.hashedBytes === 0) {
      return ESTIMATING;
    }
    const first = this.window[0];
    const last = this.window[this.window.length - 1];
    if (last.time <= first.time) {
      return ESTIMATING;
    }
    const rate = (last.bytes - first.bytes) / ((last.time - first.time) / 1000);
    if (!(rate > 0)) {
      return ESTIMATING;
    }
    return formatEta((progress.hashBytesTotal - progress.hashedBytes) / rate);
  }
}

/**
 * A time left in words (DFS-057.AC-6): "about 45 s left" under a minute, "about 4 min left" (whole minutes) under an
 * hour, "about 1 h 20 min left" from there, each rounded to the nearest unit shown; the unit is chosen after rounding
 * (59.6 s reads "about 1 min left").
 */
export function formatEta(seconds: number): string {
  const s = Math.round(seconds);
  if (s < 60) {
    return `about ${s} s left`;
  }
  const minutes = Math.round(s / 60);
  if (minutes < 60) {
    return `about ${minutes} min left`;
  }
  return `about ${Math.floor(minutes / 60)} h ${minutes % 60} min left`;
}
