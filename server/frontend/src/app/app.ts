import { Component, inject, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { SessionService } from './api/services';

type Connection = 'checking' | 'connected' | 'disconnected';

/**
 * The page shell: a toolbar and whether this browser holds the session cookie (DFS-037.AC-3, DFS-036.AC-8). The
 * session is read through the client generated from openapi.json (DFS-038), so a server-side rename breaks this file.
 * Once connected it hosts the routed screen (DFS-049 decision 1): the scan screen at `#/` (DFS-041), a scan's result
 * at `#/scans/<id>`; without a session it renders no route, so no scan call is made (DFS-041.AC-10).
 */
@Component({
  selector: 'app-root',
  imports: [MatToolbarModule, RouterOutlet],
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {
  protected readonly connection = signal<Connection>('checking');

  constructor() {
    inject(SessionService)
      .getSession()
      .then(
        (status) => this.connection.set(status.authenticated ? 'connected' : 'disconnected'),
        () => this.connection.set('disconnected'),
      );
  }
}
