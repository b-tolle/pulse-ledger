package com.example.pulseledger.data.db

import android.content.Context
import androidx.room.Room

object Db {
    @Volatile private var instance: AppDatabase? = null
    fun get(context: Context): AppDatabase =
        instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "pulse.db")
                .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
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


val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `med_shots` (`epochMillis` INTEGER NOT NULL, `doseUnits` REAL NOT NULL, `name` TEXT NOT NULL, PRIMARY KEY(`epochMillis`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `hunger_logs` (`dayEpoch` INTEGER NOT NULL, `level` INTEGER NOT NULL, PRIMARY KEY(`dayEpoch`))")
    }
}


val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `together_log` (`dayEpoch` INTEGER NOT NULL, `minutes` INTEGER NOT NULL, PRIMARY KEY(`dayEpoch`))")
    }
}
