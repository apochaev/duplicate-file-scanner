import { ScanProgress } from '../api/models';
import { formatBytes, phaseLabel, plural } from './scan-format';

/**
 * What an unfinished scan's row shows for its current phase (DFS-057 decision 1). The phase line reads
 * `<label>: <detail>`, for example "Comparing samples: 3100 of 9400 files · 6300 left · 33%".
 */
export interface PhaseView {
  /** The phase in words, `phaseLabel(phase)` (`scan-format.ts`): "Finding files", "Hashing files", ... */
  readonly label: string;
  /**
   * The rest of the phase line after `<label>: ` (DFS-057.AC-1 to AC-4), for example "3100 of 9400 files · 6300 left ·
   * 33%"; its percentage is the bar's value rounded to the nearest whole number, 0% when the phase's total is 0.
   */
  readonly detail: string;
  /** "Found: <files> files · <bytes>" in every phase after WALK (DFS-057.AC-5); null in WALK. */
  readonly found: string | null;
  /**
   * The bar's value from 0 to 100, unrounded (files in PARTIAL_HASH, bytes in FULL_HASH, groups in HARD_LINKS; 0 when
   * the total is 0); null in WALK, where the bar is indeterminate (no total exists yet).
   */
  readonly percent: number | null;
}

/**
 * The phase line, the found line and the bar's value for a scan's progress (DFS-057.AC-1 to AC-5, AC-8), counts
 * through `plural` and sizes through `formatBytes` (`scan-format.ts`); null when the progress has no phase (a QUEUED
 * scan). Read only for an unfinished scan: a finished row shows its totals instead (DFS-057.AC-7).
 */
export function phaseView(progress: ScanProgress): PhaseView | null {
  const phase = progress.phase;
  if (!phase) {
    return null;
  }
  const label = phaseLabel(phase);
  if (phase === 'WALK') {
    return {
      label,
      detail: `${plural(progress.directories, 'folder')} · ${plural(progress.files, 'file')} · ${formatBytes(progress.bytes)} found`,
      found: null,
      percent: null,
    };
  }
  const found = `Found: ${plural(progress.files, 'file')} · ${formatBytes(progress.bytes)}`;
  const share = (done: number, total: number): number => (total > 0 ? (done / total) * 100 : 0);
  const filesPart = `${progress.hashedFiles} of ${plural(progress.hashFilesTotal, 'file')} · ${progress.hashFilesTotal - progress.hashedFiles} left`;
  let percent: number;
  let detail: string;
  if (phase === 'PARTIAL_HASH') {
    percent = share(progress.hashedFiles, progress.hashFilesTotal);
    detail = `${filesPart} · ${Math.round(percent)}%`;
  } else if (phase === 'FULL_HASH') {
    percent = share(progress.hashedBytes, progress.hashBytesTotal);
    detail =
      `${filesPart} · ${formatBytes(progress.hashedBytes)} of ${formatBytes(progress.hashBytesTotal)} · ` +
      `${formatBytes(progress.hashBytesTotal - progress.hashedBytes)} left · ${Math.round(percent)}%`;
  } else {
    percent = share(progress.groupsDone, progress.groupsTotal);
    detail = `group ${progress.groupsDone} of ${progress.groupsTotal}`;
  }
  return { label, detail, found, percent };
}
