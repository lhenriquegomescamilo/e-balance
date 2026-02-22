#!/bin/bash

# E-Balance Orchestration Script
# This script builds, trains, imports transactions, and exports to Google Sheets

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Default values
DB_PATH="e-balance.db"
MODEL_PATH="model.zip"
CREDENTIALS_PATH="credentials.json"
TRAINING_DATASET=""
TRANSACTION_FILE=""
SPREADSHEET_ID=""
BANK_ACCOUNT="Santander"
RESPONSIBLE="Luis Camilo"
BUILD_ONLY=false
SKIP_TRAINING=false
SKIP_IMPORT=false
SKIP_EXPORT=false

# Function to print colored output
print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to show usage
usage() {
    echo "E-Balance Orchestration Script"
    echo ""
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  -b, --db PATH              Database path (default: e-balance.db)"
    echo "  -m, --model PATH          Model path (default: model.zip)"
    echo "  -c, --credentials PATH    Credentials JSON path (default: credentials.json)"
    echo "  -t, --training FILE       Training dataset CSV file"
    echo "  -i, --input FILE         Transaction Excel file to import"
    echo "  -s, --spreadsheet ID     Google Sheets spreadsheet ID"
    echo "  --bank-account NAME      Bank account name (default: Santander)"
    echo "  --responsible NAME       Responsible person (default: Luis Camilo)"
    echo "  --build-only             Only build, skip training/import/export"
    echo "  --skip-training         Skip training step"
    echo "  --skip-import           Skip import step"
    echo "  --skip-export           Skip export step"
    echo "  -h, --help              Show this help message"
    echo ""
    echo "Example:"
    echo "  $0 -i transactions.xls -s 1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms"
    echo ""
    echo "Full workflow (all steps):"
    echo "  $0 -t dataset.csv -i transactions.xls -s <SPREADSHEET_ID>"
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -b|--db)
            DB_PATH="$2"
            shift 2
            ;;
        -m|--model)
            MODEL_PATH="$2"
            shift 2
            ;;
        -c|--credentials)
            CREDENTIALS_PATH="$2"
            shift 2
            ;;
        -t|--training)
            TRAINING_DATASET="$2"
            shift 2
            ;;
        -i|--input)
            TRANSACTION_FILE="$2"
            shift 2
            ;;
        -s|--spreadsheet)
            SPREADSHEET_ID="$2"
            shift 2
            ;;
        --bank-account)
            BANK_ACCOUNT="$2"
            shift 2
            ;;
        --responsible)
            RESPONSIBLE="$2"
            shift 2
            ;;
        --build-only)
            BUILD_ONLY=true
            shift
            ;;
        --skip-training)
            SKIP_TRAINING=true
            shift
            ;;
        --skip-import)
            SKIP_IMPORT=true
            shift
            ;;
        --skip-export)
            SKIP_EXPORT=true
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            print_error "Unknown option: $1"
            usage
            exit 1
            ;;
    esac
done

# Check if required tools are available
check_requirements() {
    print_info "Checking requirements..."
    
    if ! command -v java &> /dev/null; then
        print_error "Java is not installed"
        exit 1
    fi
    
    if [ ! -f "./gradlew" ]; then
        print_error "Gradle wrapper not found. Run this script from the project root."
        exit 1
    fi
    
    print_success "All requirements satisfied"
}

# Build the project
build() {
    print_info "Building project..."
    ./gradlew clean installDist -x test
    print_success "Build completed"
}

# Train the classifier
train() {
    if [ "$SKIP_TRAINING" = true ]; then
        print_warning "Skipping training"
        return
    fi
    
    print_info "Training classifier..."
    
    CMD="./build/install/e-balance/bin/e-balance --model $MODEL_PATH train"
    
    if [ -n "$TRAINING_DATASET" ]; then
        CMD="$CMD --dataset $TRAINING_DATASET"
    fi
    
    eval $CMD
    
    if [ $? -eq 0 ]; then
        print_success "Training completed"
    else
        print_error "Training failed"
        exit 1
    fi
}

# Import transactions
import_transactions() {
    if [ "$SKIP_IMPORT" = true ]; then
        print_warning "Skipping import"
        return
    fi
    
    if [ -z "$TRANSACTION_FILE" ]; then
        print_warning "No transaction file specified, skipping import"
        return
    fi
    
    if [ ! -f "$TRANSACTION_FILE" ]; then
        print_error "Transaction file not found: $TRANSACTION_FILE"
        exit 1
    fi
    
    print_info "Importing transactions from: $TRANSACTION_FILE"
    
    ./build/install/e-balance/bin/e-balance \
        --db "$DB_PATH" \
        --model "$MODEL_PATH" \
        import "$TRANSACTION_FILE"
    
    if [ $? -eq 0 ]; then
        print_success "Import completed"
    else
        print_error "Import failed"
        exit 1
    fi
}

# Export to Google Sheets
export_to_sheets() {
    if [ "$SKIP_EXPORT" = true ]; then
        print_warning "Skipping export"
        return
    fi
    
    if [ -z "$SPREADSHEET_ID" ]; then
        print_warning "No spreadsheet ID specified, skipping export"
        return
    fi
    
    if [ ! -f "$CREDENTIALS_PATH" ]; then
        print_error "Credentials file not found: $CREDENTIALS_PATH"
        print_info "Get credentials from: Google Cloud Console > APIs & Services > Credentials"
        exit 1
    fi
    
    print_info "Exporting to Google Sheets..."
    
    ./build/install/e-balance/bin/e-balance \
        --db "$DB_PATH" \
        export "$SPREADSHEET_ID" \
        --credentials "$CREDENTIALS_PATH" \
        --bank-account "$BANK_ACCOUNT" \
        --responsible "$RESPONSIBLE"
    
    if [ $? -eq 0 ]; then
        print_success "Export completed"
    else
        print_error "Export failed"
        exit 1
    fi
}

# Main execution
main() {
    echo "=========================================="
    echo "  E-Balance Orchestration Script"
    echo "=========================================="
    echo ""
    
    check_requirements
    
    # Build
    build
    
    if [ "$BUILD_ONLY" = true ]; then
        print_info "Build only mode - skipping training, import, and export"
        print_success "Done!"
        exit 0
    fi
    
    # Train
    train
    
    # Import
    import_transactions
    
    # Export
    export_to_sheets
    
    echo ""
    echo "=========================================="
    print_success "All operations completed successfully!"
    echo "=========================================="
}

# Run main function
main
