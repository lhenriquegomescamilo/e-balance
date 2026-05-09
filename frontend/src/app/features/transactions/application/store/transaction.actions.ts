import { createActionGroup, emptyProps, props } from '@ngrx/store';
import { Transaction, TransactionFilter } from '../../domain/models/transaction.model';

export const TransactionActions = createActionGroup({
  source: 'Transactions',
  events: {
    Load: props<{ filter: TransactionFilter }>(),
    'Load Success': props<{ items: readonly Transaction[] }>(),
    'Load Failure': props<{ error: string }>(),
    'Set Filter': props<{ filter: TransactionFilter }>(),
    Reset: emptyProps(),
  },
});
