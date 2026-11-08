import { InjectionToken } from '@angular/core';

/** The Sets table's row density (DFS-060 decision 6, AC-9); `compact` unless the user chose otherwise. */
export type Density = 'compact' | 'comfortable';

/** The local-storage key holding the last density chosen (AC-9). */
export const DENSITY_KEY = 'dfs.results.density';

/** The two calls the result screen makes on the browser's `localStorage` (AC-9). */
export interface DensityStorage {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
}

/**
 * The browser's local storage behind a token (DFS-060 decision 6, checklist item 4), so a spec provides a fake that it
 * can read and pre-fill, or `null` for a browser without storage. `null` means: start Compact, remember nothing.
 */
export const DENSITY_STORAGE = new InjectionToken<DensityStorage | null>('DENSITY_STORAGE', {
  providedIn: 'root',
  factory: browserStorage,
});

/**
 * The default: `window.localStorage` when the browser offers it, otherwise null (also when reading the property
 * throws, as it does with storage blocked).
 */
export function browserStorage(): DensityStorage | null {
  throw new Error('not implemented: DFS-060');
}

/**
 * The density stored under `DENSITY_KEY` when it is exactly `compact` or `comfortable`; `compact` for anything else,
 * for a null storage and when `getItem` throws (AC-9).
 */
export function readDensity(storage: DensityStorage | null): Density {
  throw new Error('not implemented: DFS-060');
}

/** Stores `density` under `DENSITY_KEY`; does nothing for a null storage and ignores a `setItem` that throws (AC-9). */
export function writeDensity(storage: DensityStorage | null, density: Density): void {
  throw new Error('not implemented: DFS-060');
}
