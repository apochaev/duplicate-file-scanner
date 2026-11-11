import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, withHashLocation } from '@angular/router';
import { routes } from './app.routes';
import { sessionEndedInterceptor } from './session/session-ended.interceptor';

/**
 * Hash URLs (DFS-049 decision 1): the server always serves `/`, so `#/scans/<id>` needs no server-side fallback. The
 * interceptor turns a 403 from `/api/**` into the "session ended" notice (DFS-066 decision 3).
 */
export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideHttpClient(withInterceptors([sessionEndedInterceptor])),
    provideRouter(routes, withHashLocation()),
  ],
};
