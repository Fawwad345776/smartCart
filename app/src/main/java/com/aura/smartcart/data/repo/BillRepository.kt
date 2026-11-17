package com.aura.smartcart.data.repo

import androidx.room.withTransaction
import com.aura.smartcart.data.db.AppDatabase
import com.aura.smartcart.data.db.BillEntity
import com.aura.smartcart.data.db.BillItemEntity
import com.aura.smartcart.data.db.BillWithItems
import com.aura.smartcart.data.db.BudgetEntity
import com.aura.smartcart.data.db.ListStatus
import com.aura.smartcart.data.db.MonthTotal
import com.aura.smartcart.data.remote.BillCloudSync
import com.aura.smartcart.util.Months
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.util.UUID

data class BillDraftItem(val key: Long, val name: String, val quantity: String, val price: String)

data class BillDraft(
    val id: Long? = null,
    val remoteId: String? = null,
    val listId: Long? = null,
    val listName: String = "",
    val storeName: String = "",
    val address: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
    val total: Double = 0.0,
    val currency: String = "",
    val imagePath: String? = null,
    val purchasedAt: Long = System.currentTimeMillis(),
    val items: List<BillDraftItem> = emptyList()
)

class BillRepository(
    private val db: AppDatabase,
    private val cloud: BillCloudSync
) {
    private val bills = db.billDao()
    private val budgets = db.budgetDao()

    fun observeBills(): Flow<List<BillWithItems>> = bills.observeAll()
    suspend fun getBill(id: Long): BillWithItems? = bills.get(id)
    fun observeMonthTotal(monthKey: String): Flow<Double> = bills.observeMonthTotal(monthKey)
    fun observeMonthTotals(): Flow<List<MonthTotal>> = bills.observeMonthTotals()
    suspend fun countBillsInMonth(monthKey: String): Int = bills.countInMonth(monthKey)

    /** Saves locally first (works offline), then uploads to Firestore. Returns the bill id. */
    suspend fun save(draft: BillDraft): Long {
        val entity = BillEntity(
            id = draft.id ?: 0,
            remoteId = draft.remoteId ?: UUID.randomUUID().toString(),
            listId = draft.listId,
            listName = draft.listName,
            storeName = draft.storeName.trim(),
            address = draft.address.trim(),
            lat = draft.lat,
            lng = draft.lng,
            total = draft.total,
            currency = draft.currency,
            imagePath = draft.imagePath,
            purchasedAt = draft.purchasedAt,
            monthKey = Months.keyOf(draft.purchasedAt),
            synced = false
        )
        val items = draft.items.filter { it.name.isNotBlank() }
        val billId = db.withTransaction {
            val id = if (draft.id == null) bills.insert(entity) else {
                bills.update(entity); entity.id
            }
            bills.deleteItemsFor(id)
            bills.insertItems(
                items.map {
                    BillItemEntity(
                        billId = id,
                        name = it.name.trim(),
                        quantity = it.quantity.trim(),
                        price = com.aura.smartcart.util.Money.parse(it.price) ?: 0.0
                    )
                }
            )
            // A bill finishes its shopping list.
            draft.listId?.let { db.listDao().setStatus(it, ListStatus.DONE, System.currentTimeMillis()) }
            // First bill of a month carries over last month's budget.
            ensureBudgetForMonth(entity.monthKey)
            id
        }
        syncOne(billId)
        return billId
    }

    private suspend fun syncOne(billId: Long) {
        val saved = bills.get(billId) ?: return
        if (cloud.upload(saved.bill, saved.items)) bills.markSynced(billId)
    }

    /** Retries bills that could not be uploaded earlier (e.g. no internet). */
    suspend fun syncPending() {
        bills.unsynced().forEach { syncOne(it.id) }
    }

    suspend fun delete(billId: Long) {
        val bill = bills.get(billId)?.bill ?: return
        bills.delete(billId)
        bill.imagePath?.let { File(it).delete() }
        cloud.delete(bill.remoteId)
    }

    private suspend fun ensureBudgetForMonth(monthKey: String) {
        if (budgets.get(monthKey) != null) return
        budgets.latestBefore(monthKey)?.let { budgets.insertIfMissing(BudgetEntity(monthKey, it.amount)) }
    }
}
