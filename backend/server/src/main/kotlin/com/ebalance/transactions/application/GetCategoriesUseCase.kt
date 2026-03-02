package com.ebalance.transactions.application

import arrow.core.Either
import com.ebalance.transactions.domain.CategoryEntry
import com.ebalance.transactions.domain.TransactionError
import com.ebalance.transactions.domain.TransactionRepository

/** Input port: returns categories for populating filter dropdowns.
 *  When [ids] is non-empty only the matching categories are returned. */
interface GetCategoriesUseCase {
    fun execute(ids: List<Long> = emptyList()): Either<TransactionError, List<CategoryEntry>>
}

class GetCategoriesInteractor(
    private val repository: TransactionRepository
) : GetCategoriesUseCase {
    override fun execute(ids: List<Long>): Either<TransactionError, List<CategoryEntry>> =
        repository.getCategories().map { all ->
            if (ids.isEmpty()) all else all.filter { it.id in ids }
        }
}
