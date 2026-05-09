import { inject, Injectable, Signal } from '@angular/core';
import { Store } from '@ngrx/store';
import { Transaction, TransactionFilter } from '../../domain/models/transaction.model';
import { TransactionActions } from '../store/transaction.actions';
import {
  selectError,
  selectFilter,
  selectItems,
  selectLoading,
} from '../store/transaction.selectors';

@Injectable({ providedIn: 'root' })
export class TransactionFacade {
  private readonly store = inject(Store);

  readonly transactions: Signal<readonly Transaction[]> =
    this.store.selectSignal(selectItems);
  readonly filter: Signal<TransactionFilter> =
    this.store.selectSignal(selectFilter);
  readonly loading: Signal<boolean> = this.store.selectSignal(selectLoading);
  readonly error: Signal<string | null> = this.store.selectSignal(selectError);

  load(filter: TransactionFilter = { type: 'ALL' }): void {
    this.store.dispatch(TransactionActions.load({ filter }));
  }

  setFilter(filter: TransactionFilter): void {
    this.store.dispatch(TransactionActions.setFilter({ filter }));
    this.load(filter);
  }

  reset(): void {
    this.store.dispatch(TransactionActions.reset());
  }
}
