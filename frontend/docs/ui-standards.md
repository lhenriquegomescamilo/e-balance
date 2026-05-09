# FinGlass UI Standards

Concrete rules for building UI in this project. Every standard below is
**derived from existing code** — when in doubt, copy the cited file rather
than inventing a new pattern.

All tokens (`--brand-*`, `--glass-*`, `--text-*`, `--positive`, `--negative`,
`--radius-*`) are defined in `src/styles.scss`. **Never hard-code** colors,
radii, or blur values that have a token — use the token.

---

## 1. Color palette

### 1.1 Tokens

| Token             | Value                | Use                                          |
|-------------------|----------------------|----------------------------------------------|
| `--brand-1`       | `#7c5cff` (purple)   | Primary action gradient start, focus rings   |
| `--brand-2`       | `#4ad3ff` (cyan)     | Primary action gradient end, chart stroke    |
| `--brand-3`       | `#ff7ad9` (pink)     | Avatar / accent gradient                     |
| `--accent-mint`   | `#59f3c4`            | Tertiary accent (also used by `--positive`)  |
| `--accent-peach`  | `#ffb38a`            | Tertiary accent                              |
| `--positive`      | `#59f3c4`            | Income, "saved", under-budget chips          |
| `--negative`      | `#ff6f91`            | Over-budget, error chips, deletion           |
| `--text-primary`  | `rgba(255,255,255,.96)` | Body and headings                         |
| `--text-secondary`| `rgba(255,255,255,.66)` | Sub-labels, captions, muted text          |
| `--text-muted`    | `rgba(255,255,255,.42)` | Placeholder, disabled, "kicker" labels    |
| `--glass-bg`      | `rgba(255,255,255,.06)` | Default glass fill                        |
| `--glass-bg-strong` | `rgba(255,255,255,.10)` | Elevated glass fill                     |
| `--glass-border`  | `rgba(255,255,255,.14)` | Default glass border                      |
| `--glass-border-strong` | `rgba(255,255,255,.22)` | Elevated glass border               |
| `--glass-blur`    | `28px`               | Default backdrop-filter blur                 |
| `--radius-sm/md/lg/xl` | `12 / 18 / 26 / 34px` | Standard corner radii                  |

### 1.2 Semantic rules

- **Money up = `--positive`.** Money down = default text color, *not* red.
  Red (`--negative`) is reserved for errors, over-budget, deletes.
- **Brand gradient** (`linear-gradient(135deg, --brand-1, --brand-2)`) is for
  the **single primary action** on a page (`.btn-primary`) and the brand
  logo only. Don't apply it to cards or chips.
- Account / category cards may use **per-item gradients** stored on the data
  object (see `Account.color` in `core/models/finance.models.ts`). Always
  inject via `[style.background]="a.color"` — never duplicate the gradient
  string in CSS.
- Text hierarchy: **primary → secondary → muted**. If you need a fourth
  level, use opacity on the parent rather than adding a new color.
- **Never** introduce a new hex outside `:root`. If you need a new color,
  add it as a token in `src/styles.scss`.

### 1.3 Suggested gradient combinations (already in use)

```
linear-gradient(135deg, #7c5cff, #4ad3ff)   /* checking, brand */
linear-gradient(135deg, #59f3c4, #4ad3ff)   /* savings, positive */
linear-gradient(135deg, #ff7ad9, #7c5cff)   /* credit, accent */
linear-gradient(135deg, #ffb38a, #ff7ad9)   /* investment, warm */
linear-gradient(135deg, #4ad3ff, #7c5cff)   /* generic */
linear-gradient(135deg, #59f3c4, #ffb38a)   /* generic */
```

Pick from this set when assigning category/account colors so the palette
stays cohesive.

---

## 2. Cards

Reference: `dashboard.component.ts` (`.hero`, `.stat`, `.tx`, `.budgets`),
`accounts.component.ts` (`.card` with `.card-top` + `.card-body`),
`budgets.component.ts` (`.card`), `settings.component.ts` (`.panel`).

### 2.1 Base rule

Every card is `.glass` (or `.glass.glass-strong` for elevation). **Do not**
recreate frosted backgrounds with hand-rolled `backdrop-filter` rules — use
the primitive.

```html
<div class="glass card">…</div>
```

### 2.2 Padding

| Card type                     | Padding   |
|-------------------------------|-----------|
| Hero / large feature card     | `28px`    |
| Standard panel / stat card    | `22px`    |
| Sub-card nested inside a card | `14–18px` |
| Topbar (pill)                 | `12px 14px` |

### 2.3 Anatomy

```
┌─ glass card ──────────────────────────────────┐
│ ┌─ row-head ─────────────────────────────┐    │
│ │  .h-card  (uppercase kicker)           │    │
│ │  .h-display / .h-section (value)       │    │
│ │                              link →    │    │
│ └────────────────────────────────────────┘    │
│  body content                                 │
└───────────────────────────────────────────────┘
```

- Kicker label: `.h-card` (12px, uppercase, letter-spacing 0.08em, secondary).
- Primary value: `.h-display` (38px, 700, tight tracking) for hero,
  `.h-section` (20px, 600) for sub-sections.
- Right-side action: `<a class="muted small">View all →</a>`.

### 2.4 Two-tone "credential" cards (accounts)

When a card represents a tangible item (account, card, asset), split it:

- **Top half**: gradient (`[style.background]="item.color"`),
  `padding: 22px`, white text, with `box-shadow: inset 0 1px 0 rgba(255,255,255,0.4)`
  and a subtle `::after` gloss highlight.
- **Bottom half**: `.card-body` on the glass surface with `padding: 18px 22px`,
  metadata rows + actions.

See `accounts.component.ts:55-74`.

### 2.5 Don'ts

- No solid (non-rgba) backgrounds on cards.
- No `box-shadow` other than `var(--glass-shadow)` or the established inset
  highlight.
- Don't nest more than one `.glass` inside another — flatten with a plain
  `rgba(255,255,255,0.04)` sub-card instead (see `.b-card`).

---

## 3. Grid system

Reference: `dashboard.component.ts` (12-col), `accounts.component.ts` /
`budgets.component.ts` / `settings.component.ts` (auto-fit).

### 3.1 Two layout modes — pick one per page

**A. 12-column structured grid** — for the dashboard or any page with
heterogeneous card sizes.

```scss
.grid {
  display: grid;
  grid-template-columns: repeat(12, 1fr);
  gap: 20px;
}
.hero    { grid-column: span 12; }
.stat    { grid-column: span 4; }
.tx      { grid-column: span 7; }
.budgets { grid-column: span 5; }
```

**B. Auto-fit grid** — for lists of equivalent items (account cards, budget
cards, setting panels).

```scss
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 18px;
}
```

Standard `minmax` floors per use case:
- Account / budget cards: `minmax(280px, 1fr)` — gap `18–20px`.
- Settings panels: `minmax(320px, 1fr)` — gap `20px`.
- Inline mini-cards (e.g. `.acc-grid`, `.b-grid`): `minmax(220px, 1fr)`,
  gap `14px`.

### 3.2 Gaps (consistent ladder)

`8 → 10 → 12 → 14 → 16 → 18 → 20 → 22 → 24` px. Never use values outside
this ladder; never use odd numbers.

### 3.3 Breakpoints

| Width    | Behavior                                                    |
|----------|-------------------------------------------------------------|
| `≤ 1100` | 12-col cards collapse to `span 12` (single column).         |
| `≤ 960`  | App shell collapses sidebar inline; padding drops 24→16.    |
| `≤ 600`  | Topbar hides search shortcut + "Add" button (icons only).   |

Use these exact thresholds — don't introduce new ones.

### 3.4 Page shell

The app shell (`app.component.ts`) is fixed:
`grid-template-columns: 260px 1fr; gap: 24px; padding: 24px`. Don't override
it from inside features.

---

## 4. Tables

Reference: `transactions.component.ts` (`.tbl`, `.table-wrap`).

### 4.1 Wrap

```html
<div class="glass table-wrap">
  <table class="tbl">…</table>
</div>
```

`.table-wrap` uses `padding: 8px 22px` so the first/last rows breathe inside
the glass border.

### 4.2 Header

```scss
.tbl th {
  text-align: left;
  font-size: 11px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--text-muted);
  padding: 16px 8px;
  font-weight: 500;
  border-bottom: 1px solid rgba(255,255,255,0.08);
}
```

### 4.3 Body row

```scss
.tbl td {
  padding: 14px 8px;
  border-bottom: 1px solid rgba(255,255,255,0.05);
  font-size: 14px;
}
.tbl tr:last-child td { border-bottom: none; }
.tbl tr:hover td       { background: rgba(255,255,255,0.03); }
```

### 4.4 Column conventions

| Column type      | Class / treatment                                     |
|------------------|-------------------------------------------------------|
| Identifier (name + icon) | `<div class="m"><span class="ico">…</span><span>{{ name }}</span></div>` |
| Category / tag   | `<span class="chip">{{ category }}</span>`            |
| Secondary meta   | `class="muted"`                                       |
| Date             | ISO `yyyy-MM-dd`, `class="muted"`                     |
| Amount (money)   | `class="right amount"` + `font-variant-numeric: tabular-nums` |
| Positive amount  | additional `.positive` class — prefix with `+`        |

### 4.5 Empty / loading state

When data is empty, render a single full-width `<tr>` with a muted message
inside the glass wrap; never show an empty `<table>`. (Pattern not yet in
code — establish it the first time you need it.)

### 4.6 Don'ts

- No vertical borders. Rows are separated horizontally only.
- No striped rows. Hover provides the visual cue.
- Don't sort/paginate client-side once the real API lands — push it to the
  backend (`/api/v1/transactions` already accepts filters).

---

## 5. Fields & forms

Reference: `settings.component.ts` (`.field`, `.switch`),
`transactions.component.ts` (`.filters`).

### 5.1 Standard text / select field

```html
<label class="field">
  <span>Display name</span>
  <input [(ngModel)]="name" />
</label>
```

```scss
.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  color: var(--text-secondary);
}
.field input,
.field select {
  background: rgba(255,255,255,0.06);
  border: 1px solid rgba(255,255,255,0.12);
  border-radius: 12px;
  padding: 10px 12px;
  color: var(--text-primary);
  font-family: inherit;
  font-size: 14px;
  outline: none;
  transition: border 0.15s ease, background 0.15s ease;
}
.field input:focus,
.field select:focus {
  border-color: rgba(124, 92, 255, 0.6);   /* brand-1 @ 60% */
  background: rgba(255,255,255,0.08);
}
.field select option { background: #14141e; }   /* solid bg for native dropdown */
```

### 5.2 Toggle switch

Pill switch with brand gradient when checked. Width 46×26, knob 20×20,
`translateX(20px)` on check. Copy the markup and styles from
`settings.component.ts:99-120`.

### 5.3 Inline filter bar

Pill-shaped `.glass` container, `display: flex; gap: 8px; padding: 8px;
border-radius: 999px;`. Inputs inside are transparent (no border, no
background) — the wrapper provides the chrome. See
`transactions.component.ts:60-66`.

### 5.4 Validation

- Invalid state: `border-color: var(--negative)` plus a `<small class="muted negative">` message below the input.
- Don't shake, don't tooltip — keep error text inline and persistent.
- Disabled state: `opacity: 0.5; pointer-events: none;` on the wrapper.

### 5.5 Forms binding

Use **template-driven forms** (`FormsModule`, `[(ngModel)]`) for simple
settings. Reach for **reactive forms** only when you have cross-field
validation or dynamic field arrays. Bind to **signals**, not plain
properties — this matches the rest of the codebase.

---

## 6. Search field

Reference: `topbar.component.ts`.

The "global search" pattern is a pill with **three slots**: leading icon,
input, trailing keyboard hint.

```html
<div class="search">
  <span class="ico">⌕</span>
  <input type="text" placeholder="Search transactions, accounts, categories…" />
  <span class="kbd">⌘K</span>
</div>
```

```scss
.search {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  padding: 8px 14px;
  background: rgba(255,255,255,0.05);
  border: 1px solid rgba(255,255,255,0.10);
  border-radius: 999px;
}
.search input {
  flex: 1;
  background: transparent;
  border: none;
  outline: none;
  color: var(--text-primary);
  font-size: 14px;
  font-family: inherit;
}
.search input::placeholder { color: var(--text-muted); }
.kbd {
  font-size: 11px;
  padding: 3px 8px;
  border-radius: 6px;
  background: rgba(255,255,255,0.08);
  border: 1px solid rgba(255,255,255,0.14);
  color: var(--text-secondary);
}
```

Rules:
- **Always pill-shaped** (`border-radius: 999px`). No square search inputs.
- The `⌥`/`⌘K` hint is **decorative**; the corresponding handler must be
  wired with `@HostListener` or `fromEvent(document, 'keydown')` when search
  is implemented.
- Hide the `.kbd` chip below 600px (`@media (max-width: 600px) { .kbd { display: none; } }`).
- Debounce real searches with **250ms** before hitting the API. Bind through
  a signal + `effect()` or a `toSignal(input.valueChanges.pipe(debounceTime(250)))`.
- For inline filters (page-scoped, not global), use the `.filters` pill bar
  pattern from §5.3 — not the global search style.

---

## 7. Line / trend charts

Reference: `shared/components/trend-chart.component.ts`.

### 7.1 Use the existing component

The trend sparkline is a pure-SVG `app-trend-chart` that takes
`data: number[]` via `input.required<number[]>()`. **Reuse it** for any
line chart that fits the sparkline use case.

```html
<app-trend-chart [data]="series()" />
```

### 7.2 When to extend it (not fork it)

If a new chart needs axes, multiple series, tooltips, or interaction, do
**not** copy-paste `trend-chart.component.ts`. Instead:

1. Generalize `TrendChartComponent` with additional signal inputs
   (e.g. `axes`, `series[]`, `tooltipFormatter`), keeping the existing
   single-series API as the default.
2. Or, once the chart needs ApexCharts (named in the root `CLAUDE.md` target
   stack), introduce it as a single new component (`app-area-chart`,
   `app-bar-chart`) that other features consume — never `<apexchart>` in
   feature templates directly.

### 7.3 Sparkline rules (current implementation)

- ViewBox: `0 0 600 180`. `preserveAspectRatio="none"` so it stretches
  fluidly to its container.
- Container needs an explicit height — wrap in `<div class="chart-wrap"
  style="height: 180px">` or set `:host { height: ... }`.
- **Line gradient** runs left → right: `--brand-1` (#7c5cff) → `--brand-2`
  (#4ad3ff). Stroke width `2.4`, `linecap` and `linejoin` `round`.
- **Fill gradient** runs top → bottom: `#7c5cff @ 0.55` → `#4ad3ff @ 0`.
  Use this exact pair so the chart matches the brand.
- **Curve** is a smoothed Bezier (`Q` + `T` controls). Do not switch to
  straight `L` segments without a strong reason — the smoothness is part
  of the brand.
- **Padding**: `padY = 14` keeps the line off the edges.
- **Min/max** are derived per render (`Math.min/max(...d)`); never assume
  zero-anchored axes for a sparkline.

### 7.4 Color rules for charts

- **Single-series**: brand gradient as above.
- **Two series**: `--brand-1` and `--accent-mint` (purple + mint).
- **Positive vs negative bars**: `--positive` and `--negative`.
- **Categorical multi-series**: cycle through the gradient list in §1.3.
- Always render the line/area on top of a faint fill — never a hairline alone
  on the dark background.

---

## 8. Icons

The codebase currently uses **two icon styles** and no icon library.

### 8.1 Glyph icons (UI chrome)

Used in nav, search, brand. These are **single Unicode characters** chosen
for their geometric weight:

| Where        | Glyph | Notes                              |
|--------------|-------|------------------------------------|
| Brand mark   | `◈`   | Inside `.logo` 40×40 gradient tile |
| Dashboard    | `◐`   | Sidebar nav                        |
| Transactions | `⇄`   | Sidebar nav                        |
| Budgets      | `◔`   | Sidebar nav                        |
| Accounts     | `▤`   | Sidebar nav                        |
| Settings     | `⚙`   | Sidebar nav                        |
| Search       | `⌕`   | Topbar leading icon                |
| Trend up     | `▲`   | Inside chips                       |
| Arrow        | `→`   | Inline links ("View all →")        |

Rules:
- One glyph per nav slot. Don't combine glyph + emoji in the same UI region.
- Wrap glyphs in `<span class="ico">` so they inherit the parent color and
  can be sized via the container.
- **Width is fixed** in lists: `.ico { width: 22px; text-align: center; }`
  (sidebar) or 32–40px squares for transaction rows / merchant tiles.

### 8.2 Emoji icons (data-driven)

Used in the **data layer** for transactions, budgets, categories
(see `Transaction.icon: string` in `core/models/finance.models.ts`).

Rules:
- Emoji belong **only on data records**. Never put an emoji in static UI
  chrome — use a glyph or, eventually, an SVG icon.
- Render inside a square tile with frosted background:

  ```scss
  .tx-ico, .ico {
    width: 32px; height: 32px;          /* or 40×40 in larger lists */
    border-radius: 10px;                /* 12px for 40px size */
    display: grid; place-items: center;
    background: rgba(255,255,255,0.06);
    border: 1px solid rgba(255,255,255,0.10);
    font-size: 18px;
  }
  ```

- For categories that come from the backend (no emoji field), pick a
  fallback character from the first letter of the category name styled
  inside the same tile.

### 8.3 Future: SVG icon set

If/when an SVG icon system is added, prefer **Lucide** or **Phosphor**
(both fit the thin-stroke aesthetic). Wrap them in a single
`<app-icon name="..." size="..." />` component so callers don't import
icon packages directly. Don't introduce Material Icons or FontAwesome
— they clash with the design language.

---

## 9. Composition checklist (before opening a PR)

- [ ] Every color, radius, blur comes from a `--token`, not a literal.
- [ ] Every card uses `.glass` (or `.glass.glass-strong`).
- [ ] Padding chosen from the standard ladder (§2.2 / §3.2).
- [ ] Money values use `Intl.NumberFormat` and `tabular-nums`.
- [ ] Tables follow the §4 anatomy; positive amounts get `.positive`.
- [ ] Inputs follow §5.1; focus uses `--brand-1` at 60%.
- [ ] Charts reuse `TrendChartComponent` or extend it generically.
- [ ] Icons follow the glyph (chrome) vs emoji (data) split.
- [ ] No new font, no new icon library, no new UI library introduced.
- [ ] Mobile breakpoints `1100 / 960 / 600` honored.
