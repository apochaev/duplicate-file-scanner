import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, withHashLocation } from '@angular/router';
import { HarnessLoader, TestKey } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { MatButtonHarness } from '@angular/material/button/testing';
import { MatButtonToggleHarness } from '@angular/material/button-toggle/testing';
import { MatCheckboxHarness } from '@angular/material/checkbox/testing';
import { MatChipGridHarness, MatChipInputHarness } from '@angular/material/chips/testing';
import { MatExpansionPanelHarness } from '@angular/material/expansion/testing';
import { MatInputHarness } from '@angular/material/input/testing';
import { MatProgressBarHarness } from '@angular/material/progress-bar/testing';
import { ScanEntry, ScanProgress } from '../api/models';
import { EVENT_SOURCE } from './scan-events.service';
import { ScanScreen } from './scan-screen';

/**
 * A stand-in for the browser's EventSource (jsdom has none), driven by the test one event at a time
 * (DFS-041 decision 5). Its readyState follows the real one: 0 connecting, 1 open, 2 closed.
 */
class FakeEventSource {
  static opened: FakeEventSource[] = [];
  readyState = 0;
  onerror: ((event: Event) => void) | null = null;
  private readonly listeners = new Map<string, ((event: MessageEvent) => void)[]>();

  constructor(readonly url: string) {
    FakeEventSource.opened.push(this);
  }

  addEventListener(type: string, listener: (event: MessageEvent) => void): void {
    this.listeners.set(type, [...(this.listeners.get(type) ?? []), listener]);
  }

  close(): void {
    this.readyState = 2;
  }

  get closed(): boolean {
    return this.readyState === 2;
  }

  /** Delivers one `progress` event, as the server's stream does. */
  send(update: Partial<ScanProgress>): void {
    this.readyState = 1;
    const event = new MessageEvent('progress', { data: JSON.stringify(progress(update)) });
    for (const listener of this.listeners.get('progress') ?? []) listener(event);
  }

  /** Reports an error; with `closedForGood` the browser has given up, otherwise it is reconnecting. */
  fail(closedForGood: boolean): void {
    this.readyState = closedForGood ? 2 : 0;
    this.onerror?.(new Event('error'));
  }
}

function progress(update: Partial<ScanProgress> = {}): ScanProgress {
  return {
    status: 'RUNNING',
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

function entry(
  id: string,
  update: Partial<ScanProgress>,
  locations = ['C:/a'],
  startedAt = '2026-11-01T10:00:00Z',
): ScanEntry {
  return { id, locations, startedAt, finishedAt: null, progress: progress(update) };
}

describe('ScanScreen', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<ScanScreen>;
  let loader: HarnessLoader;

  beforeEach(async () => {
    FakeEventSource.opened = [];
    await TestBed.configureTestingModule({
      imports: [ScanScreen],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        // A COMPLETED row holds an "Open results" link (DFS-049): it needs a router.
        provideRouter([], withHashLocation()),
        { provide: EVENT_SOURCE, useValue: (url: string) => new FakeEventSource(url) as unknown as EventSource },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  /** Lets the generated client's Promises and the signals settle, then renders (DFS-038's Promise timing). */
  async function settle(): Promise<void> {
    await new Promise((resolve) => setTimeout(resolve));
    await fixture.whenStable();
    fixture.detectChanges();
  }

  /** Renders the screen and answers its GET /api/scans with the given entries. */
  async function render(scans: ScanEntry[] = []): Promise<void> {
    fixture = TestBed.createComponent(ScanScreen);
    loader = TestbedHarnessEnvironment.loader(fixture);
    fixture.detectChanges();
    const list = http.expectOne('/api/scans');
    expect(list.request.method).toBe('GET');
    list.flush(scans);
    await settle();
  }

  function page(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function rows(): HTMLElement[] {
    return Array.from(page().querySelectorAll<HTMLElement>('.scan-row'));
  }

  async function addFolder(path: string): Promise<void> {
    const input = await loader.getHarness(MatChipInputHarness);
    await input.setValue(path);
    await input.sendSeparatorKey(TestKey.ENTER);
    await settle();
  }

  async function chipTexts(): Promise<string[]> {
    const grid = await loader.getHarness(MatChipGridHarness);
    return Promise.all((await grid.getRows()).map((row) => row.getText()));
  }

  async function startButton(): Promise<MatButtonHarness> {
    return loader.getHarness(MatButtonHarness.with({ text: 'Start' }));
  }

  async function pressStart(): Promise<void> {
    await (await startButton()).click();
    await settle();
  }

  async function openSettings(): Promise<void> {
    await (await loader.getHarness(MatExpansionPanelHarness)).expand();
    await settle();
  }

  async function setMinSize(value: string, unit: string): Promise<void> {
    await openSettings();
    const size = await loader.getHarness(MatInputHarness.with({ selector: '.min-size' }));
    await size.setValue(value);
    await (await loader.getHarness(MatButtonToggleHarness.with({ text: unit }))).check();
    await settle();
  }

  it('DFS-041.AC-1 adds folders as chips, skips a repeated path and removes a chip', async () => {
    await render();

    await addFolder('C:/photos');
    await addFolder('D:/backup/photos');
    expect(await chipTexts()).toEqual(['C:/photos', 'D:/backup/photos']);
    expect(await (await loader.getHarness(MatChipInputHarness)).getValue()).toBe('');

    await addFolder('C:/photos');
    expect(await chipTexts()).toEqual(['C:/photos', 'D:/backup/photos']);

    const grid = await loader.getHarness(MatChipGridHarness);
    await (await grid.getRows({ text: 'C:/photos' }))[0].remove();
    await settle();
    expect(await chipTexts()).toEqual(['D:/backup/photos']);
  });

  it('DFS-041.AC-1 disables Start until a folder is added', async () => {
    await render();
    expect(await (await startButton()).isDisabled()).toBe(true);

    await addFolder('C:/photos');
    expect(await (await startButton()).isDisabled()).toBe(false);
  });

  it("DFS-041.AC-2 starts a scan of the chips' folders with the server's defaults and lists it first", async () => {
    await render([entry('s-0', { status: 'COMPLETED' }, ['C:/old'])]);
    await addFolder('C:/photos');
    await addFolder('D:/backup/photos');

    await pressStart();
    const start = http.expectOne('/api/scans');
    expect(start.request.method).toBe('POST');
    expect(start.request.body).toEqual({ locations: ['C:/photos', 'D:/backup/photos'] });
    start.flush(entry('s-1', { status: 'QUEUED' }, ['C:/photos', 'D:/backup/photos']), {
      status: 201,
      statusText: 'Created',
    });
    await settle();

    expect(await chipTexts()).toEqual([]);
    expect(rows()).toHaveLength(2);
    expect(rows()[0].textContent).toContain('C:/photos');
    expect(rows()[0].textContent).toContain('D:/backup/photos');
    expect(rows()[0].querySelector('.scan-status')?.textContent?.trim()).toBe('Queued');
    expect(rows()[1].textContent).toContain('C:/old');
  });

  it.each([
    ['2', 'MB', 2097152],
    ['3', 'KB', 3072],
    ['5', 'bytes', 5],
  ])('DFS-041.AC-3 sends a minimum size of %s %s as %i bytes', async (value, unit, bytes) => {
    await render();
    await addFolder('C:/photos');
    await setMinSize(value, unit);

    await pressStart();
    const start = http.expectOne('/api/scans');
    expect(start.request.body).toEqual({ locations: ['C:/photos'], minSize: bytes });
    start.flush(entry('s-1', { status: 'QUEUED' }), { status: 201, statusText: 'Created' });
    await settle();
  });

  it('DFS-041.AC-3 starts with Settings closed and can turn the default exclusion off', async () => {
    await render();
    expect(await (await loader.getHarness(MatExpansionPanelHarness)).isExpanded()).toBe(false);

    await addFolder('C:/photos');
    await openSettings();
    const excludes = await loader.getHarness(MatCheckboxHarness);
    expect(await excludes.getLabelText()).toContain('Skip .DS_Store and ._* files');
    expect(await excludes.isChecked()).toBe(true);
    await excludes.uncheck();
    await settle();

    await pressStart();
    const start = http.expectOne('/api/scans');
    expect(start.request.body).toEqual({ locations: ['C:/photos'], defaultExcludes: false });
    start.flush(entry('s-1', { status: 'QUEUED' }), { status: 201, statusText: 'Created' });
    await settle();
  });

  it('DFS-041.AC-3 refuses a minimum size below one byte', async () => {
    await render();
    await addFolder('C:/photos');
    await setMinSize('0', 'bytes');

    expect(page().textContent).toContain('At least 1 byte');
    expect(await (await startButton()).isDisabled()).toBe(true);
    http.expectNone('/api/scans');
  });

  it("DFS-041.AC-4 shows a refused folder's message next to the field and keeps the chips", async () => {
    await render();
    await addFolder('photos');

    await pressStart();
    http
      .expectOne('/api/scans')
      .flush(
        { code: 'RELATIVE_LOCATION', message: 'Not an absolute path: photos' },
        { status: 400, statusText: 'Bad Request' },
      );
    await settle();

    expect(page().querySelector('.form-error')?.textContent).toContain('Not an absolute path: photos');
    expect(await chipTexts()).toEqual(['photos']);
    expect(rows()).toHaveLength(0);
  });

  it('DFS-041.AC-4 reports a busy server', async () => {
    await render();
    await addFolder('C:/photos');

    await pressStart();
    http
      .expectOne('/api/scans')
      .flush(
        { code: 'EXECUTOR_REJECTED', message: 'Task rejected' },
        { status: 503, statusText: 'Service Unavailable' },
      );
    await settle();

    expect(page().querySelector('.form-error')?.textContent).toContain('The server is busy, try again');
    expect(rows()).toHaveLength(0);
  });

  it("DFS-041.AC-5 DFS-041.AC-8 lists this server run's scans in the server's order with their outcomes", async () => {
    await render([
      entry('s-3', { status: 'COMPLETED', files: 1200, bytes: 5242880, groupsTotal: 7 }, ['C:/a'], '2026-11-01T10:30:00Z'),
      entry(
        's-2',
        { status: 'FAILED', failureMessage: 'Disk not readable' },
        ['C:/b', 'D:/c'],
        '2026-11-01T10:20:00Z',
      ),
      entry('s-1', { status: 'CANCELLED', files: 10, bytes: 100 }, ['C:/d'], '2026-11-01T10:10:00Z'),
    ]);

    const [s3, s2, s1] = rows();
    expect(rows()).toHaveLength(3);
    expect(s3.textContent).toContain('C:/a');
    expect(s2.textContent).toContain('C:/b');
    expect(s2.textContent).toContain('D:/c');
    expect(s1.textContent).toContain('C:/d');
    expect(s3.querySelector('time')?.getAttribute('datetime')).toBe('2026-11-01T10:30:00Z');
    expect(s2.querySelector('time')?.getAttribute('datetime')).toBe('2026-11-01T10:20:00Z');
    expect(s1.querySelector('time')?.getAttribute('datetime')).toBe('2026-11-01T10:10:00Z');

    expect(s3.querySelector('.scan-status')?.textContent?.trim()).toBe('Completed');
    expect(s3.textContent).toContain('1200 files');
    expect(s3.textContent).toContain('5.0 MB');
    expect(s3.textContent).toContain('7 duplicate sets');
    expect(s2.querySelector('.scan-status')?.textContent?.trim()).toBe('Failed');
    expect(s2.textContent).toContain('Disk not readable');
    expect(s1.querySelector('.scan-status')?.textContent?.trim()).toBe('Cancelled');

    expect(FakeEventSource.opened).toHaveLength(0);
    expect(await loader.getAllHarnesses(MatButtonHarness.with({ text: 'Cancel' }))).toHaveLength(0);
  });

  it('DFS-041.AC-6 shows a running scan\'s live progress until it completes', async () => {
    await render([entry('s-1', { status: 'RUNNING', phase: 'WALK' })]);

    expect(FakeEventSource.opened.map((source) => source.url)).toEqual(['/api/scans/s-1/events']);
    const stream = FakeEventSource.opened[0];

    stream.send({ status: 'RUNNING', phase: 'WALK', directories: 12, files: 340, bytes: 1572864 });
    await settle();
    expect(rows()[0].querySelector('.scan-phase')?.textContent?.trim()).toBe('Finding files');
    expect(rows()[0].textContent).toContain('12 folders');
    expect(rows()[0].textContent).toContain('340 files');
    expect(rows()[0].textContent).toContain('1.5 MB');

    stream.send({
      status: 'RUNNING',
      phase: 'FULL_HASH',
      directories: 12,
      files: 340,
      bytes: 1572864,
      hashedBytes: 262144,
      hashBytesTotal: 1048576,
    });
    await settle();
    expect(rows()[0].querySelector('.scan-phase')?.textContent?.trim()).toBe('Hashing files');
    expect(await (await loader.getHarness(MatProgressBarHarness)).getValue()).toBe(25);

    stream.send({ status: 'COMPLETED', files: 340, bytes: 1572864, groupsTotal: 3 });
    await settle();
    expect(rows()[0].querySelector('.scan-status')?.textContent?.trim()).toBe('Completed');
    expect(rows()[0].textContent).toContain('3 duplicate sets');
    expect(stream.closed).toBe(true);
    expect(FakeEventSource.opened).toHaveLength(1);
  });

  it('DFS-041.AC-7 cancels a running scan once and follows it to Cancelled', async () => {
    await render([entry('s-1', { status: 'RUNNING', phase: 'WALK' })]);
    const stream = FakeEventSource.opened[0];
    const cancel = await loader.getHarness(MatButtonHarness.with({ text: 'Cancel' }));

    await cancel.click();
    await settle();
    const request = http.expectOne('/api/scans/s-1/cancel');
    expect(request.request.method).toBe('POST');
    expect(await cancel.isDisabled()).toBe(true);
    request.flush(entry('s-1', { status: 'CANCELLING', phase: 'WALK' }));
    await settle();
    expect(rows()[0].querySelector('.scan-status')?.textContent?.trim()).toBe('Cancelling');

    stream.send({ status: 'CANCELLED' });
    await settle();
    expect(rows()[0].querySelector('.scan-status')?.textContent?.trim()).toBe('Cancelled');
    expect(stream.closed).toBe(true);
    expect(await loader.getAllHarnesses(MatButtonHarness.with({ text: 'Cancel' }))).toHaveLength(0);
  });

  it('DFS-041.AC-9 keeps the last values and reports a stream the browser gave up on', async () => {
    await render([entry('s-1', { status: 'RUNNING', phase: 'WALK' })]);
    const stream = FakeEventSource.opened[0];
    stream.send({ status: 'RUNNING', phase: 'WALK', files: 340 });
    await settle();

    stream.fail(true);
    await settle();

    expect(rows()[0].textContent).toContain('340 files');
    expect(rows()[0].textContent).toContain('Live progress lost; reload the page');
  });

  it('DFS-041.AC-9 carries on after the browser reconnects a stream', async () => {
    await render([entry('s-1', { status: 'RUNNING', phase: 'WALK' })]);
    const stream = FakeEventSource.opened[0];

    stream.fail(false);
    await settle();
    stream.send({ status: 'RUNNING', phase: 'WALK', files: 500 });
    await settle();

    expect(rows()[0].textContent).toContain('500 files');
    expect(rows()[0].textContent).not.toContain('Live progress lost');
  });
});
