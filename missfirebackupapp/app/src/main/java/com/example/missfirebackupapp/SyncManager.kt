package com.example.missfirebackupapp

import android.content.Context
import android.net.Uri
import android.util.Log
import android.os.Build
import com.example.missfirebackupapp.data.BackupDatabase
import com.example.missfirebackupapp.data.BackupEntity
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

object SyncManager {
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val storage by lazy { FirebaseStorage.getInstance() }

    private fun getInstallationId(context: Context): String {
        val sp = context.getSharedPreferences("app_meta", Context.MODE_PRIVATE)
        var id = sp.getString("install_id", null)
        if (id.isNullOrBlank()) {
            id = java.util.UUID.randomUUID().toString()
            sp.edit().putString("install_id", id).apply()
        }
        return id
    }

    private fun getAppVersion(context: Context): Pair<Long, String> {
        return try {
            val pm = context.packageManager
            val p = pm.getPackageInfo(context.packageName, 0)
            val code = if (android.os.Build.VERSION.SDK_INT >= 28) p.longVersionCode else p.versionCode.toLong()
            val name = p.versionName ?: "?"
            code to name
        } catch (e: Exception) {
            0L to "?"
        }
    }

    suspend fun syncBackup(context: Context, entity: BackupEntity): Result<BackupEntity> = try {
        // Garantir auth anônima
        val auth = FirebaseAuth.getInstance()
    if (auth.currentUser == null) {
            runCatching { auth.signInAnonymously().await() }
                .onSuccess { Log.d("SyncManager", "Anon auth OK uid=${'$'}{auth.currentUser?.uid}") }
                .onFailure { Log.e("SyncManager", "Falha anon auth", it) }
        }
    val uid = FirebaseAuth.getInstance().currentUser?.uid
        val opts: FirebaseOptions? = runCatching { FirebaseApp.getInstance().options }.getOrNull()
        Log.d(
            "SyncManager",
            "Iniciando sync localId=${entity.id} remoteIdPrev=${entity.remoteId} pkg=${context.packageName} project=${opts?.projectId}"
        )

        // docId consistente ou gera novo
        var remoteId = entity.remoteId
        if (remoteId.isNullOrBlank()) {
            remoteId = java.util.UUID.randomUUID().toString()
            Log.d("SyncManager", "Gerado novo remoteId=${remoteId} para localId=${entity.id}")
        } else {
            Log.d("SyncManager", "Reutilizando remoteId=${remoteId} para localId=${entity.id}")
        }

        val dao = withContext(Dispatchers.IO) { BackupDatabase.getDatabase(context).backupDao() }
        val fotos = withContext(Dispatchers.IO) { dao.getFotosListByBackupId(entity.id) }

        val photoUrls = mutableListOf<String>()
        val photoList = mutableListOf<Map<String, Any?>>()

        for (foto in fotos) {
            var remoteUrl = foto.remoteUrl
            var uploadError: String? = null
            var uploadErrorDetail: String? = null
            var sizeBytes: Long? = null

            if (remoteUrl == null) {
                val file = File(foto.caminhoFoto)
                if (!file.exists()) {
                    uploadError = "FILE_NOT_FOUND"
                    uploadErrorDetail = foto.caminhoFoto
                    Log.w("SyncManager", "Foto inexistente path=${foto.caminhoFoto}")
                } else {
                    sizeBytes = file.length()
                    Log.d("SyncManager", "Preparando upload fotoId=${foto.id} size=${sizeBytes} path=${file.absolutePath} remoteId=${remoteId}")
                    if (sizeBytes == 0L) {
                        uploadError = "EMPTY_FILE"
                        Log.w("SyncManager", "Arquivo vazio fotoId=${foto.id}")
                    } else {
                        val ref = storage.reference.child("backups/${remoteId}/photos/${foto.id}.jpg")
                        var attempt = 0
                        val maxAttempts = 3
            while (attempt < maxAttempts && remoteUrl == null) {
                            attempt++
                            try {
                                Log.d("SyncManager", "Upload tentativa ${attempt}/${maxAttempts} fotoId=${foto.id}")
                // Etapa 1: upload
                ref.putFile(Uri.fromFile(file)).await()
                // Etapa 2: downloadUrl
                remoteUrl = ref.downloadUrl.await().toString()
                                withContext(Dispatchers.IO) { dao.updateFotoRemoteUrl(foto.id, remoteUrl!!) }
                                Log.d("SyncManager", "Upload OK fotoId=${foto.id} attempts=${attempt}")
                            } catch (fe: Exception) {
                val code = if (fe is StorageException) fe.errorCode.toString() else "NA"
                uploadError = fe.javaClass.simpleName
                uploadErrorDetail = "code=${code} msg=${fe.message}"
                Log.e("SyncManager", "Falha tentativa ${attempt} fotoId=${foto.id} code=${code}: ${fe.message}")
                                if (attempt < maxAttempts) {
                                    val backoff = attempt * 750L
                                    Log.d("SyncManager", "Retry em ${backoff}ms fotoId=${foto.id}")
                                    delay(backoff)
                                }
                            }

                                // (syncMissfire removido daqui - movido para escopo do objeto)
                        }
                        if (remoteUrl == null) Log.e("SyncManager", "Upload falhou definitivamente fotoId=${foto.id}")
                    }
                }
            }

            remoteUrl?.let { photoUrls.add(it) }
            photoList.add(
                mapOf(
                    "url" to remoteUrl,
                    "latitude" to foto.latitude,
                    "longitude" to foto.longitude,
                    "altitude" to foto.altitude,
                    "sistemaCoordenadas" to foto.sistemaCoordenadas,
                    "dataHora" to foto.dataHora,
                    "uploadError" to uploadError,
                    "uploadErrorDetail" to uploadErrorDetail,
                    "sizeBytes" to sizeBytes
                )
            )
        }

        val mainPhotoUrl = photoUrls.firstOrNull()
    val (versionCode, versionName) = getAppVersion(context)
    val data = hashMapOf(
            "id" to entity.id, // id local
            "remoteId" to remoteId,
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
            // Metadados de auditoria
            "createdByUserId" to uid,
            "appVersionCode" to versionCode,
            "appVersionName" to versionName,
            "device" to (Build.MANUFACTURER + " " + Build.MODEL),
            "installationId" to getInstallationId(context),
            "photoUrl" to mainPhotoUrl,
            "photoUrls" to photoUrls,
            "photoList" to photoList
        )

        Log.d("SyncManager", "Enviando Firestore doc=${remoteId} localId=${entity.id} fotos=${photoList.size}")
        firestore.collection("backups").document(remoteId!!).set(data).await()
        Log.d("SyncManager", "Firestore OK doc=${remoteId}")

        val now = System.currentTimeMillis()
        val synced = entity.copy(
            status = "SINCRONIZADO",
            syncError = false,
            syncErrorMessage = null,
            remoteId = remoteId,
            lastSyncAt = now
        )
        withContext(Dispatchers.IO) {
            val dao2 = BackupDatabase.getDatabase(context).backupDao()
            dao2.updateFull(
                id = synced.id,
                remoteId = synced.remoteId,
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
                syncError = synced.syncError,
                syncErrorMessage = synced.syncErrorMessage,
                lastSyncAt = synced.lastSyncAt
            )
        }
        Result.success(synced)
    } catch (e: Exception) {
        Log.e("SyncManager", "Falha sync id=${entity.id}", e)
        withContext(Dispatchers.IO) {
            val dao2 = BackupDatabase.getDatabase(context).backupDao()
            val failed = entity.copy(
                status = entity.status,
                syncError = true,
                syncErrorMessage = e.message,
                lastSyncAt = entity.lastSyncAt
            )
            runCatching {
                dao2.updateFull(
                    id = failed.id,
                    remoteId = failed.remoteId,
                    data = failed.data,
                    unidade = failed.unidade,
                    cava = failed.cava,
                    banco = failed.banco,
                    fogoId = failed.fogoId,
                    furoNumero = failed.furoNumero,
                    detonadorNumero = failed.detonadorNumero,
                    espoletaId = failed.espoletaId,
                    motivo = failed.motivo,
                    tipoDetonador = failed.tipoDetonador,
                    caboDetonador = failed.caboDetonador,
                    metragem = failed.metragem,
                    tentativaRecuperacao = failed.tentativaRecuperacao,
                    coordenadaX = failed.coordenadaX,
                    coordenadaY = failed.coordenadaY,
                    coordenadaZ = failed.coordenadaZ,
                    sistemaCoordenadas = failed.sistemaCoordenadas,
                    status = failed.status,
                    syncError = failed.syncError,
                    syncErrorMessage = failed.syncErrorMessage,
                    lastSyncAt = failed.lastSyncAt
                )
            }
        }
        Result.failure(e)
    }

    // ================== MISSFIRE SYNC (escopo do objeto) ==================
    suspend fun syncMissfire(context: Context, missfireId: Int): Result<Unit> = try {
    val auth = FirebaseAuth.getInstance()
    if (auth.currentUser == null) runCatching { auth.signInAnonymously().await() }
    val uid = FirebaseAuth.getInstance().currentUser?.uid

        val db = BackupDatabase.getDatabase(context)
        val missfireDao = db.missfireDao()

        val missfire = missfireDao.getMissfireById(missfireId) ?: return Result.failure(IllegalArgumentException("Missfire não encontrado"))
        var remoteId = missfire.remoteId
        if (remoteId.isNullOrBlank()) {
            remoteId = java.util.UUID.randomUUID().toString()
            missfireDao.updateMissfireRemoteId(missfire.id, remoteId)
        }

        val attachments = missfireDao.getAttachments(missfire.id)
        val updates = missfireDao.getUpdates(missfire.id)

        for (att in attachments) {
            if (att.remoteUrl == null) {
                val file = java.io.File(att.localPath)
                if (!file.exists()) continue
                val ext = when {
                    att.mimeType.contains("png") -> "png"
                    att.mimeType.contains("pdf") -> "pdf"
                    att.mimeType.contains("excel") || att.mimeType.contains("spreadsheetml") -> "xlsx"
                    else -> "jpg"
                }
                val ref = storage.reference.child("missfires/${remoteId}/attachments/${att.id}.${ext}")
                runCatching {
                    ref.putFile(Uri.fromFile(file)).await()
                    val url = ref.downloadUrl.await().toString()
                    missfireDao.updateAttachmentRemoteUrl(att.id, url)
                }.onFailure { ex -> Log.e("SyncManager","Falha upload attachment id=${att.id}", ex) }
            }
        }

        val attsAtualizados = missfireDao.getAttachments(missfire.id)
        val fotoUrls = attsAtualizados.filter { it.tipo == "FOTO" && it.remoteUrl != null }.map { it.remoteUrl!! }
        val arquivosMeta = attsAtualizados.map { a ->
            mapOf(
                "id" to a.id,
                "tipo" to a.tipo,
                "mimeType" to a.mimeType,
                "remoteUrl" to a.remoteUrl,
                "tamanhoBytes" to a.tamanhoBytes,
                "createdAt" to a.createdAt
            )
        }
    val (mVersionCode, mVersionName) = getAppVersion(context)
    val missfireDoc = hashMapOf(
            "localId" to missfire.id,
            "remoteId" to remoteId,
            "dataOcorrencia" to missfire.dataOcorrencia,
            "local" to missfire.local,
            "responsavel" to missfire.responsavel,
            "itensEncontrados" to missfire.itensEncontrados,
            "descricaoOcorrencia" to missfire.descricaoOcorrencia,
            "statusInvestigacao" to missfire.statusInvestigacao,
            "causa" to missfire.causa,
            "medidasPreventivas" to missfire.medidasPreventivas,
            "createdAt" to missfire.createdAt,
            // Metadados de auditoria
            "createdByUserId" to uid,
            "appVersionCode" to mVersionCode,
            "appVersionName" to mVersionName,
            "device" to (Build.MANUFACTURER + " " + Build.MODEL),
            "installationId" to getInstallationId(context),
            "lastUpdated" to missfire.lastUpdated,
            "photoUrls" to fotoUrls,
            "attachments" to arquivosMeta
        )
        firestore.collection("missfires").document(remoteId!!).set(missfireDoc).await()

        val now = System.currentTimeMillis()
        for (u in updates) {
            if (u.remoteId == null) {
                val newId = java.util.UUID.randomUUID().toString()
                val data = mapOf(
                    "texto" to u.texto,
                    "createdAt" to u.createdAt,
                    "userId" to u.userId
                )
                firestore.collection("missfires").document(remoteId!!)
                    .collection("updates").document(newId).set(data).await()
                missfireDao.updateUpdateRemote(u.id, newId, now)
            }
        }

        missfireDao.updateMissfireSyncState(missfire.id, now, false, null)
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e("SyncManager", "Falha sync Missfire id=${missfireId}", e)
        runCatching {
            val db = BackupDatabase.getDatabase(context)
            db.missfireDao().updateMissfireSyncState(missfireId, null, true, e.message)
        }
        Result.failure(e)
    }
    // =====================================================================
}
