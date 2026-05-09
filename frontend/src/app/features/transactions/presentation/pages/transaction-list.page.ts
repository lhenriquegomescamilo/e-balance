import { CommonModule } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TransactionFacade } from '../../application/facade/transaction.facade';
import { TransactionType } from '../../domain/models/transaction.model';
import { TransactionRowComponent } from '../components/transaction-row.component';

@Component({
  selector: 'app-transaction-list',
  standalone: true,
  imports: [CommonModule, FormsModule, TransactionRowComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="header">
      <div>
        <div class="h-card">All transactions</div>
        <div class="h-display">{{ facade.transactions().length }} entries</div>
      </div>
      <div class="filters glass">
        <input
          class="search"
          placeholder="Search…"
          [ngModel]="query()"
          (ngModelChange)="query.set($event)"
        />
        <select
          [ngModel]="typeFilter()"
          (ngModelChange)="onTypeChange($event)"
        >
          <option value="ALL">All</option>
          <option value="INCOME">Income</option>
          <option value="EXPENSE">Expenses</option>
        </select>
      </div>
    </div>

    @if (facade.loading() && facade.transactions().length === 0) {
      <div class="glass message">Loading transactions…</div>
    } @else if (facade.error()) {
      <div class="glass message error">
        <strong>Could not load transactions.</strong>
        <span class="muted">{{ facade.error() }}</span>
      </div>
    } @else {
      <div class="glass table-wrap">
        <table class="tbl">
          <thead>
            <tr>
              <th>Description</th>
              <th>Category</th>
              <th>Date</th>
              <th class="right">Amount</th>
            </tr>
          </thead>
          <tbody>
            @for (t of filtered(); track t.id) {
              <tr app-transaction-row [transaction]="t"></tr>
            } @empty {
              <tr>
                <td colspan="4" class="muted center">No transactions match.</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }
  `,
  styles: [`
    .header {
      display: flex;
      justify-content: space-between;
      align-items: flex-end;
      margin-bottom: 20px;
      gap: 16px;
      flex-wrap: wrap;
    }
    .filters {
      display: flex;
      gap: 8px;
      padding: 8px;
      border-radius: 999px;
    }
    .filters input,
    .filters select {
      background: transparent;
      border: none;
      outline: none;
      color: var(--text-primary);
      font-family: inherit;
      font-size: 14px;
      padding: 6px 12px;
    }
    .filters select { background: rgba(255, 255, 255, 0.06); border-radius: 999px; }
    .filters select option { background: #14141e; color: white; }

    .table-wrap { padding: 8px 22px; }
    .tbl { width: 100%; border-collapse: collapse; }
    .tbl th {
      text-align: left;
      font-size: 11px;
      letter-spacing: 0.1em;
      text-transform: uppercase;
      color: var(--text-muted);
      padding: 16px 8px;
      font-weight: 500;
      border-bottom: 1px solid rgba(255, 255, 255, 0.08);
    }
    .right { text-align: right; }
    .center { text-align: center; padding: 32px 8px; }

    .message {
      padding: 24px;
      display: flex;
      flex-direction: column;
      gap: 6px;
    }
    .message.error { border-color: rgba(255, 111, 145, 0.4); }
  `],
})
export class TransactionListPage implements OnInit {
  readonly facade = inject(TransactionFacade);
  readonly query = signal('');
  readonly typeFilter = signal<TransactionType>('ALL');

  readonly filtered = computed(() => {
    const q = this.query().toLowerCase();
    if (!q) return this.facade.transactions();
    return this.facade
      .transactions()
      .filter(
        (t) =>
          t.description.toLowerCase().includes(q) ||
          (t.category?.name.toLowerCase().includes(q) ?? false),
      );
  });

  ngOnInit(): void {
    this.facade.load({ type: this.typeFilter() });
  }

  onTypeChange(type: TransactionType): void {
    this.typeFilter.set(type);
    this.facade.setFilter({ type });
  }
}
