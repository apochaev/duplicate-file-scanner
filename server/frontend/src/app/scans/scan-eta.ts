import { InjectionToken } from '@angular/core';
import { ScanProgress } from '../api/models';
import { noBytesLeft } from './scan-format';

/**
 * The page's clock in milliseconds, read when a progress update arrives (DFS-057 decision 2); a spec provides a fake
 * through this token so no test waits on real time.
 */
export const CLOCK = new InjectionToken<() => number>('CLOCK', {
  providedIn: 'root',
  factory: () => () => Date.now(),
});

const WINDOW_MS = 30_000;
/** How long a phase must have run before a time is shown (DFS-063 decision 3; DFS-057 used 3 s). */
const WARM_UP_MS = 30_000;
const ESTIMATING = 'estimating…';
const FINISHING = 'finishing…';

/**
 * Estimates the time left in the full-hash and hard-link phases from the rate over the updates received in the last
 * 30 s (DFS-057.AC-6, DFS-063.AC-4 to AC-6). One estimator per scan row; it is handed every progress the row holds, in
 * order, and times each one with the clock it was given (the stream carries no times).
 *
 * The window holds the updates of the current phase received since the phase started (the first update of that phase
 * this estimator saw), keeping those received at or after `now - 30 s`; the rate is the progress (bytes in FULL_HASH,
 * groups in HARD_LINKS) between the window's first and last update divided by the time between them. Any other phase,
 * a phase change or a terminal status empties the window, so a new phase starts it afresh.
 */
export class EtaEstimator {
  private window: { time: number; done: number }[] = [];
  private phaseStart = 0;
  private phase: string | null = null;

  constructor(private readonly now: () => number) {}

  /**
   * Records one progress update at the clock's current time and returns the time-left text for it (DFS-063 decision 3):
   * null in WALK, PARTIAL_HASH, QUEUED and for a terminal status;
   * - FULL_HASH with no bytes left (`hashedBytes` equal to `hashBytesTotal`): "finishing…", also inside the first 30 s;
   * - otherwise (FULL_HASH and HARD_LINKS) "estimating…" until the phase has run 30 s with some progress made and
   *   while the rate over the last 30 s is 0, then `formatEta(left / rate)` with `left` the bytes (FULL_HASH) or groups
   *   (HARD_LINKS) still to do.
   * The 30 s warm-up is counted from the phase's own start (the first update of that phase this estimator saw).
   */
  record(progress: ScanProgress): string | null {
    const now = this.now();
    const phase = progress.phase;
    const active = progress.status === 'RUNNING' || progress.status === 'CANCELLING';
    if (!active || (phase !== 'FULL_HASH' && phase !== 'HARD_LINKS')) {
      this.window = [];
      this.phase = null;
      return null;
    }
    if (phase !== this.phase) {
      this.phase = phase;
      this.window = [];
      this.phaseStart = now;
    }
    const hashing = phase === 'FULL_HASH';
    const done = hashing ? progress.hashedBytes : progress.groupsDone;
    const total = hashing ? progress.hashBytesTotal : progress.groupsTotal;
    this.window = this.window.filter((w) => w.time >= now - WINDOW_MS);
    this.window.push({ time: now, done });
    if (hashing && noBytesLeft(progress)) {
      return FINISHING;
    }
    if (now - this.phaseStart < WARM_UP_MS || this.window.length < 2) {
      return ESTIMATING;
    }
    const first = this.window[0];
    const seconds = (now - first.time) / 1000;
    const rate = seconds > 0 ? (done - first.done) / seconds : 0;
    if (rate <= 0) {
      return ESTIMATING;
    }
    const left = total - done;
    return left <= 0 ? FINISHING : formatEta(left / rate);
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
