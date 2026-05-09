import { inject, Injectable } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { catchError, map, of, switchMap } from 'rxjs';
import { GetTransactionsUseCase } from '../../domain/use-cases/get-transactions.use-case';
import { TransactionActions } from './transaction.actions';

@Injectable()
export class TransactionEffects {
  private readonly actions$ = inject(Actions);
  private readonly getTransactions = inject(GetTransactionsUseCase);

  load$ = createEffect(() =>
    this.actions$.pipe(
      ofType(TransactionActions.load),
      switchMap(({ filter }) =>
        this.getTransactions.execute(filter).pipe(
          map((items) => TransactionActions.loadSuccess({ items })),
          catchError((err: unknown) =>
            of(TransactionActions.loadFailure({ error: errorMessage(err) })),
          ),
        ),
      ),
    ),
  );
}

function errorMessage(err: unknown): string {
  if (err instanceof Error) return err.message;
  if (typeof err === 'string') return err;
  return 'Unknown error';
}
