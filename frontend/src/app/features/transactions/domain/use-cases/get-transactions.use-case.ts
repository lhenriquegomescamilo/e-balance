import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Transaction, TransactionFilter } from '../models/transaction.model';
import { TransactionRepository } from '../../infrastructure/repositories/transaction.repository';

@Injectable({ providedIn: 'root' })
export class GetTransactionsUseCase {
  private readonly repo = inject(TransactionRepository);

  execute(filter: TransactionFilter): Observable<readonly Transaction[]> {
    return this.repo.findAll(filter);
  }
}
