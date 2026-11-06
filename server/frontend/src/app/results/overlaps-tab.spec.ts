import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { MatButtonHarness } from '@angular/material/button/testing';
import { MatPaginatorHarness } from '@angular/material/paginator/testing';
import { MatProgressBarHarness } from '@angular/material/progress-bar/testing';
import { FolderPairView, PairPage, SummaryPage } from '../api/models';
import { provideStubSurfacing, settle } from '../../testing/stub-surfacing';
import { OverlapsTab } from './overlaps-tab';
import {
  expectPair,
  expectPairs,
  expectSummaries,
  pair,
  pairPage,
  refuse,
  side,
  summary,
  summaryPage,
} from './result-test-data';

describe('OverlapsTab', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<OverlapsTab>;
  let loader: HarnessLoader;
  let unpinned: number;

  const NOTE = 'A copy elsewhere may itself be deleted by another choice; checking what is safe comes with the deletion plan.';

  beforeEach(async () => {
    unpinned = 0;
    await TestBed.configureTestingModule({
      imports: [OverlapsTab],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideStubSurfacing()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function render(folder: string | null = null, pinned: string | null = null): Promise<void> {
    fixture = TestBed.createComponent(OverlapsTab);
    fixture.componentRef.setInput('scanId', 's-1');
    fixture.componentRef.setInput('folder', folder);
    fixture.componentRef.setInput('pinned', pinned);
    fixture.componentInstance.unpinned.subscribe(() => unpinned++);
    loader = TestbedHarnessEnvironment.loader(fixture);
    await settle(fixture);
  }

  async function step(): Promise<void> {
    await settle(fixture);
  }

  /** Answers the summaries and pairs requests made for `under` (page 0, size 10). */
  async function answer(
    summaries: SummaryPage,
    pairs: PairPage,
    under: string | null = null,
  ): Promise<void> {
    expectSummaries(http, 's-1', under).flush(summaries);
    expectPairs(http, 's-1', under).flush(pairs);
    await step();
  }

  async function renderAnswered(
    summaries: SummaryPage = summaryPage([]),
    pairs: PairPage = pairPage([]),
    folder: string | null = null,
  ): Promise<void> {
    await render(folder);
    await answer(summaries, pairs, folder);
  }

  function page(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function norm(el: Element | null | undefined): string {
    return (el?.textContent ?? '').replace(/\s+/g, ' ').trim();
  }

  function text(selector: string): string[] {
    return Array.from(page().querySelectorAll(selector)).map((el) => norm(el));
  }

  const backup2019 = summary('D:\\Backup\\2019', {
    totalFiles: 340,
    totalBytes: 1288490188,
    filesWithCopyElsewhere: 340,
    bytesWithCopyElsewhere: 1288490188,
  });
  const photos2019 = summary('C:\\Photos\\2019', {
    totalFiles: 400,
    totalBytes: 1503238553,
    filesWithCopyElsewhere: 340,
    bytesWithCopyElsewhere: 1288490188,
    internalDuplicateFiles: 12,
    internalBytes: 83886080,
  });

  it('DFS-050.AC-1 shows no header under "All folders", then the selected folder\'s summary above its tables', async () => {
    await renderAnswered(summaryPage([backup2019], { totalItems: 34 }), pairPage([], { totalItems: 12 }));

    expect(page().querySelector('.folder-header')).toBeNull();
    expect(text('section.summaries h3')).toEqual(['Folders']);
    expect(text('section.pairs h3')).toEqual(['Folder pairs']);
    expect(page().querySelectorAll('mat-paginator')).toHaveLength(2);
    expect(page().querySelector('.summaries-paginator')).not.toBeNull();
    expect(page().querySelector('.pairs-paginator')).not.toBeNull();

    fixture.componentRef.setInput('folder', 'D:\\Backup');
    await step();
    await answer(
      summaryPage([backup2019], { totalItems: 34, folder: summary('D:\\Backup', { totalFiles: 5, totalBytes: 900 }) }),
      pairPage([], { totalItems: 12 }),
      'D:\\Backup',
    );

    expect(text('.folder-header .summary-folder')).toEqual(['D:\\Backup']);
    expect(text('.folder-header .summary-total')).toEqual(['Total 5 files · 900 B']);
    const header = page().querySelector('.folder-header') as HTMLElement;
    const folders = page().querySelector('section.summaries') as HTMLElement;
    expect(header.compareDocumentPosition(folders) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it('DFS-050.AC-2 reads each folder row and the selected folder\'s header, with the chip only on a fully copied folder', async () => {
    await renderAnswered(
      summaryPage([backup2019, photos2019], { folder: backup2019 }),
      pairPage([]),
      'D:\\Backup\\2019',
    );

    const rows = Array.from(page().querySelectorAll('tr.summary-row'));
    expect(rows).toHaveLength(2);
    const cells = (row: Element) => Array.from(row.querySelectorAll('td')).map((td) => norm(td));
    expect(cells(rows[0])).toEqual([
      'D:\\Backup\\2019 all copied elsewhere',
      '340 / 340 files · 1.2 GB',
      '0 files · 0 B',
      '340 files · 1.2 GB',
    ]);
    expect(cells(rows[1])).toEqual([
      'C:\\Photos\\2019',
      '340 / 400 files · 1.2 GB',
      '12 files · 80.0 MB',
      '400 files · 1.4 GB',
    ]);
    expect(rows[0].querySelector('mat-chip')).not.toBeNull();
    expect(rows[1].querySelector('mat-chip')).toBeNull();
    expect(text('th')).toEqual(expect.arrayContaining(['Folder', 'Copied elsewhere', 'Duplicates inside', 'Total']));

    expect(text('.folder-header .summary-folder')).toEqual(['D:\\Backup\\2019']);
    expect(text('.folder-header .summary-copied')).toEqual(['Copied elsewhere 340 / 340 files · 1.2 GB']);
    expect(text('.folder-header .summary-inside')).toEqual(['Duplicates inside 0 files · 0 B']);
    expect(text('.folder-header .summary-total')).toEqual(['Total 340 files · 1.2 GB']);
    expect(page().querySelector('.folder-header mat-chip.all-copied')).not.toBeNull();
  });

  it('DFS-050.AC-2 gives an empty folder no "all copied elsewhere" chip', async () => {
    const empty = summary('D:\\Empty', {
      totalFiles: 0,
      totalBytes: 0,
      filesWithCopyElsewhere: 0,
      bytesWithCopyElsewhere: 0,
    });
    await renderAnswered(summaryPage([empty, backup2019]));

    const rows = Array.from(page().querySelectorAll('tr.summary-row'));
    expect(rows).toHaveLength(2);
    expect(rows[0].querySelector('mat-chip')).toBeNull();
    expect(norm(rows[0].querySelector('td'))).toBe('D:\\Empty');
    expect(rows[1].querySelector('mat-chip')).not.toBeNull();
  });

  it('DFS-050.AC-3 shows side A then side B in the API order, "partial" and "all have a copy in the other"', async () => {
    const row: FolderPairView = pair(
      side('C:\\Photos\\2019', { filesWithCopy: 340, totalFiles: 400, bytesWithCopy: 1288490188, full: false }),
      side('D:\\Backup\\2019', { filesWithCopy: 340, totalFiles: 340, bytesWithCopy: 1288490188, full: true }),
    );
    await renderAnswered(summaryPage([]), pairPage([row]));

    expect(page().querySelectorAll('tr.pair-row')).toHaveLength(1);
    expect(text('tr.pair-row .pair-side .side-text')).toEqual([
      'C:\\Photos\\2019: 340 / 400 files · 1.2 GB',
      'D:\\Backup\\2019: 340 / 340 files · 1.2 GB',
    ]);
    expect(text('tr.pair-row .pair-side .side-coverage')).toEqual(['partial', 'all have a copy in the other']);
    expect(text('tr.pair-row .side-a .side-text')).toEqual(['C:\\Photos\\2019: 340 / 400 files · 1.2 GB']);
    expect(text('tr.pair-row .side-b .side-coverage')).toEqual(['all have a copy in the other']);
  });

  it('DFS-050.AC-4 shows the note above the tables and no verdict wording for two folders each fully covered by the other', async () => {
    const both = pair(
      side('D:\\A', { totalFiles: 5, filesWithCopy: 5, full: true }),
      side('D:\\B', { totalFiles: 5, filesWithCopy: 5, full: true }),
    );
    await renderAnswered(
      summaryPage([backup2019], { folder: backup2019 }),
      pairPage([both]),
      'D:\\Backup\\2019',
    );

    expect(text('.pair-side .side-coverage')).toEqual(['all have a copy in the other', 'all have a copy in the other']);
    expect(text('.overlap-note')).toEqual([NOTE]);
    const noteEl = page().querySelector('.overlap-note') as HTMLElement;
    for (const section of ['section.folder-header', 'section.summaries', 'section.pairs']) {
      const el = page().querySelector(section) as HTMLElement;
      expect(noteEl.compareDocumentPosition(el) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    }
    const verdict = /safe|delete|can go/i;
    for (const selector of ['tr', '.folder-header', 'mat-chip', '.pair-side', 'th']) {
      for (const content of text(selector)) expect(content).not.toMatch(verdict);
    }
  });

  it('DFS-050.AC-5 shows "Pinned: <path>" with an "Unpin" link that emits unpinned, and nothing once unpinned', async () => {
    await render(null, 'D:\\Backup');
    await answer(summaryPage([]), pairPage([]));

    expect(text('.pinned-folder')).toEqual(['Pinned: D:\\Backup']);
    const unpin = await loader.getHarness(MatButtonHarness.with({ text: 'Unpin' }));
    await unpin.click();
    await step();
    expect(unpinned).toBe(1);

    fixture.componentRef.setInput('pinned', 'C:\\Photos');
    await step();
    expect(text('.pinned-folder')).toEqual(['Pinned: C:\\Photos']);

    fixture.componentRef.setInput('pinned', null);
    await step();
    expect(page().querySelector('.pinned-folder')).toBeNull();
    expect(page().querySelector('section.comparison')).toBeNull();
    expect(await loader.getAllHarnesses(MatButtonHarness.with({ text: 'Unpin' }))).toHaveLength(0);
  });

  it('DFS-050.AC-6 requests /pair with a=<pinned> and b=<selected> and shows one pair row in the Comparison block', async () => {
    await render('C:\\Photos', 'D:\\Backup');
    expectSummaries(http, 's-1', 'C:\\Photos').flush(summaryPage([]));
    expectPairs(http, 's-1', 'C:\\Photos').flush(pairPage([]));
    const req = expectPair(http, 's-1', 'D:\\Backup', 'C:\\Photos');
    expect(req.request.method).toBe('GET');
    req.flush(
      pair(
        side('C:\\Photos', { filesWithCopy: 340, totalFiles: 3210, bytesWithCopy: 1288490188, full: false }),
        side('D:\\Backup', { filesWithCopy: 2, totalFiles: 2, bytesWithCopy: 900, full: true }),
      ),
    );
    await step();

    expect(text('section.comparison h3')).toEqual(['Comparison']);
    expect(text('.comparison-pair .pair-side .side-text')).toEqual([
      'C:\\Photos: 340 / 3210 files · 1.2 GB',
      'D:\\Backup: 2 / 2 files · 900 B',
    ]);
    expect(text('.comparison-pair .pair-side .side-coverage')).toEqual(['partial', 'all have a copy in the other']);
    expect(page().querySelector('.comparison-hint')).toBeNull();
  });

  it('DFS-050.AC-7 sends no /pair for the pinned folder itself or "All folders" and shows the hint', async () => {
    await render('D:\\Backup', 'D:\\Backup');
    await answer(summaryPage([]), pairPage([]), 'D:\\Backup');
    http.expectNone((r) => r.url.endsWith('/pair'));
    expect(text('.comparison-hint')).toEqual(['Select another folder to compare with the pinned one']);

    fixture.componentRef.setInput('folder', null);
    await step();
    await answer(summaryPage([]), pairPage([]));
    http.expectNone((r) => r.url.endsWith('/pair'));
    expect(text('.comparison-hint')).toEqual(['Select another folder to compare with the pinned one']);
  });

  it('DFS-050.AC-7 shows the server message when /pair answers 400 NESTED_FOLDERS', async () => {
    await render('C:\\Photos\\2019', 'C:\\Photos');
    expectSummaries(http, 's-1', 'C:\\Photos\\2019').flush(summaryPage([]));
    expectPairs(http, 's-1', 'C:\\Photos\\2019').flush(pairPage([]));
    refuse(expectPair(http, 's-1', 'C:\\Photos', 'C:\\Photos\\2019'), 400, {
      code: 'NESTED_FOLDERS',
      message: 'C:\\Photos contains C:\\Photos\\2019',
    });
    await step();

    const alert = page().querySelector('.comparison-error');
    expect(alert?.getAttribute('role')).toBe('alert');
    expect(norm(alert)).toBe('C:\\Photos contains C:\\Photos\\2019');
    expect(page().querySelector('.comparison-pair')).toBeNull();
  });

  it('DFS-050.AC-9 shows count lines and pages each table on its own, then both from page 0 for another folder', async () => {
    await renderAnswered(
      summaryPage([backup2019], { totalItems: 34 }),
      pairPage([pair(side('D:\\A'), side('D:\\B'))], { totalItems: 12 }),
    );

    expect(text('.summaries-count')).toEqual(['34 folders']);
    expect(text('.pairs-count')).toEqual(['12 folder pairs']);
    const summariesPager = await loader.getHarness(MatPaginatorHarness.with({ selector: '.summaries-paginator' }));
    const pairsPager = await loader.getHarness(MatPaginatorHarness.with({ selector: '.pairs-paginator' }));
    expect(await summariesPager.getPageSize()).toBe(10);
    expect(await pairsPager.getPageSize()).toBe(10);

    await summariesPager.goToNextPage();
    await step();
    expectSummaries(http, 's-1', null, 1, 10).flush(summaryPage([backup2019], { totalItems: 34, page: 1 }));
    http.expectNone((r) => r.url.endsWith('/pairs'));
    await step();

    await pairsPager.setPageSize(25);
    await step();
    expectPairs(http, 's-1', null, 0, 25).flush(
      pairPage([pair(side('D:\\A'), side('D:\\B'))], { totalItems: 12, size: 25 }),
    );
    http.expectNone((r) => r.url.endsWith('/summaries'));
    await step();

    fixture.componentRef.setInput('folder', 'C:\\Photos');
    await step();
    expectSummaries(http, 's-1', 'C:\\Photos', 0, null).flush(summaryPage([backup2019], { totalItems: 34 }));
    expectPairs(http, 's-1', 'C:\\Photos', 0, null).flush(pairPage([], { totalItems: 0 }));
    await step();
  });

  it('DFS-050.AC-10 says there is nothing to show and no paginator when neither table has items', async () => {
    await renderAnswered(summaryPage([], { totalItems: 0 }), pairPage([], { totalItems: 0 }));

    expect(text('.summaries-empty')).toEqual(['No folders with copies here']);
    expect(text('.pairs-empty')).toEqual(['No folder pairs share copies here']);
    expect(page().querySelector('mat-paginator')).toBeNull();
    expect(page().querySelector('.summaries-count')).toBeNull();
    expect(page().querySelector('.pairs-count')).toBeNull();
  });

  it('DFS-050.AC-10 shows a progress bar while a table loads, then its server message while the other table keeps its rows', async () => {
    await render();
    const summaries = expectSummaries(http, 's-1', null);
    const pairs = expectPairs(http, 's-1', null);
    await step();
    const bar = await loader.getAllHarnesses(MatProgressBarHarness.with({ selector: '[aria-label="Loading folders"]' }));
    expect(bar).toHaveLength(1);

    pairs.flush(pairPage([pair(side('D:\\A'), side('D:\\B'))]));
    refuse(summaries, 403, { code: 'PATH_OUTSIDE_ROOTS', message: 'The path is outside the scanned folders' });
    await step();

    const alert = page().querySelector('.summaries-error');
    expect(alert?.getAttribute('role')).toBe('alert');
    expect(norm(alert)).toBe('The path is outside the scanned folders');
    expect(
      await loader.getAllHarnesses(MatProgressBarHarness.with({ selector: '[aria-label="Loading folders"]' })),
    ).toHaveLength(0);
    expect(page().querySelectorAll('tr.pair-row')).toHaveLength(1);
    expect(page().querySelector('.pairs-error')).toBeNull();
  });

  it('DFS-050.AC-10 shows a progress bar while the comparison loads', async () => {
    await render('C:\\Photos', 'D:\\Backup');
    expectSummaries(http, 's-1', 'C:\\Photos').flush(summaryPage([]));
    expectPairs(http, 's-1', 'C:\\Photos').flush(pairPage([]));
    const req = expectPair(http, 's-1', 'D:\\Backup', 'C:\\Photos');
    await step();

    const bars = await loader.getAllHarnesses(MatProgressBarHarness.with({ selector: '[aria-label="Loading the comparison"]' }));
    expect(bars).toHaveLength(1);

    req.flush(pair(side('C:\\Photos'), side('D:\\Backup')));
    await step();
    expect(
      await loader.getAllHarnesses(MatProgressBarHarness.with({ selector: '[aria-label="Loading the comparison"]' })),
    ).toHaveLength(0);
    expect(page().querySelector('.comparison-pair')).not.toBeNull();
  });
});
