import { Routes } from '@angular/router';
import { ScanScreen } from './scans/scan-screen';

/**
 * The page's routes, read from the URL's hash (DFS-049 decision 1, AC-1): `#/` is the scan screen, `#/scans/<id>` the
 * result screen of one scan; anything else goes back to the scan screen. The result screen is loaded lazily, so its
 * Material tree, tabs, card and paginator stay out of the initial bundle (checklist item 10).
 */
export const routes: Routes = [
  { path: '', pathMatch: 'full', component: ScanScreen },
  { path: 'scans/:id', loadComponent: () => import('./results/result-screen').then((m) => m.ResultScreen) },
  { path: '**', redirectTo: '' },
];
