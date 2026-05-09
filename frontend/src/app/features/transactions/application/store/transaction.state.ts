import { Transaction, TransactionFilter } from '../../domain/models/transaction.model';

export const TRANSACTION_FEATURE_KEY = 'transactions';

export interface TransactionState {
  readonly items: readonly Transaction[];
  readonly filter: TransactionFilter;
  readonly loading: boolean;
  readonly error: string | null;
}

export const initialTransactionState: TransactionState = {
  items: [],
  filter: { type: 'ALL' },
  loading: false,
  error: null,
};
