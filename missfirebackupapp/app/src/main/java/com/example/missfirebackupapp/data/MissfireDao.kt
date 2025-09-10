package com.example.missfirebackupapp.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MisfireDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMissfire(entity: MisfireEntity): Long

    @Update
    suspend fun updateMissfire(entity: MisfireEntity): Int

    @Query("SELECT * FROM missfire_table WHERE id = :id")
    suspend fun getMissfireById(id: Int): MisfireEntity?

    @Query("SELECT * FROM missfire_table ORDER BY createdAt DESC")
    fun getAllMissfiresFlow(): Flow<List<MisfireEntity>>

    @Delete
    suspend fun deleteMissfire(entity: MisfireEntity): Int

    // Attachments
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachment(entity: MissfireAttachmentEntity): Long

    @Query("SELECT * FROM missfire_attachment_table WHERE missfireId = :missfireId")
    suspend fun getAttachments(missfireId: Int): List<MissfireAttachmentEntity>

    @Query("SELECT * FROM missfire_attachment_table WHERE updateId = :updateId")
    suspend fun getAttachmentsByUpdate(updateId: Int): List<MissfireAttachmentEntity>

    @Delete
    suspend fun deleteAttachment(entity: MissfireAttachmentEntity): Int

    // Sync helpers Attachments
    @Query("UPDATE missfire_attachment_table SET remoteUrl = :remoteUrl WHERE id = :id")
    suspend fun updateAttachmentRemoteUrl(id: Int, remoteUrl: String)

    // Updates (investigação)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUpdate(entity: MissfireUpdateEntity): Long

    @Query("SELECT * FROM missfire_update_table WHERE missfireId = :missfireId ORDER BY createdAt ASC")
    suspend fun getUpdates(missfireId: Int): List<MissfireUpdateEntity>

    @Delete
    suspend fun deleteUpdate(entity: MissfireUpdateEntity): Int

    // Sync helpers Missfire / Updates
    @Query("UPDATE missfire_table SET remoteId = :remoteId WHERE id = :id")
    suspend fun updateMissfireRemoteId(id: Int, remoteId: String)

    @Query("UPDATE missfire_table SET lastSyncAt = :lastSyncAt, syncError = :syncError, syncErrorMessage = :syncErrorMessage WHERE id = :id")
    suspend fun updateMissfireSyncState(id: Int, lastSyncAt: Long?, syncError: Boolean, syncErrorMessage: String?)

    @Query("UPDATE missfire_update_table SET remoteId = :remoteId, lastSyncAt = :lastSyncAt, syncError = 0, syncErrorMessage = NULL WHERE id = :id")
    suspend fun updateUpdateRemote(id: Int, remoteId: String, lastSyncAt: Long)

    @Query("UPDATE missfire_update_table SET texto = :texto, syncError = 0, syncErrorMessage = NULL, lastSyncAt = NULL WHERE id = :id AND remoteId IS NULL")
    suspend fun updateUpdateTextoIfNotSynced(id: Int, texto: String): Int
}
