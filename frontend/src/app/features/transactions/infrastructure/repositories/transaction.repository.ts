import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { Transaction, TransactionFilter } from '../../domain/models/transaction.model';
import { TransactionApiService, TransactionDto } from '../api/transaction.api.service';

@Injectable({ providedIn: 'root' })
export class TransactionRepository {
  private readonly api = inject(TransactionApiService);

  findAll(filter: TransactionFilter): Observable<readonly Transaction[]> {
    return this.api
      .queryTransactions(filter)
      .pipe(map((dtos) => dtos.map((dto) => this.toEntity(dto))));
  }

  private toEntity(dto: TransactionDto): Transaction {
    return {
      id: dto.id,
      operatedAt: dto.operatedAt,
      description: dto.description,
      value: dto.value,
      balance: dto.balance,
      category: dto.category,
    };
  }
}
