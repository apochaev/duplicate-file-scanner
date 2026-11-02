import {
  DestroyRef,
  EnvironmentProviders,
  ErrorHandler,
  Injectable,
  Provider,
  inject,
  provideBrowserGlobalErrorListeners,
  provideEnvironmentInitializer,
} from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

/**
 * Makes an architect's stub fail the test that reaches it with the stub's own error (CCW-020 decision 7), the frontend
 * counterpart of the server tests' `StubSurfacingConfiguration` (CCW-019).
 *
 * Without it, a stub that throws `new Error('not implemented: <ID>')` from a template, an event handler or a Promise
 * callback never fails its test: Angular hands the error to its `ErrorHandler`, or the Promise rejects unhandled, the
 * test goes on and fails on an assertion, and the pipeline's red gate rejects it. With it, every such error is
 * recorded and `settle()` throws the first one.
 *
 * Usage, in a spec's `TestBed.configureTestingModule`: `providers: [provideStubSurfacing(), ...]`; then call
 * `await settle(fixture)` after each step that renders or lets Promises run, instead of a local settle function.
 */
@Injectable()
export class StubErrorRecorder extends ErrorHandler {
  private readonly errors: unknown[] = [];

  override handleError(error: unknown): void {
    this.errors.push(error);
  }

  /** Throws the first error recorded since the last call, if any, and forgets the rest. */
  rethrow(): void {
    if (this.errors.length === 0) return;
    const first = this.errors[0];
    this.errors.length = 0;
    throw first;
  }
}

/** Node's process, reached without Node's types (the app's tsconfig has none); absent outside Node. */
interface NodeProcess {
  on(event: 'unhandledRejection', listener: (reason: unknown) => void): void;
  off(event: 'unhandledRejection', listener: (reason: unknown) => void): void;
}

/** The providers that record every error reaching Angular's `ErrorHandler`, and every unhandled Promise rejection. */
export function provideStubSurfacing(): (Provider | EnvironmentProviders)[] {
  return [
    provideBrowserGlobalErrorListeners(),
    StubErrorRecorder,
    { provide: ErrorHandler, useExisting: StubErrorRecorder },
    // A rejection in a Promise callback (the generated client's) reaches neither the ErrorHandler nor jsdom's window
    // under Vitest, only Node's process; recorded for as long as this test's injector lives.
    provideEnvironmentInitializer(() => {
      const recorder = inject(StubErrorRecorder);
      const node = (globalThis as { process?: NodeProcess }).process;
      if (!node) return;
      const record = (reason: unknown) => recorder.handleError(reason);
      node.on('unhandledRejection', record);
      inject(DestroyRef).onDestroy(() => node.off('unhandledRejection', record));
    }),
  ];
}

/**
 * Lets the generated client's Promises and the signals settle, renders (DFS-038's Promise timing), then throws the
 * first error recorded since the last call: a stub's error fails the test right here, with the stub's own message.
 */
export async function settle(fixture: ComponentFixture<unknown>): Promise<void> {
  const recorder = TestBed.inject(StubErrorRecorder, null);
  if (!recorder) throw new Error('settle() needs provideStubSurfacing() in the TestBed providers');
  await new Promise((resolve) => setTimeout(resolve));
  await fixture.whenStable();
  fixture.detectChanges();
  recorder.rethrow();
}
