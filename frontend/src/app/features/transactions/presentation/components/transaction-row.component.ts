import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { Transaction } from '../../domain/models/transaction.model';

@Component({
  selector: 'tr[app-transaction-row]',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <td>
      <div class="m">
        <span class="ico">{{ initial() }}</span>
        <span>{{ transaction().description }}</span>
      </div>
    </td>
    <td>
      @if (transaction().category; as cat) {
        <span class="chip">{{ cat.name }}</span>
      } @else {
        <span class="chip dim">Uncategorized</span>
      }
    </td>
    <td class="muted">{{ transaction().operatedAt }}</td>
    <td class="right amount" [class.positive]="transaction().value > 0">
      {{ transaction().value > 0 ? '+' : '' }}{{ formatted() }}
    </td>
  `,
  styles: [`
    :host { display: table-row; }
    :host:hover { background: rgba(255, 255, 255, 0.03); }
    td {
      padding: 14px 8px;
      border-bottom: 1px solid rgba(255, 255, 255, 0.05);
      font-size: 14px;
    }
    .right { text-align: right; }
    .m { display: flex; align-items: center; gap: 10px; }
    .ico {
      width: 32px; height: 32px; border-radius: 10px;
      display: grid; place-items: center;
      background: rgba(255, 255, 255, 0.06);
      border: 1px solid rgba(255, 255, 255, 0.10);
      font-weight: 600;
      font-size: 13px;
    }
    .amount { font-weight: 600; font-variant-numeric: tabular-nums; }
    .amount.positive { color: var(--positive); }
  `],
})
export class TransactionRowComponent {
  readonly transaction = input.required<Transaction>();

  readonly initial = computed(() =>
    this.transaction().description.charAt(0).toUpperCase(),
  );

  readonly formatted = computed(() =>
    new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(
      this.transaction().value,
    ),
  );
}
