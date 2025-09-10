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

    suspend fun getBackupById(id: Int): BackupEntity? = backupDao.getBackupById(id)

    suspend fun updateStatus(id: Int, status: String): Int = backupDao.updateStatus(id, status)

    suspend fun updateFull(backup: BackupEntity): Int = backupDao.updateFull(
        id = backup.id,
    remoteId = backup.remoteId,
        data = backup.data,
        unidade = backup.unidade,
        cava = backup.cava,
        banco = backup.banco,
        fogoId = backup.fogoId,
        furoNumero = backup.furoNumero,
        detonadorNumero = backup.detonadorNumero,
        espoletaId = backup.espoletaId,
        motivo = backup.motivo,
        tipoDetonador = backup.tipoDetonador,
        caboDetonador = backup.caboDetonador,
        metragem = backup.metragem,
        tentativaRecuperacao = backup.tentativaRecuperacao,
        coordenadaX = backup.coordenadaX,
        coordenadaY = backup.coordenadaY,
        coordenadaZ = backup.coordenadaZ,
    sistemaCoordenadas = backup.sistemaCoordenadas,
    status = backup.status,
    syncError = backup.syncError,
    syncErrorMessage = backup.syncErrorMessage,
    lastSyncAt = backup.lastSyncAt
    )

    suspend fun updateRemoteId(id: Int, remoteId: String): Int = backupDao.updateRemoteId(id, remoteId)

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

    suspend fun getFotosListByBackupId(backupId: Int): List<FotoEntity> = backupDao.getFotosListByBackupId(backupId)

    suspend fun updateFotoRemoteUrl(fotoId: Int, remoteUrl: String) = backupDao.updateFotoRemoteUrl(fotoId, remoteUrl)

    suspend fun deleteFoto(fotoId: Long): Int {
        return backupDao.deleteFoto(fotoId)
    }
}
