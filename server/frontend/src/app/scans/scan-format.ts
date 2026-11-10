import { ScanProgress } from '../api/models';

export type ScanStatus = ScanProgress['status'];
export type ScanPhase = NonNullable<ScanProgress['phase']>;

const STATUS_LABELS: Record<ScanStatus, string> = {
  QUEUED: 'Queued',
  RUNNING: 'Running',
  CANCELLING: 'Cancelling',
  COMPLETED: 'Completed',
  CANCELLED: 'Cancelled',
  FAILED: 'Failed',
};

const PHASE_LABELS: Record<ScanPhase, string> = {
  WALK: 'Finding files',
  PARTIAL_HASH: 'Comparing samples',
  FULL_HASH: 'Hashing files',
  HARD_LINKS: 'Checking hard links',
};

const UNITS = ['KB', 'MB', 'GB', 'TB'];

/** A status in words (DFS-041.AC-5). */
export function statusLabel(status: ScanStatus): string {
  return STATUS_LABELS[status];
}

/** A phase in words (DFS-041.AC-6). */
export function phaseLabel(phase: ScanPhase): string {
  return PHASE_LABELS[phase];
}

/** Whether the scan has stopped for good: its stream ends and it can no longer be cancelled. */
export function isTerminal(status: ScanStatus): boolean {
  return status === 'COMPLETED' || status === 'CANCELLED' || status === 'FAILED';
}

/**
 * Bytes in 1024-based units with one decimal, rounded half-up; below 1024 a whole number of B. The same format as
 * the CLI's text report (`TextReport.size`), so both show a scan's sizes alike.
 */
export function formatBytes(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  let unit = 0;
  let divisor = 1024;
  while (unit < UNITS.length - 1 && Math.floor(bytes / divisor) >= 1024) {
    unit++;
    divisor *= 1024;
  }
  return `${(Math.round((bytes * 10) / divisor) / 10).toFixed(1)} ${UNITS[unit]}`;
}

/** "1 file", "2 files". */
export function plural(count: number, noun: string): string {
  return `${count} ${noun}${count === 1 ? '' : 's'}`;
}

/**
 * A duration in whole seconds as words (DFS-063.AC-13): "42 s" under a minute, "14 min 38 s" under an hour, "1 h 05 min"
 * from an hour (whole minutes, minutes zero-padded, seconds dropped).
 */
export function formatDuration(seconds: number): string {
  throw new Error('not implemented: DFS-063');
}

/**
 * The two lines of the section for `phase` while it runs (DFS-063.AC-7), counts without thousands separators, sizes
 * through `formatBytes`: walk "N files · SIZE found" over "in N folders"; compare "A of B files" over "C left"; hashing
 * "A of B SIZE · C SIZE left" over "X of Y files · Z left", or with no bytes left "B of B SIZE · nothing left to read"
 * over "Y of Y files · finishing…"; hard links "A of B groups" over "C left". Reads the flat fields of the progress.
 */
export function runningLines(progress: ScanProgress, phase: ScanPhase): [string, string] {
  throw new Error('not implemented: DFS-063');
}

/**
 * The two lines of a finished section from `progress.phases` (DFS-063.AC-8): walk "N files · SIZE" over "found in N
 * folders"; compare "C compared" (the compare entry's files) over "→ N candidates" (the hash entry's files); hashing
 * "N hashed · SIZE" (the hash entry) over "→ N matching groups" (the hard-link entry's groups); hard links "N groups
 * checked" over "→ N duplicate groups" (`groupsTotal`, only once the scan is COMPLETED). The second line is '' until
 * the number its arrow needs is known.
 */
export function finishedLines(progress: ScanProgress, phase: ScanPhase): [string, string] {
  throw new Error('not implemented: DFS-063');
}

/**
 * The two lines of the section a CANCELLED or FAILED scan stopped in (DFS-063.AC-9): the counts it reached, hashing
 * "5.1 of 17.1 GB read" over "6210 of 13845 files"; compare "A of B files" over ''; hard links "A of B groups" over '';
 * the walk "N files · SIZE found" over "in N folders".
 */
export function stoppedLines(progress: ScanProgress, phase: ScanPhase): [string, string] {
  throw new Error('not implemented: DFS-063');
}

/**
 * The two lines of a section not reached yet (DFS-063.AC-9): "not started" over "total known after step N" (N the step
 * before it; '' for the walk), or, when `stopped` is true (an earlier section stopped or failed), "not run" over ''.
 */
export function notStartedLines(phase: ScanPhase, stopped: boolean): [string, string] {
  throw new Error('not implemented: DFS-063');
}

/**
 * The collapsed COMPLETED row's narrowing totals (DFS-063.AC-12): "38886 files · 902.9 GB found → 29594 compared →
 * 13845 hashed · 17.1 GB → 2987 duplicate groups", from `progress.phases` and `groupsTotal`.
 */
export function collapsedTotals(progress: ScanProgress): string {
  throw new Error('not implemented: DFS-063');
}

/**
 * The activity a FAILED scan was in, for its failure line (DFS-063.AC-11): "finding files", "comparing samples",
 * "hashing files" or "checking hard links".
 */
export function activityLabel(phase: ScanPhase): string {
  throw new Error('not implemented: DFS-063');
}

/**
 * The failure line of a FAILED row (DFS-063.AC-11): "Scan failed while hashing files. <failureMessage>"; without a
 * phase "Scan failed. <failureMessage>".
 */
export function failureLine(progress: ScanProgress): string {
  throw new Error('not implemented: DFS-063');
}
