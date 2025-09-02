package com.example.missfirebackupapp

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.storage.FirebaseStorage
import com.example.missfirebackupapp.data.FotoEntity
import android.net.Uri
import java.io.File
import com.example.missfirebackupapp.data.BackupEntity
import com.example.missfirebackupapp.data.BackupDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object SyncManager {
    private val firestore by lazy {
        val instance = FirebaseFirestore.getInstance()
        Log.d("SyncManager", "Firestore instance criado: $instance")
        instance
    }
    private val storage by lazy { FirebaseStorage.getInstance() }

    suspend fun syncBackup(context: Context, entity: BackupEntity): Result<BackupEntity> {
        return try {
            val auth = FirebaseAuth.getInstance()
            if (auth.currentUser == null) {
                Log.d("SyncManager", "Nenhum usuário autenticado. Tentando signInAnonymously...")
                runCatching { auth.signInAnonymously().await() }
                    .onSuccess { Log.d("SyncManager", "Anon auth OK uid=${'$'}{auth.currentUser?.uid}") }
                    .onFailure { Log.e("SyncManager", "Falha anon auth", it) }
            }
            val uid = auth.currentUser?.uid
            val opts: FirebaseOptions? = runCatching { FirebaseApp.getInstance().options }.getOrNull()
            Log.d(
                "SyncManager",
                "Iniciando sync id=${entity.id} appId=${context.packageName} uid=${uid ?: "(null)"} projectId=${opts?.projectId} appIdFirebase=${opts?.applicationId}"
            )
            // Upload de todas as fotos locais sem remoteUrl
            val dao = withContext(Dispatchers.IO) { BackupDatabase.getDatabase(context).backupDao() }
            val fotos = withContext(Dispatchers.IO) { dao.getFotosListByBackupId(entity.id) }
            val photoUrls = mutableListOf<String>()
            for (foto in fotos) {
                if (foto.remoteUrl != null) {
                    photoUrls.add(foto.remoteUrl)
                    continue
                }
                val file = File(foto.caminhoFoto)
                if (!file.exists()) {
                    Log.w("SyncManager", "Arquivo de foto não encontrado: ${foto.caminhoFoto}")
                    continue
                }
                val ref = storage.reference.child("backups/${entity.id}/photos/${foto.id}.jpg")
                try {
                    ref.putFile(Uri.fromFile(file)).await()
                    val url = ref.downloadUrl.await().toString()
                    photoUrls.add(url)
                    withContext(Dispatchers.IO) { dao.updateFotoRemoteUrl(foto.id, url) }
                    Log.d("SyncManager", "Foto ${foto.id} upload OK")
                } catch (fe: Exception) {
                    Log.e("SyncManager", "Falha upload foto id=${foto.id}", fe)
                }
            }
            val mainPhotoUrl: String? = photoUrls.firstOrNull()

            val data = hashMapOf(
                "id" to entity.id,
                "data" to entity.data,
                "unidade" to entity.unidade,
                "cava" to entity.cava,
                "banco" to entity.banco,
                "fogoId" to entity.fogoId,
                "furoNumero" to entity.furoNumero,
                "detonadorNumero" to entity.detonadorNumero,
                "espoletaId" to entity.espoletaId,
                "motivo" to entity.motivo,
                "tipoDetonador" to entity.tipoDetonador,
                "caboDetonador" to entity.caboDetonador,
                "metragem" to entity.metragem,
                "tentativaRecuperacao" to entity.tentativaRecuperacao,
                "coordenadaX" to entity.coordenadaX,
                "coordenadaY" to entity.coordenadaY,
                "coordenadaZ" to entity.coordenadaZ,
                "sistemaCoordenadas" to entity.sistemaCoordenadas,
                "status" to entity.status,
                "createdAt" to entity.createdAt,
                "photoUrl" to mainPhotoUrl,
                "photoUrls" to photoUrls
            )
            Log.d("SyncManager", "Enviando para coleção 'backups' doc=${entity.id}")
            firestore.collection("backups").document(entity.id.toString()).set(data).await()
            Log.d("SyncManager", "Upload Firestore sucesso id=${entity.id}")
        val synced = entity.copy(status = "SINCRONIZADO", syncError = false)
            withContext(Dispatchers.IO) {
                val dao = BackupDatabase.getDatabase(context).backupDao()
                dao.updateFull(
                    id = synced.id,
                    data = synced.data,
                    unidade = synced.unidade,
                    cava = synced.cava,
                    banco = synced.banco,
                    fogoId = synced.fogoId,
                    furoNumero = synced.furoNumero,
                    detonadorNumero = synced.detonadorNumero,
                    espoletaId = synced.espoletaId,
                    motivo = synced.motivo,
                    tipoDetonador = synced.tipoDetonador,
                    caboDetonador = synced.caboDetonador,
                    metragem = synced.metragem,
                    tentativaRecuperacao = synced.tentativaRecuperacao,
                    coordenadaX = synced.coordenadaX,
                    coordenadaY = synced.coordenadaY,
                    coordenadaZ = synced.coordenadaZ,
                    sistemaCoordenadas = synced.sistemaCoordenadas,
            status = synced.status,
            syncError = synced.syncError
                )
            }
            Result.success(synced)
        } catch (e: Exception) {
            Log.e("SyncManager", "Falha sync id=${entity.id}", e)
            Result.failure(e)
        }
    }
}
