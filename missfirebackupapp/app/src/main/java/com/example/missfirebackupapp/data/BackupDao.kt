package com.example.missfirebackupapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BackupDao {

    // ---- BACKUPS ----
    @Insert
    suspend fun insertBackup(backup: BackupEntity): Long

    @Query("SELECT * FROM backup_table")
    fun getAllBackups(): Flow<List<BackupEntity>>

    @Query("DELETE FROM backup_table")
    suspend fun clearAll(): Int

    // ---- FOTOS ----
    @Insert
    suspend fun insertFoto(foto: FotoEntity): Long

    @Query("SELECT * FROM foto_table WHERE backupId = :backupId")
    fun getFotosByBackupId(backupId: Long): Flow<List<FotoEntity>>

    @Query("DELETE FROM foto_table WHERE id = :fotoId")
    suspend fun deleteFoto(fotoId: Long): Int
}
