import { Component, inject, signal } from '@angular/core';
import { MatToolbarModule } from '@angular/material/toolbar';
import { SessionService } from './api/services';

type Connection = 'checking' | 'connected' | 'disconnected';

/**
 * The page shell: a toolbar and whether this browser holds the session cookie (DFS-037.AC-3, DFS-036.AC-8). The
 * session is read through the client generated from openapi.json (DFS-038), so a server-side rename breaks this file.
 */
@Component({
  selector: 'app-root',
  imports: [MatToolbarModule],
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
