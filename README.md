# E-Balance

A CLI tool for importing bank transactions from Excel files into SQLite database, built with Clean Architecture principles in Kotlin.

## Features

- Import transactions from Excel `.xls` files
- Automatic duplicate detection (skips already imported transactions)
- Automatic transaction categorization using ML classifier (DL4j ParagraphVectors)
- SQLite database with Flyway migrations
- Database initialized automatically on first run

## Prerequisites

- JDK 17+
- Gradle 8.x (included via wrapper)

## Build

```bash
# Build the project
./gradlew build

# Create distribution
./gradlew installDist
```

## Usage

### Using Gradle

```bash
# Basic import (creates e-balance.db in current directory)
./gradlew run --args="import /path/to/transactions.xls"

# With custom database location
./gradlew run --args="--db /custom/path/mydb.db import /path/to/transactions.xls"

# With custom model path (for classification)
./gradlew run --args="--model /custom/path/model.zip import /path/to/transactions.xls"

# Train the classifier (requires dataset at classpath:dataset/category.for.training.csv)
./gradlew run --args="train"

# Train with custom dataset
./gradlew run --args="train --dataset /path/to/dataset.csv"

# Train with custom model path (overrides default model.zip)
./gradlew run --args="train --model /custom/path/model.zip"
```

### Using Distribution (without Gradle)

```bash
# Build and install the distribution
./gradlew clean installDist

# Run the application
./build/install/e-balance/bin/e-balance import /path/to/transactions.xls

# With custom database
./build/install/e-balance/bin/e-balance --db /custom/path.db import /path/to/transactions.xls

# With custom model path (for classification)
./build/install/e-balance/bin/e-balance --model /custom/path/model.zip import /path/to/transactions.xls

# Train the classifier (requires dataset at classpath:dataset/category.for.training.csv)
./build/install/e-balance/bin/e-balance train

# Train with custom dataset
./build/install/e-balance/bin/e-balance train --dataset /path/to/dataset.csv

# Train with custom model path (overrides default model.zip)
./build/install/e-balance/bin/e-balance train --model /custom/path/model.zip

# Show help
./build/install/e-balance/bin/e-balance --help
```

### Example

```bash
# First, train the classifier with custom model path (optional but recommended for categorization)
./build/install/e-balance/bin/e-balance train --model /custom/path/model.zip

# Then import transactions using the custom model
./build/install/e-balance/bin/e-balance --model /custom/path/model.zip import src/main/resources/.tmp/transactions/descarga.xls
```

Output:
```
Initializing database: e-balance.db
Training category classifier...
Using default dataset from classpath
Training completed successfully!
  Dataset entries: 123
  Model saved to:  model.zip

Importing transactions from: src/main/resources/.tmp/transactions/descarga.xls
Import completed:
  Total read:      329
  Inserted:        318
  Duplicates:      11
  Classified:      318
```

## Excel File Format

The tool expects Excel files with the following structure:

| Row | Content |
|-----|---------|
| 0-5 | Metadata (ignored) |
| 6 | Column headers |
| 7+ | Transaction data |

**Columns:**

| Column | Description | Example |
|--------|-------------|---------|
| A | Data Operação (Operation Date) | `23-02-2026` |
| B | Data valor (Value Date) | Ignored |
| C | Descrição (Description) | `Repsol E0394` |
| D | Montante (Amount in EUR) | `-75.01` |
| E | Saldo (Balance in EUR) | `578.96` |

**Date Format:** `DD-MM-YYYY`

## Database Schema

```sql
CREATE TABLE transactions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    operated_at TEXT NOT NULL,      -- ISO date format: YYYY-MM-DD
    description TEXT NOT NULL,
    value REAL NOT NULL,            -- Negative for expenses, positive for income
    balance REAL NOT NULL,
    category_id INTEGER DEFAULT 0   -- Foreign key to category table
);

-- Category table (predefined categories)
CREATE TABLE category (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE
);

-- Unique constraint prevents duplicate imports
CREATE UNIQUE INDEX idx_transactions_unique 
ON transactions(operated_at, description, value);
```

## Duplicate Detection

Transactions are considered duplicates when they have the same:
- Operation date (`operated_at`)
- Description
- Amount (`value`)

Balance is not part of the unique constraint.

## Project Structure

```
src/main/kotlin/com/ebalance/
├── Main.kt                           # Entry point
├── cli/
│   ├── InitCommand.kt               # Root command + dependency injection
│   ├── ImportCommand.kt             # Import subcommand
│   └── TrainCommand.kt               # Train classifier subcommand
├── domain/
│   └── model/
│       └── Transaction.kt           # Domain entity
├── application/
│   ├── port/
│   │   ├── TransactionReader.kt     # Port interface
│   │   └── TransactionRepository.kt # Port interface
│   └── usecase/
│       └── ImportTransactionsUseCase.kt
└── infrastructure/
    ├── persistence/
    │   ├── DatabaseFactory.kt       # SQLite + Flyway setup
    │   └── SQLiteTransactionRepository.kt
    └── excel/
        └── ExcelTransactionReader.kt

classification/src/main/kotlin/com/ebalance/classification/
├── TextClassifier.kt                 # DL4j ParagraphVectors classifier
├── CategoryClassifier.kt            # Wrapper with Either error handling
└── CategoryClassifierTrainer.kt     # Training service

src/main/resources/
└── db/migration/
    ├── V1__Create_transactions_table.sql
    └── V2__Create_category_table.sql
```

## Architecture

```
┌─────────────────┐     ┌──────────────────────┐     ┌─────────────────────┐
│  CLI Layer      │────▶│  Application Layer   │────▶│  Infrastructure     │
│  (Clikt)        │     │  (Use Cases, Ports)  │     │  (SQLite, POI)      │
└─────────────────┘     └──────────────────────┘     └─────────────────────┘
                                    │
                                    ▼
                        ┌──────────────────────┐
                        │    Domain Layer      │
                        │    (Transaction)     │
                        └──────────────────────┘
```

**Clean Architecture Principles:**
- Domain layer has no dependencies on frameworks
- Application layer defines ports (interfaces)
- Infrastructure layer implements ports
- Dependency injection at CLI layer

## Testing

```bash
# Run all tests
./gradlew test

# Run with verbose output
./gradlew test --info
```

**Test Stack:**
- Kotest (DescribeSpec style)
- MockK (for mocking)
- kotlinx-coroutines-test

## Error Handling

The application uses Arrow's `Either` for functional error handling with sealed error types:

```kotlin
// Error types are sealed classes for exhaustive pattern matching
sealed class ImportError : DomainError {
    data class ReadError(val error: TransactionReadError) : ImportError()
    data class PersistenceError(val error: TransactionRepositoryError) : ImportError()
    data class EmptyInput(override val message: String) : ImportError()
}

// Use case returns Either<ImportError, Result>
suspend fun execute(inputStream: InputStream): Either<ImportError, Result>

// Pattern match with fold
result.fold(
    ifLeft = { error -> handleError(error) },
    ifRight = { success -> handleSuccess(success) }
)
```

**Error Categories:**
- `TransactionReadError` - File not found, invalid format, parse errors
- `TransactionRepositoryError` - Connection, insert, query errors
- `DatabaseError` - Creation, migration errors

## Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| Clikt | 5.0.1 | CLI framework |
| Apache POI | 5.5.1 | Excel parsing |
| SQLite JDBC | 3.45.2.0 | Database driver |
| Flyway | 10.10.0 | Database migrations |
| Kotlinx Coroutines | 1.8.0 | Async operations |
| Arrow Core | 2.0.1 | Functional error handling |
| DL4j | 1.0.0-M2.1 | ML classifier (ParagraphVectors) |
| Kotest | 6.1.3 | Testing framework |
| MockK | 1.14.9 | Mocking library |

## Development

### Adding a New Migration

1. Create `V3__Your_migration_name.sql` in `src/main/resources/db/migration/`
2. Run the application - Flyway will apply the migration automatically

### Querying the Database

```bash
# Count transactions
sqlite3 e-balance.db "SELECT COUNT(*) FROM transactions;"

# View recent transactions with categories
sqlite3 e-balance.db "SELECT t.operated_at, t.description, t.value, c.name as category FROM transactions t LEFT JOIN category c ON t.category_id = c.id ORDER BY t.operated_at DESC LIMIT 10;"

# View all categories
sqlite3 e-balance.db "SELECT * FROM category ORDER BY id;"

# Export to CSV
sqlite3 -csv e-balance.db "SELECT * FROM transactions;" > transactions.csv
```

## License

MIT
