import { Component, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { MatToolbarModule } from '@angular/material/toolbar';

type Connection = 'checking' | 'connected' | 'disconnected';

/** The page shell: a toolbar and whether this browser holds the session cookie (DFS-037.AC-3, DFS-036.AC-8). */
@Component({
  selector: 'app-root',
  imports: [MatToolbarModule],
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {
  protected readonly connection = signal<Connection>('checking');

  constructor() {
    inject(HttpClient)
      .get('/api/session')
      .subscribe({
        next: () => this.connection.set('connected'),
        error: () => this.connection.set('disconnected'),
      });
  }
}
