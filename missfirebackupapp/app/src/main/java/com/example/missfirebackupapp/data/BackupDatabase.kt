package com.example.missfirebackupapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [BackupEntity::class, FotoEntity::class], // Agora temos duas tabelas
    version = 2, // ✅ Atualize a versão do banco
    exportSchema = false
)
abstract class BackupDatabase : RoomDatabase() {

    abstract fun backupDao(): BackupDao

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
                    .fallbackToDestructiveMigration(false) // ✅ Recria DB se houver mudança de schema
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
