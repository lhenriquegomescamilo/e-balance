export type TransactionType = 'INCOME' | 'EXPENSE' | 'ALL';

export interface Category {
  readonly id: string;
  readonly name: string;
}

export interface Transaction {
  readonly id: string;
  readonly operatedAt: string;
  readonly description: string;
  readonly value: number;
  readonly balance: number;
  readonly category: Category | null;
}

export interface TransactionFilter {
  readonly startDate?: string;
  readonly endDate?: string;
  readonly categoryIds?: readonly string[];
  readonly type?: TransactionType;
}
