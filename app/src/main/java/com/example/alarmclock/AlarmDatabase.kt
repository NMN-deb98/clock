package com.example.alarmclock

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Alarm::class], version = 4, exportSchema = false)
abstract class AlarmDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE alarms ADD COLUMN wakeMode TEXT NOT NULL DEFAULT 'NONE'")
                database.execSQL("ALTER TABLE alarms ADD COLUMN mathDifficulty TEXT NOT NULL DEFAULT 'MEDIUM'")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE alarms ADD COLUMN deleteAfterRinging INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE alarms ADD COLUMN walkTarget INTEGER NOT NULL DEFAULT 40")
                database.execSQL("ALTER TABLE alarms ADD COLUMN shakeDifficulty TEXT NOT NULL DEFAULT 'MEDIUM'")
                database.execSQL("ALTER TABLE alarms ADD COLUMN typeMode TEXT NOT NULL DEFAULT 'RANDOM'")
                database.execSQL("ALTER TABLE alarms ADD COLUMN customPhrase TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE alarms ADD COLUMN touchTarget INTEGER NOT NULL DEFAULT 30")
                database.execSQL("ALTER TABLE alarms ADD COLUMN patternSize INTEGER NOT NULL DEFAULT 3")
                database.execSQL("ALTER TABLE alarms ADD COLUMN memoryDifficulty TEXT NOT NULL DEFAULT 'MEDIUM'")
                database.execSQL("ALTER TABLE alarms ADD COLUMN snoozeCount INTEGER NOT NULL DEFAULT 3")
                database.execSQL("ALTER TABLE alarms ADD COLUMN snoozeMinutes INTEGER NOT NULL DEFAULT 10")
            }
        }

        @Volatile private var INSTANCE: AlarmDatabase? = null

        fun getInstance(context: Context): AlarmDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AlarmDatabase::class.java,
                    "alarm_database"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
                INSTANCE = instance
                instance
            }
        }
    }
}