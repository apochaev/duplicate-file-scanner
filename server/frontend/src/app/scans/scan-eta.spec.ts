import { progress } from '../results/result-test-data';
import { EtaEstimator, formatEta } from './scan-eta';

describe('EtaEstimator', () => {
  let now = 0;
  let estimator: EtaEstimator;

  beforeEach(() => {
    now = 0;
    estimator = new EtaEstimator(() => now);
  });

  function hashing(atSeconds: number, hashedBytes: number, total = 6_000_000_000): string | null {
    now = atSeconds * 1000;
    return estimator.record(progress({ status: 'RUNNING', phase: 'FULL_HASH', hashedBytes, hashBytesTotal: total }));
  }

  it('DFS-057.AC-6 waits, then follows the recent rate', () => {
    expect(hashing(0, 0)).toBe('estimating…');
    expect(hashing(2, 100_000_000)).toBe('estimating…');
    // 300 MB in 4 s is 75 MB/s; 5.7 GB left is 76 s.
    expect(hashing(4, 300_000_000)).toBe('about 1 min left');
  });

  it('DFS-057.AC-6 keeps estimating while no bytes are hashed, however long the phase has run', () => {
    expect(hashing(0, 0)).toBe('estimating…');
    expect(hashing(10, 0)).toBe('estimating…');
  });

  it('DFS-057.AC-6 counts only the updates of the last 30 seconds', () => {
    hashing(0, 0);
    hashing(4, 300_000_000);
    hashing(30, 1_000_000_000);
    // From the 30 s and 60 s updates only: 3 GB in 30 s is 100 MB/s; 2 GB left is 20 s.
    expect(hashing(60, 4_000_000_000)).toBe('about 20 s left');
  });

  it('DFS-057.AC-6 reads "estimating…" while the recent rate is 0, and shows no ETA in another phase', () => {
    // 50 MB in 5 s is 10 MB/s; 2.4 GB left is 240 s.
    hashing(0, 0, 2_450_000_000);
    expect(hashing(5, 50_000_000, 2_450_000_000)).toBe('about 4 min left');

    hashing(10, 50_000_000, 2_450_000_000);
    hashing(20, 50_000_000, 2_450_000_000);
    expect(hashing(36, 50_000_000, 2_450_000_000)).toBe('estimating…');

    now = 40_000;
    expect(estimator.record(progress({ status: 'RUNNING', phase: 'HARD_LINKS', groupsDone: 1, groupsTotal: 3 }))).toBeNull();
  });

  it('DFS-057.AC-6 starts a new phase afresh', () => {
    hashing(0, 0);
    expect(hashing(5, 500_000_000)).toBe('about 55 s left');
    now = 6000;
    expect(estimator.record(progress({ status: 'RUNNING', phase: 'PARTIAL_HASH' }))).toBeNull();

    // A second FULL_HASH phase: its first update starts the window, so it estimates for the first 3 s.
    expect(hashing(100, 0)).toBe('estimating…');
    expect(hashing(102, 900_000_000)).toBe('estimating…');
    // 1 GB in 4 s is 250 MB/s; 5 GB left is 20 s.
    expect(hashing(104, 1_000_000_000)).toBe('about 20 s left');
  });

  it('DFS-057.AC-6, DFS-057.AC-7 gives no ETA outside FULL_HASH or for a finished scan', () => {
    expect(hashing(0, 0)).toBe('estimating…');
    for (const status of ['COMPLETED', 'CANCELLED', 'FAILED'] as const) {
      expect(estimator.record(progress({ status, phase: 'FULL_HASH' })), status).toBeNull();
    }
    expect(estimator.record(progress({ status: 'RUNNING', phase: 'WALK' }))).toBeNull();
  });
});

describe('formatEta', () => {
  it.each([
    [45, 'about 45 s left'],
    [250, 'about 4 min left'],
    [4800, 'about 1 h 20 min left'],
  ])('DFS-057.AC-6 rounds %s seconds to a readable time', (seconds, text) => {
    expect(formatEta(seconds)).toBe(text);
  });

  it('DFS-057.AC-6 chooses the unit after rounding', () => {
    expect(formatEta(59.6)).toBe('about 1 min left');
    expect(formatEta(12.4)).toBe('about 12 s left');
  });
});
