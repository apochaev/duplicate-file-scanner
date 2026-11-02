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
