import { Component, DestroyRef, OnInit, computed, inject, input, linkedSignal, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ScanEntry } from '../api/models';
import { ScansService } from '../api/services';
import { ScanEventsService } from './scan-events.service';
import { formatBytes, isTerminal, phaseLabel, plural, statusLabel } from './scan-format';

/**
 * One scan in the list (DFS-041.AC-5 to AC-9): its folders, start time and status; while it is not finished, its live
 * progress from the stream and a Cancel button; once finished, its totals or failure, and for a COMPLETED scan an
 * "Open results" link to its result screen (DFS-049.AC-2).
 */
@Component({
  selector: 'app-scan-row',
  imports: [DatePipe, RouterLink, MatButtonModule, MatProgressBarModule],
  templateUrl: './scan-row.html',
  styleUrl: './scan-row.css',
  host: { class: 'scan-row' },
})
export class ScanRow implements OnInit {
  readonly entry = input.required<ScanEntry>();

  protected readonly progress = linkedSignal(() => this.entry().progress);
  protected readonly lost = signal(false);
  protected readonly cancelRequested = signal(false);

  protected readonly status = computed(() => this.progress().status);
  protected readonly finished = computed(() => isTerminal(this.status()));
  protected readonly cancellable = computed(() => this.status() === 'QUEUED' || this.status() === 'RUNNING');
  protected readonly hashing = computed(() => {
    const phase = this.progress().phase;
    return phase === 'PARTIAL_HASH' || phase === 'FULL_HASH';
  });
  protected readonly hashPercent = computed(() => {
    const { hashedBytes, hashBytesTotal } = this.progress();
    return hashBytesTotal > 0 ? (hashedBytes / hashBytesTotal) * 100 : 0;
  });

  protected readonly statusLabel = statusLabel;
  protected readonly phaseLabel = phaseLabel;
  protected readonly formatBytes = formatBytes;
  protected readonly plural = plural;

  private readonly scans = inject(ScansService);
  private readonly events = inject(ScanEventsService);
  private stream?: Subscription;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.stream?.unsubscribe());
  }

  ngOnInit(): void {
    if (!this.finished()) {
      this.stream = this.events.progress(this.entry().id).subscribe({
        next: (progress) => this.progress.set(progress),
        error: () => this.lost.set(true),
      });
    }
  }

  protected async cancel(): Promise<void> {
    this.cancelRequested.set(true);
    try {
      const entry = await this.scans.cancelScan({ id: this.entry().id });
      // The stream may already have carried a later status; never step back from a finished one.
      if (!this.finished()) {
        this.progress.set(entry.progress);
      }
      if (this.finished()) {
        this.stream?.unsubscribe();
      }
    } catch {
      this.cancelRequested.set(false);
    }
  }

  /**
   * The router commands of this scan's result screen, `['/scans', <id>]`, which the hash location renders as
   * `#/scans/<id>`; read only by a COMPLETED row's "Open results" link (DFS-049.AC-2).
   */
  protected resultLink(): string[] {
    return ['/scans', this.entry().id];
  }
}
