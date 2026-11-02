import { Injectable, InjectionToken, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiConfiguration } from '../api/api-configuration';
import { ScanProgress } from '../api/models';
import { isTerminal } from './scan-format';

/** Opens an EventSource; replaced in tests, where jsdom has none (DFS-041 decision 5). */
export const EVENT_SOURCE = new InjectionToken<(url: string) => EventSource>('EVENT_SOURCE', {
  providedIn: 'root',
  factory: () => (url: string) => new EventSource(url),
});

/** EventSource.CLOSED, spelled out because jsdom has no EventSource to read it from. */
const CLOSED = 2;

/**
 * A scan's progress stream (`GET /api/scans/{id}/events`, DFS-040) as an Observable of `ScanProgress`. The generated
 * client cannot read Server-Sent Events, so this is the one call that bypasses it (DFS-041 decision 5); the event data
 * is still the generated `ScanProgress` type, so a change to it in the spec breaks this file's callers.
 *
 * The Observable completes after a terminal status and closes the EventSource, which would otherwise reconnect when
 * the server ends the stream. An error while the browser reconnects is ignored: the server sends the current progress
 * on every connect. Once the browser gives up (the EventSource is closed, for example after a server restart), the
 * Observable errors.
 */
@Injectable({ providedIn: 'root' })
export class ScanEventsService {
  private readonly open = inject(EVENT_SOURCE);
  private readonly rootUrl = inject(ApiConfiguration).rootUrl;

  progress(id: string): Observable<ScanProgress> {
    return new Observable<ScanProgress>((subscriber) => {
      const source = this.open(`${this.rootUrl}/api/scans/${encodeURIComponent(id)}/events`);
      source.addEventListener('progress', (event) => {
        const progress = JSON.parse((event as MessageEvent<string>).data) as ScanProgress;
        subscriber.next(progress);
        if (isTerminal(progress.status)) {
          source.close();
          subscriber.complete();
        }
      });
      source.onerror = () => {
        if (source.readyState === CLOSED) {
          subscriber.error(new Error(`The progress stream of scan ${id} was closed`));
        }
      };
      return () => source.close();
    });
  }
}
