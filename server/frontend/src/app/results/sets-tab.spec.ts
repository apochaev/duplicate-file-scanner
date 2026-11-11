import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { MatButtonToggleGroupHarness } from '@angular/material/button-toggle/testing';
import { PageEvent } from '@angular/material/paginator';
import { MatPaginatorHarness } from '@angular/material/paginator/testing';
import { MatProgressBarHarness } from '@angular/material/progress-bar/testing';
import { SetPage } from '../api/models';
import { provideStubSurfacing, settle } from '../../testing/stub-surfacing';
import { CLIPBOARD } from './clipboard';
import { Density } from './result-density';
import { duplicateSet, setPage } from './result-test-data';
import { PATH_BUDGET, SetsTab, pathBudget } from './sets-tab';

describe('SetsTab', () => {
  let fixture: ComponentFixture<SetsTab>;
  let loader: HarnessLoader;
  let emitted: PageEvent[];
  let filters: boolean[];

  beforeEach(async () => {
    emitted = [];
    filters = [];
    await TestBed.configureTestingModule({
      imports: [SetsTab],
      providers: [provideStubSurfacing(), { provide: CLIPBOARD, useValue: null }],
    }).compileComponents();
  });

  interface Inputs {
    folder?: string | null;
    filtered?: boolean;
    roots?: string[];
    density?: Density;
    loading?: boolean;
    error?: string | null;
    pageIndex?: number;
    pageSize?: number;
  }

  async function render(page: SetPage | null, inputs: Inputs = {}): Promise<void> {
    fixture = TestBed.createComponent(SetsTab);
    fixture.componentRef.setInput('page', page);
    fixture.componentRef.setInput('roots', ['C:\\Archive']);
    for (const [name, value] of Object.entries(inputs)) {
      fixture.componentRef.setInput(name, value);
    }
    fixture.componentInstance.pageChanged.subscribe((event) => emitted.push(event));
    fixture.componentInstance.filterChanged.subscribe((filtered) => filters.push(filtered));
    loader = TestbedHarnessEnvironment.loader(fixture);
    await settle(fixture);
  }

  function root(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function texts(selector: string, within: ParentNode = root()): string[] {
    return Array.from(within.querySelectorAll(selector)).map((el) => (el.textContent ?? '').replace(/\s+/g, ' ').trim());
  }

  /** The copy rows of the set at `index`. */
  function copyRows(index: number): HTMLElement[] {
    return Array.from(root().querySelectorAll('tbody.duplicate-set')[index].querySelectorAll<HTMLElement>('tr.copy-row'));
  }

  const ubuntu = duplicateSet(
    [
      ['C:\\Archive\\Downloads\\ubuntu.iso'],
      ['C:\\Archive\\Backups\\MacBook\\Users\\me\\Downloads\\ubuntu.iso'],
      ['C:\\Archive\\Backups\\old-drive\\isos\\linux\\ubuntu.iso'],
    ],
    1610612736,
    'DIGEST-SECRET-0123',
  );
  const small = duplicateSet([['C:\\Archive\\x\\b.txt'], ['C:\\Archive\\y\\b.txt']], 1024, 'D2');
  const gopro = duplicateSet(
    [['C:\\Archive\\Backup\\GX01.MP4'], ['C:\\Archive\\Video\\GX01.MP4', 'C:\\Archive\\Video\\GX01-link.MP4']],
    5242880,
    'D3',
  );

  it('DFS-060.AC-5 shows each set as a header row with its name, copies, size each and reclaimable bytes', async () => {
    await render(setPage([ubuntu, small]));

    const sets = Array.from(root().querySelectorAll('tbody.duplicate-set'));
    expect(sets).toHaveLength(2);
    expect(texts('.set-name', sets[0])).toEqual(['ubuntu.iso']);
    expect(texts('.set-count', sets[0])).toEqual(['3 copies']);
    expect(texts('.set-size', sets[0])).toEqual(['1.5 GB each']);
    expect(texts('.set-reclaimable-bytes', sets[0])).toEqual(['3.0 GB']);
    expect(texts('.set-name', sets[1])).toEqual(['b.txt']);
    expect(texts('.set-count', sets[1])).toEqual(['2 copies']);
    expect(texts('.set-size', sets[1])).toEqual(['1.0 KB each']);
    expect(texts('.set-reclaimable-bytes', sets[1])).toEqual(['1.0 KB']);
    expect(root().querySelectorAll('tr.set-header')).toHaveLength(2);
  });

  it('DFS-060.AC-5 scales each reclaimable bar to the largest on the page, which is full', async () => {
    await render(setPage([small, ubuntu]));

    const widths = Array.from(root().querySelectorAll<HTMLElement>('tr.set-header .bar-fill')).map((el) =>
      parseFloat(el.style.width),
    );
    expect(widths[1]).toBe(100);
    expect(widths[0]).toBeCloseTo((1024 / 3221225472) * 100, 6);
  });

  it('DFS-060.AC-5 follows a header with one row per copy showing its short path, modified date and a copy button', async () => {
    await render(setPage([ubuntu]));

    const rows = copyRows(0);
    expect(rows).toHaveLength(3);
    expect(texts('.short-path', rows[0])).toEqual(['Downloads\\ubuntu.iso']);
    expect(texts('.short-path', rows[1])).toEqual(['Backups\\MacBook\\Users\\me\\Downloads\\ubuntu.iso']);
    expect(texts('.short-path', rows[2])).toEqual(['Backups\\old-drive\\isos\\linux\\ubuntu.iso']);
    for (const row of rows) {
      expect(texts('.copy-modified time', row)).toEqual(['2026-10-01']);
      expect(row.querySelector('.copy-modified time')?.getAttribute('datetime')).toBe('2026-10-01T12:00:00Z');
      expect(row.querySelectorAll('app-path-copy button')).toHaveLength(1);
    }
  });

  it('DFS-060.AC-5 never shows the digest', async () => {
    await render(setPage([ubuntu]));

    expect(root().textContent).not.toContain('DIGEST-SECRET-0123');
  });

  it('DFS-060.AC-7 puts the full path in each row\'s tooltip and on its copy button', async () => {
    await render(setPage([ubuntu]));

    const rows = copyRows(0);
    expect(rows[2].querySelector('.short-path')?.getAttribute('title')).toBe(
      'C:\\Archive\\Backups\\old-drive\\isos\\linux\\ubuntu.iso',
    );
    expect(rows[2].querySelector('app-path-copy button')?.getAttribute('aria-label')).toBe(
      'Copy path of C:\\Archive\\Backups\\old-drive\\isos\\linux\\ubuntu.iso',
    );
  });

  it('DFS-060.AC-7 shortens a long path from the middle against the scan root, in two colours', async () => {
    const long = 'C:\\Archive\\Backups\\MacBook-2020\\Users\\me\\Pictures\\Iceland 2019\\Raw Exports\\Day 3\\pano-final.tif';
    await render(setPage([duplicateSet([[long], ['C:\\Archive\\other\\pano-final.tif']])]));

    const row = copyRows(0)[0];
    expect(texts('.short-path', row)).toEqual(['Backups\\…\\Iceland 2019\\Raw Exports\\Day 3\\pano-final.tif']);
    expect(texts('.path-folders', row)).toEqual(['Backups\\…\\Iceland 2019\\Raw Exports\\Day 3\\']);
    expect(texts('.path-name', row)).toEqual(['pano-final.tif']);
    expect(row.querySelector('.short-path')?.getAttribute('title')).toBe(long);
  });

  it('DFS-060.AC-7 prefixes the root\'s name when the scan has several roots', async () => {
    const photos = duplicateSet([['C:\\Archive\\a.jpg'], ['D:\\Backup\\Photos\\a.jpg']]);
    await render(setPage([photos]), { roots: ['C:\\Archive', 'D:\\Backup'] });

    expect(texts('.short-path', copyRows(0)[0])).toEqual(['Archive\\a.jpg']);
    expect(texts('.short-path', copyRows(0)[1])).toEqual(['Backup\\Photos\\a.jpg']);
  });

  it('DFS-060.AC-6 shows a copy with several links as one row with a "<n> links" badge listing every full path', async () => {
    await render(setPage([gopro]));

    const rows = copyRows(0);
    expect(rows).toHaveLength(2);
    expect(rows[0].querySelector('.links-badge')).toBeNull();
    const badge = rows[1].querySelector('.links-badge');
    expect(badge?.textContent?.trim()).toBe('2 links');
    expect(badge?.getAttribute('title')).toBe('C:\\Archive\\Video\\GX01.MP4\nC:\\Archive\\Video\\GX01-link.MP4');
    expect(texts('.short-path', rows[1])).toEqual(['Video\\GX01.MP4']);
    expect(root().textContent).not.toContain('hard link (same file)');
    expect(root().querySelectorAll('.links-badge')).toHaveLength(1);
  });

  it('DFS-060.AC-6 marks a copy under the selected folder "in selected" and no other', async () => {
    await render(setPage([ubuntu]), { folder: 'C:\\Archive\\Backups\\old-drive' });

    const rows = copyRows(0);
    expect(rows.map((row) => row.querySelector('.selected-badge')?.textContent?.trim() ?? null)).toEqual([
      null,
      null,
      'in selected',
    ]);
    expect(rows.map((row) => row.classList.contains('in-selected-folder'))).toEqual([false, false, true]);
  });

  it('DFS-060.AC-6 marks a copy when any of its links is under the selected folder', async () => {
    await render(setPage([gopro]), { folder: 'C:\\Archive\\Video' });

    const rows = copyRows(0);
    expect(rows.map((row) => row.querySelector('.selected-badge') !== null)).toEqual([false, true]);
  });

  it('DFS-060.AC-6 does not treat a sibling with the same prefix as under the folder', async () => {
    const siblings = duplicateSet([['C:\\Archive\\Photos\\a.jpg'], ['C:\\Archive\\Photos2\\a.jpg']]);
    await render(setPage([siblings]), { folder: 'C:\\Archive\\Photos' });

    expect(copyRows(0).map((row) => row.querySelector('.selected-badge') !== null)).toEqual([true, false]);
  });

  it('DFS-060.AC-6 marks nothing with "All folders" selected', async () => {
    await render(setPage([ubuntu, gopro]), { folder: null });

    expect(root().querySelectorAll('.selected-badge')).toHaveLength(0);
    expect(root().querySelectorAll('.in-selected-folder')).toHaveLength(0);
  });

  it('DFS-060.AC-8 offers "All sets" and "Copies under <folder name>" with a folder selected, the second on', async () => {
    await render(setPage([ubuntu]), { folder: 'C:\\Archive\\Backups\\old-drive', filtered: true });

    const group = await loader.getHarness(MatButtonToggleGroupHarness.with({ selector: '.sets-filter' }));
    const toggles = await group.getToggles();
    expect(await Promise.all(toggles.map((toggle) => toggle.getText()))).toEqual(['All sets', 'Copies under old-drive']);
    expect(await Promise.all(toggles.map((toggle) => toggle.isChecked()))).toEqual([false, true]);
  });

  it('DFS-060.AC-8 shows "All sets" on when the selection does not filter', async () => {
    await render(setPage([ubuntu]), { folder: 'C:\\Archive\\Backups\\old-drive', filtered: false });

    const group = await loader.getHarness(MatButtonToggleGroupHarness.with({ selector: '.sets-filter' }));
    const toggles = await group.getToggles();
    expect(await Promise.all(toggles.map((toggle) => toggle.isChecked()))).toEqual([true, false]);
  });

  it('DFS-060.AC-8 emits false for "All sets" and true for "Copies under <folder>"', async () => {
    await render(setPage([ubuntu]), { folder: 'C:\\Archive\\Backups\\old-drive', filtered: true });
    const group = await loader.getHarness(MatButtonToggleGroupHarness.with({ selector: '.sets-filter' }));

    await (await group.getToggles({ text: 'All sets' }))[0].check();
    await settle(fixture);
    expect(filters).toEqual([false]);

    fixture.componentRef.setInput('filtered', false);
    await settle(fixture);
    await (await group.getToggles({ text: 'Copies under old-drive' }))[0].check();
    await settle(fixture);
    expect(filters).toEqual([false, true]);
  });

  it('DFS-060.AC-8 shows no filter toggle with "All folders" selected', async () => {
    await render(setPage([ubuntu]), { folder: null });

    expect(root().querySelector('.sets-filter')).toBeNull();
    expect(root().querySelectorAll('tbody.duplicate-set')).toHaveLength(1);
  });

  it('DFS-060.AC-8 shows a paginator of size 50 with the choices 25, 50 and 100', async () => {
    await render(setPage([ubuntu], { totalItems: 120 }));

    const paginator = await loader.getHarness(MatPaginatorHarness);
    expect(await paginator.getPageSize()).toBe(50);
    expect(root().querySelector('mat-paginator')?.getAttribute('aria-label')).toBe('Duplicate set pages');
  });

  it('DFS-060.AC-8 emits the next page and a chosen page size', async () => {
    await render(setPage([ubuntu], { totalItems: 120 }));
    const paginator = await loader.getHarness(MatPaginatorHarness);

    await paginator.goToNextPage();
    await settle(fixture);
    expect(emitted.map((e) => [e.pageIndex, e.pageSize])).toEqual([[1, 50]]);

    await paginator.setPageSize(100);
    await settle(fixture);
    expect(emitted[emitted.length - 1].pageSize).toBe(100);
    await paginator.setPageSize(25);
    await settle(fixture);
    expect(emitted[emitted.length - 1].pageSize).toBe(25);
  });

  it('DFS-060.AC-9 starts Compact and follows the density it is given', async () => {
    await render(setPage([ubuntu]));
    const section = () => root().querySelector('section.sets-tab');
    expect(section()?.classList.contains('density-compact')).toBe(true);
    expect(section()?.classList.contains('density-comfortable')).toBe(false);

    fixture.componentRef.setInput('density', 'comfortable');
    await settle(fixture);
    expect(section()?.classList.contains('density-comfortable')).toBe(true);
    expect(section()?.classList.contains('density-compact')).toBe(false);
    // The class replacement keeps the section's own class.
    expect(section()?.classList.contains('sets-tab')).toBe(true);
  });

  it('DFS-060.AC-10 says the scan found no duplicates under "All folders" and shows no paginator', async () => {
    await render(setPage([], { totalItems: 0 }), { folder: null });

    expect(texts('.sets-empty')).toEqual(['This scan found no duplicates']);
    expect(root().querySelector('mat-paginator')).toBeNull();
    expect(root().querySelector('table.sets-table')).toBeNull();
  });

  it('DFS-060.AC-10 says there are no duplicates under a selected folder that filters', async () => {
    await render(setPage([], { totalItems: 0 }), { folder: 'D:\\Backup', filtered: true });

    expect(texts('.sets-empty')).toEqual(['No duplicates under this folder']);
    expect(root().querySelector('mat-paginator')).toBeNull();
  });

  it('DFS-060.AC-10 says the scan found no duplicates under "All sets" with a folder selected', async () => {
    await render(setPage([], { totalItems: 0 }), { folder: 'D:\\Backup', filtered: false });

    expect(texts('.sets-empty')).toEqual(['This scan found no duplicates']);
  });

  it('DFS-060.AC-10 shows a progress bar labelled "Loading duplicate sets" while a page loads', async () => {
    await render(null, { loading: true });

    const bars = await loader.getAllHarnesses(MatProgressBarHarness.with({ selector: '[aria-label="Loading duplicate sets"]' }));
    expect(bars).toHaveLength(1);
    expect(root().querySelector('section.sets-tab')?.getAttribute('aria-busy')).toBe('true');
  });

  it('DFS-060.AC-10 shows the server message with role "alert" when the page failed', async () => {
    await render(null, { error: 'The path is not absolute' });

    const alert = root().querySelector('.sets-error');
    expect(alert?.getAttribute('role')).toBe('alert');
    expect(alert?.textContent?.trim()).toBe('The path is not absolute');
    expect(
      await loader.getAllHarnesses(MatProgressBarHarness.with({ selector: '[aria-label="Loading duplicate sets"]' })),
    ).toHaveLength(0);
  });
});

describe('pathBudget', () => {
  it('DFS-065.AC-8 fits the measured column, less the badges a row carries', () => {
    expect(pathBudget(720, 7.2, false, false)).toBe(100);
    expect(pathBudget(720, 7.2, true, false)).toBe(91);
    expect(pathBudget(720, 7.2, true, true)).toBe(78);
  });

  it('DFS-065.AC-8 never goes below 24 characters', () => {
    expect(pathBudget(100, 7.2, true, true)).toBe(24);
  });

  it('DFS-065.AC-8 falls back to PATH_BUDGET when nothing was measured', () => {
    expect(pathBudget(0, 7.2, false, false)).toBe(PATH_BUDGET);
    expect(pathBudget(720, 0, false, false)).toBe(PATH_BUDGET);
  });
});
