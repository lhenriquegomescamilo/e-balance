# Frontend — CLAUDE.md

Guidelines for Claude Code when working inside `frontend/`.

This project follows **Feature-Based + Clean Architecture + Facade Pattern**
on top of Angular 21 + NgRx 21 + Apollo Angular (GraphQL). The architectural
spec lives in `docs/prompt.md`; UI conventions live in `docs/ui-standards.md`.
**Read both** before making non-trivial changes.

---

## 1. Project identity

- **Internal name**: `finglass` (see `package.json` and `angular.json`)
- **Purpose**: SPA front-end for the E-Balance personal finance backend.
- **Design language**: "Apple Liquid Glass" — frosted blur, gradient orbs,
  pill controls, dark theme. Tokens live in `src/styles.scss`; rules in
  `docs/ui-standards.md`.

## 2. Tech stack

| Concern         | Library / Tool                                                |
|-----------------|---------------------------------------------------------------|
| Framework       | Angular **21.x** (standalone components only)                 |
| Reactivity      | Angular Signals (`signal`, `computed`, `input.required`)      |
| State mgmt      | **NgRx 21** (`@ngrx/store`, `@ngrx/effects`, `store-devtools`) |
| Data fetching   | **Apollo Angular 13** + `@apollo/client` 4 over **GraphQL**   |
| HTTP (REST)     | `@angular/common/http` (`provideHttpClient`)                  |
| Routing         | `provideRouter` + lazy `loadChildren` per feature             |
| Forms           | `@angular/forms` (template-driven by default; reactive when needed) |
| Styling         | Plain SCSS — global tokens in `src/styles.scss`, per-component `styles: [...]` |
| Charts          | (Not yet — when needed, prefer pure SVG; ApexCharts is the long-term plan) |
| Tests           | (Not yet wired — Vitest is the target)                        |
| Build           | `@angular-devkit/build-angular:application`                   |
| TypeScript      | 5.9, `strict: true`, `strictTemplates: true`, `noPropertyAccessFromIndexSignature: true` |
| Node            | 20.19+ or 22.12+ (Angular 21 requirement)                     |

## 3. Architecture (mandatory)

Combination: **Feature-Based + Clean Architecture + Facade Pattern**.

```
src/app/features/<feature>/
├── application/
│   ├── facade/        # Facade services exposing Signals to components
│   ├── store/         # NgRx (actions, reducers, effects, selectors, state)
│   └── services/      # Application-level orchestration (optional)
├── domain/
│   ├── models/        # TS interfaces / types
│   ├── entities/      # Business entities (when distinct from DTOs)
│   └── use-cases/     # Pure business logic
├── infrastructure/
│   ├── api/           # Apollo queries/mutations + thin API service wrapper
│   └── repositories/  # DTO ↔ entity mapping
└── presentation/
    ├── pages/         # Smart/container components (routable)
    ├── components/    # Dumb/presentational components
    └── routes/        # Feature routing config (lazy-loaded)
```

Hard rules:

- **No global `components/`, `services/`, `reducers/`, `actions/` at the app root.**
- Components **must not** inject `Store` directly. They consume a Facade,
  which exposes state via `store.selectSignal(...)` and dispatches actions
  internally.
- Every component is `standalone: true` and uses `ChangeDetectionStrategy.OnPush`.
- Lazy-loaded feature routes register their NgRx state via `provideState(feature)`
  + `provideEffects([...])` in the route's `providers: []` — **not** at the
  app root.
- The reference implementation is **`features/transactions/`**. Copy its
  layer-by-layer shape when adding a new feature.

## 4. Folder layout (current)

```
src/
├── index.html
├── main.ts                      # bootstrapApplication + all global providers
├── styles.scss                  # Liquid Glass design system (tokens + .glass)
└── app/
    ├── app.component.ts         # shell (sidebar + <router-outlet>)
    ├── app.routes.ts            # top-level routes, lazy-load each feature
    └── features/
        └── transactions/
            ├── application/
            │   ├── facade/transaction.facade.ts
            │   └── store/{actions,reducer,effects,selectors,state}.ts
            ├── domain/
            │   ├── models/transaction.model.ts
            │   └── use-cases/get-transactions.use-case.ts
            ├── infrastructure/
            │   ├── api/{transaction.queries.ts, transaction.api.service.ts}
            │   └── repositories/transaction.repository.ts
            └── presentation/
                ├── pages/transaction-list.page.ts
                ├── components/transaction-row.component.ts
                └── routes/transaction.routes.ts
```

## 5. Data flow (one direction, every time)

```
Component (page)
   └─► TransactionFacade.load(filter)
          └─► Store.dispatch(TransactionActions.load)
                 └─► TransactionEffects.load$
                        └─► GetTransactionsUseCase.execute(filter)
                               └─► TransactionRepository.findAll(filter)
                                      └─► TransactionApiService.queryTransactions()
                                             └─► Apollo.watchQuery(TRANSACTIONS_QUERY)
                                                    └─► /graphql (proxied to :8080)
                 ◄─ TransactionActions.loadSuccess({ items })
   ◄─ Facade exposes facade.transactions: Signal<readonly Transaction[]>
```

When adding a feature, **build it in this order**: domain → infrastructure
→ application/store → application/facade → presentation. Don't shortcut.

## 6. Build & run

```bash
cd frontend
npm install
npm start          # ng serve --open --proxy-config proxy.conf.json → http://localhost:4200
npm run build      # production build → dist/finglass
npm run watch      # dev build, watch mode
```

The dev server proxies `/api` and `/graphql` to `http://localhost:8080` —
see `proxy.conf.json`.

## 7. Backend integration

- The Ktor backend serves on `http://localhost:8080`.
- REST endpoints live under `/api/v1/...` (transactions, summary,
  categories, import). These are wrapped by the existing REST clients in
  the backend tooling — Claude code in `frontend/` should prefer **GraphQL**
  for new feature work.
- **GraphQL endpoint at `/graphql` does not exist on the backend yet.**
  Apollo Angular is wired and will hit it once the backend gateway lands.
  Until then, the transactions page renders the loading state and then an
  error — that is expected.
- The expected GraphQL schema (consumed by `infrastructure/api/transaction.queries.ts`):

  ```graphql
  input TransactionFilterInput {
    startDate: String
    endDate: String
    categoryIds: [ID!]
    type: TransactionType
  }
  enum TransactionType { INCOME EXPENSE ALL }
  type Transaction {
    id: ID!
    operatedAt: String!
    description: String!
    value: Float!
    balance: Float!
    category: Category
  }
  type Category { id: ID!  name: String! }
  type Query {
    transactions(filter: TransactionFilterInput): [Transaction!]!
  }
  ```
- For SSE endpoints (e.g. `/api/v1/transactions/import`), keep using
  `fetch` + `ReadableStream` from inside an `infrastructure/api/*.ts`
  service. Do not use `EventSource`.

## 8. Code conventions

- **Standalone + OnPush** on every component.
- **Signals** for component / facade state. Reserve RxJS for HTTP, Apollo
  observables, and effect streams.
- **`inject()`** instead of constructor DI.
- **Lazy routes** per feature via `loadChildren` → feature `routes/`.
- **`createFeature` + `createActionGroup`** for NgRx — gives you typed
  selectors and grouped actions for free.
- **`createEffect` with `switchMap`** when a new request supersedes the old
  one (default for "load" effects).
- **GraphQL queries** are colocated with the feature in
  `infrastructure/api/<feature>.queries.ts` using the `gql` tag from
  `apollo-angular`. Do not use `@apollo/client/core`'s `gql` — it bypasses
  Apollo Angular's parser cache.
- **DTO → entity mapping** lives in `infrastructure/repositories/`. The
  domain layer never sees a DTO.
- **Currency formatting**: `Intl.NumberFormat('en-US', { style: 'currency',
  currency: 'USD' })`. Source the currency from a settings facade once
  there is one — don't hard-code USD.
- TypeScript is strict; index access requires bracket notation with explicit
  non-null assertions where appropriate.

## 9. Liquid Glass design system

> Detailed component-level rules (cards, tables, grids, fields, charts,
> icons, search, color palette) live in **`docs/ui-standards.md`**.
> Read it before building any new UI.

All tokens and primitives live in `src/styles.scss`:

- `:root` CSS custom properties: `--brand-1/2/3`, `--accent-mint/peach`,
  `--glass-bg`, `--glass-border`, `--glass-blur`, `--positive`, `--negative`,
  `--radius-sm/md/lg/xl`.
- `.glass` primitive (frosted card), `.glass-strong` (elevated variant).
- `.btn` (frosted) and `.btn-primary` (brand gradient).
- `.chip`, `.chip.positive`, `.chip.negative`.
- Typography helpers: `.h-display`, `.h-section`, `.h-card`, `.muted`, `.dim`.
- Animated background orbs and grain are global on `body::before` /
  `body::after`.

When building new UI, **reuse these classes** instead of re-creating frosted
panels. Per-component styles should layer onto `.glass`, not replace it.

## 10. What NOT to do

- Don't add Tailwind, Flowbite, Material, or another component library
  without explicit user agreement — it would conflict with the Liquid Glass
  design system.
- Don't import `Store` from `@ngrx/store` in components. Always go through a
  Facade.
- Don't call `HttpClient` or `Apollo` directly from components, facades,
  reducers, or use cases. They live in `infrastructure/api/`.
- Don't add new files under `app/core/` or `app/shared/` for things that
  conceptually belong to a single feature. Co-locate them under that
  feature instead.
- Don't introduce a new state store (Akita, Elf, NGXS, Signal Store) — NgRx
  is the single source of truth.
- Don't bypass the facade by calling a use-case from a component directly,
  even when "it's just one read."
- Don't ship `console.log` in committed code.

## 11. Adding a new feature — checklist

1. Create the folder skeleton (`application/{facade,store}`, `domain/{models,use-cases}`,
   `infrastructure/{api,repositories}`, `presentation/{pages,components,routes}`).
2. Define the **domain model** first (interface + filter type).
3. Write the **GraphQL query** + **API service** + **repository** (DTO → entity).
4. Write the **NgRx feature** (`state`, `actions`, `reducer` via `createFeature`,
   `selectors`, `effects` calling the use-case).
5. Write the **use-case** that the effect calls (it should be a one-liner
   delegating to the repository, but it's the seam where business rules live).
6. Write the **facade** that exposes signals + intent methods.
7. Write the **page** (smart, injects only the facade) and any **dumb
   components**.
8. Write the **routes** file with `provideState(feature)` + `provideEffects([...])`.
9. Register the feature route in `app.routes.ts` via `loadChildren`.
10. Run `npm run build` and confirm zero errors.
