# E-Balance

A CLI tool for importing bank transactions from Excel files into SQLite database, built with Clean Architecture principles in Kotlin.

## Features

- Import transactions from Excel `.xls` files
- Automatic duplicate detection (skips already imported transactions)
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
```

### Using Distribution

```bash
# Build distribution
./gradlew installDist

# Run the application
./build/install/e-balance/bin/e-balance import /path/to/transactions.xls

# With custom database
./build/install/e-balance/bin/e-balance --db /custom/path.db import /path/to/transactions.xls

# Show help
./build/install/e-balance/bin/e-balance --help
```

### Example

```bash
./build/install/e-balance/bin/e-balance import src/main/resources/.tmp/transactions/descarga.xls
```

Output:
```
Initializing database: e-balance.db
Importing transactions from: src/main/resources/.tmp/transactions/descarga.xls
Import completed:
  Total read:      329
  Inserted:        318
  Duplicates:      11
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
    balance REAL NOT NULL
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
│   └── ImportCommand.kt             # Import subcommand
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

src/main/resources/
└── db/migration/
    └── V1__Create_transactions_table.sql
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

## Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| Clikt | 5.0.1 | CLI framework |
| Apache POI | 5.5.1 | Excel parsing |
| SQLite JDBC | 3.45.2.0 | Database driver |
| Flyway | 10.10.0 | Database migrations |
| Kotlinx Coroutines | 1.8.0 | Async operations |
| Kotest | 6.1.3 | Testing framework |
| MockK | 1.14.9 | Mocking library |

## Development

### Adding a New Migration

1. Create `V2__Your_migration_name.sql` in `src/main/resources/db/migration/`
2. Run the application - Flyway will apply the migration automatically

### Querying the Database

```bash
# Count transactions
sqlite3 e-balance.db "SELECT COUNT(*) FROM transactions;"

# View recent transactions
sqlite3 e-balance.db "SELECT * FROM transactions ORDER BY operated_at DESC LIMIT 10;"

# Export to CSV
sqlite3 -csv e-balance.db "SELECT * FROM transactions;" > transactions.csv
```

## License

MIT
