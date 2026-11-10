import { progress } from '../results/result-test-data';
import { EtaEstimator, formatEta } from './scan-eta';

const MB = 1_000_000;

describe('EtaEstimator', () => {
  let now = 0;
  let estimator: EtaEstimator;

  beforeEach(() => {
    now = 0;
    estimator = new EtaEstimator(() => now);
  });

  function hashing(atSeconds: number, hashedBytes: number, total = 2600 * MB): string | null {
    now = atSeconds * 1000;
    return estimator.record(progress({ status: 'RUNNING', phase: 'FULL_HASH', hashedBytes, hashBytesTotal: total }));
  }

  function links(atSeconds: number, groupsDone: number, total = 3000): string | null {
    now = atSeconds * 1000;
    return estimator.record(progress({ status: 'RUNNING', phase: 'HARD_LINKS', groupsDone, groupsTotal: total }));
  }

  it('DFS-063.AC-4 estimates for the first 30 seconds of hashing, then follows the rate', () => {
    expect(hashing(0, 0)).toBe('estimating…');
    expect(hashing(29, 290 * MB)).toBe('estimating…');
    // 300 MB in 30 s is 10 MB/s; 2300 MB left is 230 s.
    expect(hashing(30, 300 * MB)).toBe('about 4 min left');
  });

  it('DFS-063.AC-4 keeps estimating while the rate over the last 30 seconds is 0', () => {
    hashing(0, 0);
    hashing(60, 500 * MB);
    expect(hashing(90, 500 * MB)).toBe('estimating…');
  });

  it('DFS-063.AC-4 keeps estimating while no bytes are hashed, however long the phase has run', () => {
    expect(hashing(0, 0)).toBe('estimating…');
    expect(hashing(45, 0)).toBe('estimating…');
  });

  it('DFS-063.AC-4 counts only the updates of the last 30 seconds', () => {
    const total = 6_000_000_000;
    hashing(0, 0, total);
    hashing(4, 300 * MB, total);
    hashing(30, 1_000 * MB, total);
    // From the 30 s and 60 s updates only: 3 GB in 30 s is 100 MB/s; 2 GB left is 20 s.
    expect(hashing(60, 4_000 * MB, total)).toBe('about 20 s left');
  });

  it('DFS-063.AC-4 shows no time left in the walk, in comparing, when queued or once the scan has ended', () => {
    expect(hashing(0, 0)).toBe('estimating…');
    expect(estimator.record(progress({ status: 'RUNNING', phase: 'WALK' }))).toBeNull();
    expect(estimator.record(progress({ status: 'RUNNING', phase: 'PARTIAL_HASH' }))).toBeNull();
    expect(estimator.record(progress({ status: 'QUEUED', phase: null }))).toBeNull();
    for (const status of ['COMPLETED', 'CANCELLED', 'FAILED'] as const) {
      expect(estimator.record(progress({ status, phase: 'FULL_HASH' })), status).toBeNull();
    }
  });

  it('DFS-063.AC-4 starts a new hashing phase afresh, with its own 30 second warm-up', () => {
    hashing(0, 0, 6_000 * MB);
    now = 6000;
    expect(estimator.record(progress({ status: 'RUNNING', phase: 'PARTIAL_HASH' }))).toBeNull();

    expect(hashing(100, 0, 6_000 * MB)).toBe('estimating…');
    expect(hashing(102, 900 * MB, 6_000 * MB)).toBe('estimating…');
    // 2 GB in 30 s is about 66.7 MB/s; 4 GB left is 60 s.
    expect(hashing(130, 2_000 * MB, 6_000 * MB)).toBe('about 1 min left');
  });

  it('DFS-063.AC-5 follows the groups rate of hard links, estimating for the first 30 seconds', () => {
    expect(links(0, 0)).toBe('estimating…');
    expect(links(29, 580)).toBe('estimating…');
    // 600 groups in 30 s is 20 per second; 2400 left is 120 s.
    expect(links(30, 600)).toBe('about 2 min left');
  });

  it('DFS-063.AC-5 counts the hard-link warm-up from that phase, not from the hashing before it', () => {
    hashing(0, 0);
    expect(links(100, 0)).toBe('estimating…');
    expect(links(129, 580)).toBe('estimating…');
    expect(links(130, 600)).toBe('about 2 min left');
  });

  it('DFS-063.AC-5 keeps estimating while hard links make no progress', () => {
    links(0, 0);
    links(40, 100);
    links(60, 100);
    expect(links(80, 100)).toBe('estimating…');
  });

  it('DFS-063.AC-6 reads "finishing…" when hashing has no bytes left, after a long phase', () => {
    const total = 18_361_000_000;
    hashing(0, 0, total);
    expect(hashing(1200, total, total)).toBe('finishing…');
  });

  it('DFS-063.AC-6 reads "finishing…" also inside the first 30 seconds of a tiny scan', () => {
    hashing(0, 0, 5 * MB);
    expect(hashing(4, 5 * MB, 5 * MB)).toBe('finishing…');
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
