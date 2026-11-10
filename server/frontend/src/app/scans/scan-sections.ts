import { ScanProgress } from '../api/models';
import {
  ScanPhase,
  finishedLines,
  noBytesLeft,
  notStartedLines,
  phaseLabel,
  runningLines,
  stoppedLines,
} from './scan-format';

/**
 * The four fixed sections of a scan row's bar, in run order (DFS-063 decision 2): the phases of `ScanProgress.phase`.
 * `SECTIONS[i]` is step `i + 1`; its weight is its share of the overall percentage and of the strip's width.
 */
export const SECTIONS: readonly { readonly phase: ScanPhase; readonly weight: number }[] = [
  { phase: 'WALK', weight: 12 },
  { phase: 'PARTIAL_HASH', weight: 18 },
  { phase: 'FULL_HASH', weight: 60 },
  { phase: 'HARD_LINKS', weight: 10 },
];

/**
 * One section's state (DFS-063.AC-10): `done` (solid, check), `running` (outlined with fill; the walk is `running`
 * with `striped` true), `finishing` (hashing with no bytes left: full, moving stripes), `not-started` (dashed, grey;
 * nothing reached it yet), `not-run` (after a stopped section, or in a scan that never started), `stopped` (grey hatch,
 * CANCELLED) and `failed` (grey hatch, red outline, FAILED).
 */
export type SectionState = 'done' | 'running' | 'finishing' | 'not-started' | 'not-run' | 'stopped' | 'failed';

/** Everything the row renders for one section; the component only reads these plain values (DFS-063 decision 2). */
export interface SectionView {
  readonly phase: ScanPhase;
  /** 1 to 4. */
  readonly step: number;
  /** "1 · Finding files", "2 · Comparing samples", "3 · Hashing files", "4 · Checking hard links". */
  readonly title: string;
  readonly state: SectionState;
  /**
   * The state as text, so it reads without colour (DFS-063.AC-10): "done" (shown with a check), "now", "finishing",
   * "stopped", "failed"; null for `not-started` and `not-run`, whose first line already says so. Never a percentage:
   * a section shows none.
   */
  readonly tag: string | null;
  /** How much of the section is filled, 0 to 100: 100 for done, finishing and a running walk, the section's own progress for the others, the frozen progress for stopped and failed, 0 for not started. */
  readonly fill: number;
  /** True for the walk while it runs and for `finishing`: the fill shows moving stripes (DFS-063.AC-15). */
  readonly striped: boolean;
  /** The section's two lines (DFS-063.AC-7 to AC-9); `line2` is '' when there is none yet. */
  readonly line1: string;
  readonly line2: string;
}

/**
 * The overall percentage, a whole number from 0 to 100, or null while there is none ("—": the walk, a QUEUED scan, or
 * a scan stopped in the walk) (DFS-063.AC-3). It is the weights of the finished sections plus the running section's
 * weight times its own progress, rounded down: compare by `hashedFiles` over `hashFilesTotal`, hashing by
 * `hashedBytes` over `hashBytesTotal`, hard links by `groupsDone` over `groupsTotal` (a zero total counts as no
 * progress, never NaN). At most 99 for any status but COMPLETED, which is 100. A CANCELLED or FAILED scan keeps the
 * value of the phase it stopped in. The CANCELLING hold (the last value shown) is the component's, not this function's.
 */
export function overallPercent(progress: ScanProgress): number | null {
  if (progress.status === 'COMPLETED') {
    return 100;
  }
  const index = progress.phase ? phaseIndex(progress.phase) : -1;
  if (progress.status === 'QUEUED' || index <= 0) {
    return null;
  }
  const before = SECTIONS.slice(0, index).reduce((sum, s) => sum + s.weight, 0);
  return Math.min(99, Math.floor(before + SECTIONS[index].weight * ownProgress(progress, SECTIONS[index].phase)));
}

/** The small text under the overall percentage: "of whole scan", "when stopped" (CANCELLED, FAILED) or "total not known yet" (walk, QUEUED) (DFS-063.AC-2). */
export function overallCaption(progress: ScanProgress): string {
  if (progress.status === 'CANCELLED' || progress.status === 'FAILED') {
    return 'when stopped';
  }
  return overallPercent(progress) === null ? 'total not known yet' : 'of whole scan';
}

/**
 * The four sections for a progress, always four, in run order (DFS-063.AC-1, AC-2, AC-7 to AC-11): QUEUED all
 * `not-started`; RUNNING and CANCELLING the phase's section `running` (or `finishing`: FULL_HASH with `hashedBytes`
 * equal to `hashBytesTotal`), earlier ones `done`, later ones `not-started`; COMPLETED all `done`; CANCELLED and FAILED
 * the phase's section `stopped` or `failed`, earlier ones `done`, later ones `not-run`. Lines come from the helpers in
 * `scan-format.ts` (`runningLines`, `finishedLines`, `stoppedLines`, `notStartedLines`).
 */
export function sectionViews(progress: ScanProgress): SectionView[] {
  const status = progress.status;
  const stoppedStatus = status === 'CANCELLED' || status === 'FAILED';
  const at = progress.phase ? phaseIndex(progress.phase) : -1;
  return SECTIONS.map(({ phase }, i): SectionView => {
    const base = { phase, step: i + 1, title: `${i + 1} · ${phaseLabel(phase)}` };
    const view = (state: SectionState, tag: string | null, fill: number, striped: boolean, lines: [string, string]): SectionView => ({
      ...base,
      state,
      tag,
      fill,
      striped,
      line1: lines[0],
      line2: lines[1],
    });
    if (status === 'COMPLETED' || (at >= 0 && i < at)) {
      return view('done', 'done', 100, false, finishedLines(progress, phase));
    }
    if (i === at && status !== 'QUEUED') {
      const walk = phase === 'WALK';
      if (status === 'CANCELLED') {
        return view('stopped', 'stopped', walk ? 100 : ownProgress(progress, phase) * 100, false, stoppedLines(progress, phase));
      }
      if (status === 'FAILED') {
        return view('failed', 'failed', walk ? 100 : ownProgress(progress, phase) * 100, false, stoppedLines(progress, phase));
      }
      if (phase === 'FULL_HASH' && noBytesLeft(progress)) {
        return view('finishing', 'finishing', 100, true, runningLines(progress, phase));
      }
      return view('running', 'now', walk ? 100 : ownProgress(progress, phase) * 100, walk, runningLines(progress, phase));
    }
    const notRun = stoppedStatus;
    return view(notRun ? 'not-run' : 'not-started', null, 0, false, notStartedLines(phase, notRun));
  });
}

/**
 * The row's step line (DFS-063 decision 1 and the canvas): "Waiting to start" (QUEUED), "Step 3 of 4 · Hashing files",
 * "Step 3 of 4 · Hashing files, finishing…", "Cancelled during step 3 · Hashing files", "Failed during step 3 · Hashing
 * files"; '' for a COMPLETED scan (its row is collapsed) (DFS-063.AC-1).
 */
export function stepLine(progress: ScanProgress): string {
  if (progress.status === 'QUEUED') {
    return 'Waiting to start';
  }
  if (progress.status === 'COMPLETED' || !progress.phase) {
    return '';
  }
  const step = phaseIndex(progress.phase) + 1;
  const label = phaseLabel(progress.phase);
  switch (progress.status) {
    case 'CANCELLED':
      return `Cancelled during step ${step} · ${label}`;
    case 'FAILED':
      return `Failed during step ${step} · ${label}`;
    default:
      return `Step ${step} of 4 · ${label}${progress.phase === 'FULL_HASH' && noBytesLeft(progress) ? ', finishing…' : ''}`;
  }
}

function phaseIndex(phase: ScanPhase): number {
  return SECTIONS.findIndex((s) => s.phase === phase);
}

/** The running section's own progress from 0 to 1; a zero total counts as none, never NaN. */
function ownProgress(progress: ScanProgress, phase: ScanPhase): number {
  const [done, total] =
    phase === 'PARTIAL_HASH'
      ? [progress.hashedFiles, progress.hashFilesTotal]
      : phase === 'FULL_HASH'
        ? [progress.hashedBytes, progress.hashBytesTotal]
        : phase === 'HARD_LINKS'
          ? [progress.groupsDone, progress.groupsTotal]
          : [0, 0];
  return total > 0 ? Math.min(1, Math.max(0, done / total)) : 0;
}
