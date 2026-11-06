import { DuplicateSetView, FolderNode, FolderSummaryView, PairSideView, SetPage } from '../api/models';
import { formatBytes, plural } from '../scans/scan-format';

/**
 * Text for the result screen, built with the scan screen's `formatBytes` and `plural` (`scans/scan-format.ts`) so both
 * screens read alike (DFS-049 decision 3).
 */

/** A tree node: "<name> · <subtree bytes> · <files> files", e.g. "Photos · 12.0 GB · 3210 files" (AC-5). */
export function folderLabel(folder: FolderNode): string {
  return `${folder.name} · ${formatBytes(folder.bytes)} · ${plural(folder.files, 'file')}`;
}

/**
 * A set card's heading: "<n> copies · <size> each · <reclaimable> reclaimable", n the number of physical copies
 * (`copies.length`, never the number of links), e.g. "2 copies · 2.0 MB each · 2.0 MB reclaimable" (AC-7).
 */
export function setHeading(set: DuplicateSetView): string {
  const n = set.copies.length;
  return `${n} ${n === 1 ? 'copy' : 'copies'} · ${formatBytes(set.size)} each · ${formatBytes(set.reclaimableBytes)} reclaimable`;
}

/** The Sets tab's summary line: "<totalItems> duplicate sets · <totalReclaimableBytes> reclaimable" (AC-9). */
export function setsSummary(page: SetPage): string {
  return `${plural(page.totalItems, 'duplicate set')} · ${formatBytes(page.totalReclaimableBytes)} reclaimable`;
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
