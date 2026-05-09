import { Route } from '@angular/router';
import { provideEffects } from '@ngrx/effects';
import { provideState } from '@ngrx/store';
import { TransactionEffects } from '../../application/store/transaction.effects';
import { transactionFeature } from '../../application/store/transaction.reducer';

export const TRANSACTION_ROUTES: Route[] = [
  {
    path: '',
    providers: [
      provideState(transactionFeature),
      provideEffects([TransactionEffects]),
    ],
    loadComponent: () =>
      import('../pages/transaction-list.page').then((m) => m.TransactionListPage),
  },
];
