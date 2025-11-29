import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./ar/ar-view/ar-view.component').then(m => m.ArViewComponent)
  },
  {
    path: '**',
    redirectTo: ''
  }
];
