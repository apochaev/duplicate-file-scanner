import { HttpTestingController, TestRequest } from '@angular/common/http/testing';
import {
  DuplicateSetView,
  FolderNode,
  FolderPairView,
  FolderSummaryView,
  PairPage,
  PairSideView,
  ScanEntry,
  ScanProgress,
  SetPage,
  SummaryPage,
} from '../api/models';

/** Builders and request helpers shared by the DFS-049 specs, so a test states only what it is about. */

export function progress(update: Partial<ScanProgress> = {}): ScanProgress {
  return {
    status: 'COMPLETED',
    phase: null,
    directories: 0,
    files: 0,
    bytes: 0,
    hashedFiles: 0,
    hashFilesTotal: 0,
    hashedBytes: 0,
    hashBytesTotal: 0,
    groupsDone: 0,
    groupsTotal: 0,
    failureMessage: null,
    phases: [],
    ...update,
  };
}

export function scanEntry(id: string, update: Partial<ScanProgress> = {}, locations = ['C:\\Photos']): ScanEntry {
  return { id, locations, startedAt: '2026-11-01T10:00:00Z', finishedAt: null, progress: progress(update) };
}

export function folder(path: string, update: Partial<FolderNode> = {}): FolderNode {
  const name = path.split(/[\\/]/).filter((part) => part.length > 0).pop() ?? path;
  return { path, name, bytes: 900, files: 2, directBytes: 900, directFiles: 2, hasChildren: false, duplicateFiles: 0, duplicateBytes: 0, ...update };
}

/** A set whose copies each list their links; the first link of a copy is the copy line. */
export function duplicateSet(copies: string[][], size = 2097152, digest = 'DIGEST-SECRET-0123'): DuplicateSetView {
  return {
    digest,
    size,
    reclaimableBytes: size * (copies.length - 1),
    copies: copies.map((links) => ({ links: links.map((path) => ({ path, modified: '2026-10-01T12:00:00Z' })) })),
  };
}

export function setPage(items: DuplicateSetView[], update: Partial<SetPage> = {}): SetPage {
  return { items, page: 0, size: 50, totalItems: items.length, totalReclaimableBytes: 0, totalFiles: 0, totalBytes: 0, ...update };
}

export const NOT_COMPLETED = { code: 'SCAN_NOT_COMPLETED', message: 'The scan has not completed' };
export const UNKNOWN_SCAN = { code: 'UNKNOWN_SCAN', message: 'No such scan' };

export function refuse(req: TestRequest, status: number, body: { code: string; message: string }): void {
  req.flush(body, { status, statusText: `Status ${status}` });
}

/** The one request to `/api/scans/<id><suffix>` (query parameters are in `req.params`). */
export function expectScan(http: HttpTestingController, id: string, suffix = ''): TestRequest {
  return http.expectOne((req) => req.url === `/api/scans/${id}${suffix}`);
}

/** The one `GET /api/scans/<id>/folders`, with `parent` as given (null: the roots, no `parent` parameter). */
export function expectFolders(http: HttpTestingController, id: string, parent: string | null): TestRequest {
  return http.expectOne(
    (r) => r.method === 'GET' && r.url === `/api/scans/${id}/folders` && (r.params.get('parent') ?? null) === parent,
  );
}

/** The one `GET /api/scans/<id>/sets`, with `under` (null: no `under` parameter), `page` and `size` as given. */
export function expectSets(
  http: HttpTestingController,
  id: string,
  under: string | null,
  page = 0,
  size = 50,
): TestRequest {
  return http.expectOne(
    (r) =>
      r.method === 'GET' &&
      r.url === `/api/scans/${id}/sets` &&
      r.params.has('under') === (under !== null) &&
      (r.params.get('under') ?? null) === under &&
      r.params.get('page') === String(page) &&
      r.params.get('size') === String(size),
  );
}

/** DFS-050: builders and request helpers for the Overlaps tab. */

export function summary(path: string, update: Partial<FolderSummaryView> = {}): FolderSummaryView {
  return {
    folder: path,
    totalFiles: 10,
    totalBytes: 1000,
    filesWithCopyElsewhere: 4,
    bytesWithCopyElsewhere: 400,
    internalDuplicateFiles: 0,
    internalBytes: 0,
    ...update,
  };
}

export function summaryPage(
  items: FolderSummaryView[],
  update: Partial<SummaryPage> = {},
): SummaryPage {
  return { items, page: 0, size: 10, totalItems: items.length, ...update };
}

export function side(path: string, update: Partial<PairSideView> = {}): PairSideView {
  return { folder: path, totalFiles: 10, totalBytes: 1000, filesWithCopy: 4, bytesWithCopy: 400, full: false, ...update };
}

export function pair(a: PairSideView, b: PairSideView): FolderPairView {
  return { a, b };
}

export function pairPage(items: FolderPairView[], update: Partial<PairPage> = {}): PairPage {
  return { items, page: 0, size: 10, totalItems: items.length, ...update };
}

/** The one `GET /api/scans/<id>/<kind>` (`summaries` or `pairs`) with `under` (null: absent), `page` and, when given, `size`. */
export function expectOverlaps(
  http: HttpTestingController,
  id: string,
  kind: 'summaries' | 'pairs',
  under: string | null,
  page = 0,
  size: number | null = 10,
): TestRequest {
  return http.expectOne(
    (r) =>
      r.method === 'GET' &&
      r.url === `/api/scans/${id}/${kind}` &&
      r.params.has('under') === (under !== null) &&
      (r.params.get('under') ?? null) === under &&
      r.params.get('page') === String(page) &&
      (size === null || r.params.get('size') === String(size)),
  );
}

export function expectSummaries(
  http: HttpTestingController,
  id: string,
  under: string | null,
  page = 0,
  size: number | null = 10,
): TestRequest {
  return expectOverlaps(http, id, 'summaries', under, page, size);
}

export function expectPairs(
  http: HttpTestingController,
  id: string,
  under: string | null,
  page = 0,
  size: number | null = 10,
): TestRequest {
  return expectOverlaps(http, id, 'pairs', under, page, size);
}

/** The one `GET /api/scans/<id>/pair?a=&b=`. */
export function expectPair(http: HttpTestingController, id: string, a: string, b: string): TestRequest {
  return http.expectOne(
    (r) =>
      r.method === 'GET' && r.url === `/api/scans/${id}/pair` && r.params.get('a') === a && r.params.get('b') === b,
  );
}
