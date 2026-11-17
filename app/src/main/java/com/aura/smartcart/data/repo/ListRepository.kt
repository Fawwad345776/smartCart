package com.aura.smartcart.data.repo

import androidx.room.withTransaction
import com.aura.smartcart.data.db.AppDatabase
import com.aura.smartcart.data.db.ListItemEntity
import com.aura.smartcart.data.db.ListStatus
import com.aura.smartcart.data.db.ListWithItems
import com.aura.smartcart.data.db.RemainingItemEntity
import com.aura.smartcart.data.db.ShoppingListEntity
import kotlinx.coroutines.flow.Flow

/** An item while a list is being written or edited. */
data class DraftItem(
    val key: Long,
    val name: String,
    val quantity: String = "",
    val checked: Boolean = false,
    val checkedAt: Long? = null
)

class ListRepository(private val db: AppDatabase) {
    private val lists = db.listDao()
    private val remaining = db.remainingDao()

    fun observeLists(): Flow<List<ListWithItems>> = lists.observeAll()
    fun observeList(id: Long): Flow<ListWithItems?> = lists.observe(id)
    suspend fun getList(id: Long): ListWithItems? = lists.get(id)
    suspend fun getAllLists(): List<ListWithItems> = lists.getAll()

    fun observeRemaining(): Flow<List<RemainingItemEntity>> = remaining.observeAll()

    /**
     * Creates or replaces a list with [items]. Remaining items the user pulled in ([usedRemainingIds])
     * leave the remaining pool once the list is saved.
     */
    suspend fun saveList(
        id: Long?,
        name: String,
        shoppingAt: Long?,
        items: List<DraftItem>,
        usedRemainingIds: List<Long>
    ): Long = db.withTransaction {
        val now = System.currentTimeMillis()
        val listId = if (id == null) {
            lists.insert(ShoppingListEntity(name = name, shoppingAt = shoppingAt, createdAt = now, updatedAt = now))
        } else {
            val existing = lists.get(id)?.list
            val allChecked = items.isNotEmpty() && items.all { it.checked }
            lists.update(
                (existing ?: ShoppingListEntity(id = id, name = name)).copy(
                    name = name,
                    shoppingAt = shoppingAt,
                    updatedAt = now,
                    // Editing a finished list with new unticked items re-opens it.
                    status = if (existing?.status == ListStatus.DONE && allChecked) ListStatus.DONE else ListStatus.ACTIVE,
                    completedAt = if (existing?.status == ListStatus.DONE && allChecked) existing.completedAt else null
                )
            )
            lists.deleteItemsFor(id)
            id
        }
        lists.insertItems(
            items.mapIndexed { i, d ->
                ListItemEntity(
                    listId = listId,
                    name = d.name.trim(),
                    quantity = d.quantity.trim(),
                    checked = d.checked,
                    checkedAt = d.checkedAt,
                    position = i
                )
            }
        )
        if (usedRemainingIds.isNotEmpty()) remaining.deleteByIds(usedRemainingIds)
        listId
    }

    suspend fun setChecked(itemId: Long, checked: Boolean) {
        lists.setChecked(itemId, checked, if (checked) System.currentTimeMillis() else null)
    }

    /** Moves every unticked item to the remaining pool and finishes the list. */
    suspend fun moveUncheckedToRemaining(listId: Long): Int = db.withTransaction {
        val list = lists.get(listId) ?: return@withTransaction 0
        val unchecked = list.items.filter { !it.checked }
        if (unchecked.isNotEmpty()) {
            remaining.insertAll(
                unchecked.map { RemainingItemEntity(name = it.name, quantity = it.quantity, fromListName = list.list.name) }
            )
            lists.deleteUnchecked(listId)
        }
        lists.setStatus(listId, ListStatus.DONE, System.currentTimeMillis())
        unchecked.size
    }

    suspend fun completeList(listId: Long) {
        lists.setStatus(listId, ListStatus.DONE, System.currentTimeMillis())
    }

    suspend fun deleteList(listId: Long) = lists.delete(listId)

    suspend fun countListsThisMonth(monthStart: Long): Int = lists.countCreatedSince(monthStart)
}
