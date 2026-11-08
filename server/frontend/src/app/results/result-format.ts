import { FolderSummaryView, PairSideView, ScanEntry, SetPage } from '../api/models';
import { formatBytes, plural } from '../scans/scan-format';

/**
 * Text and figures for the result screen, built with the scan screen's `formatBytes` and `plural`
 * (`scans/scan-format.ts`) so both screens read alike (DFS-049 decision 3). No thousands separators (DFS-060 AC-2).
 */

/** One figure of the result header: a label above its value (DFS-060 decision 1, AC-2). */
export interface HeaderFigure {
  readonly label: string;
  readonly value: string;
}

/** The header bar's three segments, in this order (DFS-060 decision 1, AC-3). */
export type SegmentKind = 'reclaimable' | 'kept' | 'unique';

/** One segment of the header bar with its legend entry (AC-3). */
export interface SpaceSegment {
  readonly kind: SegmentKind;
  readonly bytes: number;
  /** Its share of the scanned bytes, 0 to 100, unrounded; 0 when the scan has 0 bytes. */
  readonly percent: number;
  /** "<label> · <formatBytes(bytes)>", e.g. "Reclaimable · 118.4 GB". */
  readonly legend: string;
}

export const FIGURE_RECLAIMABLE = 'Reclaimable';
export const FIGURE_SETS = 'Duplicate sets';
export const FIGURE_FILES = 'Duplicate files';
export const FIGURE_SCANNED = 'Scanned';

export const LEGEND_RECLAIMABLE = 'Reclaimable';
export const LEGEND_KEPT = 'Last copies, always kept';
export const LEGEND_UNIQUE = 'Unique';

/**
 * The header's four figures, in order (DFS-060 AC-2), from the unfiltered sets page and the scan entry:
 * Reclaimable `formatBytes(totalReclaimableBytes)`, Duplicate sets `totalItems`, Duplicate files `totalFiles`, and
 * Scanned "<formatBytes(progress.bytes)> · <plural(progress.files, 'file')>", e.g. "474.0 GB · 76214 files".
 */
export function headerFigures(scan: ScanEntry, totals: SetPage): HeaderFigure[] {
  throw new Error('not implemented: DFS-060');
}

/**
 * The header bar's segments, in order (DFS-060 AC-3): reclaimable (`totalReclaimableBytes`), kept
 * (`totalBytes - totalReclaimableBytes`) and unique (`progress.bytes - totalBytes`, never below 0), each with its
 * `barPercent` of `progress.bytes` and its legend ("Reclaimable · …", "Last copies, always kept · …", "Unique · …").
 */
export function spaceSegments(scan: ScanEntry, totals: SetPage): SpaceSegment[] {
  throw new Error('not implemented: DFS-060');
}

/**
 * `part` as a percentage of `whole`, unrounded and clamped to 0..100; 0 when `whole` is 0 or less, so nothing divides
 * by zero (DFS-060 AC-3, AC-4, AC-5).
 */
export function barPercent(part: number, whole: number): number {
  throw new Error('not implemented: DFS-060');
}

/** A set's copy count: "1 copy", "3 copies", counting physical copies, never links (DFS-060 AC-5). */
export function copiesText(count: number): string {
  throw new Error('not implemented: DFS-060');
}

/** A pair side whose `full` is true (DFS-050 decision 2, AC-3). */
export const SIDE_FULL = 'all have a copy in the other';

/** A pair side whose `full` is false (DFS-050 decision 2, AC-3). */
export const SIDE_PARTIAL = 'partial';

/**
 * A folder summary's "Copied elsewhere" value: "<filesWithCopyElsewhere> / <totalFiles> files · <bytesWithCopyElsewhere>",
 * e.g. "340 / 400 files · 1.2 GB" (DFS-050 AC-2); `plural` on `totalFiles`.
 */
export function copiedElsewhereText(summary: FolderSummaryView): string {
  return `${summary.filesWithCopyElsewhere} / ${plural(summary.totalFiles, 'file')} · ${formatBytes(summary.bytesWithCopyElsewhere)}`;
}

/** A folder summary's "Duplicates inside" value: "<internalDuplicateFiles> files · <internalBytes>", e.g. "12 files · 80.0 MB" (AC-2). */
export function duplicatesInsideText(summary: FolderSummaryView): string {
  return `${plural(summary.internalDuplicateFiles, 'file')} · ${formatBytes(summary.internalBytes)}`;
}

/** A folder summary's "Total" value: "<totalFiles> files · <totalBytes>", e.g. "400 files · 1.4 GB" (AC-2). */
export function summaryTotalText(summary: FolderSummaryView): string {
  return `${plural(summary.totalFiles, 'file')} · ${formatBytes(summary.totalBytes)}`;
}

/**
 * Whether the summary carries the chip "all copied elsewhere": exactly when `totalFiles` > 0 and
 * `filesWithCopyElsewhere` equals `totalFiles`; an empty folder never does (AC-2).
 */
export function isAllCopiedElsewhere(summary: FolderSummaryView): boolean {
  return summary.totalFiles > 0 && summary.filesWithCopyElsewhere === summary.totalFiles;
}

/**
 * One side of a folder pair: "<folder>: <filesWithCopy> / <totalFiles> files · <bytesWithCopy>", e.g.
 * "C:\Photos\2019: 340 / 400 files · 1.2 GB" (AC-3); `plural` on `totalFiles`.
 */
export function pairSideText(side: PairSideView): string {
  return `${side.folder}: ${side.filesWithCopy} / ${plural(side.totalFiles, 'file')} · ${formatBytes(side.bytesWithCopy)}`;
}

/** `SIDE_FULL` when the side's `full` is true, `SIDE_PARTIAL` otherwise; never a verdict (AC-3, AC-4). */
export function pairCoverageText(side: PairSideView): string {
  return side.full ? SIDE_FULL : SIDE_PARTIAL;
}
