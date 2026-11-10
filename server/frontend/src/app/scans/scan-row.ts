import { Component, DestroyRef, OnInit, computed, effect, inject, input, linkedSignal, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { ScanEntry } from '../api/models';
import { ScansService } from '../api/services';
import { ScanEventsService } from './scan-events.service';
import { collapsedTotals, failureLine, isTerminal, statusLabel } from './scan-format';
import { CLOCK, EtaEstimator } from './scan-eta';
import { overallCaption, sectionViews, stepLine } from './scan-sections';

/**
 * One scan in the list (DFS-041.AC-5 to AC-9, redesigned in DFS-063): its folders, start time and status, and
 *
 * - while it is QUEUED, RUNNING or CANCELLING, or after it was CANCELLED or FAILED: the expanded row, a step line, the
 *   overall percentage with its caption, the time left (hashing and hard links only), Cancel while it can be cancelled,
 *   one strip of four fixed sections and, under each, its state and two lines of text (`sectionViews`); a FAILED row
 *   adds the failure line (DFS-063.AC-1, AC-2, AC-7 to AC-11);
 * - once COMPLETED: the collapsed one-line row, with the narrowing totals, "100%", the duration and the "Open results"
 *   link (DFS-063.AC-12, DFS-049.AC-2).
 *
 * The calculations are the pure helpers in `scan-sections.ts`, `scan-format.ts` and `scan-eta.ts`; this component only
 * renders what they return and holds the CANCELLING percentage (DFS-063 decisions 1 and 2).
 */
@Component({
  selector: 'app-scan-row',
  imports: [DatePipe, RouterLink, MatButtonModule],
  templateUrl: './scan-row.html',
  styleUrl: './scan-row.css',
  host: { class: 'scan-row' },
})
export class ScanRow implements OnInit {
  readonly entry = input.required<ScanEntry>();

  protected readonly progress = linkedSignal(() => this.entry().progress);
  protected readonly lost = signal(false);
  protected readonly cancelRequested = signal(false);
  /** The time-left text of the latest progress, or null where none is shown (DFS-063.AC-4 to AC-6). */
  protected readonly eta = signal<string | null>(null);

  protected readonly status = computed(() => this.progress().status);
  protected readonly finished = computed(() => isTerminal(this.status()));
  protected readonly cancellable = computed(() => this.status() === 'QUEUED' || this.status() === 'RUNNING');
  /** Only a COMPLETED scan collapses; CANCELLED and FAILED rows stay expanded (DFS-063 decision 5). */
  protected readonly collapsed = computed(() => this.status() === 'COMPLETED');
  /** The four sections of the strip, in run order (DFS-063.AC-1 to AC-3, AC-7 to AC-11). */
  protected readonly sections = computed(() => sectionViews(this.progress()));

  protected readonly statusLabel = statusLabel;
  /** The caption under the percentage, `overallCaption` (DFS-063.AC-2). */
  protected readonly caption = computed(() => overallCaption(this.progress()));
  /** The step line, `stepLine` (DFS-063.AC-1). */
  protected readonly step = computed(() => stepLine(this.progress()));
  /** The collapsed row's narrowing totals, `collapsedTotals` (DFS-063.AC-12). */
  protected readonly totals = computed(() => collapsedTotals(this.progress()));
  /** The failure line of a FAILED row, `failureLine` (DFS-063.AC-11). */
  protected readonly failure = computed(() => failureLine(this.progress()));

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
   * The overall percentage as the row shows it: "47%", or "—" when `overallPercent` is null (DFS-063.AC-1, AC-3). While
   * the status is CANCELLING it holds the last value shown before it, however far the progress moves (DFS-063.AC-2);
   * once CANCELLED it shows the percentage of the progress again, labelled "when stopped". How the last value is kept
   * (an `effect` or a `linkedSignal`) is the implementation's choice; this method only reads signals.
   */
  protected overallLabel(): string {
    throw new Error('not implemented: DFS-063');
  }

  /**
   * The scan's duration for the collapsed row, "14 min 38 s" (DFS-063.AC-12, AC-13): `formatDuration` of the whole
   * seconds from `entry().startedAt` to `entry().finishedAt`; '' while `finishedAt` is null.
   */
  protected duration(): string {
    throw new Error('not implemented: DFS-063');
  }

  /**
   * The router commands of this scan's result screen, `['/scans', <id>]`, which the hash location renders as
   * `#/scans/<id>`; read only by a COMPLETED row's "Open results" link (DFS-049.AC-2).
   */
  protected resultLink(): string[] {
    return ['/scans', this.entry().id];
  }
}
