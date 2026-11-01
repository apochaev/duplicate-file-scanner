import { Component, computed, inject, output, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatChipInputEvent, MatChipsModule } from '@angular/material/chips';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { ApiError, ScanEntry, StartScanRequest } from '../api/models';
import { ScansService } from '../api/services';

/** The size units offered for the minimum size, 1024-based like the CLI's report (DFS-041 decision 3). */
const UNITS = [
  { label: 'bytes', factor: 1 },
  { label: 'KB', factor: 1024 },
  { label: 'MB', factor: 1024 * 1024 },
];

/**
 * The scan form (DFS-041.AC-1 to AC-4): folders typed as absolute paths and kept as chips (decision 1), the two
 * settings in a panel closed by default (decision 3), and Start. A setting left at its default is left out of the
 * request, so the server's defaults apply. Emits the new scan after a `201`.
 */
@Component({
  selector: 'app-scan-form',
  imports: [
    MatButtonModule,
    MatButtonToggleModule,
    MatCheckboxModule,
    MatChipsModule,
    MatExpansionModule,
    MatFormFieldModule,
    MatInputModule,
  ],
  templateUrl: './scan-form.html',
  styleUrl: './scan-form.css',
})
export class ScanForm {
  readonly started = output<ScanEntry>();

  protected readonly units = UNITS;
  protected readonly folders = signal<string[]>([]);
  protected readonly sizeText = signal('1');
  protected readonly unitFactor = signal(1);
  protected readonly defaultExcludes = signal(true);
  protected readonly starting = signal(false);
  protected readonly error = signal<string | null>(null);

  /** The minimum size in bytes, or null when the field does not give at least one byte. */
  protected readonly minSize = computed(() => {
    const text = this.sizeText().trim();
    const value = Number(text);
    if (text === '' || !Number.isFinite(value)) {
      return null;
    }
    const bytes = Math.floor(value * this.unitFactor());
    return bytes >= 1 ? bytes : null;
  });

  protected readonly canStart = computed(
    () => this.folders().length > 0 && this.minSize() !== null && !this.starting(),
  );

  private readonly scans = inject(ScansService);

  protected add(event: MatChipInputEvent): void {
    const path = event.value.trim();
    if (path !== '' && !this.folders().includes(path)) {
      this.folders.update((folders) => [...folders, path]);
      this.error.set(null);
    }
    event.chipInput.clear();
  }

  protected remove(path: string): void {
    this.folders.update((folders) => folders.filter((folder) => folder !== path));
    this.error.set(null);
  }

  protected async start(): Promise<void> {
    const minSize = this.minSize();
    if (!this.canStart() || minSize === null) {
      return;
    }
    const body: StartScanRequest = { locations: this.folders() };
    if (minSize !== 1) {
      body.minSize = minSize;
    }
    if (!this.defaultExcludes()) {
      body.defaultExcludes = false;
    }
    this.starting.set(true);
    this.error.set(null);
    try {
      const entry = await this.scans.startScan({ body });
      this.folders.set([]);
      this.started.emit(entry);
    } catch (failure) {
      this.error.set(describe(failure));
    } finally {
      this.starting.set(false);
    }
  }
}

/** The text shown for a failed start: the server's own message for a `400`, a fixed one otherwise (DFS-041.AC-4). */
function describe(failure: unknown): string {
  if (!(failure instanceof HttpErrorResponse)) {
    return 'The scan could not be started';
  }
  const body = failure.error as Partial<ApiError> | null;
  if (failure.status === 503 || body?.code === 'EXECUTOR_REJECTED') {
    return 'The server is busy, try again';
  }
  if (failure.status === 0) {
    return 'The server cannot be reached';
  }
  return body?.message ?? `The server answered ${failure.status}`;
}
