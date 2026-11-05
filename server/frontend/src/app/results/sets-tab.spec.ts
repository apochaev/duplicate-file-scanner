import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { PageEvent } from '@angular/material/paginator';
import { MatPaginatorHarness } from '@angular/material/paginator/testing';
import { SetPage } from '../api/models';
import { provideStubSurfacing, settle } from '../../testing/stub-surfacing';
import { CLIPBOARD } from './clipboard';
import { duplicateSet, setPage } from './result-test-data';
import { SetsTab } from './sets-tab';

describe('SetsTab', () => {
  let fixture: ComponentFixture<SetsTab>;
  let loader: HarnessLoader;
  let emitted: PageEvent[];

  beforeEach(async () => {
    emitted = [];
    await TestBed.configureTestingModule({
      imports: [SetsTab],
      providers: [provideStubSurfacing(), { provide: CLIPBOARD, useValue: null }],
    }).compileComponents();
  });

  async function render(
    page: SetPage | null,
    inputs: { folder?: string | null; pageIndex?: number; pageSize?: number } = {},
  ): Promise<void> {
    fixture = TestBed.createComponent(SetsTab);
    fixture.componentRef.setInput('page', page);
    for (const [name, value] of Object.entries(inputs)) {
      fixture.componentRef.setInput(name, value);
    }
    fixture.componentInstance.pageChanged.subscribe((event) => emitted.push(event));
    loader = TestbedHarnessEnvironment.loader(fixture);
    await settle(fixture);
  }

  function root(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function texts(selector: string): string[] {
    return Array.from(root().querySelectorAll(selector)).map((el) => (el.textContent ?? '').replace(/\s+/g, ' ').trim());
  }

  const hardLinkSet = duplicateSet([['C:\\Photos\\a.jpg', 'C:\\Photos\\link-to-a.jpg'], ['D:\\Backup\\a.jpg']]);

  it('DFS-049.AC-7 shows a card per set in the given order, headed by copies, size and reclaimable', async () => {
    const small = duplicateSet([['C:\\x\\b.txt'], ['D:\\x\\b.txt'], ['E:\\x\\b.txt']], 1024, 'D2');
    await render(setPage([hardLinkSet, small], { totalReclaimableBytes: 2097152 + 2048 }));

    expect(texts('mat-card.duplicate-set .set-heading')).toEqual([
      '2 copies · 2.0 MB each · 2.0 MB reclaimable',
      '3 copies · 1.0 KB each · 2.0 KB reclaimable',
    ]);
  });

  it('DFS-049.AC-7 lists one line per physical copy with its date and indents the hard link beneath its copy', async () => {
    await render(setPage([hardLinkSet]));

    const copies = Array.from(root().querySelectorAll('li.physical-copy'));
    expect(copies).toHaveLength(2);
    const first = copies[0];
    expect(first.querySelectorAll('.file-link.copy-line')).toHaveLength(1);
    expect(first.querySelector('.copy-line .file-path')?.textContent?.trim()).toBe('C:\\Photos\\a.jpg');
    expect(first.querySelector('.copy-line time')?.textContent).toContain('Oct 1, 2026');
    expect(first.querySelector('.copy-line time')?.getAttribute('datetime')).toBe('2026-10-01T12:00:00Z');
    expect(first.querySelectorAll('.file-link.hard-link')).toHaveLength(1);
    expect(first.querySelector('.hard-link .file-path')?.textContent?.trim()).toBe('C:\\Photos\\link-to-a.jpg');
    expect(first.querySelector('.hard-link .hard-link-label')?.textContent?.trim()).toBe('hard link (same file)');
    // The copy line itself is not marked as a hard link.
    expect(first.querySelector('.copy-line .hard-link-label')).toBeNull();

    const second = copies[1];
    expect(second.querySelector('.copy-line .file-path')?.textContent?.trim()).toBe('D:\\Backup\\a.jpg');
    expect(second.querySelectorAll('.hard-link')).toHaveLength(0);
    expect(root().querySelectorAll('.hard-link-label')).toHaveLength(1);
  });

  it('DFS-049.AC-7 never shows the digest', async () => {
    await render(setPage([hardLinkSet]));

    expect(root().textContent).not.toContain('DIGEST-SECRET-0123');
  });

  it('DFS-049.AC-8 marks only the links under the selected folder', async () => {
    await render(setPage([hardLinkSet]), { folder: 'D:\\Backup' });

    const marked = Array.from(root().querySelectorAll('.file-link.in-selected-folder'));
    expect(marked.map((el) => el.querySelector('.file-path')?.textContent?.trim())).toEqual(['D:\\Backup\\a.jpg']);
    expect(marked[0].querySelector('.selected-label')?.textContent?.trim()).toBe('in selected folder');
    expect(root().querySelectorAll('.selected-label')).toHaveLength(1);
  });

  it('DFS-049.AC-8 does not treat a sibling with the same prefix as under the folder', async () => {
    const siblings = duplicateSet([['C:\\Photos\\a.jpg'], ['C:\\Photos2\\a.jpg']]);
    await render(setPage([siblings]), { folder: 'C:\\Photos' });

    const marked = Array.from(root().querySelectorAll('.file-link.in-selected-folder'));
    expect(marked.map((el) => el.querySelector('.file-path')?.textContent?.trim())).toEqual(['C:\\Photos\\a.jpg']);
  });

  it('DFS-049.AC-8 marks nothing with "All folders" selected', async () => {
    await render(setPage([hardLinkSet]), { folder: null });

    expect(root().querySelectorAll('.in-selected-folder')).toHaveLength(0);
    expect(root().querySelectorAll('.selected-label')).toHaveLength(0);
  });

  it('DFS-049.AC-9 shows the summary line and a paginator of size 50 with the choices 25, 50 and 100', async () => {
    await render(setPage([hardLinkSet], { totalItems: 120, totalReclaimableBytes: 5368709120 }));

    expect(texts('.sets-summary')).toEqual(['120 duplicate sets · 5.0 GB reclaimable']);
    const paginator = await loader.getHarness(MatPaginatorHarness);
    expect(await paginator.getPageSize()).toBe(50);
  });

  it('DFS-049.AC-9 emits the next page and a chosen page size', async () => {
    await render(setPage([hardLinkSet], { totalItems: 120, totalReclaimableBytes: 5368709120 }));
    const paginator = await loader.getHarness(MatPaginatorHarness);

    await paginator.goToNextPage();
    await settle(fixture);
    expect(emitted.map((e) => [e.pageIndex, e.pageSize])).toEqual([[1, 50]]);

    await paginator.setPageSize(100);
    await settle(fixture);
    expect(emitted[emitted.length - 1].pageSize).toBe(100);
  });

  it('DFS-049.AC-10 says the scan found no duplicates under "All folders" and shows no paginator', async () => {
    await render(setPage([], { totalItems: 0 }), { folder: null });

    expect(texts('.sets-empty')).toEqual(['This scan found no duplicates']);
    expect(root().querySelector('mat-paginator')).toBeNull();
    expect(root().querySelector('.sets-summary')).toBeNull();
  });

  it('DFS-049.AC-10 says there are no duplicates under a selected folder', async () => {
    await render(setPage([], { totalItems: 0 }), { folder: 'D:\\Backup' });

    expect(texts('.sets-empty')).toEqual(['No duplicates under this folder']);
    expect(root().querySelector('mat-paginator')).toBeNull();
  });

  it('DFS-049.AC-11 gives every link, hard link included, its own "Copy folder path" action', async () => {
    await render(setPage([hardLinkSet]));

    const actions = Array.from(root().querySelectorAll('app-path-copy button.copy-folder'));
    expect(actions.map((a) => a.getAttribute('aria-label'))).toEqual([
      'Copy folder path of C:\\Photos\\a.jpg',
      'Copy folder path of C:\\Photos\\link-to-a.jpg',
      'Copy folder path of D:\\Backup\\a.jpg',
    ]);
  });
});
