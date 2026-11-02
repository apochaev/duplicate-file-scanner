import { Component, inject, signal } from '@angular/core';
import { ScanEntry } from '../api/models';
import { ScansService } from '../api/services';
import { ScanForm } from './scan-form';
import { ScanRow } from './scan-row';

/**
 * The scan screen (DFS-041, `ui.md` item 5, U2 and D3): the form on top, this server run's scans below, newest first
 * (decision 2). A scan started here goes to the top of the list.
 */
@Component({
  selector: 'app-scan-screen',
  imports: [ScanForm, ScanRow],
  templateUrl: './scan-screen.html',
  styleUrl: './scan-screen.css',
})
export class ScanScreen {
  protected readonly scans = signal<ScanEntry[]>([]);
  protected readonly loadError = signal(false);

  constructor() {
    inject(ScansService)
      .listScans()
      .then(
        (scans) => this.scans.set(scans),
        () => this.loadError.set(true),
      );
  }

  protected started(entry: ScanEntry): void {
    this.scans.update((scans) => [entry, ...scans]);
  }
}
