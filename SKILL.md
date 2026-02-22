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
result.bind()  // In either { } block
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

**Version:** 1.0.0  
**Last Updated:** 2026-02-22  
**Kotlin Version:** 2.3.0  
**JDK Version:** 17+
