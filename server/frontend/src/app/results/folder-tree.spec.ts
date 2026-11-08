import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { MatButtonHarness } from '@angular/material/button/testing';
import { MatProgressBarHarness } from '@angular/material/progress-bar/testing';
import { MatTreeHarness } from '@angular/material/tree/testing';
import { FolderNode } from '../api/models';
import { provideStubSurfacing, settle } from '../../testing/stub-surfacing';
import { FolderTree, sortByDuplicateSpace } from './folder-tree';
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

describe('FolderTree', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<FolderTree>;
  let loader: HarnessLoader;
  let selections: (string | null)[];

  const photos = folder('C:\\Photos', { bytes: 12884901888, files: 3210, hasChildren: true });
  const backup = folder('D:\\Backup', { bytes: 900, files: 2, hasChildren: false });

  beforeEach(async () => {
    selections = [];
    await TestBed.configureTestingModule({
      imports: [FolderTree],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideStubSurfacing()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function render(roots: FolderNode[], selected: string | null = null): Promise<void> {
    fixture = TestBed.createComponent(FolderTree);
    fixture.componentRef.setInput('scanId', 's-1');
    fixture.componentRef.setInput('roots', roots);
    fixture.componentRef.setInput('selected', selected);
    fixture.componentInstance.folderSelected.subscribe((path) => selections.push(path));
    loader = TestbedHarnessEnvironment.loader(fixture);
    await settle(fixture);
  }

  function root(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  /** The folder names of the visible rows, in order. */
  function names(): string[] {
    return Array.from(root().querySelectorAll('button.folder-select .folder-name')).map((el) =>
      (el.textContent ?? '').trim(),
    );
  }

  function selectButton(name: string): HTMLButtonElement {
    const found = Array.from(root().querySelectorAll<HTMLButtonElement>('button.folder-select')).find(
      (button) => (button.querySelector('.folder-name')?.textContent ?? '').trim() === name,
    );
    if (!found) throw new Error(`no folder row named ${name}`);
    return found;
  }

  async function tree(): Promise<MatTreeHarness> {
    return loader.getHarness(MatTreeHarness);
  }

  it('DFS-060.AC-4 shows the roots by duplicate space then name, without asking for children', async () => {
    await render([photos, backup]);

    expect(names()).toEqual(['Backup', 'Photos']);
    http.expectNone((req) => req.params.has('parent'));
  });

  it('DFS-060.AC-4 shows a row as name, bar, size and duplicate size, the folder path as its tooltip', async () => {
    const archive = folder('C:\\Archive', { bytes: 76235669504, duplicateBytes: 40802189312 });
    await render([archive]);

    const row = selectButton('Archive');
    expect(row.querySelector('.folder-size')?.textContent?.trim()).toBe('71.0 GB');
    expect(row.querySelector('.folder-duplicate-size')?.textContent?.trim()).toBe('38.0 GB');
    expect(row.getAttribute('title')).toBe('C:\\Archive');
    const fill = row.querySelector<HTMLElement>('.folder-bar .folder-bar-fill');
    expect(parseFloat(fill?.style.width ?? '')).toBeCloseTo(53.5, 1);
  });

  it('DFS-060.AC-4 lists a folder\'s children by duplicate space, largest first, then by name', async () => {
    const archive = folder('C:\\Archive', { bytes: 145921509491, duplicateBytes: 63350767616, hasChildren: true });
    await render([archive]);
    const nodes = await (await tree()).getNodes();
    await nodes[0].expand();
    await settle(fixture);

    expectFolders(http, 's-1', 'C:\\Archive').flush([
      folder('C:\\Archive\\Documents', { bytes: 1288490188, duplicateBytes: 0 }),
      folder('C:\\Archive\\Backups', { bytes: 76235669504, duplicateBytes: 40802189312 }),
      folder('C:\\Archive\\Photos', { bytes: 65712999219, duplicateBytes: 22548578304 }),
      folder('C:\\Archive\\Music', { bytes: 2684354560, duplicateBytes: 0 }),
    ]);
    await settle(fixture);

    expect(names()).toEqual(['Archive', 'Backups', 'Photos', 'Documents', 'Music']);
    const backups = selectButton('Backups');
    expect(parseFloat(backups.querySelector<HTMLElement>('.folder-bar-fill')?.style.width ?? '')).toBeCloseTo(53.5, 1);
    expect(backups.querySelector('.folder-size')?.textContent?.trim()).toBe('71.0 GB');
    expect(backups.querySelector('.folder-duplicate-size')?.textContent?.trim()).toBe('38.0 GB');
    expect(parseFloat(selectButton('Music').querySelector<HTMLElement>('.folder-bar-fill')?.style.width ?? '')).toBe(0);
    expect(selectButton('Music').querySelector('.folder-duplicate-size')?.textContent?.trim()).toBe('0 B');
  });

  it('DFS-060.AC-4 expands only a node with children, requesting them once with its path as parent', async () => {
    await render([photos, backup]);
    const nodes = await (await tree()).getNodes();
    expect(await nodes[0].isExpandable()).toBe(false);
    expect(await nodes[1].isExpandable()).toBe(true);

    await nodes[1].expand();
    await settle(fixture);
    const req = expectFolders(http, 's-1', 'C:\\Photos');
    req.flush([folder('C:\\Photos\\2024', { bytes: 2048, files: 7 }), folder('C:\\Photos\\2025', { bytes: 100, files: 3 })]);
    await settle(fixture);

    expect(names()).toEqual(['Backup', 'Photos', '2024', '2025']);
    const after = await (await tree()).getNodes();
    expect(await after[2].getLevel()).toBe((await after[1].getLevel()) + 1);
  });

  it('DFS-060.AC-4 does not ask again when a node is collapsed and expanded again', async () => {
    await render([photos]);
    let nodes = await (await tree()).getNodes();
    await nodes[0].expand();
    await settle(fixture);
    expectFolders(http, 's-1', 'C:\\Photos').flush([folder('C:\\Photos\\2024')]);
    await settle(fixture);

    nodes = await (await tree()).getNodes();
    await nodes[0].collapse();
    await settle(fixture);
    expect(names()).toEqual(['Photos']);

    nodes = await (await tree()).getNodes();
    await nodes[0].expand();
    await settle(fixture);
    expect(names()).toHaveLength(2);
    http.expectNone((req) => req.params.has('parent'));
  });

  it('DFS-060.AC-10 shows a progress bar while a level loads and the server message when it fails', async () => {
    await render([photos]);
    const nodes = await (await tree()).getNodes();
    await nodes[0].expand();
    await settle(fixture);

    const bar = await loader.getHarness(MatProgressBarHarness.with({ selector: '[aria-label="Loading subfolders"]' }));
    expect(bar).toBeTruthy();

    refuse(expectFolders(http, 's-1', 'C:\\Photos'), 400, { code: 'RELATIVE_PATH', message: 'The path is not absolute' });
    await settle(fixture);

    const alert = root().querySelector('.folder-error');
    expect(alert?.getAttribute('role')).toBe('alert');
    expect(alert?.textContent?.trim()).toBe('The path is not absolute');
    expect(await loader.getAllHarnesses(MatProgressBarHarness)).toHaveLength(0);
  });

  it('DFS-060.AC-4 emits the folder path on selecting a node and null on "All folders"', async () => {
    await render([photos, backup], 'D:\\Backup');

    const all = await loader.getHarness(MatButtonHarness.with({ text: 'All folders' }));
    await all.click();
    await settle(fixture);
    selectButton('Photos').click();
    await settle(fixture);

    expect(selections).toEqual([null, 'C:\\Photos']);
  });

  it('DFS-060.AC-4 shows "All folders" selected on opening and the selected folder pressed', async () => {
    await render([photos, backup], null);
    const pressed = () =>
      Array.from(root().querySelectorAll('button.folder-select')).map((b) => b.getAttribute('aria-pressed'));
    expect(root().querySelector('button.all-folders')?.getAttribute('aria-pressed')).toBe('true');
    expect(pressed()).toEqual(['false', 'false']);

    fixture.componentRef.setInput('selected', 'D:\\Backup');
    await settle(fixture);
    expect(root().querySelector('button.all-folders')?.getAttribute('aria-pressed')).toBe('false');
    expect(names()).toEqual(['Backup', 'Photos']);
    expect(pressed()).toEqual(['true', 'false']);
  });

  it('DFS-060.AC-4 offers "Pin for comparison" on every folder node but not on "All folders", and emits the folder pinned', async () => {
    const pinned: string[] = [];
    await render([photos, backup]);
    fixture.componentInstance.folderPinned.subscribe((path) => pinned.push(path));

    const pins = await loader.getAllHarnesses(MatButtonHarness.with({ selector: '.folder-pin' }));
    expect(pins).toHaveLength(2);
    expect(await pins[0].getText()).toBe('Pin for comparison');
    expect(root().querySelector('button.all-folders')?.textContent).not.toContain('Pin');
    expect(root().querySelectorAll('.folder-pin')).toHaveLength(names().length);

    const pinBackup = await loader.getHarness(MatButtonHarness.with({ selector: '[aria-label="Pin for comparison: Backup"]' }));
    await pinBackup.click();
    await settle(fixture);
    expect(pinned).toEqual(['D:\\Backup']);
    expect(selections).toEqual([]);
  });
});
