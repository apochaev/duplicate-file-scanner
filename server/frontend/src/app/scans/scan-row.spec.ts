import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { MatButtonHarness } from '@angular/material/button/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, withHashLocation } from '@angular/router';
import { ScanEntry, ScanProgress } from '../api/models';
import { provideStubSurfacing, settle } from '../../testing/stub-surfacing';
import { progress, scanEntry } from '../results/result-test-data';
import { EVENT_SOURCE } from './scan-events.service';
import { CLOCK } from './scan-eta';
import { ScanRow } from './scan-row';
import { completed, gb, hashingHalf, scanIn, startedPhases } from './scan-test-data';

/** A stream the test drives by hand, as the server's does. */
class FakeEventSource {
  static opened: FakeEventSource[] = [];
  readyState = 1;
  onerror: ((event: Event) => void) | null = null;
  private readonly listeners: ((event: MessageEvent) => void)[] = [];

  constructor(readonly url: string) {
    FakeEventSource.opened.push(this);
  }

  addEventListener(type: string, listener: (event: MessageEvent) => void): void {
    if (type === 'progress') this.listeners.push(listener);
  }

  close(): void {
    this.readyState = 2;
  }

  /** Delivers one `progress` event carrying exactly this progress. */
  send(update: ScanProgress): void {
    const event = new MessageEvent('progress', { data: JSON.stringify(update) });
    for (const listener of this.listeners) listener(event);
  }
}

describe('ScanRow', () => {
  let now = 0;
  let fixture: ComponentFixture<ScanRow>;
  let el: HTMLElement;

  beforeEach(async () => {
    now = 0;
    FakeEventSource.opened = [];
    await TestBed.configureTestingModule({
      imports: [ScanRow],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([], withHashLocation()),
        provideStubSurfacing(),
        { provide: EVENT_SOURCE, useValue: (url: string) => new FakeEventSource(url) as unknown as EventSource },
        { provide: CLOCK, useValue: () => now },
      ],
    }).compileComponents();
  });

  afterEach(() => TestBed.inject(HttpTestingController).verify());

  async function renderEntry(entry: ScanEntry): Promise<void> {
    fixture = TestBed.createComponent(ScanRow);
    fixture.componentRef.setInput('entry', entry);
    await settle(fixture);
    el = fixture.nativeElement as HTMLElement;
  }

  async function render(update: ScanProgress, id = 's-1'): Promise<void> {
    await renderEntry({ ...scanEntry(id), progress: update });
  }

  async function send(update: ScanProgress, atSeconds?: number): Promise<void> {
    if (atSeconds !== undefined) now = atSeconds * 1000;
    FakeEventSource.opened[0].send(update);
    await settle(fixture);
  }

  const text = (selector: string): string | undefined =>
    el.querySelector(selector)?.textContent?.replace(/\s+/g, ' ').trim();

  const section = (phase: string): Element | null => el.querySelector(`.scan-section[data-phase="${phase}"]`);

  const sectionText = (phase: string, part: string): string | undefined =>
    section(phase)?.querySelector(part)?.textContent?.trim();

  const lines = (phase: string): (string | undefined)[] => [
    sectionText(phase, '.section-line1'),
    sectionText(phase, '.section-line2'),
  ];

  const sectionClasses = (): string[] =>
    Array.from(el.querySelectorAll('.scan-section')).map(
      (li) => Array.from(li.classList).find((c) => c.startsWith('section-') && !['section-head'].includes(c)) ?? '',
    );

  function cancelButton(): Promise<MatButtonHarness> {
    return TestbedHarnessEnvironment.loader(fixture).getHarness(MatButtonHarness.with({ text: 'Cancel' }));
  }

  it('DFS-049.AC-2 shows "Open results" to #/scans/<id> on a COMPLETED row, also with 0 duplicate sets', async () => {
    await render(completed({ groupsTotal: 3 }), 's-3');
    const first = el.querySelector('a.open-results');
    expect(first?.textContent?.trim()).toBe('Open results');
    expect(first?.getAttribute('href')).toBe('#/scans/s-3');

    await render(completed({ groupsTotal: 0 }), 's-0');
    const second = el.querySelector('a.open-results');
    expect(second?.textContent?.trim()).toBe('Open results');
    expect(second?.getAttribute('href')).toBe('#/scans/s-0');
  });

  it('DFS-049.AC-2 shows no "Open results" link on a QUEUED, RUNNING, CANCELLING, CANCELLED or FAILED row', async () => {
    // Rendered after a COMPLETED row, so the test reaches a stub: a row with no link never would.
    await render(completed(), 's-1');
    expect(el.querySelector('a.open-results')).not.toBeNull();

    for (const status of ['QUEUED', 'RUNNING', 'CANCELLING', 'CANCELLED', 'FAILED'] as const) {
      await render(
        scanIn('WALK', {
          status,
          phase: status === 'QUEUED' ? null : 'WALK',
          failureMessage: status === 'FAILED' ? 'Disk not readable' : null,
        }),
        's-9',
      );

      expect(el.querySelector('a.open-results'), status).toBeNull();
      expect(el.textContent, status).not.toContain('Open results');
    }
  });

  it('DFS-063.AC-1 shows a QUEUED scan as four sections not started, "—", "Waiting to start" and an enabled Cancel', async () => {
    await render(progress({ status: 'QUEUED', phase: null }));

    expect(sectionClasses()).toEqual(['section-not-started', 'section-not-started', 'section-not-started', 'section-not-started']);
    expect(lines('PARTIAL_HASH')).toEqual(['not started', 'total known after step 1']);
    expect(lines('FULL_HASH')).toEqual(['not started', 'total known after step 2']);
    expect(lines('HARD_LINKS')).toEqual(['not started', 'total known after step 3']);
    expect(lines('WALK')[0]).toBe('not started');
    expect(text('.scan-overall-value')).toBe('—');
    expect(text('.scan-step')).toBe('Waiting to start');
    expect(el.querySelector('.scan-time-left')).toBeNull();
    expect(el.querySelectorAll('button')).toHaveLength(1);
    expect(await (await cancelButton()).isDisabled()).toBe(false);
  });

  it('DFS-063.AC-2 holds the last percentage while CANCELLING, keeping the running section and a disabled Cancel', async () => {
    await render(hashingHalf());
    expect(text('.scan-overall-value')).toBe('47%');
    expect(el.querySelector('.scan-cancelling')).toBeNull();

    await send(hashingHalf({ status: 'CANCELLING', hashedBytes: gb(8), hashedFiles: 8000 }));

    expect(text('.scan-overall-value')).toBe('47%');
    expect(text('.scan-cancelling')).toBe('Cancelling…');
    expect(await (await cancelButton()).isDisabled()).toBe(true);
    expect(section('FULL_HASH')?.classList.contains('section-running')).toBe(true);
  });

  it('DFS-063.AC-2 shows the hashing section as stopped, captioned "when stopped", once CANCELLED', async () => {
    await render(hashingHalf());
    await send(hashingHalf({ status: 'CANCELLING', hashedBytes: gb(8), hashedFiles: 8000 }));

    await send(hashingHalf({ status: 'CANCELLED', hashedBytes: gb(8), hashedFiles: 8000 }));

    expect(section('FULL_HASH')?.classList.contains('section-stopped')).toBe(true);
    expect(text('.scan-overall-caption')).toBe('when stopped');
    expect(el.querySelector('.scan-cancelling')).toBeNull();
    expect(el.querySelectorAll('button')).toHaveLength(0);
  });

  it.each([
    ['in the walk, 38886 files found', () => scanIn('WALK'), '—'],
    ['comparing, 2954 of 29594 files', () => scanIn('PARTIAL_HASH', { hashedFiles: 2954, hashFilesTotal: 29594 }), '13%'],
    ['hashing, 5.1 of 17.1 GB', () => hashingHalf(), '47%'],
    ['hashing, nothing left to read', () => hashingHalf({ hashedBytes: gb(17.1), hashedFiles: 13845 }), '90%'],
    ['hard links, 1242 of 3104 groups', () => scanIn('HARD_LINKS', { groupsDone: 1242, groupsTotal: 3104 }), '94%'],
    ['hard links, 3104 of 3104, not yet COMPLETED', () => scanIn('HARD_LINKS', { groupsDone: 3104, groupsTotal: 3104 }), '99%'],
  ])('DFS-063.AC-3 reads the overall percentage of a scan %s as "%s"', async (_name, build, expected) => {
    await render(build());

    expect(text('.scan-overall-value')).toBe(expected);
  });

  it('DFS-063.AC-3 reads 100% only once the scan is COMPLETED', async () => {
    await render(scanIn('HARD_LINKS', { groupsDone: 3104, groupsTotal: 3104 }));
    expect(text('.scan-overall-value')).toBe('99%');

    await send(completed());

    expect(text('.scan-overall-value')).toBe('100%');
  });

  it('DFS-063.AC-3 reads the same percentage on both sides of a phase change, never NaN', async () => {
    await render(scanIn('PARTIAL_HASH', { hashedFiles: 29594, hashFilesTotal: 29594 }));
    expect(text('.scan-overall-value')).toBe('30%');

    await send(scanIn('FULL_HASH', { hashedFiles: 0, hashFilesTotal: 0, hashedBytes: 0, hashBytesTotal: 0 }));

    expect(text('.scan-overall-value')).toBe('30%');
    expect(el.textContent).not.toContain('NaN');
  });

  it('DFS-063.AC-4 shows "estimating…" for 30 s of hashing, then the time left from the rate', async () => {
    const hash = (bytes: number) => scanIn('FULL_HASH', { hashedBytes: bytes, hashBytesTotal: 2600 * 1_000_000 });
    await render(hash(0));
    expect(text('.scan-time-left')).toBe('estimating…');

    await send(hash(290 * 1_000_000), 29);
    expect(text('.scan-time-left')).toBe('estimating…');

    await send(hash(300 * 1_000_000), 30);
    expect(text('.scan-time-left')).toBe('about 4 min left');
  });

  it('DFS-063.AC-4 shows no time left in the walk or while comparing', async () => {
    await render(hashingHalf());
    expect(el.querySelector('.scan-time-left')).not.toBeNull();

    await send(scanIn('WALK'));
    expect(el.querySelector('.scan-time-left')).toBeNull();

    await send(scanIn('PARTIAL_HASH', { hashedFiles: 100, hashFilesTotal: 1000 }));
    expect(el.querySelector('.scan-time-left')).toBeNull();
  });

  it('DFS-063.AC-5 shows the hard-link time left from the groups rate after 30 s', async () => {
    const links = (done: number) => scanIn('HARD_LINKS', { groupsDone: done, groupsTotal: 3000 });
    await render(links(0));
    expect(text('.scan-time-left')).toBe('estimating…');

    await send(links(580), 29);
    expect(text('.scan-time-left')).toBe('estimating…');

    await send(links(600), 30);
    expect(text('.scan-time-left')).toBe('about 2 min left');
  });

  it('DFS-063.AC-6 shows "finishing…" instead of a time when no bytes are left to read, inside the warm-up too', async () => {
    const total = gb(17.1);
    await render(scanIn('FULL_HASH', { hashedBytes: 0, hashBytesTotal: total, hashFilesTotal: 13845 }));
    expect(text('.scan-time-left')).toBe('estimating…');

    await send(scanIn('FULL_HASH', { hashedBytes: total, hashBytesTotal: total, hashedFiles: 13845, hashFilesTotal: 13845 }), 4);

    expect(text('.scan-time-left')).toBe('finishing…');
    expect(text('.scan-step')).toBe('Step 3 of 4 · Hashing files, finishing…');
  });

  it('DFS-063.AC-7 shows the running walk as its found totals over the folders', async () => {
    await render(scanIn('WALK'));

    expect(lines('WALK')).toEqual(['38886 files · 902.9 GB found', 'in 4120 folders']);
    expect(sectionText('WALK', '.section-tag')).toBe('now');
    expect(section('WALK')?.textContent).not.toContain('%');
  });

  it.each([
    [
      'comparing, 2954 of 29594 files',
      () => scanIn('PARTIAL_HASH', { hashedFiles: 2954, hashFilesTotal: 29594 }),
      'PARTIAL_HASH',
      ['2954 of 29594 files', '26640 left'],
    ],
    [
      'hashing, 5.1 of 17.1 GB',
      () => hashingHalf(),
      'FULL_HASH',
      ['5.1 of 17.1 GB · 12.0 GB left', '6210 of 13845 files · 7635 left'],
    ],
    [
      'hashing, nothing left to read',
      () => hashingHalf({ hashedBytes: gb(17.1), hashedFiles: 13845 }),
      'FULL_HASH',
      ['17.1 of 17.1 GB · nothing left to read', '13845 of 13845 files · finishing…'],
    ],
    [
      'hard links, 1242 of 3104 groups',
      () => scanIn('HARD_LINKS', { groupsDone: 1242, groupsTotal: 3104 }),
      'HARD_LINKS',
      ['1242 of 3104 groups', '1862 left'],
    ],
  ])('DFS-063.AC-7 shows the running section of a scan %s as its two lines', async (_name, build, phase, expected) => {
    await render(build());

    expect(lines(phase)).toEqual(expected);
  });

  it('DFS-063.AC-8 shows the finished sections their totals while hard links run', async () => {
    await render(scanIn('HARD_LINKS', { groupsDone: 1242, groupsTotal: 3104 }));

    expect(lines('WALK')).toEqual(['38886 files · 902.9 GB', 'found in 4120 folders']);
    expect(lines('PARTIAL_HASH')).toEqual(['29594 compared', '→ 13845 candidates']);
    expect(lines('FULL_HASH')).toEqual(['13845 hashed · 17.1 GB', '→ 3104 matching groups']);
  });

  it('DFS-063.AC-8 shows a finished section without its arrow line until the number is known', async () => {
    const noLinks = startedPhases('FULL_HASH').map((p) => ({ ...p, finished: true }));
    await render(scanIn('HARD_LINKS', { groupsDone: 0, groupsTotal: 3104, phases: noLinks }));

    expect(lines('FULL_HASH')).toEqual(['13845 hashed · 17.1 GB', '']);
  });

  it('DFS-063.AC-9 shows sections not started as "not started" over the step before them', async () => {
    await render(scanIn('PARTIAL_HASH', { hashedFiles: 100, hashFilesTotal: 1000 }));

    expect(lines('FULL_HASH')).toEqual(['not started', 'total known after step 2']);
    expect(lines('HARD_LINKS')).toEqual(['not started', 'total known after step 3']);
  });

  it('DFS-063.AC-9 shows a CANCELLED scan the counts it reached, later sections as not run and the earlier totals', async () => {
    await render(hashingHalf({ status: 'CANCELLED' }));

    expect(lines('FULL_HASH')).toEqual(['5.1 of 17.1 GB read', '6210 of 13845 files']);
    expect(sectionText('HARD_LINKS', '.section-line1')).toBe('not run');
    expect(sectionText('HARD_LINKS', '.section-line2')).toBe('');
    expect(lines('WALK')).toEqual(['38886 files · 902.9 GB', 'found in 4120 folders']);
    expect(lines('PARTIAL_HASH')).toEqual(['29594 compared', '→ 13845 candidates']);
  });

  it('DFS-063.AC-10 tags each state as text: done, now, not started, stopped and failed, and the walk has no percentage', async () => {
    await render(scanIn('WALK'));
    expect(sectionText('WALK', '.section-tag')).toBe('now');
    expect(section('WALK')?.textContent).not.toContain('%');

    await render(hashingHalf());
    expect(sectionText('WALK', '.section-tag')).toBe('done');
    expect(sectionText('FULL_HASH', '.section-tag')).toBe('now');
    expect(lines('HARD_LINKS')[0]).toBe('not started');

    await render(hashingHalf({ status: 'CANCELLED' }));
    expect(sectionText('FULL_HASH', '.section-tag')).toBe('stopped');

    await render(hashingHalf({ status: 'FAILED', failureMessage: 'disk read failed' }));
    expect(sectionText('FULL_HASH', '.section-tag')).toBe('failed');
  });

  it('DFS-063.AC-11 tags the failing section and shows "Scan failed while hashing files. <message>"', async () => {
    await render(hashingHalf());
    expect(el.querySelector('.scan-failure')).toBeNull();

    await render(hashingHalf({ status: 'FAILED', failureMessage: 'disk read failed' }));

    expect(sectionText('FULL_HASH', '.section-tag')).toBe('failed');
    expect(section('FULL_HASH')?.classList.contains('section-failed')).toBe(true);
    expect(text('.scan-failure')).toBe('Scan failed while hashing files. disk read failed');
  });

  it('DFS-063.AC-12 collapses a COMPLETED scan to one line with its totals, duration and the results link', async () => {
    // Local-time strings, so the HH:mm the page prints does not depend on the machine's zone.
    const entry: ScanEntry = {
      ...scanEntry('s-1'),
      startedAt: '2026-11-01T14:02:30',
      finishedAt: '2026-11-01T14:17:08',
      progress: completed(),
    };
    await renderEntry(entry);

    expect(el.querySelector('.scan-collapsed')).not.toBeNull();
    expect(el.querySelector('.scan-expanded')).toBeNull();
    expect(text('.scan-started')).toBe('Completed · started 14:02');
    expect(text('.scan-totals')).toBe(
      '38886 files · 902.9 GB found → 29594 compared → 13845 hashed · 17.1 GB → 2987 duplicate groups',
    );
    expect(text('.scan-overall-value')).toBe('100%');
    expect(text('.scan-duration')).toBe('took 14 min 38 s');
    expect(text('.scan-finished')).toBe('finished 14:17');
    expect(text('a.open-results')).toBe('Open results');
    expect(el.querySelector('.scan-strip-thin')).not.toBeNull();
    expect(el.querySelectorAll('.scan-section')).toHaveLength(0);
    expect(el.querySelectorAll('button')).toHaveLength(0);
  });

  it('DFS-063.AC-12 keeps a CANCELLED and a FAILED row expanded, the failed one with its message', async () => {
    await render(hashingHalf({ status: 'CANCELLED' }));
    expect(el.querySelector('.scan-collapsed')).toBeNull();
    expect(el.querySelectorAll('.scan-section')).toHaveLength(4);

    await render(hashingHalf({ status: 'FAILED', failureMessage: 'disk read failed' }));
    expect(el.querySelector('.scan-collapsed')).toBeNull();
    expect(el.querySelectorAll('.scan-section')).toHaveLength(4);
    expect(text('.scan-failure')).toContain('disk read failed');
  });

  it.each([
    [42, 'took 42 s'],
    [59, 'took 59 s'],
    [878, 'took 14 min 38 s'],
    [3599, 'took 59 min 59 s'],
    [3900, 'took 1 h 05 min'],
    [7230, 'took 2 h 00 min'],
  ])('DFS-063.AC-13 reads a scan that took %s seconds as "%s"', async (seconds, expected) => {
    const start = Date.parse('2026-11-01T10:00:00Z');
    await renderEntry({
      ...scanEntry('s-1'),
      startedAt: new Date(start).toISOString(),
      finishedAt: new Date(start + seconds * 1000).toISOString(),
      progress: completed(),
    });

    expect(text('.scan-duration')).toBe(expected);
  });

  it('DFS-063.AC-15 animates the stripes only outside prefers-reduced-motion: reduce', async () => {
    // Rendered first so the test reaches the stubs; the component's styles are then in the document.
    await render(scanIn('WALK'));
    expect(section('WALK')).not.toBeNull();

    const css = Array.from(document.querySelectorAll('style'))
      .map((style) => style.textContent ?? '')
      .join('\n')
      .replace(/\/\*[\s\S]*?\*\//g, '');
    const open = css.search(/@media\s*\(\s*prefers-reduced-motion\s*:\s*reduce\s*\)\s*\{/);
    expect(open, 'a prefers-reduced-motion: reduce media block').toBeGreaterThanOrEqual(0);

    let depth = 0;
    let end = css.indexOf('{', open);
    for (let i = end; i < css.length; i++) {
      if (css[i] === '{') depth++;
      if (css[i] === '}' && --depth === 0) {
        end = i;
        break;
      }
    }
    const reduced = css.slice(css.indexOf('{', open) + 1, end);
    const outside = css.slice(0, open) + css.slice(end + 1);

    expect(outside).toMatch(/@keyframes/);
    expect(outside).toMatch(/animation(-name)?\s*:\s*(?!none)/);
    expect(reduced).toMatch(/animation(-name)?\s*:\s*none/);
    expect(reduced).not.toMatch(/animation(-name)?\s*:\s*(?!none)/);
  });
});
