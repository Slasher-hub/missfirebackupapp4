package com.example.missfirebackupapp.data

import kotlinx.coroutines.flow.Flow

class BackupRepository(private val backupDao: BackupDao) {

    // ====== BACKUP ======
    suspend fun insertBackup(backup: BackupEntity): Long {
        return backupDao.insertBackup(backup)
    }

    fun getAllBackups(): Flow<List<BackupEntity>> {
        return backupDao.getAllBackups()
    }

    suspend fun clearAll(): Int {
        return backupDao.clearAll()
    }

    // ====== FOTOS ======
    suspend fun insertFoto(foto: FotoEntity): Long {
        return backupDao.insertFoto(foto)
    }

    fun getFotosByBackupId(backupId: Long): Flow<List<FotoEntity>> {
        return backupDao.getFotosByBackupId(backupId)
    }

    suspend fun deleteFoto(fotoId: Long): Int {
        return backupDao.deleteFoto(fotoId)
    }
}
