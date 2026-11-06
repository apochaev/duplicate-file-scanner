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
import { FolderTree } from './folder-tree';
import { expectFolders, folder, refuse } from './result-test-data';

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

  function labels(): string[] {
    return Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button.folder-select')).map((el) =>
      (el.textContent ?? '').replace(/\s+/g, ' ').trim(),
    );
  }

  async function tree(): Promise<MatTreeHarness> {
    return loader.getHarness(MatTreeHarness);
  }

  it('DFS-049.AC-5 shows the roots as "<name> · <size> · <n> files" in the order given, without asking for children', async () => {
    await render([photos, backup]);

    expect(labels()).toEqual(['Photos · 12.0 GB · 3210 files', 'Backup · 900 B · 2 files']);
    http.expectNone((req) => req.params.has('parent'));
  });

  it('DFS-049.AC-5 expands only a node with children, requesting them once with its path as parent', async () => {
    await render([photos, backup]);
    const nodes = await (await tree()).getNodes();
    expect(await nodes[0].isExpandable()).toBe(true);
    expect(await nodes[1].isExpandable()).toBe(false);

    await nodes[0].expand();
    await settle(fixture);
    const req = expectFolders(http, 's-1', 'C:\\Photos');
    req.flush([folder('C:\\Photos\\2024', { bytes: 2048, files: 7 }), folder('C:\\Photos\\2025', { bytes: 100, files: 3 })]);
    await settle(fixture);

    expect(labels()).toEqual([
      'Photos · 12.0 GB · 3210 files',
      '2024 · 2.0 KB · 7 files',
      '2025 · 100 B · 3 files',
      'Backup · 900 B · 2 files',
    ]);
    const after = await (await tree()).getNodes();
    expect(await after[1].getLevel()).toBe((await after[0].getLevel()) + 1);
  });

  it('DFS-049.AC-5 does not ask again when a node is collapsed and expanded again', async () => {
    await render([photos]);
    let nodes = await (await tree()).getNodes();
    await nodes[0].expand();
    await settle(fixture);
    expectFolders(http, 's-1', 'C:\\Photos').flush([folder('C:\\Photos\\2024')]);
    await settle(fixture);

    nodes = await (await tree()).getNodes();
    await nodes[0].collapse();
    await settle(fixture);
    expect(labels()).toEqual(['Photos · 12.0 GB · 3210 files']);

    nodes = await (await tree()).getNodes();
    await nodes[0].expand();
    await settle(fixture);
    expect(labels()).toHaveLength(2);
    http.expectNone((req) => req.params.has('parent'));
  });

  it('DFS-049.AC-10 shows a progress bar while a level loads and the server message when it fails', async () => {
    await render([photos]);
    const nodes = await (await tree()).getNodes();
    await nodes[0].expand();
    await settle(fixture);

    const bar = await loader.getHarness(MatProgressBarHarness.with({ selector: '[aria-label="Loading subfolders"]' }));
    expect(bar).toBeTruthy();

    refuse(expectFolders(http, 's-1', 'C:\\Photos'), 400, { code: 'RELATIVE_PATH', message: 'The path is not absolute' });
    await settle(fixture);

    const alert = (fixture.nativeElement as HTMLElement).querySelector('.folder-error');
    expect(alert?.getAttribute('role')).toBe('alert');
    expect(alert?.textContent?.trim()).toBe('The path is not absolute');
    expect(await loader.getAllHarnesses(MatProgressBarHarness)).toHaveLength(0);
  });

  it('DFS-049.AC-6 emits the folder path on selecting a node and null on "All folders"', async () => {
    await render([photos, backup], 'D:\\Backup');

    const all = await loader.getHarness(MatButtonHarness.with({ text: 'All folders' }));
    await all.click();
    await settle(fixture);
    const photosButton = await loader.getHarness(MatButtonHarness.with({ text: /^Photos/ }));
    await photosButton.click();
    await settle(fixture);

    expect(selections).toEqual([null, 'C:\\Photos']);
  });

  it('DFS-049.AC-6 shows "All folders" selected on opening and the selected folder pressed', async () => {
    await render([photos, backup], null);
    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('button.all-folders')?.getAttribute('aria-pressed')).toBe('true');
    expect(Array.from(root.querySelectorAll('button.folder-select')).map((b) => b.getAttribute('aria-pressed'))).toEqual([
      'false',
      'false',
    ]);

    fixture.componentRef.setInput('selected', 'D:\\Backup');
    await settle(fixture);
    expect(root.querySelector('button.all-folders')?.getAttribute('aria-pressed')).toBe('false');
    expect(Array.from(root.querySelectorAll('button.folder-select')).map((b) => b.getAttribute('aria-pressed'))).toEqual([
      'false',
      'true',
    ]);
  });

  it('DFS-050.AC-5 offers "Pin for comparison" on every folder node but not on "All folders", and emits the folder pinned', async () => {
    const pinned: string[] = [];
    await render([photos, backup]);
    fixture.componentInstance.folderPinned.subscribe((path) => pinned.push(path));

    const pins = await loader.getAllHarnesses(MatButtonHarness.with({ selector: '.folder-pin' }));
    expect(pins).toHaveLength(2);
    expect(await pins[0].getText()).toBe('Pin for comparison');
    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('button.all-folders')?.textContent).not.toContain('Pin');
    expect(root.querySelectorAll('.folder-pin')).toHaveLength(labels().length);

    await pins[1].click();
    await settle(fixture);
    expect(pinned).toEqual(['D:\\Backup']);
    expect(selections).toEqual([]);
  });
});
