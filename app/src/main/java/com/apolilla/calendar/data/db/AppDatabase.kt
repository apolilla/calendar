package com.apolilla.calendar.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SeriesEntity::class, TaskEntity::class, AttachmentEntity::class, ReminderEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        const val NAME = "calendar.db"

        fun build(context: Context, name: String = NAME): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, name).build()
    }
}
