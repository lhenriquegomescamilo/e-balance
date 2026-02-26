package com.ebalance.transactions.application

import com.ebalance.transactions.domain.CategoryEntry
import com.ebalance.transactions.domain.TransactionRepository

/** Input port: returns all categories for populating filter dropdowns. */
interface GetCategoriesUseCase {
    fun execute(): List<CategoryEntry>
}

class GetCategoriesInteractor(
    private val repository: TransactionRepository
) : GetCategoriesUseCase {
    override fun execute(): List<CategoryEntry> = repository.getCategories()
}
