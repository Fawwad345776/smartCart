package com.aura.smartcart.data.repo

import com.aura.smartcart.data.db.AppDatabase
import com.aura.smartcart.data.db.BudgetEntity
import kotlinx.coroutines.flow.Flow

class BudgetRepository(db: AppDatabase) {
    private val budgets = db.budgetDao()

    fun observe(monthKey: String): Flow<BudgetEntity?> = budgets.observe(monthKey)
    fun observeAll(): Flow<List<BudgetEntity>> = budgets.observeAll()

    suspend fun set(monthKey: String, amount: Double) = budgets.upsert(BudgetEntity(monthKey, amount))

    /** A new month starts with the previous month's budget until the user changes it. */
    suspend fun carryOverIfMissing(monthKey: String) {
        if (budgets.get(monthKey) != null) return
        budgets.latestBefore(monthKey)?.let { budgets.insertIfMissing(BudgetEntity(monthKey, it.amount)) }
    }
}
