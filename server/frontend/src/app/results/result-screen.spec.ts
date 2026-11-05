import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, withHashLocation } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { MatButtonHarness } from '@angular/material/button/testing';
import { MatPaginatorHarness } from '@angular/material/paginator/testing';
import { MatProgressBarHarness } from '@angular/material/progress-bar/testing';
import { MatTreeHarness } from '@angular/material/tree/testing';
import { FolderNode, ScanEntry, SetPage } from '../api/models';
import { provideStubSurfacing, settle } from '../../testing/stub-surfacing';
import { CLIPBOARD } from './clipboard';
import { ResultScreen } from './result-screen';
import {
  NOT_COMPLETED,
  UNKNOWN_SCAN,
  duplicateSet,
  expectFolders,
  expectScan,
  expectSets,
  folder,
  refuse,
  scanEntry,
  setPage,
} from './result-test-data';

describe('ResultScreen', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  let loader: HarnessLoader;

  const photos = folder('C:\\Photos', { bytes: 12884901888, files: 3210, hasChildren: true });
  const backup = folder('D:\\Backup', { bytes: 900, files: 2 });
  const completed = scanEntry('s-1', { status: 'COMPLETED' }, ['C:\\Photos', 'D:\\Backup']);

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ResultScreen],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'scans/:id', component: ResultScreen }], withHashLocation()),
        provideStubSurfacing(),
        { provide: CLIPBOARD, useValue: null },
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

  /** Opens the screen and answers getScan, the roots and the first page of sets. */
  async function openLoaded(roots: FolderNode[], sets: SetPage, scan: ScanEntry = completed): Promise<void> {
    await open(scan.id);
    expectScan(http, scan.id).flush(scan);
    await step();
    expectFolders(http, scan.id, null).flush(roots);
    expectSets(http, scan.id, null).flush(sets);
    await step();
  }

  function page(): HTMLElement {
    return harness.fixture.nativeElement as HTMLElement;
  }

  function text(selector: string): string[] {
    return Array.from(page().querySelectorAll(selector)).map((el) => (el.textContent ?? '').replace(/\s+/g, ' ').trim());
  }

  async function selectFolder(label: RegExp | string): Promise<void> {
    const button = await loader.getHarness(MatButtonHarness.with({ text: label }));
    await button.click();
    await step();
  }

  function expectNoResultView(): void {
    expect(page().querySelector('app-folder-tree')).toBeNull();
    expect(page().querySelector('mat-tab-group')).toBeNull();
  }

  const oneSet = setPage([duplicateSet([['C:\\Photos\\a.jpg'], ['D:\\Backup\\a.jpg']])], { totalItems: 3, totalReclaimableBytes: 2097152 });

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

  it('DFS-049.AC-5 shows the roots in the API order and loads the children of Photos only when it is expanded', async () => {
    await openLoaded([photos, backup], oneSet);

    expect(text('button.folder-select')).toEqual(['Photos · 12.0 GB · 3210 files', 'Backup · 900 B · 2 files']);
    const nodes = await (await loader.getHarness(MatTreeHarness)).getNodes();
    expect(await nodes[0].isExpandable()).toBe(true);
    expect(await nodes[1].isExpandable()).toBe(false);

    await nodes[0].expand();
    await step();
    expectFolders(http, 's-1', 'C:\\Photos').flush([folder('C:\\Photos\\2024', { bytes: 2048, files: 7 })]);
    await step();
    expect(text('button.folder-select')).toEqual([
      'Photos · 12.0 GB · 3210 files',
      '2024 · 2.0 KB · 7 files',
      'Backup · 900 B · 2 files',
    ]);
  });

  it('DFS-049.AC-6 opens with "All folders" selected and the sets requested without under', async () => {
    await openLoaded([photos, backup], oneSet);

    expect(page().querySelector('button.all-folders')?.getAttribute('aria-pressed')).toBe('true');
    expect(text('.sets-summary')).toEqual(['3 duplicate sets · 2.0 MB reclaimable']);
  });

  it('DFS-049.AC-6 filters the sets to a selected folder on page 0 and clears the filter on "All folders"', async () => {
    await openLoaded([photos, backup], oneSet);

    await selectFolder(/^Photos/);
    expectSets(http, 's-1', 'C:\\Photos').flush(setPage([], { totalItems: 0 }));
    await step();
    expect(page().querySelector('button.all-folders')?.getAttribute('aria-pressed')).toBe('false');
    expect(page().querySelector('button.folder-select')?.getAttribute('aria-pressed')).toBe('true');

    await selectFolder('All folders');
    expectSets(http, 's-1', null).flush(oneSet);
    await step();
    expect(page().querySelector('button.all-folders')?.getAttribute('aria-pressed')).toBe('true');
  });

  it('DFS-049.AC-8 marks the copies under the selected folder once its sets have loaded', async () => {
    await openLoaded([photos, backup], oneSet);

    await selectFolder(/^Backup/);
    expectSets(http, 's-1', 'D:\\Backup').flush(oneSet);
    await step();

    expect(text('.file-link.in-selected-folder .file-path')).toEqual(['D:\\Backup\\a.jpg']);
  });

  it('DFS-049.AC-9 shows the summary and requests the next page and a chosen page size', async () => {
    const big = setPage([duplicateSet([['C:\\Photos\\a.jpg'], ['D:\\Backup\\a.jpg']])], {
      totalItems: 120,
      totalReclaimableBytes: 5368709120,
    });
    await openLoaded([photos, backup], big);

    expect(text('.sets-summary')).toEqual(['120 duplicate sets · 5.0 GB reclaimable']);
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

  it('DFS-049.AC-9 requests the next page, then page 0 when another folder is selected', async () => {
    const big = setPage([duplicateSet([['C:\\Photos\\a.jpg'], ['D:\\Backup\\a.jpg']])], {
      totalItems: 120,
      totalReclaimableBytes: 5368709120,
    });
    await openLoaded([photos, backup], big);

    await (await loader.getHarness(MatPaginatorHarness)).goToNextPage();
    await step();
    expectSets(http, 's-1', null, 1, 50).flush({ ...big, page: 1 });
    await step();

    await selectFolder(/^Photos/);
    expectSets(http, 's-1', 'C:\\Photos', 0, 50).flush({ ...big, totalItems: 60 });
    await step();
    expect(text('.sets-summary')).toEqual(['60 duplicate sets · 5.0 GB reclaimable']);
  });

  it('DFS-049.AC-10 says there are no duplicates under "All folders" and then under a folder', async () => {
    await openLoaded([photos, backup], setPage([], { totalItems: 0 }));

    expect(text('.sets-empty')).toEqual(['This scan found no duplicates']);
    expect(page().querySelector('mat-paginator')).toBeNull();

    await selectFolder(/^Backup/);
    expectSets(http, 's-1', 'D:\\Backup').flush(setPage([], { totalItems: 0 }));
    await step();
    expect(text('.sets-empty')).toEqual(['No duplicates under this folder']);
    expect(page().querySelector('mat-paginator')).toBeNull();
  });

  it('DFS-049.AC-10 shows a progress bar while the sets load and the server message when they fail', async () => {
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
  });

  it('DFS-049.AC-10 shows the server message when the roots cannot be loaded', async () => {
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
});
