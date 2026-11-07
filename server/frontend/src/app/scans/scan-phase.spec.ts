import { progress } from '../results/result-test-data';
import { phaseView } from './scan-phase';

describe('phaseView', () => {
  it('DFS-057.AC-1, DFS-057.AC-8 shows what a walk has found and has no total', () => {
    const view = phaseView(
      progress({ status: 'RUNNING', phase: 'WALK', directories: 1204, files: 48210, bytes: 330_000_000_000 }),
    );

    expect(view?.label).toBe('Finding files');
    expect(`${view?.label}: ${view?.detail}`).toBe('Finding files: 1204 folders · 48210 files · 307.3 GB found');
    expect(view?.percent).toBeNull();
    expect(view?.found).toBeNull();
  });

  it('DFS-057.AC-1, DFS-057.AC-8 writes singular counts with plural', () => {
    const view = phaseView(progress({ status: 'RUNNING', phase: 'WALK', directories: 1, files: 1, bytes: 10 }));

    expect(`${view?.label}: ${view?.detail}`).toBe('Finding files: 1 folder · 1 file · 10 B found');
  });

  it('DFS-057.AC-2 shows files done, left and the percentage, and no sample bytes', () => {
    const view = phaseView(
      progress({
        status: 'RUNNING',
        phase: 'PARTIAL_HASH',
        hashedFiles: 3100,
        hashFilesTotal: 9400,
        hashedBytes: 400_000_000,
        hashBytesTotal: 1_200_000_000,
      }),
    );

    expect(`${view?.label}: ${view?.detail}`).toBe('Comparing samples: 3100 of 9400 files · 6300 left · 33%');
    expect(view?.percent).toBeCloseTo((3100 / 9400) * 100, 5);
    expect(view?.detail).not.toContain('MB');
  });

  it('DFS-057.AC-3, DFS-057.AC-8 shows files and bytes done and left, the percentage following bytes', () => {
    const view = phaseView(
      progress({
        status: 'RUNNING',
        phase: 'FULL_HASH',
        hashedFiles: 1204,
        hashFilesTotal: 5880,
        hashedBytes: 3_328_599_654,
        hashBytesTotal: 13_314_398_618,
      }),
    );

    expect(`${view?.label}: ${view?.detail}`).toBe(
      'Hashing files: 1204 of 5880 files · 4676 left · 3.1 GB of 12.4 GB · 9.3 GB left · 25%',
    );
    expect(view?.percent).toBeCloseTo(25, 5);
  });

  it('DFS-057.AC-4 shows the hard-link groups done of total, the bar following groups', () => {
    const view = phaseView(progress({ status: 'RUNNING', phase: 'HARD_LINKS', groupsDone: 40, groupsTotal: 310 }));

    expect(`${view?.label}: ${view?.detail}`).toBe('Checking hard links: group 40 of 310');
    expect(view?.percent).toBeCloseTo(12.9, 1);
  });

  it('DFS-057.AC-5 keeps the found totals in every phase after WALK', () => {
    for (const phase of ['PARTIAL_HASH', 'FULL_HASH', 'HARD_LINKS'] as const) {
      const view = phaseView(progress({ status: 'RUNNING', phase, files: 48210, bytes: 330_000_000_000 }));

      expect(view?.found, phase).toBe('Found: 48210 files · 307.3 GB');
    }
    expect(phaseView(progress({ status: 'RUNNING', phase: 'WALK', files: 5 }))?.found).toBeNull();
  });

  it('DFS-057.AC-5 never divides by zero when a total is 0', () => {
    const partial = phaseView(progress({ status: 'RUNNING', phase: 'PARTIAL_HASH', hashedFiles: 0, hashFilesTotal: 0 }));
    const full = phaseView(progress({ status: 'RUNNING', phase: 'FULL_HASH', hashedBytes: 0, hashBytesTotal: 0 }));
    const links = phaseView(progress({ status: 'RUNNING', phase: 'HARD_LINKS', groupsDone: 0, groupsTotal: 0 }));

    expect(`${partial?.label}: ${partial?.detail}`).toBe('Comparing samples: 0 of 0 files · 0 left · 0%');
    expect(partial?.percent).toBe(0);
    expect(full?.percent).toBe(0);
    expect(links?.percent).toBe(0);
  });

  it('DFS-057.AC-1 has no view for a scan with no phase', () => {
    // Reached after a positive case, so the test hits the stub first.
    expect(phaseView(progress({ status: 'RUNNING', phase: 'WALK' }))).not.toBeNull();
    expect(phaseView(progress({ status: 'QUEUED', phase: null }))).toBeNull();
  });
});
