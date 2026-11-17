package com.aura.smartcart.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

object ListStatus {
    const val ACTIVE = "active"
    const val DONE = "done"
}

@Entity(tableName = "lists")
data class ShoppingListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** When the user plans to go shopping (epoch millis). Reminder fires 1 hour before. */
    val shoppingAt: Long? = null,
    val status: String = ListStatus.ACTIVE,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

@Entity(
    tableName = "list_items",
    foreignKeys = [
        ForeignKey(
            entity = ShoppingListEntity::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("listId")]
)
data class ListItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val listId: Long,
    val name: String,
    val quantity: String = "",
    val checked: Boolean = false,
    val checkedAt: Long? = null,
    val position: Int = 0
)

/** Items that were not found on a trip, waiting to be added to a future list. */
@Entity(tableName = "remaining_items")
data class RemainingItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val quantity: String = "",
    val fromListName: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "bills")
data class BillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Stable id used for the Firestore document. */
    val remoteId: String,
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
    /** "yyyy-MM" of [purchasedAt], used for monthly budget totals. */
    val monthKey: String,
    val synced: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "bill_items",
    foreignKeys = [
        ForeignKey(
            entity = BillEntity::class,
            parentColumns = ["id"],
            childColumns = ["billId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("billId")]
)
data class BillItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val billId: Long,
    val name: String,
    val quantity: String = "",
    val price: Double = 0.0
)

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val monthKey: String,
    val amount: Double
)

data class ListWithItems(
    @Embedded val list: ShoppingListEntity,
    @Relation(parentColumn = "id", entityColumn = "listId")
    val items: List<ListItemEntity>
)

data class BillWithItems(
    @Embedded val bill: BillEntity,
    @Relation(parentColumn = "id", entityColumn = "billId")
    val items: List<BillItemEntity>
)

data class MonthTotal(
    @ColumnInfo(name = "monthKey") val monthKey: String,
    @ColumnInfo(name = "total") val total: Double
)
