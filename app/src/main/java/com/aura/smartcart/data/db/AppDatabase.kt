package com.aura.smartcart.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ShoppingListEntity::class,
        ListItemEntity::class,
        RemainingItemEntity::class,
        BillEntity::class,
        BillItemEntity::class,
        BudgetEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun listDao(): ListDao
    abstract fun remainingDao(): RemainingDao
    abstract fun billDao(): BillDao
    abstract fun budgetDao(): BudgetDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "smartcart.db").build()
    }
}
