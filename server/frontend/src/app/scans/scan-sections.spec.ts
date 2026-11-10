import { progress } from '../results/result-test-data';
import { overallCaption, overallPercent, sectionViews, stepLine } from './scan-sections';
import { completed, gb, hashingHalf, scanIn } from './scan-test-data';

describe('overallPercent', () => {
  it('DFS-063.AC-3 is none while a scan is queued or in the walk', () => {
    expect(overallPercent(progress({ status: 'QUEUED', phase: null }))).toBeNull();
    expect(overallPercent(scanIn('WALK'))).toBeNull();
  });

  it.each([
    ['compares 2954 of 29594 files', () => scanIn('PARTIAL_HASH', { hashedFiles: 2954, hashFilesTotal: 29594 }), 13],
    ['hashes 5.1 of 17.1 GB', () => hashingHalf(), 47],
    ['has nothing left to read', () => hashingHalf({ hashedBytes: gb(17.1), hashedFiles: 13845 }), 90],
    ['checks 1242 of 3104 groups', () => scanIn('HARD_LINKS', { groupsDone: 1242, groupsTotal: 3104 }), 94],
    ['checks 3104 of 3104 groups before COMPLETED', () => scanIn('HARD_LINKS', { groupsDone: 3104, groupsTotal: 3104 }), 99],
  ])('DFS-063.AC-3 follows the canvas when a scan %s', (_name, build, expected) => {
    expect(overallPercent(build())).toBe(expected);
  });

  it('DFS-063.AC-3 reads 100 only for a COMPLETED scan', () => {
    expect(overallPercent(completed())).toBe(100);
  });

  it('DFS-063.AC-3 is the same on both sides of a phase change and never NaN', () => {
    const comparing = scanIn('PARTIAL_HASH', { hashedFiles: 29594, hashFilesTotal: 29594 });
    const hashing = scanIn('FULL_HASH', { hashedFiles: 0, hashFilesTotal: 0, hashedBytes: 0, hashBytesTotal: 0 });

    expect(overallPercent(comparing)).toBe(30);
    expect(overallPercent(hashing)).toBe(30);
  });

  it('DFS-063.AC-3 counts a zero total as no progress', () => {
    expect(overallPercent(scanIn('PARTIAL_HASH', { hashedFiles: 0, hashFilesTotal: 0 }))).toBe(12);
    expect(overallPercent(scanIn('HARD_LINKS', { groupsDone: 0, groupsTotal: 0 }))).toBe(90);
  });

  it('DFS-063.AC-3 keeps the value of the phase a CANCELLED or FAILED scan stopped in', () => {
    expect(overallPercent(hashingHalf({ status: 'CANCELLED' }))).toBe(47);
    expect(overallPercent(hashingHalf({ status: 'FAILED', failureMessage: 'x' }))).toBe(47);
    expect(overallPercent(scanIn('WALK', { status: 'CANCELLED' }))).toBeNull();
  });
});

describe('overallCaption', () => {
  it('DFS-063.AC-2 captions a stopped scan "when stopped" and a running one "of whole scan"', () => {
    expect(overallCaption(hashingHalf())).toBe('of whole scan');
    expect(overallCaption(hashingHalf({ status: 'CANCELLED' }))).toBe('when stopped');
    expect(overallCaption(hashingHalf({ status: 'FAILED', failureMessage: 'x' }))).toBe('when stopped');
  });

  it('DFS-063.AC-1 captions a queued scan and the walk "total not known yet"', () => {
    expect(overallCaption(progress({ status: 'QUEUED', phase: null }))).toBe('total not known yet');
    expect(overallCaption(scanIn('WALK'))).toBe('total not known yet');
  });
});

describe('sectionViews', () => {
  const states = (p: Parameters<typeof sectionViews>[0]) => sectionViews(p).map((s) => s.state);

  it('DFS-063.AC-1 shows a queued scan as four sections not started', () => {
    const views = sectionViews(progress({ status: 'QUEUED', phase: null }));

    expect(views.map((s) => s.title)).toEqual([
      '1 · Finding files',
      '2 · Comparing samples',
      '3 · Hashing files',
      '4 · Checking hard links',
    ]);
    expect(views.map((s) => s.state)).toEqual(['not-started', 'not-started', 'not-started', 'not-started']);
    expect(views.map((s) => s.line1)).toEqual(['not started', 'not started', 'not started', 'not started']);
    expect(views.map((s) => s.tag)).toEqual([null, null, null, null]);
  });

  it('DFS-063.AC-2 keeps the running section running while the scan is CANCELLING', () => {
    expect(states(hashingHalf({ status: 'CANCELLING' }))).toEqual(['done', 'done', 'running', 'not-started']);
  });

  it('DFS-063.AC-2 shows the section stopped once the scan is CANCELLED', () => {
    expect(states(hashingHalf({ status: 'CANCELLED' }))).toEqual(['done', 'done', 'stopped', 'not-run']);
  });

  it('DFS-063.AC-7, DFS-063.AC-10 shows the running walk striped, tagged "now", with no percentage', () => {
    const [walk, ...rest] = sectionViews(scanIn('WALK'));

    expect(walk.state).toBe('running');
    expect(walk.striped).toBe(true);
    expect(walk.tag).toBe('now');
    expect(walk.line1).toBe('38886 files · 902.9 GB found');
    expect(walk.line2).toBe('in 4120 folders');
    expect(`${walk.tag}${walk.line1}${walk.line2}`).not.toContain('%');
    expect(rest.map((s) => s.state)).toEqual(['not-started', 'not-started', 'not-started']);
  });

  it('DFS-063.AC-7, DFS-063.AC-8, DFS-063.AC-9 shows finished totals, the running counts and what has not started', () => {
    const [walk, compare, hash, links] = sectionViews(
      scanIn('PARTIAL_HASH', { hashedFiles: 2954, hashFilesTotal: 29594 }),
    );

    expect([walk.state, walk.tag, walk.line1, walk.line2]).toEqual([
      'done',
      'done',
      '38886 files · 902.9 GB',
      'found in 4120 folders',
    ]);
    expect([compare.state, compare.tag, compare.line1, compare.line2]).toEqual([
      'running',
      'now',
      '2954 of 29594 files',
      '26640 left',
    ]);
    expect(compare.striped).toBe(false);
    expect(compare.fill).toBeGreaterThanOrEqual(9);
    expect(compare.fill).toBeLessThanOrEqual(10);
    expect([hash.state, hash.line1, hash.line2]).toEqual(['not-started', 'not started', 'total known after step 2']);
    expect([links.state, links.line1, links.line2]).toEqual(['not-started', 'not started', 'total known after step 3']);
  });

  it('DFS-063.AC-7 shows hashing with nothing left to read as finishing, full and striped', () => {
    const hash = sectionViews(hashingHalf({ hashedBytes: gb(17.1), hashedFiles: 13845 }))[2];

    expect(hash.state).toBe('finishing');
    expect(hash.striped).toBe(true);
    expect(hash.fill).toBe(100);
    expect(hash.tag).toBe('finishing');
    expect(hash.line1).toBe('17.1 of 17.1 GB · nothing left to read');
    expect(hash.line2).toBe('13845 of 13845 files · finishing…');
  });

  it('DFS-063.AC-7 shows hashing as running, not striped, while bytes are left', () => {
    const hash = sectionViews(hashingHalf())[2];

    expect(hash.state).toBe('running');
    expect(hash.striped).toBe(false);
    expect(hash.line1).toBe('5.1 of 17.1 GB · 12.0 GB left');
    expect(hash.line2).toBe('6210 of 13845 files · 7635 left');
  });

  it('DFS-063.AC-8 shows every section done with its totals once the scan is COMPLETED', () => {
    const views = sectionViews(completed());

    expect(views.map((s) => s.state)).toEqual(['done', 'done', 'done', 'done']);
    expect(views.map((s) => s.line1)).toEqual([
      '38886 files · 902.9 GB',
      '29594 compared',
      '13845 hashed · 17.1 GB',
      '3104 groups checked',
    ]);
    expect(views.map((s) => s.line2)).toEqual([
      'found in 4120 folders',
      '→ 13845 candidates',
      '→ 3104 matching groups',
      '→ 2987 duplicate groups',
    ]);
  });

  it('DFS-063.AC-9 shows a CANCELLED scan its counts and later sections as not run', () => {
    const [walk, compare, hash, links] = sectionViews(hashingHalf({ status: 'CANCELLED' }));

    expect(walk.line2).toBe('found in 4120 folders');
    expect(compare.line1).toBe('29594 compared');
    expect([hash.line1, hash.line2]).toEqual(['5.1 of 17.1 GB read', '6210 of 13845 files']);
    expect([links.state, links.line1, links.line2, links.tag]).toEqual(['not-run', 'not run', '', null]);
  });

  it('DFS-063.AC-10, DFS-063.AC-11 tags each state as text: done, now, stopped, failed, and none when not reached', () => {
    const tags = (p: Parameters<typeof sectionViews>[0]) => sectionViews(p).map((s) => s.tag);

    expect(tags(hashingHalf())).toEqual(['done', 'done', 'now', null]);
    expect(tags(hashingHalf({ status: 'CANCELLED' }))).toEqual(['done', 'done', 'stopped', null]);
    expect(tags(hashingHalf({ status: 'FAILED', failureMessage: 'disk read failed' }))).toEqual([
      'done',
      'done',
      'failed',
      null,
    ]);
    expect(states(hashingHalf({ status: 'FAILED', failureMessage: 'disk read failed' }))).toEqual([
      'done',
      'done',
      'failed',
      'not-run',
    ]);
  });
});

describe('stepLine', () => {
  it('DFS-063.AC-1 reads "Waiting to start" for a queued scan', () => {
    expect(stepLine(progress({ status: 'QUEUED', phase: null }))).toBe('Waiting to start');
  });

  it('DFS-063.AC-7 names the step of a running scan, and finishing when hashing has nothing left', () => {
    expect(stepLine(hashingHalf())).toBe('Step 3 of 4 · Hashing files');
    expect(stepLine(hashingHalf({ hashedBytes: gb(17.1) }))).toBe('Step 3 of 4 · Hashing files, finishing…');
  });

  it('DFS-063.AC-9, DFS-063.AC-11 names the step a scan was cancelled or failed in', () => {
    expect(stepLine(hashingHalf({ status: 'CANCELLED' }))).toBe('Cancelled during step 3 · Hashing files');
    expect(stepLine(hashingHalf({ status: 'FAILED', failureMessage: 'x' }))).toBe('Failed during step 3 · Hashing files');
  });

  it('DFS-063.AC-12 is empty for a COMPLETED scan, whose row is collapsed', () => {
    expect(stepLine(completed())).toBe('');
  });
});
