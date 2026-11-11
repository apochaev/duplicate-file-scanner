import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { MatButtonHarness } from '@angular/material/button/testing';
import { MatProgressBarHarness } from '@angular/material/progress-bar/testing';
import { FolderNode } from '../api/models';
import { provideStubSurfacing, settle } from '../../testing/stub-surfacing';
import { FOLDED_ROWS, FolderBrowser, crumbsFor, sortByDuplicateSpace } from './folder-browser';
import { expectFolders, folder, refuse } from './result-test-data';

describe('sortByDuplicateSpace', () => {
  it('DFS-060.AC-4 orders by duplicate bytes, largest first, then by name, without changing the input', () => {
    const documents = folder('C:\\Archive\\Documents', { bytes: 1288490188, duplicateBytes: 0 });
    const backups = folder('C:\\Archive\\Backups', { bytes: 76235669504, duplicateBytes: 40802189312 });
    const photos = folder('C:\\Archive\\Photos', { bytes: 65712999219, duplicateBytes: 22548578304 });
    const music = folder('C:\\Archive\\Music', { bytes: 2684354560, duplicateBytes: 0 });
    const input = [documents, backups, photos, music];

    const sorted = sortByDuplicateSpace(input);

    expect(sorted.map((f) => f.name)).toEqual(['Backups', 'Photos', 'Documents', 'Music']);
    expect(input.map((f) => f.name)).toEqual(['Documents', 'Backups', 'Photos', 'Music']);
  });

  it('DFS-060.AC-4 breaks a tie by name, numbers in order and letter case ignored, then by path', () => {
    const sorted = sortByDuplicateSpace([
      folder('C:\\x\\b10', { duplicateBytes: 5 }),
      folder('C:\\x\\B2', { duplicateBytes: 5 }),
      folder('C:\\y\\a', { duplicateBytes: 5 }),
      folder('C:\\x\\a', { duplicateBytes: 5 }),
    ]);

    expect(sorted.map((f) => f.path)).toEqual(['C:\\x\\a', 'C:\\y\\a', 'C:\\x\\B2', 'C:\\x\\b10']);
  });
});

describe('crumbsFor', () => {
  it('DFS-065.AC-5 leaves out the single root and names each folder below it', () => {
    expect(crumbsFor('C:\\Archive\\Backups\\old-drive', ['C:\\Archive'])).toEqual([
      { name: 'Backups', path: 'C:\\Archive\\Backups' },
      { name: 'old-drive', path: 'C:\\Archive\\Backups\\old-drive' },
    ]);
  });

  it('DFS-065.AC-5 starts with the root when there are several, a drive root by its whole path', () => {
    expect(crumbsFor('C:\\Photos\\2024', ['C:\\', 'D:\\Backup'])).toEqual([
      { name: 'C:\\', path: 'C:\\' },
      { name: 'Photos', path: 'C:\\Photos' },
      { name: '2024', path: 'C:\\Photos\\2024' },
    ]);
    expect(crumbsFor('D:\\Backup', ['C:\\Photos', 'D:\\Backup'])).toEqual([{ name: 'Backup', path: 'D:\\Backup' }]);
  });

  it('DFS-065.AC-5 gives no crumbs for a path under no root', () => {
    expect(crumbsFor('E:\\x', ['C:\\Photos'])).toEqual([]);
  });
});

describe('FolderBrowser', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<FolderBrowser>;
  let loader: HarnessLoader;
  let selections: (string | null)[];
  let pins: string[];

  const photos = folder('C:\\Photos', { bytes: 12884901888, files: 3210, hasChildren: true });
  const backup = folder('D:\\Backup', { bytes: 900, files: 2, hasChildren: false });
  const archive = folder('C:\\Archive', { bytes: 5000, files: 30, hasChildren: true, duplicateBytes: 3000 });
  const backups = folder('C:\\Archive\\Backups', {
    bytes: 3072,
    files: 20,
    hasChildren: true,
    duplicateBytes: 2048,
    duplicateFiles: 12,
  });
  const downloads = folder('C:\\Archive\\Downloads', { bytes: 2048, files: 10, duplicateBytes: 512 });

  beforeEach(async () => {
    selections = [];
    pins = [];
    await TestBed.configureTestingModule({
      imports: [FolderBrowser],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideStubSurfacing()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function render(roots: FolderNode[], selected: string | null = null): Promise<void> {
    fixture = TestBed.createComponent(FolderBrowser);
    fixture.componentRef.setInput('scanId', 's-1');
    fixture.componentRef.setInput('roots', roots);
    fixture.componentRef.setInput('selected', selected);
    fixture.componentInstance.folderSelected.subscribe((path) => selections.push(path));
    fixture.componentInstance.folderPinned.subscribe((path) => pins.push(path));
    loader = TestbedHarnessEnvironment.loader(fixture);
    await settle(fixture);
  }

  /** Renders the single-root Archive scan and answers its subfolders. */
  async function renderArchive(children: FolderNode[] = [downloads, backups]): Promise<void> {
    await render([archive]);
    expectFolders(http, 's-1', 'C:\\Archive').flush(children);
    await settle(fixture);
  }

  async function select(selected: string | null): Promise<void> {
    fixture.componentRef.setInput('selected', selected);
    await settle(fixture);
  }

  function root(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function text(selector: string): string[] {
    return Array.from(root().querySelectorAll(selector)).map((el) => (el.textContent ?? '').replace(/\s+/g, ' ').trim());
  }

  function names(): string[] {
    return text('button.folder-select .folder-name');
  }

  it('DFS-065.AC-3 lists several roots by duplicate space and requests nothing', async () => {
    await render([photos, backup]);

    expect(names()).toEqual(['Backup', 'Photos']);
    expect(root().querySelector('button.all-folders')?.getAttribute('aria-pressed')).toBe('true');
  });

  it('DFS-065.AC-3 opens a single root at its subfolders', async () => {
    await renderArchive();

    expect(names()).toEqual(['Backups', 'Downloads']);
    expect(text('.crumb')).toEqual(['All folders']);
  });

  it('DFS-065.AC-4 shows the name, a more marker, a bar and the four numbers of each row', async () => {
    await renderArchive();

    expect(root().querySelectorAll('.folder-more')).toHaveLength(1);
    expect(text('.folder-duplicate-size')).toEqual(['2.0 KB', '512 B']);
    expect(text('.folder-duplicate-files')[0]).toBe('12');
    expect(text('.folder-size')).toEqual(['3.0 KB', '2.0 KB']);
    expect(text('.folder-files')).toEqual(['20', '10']);
    const widths = Array.from(root().querySelectorAll<HTMLElement>('.folder-bar-fill')).map((el) => parseFloat(el.style.width));
    expect(widths[1]).toBeCloseTo(25);
  });

  it('DFS-065.AC-5 emits a row, a crumb and "All folders" as selections', async () => {
    await renderArchive();

    root().querySelector<HTMLButtonElement>('button.folder-select')!.click();
    expect(selections).toEqual(['C:\\Archive\\Backups']);

    await select('C:\\Archive\\Backups');
    expectFolders(http, 's-1', 'C:\\Archive\\Backups').flush([folder('C:\\Archive\\Backups\\old-drive', { bytes: 9 })]);
    await settle(fixture);
    expect(text('.crumb')).toEqual(['All folders', 'Backups']);
    expect(root().querySelector('.crumb-current')?.getAttribute('aria-current')).toBe('location');
    expect(text('.current-summary')).toEqual(['3.0 KB · 20 files · 2.0 KB in duplicates']);
    expect(names()).toEqual(['old-drive']);

    (root().querySelector('tr.folder-row') as HTMLElement).click();
    root().querySelector<HTMLButtonElement>('button.folder-crumb')!.click();
    root().querySelector<HTMLButtonElement>('button.all-folders')!.click();
    expect(selections).toEqual([
      'C:\\Archive\\Backups',
      'C:\\Archive\\Backups\\old-drive',
      'C:\\Archive\\Backups',
      null,
    ]);
  });

  it('DFS-065.AC-3 keeps a loaded level, so going back makes no new request', async () => {
    await renderArchive();
    await select('C:\\Archive\\Backups');
    expectFolders(http, 's-1', 'C:\\Archive\\Backups').flush([folder('C:\\Archive\\Backups\\old-drive')]);
    await settle(fixture);

    await select(null);
    expect(names()).toEqual(['Backups', 'Downloads']);
    await select('C:\\Archive\\Backups');

    expect(names()).toEqual(['old-drive']);
  });

  it('DFS-065.AC-7 shows "No subfolders" for a leaf without requesting it', async () => {
    await renderArchive();

    await select('C:\\Archive\\Downloads');

    expect(text('.no-subfolders')).toEqual(['No subfolders']);
    expect(text('.crumb')).toEqual(['All folders', 'Downloads']);
  });

  it('DFS-065.AC-7 shows a progress bar while a level loads and the server message when it fails', async () => {
    await render([archive]);

    const bars = await loader.getAllHarnesses(MatProgressBarHarness.with({ selector: '[aria-label="Loading subfolders"]' }));
    expect(bars).toHaveLength(1);
    refuse(expectFolders(http, 's-1', 'C:\\Archive'), 403, {
      code: 'PATH_OUTSIDE_ROOTS',
      message: 'The path is outside the scan',
    });
    await settle(fixture);

    const alert = root().querySelector('.folder-error');
    expect(alert?.getAttribute('role')).toBe('alert');
    expect(alert?.textContent?.trim()).toBe('The path is outside the scan');
    expect(root().querySelector('mat-progress-bar')).toBeNull();
  });

  it('DFS-065.AC-3 folds a long level to the first rows and shows the rest on request', async () => {
    const many = Array.from({ length: FOLDED_ROWS + 2 }, (_, i) => folder(`C:\\Archive\\f${i}`, { duplicateBytes: 100 - i }));
    await renderArchive(many);

    expect(names()).toHaveLength(FOLDED_ROWS);
    await (await loader.getHarness(MatButtonHarness.with({ text: `Show all ${FOLDED_ROWS + 2} folders` }))).click();
    await settle(fixture);
    expect(names()).toHaveLength(FOLDED_ROWS + 2);

    await (await loader.getHarness(MatButtonHarness.with({ text: 'Show fewer' }))).click();
    await settle(fixture);
    expect(names()).toHaveLength(FOLDED_ROWS);
  });

  it('DFS-065.AC-6 pins a row without selecting it', async () => {
    await render([photos, backup]);

    await (await loader.getHarness(MatButtonHarness.with({ selector: '[aria-label="Pin for comparison: Backup"]' }))).click();

    expect(pins).toEqual(['D:\\Backup']);
    expect(selections).toEqual([]);
  });
});
