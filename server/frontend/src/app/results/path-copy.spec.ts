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
    const button = await loader.getHarness(MatButtonHarness.with({ text: 'Copy' }));
    await button.click();
    await settle(fixture);
  }

  function page(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  it.each([
    'C:\\Archive\\Backups\\MacBook-2020\\Users\\me\\Pictures\\Iceland 2019\\pano.tif',
    'C:\\a.jpg',
    '\\\\nas\\share\\a.jpg',
    '/photos/a.jpg',
    'C:\\Family Photos\\été\\a.jpg',
  ])('DFS-060.AC-7 copies the full path %s exactly and announces it after the write succeeds', async (file) => {
    const clipboard = new FakeClipboard();
    await render(file, clipboard);

    await press();
    expect(clipboard.written).toEqual([file]);
    // Not yet: the write has not resolved.
    expect(page().querySelector('.copy-success')).toBeNull();

    clipboard.accept();
    await settle(fixture);
    expect(page().querySelector('.copy-success')?.textContent?.trim()).toBe('Copied');
    expect(page().querySelector('.copy-success')?.getAttribute('role')).toBe('status');
    expect(page().querySelector('.copy-error')).toBeNull();
  });

  it('DFS-060.AC-7 labels the button "Copy path of <path>"', async () => {
    await render('C:\\Photos\\a.jpg', new FakeClipboard());
    await press();

    expect(page().querySelector('button.copy-path-button')?.getAttribute('aria-label')).toBe(
      'Copy path of C:\\Photos\\a.jpg',
    );
  });

  it('DFS-060.AC-7 shows an error and the selectable path when the clipboard is unavailable', async () => {
    await render('C:\\Photos\\a.jpg', null);

    await press();
    const error = page().querySelector('.copy-error');
    expect(error?.getAttribute('role')).toBe('alert');
    expect(error?.textContent?.trim()).toBe('Clipboard access is not available; select the path below and copy it');
    expect(page().querySelector('.copy-fallback')?.textContent?.trim()).toBe('C:\\Photos\\a.jpg');
    expect(page().querySelector('.copy-success')).toBeNull();
  });

  it('DFS-060.AC-7 shows an error and the selectable path when the write is rejected', async () => {
    const clipboard = new FakeClipboard();
    await render('C:\\Photos\\a.jpg', clipboard);

    await press();
    clipboard.refuse();
    await settle(fixture);

    const error = page().querySelector('.copy-error');
    expect(error?.getAttribute('role')).toBe('alert');
    expect(error?.textContent?.trim()).toBe('The path could not be copied; select it below and copy it');
    expect(page().querySelector('.copy-fallback')?.textContent?.trim()).toBe('C:\\Photos\\a.jpg');
    expect(page().querySelector('.copy-success')).toBeNull();
  });
});
