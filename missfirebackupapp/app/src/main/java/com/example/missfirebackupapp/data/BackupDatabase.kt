package com.example.missfirebackupapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [BackupEntity::class, FotoEntity::class, MisfireEntity::class, MissfireAttachmentEntity::class, MissfireUpdateEntity::class],
    version = 10,
    exportSchema = false
)
abstract class BackupDatabase : RoomDatabase() {

    abstract fun backupDao(): BackupDao
    abstract fun missfireDao(): MisfireDao

    companion object {
        @Volatile
        private var INSTANCE: BackupDatabase? = null

        fun getDatabase(context: Context): BackupDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BackupDatabase::class.java,
                    "backup_database"
                )
                    // TODO: substituir fallback por migrations reais quando consolidarmos schema
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
