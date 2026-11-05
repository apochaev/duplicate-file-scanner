import { DuplicateSetView, FolderNode, SetPage } from '../api/models';
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
