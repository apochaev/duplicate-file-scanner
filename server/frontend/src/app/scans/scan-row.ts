import { Component, DestroyRef, OnInit, computed, effect, inject, input, linkedSignal, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ScanEntry } from '../api/models';
import { ScansService } from '../api/services';
import { ScanEventsService } from './scan-events.service';
import { formatBytes, isTerminal, plural, statusLabel } from './scan-format';
import { CLOCK, EtaEstimator } from './scan-eta';
import { phaseView } from './scan-phase';

/**
 * One scan in the list (DFS-041.AC-5 to AC-9): its folders, start time and status; while it is not finished, its live
 * progress from the stream and a Cancel button; once finished, its totals or failure, and for a COMPLETED scan an
 * "Open results" link to its result screen (DFS-049.AC-2).
 *
 * While unfinished, the progress is the current phase's line, a bar and, after WALK, the found totals (DFS-057.AC-1
 * to AC-5), plus the ETA in FULL_HASH (DFS-057.AC-6) from an estimator handed every progress the row holds.
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
  /** The ETA text of the latest progress, or null outside FULL_HASH (DFS-057.AC-6). */
  protected readonly eta = signal<string | null>(null);

  protected readonly status = computed(() => this.progress().status);
  protected readonly finished = computed(() => isTerminal(this.status()));
  protected readonly cancellable = computed(() => this.status() === 'QUEUED' || this.status() === 'RUNNING');
  /** The phase line, found line and bar value; read only while the scan is unfinished (DFS-057.AC-1 to AC-5). */
  protected readonly view = computed(() => phaseView(this.progress()));

  protected readonly statusLabel = statusLabel;
  protected readonly formatBytes = formatBytes;
  protected readonly plural = plural;

  private readonly scans = inject(ScansService);
  private readonly events = inject(ScanEventsService);
  private readonly estimator = new EtaEstimator(inject(CLOCK));
  private stream?: Subscription;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.stream?.unsubscribe());
    // Every progress the row holds (the entry's, each stream update, a cancel's answer) is timed and recorded, in
    // every status, so a phase change or a terminal status resets the estimator's window (DFS-057.AC-6, AC-7).
    effect(() => {
      const progress = this.progress();
      this.eta.set(this.estimator.record(progress));
    });
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
