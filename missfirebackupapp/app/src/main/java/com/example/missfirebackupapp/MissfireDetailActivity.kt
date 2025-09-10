package com.example.missfirebackupapp

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.view.ViewGroup
import android.view.View
import android.widget.Space
import android.widget.Toast
import android.net.Uri
import android.content.Intent
import android.provider.OpenableColumns
import android.view.LayoutInflater
import java.io.File
import androidx.core.content.FileProvider
import androidx.appcompat.app.AppCompatActivity
import com.example.missfirebackupapp.data.BackupDatabase
import com.example.missfirebackupapp.data.MissfireAttachmentEntity
import com.example.missfirebackupapp.data.MisfireEntity
import com.example.missfirebackupapp.data.MisfireRepository
import com.example.missfirebackupapp.data.MissfireUpdateEntity
import com.example.missfirebackupapp.SyncManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.view.children

class MisfireDetailActivity : AppCompatActivity() {
    private val job = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + job)
    private var missfireId: Int = -1
    private lateinit var repository: MisfireRepository

    private lateinit var tvResumoInicial: TextView
    private lateinit var containerUpdates: LinearLayout
    private lateinit var containerFotosResumo: LinearLayout
    private lateinit var btnNovaAtualizacao: MaterialButton
    private lateinit var btnAtualizarInvestigacao: MaterialButton
    private lateinit var btnSincronizar: MaterialButton
    private lateinit var btnConcluir: MaterialButton

    private var missfire: MisfireEntity? = null

    // ---- anexos temporários (nova atualização) ----
    private data class TempAttachment(
        val uri: Uri,
        val displayName: String,
        val mime: String,
        val size: Long
    )
    private val tempAttachments = mutableListOf<TempAttachment>()
    private var pendingPhotoUri: Uri? = null
    private val takePictureLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingPhotoUri
        if (success && uri != null) {
            val meta = queryMeta(uri)
            tempAttachments.add(
                TempAttachment(
                    uri = uri,
                    displayName = meta.first ?: "foto_${System.currentTimeMillis()}.jpg",
                    mime = meta.second ?: "image/jpeg",
                    size = meta.third
                )
            )
            refreshAttachmentChips()
        }
        pendingPhotoUri = null
    }

    private val pickDocumentsLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNullOrEmpty()) return@registerForActivityResult
        uris.forEach { uri ->
            takePersistable(uri)
            val meta = queryMeta(uri)
            tempAttachments.add(
                TempAttachment(
                    uri = uri,
                    displayName = meta.first ?: uri.lastPathSegment.orEmpty(),
                    mime = meta.second ?: contentResolver.getType(uri) ?: "application/octet-stream",
                    size = meta.third
                )
            )
        }
        refreshAttachmentChips()
    }

    private fun takePersistable(uri: Uri) { try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {} }
    private fun queryMeta(uri: Uri): Triple<String?, String?, Long> {
        var name: String? = null; var mime: String? = null; var size = 0L
        try {
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                if (c.moveToFirst()) {
                    if (nameIdx>=0) name = c.getString(nameIdx)
                    if (sizeIdx>=0) size = c.getLong(sizeIdx)
                }
            }
            mime = contentResolver.getType(uri)
        } catch (_: Exception) {}
        return Triple(name, mime, size)
    }
    private fun refreshAttachmentChips() {
        val container = dialogAttachmentsContainer ?: return
        container.removeAllViews()
        if (tempAttachments.isEmpty()) {
            container.addView(TextView(this).apply { text = "Nenhum anexo"; setTextColor(resources.getColor(android.R.color.darker_gray, theme)) })
        } else {
            tempAttachments.forEachIndexed { index, a ->
                val chip = Chip(this).apply {
                    text = "${iconForMime(a.mime)} ${abbreviate(a.displayName)}"
                    isCloseIconVisible = true
                    setOnCloseIconClickListener {
                        tempAttachments.removeAt(index)
                        refreshAttachmentChips()
                    }
                    setOnClickListener { openAttachment(a.uri, a.mime) }
                }
                container.addView(chip)
            }
        }
    }
    private fun iconForMime(mime: String): String = when {
        mime.startsWith("image") -> "📷"
        mime == "application/pdf" -> "📄"
        mime.contains("excel") -> "📊"
        mime.startsWith("video") -> "🎬"
        else -> "📎"
    }
    private fun openAttachment(uri: Uri, mime: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Abrir anexo"))
        } catch (_: Exception) { Toast.makeText(this, "Não foi possível abrir", Toast.LENGTH_SHORT).show() }
    }
    private fun abbreviate(name: String, max: Int = 22) = if (name.length <= max) name else name.take(max-3) + "..."

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_miss_fire_detail)
        missfireId = intent.getIntExtra("misfire_id", -1)
        if (missfireId == -1) { Toast.makeText(this, "ID inválido", Toast.LENGTH_SHORT).show(); finish(); return }
        repository = MisfireRepository(BackupDatabase.getDatabase(this).missfireDao())
    bindViews()
    setupToolbar()
    carregarDados()
    }

    private fun carregarDados() {
        uiScope.launch(Dispatchers.IO) {
            val existente = repository.obterMissfire(missfireId)
            if (existente == null) {
                launch(Dispatchers.Main) { Toast.makeText(this@MisfireDetailActivity, "Não encontrado", Toast.LENGTH_SHORT).show(); finish() }
                return@launch
            }
            missfire = existente
            val updates = repository.listarUpdates(missfireId)
            val atts = repository.listarAttachments(missfireId)
            launch(Dispatchers.Main) {
                renderResumo(existente, atts)
                renderUpdates(updates, atts)
                aplicarEstadoConclusaoSeNecessario()
            }
        }
    }

    private fun bindViews() {
        tvResumoInicial = findViewById(R.id.tvResumoInicial)
        containerUpdates = findViewById(R.id.containerUpdates)
    containerFotosResumo = findViewById(R.id.containerFotosResumo)
        btnNovaAtualizacao = findViewById(R.id.btnNovaAtualizacaoDetail)
        btnAtualizarInvestigacao = findViewById(R.id.btnAtualizarInvestigacaoDetail)
        btnSincronizar = findViewById(R.id.btnSincronizarMissfireDetail)
        btnConcluir = findViewById(R.id.btnConcluirInvestigacaoDetail)
        btnNovaAtualizacao.setOnClickListener { abrirDialogAdicionarUpdate() }
        btnAtualizarInvestigacao.setOnClickListener { salvarInvestigacaoLocal() }
        btnSincronizar.setOnClickListener { sincronizarMissfire() }
        btnConcluir.setOnClickListener { concluirMissfireDialog() }
    }

    private fun setupToolbar() {
        val tb = findViewById<MaterialToolbar>(R.id.topAppBarMissfireDetail)
        tb.setNavigationOnClickListener { finish() }
    }

    private var dialogAttachmentsContainer: ViewGroup? = null
    private var isSyncing: Boolean = false

    private fun renderResumo(entidade: MisfireEntity, attachments: List<MissfireAttachmentEntity>) {
        val sdfFull = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val sdfDay = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val fotos = attachments.count { it.tipo == "FOTO" }
        val arquivos = attachments.count { it.tipo != "FOTO" }
        val sb = StringBuilder()
        sb.appendLine("ID: ${entidade.id}")
        sb.appendLine("Status investigação: ${entidade.statusInvestigacao}")
        sb.appendLine("Data ocorrência: ${sdfFull.format(Date(entidade.dataOcorrencia))}")
        entidade.dataDesmonte?.let { sb.appendLine("Data desmonte: ${sdfDay.format(Date(it))}") }
        sb.appendLine("Responsável: ${entidade.responsavel}")
        sb.appendLine("Local: ${entidade.local}")
        sb.appendLine("Itens encontrados: ${entidade.itensEncontrados}")
        sb.appendLine("Descrição: ${entidade.descricaoOcorrencia}")
        entidade.causa?.takeIf { it.isNotBlank() }?.let { sb.appendLine("Causa: $it") }
        entidade.medidasPreventivas?.takeIf { it.isNotBlank() }?.let { sb.appendLine("Medidas preventivas: $it") }
        entidade.infoAdicionais?.takeIf { it.isNotBlank() }?.let { sb.appendLine("Info adicionais: $it") }
        sb.appendLine("Criado em: ${sdfFull.format(Date(entidade.createdAt))}")
        sb.appendLine("Última atualização: ${sdfFull.format(Date(entidade.lastUpdated))}")
        sb.appendLine("Total fotos: $fotos | Arquivos: $arquivos")
        tvResumoInicial.text = sb.toString()

        // thumbnails das fotos iniciais (updateId null)
        containerFotosResumo.removeAllViews()
        val fotosIniciais = attachments.filter { it.updateId == null && it.tipo == "FOTO" }
        if (fotosIniciais.isNotEmpty()) {
            fotosIniciais.forEach { att ->
                containerFotosResumo.addView(buildImageThumb(att.localPath))
            }
        }
    }

    private fun buildImageThumb(path: String): View {
        val size = (60 * resources.displayMetrics.density).toInt()
        return androidx.appcompat.widget.AppCompatImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(size, size).apply { rightMargin = 12 }
            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            setBackgroundResource(R.drawable.bg_thumbnail)
            clipToOutline = true
            try { setImageURI(Uri.parse(path)) } catch (_: Exception) {}
            setOnClickListener { openGallery(listOf(path)) }
        }
    }

    private fun openGallery(imagePaths: List<String>) {
        val intent = Intent(this, PhotoGalleryActivity::class.java).apply {
            putStringArrayListExtra("photo_uris", ArrayList(imagePaths))
        }
        startActivity(intent)
    }

    private fun renderUpdates(updates: List<MissfireUpdateEntity>, attachments: List<MissfireAttachmentEntity>) {
        containerUpdates.removeAllViews()

        // fotos iniciais já exibidas no resumo; base attachments não-foto (arquivos) podem ser mostrados antes da lista
        val baseArquivos = attachments.filter { it.updateId == null && it.tipo != "FOTO" }
        if (baseArquivos.isNotEmpty()) {
            val blocoArquivos = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0,8,0,8) }
            val label = TextView(this).apply { text = "Arquivos iniciais"; setTextColor(resources.getColor(R.color.white, theme)); setTypeface(typeface, android.graphics.Typeface.BOLD) }
            blocoArquivos.addView(label)
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            baseArquivos.take(10).forEach { att ->
                val chip = Chip(this).apply {
                    text = "${iconForMime(att.mimeType ?: "")} ${abbreviate(att.localPath.substringAfterLast('/'))}"
                    isCloseIconVisible = false
                    setOnClickListener { openAttachment(Uri.parse(att.localPath), att.mimeType ?: "application/octet-stream") }
                }
                row.addView(chip)
            }
            blocoArquivos.addView(row)
            containerUpdates.addView(blocoArquivos)
        }

        if (updates.isEmpty()) {
            val tv = TextView(this).apply { text = "Nenhuma atualização"; setTextColor(resources.getColor(android.R.color.darker_gray, theme)) }
            containerUpdates.addView(tv); return
        }
        val grouped = attachments.filter { it.updateId != null }.groupBy { it.updateId }
        val sorted = updates.sortedBy { it.createdAt } // chronological for numbering
        sorted.forEachIndexed { index, upd ->
            val bloco = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 12, 0, 12)
                setBackgroundResource(R.drawable.bg_update_card)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = 12
                }
            }
            val headerRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val headerText = TextView(this).apply {
                val sdf = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
                text = "Atualização ${String.format("%02d", index+1)} - ${sdf.format(Date(upd.createdAt))}"
                setTextColor(resources.getColor(android.R.color.white, theme))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            headerRow.addView(headerText)
            // status chip
            val statusLabel: Pair<String, Int> = when {
                missfire?.statusInvestigacao == "CONCLUIDA" -> "Concluída" to R.color.statusConcluida
                upd.remoteId == null -> "Local" to R.color.statusLocal
                else -> "Nuvem" to R.color.statusCloud
            }
            val chipStatus = Chip(this).apply {
                text = statusLabel.first
                isCloseIconVisible = false
                setTextColor(resources.getColor(R.color.white, theme))
                chipBackgroundColor = android.content.res.ColorStateList.valueOf(resources.getColor(statusLabel.second, theme))
            }
            headerRow.addView(chipStatus)
            bloco.addView(headerRow)
            // corpo texto
            bloco.addView(TextView(this).apply {
                text = upd.texto
                setTextColor(resources.getColor(R.color.white, theme))
                setPadding(0,8,0,8)
            })
            if (missfire?.statusInvestigacao != "CONCLUIDA" && upd.remoteId == null && !isSyncing) {
                val actions = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                }
                // helper lambdas for consistent styling
                fun secondaryButton(label: String, onClick: () -> Unit): MaterialButton =
                    MaterialButton(this).apply {
                        text = label
                        isAllCaps = false
                        setTextColor(resources.getColor(R.color.white, theme))
                        backgroundTintList = android.content.res.ColorStateList.valueOf(resources.getColor(R.color.buttonSecondary, theme))
                        cornerRadius = 40
                        insetTop = 0; insetBottom = 0
                        minimumHeight = 0
                        setPadding(40,20,40,20)
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                            rightMargin = 16
                        }
                        setOnClickListener { onClick() }
                    }
                fun primaryButton(label: String, onClick: () -> Unit): MaterialButton =
                    MaterialButton(this).apply {
                        text = label
                        isAllCaps = false
                        setTextColor(resources.getColor(R.color.white, theme))
                        backgroundTintList = android.content.res.ColorStateList.valueOf(resources.getColor(R.color.redAccent, theme))
                        cornerRadius = 40
                        insetTop = 0; insetBottom = 0
                        minimumHeight = 0
                        setPadding(40,20,40,20)
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        setOnClickListener { onClick() }
                    }
                val btnEdit = secondaryButton("Editar") { abrirDialogEditarUpdate(upd) }
                val btnAddAtt = secondaryButton("+Arquivo") { abrirDialogAdicionarAnexoUpdate(upd) }
                val btnSync = primaryButton("Enviar") { sincronizarMissfire() }.apply { isEnabled = !isSyncing }
                actions.addView(btnEdit)
                actions.addView(btnAddAtt)
                actions.addView(btnSync)
                bloco.addView(actions)
            }
            val atts = grouped[upd.id] ?: emptyList()
            val fotosUpdate = atts.filter { it.tipo == "FOTO" }
            val arquivosUpdate = atts.filter { it.tipo != "FOTO" }
            if (fotosUpdate.isNotEmpty()) {
                val allPaths = fotosUpdate.map { it.localPath }
                val fotoRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0,4,0,4) }
                fotosUpdate.take(5).forEach { f -> fotoRow.addView(buildImageThumb(f.localPath)) }
                if (fotosUpdate.size > 5) {
                    val more = MaterialButton(this).apply {
                        text = "+${fotosUpdate.size - 5}"
                        isAllCaps = false
                        backgroundTintList = android.content.res.ColorStateList.valueOf(resources.getColor(R.color.buttonSecondary, theme))
                        setTextColor(resources.getColor(R.color.white, theme))
                        cornerRadius = 40
                        setOnClickListener { openGallery(allPaths) }
                    }
                    fotoRow.addView(more)
                } else {
                    // clicking any thumb opens full gallery with all photos for the update
                    fotoRow.children.forEach { view -> view.setOnClickListener { openGallery(allPaths) } }
                }
                bloco.addView(fotoRow)
            }
            if (arquivosUpdate.isNotEmpty()) {
                val fileRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                arquivosUpdate.take(10).forEach { att ->
                    val chip = Chip(this).apply {
                        text = "${iconForMime(att.mimeType ?: "")} ${abbreviate(att.localPath.substringAfterLast('/'))}"
                        isCloseIconVisible = false
                        setOnClickListener { openAttachment(Uri.parse(att.localPath), att.mimeType ?: "application/octet-stream") }
                    }
                    fileRow.addView(chip)
                }
                bloco.addView(fileRow)
            }
            containerUpdates.addView(bloco)
        }
    }
    private fun abrirDialogEditarUpdate(update: MissfireUpdateEntity) {
    if (missfire?.statusInvestigacao == "CONCLUIDA") { Toast.makeText(this, "Relatório concluído", Toast.LENGTH_SHORT).show(); return }
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_editar_update, null)
        val input = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.inputEditarTextoUpdate)
        val btnSalvar = view.findViewById<MaterialButton>(R.id.btnSalvarEditarUpdate)
        val btnCancelar = view.findViewById<MaterialButton>(R.id.btnCancelarEditarUpdate)
        input.setText(update.texto)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this).setView(view).create()
        btnSalvar.setOnClickListener {
            val novo = input.text?.toString()?.trim().orEmpty()
            if (novo.isBlank()) { Toast.makeText(this, "Vazio", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            uiScope.launch(Dispatchers.IO) {
                repository.editarTextoUpdateSeNaoSincronizado(update.id, novo)
                val ups = repository.listarUpdates(missfireId)
                val atts = repository.listarAttachments(missfireId)
                launch(Dispatchers.Main) { renderUpdates(ups, atts); dialog.dismiss() }
            }
        }
        btnCancelar.setOnClickListener { dialog.dismiss() }
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }
    private fun abrirDialogAdicionarAnexoUpdate(update: MissfireUpdateEntity) {
    if (missfire?.statusInvestigacao == "CONCLUIDA") { Toast.makeText(this, "Relatório concluído", Toast.LENGTH_SHORT).show(); return }
        tempAttachments.clear()
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_anexos_update, null)
        val btnAddArquivo = view.findViewById<MaterialButton>(R.id.btnAddArquivo)
        val btnAddFoto = view.findViewById<MaterialButton>(R.id.btnAddFoto)
        val btnSalvar = view.findViewById<MaterialButton>(R.id.btnSalvarAnexosUpdate)
        val btnCancelar = view.findViewById<MaterialButton>(R.id.btnCancelarAnexosUpdate)
        val chips = view.findViewById<ViewGroup>(R.id.containerAnexosUpdate)
        dialogAttachmentsContainer = chips
        refreshAttachmentChips()
        btnAddArquivo.setOnClickListener {
            if (!canAddMoreAttachments()) return@setOnClickListener
            pickDocumentsLauncher.launch(arrayOf("image/*","application/pdf","application/vnd.ms-excel","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","video/*"))
        }
        btnAddFoto.setOnClickListener {
            if (!canAddMoreAttachments()) return@setOnClickListener
            escolherFonteFoto { fromCamera ->
                if (fromCamera) launchCameraForAttachment() else pickDocumentsLauncher.launch(arrayOf("image/*"))
            }
        }
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this).setView(view).create()
        btnSalvar.setOnClickListener {
            uiScope.launch(Dispatchers.IO) {
                tempAttachments.forEach { tmp ->
                    repository.adicionarAttachment(
                        MissfireAttachmentEntity(
                            missfireId = missfireId,
                            updateId = update.id,
                            tipo = if (tmp.mime.startsWith("image")) "FOTO" else "ARQUIVO",
                            localPath = tmp.uri.toString(),
                            mimeType = tmp.mime,
                            tamanhoBytes = tmp.size
                        )
                    )
                }
                val ups = repository.listarUpdates(missfireId)
                val atts = repository.listarAttachments(missfireId)
                launch(Dispatchers.Main) { renderUpdates(ups, atts); dialog.dismiss() }
            }
        }
        btnCancelar.setOnClickListener { dialog.dismiss() }
        dialog.setOnDismissListener { tempAttachments.clear(); dialogAttachmentsContainer = null }
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun abrirDialogAdicionarUpdate() {
    if (missfire?.statusInvestigacao == "CONCLUIDA") { Toast.makeText(this, "Relatório concluído", Toast.LENGTH_SHORT).show(); return }
        tempAttachments.clear()
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_nova_atualizacao, null)
        val input = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.inputTextoUpdate)
        val btnAddArquivo = view.findViewById<MaterialButton>(R.id.btnAddArquivo)
        val btnAddFoto = view.findViewById<MaterialButton>(R.id.btnAddFoto)
        val chipsContainer = view.findViewById<ViewGroup>(R.id.containerAnexosNovoUpdate)
        val btnSalvar = view.findViewById<MaterialButton>(R.id.btnSalvarUpdate)
        val btnCancelar = view.findViewById<MaterialButton>(R.id.btnCancelarUpdate)

        dialogAttachmentsContainer = chipsContainer
        refreshAttachmentChips()

        btnAddArquivo.setOnClickListener {
            if (!canAddMoreAttachments()) return@setOnClickListener
            pickDocumentsLauncher.launch(arrayOf("image/*","application/pdf","application/vnd.ms-excel","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","video/*"))
        }
        btnAddFoto.setOnClickListener {
            if (!canAddMoreAttachments()) return@setOnClickListener
            escolherFonteFoto { fromCamera ->
                if (fromCamera) launchCameraForAttachment() else pickDocumentsLauncher.launch(arrayOf("image/*"))
            }
        }

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(view)
            .create()

        btnSalvar.setOnClickListener {
            val texto = input.text?.toString()?.trim().orEmpty()
            if (texto.isBlank()) { Toast.makeText(this, "Vazio", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            uiScope.launch(Dispatchers.IO) {
                val updateId = repository.adicionarUpdate(MissfireUpdateEntity(missfireId = missfireId, texto = texto))
                tempAttachments.takeIf { it.isNotEmpty() }?.forEach { tmp ->
                    repository.adicionarAttachment(
                        MissfireAttachmentEntity(
                            missfireId = missfireId,
                            updateId = updateId,
                            tipo = if (tmp.mime.startsWith("image")) "FOTO" else "ARQUIVO",
                            localPath = tmp.uri.toString(),
                            mimeType = tmp.mime,
                            tamanhoBytes = tmp.size
                        )
                    )
                }
                val ups = repository.listarUpdates(missfireId)
                val atts = repository.listarAttachments(missfireId)
                launch(Dispatchers.Main) {
                    missfire?.let { renderResumo(it, atts) }
                    renderUpdates(ups, atts)
                    Toast.makeText(this@MisfireDetailActivity, "Atualização adicionada", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            }
        }
        btnCancelar.setOnClickListener { dialog.dismiss() }

        dialog.setOnDismissListener { tempAttachments.clear(); dialogAttachmentsContainer = null }
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun canAddMoreAttachments(): Boolean {
        val max = 10
        if (tempAttachments.size >= max) {
            Toast.makeText(this, "Limite de $max anexos", Toast.LENGTH_SHORT).show(); return false
        }
        return true
    }
    private fun launchCameraForAttachment() {
        try {
            val file = File(cacheDir, "upd_photo_${'$'}{System.currentTimeMillis()}.jpg")
            val authority = "${'$'}packageName.fileprovider"
            val uri = FileProvider.getUriForFile(this, authority, file)
            pendingPhotoUri = uri
            takePictureLauncher.launch(uri)
        } catch (e: Exception) { Toast.makeText(this, "Erro câmera: ${'$'}{e.message}", Toast.LENGTH_SHORT).show() }
    }

    private fun escolherFonteFoto(onResult: (fromCamera: Boolean) -> Unit) {
        val opcoes = arrayOf("Câmera", "Galeria")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Adicionar foto")
            .setItems(opcoes) { _, which ->
                when(which) {
                    0 -> onResult(true)
                    1 -> onResult(false)
                }
            }.show()
    }

    // ---- existing functions below (sincronizar, concluir etc.) ----
    // (Removed duplicated malformed code block)

    private fun salvarInvestigacaoLocal() { Toast.makeText(this, "Alterações locais salvas", Toast.LENGTH_SHORT).show() }

    private fun sincronizarMissfire() {
        if (isSyncing) return
        isSyncing = true
        // Atualização visual mínima (desabilita botões) sem recarregar dados
        missfire?.let { current ->
            renderUpdates(emptyList(), emptyList()) // limpa para evitar ações repetidas
        }
        uiScope.launch {
            val result = SyncManager.syncMissfire(this@MisfireDetailActivity, missfireId)
            result.onSuccess {
                Toast.makeText(this@MisfireDetailActivity, "Sync ok", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(this@MisfireDetailActivity, "Falha: ${it.message}", Toast.LENGTH_LONG).show()
            }
            val ups = repository.listarUpdates(missfireId)
            val atts = repository.listarAttachments(missfireId)
            isSyncing = false
            renderUpdates(ups, atts)
            missfire?.let { renderResumo(it, atts) }
            aplicarEstadoConclusaoSeNecessario()
        }
    }

    // helper removido (não mais necessário)

    private fun concluirMissfireDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20,20,20,20)
            setBackgroundResource(R.drawable.bg_dialog_update)
        }
        val titulo = TextView(this).apply {
            text = "Concluir investigação"
            setTextColor(resources.getColor(R.color.white, theme))
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        container.addView(titulo)
        container.addView(Space(this).apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,12) })
        fun styledInputLayout(label: String, tagValue: String): com.google.android.material.textfield.TextInputLayout =
            com.google.android.material.textfield.TextInputLayout(this, null, com.google.android.material.R.attr.textInputStyle).apply {
                hint = label
                boxBackgroundMode = com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE
                setBoxBackgroundColorResource(R.color.fieldBackground)
                boxStrokeColor = resources.getColor(R.color.redAccent, theme)
                setPadding(0,0,0,0)
                addView(com.google.android.material.textfield.TextInputEditText(context).apply {
                    id = View.generateViewId()
                    tag = tagValue
                    setTextColor(resources.getColor(R.color.white, theme))
                    setHintTextColor(resources.getColor(R.color.gray, theme))
                })
            }
        val inputCausa = styledInputLayout("Causa", "inputCausa")
        val inputMedidas = styledInputLayout("Medidas Preventivas", "inputMedidas")
        container.addView(inputCausa)
        container.addView(Space(this).apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,12) })
        container.addView(inputMedidas)
        container.addView(Space(this).apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,20) })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 2f }
        val btnCancelar = MaterialButton(this).apply {
            text = "Cancelar"; setTextColor(resources.getColor(R.color.white, theme)); backgroundTintList = android.content.res.ColorStateList.valueOf(resources.getColor(R.color.buttonSecondary, theme)); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT,1f)
        }
        val space = Space(this).apply { layoutParams = LinearLayout.LayoutParams(12, LinearLayout.LayoutParams.WRAP_CONTENT) }
        val btnConcluir = MaterialButton(this).apply {
            text = "Concluir"; setTextColor(resources.getColor(R.color.white, theme)); backgroundTintList = android.content.res.ColorStateList.valueOf(resources.getColor(R.color.redAccent, theme)); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT,1f)
        }
        row.addView(btnCancelar)
        row.addView(space)
        row.addView(btnConcluir)
        container.addView(row)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this).setView(container).create()
        btnCancelar.setOnClickListener { dialog.dismiss() }
        btnConcluir.setOnClickListener {
            val causaTxt = (inputCausa.editText?.text?.toString() ?: "").trim()
            val medidasTxt = (inputMedidas.editText?.text?.toString() ?: "").trim()
            if (causaTxt.isBlank() || medidasTxt.isBlank()) { Toast.makeText(this, "Preencha ambos", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            dialog.dismiss()
            concluirMissfire(causaTxt, medidasTxt)
        }
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun concluirMissfire(causa: String, medidas: String) {
        uiScope.launch(Dispatchers.IO) {
            val atual = repository.obterMissfire(missfireId) ?: return@launch
            if (atual.statusInvestigacao == "CONCLUIDA") {
                launch(Dispatchers.Main) { Toast.makeText(this@MisfireDetailActivity, "Já concluída", Toast.LENGTH_SHORT).show() }
                return@launch
            }
            val atualizado = atual.copy(
                statusInvestigacao = "CONCLUIDA",
                causa = causa,
                medidasPreventivas = medidas,
                lastUpdated = System.currentTimeMillis()
            )
            repository.atualizarMissfire(atualizado)
            val ups = repository.listarUpdates(missfireId)
            val atts = repository.listarAttachments(missfireId)
            launch(Dispatchers.Main) {
                missfire = atualizado
                Toast.makeText(this@MisfireDetailActivity, "Concluída", Toast.LENGTH_SHORT).show()
                renderResumo(atualizado, atts)
                renderUpdates(ups, atts)
                aplicarEstadoConclusaoSeNecessario()
                // tenta sincronizar automaticamente ao concluir
                uiScope.launch {
                    val result = SyncManager.syncMissfire(this@MisfireDetailActivity, missfireId)
                    result.onSuccess {
                        Toast.makeText(this@MisfireDetailActivity, "Relatório sincronizado", Toast.LENGTH_SHORT).show()
                    }.onFailure {
                        Toast.makeText(this@MisfireDetailActivity, "Conclusão salva localmente. Falha ao sincronizar: ${it.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun aplicarEstadoConclusaoSeNecessario() {
        val concluida = missfire?.statusInvestigacao == "CONCLUIDA"
        if (concluida) {
            // Desabilita e remove listeners
            listOf(btnNovaAtualizacao, btnAtualizarInvestigacao, btnSincronizar, btnConcluir).forEach { b ->
                b.isEnabled = false
                b.isClickable = false
                b.alpha = 0.35f
                b.setOnClickListener { Toast.makeText(this, "Relatório concluído", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    override fun onDestroy() { super.onDestroy(); job.cancel() }

    override fun onResume() {
        super.onResume()
        aplicarEstadoConclusaoSeNecessario()
    }
}
