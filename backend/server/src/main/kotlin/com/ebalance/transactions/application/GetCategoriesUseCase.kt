package com.ebalance.transactions.application

import com.ebalance.transactions.domain.CategoryEntry
import com.ebalance.transactions.domain.TransactionRepository

/** Input port: returns categories for populating filter dropdowns.
 *  When [ids] is non-empty only the matching categories are returned. */
interface GetCategoriesUseCase {
    fun execute(ids: List<Long> = emptyList()): List<CategoryEntry>
}

class GetCategoriesInteractor(
    private val repository: TransactionRepository
) : GetCategoriesUseCase {
    override fun execute(ids: List<Long>): List<CategoryEntry> {
        val all = repository.getCategories()
        return if (ids.isEmpty()) all else all.filter { it.id in ids }
    }
}
