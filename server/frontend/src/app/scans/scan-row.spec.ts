import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { MatProgressBarHarness } from '@angular/material/progress-bar/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, withHashLocation } from '@angular/router';
import { ScanProgress } from '../api/models';
import { provideStubSurfacing, settle } from '../../testing/stub-surfacing';
import { progress, scanEntry } from '../results/result-test-data';
import { EVENT_SOURCE } from './scan-events.service';
import { CLOCK } from './scan-eta';
import { ScanRow } from './scan-row';

/** A stream that never delivers: the rows under test only need the link, not live progress. */
class SilentEventSource {
  readyState = 1;
  onerror: ((event: Event) => void) | null = null;
  addEventListener(): void {}
  close(): void {
    this.readyState = 2;
  }
}

describe('ScanRow', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ScanRow],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([], withHashLocation()),
        provideStubSurfacing(),
        { provide: EVENT_SOURCE, useValue: () => new SilentEventSource() as unknown as EventSource },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function render(id: string, update: Partial<ScanProgress>): Promise<HTMLElement> {
    const fixture = TestBed.createComponent(ScanRow);
    fixture.componentRef.setInput('entry', scanEntry(id, update));
    await settle(fixture);
    return fixture.nativeElement as HTMLElement;
  }

  it('DFS-049.AC-2 shows "Open results" to #/scans/<id> on a COMPLETED row, also with 0 duplicate sets', async () => {
    const withSets = await render('s-3', { status: 'COMPLETED', groupsTotal: 3 });
    const noSets = await render('s-0', { status: 'COMPLETED', groupsTotal: 0 });

    const first = withSets.querySelector('a.open-results');
    expect(first?.textContent?.trim()).toBe('Open results');
    expect(first?.getAttribute('href')).toBe('#/scans/s-3');
    const second = noSets.querySelector('a.open-results');
    expect(second?.textContent?.trim()).toBe('Open results');
    expect(second?.getAttribute('href')).toBe('#/scans/s-0');
  });

  it('DFS-049.AC-2 shows no "Open results" link on a QUEUED, RUNNING, CANCELLING, CANCELLED or FAILED row', async () => {
    // Rendered after a COMPLETED row, so the test reaches the link's stub: a row with no link never would.
    const done = await render('s-1', { status: 'COMPLETED' });
    expect(done.querySelector('a.open-results')).not.toBeNull();

    for (const status of ['QUEUED', 'RUNNING', 'CANCELLING', 'CANCELLED', 'FAILED'] as const) {
      const row = await render('s-9', { status, failureMessage: status === 'FAILED' ? 'Disk not readable' : null });

      expect(row.querySelector('a.open-results'), status).toBeNull();
      expect(row.textContent, status).not.toContain('Open results');
    }
  });
});

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

  send(update: Partial<ScanProgress>): void {
    const event = new MessageEvent('progress', { data: JSON.stringify(progress({ status: 'RUNNING', ...update })) });
    for (const listener of this.listeners) listener(event);
  }
}

describe('ScanRow phase lines', () => {
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

  async function render(update: Partial<ScanProgress> = { status: 'RUNNING', phase: 'WALK' }): Promise<void> {
    fixture = TestBed.createComponent(ScanRow);
    fixture.componentRef.setInput('entry', scanEntry('s-1', update));
    await settle(fixture);
    el = fixture.nativeElement as HTMLElement;
  }

  async function send(update: Partial<ScanProgress>, atSeconds?: number): Promise<void> {
    if (atSeconds !== undefined) now = atSeconds * 1000;
    FakeEventSource.opened[0].send(update);
    await settle(fixture);
  }

  const text = (selector: string): string | undefined => el.querySelector(selector)?.textContent?.trim();

  async function bar(): Promise<MatProgressBarHarness> {
    return TestbedHarnessEnvironment.loader(fixture).getHarness(MatProgressBarHarness);
  }

  it('DFS-057.AC-1, DFS-057.AC-8 shows what a walk has found over an indeterminate bar', async () => {
    await render();
    await send({ phase: 'WALK', directories: 1204, files: 48210, bytes: 330_000_000_000 });

    expect(text('.scan-phase-line')).toBe('Finding files: 1204 folders · 48210 files · 307.3 GB found');
    expect(await (await bar()).getMode()).toBe('indeterminate');
    expect(el.querySelector('.scan-found')).toBeNull();
    expect(el.querySelector('.scan-eta')).toBeNull();
  });

  it('DFS-057.AC-2 shows the samples compared, the files left and a bar following files', async () => {
    await render();
    await send({
      phase: 'PARTIAL_HASH',
      hashedFiles: 3100,
      hashFilesTotal: 9400,
      hashedBytes: 400_000_000,
      hashBytesTotal: 1_200_000_000,
    });

    expect(text('.scan-phase-line')).toBe('Comparing samples: 3100 of 9400 files · 6300 left · 33%');
    expect(await (await bar()).getMode()).toBe('determinate');
    expect(await (await bar()).getValue()).toBeCloseTo(33, 0);
    expect(el.textContent).not.toContain('MB');
  });

  it('DFS-057.AC-3, DFS-057.AC-8 shows files and bytes done and left, a bar following bytes and an ETA', async () => {
    await render();
    await send({
      phase: 'FULL_HASH',
      hashedFiles: 1204,
      hashFilesTotal: 5880,
      hashedBytes: 3_328_599_654,
      hashBytesTotal: 13_314_398_618,
    });

    expect(text('.scan-phase-line')).toBe(
      'Hashing files: 1204 of 5880 files · 4676 left · 3.1 GB of 12.4 GB · 9.3 GB left · 25%',
    );
    expect(await (await bar()).getValue()).toBeCloseTo(25, 0);
    expect(text('.scan-eta')).toBe('estimating…');
  });

  it('DFS-057.AC-4 shows the hard-link groups done and a bar following groups', async () => {
    await render();
    await send({ phase: 'HARD_LINKS', groupsDone: 40, groupsTotal: 310 });

    expect(text('.scan-phase-line')).toBe('Checking hard links: group 40 of 310');
    expect(await (await bar()).getValue()).toBeCloseTo(12.9, 0);
    expect(el.querySelector('.scan-eta')).toBeNull();
  });

  it('DFS-057.AC-5 keeps the found line after WALK and shows none in WALK', async () => {
    await render();
    await send({ phase: 'WALK', files: 10, bytes: 1000 });
    expect(el.querySelector('.scan-found')).toBeNull();

    await send({ phase: 'FULL_HASH', files: 48210, bytes: 330_000_000_000 });
    expect(text('.scan-found')).toBe('Found: 48210 files · 307.3 GB');
  });

  it('DFS-057.AC-5 reads 0% and an empty bar when a total is 0', async () => {
    await render();
    await send({ phase: 'PARTIAL_HASH', hashedFiles: 0, hashFilesTotal: 0 });

    expect(text('.scan-phase-line')).toBe('Comparing samples: 0 of 0 files · 0 left · 0%');
    expect(await (await bar()).getValue()).toBe(0);
  });

  it('DFS-057.AC-6 shows "estimating…", then the ETA from the recent rate, and none in the next phase', async () => {
    await render();
    const full = { phase: 'FULL_HASH' as const, hashBytesTotal: 6_000_000_000 };
    await send({ ...full, hashedBytes: 0 }, 0);
    await send({ ...full, hashedBytes: 100_000_000 }, 2);
    expect(text('.scan-eta')).toBe('estimating…');

    await send({ ...full, hashedBytes: 300_000_000 }, 4);
    expect(text('.scan-eta')).toBe('about 1 min left');

    await send({ phase: 'HARD_LINKS', groupsDone: 1, groupsTotal: 3 }, 5);
    expect(el.querySelector('.scan-eta')).toBeNull();
  });

  it('DFS-057.AC-6 follows only the last 30 seconds of updates', async () => {
    await render();
    const full = { phase: 'FULL_HASH' as const, hashBytesTotal: 6_000_000_000 };
    await send({ ...full, hashedBytes: 0 }, 0);
    await send({ ...full, hashedBytes: 300_000_000 }, 4);
    await send({ ...full, hashedBytes: 1_000_000_000 }, 30);
    await send({ ...full, hashedBytes: 4_000_000_000 }, 60);

    expect(text('.scan-eta')).toBe('about 20 s left');
  });

  it('DFS-057.AC-6 goes back to "estimating…" when no bytes were hashed for 30 seconds', async () => {
    await render();
    const full = { phase: 'FULL_HASH' as const, hashBytesTotal: 2_450_000_000 };
    await send({ ...full, hashedBytes: 0 }, 0);
    await send({ ...full, hashedBytes: 50_000_000 }, 5);
    expect(text('.scan-eta')).toBe('about 4 min left');

    await send({ ...full, hashedBytes: 50_000_000 }, 10);
    await send({ ...full, hashedBytes: 50_000_000 }, 20);
    await send({ ...full, hashedBytes: 50_000_000 }, 36);
    expect(text('.scan-eta')).toBe('estimating…');
  });

  it('DFS-057.AC-7 shows a COMPLETED scan its totals and "Open results" and no phase line, bar or ETA', async () => {
    await render({ status: 'RUNNING', phase: 'FULL_HASH' });
    expect(el.querySelector('.scan-phase-line')).not.toBeNull();

    await send({ status: 'COMPLETED', phase: null, files: 48210, bytes: 330_000_000_000, groupsTotal: 310 });

    expect(text('.scan-counts')).toBe('48210 files · 307.3 GB · 310 duplicate sets');
    expect(text('a.open-results')).toBe('Open results');
    expect(el.querySelector('.scan-phase-line')).toBeNull();
    expect(el.querySelector('mat-progress-bar')).toBeNull();
    expect(el.querySelector('.scan-eta')).toBeNull();
    expect(el.querySelector('.scan-found')).toBeNull();
  });

  it('DFS-057.AC-7 shows a CANCELLED scan no phase line, bar or ETA', async () => {
    await render({ status: 'RUNNING', phase: 'FULL_HASH' });
    expect(el.querySelector('.scan-phase-line')).not.toBeNull();

    await send({ status: 'CANCELLED', phase: 'FULL_HASH' });

    expect(el.querySelector('.scan-phase-line')).toBeNull();
    expect(el.querySelector('mat-progress-bar')).toBeNull();
    expect(el.querySelector('.scan-eta')).toBeNull();
  });

  it('DFS-057.AC-7 shows a FAILED scan its message and no phase line, bar or ETA', async () => {
    await render({ status: 'RUNNING', phase: 'FULL_HASH' });
    expect(el.querySelector('.scan-phase-line')).not.toBeNull();

    await send({ status: 'FAILED', phase: 'FULL_HASH', failureMessage: 'Disk not readable' });

    expect(text('.scan-failure')).toBe('Disk not readable');
    expect(el.querySelector('.scan-phase-line')).toBeNull();
    expect(el.querySelector('mat-progress-bar')).toBeNull();
    expect(el.querySelector('.scan-eta')).toBeNull();
  });
});
