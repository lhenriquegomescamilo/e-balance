import { createFeature, createReducer, on } from '@ngrx/store';
import { TransactionActions } from './transaction.actions';
import { initialTransactionState, TRANSACTION_FEATURE_KEY } from './transaction.state';

export const transactionFeature = createFeature({
  name: TRANSACTION_FEATURE_KEY,
  reducer: createReducer(
    initialTransactionState,
    on(TransactionActions.load, (state, { filter }) => ({
      ...state,
      filter,
      loading: true,
      error: null,
    })),
    on(TransactionActions.loadSuccess, (state, { items }) => ({
      ...state,
      items,
      loading: false,
    })),
    on(TransactionActions.loadFailure, (state, { error }) => ({
      ...state,
      loading: false,
      error,
    })),
    on(TransactionActions.setFilter, (state, { filter }) => ({
      ...state,
      filter,
    })),
    on(TransactionActions.reset, () => initialTransactionState),
  ),
});
