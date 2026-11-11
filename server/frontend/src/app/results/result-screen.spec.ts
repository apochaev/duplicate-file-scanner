import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, withHashLocation } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { MatButtonHarness } from '@angular/material/button/testing';
import { MatButtonToggleGroupHarness } from '@angular/material/button-toggle/testing';
import { MatPaginatorHarness } from '@angular/material/paginator/testing';
import { MatProgressBarHarness } from '@angular/material/progress-bar/testing';
import { MatTabGroupHarness } from '@angular/material/tabs/testing';
import { FolderNode, ScanEntry, SetPage } from '../api/models';
import { provideStubSurfacing, settle } from '../../testing/stub-surfacing';
import { CLIPBOARD } from './clipboard';
import { DENSITY_KEY, DENSITY_STORAGE } from './result-density';
import { ResultScreen } from './result-screen';
import {
  NOT_COMPLETED,
  UNKNOWN_SCAN,
  duplicateSet,
  expectFolders,
  expectPair,
  expectPairs,
  expectScan,
  expectSets,
  expectSummaries,
  folder,
  pair,
  pairPage,
  refuse,
  scanEntry,
  setPage,
  side,
  summary,
  summaryPage,
} from './result-test-data';

describe('ResultScreen', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  let loader: HarnessLoader;

  /** The fake local storage behind `DENSITY_STORAGE`; a test fills it before opening the screen. */
  const store = new Map<string, string>();
  /** The fake clipboard behind `CLIPBOARD`: every write resolves at once. */
  const written: string[] = [];

  const photos = folder('C:\\Photos', { bytes: 12884901888, files: 3210, hasChildren: true });
  const backup = folder('D:\\Backup', { bytes: 900, files: 2 });
  const completed = scanEntry('s-1', { status: 'COMPLETED' }, ['C:\\Photos', 'D:\\Backup']);

  // The ticket's background scan: root C:\Archive, 76214 files, 508 900 000 000 bytes.
  const archiveScan: ScanEntry = {
    ...scanEntry('s-1', { files: 76214, bytes: 508900000000 }, ['C:\\Archive']),
    startedAt: '2026-11-07T14:02:00Z',
  };
  const archive = folder('C:\\Archive', { bytes: 508900000000, files: 76214, hasChildren: true, duplicateBytes: 127100000000 });
  const backups = folder('C:\\Archive\\Backups', { bytes: 76235669504, hasChildren: true, duplicateBytes: 40802189312 });
  const downloads = folder('C:\\Archive\\Downloads', { bytes: 1610612736, duplicateBytes: 1610612736 });
  const oldDrive = folder('C:\\Archive\\Backups\\old-drive', { bytes: 3221225472, duplicateBytes: 1610612736 });
  const ubuntu = duplicateSet(
    [
      ['C:\\Archive\\Downloads\\ubuntu.iso'],
      ['C:\\Archive\\Backups\\MacBook\\Users\\me\\Downloads\\ubuntu.iso'],
      ['C:\\Archive\\Backups\\old-drive\\isos\\linux\\ubuntu.iso'],
    ],
    1610612736,
    'D1',
  );
  const archiveSets = setPage([ubuntu], {
    totalItems: 8412,
    totalFiles: 21930,
    totalBytes: 178100000000,
    totalReclaimableBytes: 127100000000,
  });
  /** What the server answers once a folder filters: totals of the filtered list, which the header must not show. */
  const filteredSets = setPage([ubuntu], { totalItems: 1, totalFiles: 3, totalBytes: 4800, totalReclaimableBytes: 3200 });

  beforeEach(async () => {
    store.clear();
    written.length = 0;
    await TestBed.configureTestingModule({
      imports: [ResultScreen],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'scans/:id', component: ResultScreen }], withHashLocation()),
        provideStubSurfacing(),
        {
          provide: CLIPBOARD,
          useValue: {
            writeText: (text: string) => {
              written.push(text);
              return Promise.resolve();
            },
          },
        },
        {
          provide: DENSITY_STORAGE,
          useValue: {
            getItem: (key: string) => store.get(key) ?? null,
            setItem: (key: string, value: string) => void store.set(key, value),
          },
        },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  /** Opens `#/scans/<id>` on the screen; its first request, getScan, is pending afterwards. */
  async function open(id = 's-1'): Promise<void> {
    harness = await RouterTestingHarness.create();
    loader = TestbedHarnessEnvironment.loader(harness.fixture);
    await harness.navigateByUrl(`/scans/${id}`, ResultScreen);
    await settle(harness.fixture);
  }

  async function step(): Promise<void> {
    await settle(harness.fixture);
  }

  /** Selects the tab labelled `label`. */
  async function selectTab(label: string): Promise<void> {
    const tabs = await loader.getHarness(MatTabGroupHarness);
    await tabs.selectTab({ label });
    await step();
  }

  /**
   * Opens the screen and answers getScan, the roots and the first page of sets. The Sets tab is selected on open
   * (DFS-060 decision 2), and the Overlaps tab requests nothing until it is selected.
   */
  async function openLoaded(
    roots: FolderNode[],
    sets: SetPage,
    scan: ScanEntry = completed,
    rootChildren: FolderNode[] = [],
  ): Promise<void> {
    await open(scan.id);
    expectScan(http, scan.id).flush(scan);
    await step();
    expectFolders(http, scan.id, null).flush(roots);
    expectSets(http, scan.id, null).flush(sets);
    await step();
    // One root: the folder browser opens at its subfolders (DFS-065.AC-3).
    await answerSubfolders(rootChildren);
  }

  /** Answers every pending subfolder request of the folder browser with `children` (DFS-065.AC-3). */
  async function answerSubfolders(children: FolderNode[] = []): Promise<void> {
    http
      .match((r) => r.method === 'GET' && r.url.endsWith('/folders') && r.params.has('parent'))
      .forEach((req) => req.flush(children));
    await step();
  }

  /** Selects the Overlaps tab and answers its two first requests with `summaries` and no pairs. */
  async function openOverlaps(summaries: ReturnType<typeof summaryPage> = summaryPage([]), id = 's-1'): Promise<void> {
    await selectTab('Overlaps');
    expectSummaries(http, id, null).flush(summaries);
    expectPairs(http, id, null).flush(pairPage([]));
    await step();
  }

  function page(): HTMLElement {
    return harness.fixture.nativeElement as HTMLElement;
  }

  function text(selector: string): string[] {
    return Array.from(page().querySelectorAll(selector)).map((el) => (el.textContent ?? '').replace(/\s+/g, ' ').trim());
  }

  /**
   * Selects the folder browser's row named `name`, or "All folders"; a folder with subfolders is then listed, and its
   * request is answered with `children` (DFS-065.AC-3, AC-5).
   */
  async function selectFolder(name: string, children: FolderNode[] = []): Promise<void> {
    if (name === 'All folders') {
      page().querySelector<HTMLButtonElement>('button.all-folders')!.click();
    } else {
      const button = Array.from(page().querySelectorAll<HTMLButtonElement>('button.folder-select')).find(
        (candidate) => (candidate.querySelector('.folder-name')?.textContent ?? '').trim() === name,
      );
      if (!button) throw new Error(`no folder row named ${name}`);
      button.click();
    }
    await step();
    await answerSubfolders(children);
  }

  /** Opens the Archive scan, drills down to old-drive and selects it; its filtered sets are answered. */
  async function openWithOldDriveSelected(): Promise<void> {
    await openLoaded([archive], archiveSets, archiveScan, [backups, downloads]);
    await selectFolder('Backups', [oldDrive]);
    expectSets(http, 's-1', 'C:\\Archive\\Backups').flush(filteredSets);
    await step();
    await selectFolder('old-drive');
    expectSets(http, 's-1', 'C:\\Archive\\Backups\\old-drive').flush(filteredSets);
    await step();
  }

  function expectNoResultView(): void {
    expect(page().querySelector('app-folder-browser')).toBeNull();
    expect(page().querySelector('mat-tab-group')).toBeNull();
  }

  function widths(selector: string): number[] {
    return Array.from(page().querySelectorAll<HTMLElement>(selector)).map((el) => parseFloat(el.style.width));
  }

  const oneSet = setPage([duplicateSet([['C:\\Photos\\a.jpg'], ['D:\\Backup\\a.jpg']])], { totalItems: 3, totalReclaimableBytes: 2097152 });

  it('DFS-060.AC-1 selects the Sets tab on open, with Overlaps second, and loads Overlaps only when selected', async () => {
    await openLoaded([photos, backup], oneSet);

    const tabs = await loader.getHarness(MatTabGroupHarness);
    const all = await tabs.getTabs();
    expect(await Promise.all(all.map((tab) => tab.getLabel()))).toEqual(['Sets', 'Overlaps']);
    expect(await all[0].isSelected()).toBe(true);
    expect(page().querySelector('app-sets-tab')).not.toBeNull();
    expect(page().querySelectorAll('tbody.duplicate-set')).toHaveLength(1);
    expect(page().querySelector('app-overlaps-tab')).toBeNull();
    http.expectNone((r) => r.url.endsWith('/summaries') || r.url.endsWith('/pairs'));
  });

  it('DFS-060.AC-1 shows the folder summary and the Folders table once Overlaps is selected', async () => {
    await openLoaded([photos, backup], oneSet);

    await openOverlaps(summaryPage([summary('C:\\Photos')]));

    const tabs = await loader.getHarness(MatTabGroupHarness);
    expect(await (await tabs.getSelectedTab()).getLabel()).toBe('Overlaps');
    expect(page().querySelector('app-overlaps-tab')).not.toBeNull();
    expect(text('.summaries-table')[0]).toContain('C:\\Photos');
    expect(text('.overlap-note')).toHaveLength(1);
  });

  it('DFS-060.AC-2 shows the scan folders, the start time and the four figures', async () => {
    await openLoaded([archive], archiveSets, archiveScan);

    expect(text('.result-locations li')).toEqual(['C:\\Archive']);
    const started = page().querySelector('.result-started time');
    expect(started?.getAttribute('datetime')).toBe('2026-11-07T14:02:00Z');
    expect(started?.textContent?.trim()).toMatch(/^2026-11-0[678] \d{2}:\d{2}$/);
    expect(text('.result-figures .figure-label')).toEqual(['Reclaimable', 'Duplicate sets', 'Duplicate files', 'Scanned']);
    expect(text('.result-figures .figure-value')).toEqual(['118.4 GB', '8412', '21930', '474.0 GB · 76214 files']);
  });

  it('DFS-060.AC-2 keeps the figures of the whole scan when a folder filters the sets', async () => {
    await openLoaded([archive], archiveSets, archiveScan, [backups, downloads]);

    await selectFolder('Backups');
    expectSets(http, 's-1', 'C:\\Archive\\Backups').flush(filteredSets);
    await step();

    expect(text('.result-figures .figure-value')).toEqual(['118.4 GB', '8412', '21930', '474.0 GB · 76214 files']);
    expect(text('.space-legend li')).toEqual([
      'Reclaimable · 118.4 GB',
      'Last copies, always kept · 47.5 GB',
      'Unique · 308.1 GB',
    ]);
  });

  it('DFS-060.AC-3 sizes the header bar as reclaimable, last copies kept and unique, each with its legend', async () => {
    await openLoaded([archive], archiveSets, archiveScan);

    const segments = Array.from(page().querySelectorAll('.space-bar .space-segment'));
    expect(segments.map((el) => el.classList.contains('segment-reclaimable'))).toEqual([true, false, false]);
    expect(segments[1].classList.contains('segment-kept')).toBe(true);
    expect(segments[2].classList.contains('segment-unique')).toBe(true);
    const [reclaimable, kept, unique] = widths('.space-bar .space-segment');
    expect(reclaimable).toBeCloseTo(25.0, 0);
    expect(kept).toBeCloseTo(10.0, 0);
    expect(unique).toBeCloseTo(65.0, 0);
    expect(text('.space-legend li')).toEqual([
      'Reclaimable · 118.4 GB',
      'Last copies, always kept · 47.5 GB',
      'Unique · 308.1 GB',
    ]);
  });

  it('DFS-060.AC-3 shows three empty segments and 0 B legends for a scan of 0 bytes', async () => {
    const empty = scanEntry('s-1', { files: 0, bytes: 0 }, ['C:\\Archive']);
    await openLoaded([folder('C:\\Archive', { bytes: 0, files: 0 })], setPage([], { totalItems: 0 }), empty);

    expect(widths('.space-bar .space-segment')).toEqual([0, 0, 0]);
    expect(text('.space-legend li')).toEqual([
      'Reclaimable · 0 B',
      'Last copies, always kept · 0 B',
      'Unique · 0 B',
    ]);
    expect(text('.result-figures .figure-value')).toEqual(['0 B', '0', '0', '0 B · 0 files']);
  });

  it('DFS-049.AC-3 shows the scan folders and a "Back to scans" link to #/ in the header', async () => {
    await openLoaded([photos, backup], oneSet);

    expect(text('.result-locations li')).toEqual(['C:\\Photos', 'D:\\Backup']);
    const back = page().querySelector('a.back-to-scans');
    expect(back?.textContent?.trim()).toBe('Back to scans');
    expect(back?.getAttribute('href')).toBe('#/');
  });

  it('DFS-049.AC-3 asks getScan first, with GET /api/scans/<id>', async () => {
    await open('s-1');

    const req = expectScan(http, 's-1');
    expect(req.request.method).toBe('GET');
    http.expectNone((r) => r.url.endsWith('/folders') || r.url.endsWith('/sets'));
    req.flush(completed);
    await step();
    expectFolders(http, 's-1', null).flush([]);
    expectSets(http, 's-1', null).flush(setPage([], { totalItems: 0 }));
    await step();
  });

  it('DFS-049.AC-4 says the scan is not in this server run for a 404 and requests nothing else', async () => {
    await open('0bad');
    refuse(expectScan(http, '0bad'), 404, UNKNOWN_SCAN);
    await step();

    const alert = page().querySelector('.result-error');
    expect(alert?.getAttribute('role')).toBe('alert');
    expect(alert?.textContent?.trim()).toBe('This scan is not in this server run (the server may have restarted)');
    expect(page().querySelector('a.back-to-scans')?.getAttribute('href')).toBe('#/');
    expectNoResultView();
    http.expectNone((r) => r.url.endsWith('/folders') || r.url.endsWith('/sets'));
  });

  it('DFS-049.AC-4 says the scan has not finished for a RUNNING scan and requests nothing else', async () => {
    await open('s-1');
    expectScan(http, 's-1').flush(scanEntry('s-1', { status: 'RUNNING' }));
    await step();

    const alert = page().querySelector('.result-error');
    expect(alert?.getAttribute('role')).toBe('alert');
    expect(alert?.textContent?.trim()).toBe('This scan has not finished yet');
    expect(page().querySelector('a.back-to-scans')?.getAttribute('href')).toBe('#/');
    expectNoResultView();
    http.expectNone((r) => r.url.endsWith('/folders') || r.url.endsWith('/sets'));
  });

  it('DFS-049.AC-4 says the scan has not finished when listFolders answers 409', async () => {
    await open('s-1');
    expectScan(http, 's-1').flush(completed);
    await step();
    refuse(expectFolders(http, 's-1', null), 409, NOT_COMPLETED);
    expectSets(http, 's-1', null).flush(setPage([], { totalItems: 0 }));
    await step();

    const alert = page().querySelector('.result-error');
    expect(alert?.getAttribute('role')).toBe('alert');
    expect(alert?.textContent?.trim()).toBe('This scan has not finished yet');
    expect(page().querySelector('a.back-to-scans')).not.toBeNull();
    expectNoResultView();
  });

  it('DFS-049.AC-4 says the scan has not finished when listSets answers 409', async () => {
    await open('s-1');
    expectScan(http, 's-1').flush(completed);
    await step();
    expectFolders(http, 's-1', null).flush([photos]);
    refuse(expectSets(http, 's-1', null), 409, NOT_COMPLETED);
    await step();

    const alert = page().querySelector('.result-error');
    expect(alert?.getAttribute('role')).toBe('alert');
    expect(alert?.textContent?.trim()).toBe('This scan has not finished yet');
    expect(page().querySelector('a.back-to-scans')).not.toBeNull();
    expectNoResultView();
  });

  it('DFS-060.AC-4, DFS-065.AC-3 lists the roots by duplicate space then name and the subfolders of a selected folder', async () => {
    await openLoaded([photos, backup], oneSet);

    expect(text('button.folder-select .folder-name')).toEqual(['Backup', 'Photos']);
    expect(text('.folder-size')).toEqual(['900 B', '12.0 GB']);

    await selectFolder('Photos', [folder('C:\\Photos\\2024', { bytes: 2048, files: 7 })]);
    expectSets(http, 's-1', 'C:\\Photos').flush(oneSet);
    await step();
    expect(text('button.folder-select .folder-name')).toEqual(['2024']);
    expect(text('.crumb')).toEqual(['All folders', 'Photos']);
  });

  it('DFS-065.AC-2 stacks the folder browser above the tabs, with no left column', async () => {
    await openLoaded([photos, backup], oneSet);

    const layout = page().querySelector('.result-layout')!;
    expect(Array.from(layout.children).map((el) => el.tagName.toLowerCase())).toEqual([
      'app-folder-browser',
      'mat-tab-group',
    ]);
  });

  it('DFS-049.AC-6 opens with "All folders" selected and the sets requested without under', async () => {
    await openLoaded([photos, backup], oneSet);

    expect(page().querySelector('button.all-folders')?.getAttribute('aria-pressed')).toBe('true');
    expect(page().querySelectorAll('tbody.duplicate-set')).toHaveLength(1);
    expect(page().querySelector('.sets-filter')).toBeNull();
  });

  it('DFS-049.AC-6 filters the sets to a selected folder on page 0 and clears the filter on "All folders"', async () => {
    await openLoaded([photos, backup], oneSet);

    await selectFolder('Photos');
    expectSets(http, 's-1', 'C:\\Photos').flush(setPage([], { totalItems: 0 }));
    await step();
    expect(page().querySelector('button.all-folders')?.getAttribute('aria-pressed')).toBe('false');
    expect(text('.crumb')).toEqual(['All folders', 'Photos']);

    await selectFolder('All folders');
    expectSets(http, 's-1', null).flush(oneSet);
    await step();
    expect(page().querySelector('button.all-folders')?.getAttribute('aria-pressed')).toBe('true');
  });

  it('DFS-060.AC-6 marks the copies under the selected folder once its sets have loaded', async () => {
    await openLoaded([photos, backup], oneSet);

    await selectFolder('Backup');
    expectSets(http, 's-1', 'D:\\Backup').flush(oneSet);
    await step();

    const marked = Array.from(page().querySelectorAll('tr.copy-row.in-selected-folder .short-path'));
    expect(marked.map((el) => el.getAttribute('title'))).toEqual(['D:\\Backup\\a.jpg']);
    expect(page().querySelectorAll('.selected-badge')).toHaveLength(1);
  });

  it('DFS-060.AC-6 marks only the old-drive copy "in selected" and shows one row per copy', async () => {
    await openWithOldDriveSelected();

    const rows = Array.from(page().querySelectorAll('tbody.duplicate-set tr.copy-row'));
    expect(rows).toHaveLength(3);
    const badges = rows.map((row) => ({
      path: row.querySelector('.short-path')?.getAttribute('title'),
      marked: row.querySelector('.selected-badge')?.textContent?.trim() ?? null,
    }));
    expect(badges).toEqual([
      { path: 'C:\\Archive\\Downloads\\ubuntu.iso', marked: null },
      { path: 'C:\\Archive\\Backups\\MacBook\\Users\\me\\Downloads\\ubuntu.iso', marked: null },
      { path: 'C:\\Archive\\Backups\\old-drive\\isos\\linux\\ubuntu.iso', marked: 'in selected' },
    ]);
  });

  it('DFS-060.AC-6 shows a copy with two links as one row with a badge, and no "hard link" line', async () => {
    const linked = duplicateSet([
      ['C:\\Archive\\Backup\\GX01.MP4'],
      ['C:\\Archive\\Video\\GX01.MP4', 'C:\\Archive\\Video\\GX01-link.MP4'],
    ]);
    await openLoaded([archive], setPage([linked], { totalItems: 1 }), archiveScan);

    expect(page().querySelectorAll('tr.copy-row')).toHaveLength(2);
    const badge = page().querySelector('.links-badge');
    expect(badge?.textContent?.trim()).toBe('2 links');
    expect(badge?.getAttribute('title')).toBe('C:\\Archive\\Video\\GX01.MP4\nC:\\Archive\\Video\\GX01-link.MP4');
    expect(page().textContent).not.toContain('hard link (same file)');
  });

  it('DFS-060.AC-5 shows a set as a header row and its copy rows, with the reclaimable bar full for the largest', async () => {
    await openLoaded([archive], archiveSets, archiveScan);

    expect(text('tr.set-header .set-name')).toEqual(['ubuntu.iso']);
    expect(text('tr.set-header .set-count')).toEqual(['3 copies']);
    expect(text('tr.set-header .set-size')).toEqual(['1.5 GB each']);
    expect(text('tr.set-header .set-reclaimable-bytes')).toEqual(['3.0 GB']);
    expect(widths('tr.set-header .bar-fill')).toEqual([100]);
    expect(page().querySelectorAll('tr.copy-row')).toHaveLength(3);
    expect(page().querySelectorAll('tr.copy-row app-path-copy')).toHaveLength(3);
  });

  it('DFS-060.AC-7 copies the full path of the row that shows it shortened', async () => {
    const long = 'C:\\Archive\\Backups\\MacBook-2020\\Users\\me\\Pictures\\Iceland 2019\\Raw Exports\\Day 3\\pano-final.tif';
    const set = duplicateSet([[long], ['C:\\Archive\\Downloads\\pano-final.tif']]);
    await openLoaded([archive], setPage([set], { totalItems: 1 }), archiveScan);

    const shown = page().querySelector('tr.copy-row .short-path');
    expect(shown?.textContent).toContain('…');
    expect(shown?.getAttribute('title')).toBe(long);
    const copy = Array.from(page().querySelectorAll<HTMLButtonElement>('button.copy-path-button')).find(
      (button) => button.getAttribute('aria-label') === `Copy path of ${long}`,
    );
    expect(copy).toBeDefined();
    copy?.click();
    await step();

    expect(written).toEqual([long]);
    expect(text('.copy-success')).toEqual(['Copied']);
  });

  it('DFS-060.AC-8 offers "Copies under <folder>" on with a folder selected, sending under, and "All sets" without it', async () => {
    await openWithOldDriveSelected();

    const group = await loader.getHarness(MatButtonToggleGroupHarness.with({ selector: '.sets-filter' }));
    let toggles = await group.getToggles();
    expect(await Promise.all(toggles.map((toggle) => toggle.getText()))).toEqual(['All sets', 'Copies under old-drive']);
    expect(await Promise.all(toggles.map((toggle) => toggle.isChecked()))).toEqual([false, true]);

    await toggles[0].check();
    await step();
    expectSets(http, 's-1', null).flush(archiveSets);
    await step();

    toggles = await (await loader.getHarness(MatButtonToggleGroupHarness.with({ selector: '.sets-filter' }))).getToggles();
    expect(await Promise.all(toggles.map((toggle) => toggle.isChecked()))).toEqual([true, false]);
    expect(page().querySelector('.crumb-current')?.textContent?.trim()).toBe('old-drive');
    expect(page().querySelectorAll('tbody.duplicate-set')).toHaveLength(1);
  });

  it('DFS-060.AC-8 returns to the filtered list when "Copies under <folder>" is chosen again, and pages "All sets"', async () => {
    await openWithOldDriveSelected();
    const group = async () => loader.getHarness(MatButtonToggleGroupHarness.with({ selector: '.sets-filter' }));

    await (await (await group()).getToggles({ text: 'All sets' }))[0].check();
    await step();
    expectSets(http, 's-1', null).flush({ ...archiveSets, totalItems: 120 });
    await step();

    await (await loader.getHarness(MatPaginatorHarness)).setPageSize(25);
    await step();
    expectSets(http, 's-1', null, 0, 25).flush({ ...archiveSets, totalItems: 120, size: 25 });
    await step();

    await (await (await group()).getToggles({ text: 'Copies under old-drive' }))[0].check();
    await step();
    expectSets(http, 's-1', 'C:\\Archive\\Backups\\old-drive', 0, 25).flush(filteredSets);
    await step();
  });

  it('DFS-060.AC-8 requests the next page, then page 0 when another folder is selected', async () => {
    const big = setPage([duplicateSet([['C:\\Photos\\a.jpg'], ['D:\\Backup\\a.jpg']])], {
      totalItems: 120,
      totalReclaimableBytes: 5368709120,
    });
    await openLoaded([photos, backup], big);

    await (await loader.getHarness(MatPaginatorHarness)).goToNextPage();
    await step();
    expectSets(http, 's-1', null, 1, 50).flush({ ...big, page: 1 });
    await step();

    await selectFolder('Photos');
    expectSets(http, 's-1', 'C:\\Photos', 0, 50).flush({ ...big, totalItems: 60 });
    await step();
    const paginator = await loader.getHarness(MatPaginatorHarness);
    expect(await paginator.getRangeLabel()).toBe('1 – 50 of 60');
  });

  it('DFS-060.AC-8 requests a chosen page size and the next page of it', async () => {
    const big = setPage([duplicateSet([['C:\\Photos\\a.jpg'], ['D:\\Backup\\a.jpg']])], { totalItems: 120 });
    await openLoaded([photos, backup], big);
    const paginator = await loader.getHarness(MatPaginatorHarness);
    expect(await paginator.getPageSize()).toBe(50);

    await paginator.setPageSize(100);
    await step();
    expectSets(http, 's-1', null, 0, 100).flush(big);
    await step();

    await (await loader.getHarness(MatPaginatorHarness)).goToNextPage();
    await step();
    expectSets(http, 's-1', null, 1, 100).flush({ ...big, page: 1, size: 100 });
    await step();
  });

  it('DFS-060.AC-9 starts Compact, applies Comfortable when chosen and stores the choice', async () => {
    await openLoaded([archive], archiveSets, archiveScan);
    const group = () => loader.getHarness(MatButtonToggleGroupHarness.with({ selector: '.density-toggle' }));
    let toggles = await (await group()).getToggles();
    expect(await Promise.all(toggles.map((toggle) => toggle.getText()))).toEqual(['Compact', 'Comfortable']);
    expect(await Promise.all(toggles.map((toggle) => toggle.isChecked()))).toEqual([true, false]);
    expect(page().querySelector('section.sets-tab')?.classList.contains('density-compact')).toBe(true);
    expect(store.size).toBe(0);

    await toggles[1].check();
    await step();

    expect(page().querySelector('section.sets-tab')?.classList.contains('density-comfortable')).toBe(true);
    expect(page().querySelector('section.sets-tab')?.classList.contains('density-compact')).toBe(false);
    expect(store.get(DENSITY_KEY)).toBe('comfortable');
    toggles = await (await group()).getToggles();
    expect(await Promise.all(toggles.map((toggle) => toggle.isChecked()))).toEqual([false, true]);
  });

  it('DFS-060.AC-9 restores the stored Comfortable density when the screen is opened', async () => {
    store.set(DENSITY_KEY, 'comfortable');

    await openLoaded([archive], archiveSets, archiveScan);

    const toggles = await (await loader.getHarness(MatButtonToggleGroupHarness.with({ selector: '.density-toggle' }))).getToggles();
    expect(await Promise.all(toggles.map((toggle) => toggle.isChecked()))).toEqual([false, true]);
    expect(page().querySelector('section.sets-tab')?.classList.contains('density-comfortable')).toBe(true);
  });

  it('DFS-060.AC-10 says there are no duplicates under "All folders" and then under a folder', async () => {
    await openLoaded([photos, backup], setPage([], { totalItems: 0 }));

    expect(text('.sets-empty')).toEqual(['This scan found no duplicates']);
    expect(page().querySelector('mat-paginator')).toBeNull();

    await selectFolder('Backup');
    expectSets(http, 's-1', 'D:\\Backup').flush(setPage([], { totalItems: 0 }));
    await step();
    expect(text('.sets-empty')).toEqual(['No duplicates under this folder']);
    expect(page().querySelector('mat-paginator')).toBeNull();
  });

  it('DFS-060.AC-10 shows a progress bar while the sets load and the server message when they fail', async () => {
    await open('s-1');
    expectScan(http, 's-1').flush(completed);
    await step();
    expectFolders(http, 's-1', null).flush([photos]);
    const sets = expectSets(http, 's-1', null);
    await step();

    const bars = await loader.getAllHarnesses(MatProgressBarHarness.with({ selector: '[aria-label="Loading duplicate sets"]' }));
    expect(bars).toHaveLength(1);

    refuse(sets, 400, { code: 'RELATIVE_PATH', message: 'The path is not absolute' });
    await step();
    const alert = page().querySelector('.sets-error');
    expect(alert?.getAttribute('role')).toBe('alert');
    expect(alert?.textContent?.trim()).toBe('The path is not absolute');
    expect(
      await loader.getAllHarnesses(MatProgressBarHarness.with({ selector: '[aria-label="Loading duplicate sets"]' })),
    ).toHaveLength(0);
    // The single root's subfolders, requested by the folder browser (DFS-065.AC-3).
    await answerSubfolders();
  });

  it('DFS-060.AC-10 shows the server message when the roots cannot be loaded', async () => {
    await open('s-1');
    expectScan(http, 's-1').flush(completed);
    await step();
    refuse(expectFolders(http, 's-1', null), 400, { code: 'RELATIVE_PATH', message: 'The path is not absolute' });
    expectSets(http, 's-1', null).flush(setPage([], { totalItems: 0 }));
    await step();

    const alert = page().querySelector('.result-error');
    expect(alert?.getAttribute('role')).toBe('alert');
    expect(alert?.textContent?.trim()).toBe('The path is not absolute');
    expectNoResultView();
  });

  it('DFS-050.AC-5 pins a folder from the tree, replaces the pin, and unpins it, with no selection change', async () => {
    await openLoaded([photos, backup], oneSet);
    await openOverlaps();
    expect(page().querySelectorAll('button.folder-pin')).toHaveLength(2);
    expect(page().querySelector('.pinned-folder')).toBeNull();

    const pinBackup = await loader.getHarness(MatButtonHarness.with({ selector: '[aria-label="Pin for comparison: Backup"]' }));
    await pinBackup.click();
    await step();
    expect(text('.pinned-folder')).toEqual(['Pinned: D:\\Backup']);
    expect(text('.comparison-hint')).toEqual(['Select another folder to compare with the pinned one']);

    const pinPhotos = await loader.getHarness(MatButtonHarness.with({ selector: '[aria-label="Pin for comparison: Photos"]' }));
    await pinPhotos.click();
    await step();
    expect(text('.pinned-folder')).toEqual(['Pinned: C:\\Photos']);

    await (await loader.getHarness(MatButtonHarness.with({ text: 'Unpin' }))).click();
    await step();
    expect(page().querySelector('.pinned-folder')).toBeNull();
    expect(page().querySelector('section.comparison')).toBeNull();
    expect(page().querySelector('button.all-folders')?.getAttribute('aria-pressed')).toBe('true');
  });

  it('DFS-050.AC-6 compares the pinned folder with a folder selected in the tree', async () => {
    await openLoaded([photos, backup], oneSet);
    await openOverlaps();
    await (await loader.getHarness(MatButtonHarness.with({ selector: '[aria-label="Pin for comparison: Backup"]' }))).click();
    await step();

    await selectFolder('Photos');
    expectSummaries(http, 's-1', 'C:\\Photos').flush(summaryPage([]));
    expectPairs(http, 's-1', 'C:\\Photos').flush(pairPage([]));
    expectPair(http, 's-1', 'D:\\Backup', 'C:\\Photos').flush(
      pair(
        side('C:\\Photos', { filesWithCopy: 340, totalFiles: 3210, bytesWithCopy: 1288490188, full: false }),
        side('D:\\Backup', { filesWithCopy: 2, totalFiles: 2, bytesWithCopy: 900, full: true }),
      ),
    );
    expectSets(http, 's-1', 'C:\\Photos').flush(oneSet);
    await step();

    expect(text('.comparison-pair .side-text')).toEqual([
      'C:\\Photos: 340 / 3210 files · 1.2 GB',
      'D:\\Backup: 2 / 2 files · 900 B',
    ]);
  });
});
