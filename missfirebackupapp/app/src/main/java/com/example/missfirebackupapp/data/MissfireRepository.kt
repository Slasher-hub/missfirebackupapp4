package com.example.missfirebackupapp.data

import kotlinx.coroutines.flow.Flow

class MisfireRepository(private val dao: MisfireDao) {

    suspend fun criarMissfire(entity: MisfireEntity): Int {
        return dao.insertMissfire(entity).toInt()
    }

    suspend fun atualizarMissfire(entity: MisfireEntity): Int {
        return dao.updateMissfire(entity.copy(lastUpdated = System.currentTimeMillis()))
    }

    fun observarMissfires(): Flow<List<MisfireEntity>> = dao.getAllMissfiresFlow()

    suspend fun obterMissfire(id: Int) = dao.getMissfireById(id)

    suspend fun deletarMissfire(entity: MisfireEntity): Int = dao.deleteMissfire(entity)

    suspend fun adicionarAttachment(att: MissfireAttachmentEntity): Int = dao.insertAttachment(att).toInt()

    suspend fun listarAttachments(missfireId: Int) = dao.getAttachments(missfireId)
    suspend fun listarAttachmentsPorUpdate(updateId: Int) = dao.getAttachmentsByUpdate(updateId)

    suspend fun deletarAttachment(att: MissfireAttachmentEntity): Int = dao.deleteAttachment(att)

    // Updates
    suspend fun adicionarUpdate(update: MissfireUpdateEntity): Int = dao.insertUpdate(update).toInt()
    suspend fun listarUpdates(missfireId: Int) = dao.getUpdates(missfireId)
    suspend fun deletarUpdate(update: MissfireUpdateEntity): Int = dao.deleteUpdate(update)

    // ----- Sync helpers -----
    suspend fun marcarAttachmentRemote(id: Int, remoteUrl: String) = dao.updateAttachmentRemoteUrl(id, remoteUrl)
    suspend fun atualizarRemoteIdMissfire(id: Int, remoteId: String) = dao.updateMissfireRemoteId(id, remoteId)
    suspend fun atualizarSyncStateMissfire(id: Int, lastSyncAt: Long?, syncError: Boolean, msg: String?) =
        dao.updateMissfireSyncState(id, lastSyncAt, syncError, msg)
    suspend fun marcarUpdateRemoto(id: Int, remoteId: String, lastSyncAt: Long) = dao.updateUpdateRemote(id, remoteId, lastSyncAt)

    suspend fun editarTextoUpdateSeNaoSincronizado(id: Int, novoTexto: String): Int =
        dao.updateUpdateTextoIfNotSynced(id, novoTexto)
}
