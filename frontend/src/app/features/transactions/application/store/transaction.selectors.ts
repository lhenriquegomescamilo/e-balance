import { transactionFeature } from './transaction.reducer';

export const {
  selectTransactionsState,
  selectItems,
  selectFilter,
  selectLoading,
  selectError,
} = transactionFeature;
