# E-Balance

A CLI tool for importing bank transactions from Excel files into SQLite database, built with Clean Architecture principles in Kotlin.

## Features

- Import transactions from Excel `.xls` files
- Automatic duplicate detection (skips already imported transactions)
- Automatic transaction categorization with two interchangeable classifier engines:
  - **ParagraphVectors** — DL4J neural word embeddings (default)
  - **Neural Network** — built-in bag-of-words feedforward network (no external ML dependency)
- SQLite database with Flyway migrations
- Database initialized automatically on first run

# Prerequisites

- JDK 17+
- Gradle 8.x (included via wrapper)

## Build

```bash
# Build the project
./gradlew build

# Create distribution
./gradlew installDist
```

## Docker & Makefile

The `Makefile` provides a Docker Compose-based workflow for the full stack (PostgreSQL + backend).

### Services

| Service | Description |
|---------|-------------|
| `postgres` | PostgreSQL 16 on port 5432 |
| `redis` | Redis 7 on port 6379 |
| `flyway-migrator` | Runs DB migrations on startup |
| `backend` | Ktor API on port 8080 |

### Commands

```bash
# Start everything (builds fat JAR first, then starts all containers)
make up

# Rebuild and redeploy only the backend
make restart

# Run backend tests
make test-backend

# Tail backend container logs
make logs

# Stop all services
make down
```

### Database Backup & Restore

```bash
# Dump the full database to backups/ebalance_YYYYMMDD_HHMMSS.sql
make backup

# Restore from a specific backup file
make restore FILE=backups/ebalance_20240101_020000.sql
```

Backup files are written to the `backups/` directory (created automatically). To schedule automatic backups, add a cron entry:

```cron
0 2 * * * cd /path/to/e-balance && make backup
```

---

## Using the Orchestration Script

The `ebalance.sh` script automates the entire workflow:

```bash
# Make it executable (first time only)
chmod +x ebalance.sh

# Full workflow: build + train + import + export
./ebalance.sh -t dataset.csv -i transactions.xls -s <SPREADSHEET_ID>

# Build only
./ebalance.sh --build-only

# Skip training (use existing model)
./ebalance.sh -i transactions.xls -s <SPREADSHEET_ID> --skip-training

# Import only (skip training and export)
./ebalance.sh -i transactions.xls

# Custom paths
./ebalance.sh \
    --db mydb.db \
    --model mymodel.zip \
    --credentials my-credentials.json \
    --training dataset.csv \
    --input transactions.xls \
    --spreadsheet 1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms \
    --bank-account "Novo Banco" \
    --responsible "Maria"
```

## Classifier Engines

Both the `train` and `import` commands accept an `--engine` flag that selects which classifier backend to use.

| `--engine` value | Backend | Model file | Notes |
|---|---|---|---|
| `paragraph-vectors` | DL4J ParagraphVectors | `*.zip` | Default. Best for large, varied datasets. |
| `neural-network` | Built-in feedforward NN | `*.bin` | No DL4J runtime needed at inference time. |

> The `--model` flag (set on the root command) tells every subcommand where to find or save the model file.
> Use a **different path** for each engine so their files don't overwrite each other.

### paragraph-vectors workflow (default)

```bash
# 1. Train
./gradlew run --args="train"
# or with custom paths
./gradlew run --args="--model model.zip train --engine paragraph-vectors --dataset /path/to/dataset.csv --epoch 500"

# 2. Import
./gradlew run --args="import /path/to/transactions.xls"
# or with explicit engine
./gradlew run --args="--model model.zip import /path/to/transactions.xls --engine paragraph-vectors"
```

### neural-network workflow

```bash
# 1. Train — saves a .bin file instead of .zip
./gradlew run --args="--model nn-model.bin train --engine neural-network"

# Custom dataset or epoch count
./gradlew run --args="--model nn-model.bin train --engine neural-network --dataset /path/to/dataset.csv --epoch 1000"

# 2. Import using the neural-network model
./gradlew run --args="--model nn-model.bin import /path/to/transactions.xls --engine neural-network"
```

Both engines read the same dataset CSV (`CATEGORY_ID;BusinessName` format). The neural-network trainer converts the numeric IDs to category enum names automatically.

---

## Usage

### Using Gradle

```bash
# Basic import — paragraph-vectors engine (default)
./gradlew run --args="import /path/to/transactions.xls"

# Import with neural-network engine
./gradlew run --args="--model nn-model.bin import /path/to/transactions.xls --engine neural-network"

# With custom database location
./gradlew run --args="--db /custom/path/mydb.db import /path/to/transactions.xls"

# Train with default engine (paragraph-vectors)
./gradlew run --args="train"

# Train with neural-network engine
./gradlew run --args="--model nn-model.bin train --engine neural-network"

# Train with custom dataset
./gradlew run --args="train --dataset /path/to/dataset.csv"
./gradlew run --args="--model nn-model.bin train --engine neural-network --dataset /path/to/dataset.csv"

# Export transactions to Google Sheets
./gradlew run --args="export <SPREADSHEET_ID>"
```

### Using Distribution (without Gradle)

```bash
# Build and install the distribution
./gradlew clean installDist

# Alias for brevity (optional)
alias ebalance="./build/install/e-balance/bin/e-balance"

# Import — default engine (paragraph-vectors)
ebalance import /path/to/transactions.xls

# Import — neural-network engine
ebalance --model nn-model.bin import /path/to/transactions.xls --engine neural-network

# With custom database
ebalance --db /custom/path.db import /path/to/transactions.xls

# Train — default engine (paragraph-vectors)
ebalance train

# Train — neural-network engine
ebalance --model nn-model.bin train --engine neural-network

# Train with custom dataset
ebalance train --dataset /path/to/dataset.csv
ebalance --model nn-model.bin train --engine neural-network --dataset /path/to/dataset.csv

# Export transactions to Google Sheets
ebalance export <SPREADSHEET_ID>

# Export with custom bank account and responsible
ebalance export <SPREADSHEET_ID> --bank-account "Novo Banco" --responsible "Maria"

# Show help
ebalance --help
ebalance train --help
ebalance import --help
```

### Example

#### With paragraph-vectors (default)

```bash
# Train
./build/install/e-balance/bin/e-balance train

# Import
./build/install/e-balance/bin/e-balance import /path/to/transactions.xls
```

Output:
```
Initializing database: e-balance.db
Training category classifier...
Engine:  paragraph-vectors
...
Training completed successfully!
  Dataset entries: 123
  Epochs:          500
  Model saved to:  model.zip

Importing transactions from: /path/to/transactions.xls
Classifier engine:           paragraph-vectors
Classifier model loaded:     model.zip
Import completed:
  Total read:      329
  Inserted:        318
  Duplicates:      11
  Classified:      318
```

#### With neural-network

```bash
# Train (saves nn-model.bin instead of model.zip)
./build/install/e-balance/bin/e-balance --model nn-model.bin train --engine neural-network

# Import using the neural-network model
./build/install/e-balance/bin/e-balance --model nn-model.bin import /path/to/transactions.xls --engine neural-network
```

Output:
```
Initializing database: e-balance.db
Training category classifier...
Engine:  neural-network
Model:   nn-model.bin
Epochs:  500
...
Training completed successfully!
  Dataset entries: 123
  Epochs:          500
  Model saved to:  nn-model.bin

Importing transactions from: /path/to/transactions.xls
Classifier engine:           neural-network
Classifier model loaded:     nn-model.bin
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

## Google Sheets Export

### Prerequisites

1. **Create a Google Cloud Project**:
   - Go to [Google Cloud Console](https://console.cloud.google.com/)
   - Create a new project

2. **Enable Google Sheets API**:
   - Go to "APIs & Services" > "Library"
   - Search for "Google Sheets API" and enable it

3. **Create Service Account Credentials**:
   - Go to "APIs & Services" > "Credentials"
   - Click "Create Credentials" > "Service Account"
   - Fill in the details and create the account
   - Once created, go to the "Keys" tab
   - Click "Add Key" > "Create new key" > "JSON"
   - Save the JSON file as `credentials.json` in your project directory

4. **Share Your Spreadsheet**:
   - Open your Google Sheet
   - Click "Share"
   - Add the service account email (found in your JSON file) as an editor

### Export Command

The export command maps transactions to the following spreadsheet columns:

| Column | Description | Source |
|--------|-------------|--------|
| A | Categoria | Classified category (from ML model) |
| B | Valor | Transaction amount (€X.XX format) |
| C | Conta Bancária | Bank account (default: Santander) |
| D | Responsável | Responsible person (default: Luis Camilo) |
| E | Tipo | Type: "Fixa" or "Variáda" (based on category) |
| F | Observações | Transaction description |
| G | Data Pagamento | Payment date (DD/MM/YYYY) |

```bash
# Basic export (exports all transactions from database)
./build/install/e-balance/bin/e-balance export <SPREADSHEET_ID>

# With custom credentials file
./build/install/e-balance/bin/e-balance export <SPREADSHEET_ID> --credentials /path/to/credentials.json

# With custom bank account and responsible
./build/install/e-balance/bin/e-balance export <SPREADSHEET_ID> --bank-account "Novo Banco" --responsible "Maria"
```

### Finding Your Spreadsheet ID

The spreadsheet ID is found in the URL:
```
https://docs.google.com/spreadsheets/d/<SPREADSHEET_ID>/edit
```

Example: If URL is `https://docs.google.com/spreadsheets/d/1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms/edit`
Then the spreadsheet ID is: `1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms`

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
│   ├── InitCommand.kt               # Root command — DB init + shared deps
│   ├── ClassifierEngine.kt          # Enum for --engine option values
│   ├── ImportCommand.kt             # Import subcommand (owns classifier wiring)
│   ├── TrainCommand.kt              # Train classifier subcommand
│   └── ExportCommand.kt             # Google Sheets export subcommand
├── domain/
│   └── model/
│       └── Transaction.kt           # Domain entity
├── application/
│   ├── port/
│   │   ├── CategoryClassifierPort.kt # Classifier abstraction
│   │   ├── TransactionReader.kt     # Port interface
│   │   └── TransactionRepository.kt # Port interface
│   └── usecase/
│       └── ImportTransactionsUseCase.kt
└── infrastructure/
    ├── classification/
    │   ├── CategoryClassifierAdapter.kt        # paragraph-vectors engine adapter
    │   └── NeuralNetworkClassifierAdapter.kt   # neural-network engine adapter
    ├── persistence/
    │   ├── DatabaseFactory.kt       # SQLite + Flyway setup
    │   └── SQLiteTransactionRepository.kt
    └── excel/
        └── ExcelTransactionReader.kt

classification/src/main/kotlin/com/ebalance/classification/
├── TextClassifier.kt                 # DL4J ParagraphVectors classifier
├── CategoryClassifier.kt            # Either-based wrapper for TextClassifier
├── CategoryClassifierTrainer.kt     # Training service for paragraph-vectors
├── TextClassifierNeuralNetwork.kt   # Bag-of-words feedforward neural network
└── NeuralNetworkClassifier.kt       # Adapter: train/save/load/predict (text-level)

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
| DL4j | 1.0.0-M2.1 | ML classifier (ParagraphVectors engine) |
| Google API Client | 2.0.0 | Google APIs client |
| Google Sheets API | v4-rev612-1.25.0 | Google Sheets integration |
| Google Auth Library | 1.16.0 | OAuth2 authentication |
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
