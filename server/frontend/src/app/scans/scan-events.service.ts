import { InjectionToken } from '@angular/core';

/** Opens an EventSource; replaced in tests, where jsdom has none (DFS-041 decision 5). */
export const EVENT_SOURCE = new InjectionToken<(url: string) => EventSource>('EVENT_SOURCE', {
  providedIn: 'root',
  factory: () => (url: string) => new EventSource(url),
});
