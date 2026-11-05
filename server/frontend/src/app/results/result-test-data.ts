import { HttpTestingController, TestRequest } from '@angular/common/http/testing';
import { DuplicateSetView, FolderNode, ScanEntry, ScanProgress, SetPage } from '../api/models';

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
    ...update,
  };
}

export function scanEntry(id: string, update: Partial<ScanProgress> = {}, locations = ['C:\\Photos']): ScanEntry {
  return { id, locations, startedAt: '2026-11-01T10:00:00Z', progress: progress(update) };
}

export function folder(path: string, update: Partial<FolderNode> = {}): FolderNode {
  const name = path.split(/[\\/]/).filter((part) => part.length > 0).pop() ?? path;
  return { path, name, bytes: 900, files: 2, directBytes: 900, directFiles: 2, hasChildren: false, ...update };
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
  return { items, page: 0, size: 50, totalItems: items.length, totalReclaimableBytes: 0, ...update };
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
