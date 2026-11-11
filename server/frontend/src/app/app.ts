import { Component, inject, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { SessionService } from './api/services';
import { SESSION_ENDED_NOTICE, SessionEnded } from './session/session-ended';

type Connection = 'checking' | 'connected' | 'disconnected';

/**
 * The page shell: a toolbar and whether this browser holds the session cookie (DFS-037.AC-3, DFS-036.AC-8). The
 * session is read through the client generated from openapi.json (DFS-038), so a server-side rename breaks this file.
 * Once connected it hosts the routed screen (DFS-049 decision 1): the scan screen at `#/` (DFS-041), a scan's result
 * at `#/scans/<id>`; without a session it renders no route, so no scan call is made (DFS-041.AC-10).
 *
 * DFS-066: when there is no session at load (a rejected or unauthenticated `/api/session`), or any `/api/**` call
 * answers 403 later (`SessionEnded`, set by the interceptor), the routed screen is replaced by one notice,
 * `SESSION_ENDED_NOTICE`, in `<p class="session-ended" role="alert">`.
 *
 * Stubs (design phase): `showNotice` throws the not-implemented error and the template calls it on every render; the
 * signatures, injected services, the template's structure and its CSS classes are final.
 */
@Component({
  selector: 'app-root',
  imports: [MatToolbarModule, RouterOutlet],
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {
  protected readonly connection = signal<Connection>('checking');
  protected readonly notice = SESSION_ENDED_NOTICE;
  protected readonly sessionEnded = inject(SessionEnded);

  constructor() {
    inject(SessionService)
      .getSession()
      .then(
        (status) => this.connection.set(status.authenticated ? 'connected' : 'disconnected'),
        () => this.connection.set('disconnected'),
      );
  }

  /**
   * Whether the notice replaces the routed screen: no session at load (`connection() === 'disconnected'`) or a 403
   * seen since (`sessionEnded.ended()`) (DFS-066.AC-2, AC-3).
   */
  protected showNotice(): boolean {
    throw new Error('not implemented: DFS-066');
  }
}
