import { progress } from '../results/result-test-data';
import {
  activityLabel,
  collapsedTotals,
  failureLine,
  finishedLines,
  formatDuration,
  notStartedLines,
  runningLines,
  stoppedLines,
} from './scan-format';
import { completed, gb, hashingHalf, scanIn, startedPhases } from './scan-test-data';

describe('formatDuration', () => {
  it.each([
    [42, '42 s'],
    [59, '59 s'],
    [878, '14 min 38 s'],
    [3599, '59 min 59 s'],
    [3900, '1 h 05 min'],
    [3659, '1 h 00 min'],
    [7230, '2 h 00 min'],
  ])('DFS-063.AC-13 writes %s seconds as "%s"', (seconds, text) => {
    expect(formatDuration(seconds)).toBe(text);
  });
});

describe('runningLines', () => {
  it('DFS-063.AC-7 reads the running walk as files and size found over the folders', () => {
    expect(runningLines(scanIn('WALK'), 'WALK')).toEqual(['38886 files · 902.9 GB found', 'in 4120 folders']);
  });

  it('DFS-063.AC-7 reads comparing as files done of files in over the files left', () => {
    const lines = runningLines(scanIn('PARTIAL_HASH', { hashedFiles: 2954, hashFilesTotal: 29594 }), 'PARTIAL_HASH');
    expect(lines).toEqual(['2954 of 29594 files', '26640 left']);
  });

  it('DFS-063.AC-7 reads hashing as bytes and files done, with what is left', () => {
    expect(runningLines(hashingHalf(), 'FULL_HASH')).toEqual([
      '5.1 of 17.1 GB · 12.0 GB left',
      '6210 of 13845 files · 7635 left',
    ]);
  });

  it('DFS-063.AC-7 reads hashing with no bytes left as nothing left to read, finishing', () => {
    const lines = runningLines(
      hashingHalf({ hashedBytes: gb(17.1), hashedFiles: 13845 }),
      'FULL_HASH',
    );
    expect(lines).toEqual(['17.1 of 17.1 GB · nothing left to read', '13845 of 13845 files · finishing…']);
  });

  it('DFS-063.AC-7 reads hard links as groups done of groups over the groups left', () => {
    const lines = runningLines(scanIn('HARD_LINKS', { groupsDone: 1242, groupsTotal: 3104 }), 'HARD_LINKS');
    expect(lines).toEqual(['1242 of 3104 groups', '1862 left']);
  });
});

describe('finishedLines', () => {
  const done = completed();

  it('DFS-063.AC-8 reads each finished section from its totals', () => {
    expect(finishedLines(done, 'WALK')).toEqual(['38886 files · 902.9 GB', 'found in 4120 folders']);
    expect(finishedLines(done, 'PARTIAL_HASH')).toEqual(['29594 compared', '→ 13845 candidates']);
    expect(finishedLines(done, 'FULL_HASH')).toEqual(['13845 hashed · 17.1 GB', '→ 3104 matching groups']);
    expect(finishedLines(done, 'HARD_LINKS')).toEqual(['3104 groups checked', '→ 2987 duplicate groups']);
  });

  it('DFS-063.AC-8 leaves the second line empty until the number its arrow needs is known', () => {
    const noLinks = scanIn('HARD_LINKS', { phases: startedPhases('FULL_HASH').map((p) => ({ ...p, finished: true })) });

    expect(finishedLines(noLinks, 'FULL_HASH')).toEqual(['13845 hashed · 17.1 GB', '']);
  });
});

describe('stoppedLines and notStartedLines', () => {
  it('DFS-063.AC-9 reads the section a scan stopped in as the counts it reached', () => {
    const cancelled = hashingHalf({ status: 'CANCELLED' });

    expect(stoppedLines(cancelled, 'FULL_HASH')).toEqual(['5.1 of 17.1 GB read', '6210 of 13845 files']);
  });

  it('DFS-063.AC-9 names the step before a section that has not started', () => {
    expect(notStartedLines('PARTIAL_HASH', false)).toEqual(['not started', 'total known after step 1']);
    expect(notStartedLines('FULL_HASH', false)).toEqual(['not started', 'total known after step 2']);
    expect(notStartedLines('HARD_LINKS', false)).toEqual(['not started', 'total known after step 3']);
    expect(notStartedLines('WALK', false)).toEqual(['not started', '']);
  });

  it('DFS-063.AC-9 reads a section after a stopped one as not run, with nothing under it', () => {
    expect(notStartedLines('HARD_LINKS', true)).toEqual(['not run', '']);
  });
});

describe('collapsedTotals', () => {
  it('DFS-063.AC-12 narrows from the files found to the duplicate groups', () => {
    expect(collapsedTotals(completed())).toBe(
      '38886 files · 902.9 GB found → 29594 compared → 13845 hashed · 17.1 GB → 2987 duplicate groups',
    );
  });
});

describe('failure line', () => {
  it.each([
    ['WALK', 'finding files'],
    ['PARTIAL_HASH', 'comparing samples'],
    ['FULL_HASH', 'hashing files'],
    ['HARD_LINKS', 'checking hard links'],
  ] as const)('DFS-063.AC-11 names the activity of %s as "%s"', (phase, activity) => {
    expect(activityLabel(phase)).toBe(activity);
  });

  it('DFS-063.AC-11 writes the failure line with the activity and the message', () => {
    const failed = hashingHalf({ status: 'FAILED', failureMessage: 'disk read failed' });

    expect(failureLine(failed)).toBe('Scan failed while hashing files. disk read failed');
  });

  it('DFS-063.AC-11 writes the failure line without an activity when the scan had no phase', () => {
    const failed = progress({ status: 'FAILED', phase: null, failureMessage: 'disk read failed' });

    expect(failureLine(failed)).toBe('Scan failed. disk read failed');
  });
});
