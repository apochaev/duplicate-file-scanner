import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { App } from './app';

describe('App', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  /** Renders the page, answers its GET /api/session, and returns the page text afterwards. */
  async function render(answer: (req: TestRequest) => void): Promise<string> {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    answer(http.expectOne('/api/session'));
    // The generated SessionService returns a Promise: let it settle before change detection (DFS-038).
    await new Promise((resolve) => setTimeout(resolve));
    await fixture.whenStable();
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  it('DFS-037.AC-3 shows the heading and "Connected" when /api/session answers 200', async () => {
    const text = await render((req) => req.flush({ authenticated: true }));
    // Connected, the page also shows the scan screen, which lists the scans (DFS-041).
    http.expectOne('/api/scans').flush([]);

    expect(text).toContain('DFS server is running');
    expect(text).toContain('Connected');
  });

  it('DFS-041.AC-10 shows the scan screen once connected', async () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    http.expectOne('/api/session').flush({ authenticated: true });
    await new Promise((resolve) => setTimeout(resolve));
    await fixture.whenStable();
    fixture.detectChanges();
    http.expectOne('/api/scans').flush([]);
    await new Promise((resolve) => setTimeout(resolve));
    await fixture.whenStable();
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('app-scan-screen')).not.toBeNull();
  });

  it.each([
    ['403', (req: TestRequest) => req.flush('', { status: 403, statusText: 'Forbidden' })],
    ['200 authenticated false', (req: TestRequest) => req.flush({ authenticated: false })],
  ])('DFS-041.AC-10 shows no scan screen and calls no scan endpoint when /api/session answers %s', async (_, answer) => {
    const text = await render(answer);

    expect(text).toContain('Open the link printed in the console');
    expect(text).not.toContain('Start');
    http.expectNone('/api/scans');
  });

  it('DFS-037.AC-3 asks for the console link when /api/session answers 403', async () => {
    const text = await render((req) => req.flush('', { status: 403, statusText: 'Forbidden' }));

    expect(text).toContain('DFS server is running');
    expect(text).toContain('Open the link printed in the console');
    expect(text).not.toContain('Connected');
  });

  it('DFS-038.AC-5 does not show "Connected" when /api/session answers authenticated: false', async () => {
    const text = await render((req) => req.flush({ authenticated: false }));

    expect(text).toContain('Open the link printed in the console');
    expect(text).not.toContain('Connected');
  });
});
