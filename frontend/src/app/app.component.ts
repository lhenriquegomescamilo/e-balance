import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="app-shell">
      <aside class="glass sidebar">
        <div class="brand">
          <div class="logo">◈</div>
          <div>
            <div class="brand-name">FinGlass</div>
            <div class="brand-sub">Personal finance</div>
          </div>
        </div>

        <nav class="nav">
          <a class="nav-item" routerLink="/transactions" routerLinkActive="active">
            <span class="ico">⇄</span>
            <span>Transactions</span>
          </a>
        </nav>
      </aside>

      <main class="main">
        <router-outlet />
      </main>
    </div>
  `,
  styles: [`
    :host { display: block; }
    .app-shell {
      display: grid;
      grid-template-columns: 260px 1fr;
      gap: 24px;
      padding: 24px;
      min-height: 100vh;
    }
    .sidebar {
      position: sticky;
      top: 24px;
      align-self: start;
      height: calc(100vh - 48px);
      padding: 22px;
      display: flex;
      flex-direction: column;
      gap: 22px;
    }
    .brand { display: flex; align-items: center; gap: 12px; }
    .logo {
      width: 40px; height: 40px; border-radius: 12px;
      display: grid; place-items: center; font-size: 20px;
      background: linear-gradient(135deg, var(--brand-1), var(--brand-2));
      box-shadow: 0 8px 24px -8px rgba(124, 92, 255, 0.6),
                  inset 0 1px 0 rgba(255, 255, 255, 0.4);
    }
    .brand-name { font-weight: 700; letter-spacing: -0.02em; }
    .brand-sub {
      font-size: 11px;
      color: var(--text-muted);
      letter-spacing: 0.08em;
      text-transform: uppercase;
    }

    .nav { display: flex; flex-direction: column; gap: 4px; flex: 1; }
    .nav-item {
      display: flex; align-items: center; gap: 12px;
      padding: 10px 12px;
      border-radius: 12px;
      color: var(--text-secondary);
      font-size: 14px; font-weight: 500;
      transition: background 0.15s ease, color 0.15s ease;
      border: 1px solid transparent;
    }
    .nav-item:hover {
      background: rgba(255, 255, 255, 0.06);
      color: var(--text-primary);
    }
    .nav-item.active {
      background: rgba(255, 255, 255, 0.10);
      border-color: rgba(255, 255, 255, 0.16);
      color: var(--text-primary);
      box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.18);
    }
    .ico { width: 22px; text-align: center; }

    .main { display: flex; flex-direction: column; gap: 24px; min-width: 0; }

    @media (max-width: 960px) {
      .app-shell {
        grid-template-columns: 1fr;
        padding: 16px;
        gap: 16px;
      }
      .sidebar { position: static; height: auto; }
    }
  `],
})
export class AppComponent {}
