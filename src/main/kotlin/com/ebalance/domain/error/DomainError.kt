package com.ebalance.domain.error

/**
 * Base sealed interface for all domain errors.
 */
sealed interface DomainError {
    val message: String
}

/**
 * Errors that can occur during transaction reading operations.
 */
sealed class TransactionReadError : DomainError {
    
    /**
     * The file could not be found at the specified path.
     */
    data class FileNotFound(
        override val message: String,
        val path: String
    ) : TransactionReadError()
    
    /**
     * The file format is invalid or corrupted.
     */
    data class InvalidFormat(
        override val message: String,
        val cause: Throwable? = null
    ) : TransactionReadError()
    
    /**
     * A specific row could not be parsed.
     */
    data class ParseError(
        override val message: String,
        val rowIndex: Int,
        val cause: Throwable? = null
    ) : TransactionReadError()
    
    /**
     * The Excel file is empty or has no valid data rows.
     */
    data class EmptyFile(
        override val message: String
    ) : TransactionReadError()
}

/**
 * Errors that can occur during transaction persistence operations.
 */
sealed class TransactionRepositoryError : DomainError {
    
    /**
     * Could not connect to the database.
     */
    data class ConnectionError(
        override val message: String,
        val cause: Throwable? = null
    ) : TransactionRepositoryError()
    
    /**
     * Failed to insert a transaction.
     */
    data class InsertError(
        override val message: String,
        val cause: Throwable? = null
    ) : TransactionRepositoryError()
    
    /**
     * Failed to query transactions.
     */
    data class QueryError(
        override val message: String,
        val cause: Throwable? = null
    ) : TransactionRepositoryError()
}

/**
 * Errors that can occur during database initialization.
 */
sealed class DatabaseError : DomainError {
    
    /**
     * Failed to create the database file.
     */
    data class CreationError(
        override val message: String,
        val path: String,
        val cause: Throwable? = null
    ) : DatabaseError()
    
    /**
     * Migration failed to apply.
     */
    data class MigrationError(
        override val message: String,
        val version: String? = null,
        val cause: Throwable? = null
    ) : DatabaseError()
}

/**
 * Errors that can occur during the import operation.
 * This is the top-level error type returned by the use case.
 */
sealed class ImportError : DomainError {
    
    /**
     * Error occurred while reading transactions.
     */
    data class ReadError(val error: TransactionReadError) : ImportError() {
        override val message: String = error.message
    }
    
    /**
     * Error occurred while persisting transactions.
     */
    data class PersistenceError(val error: TransactionRepositoryError) : ImportError() {
        override val message: String = error.message
    }
    
    /**
     * The input was empty or contained no valid transactions.
     */
    data class EmptyInput(override val message: String) : ImportError()
}