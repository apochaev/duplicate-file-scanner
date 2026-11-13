import { InjectionToken } from '@angular/core';
import { browserStorage } from './result-density';

/** The start of the local-storage key holding one scan's marks: `dfs.results.done.<scanId>` (DFS-069.AC-4). */
export const DONE_KEY_PREFIX = 'dfs.results.done.';

/** The two calls the Overlaps tab makes on the browser's `localStorage` (DFS-069, same shape as `DensityStorage`). */
export interface DoneStorage {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
}

/**
 * The browser's local storage behind a token (DFS-069 decision 3, checklist item 4), so a spec provides a fake it can
 * read and pre-fill, one whose calls throw, or `null` for a browser without storage. `null` means: remember nothing
 * beyond the visit.
 */
export const DONE_STORAGE = new InjectionToken<DoneStorage | null>('DONE_STORAGE', {
  providedIn: 'root',
  factory: browserStorage,
});

/**
 * The storage key of a scan's marks: `DONE_KEY_PREFIX` followed by the scan ID (DFS-069.AC-4).
 */
export function doneStorageKey(scanId: string): string {
  throw new Error('not implemented: DFS-069');
}

/**
 * A group's identity: its folders' exact paths, sorted (plain code-unit order) and joined with a line feed `\n`, so the
 * order the API lists the folders in does not matter; `['/b/x', '/a/x']` gives `'/a/x\n/b/x'` (DFS-069.AC-4).
 */
export function groupKey(folders: readonly string[]): string {
  throw new Error('not implemented: DFS-069');
}

/**
 * The marks stored for `scanId`: the JSON array under `doneStorageKey(scanId)`, when every element is a string. Empty for
 * a null storage, a missing value, a `getItem` that throws, a value that is not JSON and one that is not an array of
 * strings (`{"a":1}`, `[1,2]`) (DFS-069.AC-6).
 */
export function readDoneMarks(storage: DoneStorage | null, scanId: string): string[] {
  throw new Error('not implemented: DFS-069');
}

/**
 * Stores `keys` as a JSON array under `doneStorageKey(scanId)`; does nothing for a null storage and ignores a `setItem`
 * that throws, so the marks then last only for the visit (DFS-069.AC-4, AC-6).
 */
export function writeDoneMarks(storage: DoneStorage | null, scanId: string, keys: readonly string[]): void {
  throw new Error('not implemented: DFS-069');
}

/**
 * How many of the `keys` (group keys, see `groupKey`) lie under `folder`: a key counts when any of its folders equals
 * `folder` or is below it (it continues with `/` or `\` after `folder`, trailing separators of `folder` ignored,
 * case-sensitive). Every key counts when `folder` is null, "All folders" (DFS-069.AC-3).
 */
export function countMarksUnder(keys: readonly string[], folder: string | null): number {
  throw new Error('not implemented: DFS-069');
}
