package com.ebalance.transactions.application

import com.ebalance.transactions.domain.TransactionRepository

/** Input port: update the category assigned to a single transaction. */
interface UpdateTransactionCategoryUseCase {
    /** @throws NoSuchElementException if the transaction or category does not exist. */
    fun execute(transactionId: Long, categoryId: Long)
}

class UpdateTransactionCategoryInteractor(
    private val repository: TransactionRepository
) : UpdateTransactionCategoryUseCase {
    override fun execute(transactionId: Long, categoryId: Long) =
        repository.updateTransactionCategory(transactionId, categoryId)
}
