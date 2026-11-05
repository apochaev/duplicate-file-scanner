import { TestBed } from '@angular/core/testing';
import { Location } from '@angular/common';
import { provideLocationMocks } from '@angular/common/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter, withHashLocation } from '@angular/router';
import { provideStubSurfacing, settle } from '../testing/stub-surfacing';
import { App } from './app';
import { routes } from './app.routes';
import { CLIPBOARD } from './results/clipboard';
import { scanEntry } from './results/result-test-data';
import { EVENT_SOURCE } from './scans/scan-events.service';

describe('App routes', () => {
  let http: HttpTestingController;
  let router: Router;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideHttpClient(),
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
    // Subscribes the router to location changes, so Location.back() navigates.
    router.initialNavigation();
  });

  afterEach(() => http.verify());

  /** Renders the shell and answers /api/session as connected. */
  async function connectedShell() {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    http.expectOne('/api/session').flush({ authenticated: true });
    await settle(fixture);
    return fixture;
  }

  it('DFS-049.AC-1 shows the result screen when the page is loaded at #/scans/<id>', async () => {
    const fixture = await connectedShell();

    await router.navigateByUrl('/scans/s-1');
    await settle(fixture);

    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelector('app-result-screen')).not.toBeNull();
    expect(page.querySelector('app-scan-screen')).toBeNull();
    expect(http.expectOne((r) => r.url === '/api/scans/s-1').request.method).toBe('GET');
    http.match(() => true);
  });

  it('DFS-049.AC-1 shows the scan screen at #/ and goes back to it from a result', async () => {
    const fixture = await connectedShell();
    await router.navigateByUrl('/');
    await settle(fixture);
    http.expectOne('/api/scans').flush([scanEntry('s-1')]);
    await settle(fixture);
    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelector('app-scan-screen')).not.toBeNull();

    await router.navigateByUrl('/scans/s-1');
    await settle(fixture);
    expect(page.querySelector('app-result-screen')).not.toBeNull();
    http.match(() => true);

    TestBed.inject(Location).back();
    await settle(fixture);

    expect(router.url).toBe('/');
    expect(page.querySelector('app-scan-screen')).not.toBeNull();
    expect(page.querySelector('app-result-screen')).toBeNull();
    http.match(() => true);
  });
});
