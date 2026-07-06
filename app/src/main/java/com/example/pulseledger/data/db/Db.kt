package com.example.pulseledger.data.db

import android.content.Context
import androidx.room.Room

object Db {
    @Volatile private var instance: AppDatabase? = null
    fun get(context: Context): AppDatabase =
        instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "pulse.db")
                .fallbackToDestructiveMigration()
                .build().also { instance = it }
        }
}
