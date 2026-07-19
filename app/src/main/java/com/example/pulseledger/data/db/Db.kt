package com.example.pulseledger.data.db

import android.content.Context
import androidx.room.Room

object Db {
    @Volatile private var instance: AppDatabase? = null
    fun get(context: Context): AppDatabase =
        instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "pulse.db")
                .addMigrations(MIGRATION_4_5)
                .fallbackToDestructiveMigration()
                .build().also { instance = it }
        }
}


val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `weight_entries` " +
            "(`epochMillis` INTEGER NOT NULL, `lbs` REAL NOT NULL, `source` TEXT NOT NULL, " +
            "PRIMARY KEY(`epochMillis`))"
        )
    }
}
