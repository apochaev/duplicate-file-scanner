import { PhaseTotals, ScanProgress } from '../api/models';
import { progress } from '../results/result-test-data';
import { ScanPhase } from './scan-format';

/**
 * Builders shared by the DFS-063 specs (the canvas's worked numbers, `docs/design/mockups/scan-progress-row-2026-11-08/`),
 * so a test states only what it is about. No Vitest globals: `tsconfig.app.json` compiles this file too.
 */

export const GB = 1024 ** 3;

/** `n` gibibytes as a whole number of bytes, so `formatBytes` reads it back as "n GB". */
export function gb(n: number): number {
  return Math.round(n * GB);
}

export function totals(phase: ScanPhase, update: Partial<PhaseTotals> = {}): PhaseTotals {
  return { phase, finished: true, directories: 0, files: 0, bytes: 0, groups: 0, ...update };
}

const ENTRIES: Record<ScanPhase, PhaseTotals> = {
  WALK: totals('WALK', { directories: 4120, files: 38886, bytes: gb(902.9) }),
  PARTIAL_HASH: totals('PARTIAL_HASH', { files: 29594, bytes: gb(300) }),
  FULL_HASH: totals('FULL_HASH', { files: 13845, bytes: gb(17.1) }),
  HARD_LINKS: totals('HARD_LINKS', { groups: 3104 }),
};

const ORDER: ScanPhase[] = ['WALK', 'PARTIAL_HASH', 'FULL_HASH', 'HARD_LINKS'];

/** The `phases` of a scan that has reached `phase`: earlier entries finished, `phase`'s own still running. */
export function startedPhases(phase: ScanPhase): PhaseTotals[] {
  const upTo = ORDER.indexOf(phase);
  return ORDER.slice(0, upTo + 1).map((p, i) => ({ ...ENTRIES[p], finished: i < upTo }));
}

/** The `phases` of a COMPLETED scan: every entry finished. */
export function allPhasesDone(): PhaseTotals[] {
  return ORDER.map((p) => ENTRIES[p]);
}

/** A RUNNING scan in `phase` with the canvas's walk totals; `update` overrides any field. */
export function scanIn(phase: ScanPhase, update: Partial<ScanProgress> = {}): ScanProgress {
  return progress({
    status: 'RUNNING',
    phase,
    directories: 4120,
    files: 38886,
    bytes: gb(902.9),
    phases: startedPhases(phase),
    ...update,
  });
}

/** Hashing with 5.1 of 17.1 GB and 6210 of 13845 files read (47%). */
export function hashingHalf(update: Partial<ScanProgress> = {}): ScanProgress {
  return scanIn('FULL_HASH', {
    hashedFiles: 6210,
    hashFilesTotal: 13845,
    hashedBytes: gb(5.1),
    hashBytesTotal: gb(17.1),
    ...update,
  });
}

/** A COMPLETED scan with the canvas's totals. */
export function completed(update: Partial<ScanProgress> = {}): ScanProgress {
  return progress({
    status: 'COMPLETED',
    phase: null,
    directories: 4120,
    files: 38886,
    bytes: gb(902.9),
    hashedFiles: 13845,
    hashFilesTotal: 13845,
    hashedBytes: gb(17.1),
    hashBytesTotal: gb(17.1),
    groupsDone: 3104,
    groupsTotal: 2987,
    phases: allPhasesDone(),
    ...update,
  });
}
