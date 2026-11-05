import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { HarnessLoader } from '@angular/cdk/testing';
import { MatButtonHarness } from '@angular/material/button/testing';
import { provideStubSurfacing, settle } from '../../testing/stub-surfacing';
import { CLIPBOARD, ClipboardWriter } from './clipboard';
import { PathCopy } from './path-copy';

/** A clipboard whose write the test resolves or rejects itself. */
class FakeClipboard implements ClipboardWriter {
  readonly written: string[] = [];
  private pending: { resolve: () => void; reject: (reason: unknown) => void } | null = null;

  writeText(text: string): Promise<void> {
    this.written.push(text);
    return new Promise<void>((resolve, reject) => {
      this.pending = { resolve, reject };
    });
  }

  accept(): void {
    this.pending?.resolve();
  }

  refuse(): void {
    this.pending?.reject(new DOMException('denied', 'NotAllowedError'));
  }
}

describe('PathCopy', () => {
  let fixture: ComponentFixture<PathCopy>;
  let loader: HarnessLoader;

  async function render(path: string, clipboard: ClipboardWriter | null): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [PathCopy],
      providers: [provideStubSurfacing(), { provide: CLIPBOARD, useValue: clipboard }],
    }).compileComponents();
    fixture = TestBed.createComponent(PathCopy);
    fixture.componentRef.setInput('path', path);
    loader = TestbedHarnessEnvironment.loader(fixture);
    await settle(fixture);
  }

  async function press(): Promise<void> {
    const button = await loader.getHarness(MatButtonHarness.with({ text: 'Copy folder path' }));
    await button.click();
    await settle(fixture);
  }

  function page(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  it.each([
    ['C:\\Photos\\a.jpg', 'C:\\Photos'],
    ['C:\\a.jpg', 'C:\\'],
    ['\\\\nas\\share\\a.jpg', '\\\\nas\\share\\'],
    ['/photos/a.jpg', '/photos'],
    ['/a.jpg', '/'],
    ['C:\\Family Photos\\été\\a.jpg', 'C:\\Family Photos\\été'],
  ])('DFS-049.AC-11 copies the folder of %s as exactly %s and announces it after the write succeeds', async (file, folder) => {
    const clipboard = new FakeClipboard();
    await render(file, clipboard);

    await press();
    expect(clipboard.written).toEqual([folder]);
    // Not yet: the write has not resolved.
    expect(page().querySelector('.copy-success')).toBeNull();

    clipboard.accept();
    await settle(fixture);
    expect(page().querySelector('.copy-success')?.textContent?.trim()).toBe('Folder path copied');
    expect(page().querySelector('.copy-success')?.getAttribute('role')).toBe('status');
    expect(page().querySelector('.copy-error')).toBeNull();
  });

  it('DFS-049.AC-11 copies the alias folder of a hard link, not the other link', async () => {
    const clipboard = new FakeClipboard();
    await render('D:\\Aliases\\a.jpg', clipboard);

    await press();
    expect(clipboard.written).toEqual(['D:\\Aliases']);
  });

  it('DFS-049.AC-11 shows an error and the selectable folder when the clipboard is unavailable', async () => {
    await render('C:\\Photos\\a.jpg', null);

    await press();
    const error = page().querySelector('.copy-error');
    expect(error?.getAttribute('role')).toBe('alert');
    expect(error?.textContent?.trim()).toBe(
      'Clipboard access is not available; select the folder path below and copy it',
    );
    expect(page().querySelector('.copy-fallback')?.textContent?.trim()).toBe('C:\\Photos');
    expect(page().querySelector('.copy-success')).toBeNull();
  });

  it('DFS-049.AC-11 shows an error and the selectable folder when the write is rejected', async () => {
    const clipboard = new FakeClipboard();
    await render('C:\\Photos\\a.jpg', clipboard);

    await press();
    clipboard.refuse();
    await settle(fixture);

    const error = page().querySelector('.copy-error');
    expect(error?.getAttribute('role')).toBe('alert');
    expect(error?.textContent?.trim()).toBe('The folder path could not be copied; select it below and copy it');
    expect(page().querySelector('.copy-fallback')?.textContent?.trim()).toBe('C:\\Photos');
    expect(page().querySelector('.copy-success')).toBeNull();
  });
});
