package com.aura.smartcart.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ListDao {
    @Transaction
    @Query("SELECT * FROM lists ORDER BY status ASC, COALESCE(shoppingAt, updatedAt) DESC")
    fun observeAll(): Flow<List<ListWithItems>>

    @Transaction
    @Query("SELECT * FROM lists WHERE id = :id")
    fun observe(id: Long): Flow<ListWithItems?>

    @Transaction
    @Query("SELECT * FROM lists WHERE id = :id")
    suspend fun get(id: Long): ListWithItems?

    @Transaction
    @Query("SELECT * FROM lists ORDER BY updatedAt DESC")
    suspend fun getAll(): List<ListWithItems>

    @Query("SELECT COUNT(*) FROM lists WHERE createdAt >= :since")
    suspend fun countCreatedSince(since: Long): Int

    @Insert
    suspend fun insert(list: ShoppingListEntity): Long

    @Update
    suspend fun update(list: ShoppingListEntity)

    @Query("DELETE FROM lists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE lists SET status = :status, completedAt = :completedAt, updatedAt = :completedAt WHERE id = :id")
    suspend fun setStatus(id: Long, status: String, completedAt: Long?)

    @Insert
    suspend fun insertItems(items: List<ListItemEntity>)

    @Query("DELETE FROM list_items WHERE listId = :listId")
    suspend fun deleteItemsFor(listId: Long)

    @Query("UPDATE list_items SET checked = :checked, checkedAt = :checkedAt WHERE id = :itemId")
    suspend fun setChecked(itemId: Long, checked: Boolean, checkedAt: Long?)

    @Query("DELETE FROM list_items WHERE listId = :listId AND checked = 0")
    suspend fun deleteUnchecked(listId: Long)
}

@Dao
interface RemainingDao {
    @Query("SELECT * FROM remaining_items ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<RemainingItemEntity>>

    @Insert
    suspend fun insertAll(items: List<RemainingItemEntity>)

    @Query("DELETE FROM remaining_items WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}

@Dao
interface BillDao {
    @Transaction
    @Query("SELECT * FROM bills ORDER BY purchasedAt DESC")
    fun observeAll(): Flow<List<BillWithItems>>

    @Transaction
    @Query("SELECT * FROM bills WHERE id = :id")
    suspend fun get(id: Long): BillWithItems?

    @Query("SELECT COUNT(*) FROM bills WHERE monthKey = :monthKey")
    suspend fun countInMonth(monthKey: String): Int

    @Query("SELECT COALESCE(SUM(total), 0) FROM bills WHERE monthKey = :monthKey")
    fun observeMonthTotal(monthKey: String): Flow<Double>

    @Query("SELECT monthKey, SUM(total) AS total FROM bills GROUP BY monthKey ORDER BY monthKey DESC")
    fun observeMonthTotals(): Flow<List<MonthTotal>>

    @Insert
    suspend fun insert(bill: BillEntity): Long

    @Update
    suspend fun update(bill: BillEntity)

    @Query("DELETE FROM bills WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE bills SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: Long)

    @Query("SELECT * FROM bills WHERE synced = 0")
    suspend fun unsynced(): List<BillEntity>

    @Insert
    suspend fun insertItems(items: List<BillItemEntity>)

    @Query("DELETE FROM bill_items WHERE billId = :billId")
    suspend fun deleteItemsFor(billId: Long)
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE monthKey = :monthKey")
    fun observe(monthKey: String): Flow<BudgetEntity?>

    @Query("SELECT * FROM budgets WHERE monthKey = :monthKey")
    suspend fun get(monthKey: String): BudgetEntity?

    @Query("SELECT * FROM budgets WHERE monthKey < :monthKey ORDER BY monthKey DESC LIMIT 1")
    suspend fun latestBefore(monthKey: String): BudgetEntity?

    @Query("SELECT * FROM budgets ORDER BY monthKey DESC")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Upsert
    suspend fun upsert(budget: BudgetEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(budget: BudgetEntity)
}
