import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { SessionEnded, isSessionEnded } from './session-ended';

/**
 * Turns any 403 from `/api/**` into the one "session ended" signal (DFS-066 decision 3, AC-2) and passes every failure
 * on unchanged, so a screen keeps its own handling and a `404 UNKNOWN_SCAN` or a `500` is never turned into the
 * notice (AC-4). Registered in `app.config.ts` with `withInterceptors`.
 */
export const sessionEndedInterceptor: HttpInterceptorFn = (request, next) => {
  const session = inject(SessionEnded);
  return next(request).pipe(
    catchError((failure: unknown) => {
      if (isSessionEnded(failure)) {
        session.markEnded();
      }
      return throwError(() => failure);
    }),
  );
};
