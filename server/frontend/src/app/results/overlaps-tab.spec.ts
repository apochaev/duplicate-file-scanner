import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { MatButtonHarness } from '@angular/material/button/testing';
import { MatCheckboxHarness } from '@angular/material/checkbox/testing';
import { MatExpansionPanelHarness } from '@angular/material/expansion/testing';
import { MatPaginatorHarness } from '@angular/material/paginator/testing';
import { MatProgressBarHarness } from '@angular/material/progress-bar/testing';
import { FolderGroupView, GroupPage } from '../api/models';
import { provideStubSurfacing, settle } from '../../testing/stub-surfacing';
import { isAppManaged, OverlapsTab } from './overlaps-tab';
import { DONE_STORAGE, DoneStorage } from './result-done-groups';
import {
  expectGroups,
  expectPair,
  folderGroup,
  groupFolder,
  groupPage,
  groupSet,
  pair,
  refuse,
  side,
} from './result-test-data';

describe('OverlapsTab', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<OverlapsTab>;
  let loader: HarnessLoader;
  let unpinned: number;

  const APP_TITLE = 'Managed by an application; deleting here can break it';
  const FULLY_SHARED_NOTE = 'Fully shared folders: any one can go, not all.';

  beforeEach(async () => {
    unpinned = 0;
    await TestBed.configureTestingModule({
      imports: [OverlapsTab],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideStubSurfacing()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function render(
    folder: string | null = null,
    pinned: string | null = null,
    roots: string[] = ['D:/'],
  ): Promise<void> {
    fixture = TestBed.createComponent(OverlapsTab);
    fixture.componentRef.setInput('scanId', 's-1');
    fixture.componentRef.setInput('folder', folder);
    fixture.componentRef.setInput('pinned', pinned);
    fixture.componentRef.setInput('roots', roots);
    fixture.componentInstance.unpinned.subscribe(() => unpinned++);
    loader = TestbedHarnessEnvironment.loader(fixture);
    await settle(fixture);
  }

  async function step(): Promise<void> {
    await settle(fixture);
  }

  /** Renders the tab on "All folders" and answers its first /groups request with `answer`. */
  async function renderAnswered(answer: GroupPage, roots: string[] = ['D:/']): Promise<void> {
    await render(null, null, roots);
    expectGroups(http, 's-1').flush(answer);
    await step();
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

  function widths(selector: string): number[] {
    return Array.from(page().querySelectorAll<HTMLElement>(selector)).map((el) => parseFloat(el.style.width));
  }

  const MEDIA = 'D:/Media/Photo/Import/2020-07 Camper Van/2020-07-09 Gemini Bridges';
  const AUH = `${MEDIA}/_AVP3481.auh/Contents/History/resources`;

  /** The ticket's heaviest group of the d:/ scan: 5 files, 10 copies, 206.2 MB reclaimable. */
  function heaviest() {
    const sets = Array.from({ length: 5 }, (_, i) => groupSet(`_AVP348${i}.dat`, 43200000, 2));
    return folderGroup(
      [
        groupFolder(MEDIA, { sharedFiles: 5, folderFiles: 190 }),
        groupFolder(AUH, { sharedFiles: 5, folderFiles: 12 }),
      ],
      sets,
      { reclaimableBytes: 216200000 },
    );
  }

  it('DFS-068.AC-1, DFS-068.AC-2, DFS-068.AC-8 reads the heaviest group of the d:/ scan', async () => {
    await renderAnswered(groupPage([heaviest()]));

    expect(text('.group-header .group-files')).toEqual(['5 files']);
    expect(text('.group-header .group-meta')).toEqual(['· 10 copies · 41.2 MB each · shared by 2 folders']);
    expect(text('.group-header .group-reclaimable-bytes')).toEqual(['206.2 MB']);

    const lines = Array.from(page().querySelectorAll('li.folder-line'));
    expect(lines).toHaveLength(2);
    expect(norm(lines[0].querySelector('.shared-count'))).toBe('5 of 190 files');
    expect(lines[0].querySelector('.app-tag')).toBeNull();
    expect(norm(lines[1].querySelector('.shared-count'))).toBe('5 of 12 files');
    expect(norm(lines[1].querySelector('.app-tag'))).toBe('app');
    expect(lines[1].querySelector('.app-tag')?.getAttribute('title')).toBe(APP_TITLE);
  });

  it('DFS-068.AC-1 lists the groups in the server order, the reclaimable bars scaled to the page\'s largest', async () => {
    const big = folderGroup([groupFolder('D:/A'), groupFolder('D:/B')], [groupSet('big.iso', 200, 2)]);
    const half = folderGroup([groupFolder('D:/C'), groupFolder('D:/D')], [groupSet('half.iso', 100, 2)]);
    const small = folderGroup([groupFolder('D:/E'), groupFolder('D:/F')], [groupSet('small.iso', 50, 2)]);
    // The server's order is kept, even when a later group is the largest.
    await renderAnswered(groupPage([half, big, small]));

    expect(page().querySelectorAll('div.group')).toHaveLength(3);
    expect(text('.group-header .group-reclaimable-bytes')).toEqual(['100 B', '200 B', '50 B']);
    expect(widths('.group-header .bar-fill')).toEqual([50, 100, 25]);
    expect(text('.group-header .group-meta')[0]).toBe('· 2 copies · 100 B each · shared by 2 folders');
  });

  it('DFS-068.AC-1 shows the total as "in all" when the group has sets not listed or sets of different sizes', async () => {
    // As /groups computes them: a folder's sharedBytes counts every copy it holds, reclaimableBytes is the files'
    // size times (copies - files) / files, so "in all" is the files' own size, one copy each.
    // 25 files of 1 MB, two copies each (one per folder), only 20 listed: 50 copies, 25 MB reclaimable.
    const unlisted = folderGroup(
      [
        groupFolder('D:/A', { sharedFiles: 25, sharedBytes: 26214400 }),
        groupFolder('D:/B', { sharedFiles: 25, sharedBytes: 26214400 }),
      ],
      Array.from({ length: 20 }, (_, i) => groupSet(`f${i}.bin`, 1048576, 2)),
      { files: 25, copies: 50, reclaimableBytes: 26214400, totalSets: 25 },
    );
    // 3 MB + 2 MB files in two folders: 4 copies, 5 MB reclaimable, 5 MB in all.
    const mixed = folderGroup(
      [
        groupFolder('D:/B', { sharedFiles: 2, sharedBytes: 5242880 }),
        groupFolder('D:/C', { sharedFiles: 2, sharedBytes: 5242880 }),
      ],
      [groupSet('three.bin', 3145728, 2), groupSet('two.bin', 2097152, 2)],
    );
    // 1 MB + 2 MB files, three copies each (one per folder): 6 copies, 6 MB reclaimable, 3 MB in all.
    const triple = folderGroup(
      [
        groupFolder('D:/D', { sharedFiles: 2, sharedBytes: 3145728 }),
        groupFolder('D:/E', { sharedFiles: 2, sharedBytes: 3145728 }),
        groupFolder('D:/F', { sharedFiles: 2, sharedBytes: 3145728 }),
      ],
      [groupSet('one.bin', 1048576, 3), groupSet('two.bin', 2097152, 3)],
    );
    await renderAnswered(groupPage([unlisted, mixed, triple]));

    expect(text('.group-header .group-meta')).toEqual([
      '· 50 copies · 25.0 MB in all · shared by 2 folders',
      '· 4 copies · 5.0 MB in all · shared by 2 folders',
      '· 6 copies · 3.0 MB in all · shared by 3 folders',
    ]);
  });

  it('DFS-068.AC-1 shows a progress bar while /groups loads, then the list', async () => {
    await render();
    const req = expectGroups(http, 's-1');
    await step();
    const bars = await loader.getAllHarnesses(MatProgressBarHarness.with({ selector: '[aria-label="Loading folder groups"]' }));
    expect(bars).toHaveLength(1);

    req.flush(groupPage([heaviest()]));
    await step();
    expect(
      await loader.getAllHarnesses(MatProgressBarHarness.with({ selector: '[aria-label="Loading folder groups"]' })),
    ).toHaveLength(0);
    expect(page().querySelectorAll('div.group')).toHaveLength(1);
  });

  it('DFS-068.AC-1 says "No folders share files" with no paginator when there are no groups', async () => {
    await renderAnswered(groupPage([], { totalItems: 0 }));

    expect(text('.groups-empty')).toEqual(['No folders share files']);
    expect(page().querySelector('mat-paginator')).toBeNull();
    expect(page().querySelector('div.group')).toBeNull();
  });

  it('DFS-068.AC-1 shows the server message in an alert when /groups is refused', async () => {
    await render('D:/Elsewhere');
    refuse(expectGroups(http, 's-1', 'D:/Elsewhere'), 403, {
      code: 'PATH_OUTSIDE_ROOTS',
      message: 'The path is outside the scanned folders',
    });
    await step();

    const alert = page().querySelector('.groups-error');
    expect(alert?.getAttribute('role')).toBe('alert');
    expect(norm(alert)).toBe('The path is outside the scanned folders');
    expect(page().querySelector('div.group')).toBeNull();
  });

  it('DFS-068.AC-1 pages the groups: 10 per page by default, 10, 25 and 50 offered', async () => {
    await renderAnswered(groupPage([heaviest()], { totalItems: 34 }));

    const pager = await loader.getHarness(MatPaginatorHarness.with({ selector: '.groups-paginator' }));
    expect(await pager.getPageSize()).toBe(10);

    await pager.goToNextPage();
    await step();
    expectGroups(http, 's-1', null, null, 1, 10).flush(groupPage([heaviest()], { totalItems: 34, page: 1 }));
    await step();

    await pager.setPageSize(25);
    await step();
    expectGroups(http, 's-1', null, null, 0, 25).flush(groupPage([heaviest()], { totalItems: 34, size: 25 }));
    await step();
  });

  it('DFS-068.AC-2 shows the short path with the full path as tooltip, "×N" only above 1, and a copy button', async () => {
    const group = folderGroup(
      [
        groupFolder('D:/Media/Photo/2020', { copiesEach: 1, sharedFiles: 3, folderFiles: 7 }),
        groupFolder('D:/Media/Photo/2020/Sunset', { copiesEach: 2, sharedFiles: 2, folderFiles: 7 }),
      ],
      [groupSet('a.bin')],
    );
    await renderAnswered(groupPage([group]), ['D:/Media']);

    const lines = Array.from(page().querySelectorAll('li.folder-line'));
    expect(lines.map((line) => norm(line.querySelector('.short-path')))).toEqual(['Photo/2020', 'Photo/2020/Sunset']);
    expect(lines.map((line) => line.querySelector('.short-path')?.getAttribute('title'))).toEqual([
      'D:/Media/Photo/2020',
      'D:/Media/Photo/2020/Sunset',
    ]);
    expect(lines[0].querySelector('.copies-each')).toBeNull();
    expect(norm(lines[1].querySelector('.copies-each'))).toBe('×2');
    expect(lines.map((line) => norm(line.querySelector('.shared-count')))).toEqual(['3 of 7 files', '2 of 7 files']);
    expect(widths('li.folder-line .share .bar-fill')[0]).toBeCloseTo((3 / 7) * 100, 1);
    expect(page().querySelectorAll('li.folder-line app-path-copy')).toHaveLength(2);
    const copy = await loader.getAllHarnesses(MatButtonHarness.with({ selector: '[aria-label="Copy path of D:/Media/Photo/2020"]' }));
    expect(copy).toHaveLength(1);
  });

  it('DFS-068.AC-2 cuts a long path from the middle and keeps the whole path as the tooltip', async () => {
    await renderAnswered(groupPage([heaviest()]));

    const second = page().querySelectorAll('li.folder-line')[1];
    expect(second.querySelector('.short-path')?.getAttribute('title')).toBe(AUH);
    expect(norm(second.querySelector('.path-folders'))).toContain('…');
    expect(norm(second.querySelector('.path-name'))).toBe('resources');
  });

  it('DFS-068.AC-2, DFS-068.AC-3, DFS-068.AC-8 reads two copies in an auto-save folder', async () => {
    const sunset = 'D:/Media/2020-08-20 Sunset GGBr';
    const group = folderGroup(
      [
        groupFolder(sunset, { copiesEach: 1, sharedFiles: 1, folderFiles: 1 }),
        groupFolder(`${sunset}/Adobe Premiere Pro Auto-Save`, { copiesEach: 2, sharedFiles: 2, folderFiles: 7 }),
      ],
      [groupSet('Sunset.prproj')],
    );
    await renderAnswered(groupPage([group]));

    const lines = Array.from(page().querySelectorAll('li.folder-line'));
    expect(norm(lines[0].querySelector('.fully-shared'))).toBe('fully shared');
    expect(lines[0].querySelector('.copies-each')).toBeNull();
    expect(lines[1].querySelector('.fully-shared')).toBeNull();
    expect(norm(lines[1].querySelector('.copies-each'))).toBe('×2');
    expect(norm(lines[1].querySelector('.shared-count'))).toBe('2 of 7 files');
    expect(norm(lines[1].querySelector('.app-tag'))).toBe('app');
    expect(lines[0].querySelector('.app-tag')).toBeNull();
  });

  it('DFS-068.AC-3, DFS-068.AC-4 marks two fully shared folders and says any one can go, not all', async () => {
    const group = folderGroup(
      [
        groupFolder('D:/Trips/2020-06-23 Garmin Road trip/Garmin', { sharedFiles: 1, folderFiles: 1 }),
        groupFolder('D:/Trips/2020-07-28 Virb Road Trip/Garmin', { sharedFiles: 1, folderFiles: 1 }),
      ],
      [groupSet('GMETRIX.FIT')],
    );
    await renderAnswered(groupPage([group]));

    expect(text('li.folder-line .fully-shared')).toEqual(['fully shared', 'fully shared']);
    expect(text('.fully-shared-note')).toEqual([FULLY_SHARED_NOTE]);
  });

  it('DFS-068.AC-4 shows no "any one can go" line for one fully shared folder, or none', async () => {
    const one = folderGroup(
      [groupFolder('D:/A', { sharedFiles: 1, folderFiles: 1 }), groupFolder('D:/B', { sharedFiles: 1, folderFiles: 4 })],
      [groupSet('a.bin')],
    );
    const two = folderGroup(
      [groupFolder('D:/C', { sharedFiles: 1, folderFiles: 3 }), groupFolder('D:/D', { sharedFiles: 1, folderFiles: 4 })],
      [groupSet('b.bin')],
    );
    const both = folderGroup(
      [groupFolder('D:/E', { sharedFiles: 1, folderFiles: 1 }), groupFolder('D:/F', { sharedFiles: 1, folderFiles: 1 })],
      [groupSet('c.bin')],
    );
    await renderAnswered(groupPage([one, two, both]));

    const groups = Array.from(page().querySelectorAll('div.group'));
    expect(groups.map((g) => g.querySelectorAll('.fully-shared').length)).toEqual([1, 0, 2]);
    expect(groups.map((g) => g.querySelector('.fully-shared-note') !== null)).toEqual([false, false, true]);
  });

  it('DFS-068.AC-3 shows "also in N other groups" only for a folder with otherGroups above 0', async () => {
    const group = folderGroup(
      [groupFolder('D:/A', { otherGroups: 2 }), groupFolder('D:/B', { otherGroups: 0 }), groupFolder('D:/C', { otherGroups: 1 })],
      [groupSet('a.bin')],
    );
    await renderAnswered(groupPage([group]));

    const lines = Array.from(page().querySelectorAll('li.folder-line'));
    expect(lines.map((line) => line.querySelector('.other-groups') === null ? null : norm(line.querySelector('.other-groups')))).toEqual([
      'also in 2 other groups',
      null,
      'also in 1 other group',
    ]);
  });

  it('DFS-068.AC-5 lists the sets of a group of 25 when it is expanded, 20 of them, then "and 5 more"', async () => {
    const sets = Array.from({ length: 20 }, (_, i) => groupSet(`file-${i}.bin`, 1048576, 2));
    const group = folderGroup([groupFolder('D:/A'), groupFolder('D:/B')], sets, { files: 25, totalSets: 25 });
    const small = folderGroup([groupFolder('D:/C'), groupFolder('D:/D')], [groupSet('only.bin', 2048, 3)]);
    await renderAnswered(groupPage([group, small]));

    expect(page().querySelectorAll('.group-set')).toHaveLength(0);
    const panels = await loader.getAllHarnesses(MatExpansionPanelHarness.with({ selector: '.group-sets' }));
    expect(panels).toHaveLength(2);
    expect(await panels[0].getTitle()).toBe('25 sets');
    await panels[0].expand();
    await step();

    const first = page().querySelectorAll('div.group')[0];
    expect(first.querySelectorAll('.group-set')).toHaveLength(20);
    expect(norm(first.querySelectorAll('.set-name')[0])).toBe('file-0.bin');
    expect(norm(first.querySelectorAll('.set-detail')[0])).toBe('1.0 MB · 2 copies');
    expect(norm(first.querySelector('.more-sets'))).toBe('and 5 more');

    await panels[1].expand();
    await step();
    const second = page().querySelectorAll('div.group')[1];
    expect(text('.group-set .set-name').slice(-1)).toEqual(['only.bin']);
    expect(norm(second.querySelector('.set-detail'))).toBe('2.0 KB · 3 copies');
    expect(second.querySelector('.more-sets')).toBeNull();
  });

  it('DFS-068.AC-6 requests /groups under the selected folder, and without it for "All folders"', async () => {
    await renderAnswered(groupPage([heaviest()]));

    fixture.componentRef.setInput('folder', 'D:/Media');
    await step();
    expectGroups(http, 's-1', 'D:/Media').flush(groupPage([heaviest()]));
    await step();
    expect(page().querySelectorAll('div.group')).toHaveLength(1);

    fixture.componentRef.setInput('folder', null);
    await step();
    expectGroups(http, 's-1', null).flush(groupPage([heaviest()]));
    await step();
  });

  it('DFS-068.AC-7 shows "Show 9 small groups", reloads with minBytes 0, then "Hide small groups" reloads without it', async () => {
    await renderAnswered(groupPage([heaviest()], { hiddenSmall: 9 }));

    expect(await loader.getAllHarnesses(MatButtonHarness.with({ text: 'Hide small groups' }))).toHaveLength(0);
    await (await loader.getHarness(MatButtonHarness.with({ text: 'Show 9 small groups' }))).click();
    await step();
    expectGroups(http, 's-1', null, 0).flush(groupPage([heaviest(), heaviest()], { hiddenSmall: 9 }));
    await step();
    expect(page().querySelectorAll('div.group')).toHaveLength(2);
    expect(await loader.getAllHarnesses(MatButtonHarness.with({ text: 'Show 9 small groups' }))).toHaveLength(0);

    await (await loader.getHarness(MatButtonHarness.with({ text: 'Hide small groups' }))).click();
    await step();
    expectGroups(http, 's-1', null, null).flush(groupPage([heaviest()], { hiddenSmall: 9 }));
    await step();
    expect(page().querySelectorAll('div.group')).toHaveLength(1);
    expect(await loader.getAllHarnesses(MatButtonHarness.with({ text: 'Show 9 small groups' }))).toHaveLength(1);
  });

  it('DFS-068.AC-7 shows no small-groups button when none are hidden', async () => {
    await renderAnswered(groupPage([heaviest()], { hiddenSmall: 0 }));

    expect(page().querySelectorAll('div.group')).toHaveLength(1);
    expect(page().querySelector('button.show-small')).toBeNull();
    expect(page().querySelector('button.hide-small')).toBeNull();
  });

  it('DFS-068.AC-7 keeps "Show N small groups" under an empty list', async () => {
    await renderAnswered(groupPage([], { totalItems: 0, hiddenSmall: 4 }));

    expect(text('.groups-empty')).toEqual(['No folders share files']);
    expect(await loader.getAllHarnesses(MatButtonHarness.with({ text: 'Show 4 small groups' }))).toHaveLength(1);
  });

  it('DFS-068.AC-8 tags "Photo.AUH/x" case-insensitively and leaves "Photos" and its lookalikes plain', async () => {
    const group = folderGroup(
      [
        groupFolder('D:/a/Photo.AUH/x'),
        groupFolder('D:/a/Photos'),
        groupFolder('D:/a/auh'),
        groupFolder('D:/a/Lightroom Catalog Previews.LRDATA'),
        groupFolder('D:/a/photoslibrary'),
      ],
      [groupSet('a.bin')],
    );
    await renderAnswered(groupPage([group]));

    const tagged = Array.from(page().querySelectorAll('li.folder-line')).map(
      (line) => line.querySelector('.app-tag') !== null,
    );
    expect(tagged).toEqual([true, false, false, true, false]);
    expect(page().querySelector('.app-tag')?.getAttribute('title')).toBe(APP_TITLE);
  });

  it('DFS-068.AC-8 isAppManaged matches the five patterns on any path element, ignoring letter case', () => {
    for (const path of [
      'D:/a/Photo.AUH/x',
      'D:\\a\\x.auh\\Contents',
      'D:/a/adobe premiere pro auto-save',
      'D:/a/Lib.LrData/Previews',
      'D:/a/My.PhotosLibrary',
      'D:/a/Project.FCPBUNDLE',
    ]) {
      expect(isAppManaged(path), path).toBe(true);
    }
    for (const path of ['D:/a/Photos', 'D:/a/auh', 'D:/a/x.auh.bak', 'D:/a/Adobe Premiere Pro Auto-Saves', 'D:/a/x']) {
      expect(isAppManaged(path), path).toBe(false);
    }
  });

  it('DFS-068.AC-9 shows no Folders table, Folder pairs table or fixed note, and calls neither /summaries nor /pairs', async () => {
    await renderAnswered(groupPage([heaviest()]));

    expect(page().querySelector('div.group')).not.toBeNull();
    expect(page().querySelector('.summaries-table')).toBeNull();
    expect(page().querySelector('section.summaries')).toBeNull();
    expect(page().querySelector('section.pairs')).toBeNull();
    expect(page().querySelector('.overlap-note')).toBeNull();
    expect(page().querySelector('.folder-header')).toBeNull();
    expect(norm(page())).not.toContain('Folder pairs');
    http.expectNone((r) => r.url.endsWith('/summaries') || r.url.endsWith('/pairs'));
  });

  it('DFS-050.AC-5 shows "Pinned: <path>" with an "Unpin" button that emits unpinned, and nothing once unpinned', async () => {
    await render(null, 'D:/Backup');
    expectGroups(http, 's-1').flush(groupPage([heaviest()]));
    await step();

    expect(text('.pinned-folder')).toEqual(['Pinned: D:/Backup']);
    const unpin = await loader.getHarness(MatButtonHarness.with({ text: 'Unpin' }));
    await unpin.click();
    await step();
    expect(unpinned).toBe(1);

    fixture.componentRef.setInput('pinned', 'C:/Photos');
    await step();
    expect(text('.pinned-folder')).toEqual(['Pinned: C:/Photos']);

    fixture.componentRef.setInput('pinned', null);
    await step();
    expect(page().querySelector('.pinned-folder')).toBeNull();
    expect(page().querySelector('section.comparison')).toBeNull();
  });

  it('DFS-068.AC-9, DFS-050.AC-6 requests /pair with a=<pinned> and b=<selected> and shows one pair row in the Comparison block', async () => {
    await render('C:/Photos', 'D:/Backup');
    expectGroups(http, 's-1', 'C:/Photos').flush(groupPage([heaviest()]));
    const req = expectPair(http, 's-1', 'D:/Backup', 'C:/Photos');
    expect(req.request.method).toBe('GET');
    req.flush(
      pair(
        side('C:/Photos', { filesWithCopy: 340, totalFiles: 3210, bytesWithCopy: 1288490188, full: false }),
        side('D:/Backup', { filesWithCopy: 2, totalFiles: 2, bytesWithCopy: 900, full: true }),
      ),
    );
    await step();

    expect(text('section.comparison h3')).toEqual(['Comparison']);
    expect(text('.comparison-pair .pair-side .side-text')).toEqual([
      'C:/Photos: 340 / 3210 files · 1.2 GB',
      'D:/Backup: 2 / 2 files · 900 B',
    ]);
    expect(text('.comparison-pair .pair-side .side-coverage')).toEqual(['partial', 'all have a copy in the other']);
    expect(page().querySelector('div.group')).not.toBeNull();
  });

  it('DFS-050.AC-7 sends no /pair for the pinned folder itself or "All folders" and shows the hint', async () => {
    await render('D:/Backup', 'D:/Backup');
    expectGroups(http, 's-1', 'D:/Backup').flush(groupPage([heaviest()]));
    await step();
    http.expectNone((r) => r.url.endsWith('/pair'));
    expect(text('.comparison-hint')).toEqual(['Select another folder to compare with the pinned one']);

    fixture.componentRef.setInput('folder', null);
    await step();
    expectGroups(http, 's-1', null).flush(groupPage([heaviest()]));
    await step();
    http.expectNone((r) => r.url.endsWith('/pair'));
    expect(text('.comparison-hint')).toEqual(['Select another folder to compare with the pinned one']);
  });

  it('DFS-050.AC-7 shows the server message when /pair answers 400 NESTED_FOLDERS', async () => {
    await render('C:/Photos/2019', 'C:/Photos');
    expectGroups(http, 's-1', 'C:/Photos/2019').flush(groupPage([heaviest()]));
    refuse(expectPair(http, 's-1', 'C:/Photos', 'C:/Photos/2019'), 400, {
      code: 'NESTED_FOLDERS',
      message: 'C:/Photos contains C:/Photos/2019',
    });
    await step();

    const alert = page().querySelector('.comparison-error');
    expect(alert?.getAttribute('role')).toBe('alert');
    expect(norm(alert)).toBe('C:/Photos contains C:/Photos/2019');
    expect(page().querySelector('.comparison-pair')).toBeNull();
  });

  it('DFS-050.AC-10 shows a progress bar while the comparison loads', async () => {
    await render('C:/Photos', 'D:/Backup');
    expectGroups(http, 's-1', 'C:/Photos').flush(groupPage([heaviest()]));
    const req = expectPair(http, 's-1', 'D:/Backup', 'C:/Photos');
    await step();

    const bars = await loader.getAllHarnesses(MatProgressBarHarness.with({ selector: '[aria-label="Loading the comparison"]' }));
    expect(bars).toHaveLength(1);

    req.flush(pair(side('C:/Photos'), side('D:/Backup')));
    await step();
    expect(
      await loader.getAllHarnesses(MatProgressBarHarness.with({ selector: '[aria-label="Loading the comparison"]' })),
    ).toHaveLength(0);
    expect(page().querySelector('.comparison-pair')).not.toBeNull();
  });
});

/** DFS-069: marking folder groups as dealt with. */
describe('OverlapsTab dealt with', () => {
  class FakeStorage implements DoneStorage {
    readonly data = new Map<string, string>();
    getItem(key: string): string | null {
      return this.data.get(key) ?? null;
    }
    setItem(key: string, value: string): void {
      this.data.set(key, value);
    }
  }

  const THROWING: DoneStorage = {
    getItem: () => {
      throw new Error('storage disabled');
    },
    setItem: () => {
      throw new Error('storage disabled');
    },
  };

  const DOT = '·';
  const KEY_S1 = 'dfs.results.done.s1';
  const KEY_G1 = '/a/x\n/b/x';
  const KEY_G2 = '/a/y\n/b/y';
  const KEY_G3 = '/a/z\n/c/z';

  let http: HttpTestingController;
  let fixture: ComponentFixture<OverlapsTab>;
  let loader: HarnessLoader;
  let storage: FakeStorage;

  async function configure(token: DoneStorage | null = storage): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [OverlapsTab],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideStubSurfacing(),
        { provide: DONE_STORAGE, useValue: token },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  }

  beforeEach(() => {
    storage = new FakeStorage();
  });

  afterEach(() => http.verify());

  function pairOf(letter: string, other = 'b'): FolderGroupView {
    return folderGroup(
      [groupFolder(`/a/${letter}`), groupFolder(`/${other}/${letter}`)],
      [groupSet(`${letter}.bin`)],
    );
  }

  /** G1 (/a/x, /b/x), G2 (/a/y, /b/y), G3 (/a/z, /c/z), as the ticket's Background. */
  function threeGroups(): FolderGroupView[] {
    return [pairOf('x'), pairOf('y'), pairOf('z', 'c')];
  }

  async function step(): Promise<void> {
    await settle(fixture);
  }

  /** Creates the tab for `scanId` and answers its first /groups request with `answer`. */
  async function open(
    answer: GroupPage,
    scanId = 's1',
    folder: string | null = null,
  ): Promise<void> {
    fixture = TestBed.createComponent(OverlapsTab);
    fixture.componentRef.setInput('scanId', scanId);
    fixture.componentRef.setInput('folder', folder);
    fixture.componentRef.setInput('roots', []);
    loader = TestbedHarnessEnvironment.loader(fixture);
    await settle(fixture);
    expectGroups(http, scanId, folder).flush(answer);
    await step();
  }

  function page(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function norm(el: Element | null | undefined): string {
    return (el?.textContent ?? '').replace(/\s+/g, ' ').trim();
  }

  /** The first folder path of each group card under `container`, e.g. ['/a/x', '/a/z']. */
  function firstFolders(container: string): (string | null | undefined)[] {
    return Array.from(page().querySelectorAll(`${container} > div.group`)).map((g) =>
      g.querySelector('.short-path')?.getAttribute('title'),
    );
  }

  async function tick(container: string, index: number): Promise<void> {
    const boxes = await loader.getAllHarnesses(
      MatCheckboxHarness.with({ selector: `${container} > div.group .group-done-check` }),
    );
    await boxes[index].check();
    await step();
  }

  async function untick(container: string, index: number): Promise<void> {
    const boxes = await loader.getAllHarnesses(
      MatCheckboxHarness.with({ selector: `${container} > div.group .group-done-check` }),
    );
    await boxes[index].uncheck();
    await step();
  }

  async function doneExpander() {
    return loader.getAllHarnesses(MatExpansionPanelHarness.with({ selector: '.done-groups' }));
  }

  it('DFS-069.AC-1 shows a "Mark as dealt with" checkbox in every header, ticking moves the group to Dealt with without a request', async () => {
    await configure();
    await open(groupPage(threeGroups()));

    const labels = await loader.getAllHarnesses(MatCheckboxHarness.with({ label: 'Mark as dealt with' }));
    expect(labels).toHaveLength(3);
    expect(page().querySelectorAll('div.group-header > mat-checkbox')).toHaveLength(3);

    await tick('.open-groups', 1);

    expect(firstFolders('.open-groups')).toEqual(['/a/x', '/a/z']);
    expect(firstFolders('.done-list')).toEqual(['/a/y']);
    const panels = await doneExpander();
    expect(panels).toHaveLength(1);
    expect(await panels[0].getTitle()).toBe('Dealt with on this page (1)');
    http.expectNone((r) => r.url.endsWith('/groups'));
  });

  it('DFS-069.AC-2 keeps the Dealt with expander collapsed with the group muted and ticked, and unticking returns it in the server order', async () => {
    storage.data.set(KEY_S1, JSON.stringify([KEY_G2]));
    await configure();
    await open(groupPage(threeGroups()));

    const panels = await doneExpander();
    expect(panels).toHaveLength(1);
    expect(await panels[0].getTitle()).toBe('Dealt with on this page (1)');
    expect(await panels[0].isExpanded()).toBe(false);
    const muted = page().querySelectorAll('.done-list > div.group.group-done');
    expect(muted).toHaveLength(1);
    expect(page().querySelectorAll('.open-groups .group-done')).toHaveLength(0);
    const boxes = await loader.getAllHarnesses(
      MatCheckboxHarness.with({ selector: '.done-list > div.group .group-done-check' }),
    );
    expect(await boxes[0].isChecked()).toBe(true);

    await panels[0].expand();
    await step();
    await untick('.done-list', 0);

    expect(firstFolders('.open-groups')).toEqual(['/a/x', '/a/y', '/a/z']);
    expect(await doneExpander()).toHaveLength(0);
  });

  it('DFS-069.AC-2 shows no Dealt with expander when no group is marked', async () => {
    await configure();
    await open(groupPage(threeGroups()));

    expect(page().querySelectorAll('div.group')).toHaveLength(3);
    expect(page().querySelector('mat-expansion-panel.done-groups')).toBeNull();
    expect(await doneExpander()).toHaveLength(0);
  });

  it('DFS-069.AC-3 counts the groups left and dealt with', async () => {
    storage.data.set(KEY_S1, JSON.stringify([KEY_G1, KEY_G2]));
    await configure();
    await open(groupPage(threeGroups()));

    expect(norm(page().querySelector('p.groups-counts'))).toBe(`1 groups left ${DOT} 2 dealt with`);
  });

  it('DFS-069.AC-3 counts only the marks under the selected folder', async () => {
    storage.data.set(KEY_S1, JSON.stringify([KEY_G1, KEY_G3]));
    await configure();
    const [g1, g2] = threeGroups();
    await open(groupPage([g1, g2], { totalItems: 2 }), 's1', '/b');

    expect(norm(page().querySelector('p.groups-counts'))).toBe(`1 groups left ${DOT} 1 dealt with`);
  });

  it('DFS-069.AC-3 never shows a negative number of groups left', async () => {
    storage.data.set(KEY_S1, JSON.stringify(['/k/1', '/k/2', '/k/3', '/k/4', '/k/5']));
    await configure();
    await open(groupPage(threeGroups()));

    expect(norm(page().querySelector('p.groups-counts'))).toBe(`0 groups left ${DOT} 5 dealt with`);
  });

  it('DFS-069.AC-4 stores a mark per scan under dfs.results.done.<scanId> and finds it again after a reload', async () => {
    await configure();
    await open(groupPage(threeGroups()));

    await tick('.open-groups', 0);

    expect(JSON.parse(storage.data.get(KEY_S1) ?? 'null')).toEqual([KEY_G1]);

    fixture.destroy();
    await open(groupPage(threeGroups()));
    expect(firstFolders('.done-list')).toEqual(['/a/x']);
    expect(firstFolders('.open-groups')).toEqual(['/a/y', '/a/z']);

    fixture.destroy();
    await open(groupPage(threeGroups()), 's2');
    expect(firstFolders('.open-groups')).toEqual(['/a/x', '/a/y', '/a/z']);
    expect(page().querySelector('mat-expansion-panel.done-groups')).toBeNull();
  });

  it('DFS-069.AC-4 recognises a group whatever order the answer lists its folders in', async () => {
    storage.data.set(KEY_S1, JSON.stringify([KEY_G1]));
    await configure();
    const reversed = folderGroup(
      [groupFolder('/b/x'), groupFolder('/a/x')],
      [groupSet('x.bin')],
    );
    await open(groupPage([reversed, pairOf('y')]));

    expect(firstFolders('.done-list')).toEqual(['/b/x']);
    expect(firstFolders('.open-groups')).toEqual(['/a/y']);
  });

  it('DFS-069.AC-5 keeps marks across paging, a folder selection and the small-groups toggle, with the server figures', async () => {
    storage.data.set(KEY_S1, JSON.stringify([KEY_G1]));
    await configure();
    const answer = () => groupPage([pairOf('x'), pairOf('y')], { totalItems: 30, hiddenSmall: 4 });
    await open(answer());

    const pager = await loader.getHarness(MatPaginatorHarness.with({ selector: '.groups-paginator' }));
    expect(await pager.getRangeLabel()).toContain('of 30');
    expect(firstFolders('.done-list')).toEqual(['/a/x']);

    await pager.goToNextPage();
    await step();
    expectGroups(http, 's1', null, null, 1, 10).flush({ ...answer(), page: 1 });
    await step();
    expect(firstFolders('.done-list')).toEqual(['/a/x']);
    await pager.goToPreviousPage();
    await step();
    expectGroups(http, 's1', null, null, 0, 10).flush(answer());
    await step();
    expect(firstFolders('.done-list')).toEqual(['/a/x']);

    fixture.componentRef.setInput('folder', '/a');
    await step();
    expectGroups(http, 's1', '/a').flush(answer());
    await step();
    expect(firstFolders('.done-list')).toEqual(['/a/x']);
    fixture.componentRef.setInput('folder', null);
    await step();
    expectGroups(http, 's1', null).flush(answer());
    await step();
    expect(firstFolders('.done-list')).toEqual(['/a/x']);

    await (await loader.getHarness(MatButtonHarness.with({ text: 'Show 4 small groups' }))).click();
    await step();
    expectGroups(http, 's1', null, 0).flush(answer());
    await step();
    expect(firstFolders('.done-list')).toEqual(['/a/x']);
    await (await loader.getHarness(MatButtonHarness.with({ text: 'Hide small groups' }))).click();
    await step();
    expectGroups(http, 's1', null, null).flush(answer());
    await step();
    expect(firstFolders('.done-list')).toEqual(['/a/x']);

    expect(await pager.getRangeLabel()).toContain('of 30');
    expect(await loader.getAllHarnesses(MatButtonHarness.with({ text: 'Show 4 small groups' }))).toHaveLength(1);
    expect(norm(page().querySelector('p.groups-counts'))).toBe(`29 groups left ${DOT} 1 dealt with`);
  });

  it('DFS-069.AC-6 marks groups for the visit when the browser has no storage', async () => {
    await configure(null);
    await open(groupPage(threeGroups()));

    await tick('.open-groups', 0);

    expect(firstFolders('.done-list')).toEqual(['/a/x']);
    expect(firstFolders('.open-groups')).toEqual(['/a/y', '/a/z']);
    expect(storage.data.size).toBe(0);
  });

  it('DFS-069.AC-6 loads and marks groups when the storage throws', async () => {
    await configure(THROWING);
    await open(groupPage(threeGroups()));

    expect(page().querySelectorAll('.open-groups > div.group')).toHaveLength(3);
    await tick('.open-groups', 0);

    expect(firstFolders('.done-list')).toEqual(['/a/x']);
  });

  it('DFS-069.AC-6 treats a stored value that is not a JSON array of strings as no marks', async () => {
    await configure();
    for (const value of ['{not json', '{"a":1}', '[1,2]']) {
      storage.data.set(KEY_S1, value);
      await open(groupPage(threeGroups()));

      expect(page().querySelectorAll('.open-groups > div.group'), value).toHaveLength(3);
      expect(norm(page().querySelector('p.groups-counts')), value).toBe(`3 groups left ${DOT} 0 dealt with`);
      fixture.destroy();
    }
  });

  it('DFS-069.AC-7 says all groups on the page are dealt with and keeps the paginator enabled', async () => {
    storage.data.set(KEY_S1, JSON.stringify([KEY_G1, KEY_G2, KEY_G3]));
    await configure();
    await open(groupPage(threeGroups(), { totalItems: 30 }));

    expect(norm(page().querySelector('p.all-done-note'))).toBe('All groups on this page are dealt with.');
    expect(page().querySelectorAll('.open-groups > div.group')).toHaveLength(0);
    const pager = await loader.getHarness(MatPaginatorHarness.with({ selector: '.groups-paginator' }));
    expect(await pager.isNextPageDisabled()).toBe(false);
    const panels = await doneExpander();
    expect(await panels[0].getTitle()).toBe('Dealt with on this page (3)');
    expect(firstFolders('.done-list')).toEqual(['/a/x', '/a/y', '/a/z']);
  });
});
