package com.example.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ActionEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun actionDao(): ActionDao
}
