import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, withHashLocation } from '@angular/router';
import { ScanProgress } from '../api/models';
import { provideStubSurfacing, settle } from '../../testing/stub-surfacing';
import { scanEntry } from '../results/result-test-data';
import { EVENT_SOURCE } from './scan-events.service';
import { ScanRow } from './scan-row';

/** A stream that never delivers: the rows under test only need the link, not live progress. */
class SilentEventSource {
  readyState = 1;
  onerror: ((event: Event) => void) | null = null;
  addEventListener(): void {}
  close(): void {
    this.readyState = 2;
  }
}

describe('ScanRow', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ScanRow],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([], withHashLocation()),
        provideStubSurfacing(),
        { provide: EVENT_SOURCE, useValue: () => new SilentEventSource() as unknown as EventSource },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function render(id: string, update: Partial<ScanProgress>): Promise<HTMLElement> {
    const fixture = TestBed.createComponent(ScanRow);
    fixture.componentRef.setInput('entry', scanEntry(id, update));
    await settle(fixture);
    return fixture.nativeElement as HTMLElement;
  }

  it('DFS-049.AC-2 shows "Open results" to #/scans/<id> on a COMPLETED row, also with 0 duplicate sets', async () => {
    const withSets = await render('s-3', { status: 'COMPLETED', groupsTotal: 3 });
    const noSets = await render('s-0', { status: 'COMPLETED', groupsTotal: 0 });

    const first = withSets.querySelector('a.open-results');
    expect(first?.textContent?.trim()).toBe('Open results');
    expect(first?.getAttribute('href')).toBe('#/scans/s-3');
    const second = noSets.querySelector('a.open-results');
    expect(second?.textContent?.trim()).toBe('Open results');
    expect(second?.getAttribute('href')).toBe('#/scans/s-0');
  });

  it('DFS-049.AC-2 shows no "Open results" link on a QUEUED, RUNNING, CANCELLING, CANCELLED or FAILED row', async () => {
    // Rendered after a COMPLETED row, so the test reaches the link's stub: a row with no link never would.
    const done = await render('s-1', { status: 'COMPLETED' });
    expect(done.querySelector('a.open-results')).not.toBeNull();

    for (const status of ['QUEUED', 'RUNNING', 'CANCELLING', 'CANCELLED', 'FAILED'] as const) {
      const row = await render('s-9', { status, failureMessage: status === 'FAILED' ? 'Disk not readable' : null });

      expect(row.querySelector('a.open-results'), status).toBeNull();
      expect(row.textContent, status).not.toContain('Open results');
    }
  });
});
