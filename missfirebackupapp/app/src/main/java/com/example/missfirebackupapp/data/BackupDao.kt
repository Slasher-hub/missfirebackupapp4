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

    @Query("UPDATE backup_table SET data=:data, unidade=:unidade, cava=:cava, banco=:banco, fogoId=:fogoId, furoNumero=:furoNumero, detonadorNumero=:detonadorNumero, espoletaId=:espoletaId, motivo=:motivo, tipoDetonador=:tipoDetonador, caboDetonador=:caboDetonador, metragem=:metragem, tentativaRecuperacao=:tentativaRecuperacao, coordenadaX=:coordenadaX, coordenadaY=:coordenadaY, coordenadaZ=:coordenadaZ, sistemaCoordenadas=:sistemaCoordenadas, status=:status, syncError=:syncError WHERE id=:id")
    suspend fun updateFull(
        id: Int,
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
        syncError: Boolean
    ): Int

    @Query("DELETE FROM backup_table")
    suspend fun clearAll(): Int

    // ---- FOTOS ----
    @Insert
    suspend fun insertFoto(foto: FotoEntity): Long

    @Query("SELECT * FROM foto_table WHERE backupId = :backupId")
    fun getFotosByBackupId(backupId: Long): Flow<List<FotoEntity>>

    // Versão síncrona (lista) usada no processo de sync para upload sem coletar Flow
    @Query("SELECT * FROM foto_table WHERE backupId = :backupId")
    suspend fun getFotosListByBackupId(backupId: Int): List<FotoEntity>

    @Query("UPDATE foto_table SET remoteUrl = :remoteUrl WHERE id = :fotoId")
    suspend fun updateFotoRemoteUrl(fotoId: Int, remoteUrl: String): Int

    @Query("DELETE FROM foto_table WHERE id = :fotoId")
    suspend fun deleteFoto(fotoId: Long): Int
}
