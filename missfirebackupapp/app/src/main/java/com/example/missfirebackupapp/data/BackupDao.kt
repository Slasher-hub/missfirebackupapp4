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

    @Query("SELECT * FROM backup_table WHERE id = :id LIMIT 1")
    suspend fun getBackupById(id: Int): BackupEntity?

    @Query("UPDATE backup_table SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Int, status: String): Int

    @Query("UPDATE backup_table SET remoteId = COALESCE(:remoteId, remoteId), data=:data, unidade=:unidade, cava=:cava, banco=:banco, fogoId=:fogoId, furoNumero=:furoNumero, detonadorNumero=:detonadorNumero, espoletaId=:espoletaId, motivo=:motivo, tipoDetonador=:tipoDetonador, caboDetonador=:caboDetonador, metragem=:metragem, tentativaRecuperacao=:tentativaRecuperacao, coordenadaX=:coordenadaX, coordenadaY=:coordenadaY, coordenadaZ=:coordenadaZ, sistemaCoordenadas=:sistemaCoordenadas, status=:status, syncError=:syncError, syncErrorMessage=:syncErrorMessage, lastSyncAt=:lastSyncAt WHERE id=:id")
    suspend fun updateFull(
        id: Int,
        remoteId: String?,
        data: String,
        unidade: String,
        cava: String,
        banco: String,
        fogoId: String,
        furoNumero: String,
        detonadorNumero: String,
        espoletaId: String,
        motivo: String,
        tipoDetonador: String,
        caboDetonador: String,
        metragem: String,
        tentativaRecuperacao: String,
        coordenadaX: Double,
        coordenadaY: Double,
        coordenadaZ: Double,
    sistemaCoordenadas: String,
    status: String,
        syncError: Boolean,
        syncErrorMessage: String?,
        lastSyncAt: Long?
    ): Int

    @Query("UPDATE backup_table SET remoteId = :remoteId WHERE id = :id")
    suspend fun updateRemoteId(id: Int, remoteId: String): Int

    @Query("DELETE FROM backup_table")
    suspend fun clearAll(): Int

    @Query("DELETE FROM backup_table WHERE id = :id")
    suspend fun deleteBackupById(id: Int): Int

    @Query("DELETE FROM foto_table WHERE backupId = :backupId")
    suspend fun deleteFotosByBackupId(backupId: Int): Int

    // ---- FILTROS ----
    // Esperando formato de data como dd/MM/yyyy ou yyyy-MM-dd. Usaremos LIKE para mês/ano flexível.
    // monthPattern ex: '09/2025' se data for dd/MM/yyyy. Ajustar se formato diferente.
    @Query(
        "SELECT * FROM backup_table WHERE (:mesAno IS NULL OR data LIKE '%' || :mesAno) " +
                "AND (:unidade IS NULL OR unidade LIKE '%' || :unidade || '%') " +
                "AND (:cava IS NULL OR cava LIKE '%' || :cava || '%')"
    )
    suspend fun filtrarBackups(mesAno: String?, unidade: String?, cava: String?): List<BackupEntity>

    // ---- FOTOS ----
    @Insert
    suspend fun insertFoto(foto: FotoEntity): Long

    @Query("SELECT * FROM foto_table WHERE backupId = :backupId")
    fun getFotosByBackupId(backupId: Long): Flow<List<FotoEntity>>

    // Versão síncrona (lista) usada no processo de sync para upload sem coletar Flow
    @Query("SELECT * FROM foto_table WHERE backupId = :backupId ORDER BY id ASC")
    suspend fun getFotosListByBackupId(backupId: Int): List<FotoEntity>

    @Query("UPDATE foto_table SET remoteUrl = :remoteUrl WHERE id = :fotoId")
    suspend fun updateFotoRemoteUrl(fotoId: Int, remoteUrl: String): Int

    @Query("DELETE FROM foto_table WHERE id = :fotoId")
    suspend fun deleteFoto(fotoId: Long): Int
}
