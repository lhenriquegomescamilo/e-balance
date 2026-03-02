# E-Balance Development Skill

A specialized skill for developing and maintaining the E-Balance CLI application - a Kotlin-based transaction import tool built with Clean Architecture and functional error handling.

## Overview

E-Balance is a CLI tool that imports bank transactions from Excel files into SQLite database. It demonstrates production-grade Kotlin development with:

- **Clean Architecture** - Separation of Domain, Application, and Infrastructure layers
- **Functional Error Handling** - Arrow's `Either` with sealed error types
- **Database Migrations** - Flyway for schema versioning
- **Coroutines** - Structured concurrency with injected dispatchers
- **TDD-ready** - Kotest + MockK testing stack

## Project Structure

```
src/main/kotlin/com/ebalance/
├── Main.kt                           # Entry point, wires dependencies
├── cli/                              # CLI layer (Clikt commands)
│   ├── InitCommand.kt               # Root command + DI container
│   └── ImportCommand.kt             # Import subcommand
├── domain/                           # Domain layer (no framework deps)
│   ├── model/
│   │   └── Transaction.kt           # Core entity
│   └── error/
│       └── DomainError.kt           # Sealed error types
├── application/                      # Application layer (use cases + ports)
│   ├── port/
│   │   ├── TransactionReader.kt     # Input port interface
│   │   └── TransactionRepository.kt # Output port interface
│   └── usecase/
│       └── ImportTransactionsUseCase.kt
└── infrastructure/                   # Infrastructure layer (implementations)
    ├── persistence/
    │   ├── DatabaseFactory.kt       # SQLite + Flyway setup
    │   └── SQLiteTransactionRepository.kt
    └── excel/
        └── ExcelTransactionReader.kt # Apache POI implementation

src/main/resources/
└── db/migration/
    └── V1__Create_transactions_table.sql
```

## Architecture Patterns

### Layer Dependencies

```
CLI Layer → Application Layer → Infrastructure Layer
                  ↓
           Domain Layer (core, no deps)
```

**Rules:**
- Domain layer has ZERO framework dependencies
- Application layer defines ports (interfaces only)
- Infrastructure layer implements ports
- CLI layer wires everything together (DI)

### Error Handling with runCatching

Use `runCatching` + `fold` for exception handling that returns Either:

```kotlin
// Return Either from functions
suspend fun read(inputStream: InputStream): Either<TransactionReadError, List<Transaction>>

// Use runCatching + fold in infrastructure layer
private fun openWorkbook(inputStream: InputStream): Either<TransactionReadError.InvalidFormat, HSSFWorkbook> = 
    runCatching { HSSFWorkbook(inputStream) }
        .fold(
            onSuccess = { it.right() },
            onFailure = { e ->
                TransactionReadError.InvalidFormat(
                    message = "Failed to read Excel file: ${e.message}",
                    cause = e
                ).left()
            }
        )

// Use generateSequence for lazy iteration
private fun parseTransactions(workbook: HSSFWorkbook): List<Transaction> {
    val sheet = workbook.getSheetAt(0)
    return generateSequence(sheet.getRow(7)) { row ->
        val nextRowNum = row.rowNum + 1
        if (nextRowNum <= sheet.lastRowNum) sheet.getRow(nextRowNum) else null
    }
        .mapNotNull { row -> parseRow(row) }
        .toList()
}
```

### Sealed Class Pattern for Errors

```kotlin
// Domain error hierarchy
sealed interface DomainError {
    val message: String
}

sealed class TransactionReadError : DomainError { ... }
sealed class TransactionRepositoryError : DomainError { ... }
sealed class ImportError : DomainError { ... }
```

### Port Interface Pattern

```kotlin
// Port = Interface in application layer
interface TransactionRepository {
    data class SaveResult(val inserted: Int, val duplicates: Int)
    
    suspend fun save(transaction: Transaction): Either<TransactionRepositoryError, Boolean>
    suspend fun saveAll(transactions: List<Transaction>): Either<TransactionRepositoryError, SaveResult>
}

// Implementation in infrastructure layer using runCatching
class SQLiteTransactionRepository(
    private val dbPath: String,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : TransactionRepository {
    // Implementation using runCatching + fold
}
```

### Coroutines Best Practices

```kotlin
// ALWAYS inject CoroutineDispatcher for testability
class ImportTransactionsUseCase(
    private val reader: TransactionReader,
    private val repository: TransactionRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO  // Injected!
) {
    suspend fun execute(inputStream: InputStream): Either<ImportError, Result> = 
        withContext(ioDispatcher) {
            // I/O operations here
        }
}

// In tests, use StandardTestDispatcher
val testDispatcher = StandardTestDispatcher()
val useCase = ImportTransactionsUseCase(reader, repository, testDispatcher)
```

## Common Tasks

### Adding a New Use Case

1. Create port interface in `application/port/`:
```kotlin
interface ReportGenerator {
    suspend fun generate(year: Int): Either<ReportError, Report>
}
```

2. Create use case in `application/usecase/`:
```kotlin
class GenerateReportUseCase(
    private val repository: TransactionRepository,
    private val generator: ReportGenerator,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun execute(year: Int): Either<ReportError, Report> = withContext(ioDispatcher) {
        either {
            val transactions = repository.findByYear(year).bind()
            generator.generate(year).bind()
        }
    }
}
```

3. Implement in `infrastructure/`
4. Wire in `cli/InitCommand.kt`
5. Add CLI command in `cli/`

### Adding a New Error Type

1. Define in `domain/error/DomainError.kt`:
```kotlin
sealed class ReportError : DomainError {
    data class NoData(val year: Int) : ReportError() {
        override val message = "No transactions found for year $year"
    }
    data class GenerationFailed(override val message: String, val cause: Throwable?) : ReportError()
}
```

2. Map to ImportError if needed:
```kotlin
sealed class ImportError : DomainError {
    // ... existing errors
    data class ReportError(val error: ReportError) : ImportError() {
        override val message = error.message
    }
}
```

### Adding a Database Migration

1. Create `V{n}__Description.sql` in `src/main/resources/db/migration/`:
```sql
-- V2__Add_category_column.sql
ALTER TABLE transactions ADD COLUMN category TEXT DEFAULT 'Uncategorized';
CREATE INDEX idx_transactions_category ON transactions(category);
```

2. Run the application - Flyway applies automatically

### Adding a New CLI Command

1. Create command class:
```kotlin
class ReportCommand : CliktCommand(name = "report") {
    private val year: Int by option("--year", "-y")
        .int()
        .required()
    
    private val dependencies: InitCommand.Dependencies by requireObject()
    
    override fun help(context: Context) = "Generate annual transaction report"
    
    override fun run() {
        val useCase = dependencies.generateReportUseCase
        
        runBlocking {
            useCase.execute(year).fold(
                ifLeft = { error -> echo("Error: ${error.message}", err = true) },
                ifRight = { report -> echo(report.toString()) }
            )
        }
    }
}
```

2. Register in `Main.kt`:
```kotlin
InitCommand()
    .subcommands(ImportCommand(), ReportCommand())
    .main(args)
```

## Testing Guidelines

### Test Structure

```kotlin
class ImportTransactionsUseCaseTest : DescribeSpec({
    
    val testDispatcher = StandardTestDispatcher()
    
    beforeSpec { Dispatchers.setMain(testDispatcher) }
    afterSpec { Dispatchers.resetMain() }
    
    describe("ImportTransactionsUseCase") {
        lateinit var reader: TransactionReader
        lateinit var repository: TransactionRepository
        lateinit var useCase: ImportTransactionsUseCase
        
        beforeEach {
            reader = mockk()
            repository = mockk()
            useCase = ImportTransactionsUseCase(reader, repository, testDispatcher)
        }
        
        it("should handle success") {
            runTest {
                coEvery { reader.read(any()) } returns transactions.right()
                coEvery { repository.saveAll(any()) } returns SaveResult(3, 0).right()
                
                val result = useCase.execute(inputStream)
                
                result.fold(
                    ifLeft = { throw AssertionError("Expected Right") },
                    ifRight = { it.totalRead shouldBe 3 }
                )
            }
        }
    }
})
```

### Test Patterns

```kotlin
// Mock Either returns
coEvery { reader.read(any()) } returns transactions.right()
coEvery { reader.read(any()) } returns TransactionReadError.InvalidFormat("msg").left()

// Assert Either results
result.fold(
    ifLeft = { it.shouldBeInstanceOf<TransactionReadError.InvalidFormat>() },
    ifRight = { it shouldHaveSize 3 }
)

// Use TestFixtures for test data
val transaction = TestFixtures.aTransaction(description = "Test")
val transactions = TestFixtures.aListOfTransactions(5)
```

## Commands

### Build & Test

```bash
./gradlew build              # Build + test
./gradlew test               # Run tests only
./gradlew test --info        # Verbose test output
./gradlew installDist        # Create distribution
```

### Run Application

```bash
# Via Gradle
./gradlew run --args="import /path/to/file.xls"
./gradlew run --args="--db /custom.db import /path/to/file.xls"

# Via distribution
./build/install/e-balance/bin/e-balance import /path/to/file.xls
./build/install/e-balance/bin/e-balance --help
```

### Import Command

```bash
# Import transactions from Excel file
./gradlew run --args="import /path/to/transactions.xls"

# With custom database
./gradlew run --args="--db my-database.db import /path/to/transactions.xls"

# Train the ML classifier (required before first import)
./gradlew run --args="train"
./gradlew run --args="--dataset /path/to/custom-dataset.csv train"
```

### Export Command

Export transactions to Google Sheets. Creates a separate sheet for each Month/Year.

```bash
# Export all transactions to Google Sheets
./gradlew run --args="export <SPREADSHEET_ID>"

# With custom credentials file
./gradlew run --args="--credentials /path/to/credentials.json export <SPREADSHEET_ID>"

# With custom bank account and responsible
./gradlew run --args="-b 'Nubank' -r 'John Doe' export <SPREADSHEET_ID>"
```

**Features:**
- Groups transactions by Month/Year (e.g., "02/2026", "01/2026")
- Creates a new sheet for each Month/Year if it doesn't exist
- Appends to existing sheet if it already exists
- Columns: Categoria | Valor | Conta Bancária | Responsável | Tipo | Observações | Data Pagamento

**Get Spreadsheet ID:**
From the URL: `https://docs.google.com/spreadsheets/d/<SPREADSHEET_ID>/edit`

### Train Command

Train the ML classifier model.

```bash
# Train with default dataset
./gradlew run --args="train"

# Train with custom dataset
./gradlew run --args="--dataset /path/to/dataset.csv train"
```

The model is saved to `model.zip` and used automatically during import.

### Database Operations

```bash
# Query database
sqlite3 e-balance.db "SELECT COUNT(*) FROM transactions;"
sqlite3 e-balance.db "SELECT * FROM transactions ORDER BY operated_at DESC LIMIT 10;"

# Export to CSV
sqlite3 -csv e-balance.db "SELECT * FROM transactions;" > transactions.csv

# Check migrations
sqlite3 e-balance.db "SELECT * FROM flyway_schema_history;"
```

## Code Style Guidelines

### Kotlin Conventions

- Use `data class` for models and DTOs
- Use `sealed class/interface` for error types and state
- Prefer `val` over `var`
- Use extension functions for utility operations
- Use scope functions (`use`, `let`, `also`) appropriately

### Naming Conventions

```kotlin
// UseCase: Verb + Noun + UseCase
class ImportTransactionsUseCase

// Repository: Entity + Repository
interface TransactionRepository

// Error: Category + Error
sealed class TransactionReadError

// Command: Noun + Command
class ImportCommand

// Port interfaces: Noun (no suffix)
interface TransactionReader
```

### File Organization

```kotlin
// 1. Package declaration
package com.ebalance.application.usecase

// 2. Imports (alphabetically grouped)
import arrow.core.Either
import arrow.core.raise.either
import com.ebalance.domain.error.ImportError
import kotlinx.coroutines.Dispatchers

// 3. KDoc for public APIs
/**
 * Use case for importing transactions.
 * @param reader Transaction reader port
 * @param repository Transaction repository port
 */
class ImportTransactionsUseCase(...) {
    // 4. Companion object (if needed)
    
    // 5. Public properties/functions
    
    // 6. Private helpers
}
```

## Common Pitfalls

### ❌ Don't

```kotlin
// Don't use GlobalScope
GlobalScope.launch { ... }

// Don't throw exceptions for expected errors
throw TransactionReadException("Invalid format")

// Don't use null for error states
fun read(): List<Transaction>?  // Bad

// Don't skip dispatcher injection
class UseCase(reader: Reader) {  // Missing dispatcher!
    suspend fun execute() = withContext(Dispatchers.IO) { ... }
}

// Don't put framework deps in domain
import org.apache.poi.*  // In domain layer!

// Don't use try-catch for error handling - use runCatching + fold
try {
    riskyOperation()
} catch (e: Exception) {
    // Bad - not idiomatic
}
```

### ✅ Do

```kotlin
// Inject dispatcher for testability
class UseCase(reader: Reader, ioDispatcher: CoroutineDispatcher = Dispatchers.IO)

// Use Either for errors
fun read(): Either<TransactionReadError, List<Transaction>>

// Use sealed classes for error types
sealed class TransactionReadError : DomainError

// Keep domain pure
// Domain layer only has: data classes, sealed classes, pure functions

// Use runCatching + fold for exception handling
runCatching { riskyOperation() }
    .fold(
        onSuccess = { it.right() },
        onFailure = { e -> Error(e).left() }
    )

// Use .bind() with Raise DSL
either {
    val result = operation().bind()  // Short-circuits on Left
    ...
}

// Use generateSequence for lazy iteration
generateSequence { iterator.nextOrNull() }
    .map { it }
    .toList()
```

## Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| Clikt | 5.0.1 | CLI framework |
| Apache POI | 5.5.1 | Excel parsing |
| SQLite JDBC | 3.45.2.0 | Database driver |
| Flyway | 10.10.0 | Database migrations |
| Kotlinx Coroutines | 1.8.0 | Async operations |
| Arrow Core | 2.0.1 | Functional error handling |
| Kotest | 6.1.3 | Testing framework |
| MockK | 1.14.9 | Mocking library |

## Quick Reference

### runCatching + fold Pattern

```kotlin
// Catch exceptions and convert to Either
runCatching { riskyOperation() }
    .fold(
        onSuccess = { it.right() },
        onFailure = { e -> ErrorType(e.message, e).left() }
    )

// create Right
value.right()
Either.Right(value)

// Create Left
error.left()
Either.Left(error)

// Map error type
result.mapLeft { ImportError.ReadError(it) }

// Extract or throw
result.bind()  // In Giveeither { } block
result.getOrNull()  // Nullable
result.getOrHandle { defaultValue }  // With default

// Pattern match
result.fold(
    ifLeft = { handle(it) },
    ifRight = { use(it) }
)
```

### generateSequence Pattern

```kotlin
// Lazy sequence from iterator
generateSequence { iterator.nextOrNull() }
    .map { transform(it) }
    .filter { predicate(it) }
    .toList()

// Lazy sequence from nullable
generateSequence { 
    if (hasMore) getNext() else null 
}
```

### Clikt Patterns

```kotlin
// Option with default
private val dbPath: String by option("--db", "-d")
    .default("e-balance.db")

// Required option
private val year: Int by option("--year", "-y")
    .int()
    .required()

// Argument
private val file: Path by argument()
    .path(mustExist = true)

// Subcommand dependencies
private val deps: Dependencies by requireObject()
```

---

## Backend — Ktor REST API + Dashboard

A Ktor-based REST API that serves a single-file HTML/JS dashboard. It reads from the same SQLite database that the CLI populates.

### Tech Stack

| Layer | Technology |
|-------|-----------|
| HTTP server | Ktor 3.4.0 (Netty engine) |
| Dependency injection | Koin 4.1.2-Beta1 |
| Serialization | kotlinx.serialization (JSON) |
| Database | SQLite via plain JDBC (`sqlite-jdbc 3.45.3.0`) |
| Frontend | Tailwind CSS CDN + Flowbite 2.3.0 + ApexCharts 3.48.0 |

Run the server: `./gradlew :server:run` (listens on port 8080)
Working directory when running: `backend/server/` → DB path must be `../../e-balance.db`

---

### Backend Structure

```
backend/server/src/main/kotlin/
├── Application.kt                        # Ktor entry point
├── Frameworks.kt                         # Koin install, DB path resolution, logging
├── Routing.kt                            # Top-level route wiring, injects use cases via Koin
├── Serialization.kt                      # ContentNegotiation + kotlinx.json install
├── HTTP.kt                               # CORS plugin (anyHost)
└── com/ebalance/transactions/
    ├── TransactionModule.kt              # Koin module — binds all singletons
    ├── domain/
    │   ├── TransactionFilter.kt          # Filter value object (dates, categoryIds, type, page, pageSize)
    │   ├── TransactionPage.kt            # Paginated result (rows, total, page, pageSize, totalPages)
    │   ├── TransactionSummaryResult.kt   # Summary aggregate (totals + category breakdown)
    │   ├── MonthlySummaryResult.kt       # Monthly trend (months[], series[])
    │   ├── TransactionRow.kt             # Single transaction entity
    │   ├── CategoryEntry.kt              # Category (id, name, enumName)
    │   └── TransactionRepository.kt      # Port interface (getSummary, getTransactions, getCategories, getMonthlySummary, updateTransactionCategory)
    ├── application/
    │   ├── GetTransactionSummaryUseCase.kt
    │   ├── GetTransactionsUseCase.kt     # Returns TransactionPage (paginated)
    │   ├── GetCategoriesUseCase.kt
    │   ├── GetMonthlySummaryUseCase.kt   # Overrides dates → full DB (1900–2100)
    │   └── UpdateTransactionCategoryUseCase.kt
    └── infrastructure/
        ├── persistence/
        │   └── TransactionRepositoryImpl.kt  # Plain JDBC, one connection per call
        └── web/
            ├── TransactionRoutes.kt          # All REST endpoints
            └── dto/
                ├── TransactionSummaryResponse.kt  # All DTOs (summary, list, monthly, categories, patch)
                └── ErrorResponse.kt

backend/server/src/main/resources/
├── application.yaml                      # Port 8080, database.path: ../../e-balance.db
└── static/
    └── index.html                        # Single-file dashboard (Tailwind + Flowbite + ApexCharts)
```

---

### Database Schema

```sql
-- Tables (created by Flyway in the CLI project)
transactions(id INTEGER PRIMARY KEY, operated_at TEXT, description TEXT, value REAL, balance REAL, category_id INT)
category(id INTEGER PRIMARY KEY, name TEXT, enum_name TEXT)

-- Sign convention: value > 0 = INCOME, value < 0 = EXPENSE
-- operated_at stored as ISO-8601 text: "YYYY-MM-DD"
```

---

### API Endpoints

Base path: `/api/v1`

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/transactions/summary` | Aggregated totals + category breakdown |
| `GET` | `/transactions` | Paginated list of rows |
| `GET` | `/transactions/monthly-by-category` | Monthly trend (full DB, ignores date filter) |
| `GET` | `/categories` | All categories for dropdowns |
| `PATCH` | `/transactions/{id}/category` | Update a transaction's category |

#### Common query parameters (parsed by `parseFilter`)

| Param | Default | Notes |
|-------|---------|-------|
| `startDate` | 30 days ago | ISO-8601 `YYYY-MM-DD` |
| `endDate` | today | ISO-8601 `YYYY-MM-DD` |
| `categories` | all | comma-separated category IDs |
| `type` | `ALL` | `INCOME` \| `EXPENSE` \| `ALL` |
| `page` | `1` | for `/transactions` only |
| `pageSize` | `20` | for `/transactions` only (max 200) |

#### PATCH /transactions/{id}/category

Request body:
```json
{ "categoryId": 5 }
```
Response (`200 OK`):
```json
{ "transactionId": 123, "categoryId": 5, "message": "Category updated successfully" }
```
Errors: `404` if transaction/category not found, `400` if invalid params.

---

### Architecture Patterns

#### Use Case pattern (same as CLI side)

```kotlin
// 1. Interface in application layer
interface GetTransactionsUseCase {
    fun execute(filter: TransactionFilter): TransactionPage
}

// 2. Interactor (implementation) — delegates to repository
class GetTransactionsInteractor(
    private val repository: TransactionRepository
) : GetTransactionsUseCase {
    override fun execute(filter: TransactionFilter): TransactionPage =
        repository.getTransactions(filter)
}
```

#### Pagination pattern

`TransactionFilter` carries `page` (1-based) and `pageSize`. `TransactionRepositoryImpl.getTransactions()` runs two queries on one connection:

```kotlin
// 1. COUNT(*) with same WHERE (no ORDER BY) — gives total
// 2. SELECT ... ORDER BY ... LIMIT ? OFFSET ? — gives the page

val totalPages = ceil(total / pageSize).coerceAtLeast(1)
return TransactionPage(rows, total, page, pageSize, totalPages)
```

Response DTO:
```json
{
  "transactions": [...],
  "total": 143,
  "page": 2,
  "pageSize": 20,
  "totalPages": 8
}
```

#### Monthly summary — full DB, no date filter

`GetMonthlySummaryInteractor` always overrides the caller's dates:

```kotlin
override fun execute(filter: TransactionFilter) =
    repository.getMonthlySummary(filter.copy(
        startDate = LocalDate.of(1900, 1, 1),
        endDate   = LocalDate.of(2100, 12, 31)
    ))
```

SQL groups by `strftime('%Y-%m', operated_at)` × `category_id`. Missing months for a category are zero-filled in Kotlin post-processing.

#### Error responses

All endpoints return `ErrorResponse(error: String, message: String)` on failure:

| Condition | Status |
|-----------|--------|
| Bad date format | `400 INVALID_DATE` |
| Invalid param | `400 INVALID_PARAMETER` |
| Not found | `404 NOT_FOUND` |
| Unexpected | `500 INTERNAL_ERROR` |

---

### Adding a New Endpoint (step by step)

1. **Domain** — add method to `TransactionRepository.kt`:
```kotlin
fun getYearlySummary(filter: TransactionFilter): YearlySummaryResult
```

2. **Domain** — create result type in `domain/`:
```kotlin
data class YearlySummaryResult(val years: List<String>, ...)
```

3. **Application** — create use case in `application/`:
```kotlin
interface GetYearlySummaryUseCase { fun execute(filter: TransactionFilter): YearlySummaryResult }
class GetYearlySummaryInteractor(private val repo: TransactionRepository) : GetYearlySummaryUseCase {
    override fun execute(filter: TransactionFilter) = repo.getYearlySummary(filter)
}
```

4. **Infrastructure / persistence** — implement in `TransactionRepositoryImpl.kt`

5. **Infrastructure / web / dto** — add DTO class to `TransactionSummaryResponse.kt`:
```kotlin
@Serializable
data class YearlySummaryResponse(val years: List<String>, ...)
```

6. **Infrastructure / web** — add route to `TransactionRoutes.kt`:
```kotlin
get("/transactions/yearly-by-category") {
    try {
        val filter = parseFilter(call.request)
        val result = yearlySummaryUseCase.execute(filter)
        call.respond(HttpStatusCode.OK, YearlySummaryResponse(...))
    } catch (e: DateTimeParseException) { ... }
}
```
Remember to add the new use case to the function signature of `transactionRoutes(...)`.

7. **DI** — register in `TransactionModule.kt`:
```kotlin
single<GetYearlySummaryUseCase> { GetYearlySummaryInteractor(get()) }
```

8. **Routing** — inject and pass in `Routing.kt`:
```kotlin
val yearlySummaryUseCase: GetYearlySummaryUseCase by inject()
// ...
transactionRoutes(..., yearlySummaryUseCase)
```

---

### Frontend Dashboard (`static/index.html`)

Single-file SPA — no build step. Uses:
- **Tailwind CSS CDN** with `darkMode: 'class'` (toggled on `<html>`, persisted in `localStorage`)
- **Flowbite 2.3.0** for UI components
- **ApexCharts 3.48.0** for charts
- **Font Awesome** for icons

Key JavaScript globals:

| Variable | Purpose |
|----------|---------|
| `API_BASE` | `'/api/v1'` |
| `currentPage` | current transactions page (reset to 1 on filter change) |
| `PAGE_SIZE` | `20` |
| `cachedCategories` | category list fetched once at boot, reused by the edit modal |
| `lastSummaryData` | last summary response (reused by chart view toggle) |
| `lastMonthlyData` | monthly chart data (fetched once, never re-fetched) |
| `monthlyChartInstance` | ApexCharts instance (used for `showSeries`/`hideSeries`) |
| `allSeriesVisible` | toggle state for "Deselect All / Select All" button |

Key functions:

| Function | Description |
|----------|------------|
| `updateAll()` | Fetches summary + paginated transactions, re-renders stats/donut/table/pagination |
| `loadMonthlyChart()` | Fetches full-DB monthly data once at boot |
| `buildParams()` | Builds filter query params (date, category, type) |
| `buildTxParams()` | `buildParams()` + `page` + `pageSize` |
| `renderTable(transactions)` | Renders rows; category badges are clickable → opens edit modal |
| `renderPagination(txData)` | Renders page info + ellipsis-trimmed page buttons |
| `openCategoryModal(txId, desc, catId)` | Opens the category-edit modal |
| `saveCategoryChange()` | PATCH call with loading state; closes modal + calls `updateAll()` on success |

Default date range: `2025-11-01` → today (hardcoded in `initDateFilters()`).

---

### Backend Build & Run

```bash
# Run the server (from backend/)
./gradlew :server:run

# Build fat JAR
./gradlew :server:buildFatJar

# Open dashboard
open http://localhost:8080
```

The server logs the resolved DB path on startup:
```
Database path → /absolute/path/to/e-balance.db (exists: true)
```

If `exists: false`, check that `database.path` in `application.yaml` resolves correctly relative to `backend/server/`.

---

**Version:** 1.1.0
**Last Updated:** 2026-02-27
**Kotlin Version:** 2.3.0
**JDK Version:** 17+
