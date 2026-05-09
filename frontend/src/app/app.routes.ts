import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'transactions' },
  {
    path: 'transactions',
    loadChildren: () =>
      import('./features/transactions/presentation/routes/transaction.routes').then(
        (m) => m.TRANSACTION_ROUTES,
      ),
  },
  { path: '**', redirectTo: 'transactions' },
];
