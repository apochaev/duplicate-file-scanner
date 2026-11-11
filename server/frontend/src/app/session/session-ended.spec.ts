import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideLocationMocks } from '@angular/common/testing';
import { Router, provideRouter, withHashLocation } from '@angular/router';
import { HarnessLoader, TestKey } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { MatButtonHarness } from '@angular/material/button/testing';
import { MatChipInputHarness } from '@angular/material/chips/testing';
import { MatPaginatorHarness } from '@angular/material/paginator/testing';
import { provideStubSurfacing, settle } from '../../testing/stub-surfacing';
import { App } from '../app';
import { routes } from '../app.routes';
import { CLIPBOARD } from '../results/clipboard';
import {
  UNKNOWN_SCAN,
  duplicateSet,
  expectFolders,
  expectScan,
  expectSets,
  refuse,
  scanEntry,
  setPage,
} from '../results/result-test-data';
import { EVENT_SOURCE } from '../scans/scan-events.service';
import { sessionEndedInterceptor } from './session-ended.interceptor';

const NOTICE = 'Your session ended, or the server was restarted. Open the link printed in the console.';
const FORBIDDEN = { status: 403, statusText: 'Forbidden' };

describe('Session ended notice', () => {
  let http: HttpTestingController;
  let router: Router;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideHttpClient(withInterceptors([sessionEndedInterceptor])),
        provideHttpClientTesting(),
        provideRouter(routes, withHashLocation()),
        provideLocationMocks(),
        provideStubSurfacing(),
        { provide: CLIPBOARD, useValue: null },
        { provide: EVENT_SOURCE, useValue: () => ({ readyState: 1, addEventListener() {}, close() {} }) as unknown as EventSource },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    router.initialNavigation();
  });

  // A test that already failed (on a stub) must not also fail here, nor leave the TestBed instantiated for the next one.
  afterEach((context) => {
    try {
      if (!context.task.result?.errors?.length) http.verify();
    } finally {
      TestBed.resetTestingModule();
    }
  });

  async function shell(session: (r: TestRequest) => void) {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    session(http.expectOne('/api/session'));
    await settle(fixture);
    return fixture;
  }

  const connected = (r: TestRequest) => r.flush({ authenticated: true });

  function text(fixture: { nativeElement: unknown }): string {
    return ((fixture.nativeElement as HTMLElement).textContent ?? '').replace(/\s+/g, ' ');
  }

  function notice(fixture: { nativeElement: unknown }): HTMLElement | null {
    return (fixture.nativeElement as HTMLElement).querySelector('.session-ended');
  }

  it('DFS-066.AC-2 replaces the scan screen with the notice when GET /api/scans answers 403', async () => {
    const fixture = await shell(connected);
    await router.navigateByUrl('/');
    await settle(fixture);
    http.expectOne('/api/scans').flush('', FORBIDDEN);
    await settle(fixture);

    expect(notice(fixture)?.textContent?.trim()).toBe(NOTICE);
    expect(notice(fixture)?.getAttribute('role')).toBe('alert');
    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelector('app-scan-screen')).toBeNull();
    expect(text(fixture)).not.toContain('Start');
    expect(text(fixture)).not.toContain('Connected');
  });

  it('DFS-066.AC-2 replaces the result screen with the notice when a page of sets answers 403', async () => {
    const fixture = await shell(connected);
    const loader: HarnessLoader = TestbedHarnessEnvironment.loader(fixture);
    await router.navigateByUrl('/scans/s1');
    await settle(fixture);
    expectScan(http, 's1').flush(scanEntry('s1', { status: 'COMPLETED' }));
    await settle(fixture);
    const big = setPage([duplicateSet([['C:\\Photos\\a.jpg'], ['D:\\Backup\\a.jpg']])], { totalItems: 120 });
    expectFolders(http, 's1', null).flush([]);
    expectSets(http, 's1', null).flush(big);
    await settle(fixture);
    expect(notice(fixture)).toBeNull();

    await (await loader.getHarness(MatPaginatorHarness)).goToNextPage();
    await settle(fixture);
    expectSets(http, 's1', null, 1, 50).flush('', FORBIDDEN);
    await settle(fixture);

    expect(notice(fixture)?.textContent?.trim()).toBe(NOTICE);
    expect((fixture.nativeElement as HTMLElement).querySelector('app-result-screen')).toBeNull();
  });

  it('DFS-066.AC-2 replaces the scan screen with the notice when POST /api/scans answers 403', async () => {
    const fixture = await shell(connected);
    const loader: HarnessLoader = TestbedHarnessEnvironment.loader(fixture);
    await router.navigateByUrl('/');
    await settle(fixture);
    http.expectOne('/api/scans').flush([]);
    await settle(fixture);
    expect(notice(fixture)).toBeNull();

    const input = await loader.getHarness(MatChipInputHarness);
    await input.setValue('C:/photos');
    await input.sendSeparatorKey(TestKey.ENTER);
    await settle(fixture);
    await (await loader.getHarness(MatButtonHarness.with({ text: 'Start' }))).click();
    await settle(fixture);
    const start = http.expectOne('/api/scans');
    expect(start.request.method).toBe('POST');
    start.flush('', FORBIDDEN);
    await settle(fixture);

    expect(notice(fixture)?.textContent?.trim()).toBe(NOTICE);
    expect((fixture.nativeElement as HTMLElement).querySelector('app-scan-screen')).toBeNull();
  });

  it.each([
    ['403', (r: TestRequest) => r.flush('', FORBIDDEN)],
    ['200 authenticated false', (r: TestRequest) => r.flush({ authenticated: false })],
  ])('DFS-066.AC-3 shows the notice and calls no scan endpoint when /api/session answers %s', async (_, answer) => {
    const fixture = await shell(answer);

    expect(notice(fixture)?.textContent?.trim()).toBe(NOTICE);
    expect(text(fixture)).not.toContain('Connected');
    expect(text(fixture)).not.toContain('Start');
    http.expectNone('/api/scans');
  });

  it('DFS-066.AC-4 keeps the unknown scan message for a 404 UNKNOWN_SCAN and shows no notice', async () => {
    const fixture = await shell(connected);
    await router.navigateByUrl('/scans/gone');
    await settle(fixture);
    refuse(expectScan(http, 'gone'), 404, UNKNOWN_SCAN);
    await settle(fixture);

    expect(text(fixture)).toContain('This scan is not in this server run (the server may have restarted)');
    expect(notice(fixture)).toBeNull();
    expect(text(fixture)).not.toContain(NOTICE);
  });

  it('DFS-066.AC-4 keeps the generic message for a 500 and shows no notice', async () => {
    const fixture = await shell(connected);
    await router.navigateByUrl('/scans/s1');
    await settle(fixture);
    expectScan(http, 's1').flush(scanEntry('s1', { status: 'COMPLETED' }));
    await settle(fixture);
    expectFolders(http, 's1', null).flush('', { status: 500, statusText: 'Server Error' });
    expectSets(http, 's1', null).flush(setPage([]));
    await settle(fixture);

    expect(text(fixture)).toContain('The results could not be loaded; reload the page');
    expect(notice(fixture)).toBeNull();
    expect(text(fixture)).not.toContain(NOTICE);
  });
});
