import { HttpTestingController } from '@angular/common/http/testing';
import { scanEntry } from '../app/results/result-test-data';

/**
 * Answers every open `GET /api/scans/<id>` with a COMPLETED entry that has a `finishedAt` (DFS-063.AC-12: a row whose
 * status turns COMPLETED through the stream refetches its entry). Specs that are not about the finish time call it in
 * `afterEach`, before `http.verify()`, so that request does not count as an unexpected open one. Returns the number
 * of requests answered.
 */
export function answerFinishFetches(http: HttpTestingController): number {
  const open = http.match((request) => request.method === 'GET' && /^\/api\/scans\/[^/]+$/.test(request.url));
  for (const request of open) {
    const id = request.request.url.split('/').pop() as string;
    request.flush({ ...scanEntry(id, { status: 'COMPLETED', phase: null }), finishedAt: '2026-11-01T10:14:38Z' });
  }
  return open.length;
}
