import { inject, Injectable } from '@angular/core';
import { Apollo } from 'apollo-angular';
import { map, Observable } from 'rxjs';
import { TransactionFilter } from '../../domain/models/transaction.model';
import { TRANSACTIONS_QUERY } from './transaction.queries';

export interface TransactionDto {
  readonly id: string;
  readonly operatedAt: string;
  readonly description: string;
  readonly value: number;
  readonly balance: number;
  readonly category: { readonly id: string; readonly name: string } | null;
}

interface TransactionsQueryResult {
  readonly transactions: readonly TransactionDto[];
}

@Injectable({ providedIn: 'root' })
export class TransactionApiService {
  private readonly apollo = inject(Apollo);

  queryTransactions(
    filter: TransactionFilter,
  ): Observable<readonly TransactionDto[]> {
    return this.apollo
      .watchQuery<TransactionsQueryResult>({
        query: TRANSACTIONS_QUERY,
        variables: { filter },
        fetchPolicy: 'cache-and-network',
      })
      .valueChanges.pipe(
        map(
          (result) =>
            (result.data?.transactions ?? []) as readonly TransactionDto[],
        ),
      );
  }
}
