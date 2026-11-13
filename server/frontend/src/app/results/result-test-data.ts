import { HttpTestingController, TestRequest } from '@angular/common/http/testing';
import {
  DuplicateSetView,
  FolderNode,
  FolderPairView,
  FolderGroupView,
  GroupFolderView,
  GroupPage,
  GroupSetView,
  PairSideView,
  ScanEntry,
  ScanProgress,
  SetPage,
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

/** DFS-050, DFS-068: builders and request helpers for the Overlaps tab. */

export function side(path: string, update: Partial<PairSideView> = {}): PairSideView {
  return { folder: path, totalFiles: 10, totalBytes: 1000, filesWithCopy: 4, bytesWithCopy: 400, full: false, ...update };
}

export function pair(a: PairSideView, b: PairSideView): FolderPairView {
  return { a, b };
}

/** The one `GET /api/scans/<id>/pair?a=&b=`. */
export function expectPair(http: HttpTestingController, id: string, a: string, b: string): TestRequest {
  return http.expectOne(
    (r) =>
      r.method === 'GET' && r.url === `/api/scans/${id}/pair` && r.params.get('a') === a && r.params.get('b') === b,
  );
}

let setCounter = 0;

/** A set of a group: its name, size and physical copies; each gets its own digest. */
export function groupSet(name: string, size = 1048576, copies = 2, update: Partial<GroupSetView> = {}): GroupSetView {
  setCounter++;
  return { digest: `G-DIGEST-${setCounter}`, name, size, copies, ...update };
}

/** One folder line of a group: 1 of 5 files shared once, in no other group, unless `update` says otherwise. */
export function groupFolder(path: string, update: Partial<GroupFolderView> = {}): GroupFolderView {
  return {
    folder: path,
    copiesEach: 1,
    sharedFiles: 1,
    sharedBytes: 1048576,
    folderFiles: 5,
    folderBytes: 5242880,
    otherGroups: 0,
    ...update,
  };
}

/** A group of `folders` holding `sets` (all listed unless `update.totalSets` says more); two copies per file by default. */
export function folderGroup(
  folders: GroupFolderView[],
  sets: GroupSetView[] = [groupSet('a.bin')],
  update: Partial<FolderGroupView> = {},
): FolderGroupView {
  const size = sets.reduce((sum, set) => sum + set.size * (set.copies - 1), 0);
  return {
    files: sets.length,
    copies: sets.reduce((sum, set) => sum + set.copies, 0),
    reclaimableBytes: size,
    folders,
    sets,
    totalSets: sets.length,
    ...update,
  };
}

export function groupPage(items: FolderGroupView[], update: Partial<GroupPage> = {}): GroupPage {
  return { items, page: 0, size: 10, totalItems: items.length, hiddenSmall: 0, ...update };
}

/**
 * The one `GET /api/scans/<id>/groups` with `under` and `minBytes` as given (null: the parameter is absent), `page`
 * and `size`.
 */
export function expectGroups(
  http: HttpTestingController,
  id: string,
  under: string | null = null,
  minBytes: number | null = null,
  page = 0,
  size = 10,
): TestRequest {
  return http.expectOne(
    (r) =>
      r.method === 'GET' &&
      r.url === `/api/scans/${id}/groups` &&
      r.params.has('under') === (under !== null) &&
      (r.params.get('under') ?? null) === under &&
      r.params.has('minBytes') === (minBytes !== null) &&
      (r.params.get('minBytes') ?? null) === (minBytes === null ? null : String(minBytes)) &&
      r.params.get('page') === String(page) &&
      r.params.get('size') === String(size),
  );
}
