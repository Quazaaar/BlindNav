package com.blindnav.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [TripEntity::class], version = 1, exportSchema = false)
abstract class BlindNavDatabase : RoomDatabase() {
    abstract fun trips(): TripDao
}
