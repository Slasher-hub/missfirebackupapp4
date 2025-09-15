package com.example.missfirebackupapp

import android.app.DatePickerDialog
import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageView
import android.widget.FrameLayout
import android.graphics.BitmapFactory
import android.view.View
import android.widget.Toast
import android.text.Editable
import android.text.TextWatcher
import android.content.res.ColorStateList
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.missfirebackupapp.data.BackupDatabase
import com.example.missfirebackupapp.data.MisfireEntity
import com.example.missfirebackupapp.data.MisfireRepository
import com.example.missfirebackupapp.data.MissfireAttachmentEntity
import com.example.missfirebackupapp.data.MissfireUpdateEntity // kept for potential reuse but updates UI removed
import com.example.missfirebackupapp.SyncManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MisfireActivity : AppCompatActivity() {
    private val uiScope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var repository: MisfireRepository

    private lateinit var inputData: TextInputEditText
    private lateinit var inputLocal: AutoCompleteTextView
    private lateinit var inputResponsavel: TextInputEditText
    private lateinit var inputTipoRegistro: AutoCompleteTextView
    private lateinit var inputItens: TextInputEditText
    private lateinit var inputDescricao: TextInputEditText

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private var dataOcorrenciaMillis: Long? = null

    // Anexos (fase 1: apenas fotos)
    private val MAX_FOTOS = 5
    private data class PendingFoto(val path: String)
    private val pendingFotos = mutableListOf<PendingFoto>()
    private var currentPhotoPath: String? = null
    private val pendingArquivos = mutableListOf<PendingArquivo>()
    private data class PendingArquivo(val path: String, val mime: String, val nome: String, val tamanho: Long)

    private val MIME_PERMITIDOS = arrayOf(
        "application/pdf",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "image/jpeg", "image/png"
    )
    private val TAMANHO_MAX_ARQ = 10L * 1024 * 1024 // 10MB

    // Launchers
    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && currentPhotoPath != null) {
            adicionarFotoPath(currentPhotoPath!!)
        } else if (!success) {
            toast("Captura cancelada")
        }
        currentPhotoPath = null
    }
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { copiarDaGallery(it) }
    }
    private val pickFilesLauncher = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { /* removido uso de arquivos nesta tela */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_miss_fire) // layout mantém nome antigo por enquanto

    repository = MisfireRepository(BackupDatabase.getDatabase(this).missfireDao())

        // Toolbar
    findViewById<MaterialToolbar>(R.id.topAppBarMissfire).setNavigationOnClickListener { finish() }
    inputData = findViewById(R.id.inputDataOcorrencia)
        inputLocal = findViewById(R.id.inputLocal)
    inputResponsavel = findViewById(R.id.inputResponsavel)
    inputTipoRegistro = findViewById(R.id.inputTipoRegistro)
        inputItens = findViewById(R.id.inputItens)
        inputDescricao = findViewById(R.id.inputDescricao)

        configurarDropdownLocais()
    configurarDataPicker()
    configurarDropdownTipoRegistro()

    val btnSalvar = findViewById<MaterialButton>(R.id.btnSalvarLocalMissfire)
    val btnFotos = findViewById<MaterialButton>(R.id.btnAdicionarFotos)
    val whiteTint = ColorStateList.valueOf(ContextCompat.getColor(this, android.R.color.white))
    listOf(btnSalvar, btnFotos).forEach { it.iconTint = whiteTint }

    btnSalvar.setOnClickListener { salvarLocal() }
    btnFotos.setOnClickListener { abrirMenuFotos() }

    // Tela de registro: somente adicionar fotos e salvar localmente

    restaurarRascunho()
    renderFotos()

    // Container para updates será criado dinamicamente (após campo descrição) quando em modo edição
    intent.getIntExtra("misfire_id", -1).takeIf { it > -1 }?.let { carregarMissfireExistente(it) }

    observarAlteracoesCampos()
    }

    // ------------- RASCUNHO (draft) --------------
    private fun observarAlteracoesCampos() {
        val watcher = object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { salvarRascunho() }
        }
        inputData.addTextChangedListener(watcher)
    listOf(inputLocal, inputResponsavel, inputItens, inputDescricao).forEach { it.addTextChangedListener(watcher) }
    inputTipoRegistro.setOnItemClickListener { _, _, _, _ -> salvarRascunho() }
    }

    private fun salvarRascunho() {
        val prefs = getSharedPreferences("missfire_draft", MODE_PRIVATE)
        prefs.edit()
            .putLong("dataOcorrenciaMillis", dataOcorrenciaMillis ?: -1L)
            .putString("dataTexto", inputData.text?.toString())
            .putString("local", inputLocal.text?.toString())
            .putString("responsavel", inputResponsavel.text?.toString())
            .putString("tipoRegistro", inputTipoRegistro.text?.toString())
            .putString("itens", inputItens.text?.toString())
            .putString("descricao", inputDescricao.text?.toString())
            .putString("fotos", pendingFotos.joinToString("|") { it.path })
            .putString("arquivos", pendingArquivos.joinToString("|") { listOf(it.path, it.mime, it.nome, it.tamanho).joinToString(";;") })
            .apply()
    }

    private fun restaurarRascunho() {
        val prefs = getSharedPreferences("missfire_draft", MODE_PRIVATE)
        if (intent.getIntExtra("MISSFIRE_ID", -1) > -1) return // não restaurar se editando existente
        val savedMillis = prefs.getLong("dataOcorrenciaMillis", -1L)
        if (savedMillis > 0) {
            dataOcorrenciaMillis = savedMillis
            inputData.setText(prefs.getString("dataTexto", ""))
        }
        inputLocal.setText(prefs.getString("local", ""), false)
    inputResponsavel.setText(prefs.getString("responsavel", ""))
    inputTipoRegistro.setText(prefs.getString("tipoRegistro", ""), false)
        inputItens.setText(prefs.getString("itens", ""))
        inputDescricao.setText(prefs.getString("descricao", ""))
        // Fotos
        pendingFotos.clear()
        prefs.getString("fotos", null)?.takeIf { it.isNotBlank() }?.split("|")?.forEach { path ->
            if (path.isNotBlank() && java.io.File(path).exists()) pendingFotos.add(PendingFoto(path))
        }
        // Arquivos
        pendingArquivos.clear()
        prefs.getString("arquivos", null)?.takeIf { it.isNotBlank() }?.split("|")?.forEach { combo ->
            val parts = combo.split(";;")
            if (parts.size == 4) {
                val (p, mime, nome, tamStr) = parts
                val tam = tamStr.toLongOrNull() ?: 0L
                if (p.isNotBlank() && java.io.File(p).exists()) pendingArquivos.add(PendingArquivo(p, mime, nome, tam))
            }
        }
    }

    private fun limparRascunho() {
        getSharedPreferences("missfire_draft", MODE_PRIVATE).edit().clear().apply()
    }

    private fun configurarDropdownLocais() {
        val locais = listOf(
            "US Anglo American",
            "US Atlantic Nickel",
            "US CSN",
            "US CMOC - Nióbio",
            "US CMOC - Fosfato",
            "US MVV",
            "US Colomi",
            "US Maracá",
            "US Cajati",
            "US Taboca",
            "US Vanádio",
            "Usiminas",
            "US Ciplan",
            "US Almas",
            "US Belocal - Matozinhos",
            "US Belocal - Limeira",
            "US Caraíba - Pilar",
            "US Caraíba - Vermelhos",
            "US Oz Minerals",
            "US Jacobina",
            "US Anglo Gold Ashanti - Crixás",
            "US Aripuanã",
            "Ferbasa",
            "Vale Urucum",
            "US Carajás",
            "US S11D",
            "US Sossego",
            "Vale Onça Puma",
            "US Vale Sul - Itabira",
            "US Vale Sul - Mariana",
            "US Vale Sul - Brucutu",
            "US Vale Sul - CPX",
            "US Vale Sul - Vargem grande",
            "US Vale Sul - Água Limpa",
            "US Viga",
            "US Morro da Mina",
            "CD São Paulo",
            "CD Jardinópolis",
            "CD Minas Gerais",
            "CD Paraná",
            "CD Bahia",
            "CD Goiás",
            "CD Pernambuco",
            "CD Rio Grande do Sul",
            "PD Matriz",
            "N/A",
            "CD NOVA ROMA"
        )
        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, locais)
        inputLocal.setAdapter(adapter)
    inputLocal.keyListener = null
    inputLocal.setOnClickListener { inputLocal.showDropDown() }
    inputLocal.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) inputLocal.showDropDown() }
    }

    private fun configurarDataPicker() {
        inputData.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(this, { _, y, m, d ->
                cal.set(Calendar.YEAR, y)
                cal.set(Calendar.MONTH, m)
                cal.set(Calendar.DAY_OF_MONTH, d)
                dataOcorrenciaMillis = cal.timeInMillis
                inputData.setText(dateFormat.format(cal.time))
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
    }

    private fun configurarDropdownTipoRegistro() {
        val tipos = listOf(
            "Mina viva previamente detectada",
            "Mina viva detectada pós desmonte"
        )
        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, tipos)
        inputTipoRegistro.setAdapter(adapter)
        inputTipoRegistro.keyListener = null
        inputTipoRegistro.setOnClickListener { inputTipoRegistro.showDropDown() }
        inputTipoRegistro.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) inputTipoRegistro.showDropDown() }
    }

    private fun validarCampos(): Boolean {
        if (dataOcorrenciaMillis == null) { toast("Informe a data"); return false }
        if (inputLocal.text.isNullOrBlank()) { toast("Informe a unidade operacional"); return false }
        if (inputResponsavel.text.isNullOrBlank()) { toast("Informe o nome do usuário"); return false }
        if (inputTipoRegistro.text.isNullOrBlank()) { toast("Selecione o tipo de registro"); return false }
        return true
    }

    private fun salvarLocal() {
        if (!validarCampos()) return
        val existenteId = intent.getIntExtra("MISSFIRE_ID", -1)
    val baseEntity = MisfireEntity(
            dataOcorrencia = dataOcorrenciaMillis!!,
            local = inputLocal.text.toString().trim(),
            responsavel = inputResponsavel.text.toString().trim(),
        itensEncontrados = inputItens.text?.toString()?.trim().orEmpty(),
        descricaoOcorrencia = inputDescricao.text?.toString()?.trim().orEmpty(),
        infoAdicionais = construirInfoAdicionais()
        )
        uiScope.launch(Dispatchers.IO) {
            if (existenteId > -1) {
                // Atualização
                val antigo = repository.obterMissfire(existenteId)
                if (antigo != null) {
                    val atualizado = antigo.copy(
                        dataOcorrencia = baseEntity.dataOcorrencia,
                        local = baseEntity.local,
                        responsavel = baseEntity.responsavel,
                        itensEncontrados = baseEntity.itensEncontrados,
                        descricaoOcorrencia = baseEntity.descricaoOcorrencia,
                        infoAdicionais = baseEntity.infoAdicionais,
                        lastUpdated = System.currentTimeMillis()
                    )
                    repository.atualizarMissfire(atualizado)
                    val existentesAtts = repository.listarAttachments(existenteId)
                    val existentesPaths = existentesAtts.map { it.localPath }.toSet()
                    // Adicionar somente novos
                    pendingFotos.filter { !existentesPaths.contains(it.path) }.forEach { f ->
                        val file = java.io.File(f.path)
                        val tamanho = if (file.exists()) file.length() else 0L
                        repository.adicionarAttachment(
                            MissfireAttachmentEntity(
                                missfireId = existenteId,
                                tipo = "FOTO",
                                localPath = f.path,
                                mimeType = "image/jpeg",
                                tamanhoBytes = tamanho
                            )
                        )
                    }
                    pendingArquivos.filter { !existentesPaths.contains(it.path) }.forEach { a ->
                        repository.adicionarAttachment(
                            MissfireAttachmentEntity(
                                missfireId = existenteId,
                                tipo = "ARQUIVO",
                                localPath = a.path,
                                mimeType = a.mime,
                                tamanhoBytes = a.tamanho
                            )
                        )
                    }
                    launch(Dispatchers.Main) { toast("Atualizado (#$existenteId)"); finish() }
                } else {
                    launch(Dispatchers.Main) { toast("Erro: registro não encontrado") }
                }
            } else {
                val newId = repository.criarMissfire(baseEntity)
                pendingFotos.forEach { f ->
                    val file = java.io.File(f.path)
                    val tamanho = if (file.exists()) file.length() else 0L
                    repository.adicionarAttachment(
                        MissfireAttachmentEntity(
                            missfireId = newId,
                            tipo = "FOTO",
                            localPath = f.path,
                            mimeType = "image/jpeg",
                            tamanhoBytes = tamanho
                        )
                    )
                }
                pendingArquivos.forEach { a ->
                    repository.adicionarAttachment(
                        MissfireAttachmentEntity(
                            missfireId = newId,
                            tipo = "ARQUIVO",
                            localPath = a.path,
                            mimeType = a.mime,
                            tamanhoBytes = a.tamanho
                        )
                    )
                }
                launch(Dispatchers.Main) {
                    limparRascunho()
                    toast("Salvo localmente (#$newId)")
                    resetForm()
                    finish() // voltar para menu principal conforme solicitado
                }
            }
        }
    }

    private fun construirInfoAdicionais(): String {
        val tipo = inputTipoRegistro.text?.toString()?.trim().orEmpty()
        return if (tipo.isBlank()) "" else "Tipo de registro: $tipo"
    }

    private fun carregarMissfireExistente(id: Int) {
        uiScope.launch(Dispatchers.IO) {
            val existente = repository.obterMissfire(id)
            if (existente != null) {
                val atts = repository.listarAttachments(id)
                val updates = repository.listarUpdates(id)
                // Popular UI na Main thread
                launch(Dispatchers.Main) {
                    dataOcorrenciaMillis = existente.dataOcorrencia
                    inputData.setText(dateFormat.format(java.util.Date(existente.dataOcorrencia)))
                    inputLocal.setText(existente.local, false)
                    inputResponsavel.setText(existente.responsavel)
                    inputItens.setText(existente.itensEncontrados)
                    inputDescricao.setText(existente.descricaoOcorrencia)
                    // tentar extrair tipo do campo infoAdicionais se existir
                    existente.infoAdicionais?.let { info ->
                        val prefix = "Tipo de registro: "
                        if (info.startsWith(prefix)) {
                            inputTipoRegistro.setText(info.removePrefix(prefix), false)
                        }
                    }
                    // Preenche metadados de visualização
                    findViewById<View>(R.id.sectionMetadados)?.visibility = View.VISIBLE
                    val metaTv = findViewById<TextView>(R.id.tvMetaData)
                    metaTv?.text = buildString {
                        appendLine("DATA:")
                        appendLine(dateFormat.format(java.util.Date(existente.dataOcorrencia)))
                        appendLine()
                        appendLine("RESPONSÁVEL PELO REGISTRO:")
                        appendLine(existente.responsavel.ifBlank { "-" })
                        appendLine()
                        appendLine("DESCRIÇÃO INICIAL:")
                        appendLine(existente.descricaoOcorrencia.ifBlank { "-" })
                    }
                    pendingFotos.clear()
                    pendingArquivos.clear()
                    atts.forEach { a ->
                        if (a.tipo == "FOTO") pendingFotos.add(PendingFoto(a.localPath)) else pendingArquivos.add(
                            PendingArquivo(a.localPath, a.mimeType, java.io.File(a.localPath).name, a.tamanhoBytes)
                        )
                    }
                    renderFotos()
                    renderUpdates(updates)
                    if (existente.statusInvestigacao == "CONCLUIDA") bloquearEdicaoConcluida()
                    atualizarVisibilidadeBotoes()
                }
            } else {
                launch(Dispatchers.Main) { toast("Registro não encontrado") }
            }
        }
    }

    private fun atualizarVisibilidadeBotoes() { /* registro simples: nada dinâmico agora */ }

    private fun bloquearEdicaoConcluida() { /* não usado na tela de registro */ }

    private fun resetForm() {
        dataOcorrenciaMillis = null
        inputData.setText("")
        inputLocal.setText("", false)
        inputResponsavel.setText("")
        inputItens.setText("")
        inputDescricao.setText("")
        pendingFotos.clear()
        pendingArquivos.clear()
        renderFotos()
        intent.removeExtra("MISSFIRE_ID")
    }

    // ---------------- UPDATES ----------------
    private fun ensureUpdatesSection(): LinearLayout {
        val scrollRoot = findViewById<android.widget.ScrollView>(R.id.scrollConteudo)
        val mainColumn = scrollRoot.getChildAt(0) as? LinearLayout ?: return LinearLayout(this)
        val existing = mainColumn.findViewWithTag<LinearLayout>("updates_section")
        if (existing != null) return existing
        val section = LinearLayout(this).apply {
            tag = "updates_section"
            orientation = LinearLayout.VERTICAL
            setPadding(0,24,0,0)
        }
        val title = TextView(this).apply {
            text = "Atualizações da investigação"
            setTextColor(ContextCompat.getColor(this@MisfireActivity, R.color.white))
            textSize = 16f
        }
        val listContainerId = View.generateViewId()
        val listContainer = LinearLayout(this).apply {
            id = listContainerId
            orientation = LinearLayout.VERTICAL
            setPadding(0,12,0,0)
        }
        val btnAdd = MaterialButton(this).apply {
            text = "Adicionar atualização"
            setBackgroundColor(ContextCompat.getColor(this@MisfireActivity, R.color.redAccent))
            setTextColor(ContextCompat.getColor(this@MisfireActivity, R.color.white))
            cornerRadius = 20
            setOnClickListener { abrirDialogAdicionarUpdate() }
        }
        section.addView(title)
        section.addView(listContainer)
        section.addView(btnAdd)
        mainColumn.addView(section, mainColumn.childCount - 1) // antes do espaço grande final
        return section
    }

    private fun renderUpdates(updates: List<MissfireUpdateEntity>) {
        val section = ensureUpdatesSection()
    // Find first child after title (list container)
    val listContainer = section.getChildAt(1) as LinearLayout
        listContainer.removeAllViews()
        if (updates.isEmpty()) {
            val vazio = TextView(this).apply {
                text = "Nenhuma atualização registrada ainda."
                setTextColor(ContextCompat.getColor(this@MisfireActivity, R.color.gray))
            }
            listContainer.addView(vazio)
            return
        }
        updates.forEach { up ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0,0,0,16)
            }
            val txt = TextView(this).apply {
                text = "• ${formatarDataHora(up.createdAt)}\n${up.texto}"
                setTextColor(ContextCompat.getColor(this@MisfireActivity, R.color.white))
            }
            row.addView(txt)
            listContainer.addView(row)
        }
    }

    private fun abrirDialogAdicionarUpdate() {
        val view = layoutInflater.inflate(R.layout.dialog_update_misfire, null)
        val input = view.findViewById<TextInputEditText>(R.id.inputTextoUpdate)
        val btnSalvar = view.findViewById<MaterialButton>(R.id.btnSalvarUpdate)
        val btnCancelar = view.findViewById<MaterialButton>(R.id.btnCancelarUpdate)

    val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(view)
            .create()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        btnCancelar.setOnClickListener { dialog.dismiss() }
        btnSalvar.setOnClickListener {
            val texto = input.text?.toString()?.trim().orEmpty()
            if (texto.isBlank()) { toast("Texto vazio"); return@setOnClickListener }
            val missfireId = intent.getIntExtra("MISSFIRE_ID", -1)
            if (missfireId <= -1) { toast("Registro ainda não salvo"); return@setOnClickListener }
            uiScope.launch(Dispatchers.IO) {
                repository.adicionarUpdate(
                    MissfireUpdateEntity(
                        missfireId = missfireId,
                        texto = texto
                    )
                )
                val updates = repository.listarUpdates(missfireId)
                launch(Dispatchers.Main) { renderUpdates(updates); toast("Atualização adicionada") }
            }
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun formatarDataHora(millis: Long): String {
        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        return sdf.format(java.util.Date(millis))
    }

    // ---------------- FOTOS (fase inicial) ----------------
    private fun abrirMenuFotos() {
        if (pendingFotos.size >= MAX_FOTOS) { toast("Limite $MAX_FOTOS atingido"); return }
        val opcoes = arrayOf("Câmera", "Galeria")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Adicionar foto")
            .setItems(opcoes) { d, which ->
                when(which) {
                    0 -> capturarFoto()
                    1 -> selecionarDaGaleria()
                }
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun capturarFoto() {
        if (!checarPermissaoCamera()) { solicitarPermissoes() ; return }
        val dir = java.io.File(filesDir, "missfire_photos")
        if (!dir.exists()) dir.mkdirs()
        val file = java.io.File(dir, "mf_${System.currentTimeMillis()}.jpg")
        currentPhotoPath = file.absolutePath
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        takePictureLauncher.launch(uri)
    }

    private fun selecionarDaGaleria() {
        pickImageLauncher.launch("image/*")
    }

    private fun copiarDaGallery(uri: Uri) {
        try {
            val dir = java.io.File(filesDir, "missfire_photos")
            if (!dir.exists()) dir.mkdirs()
            val dest = java.io.File(dir, "mf_${System.currentTimeMillis()}.jpg")
            contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { out -> input.copyTo(out) }
            }
            adicionarFotoPath(dest.absolutePath)
        } catch (e: Exception) {
            toast("Falha ao copiar imagem")
        }
    }

    private fun adicionarFotoPath(path: String) {
        if (pendingFotos.size >= MAX_FOTOS) { toast("Limite $MAX_FOTOS"); return }
    pendingFotos.add(PendingFoto(path))
    renderFotos()
    salvarRascunho()
    }

    private fun renderFotos() {
        val container = findViewById<LinearLayout>(R.id.containerPreviewAnexos) ?: return
        val titulo = findViewById<TextView>(R.id.tvPreviewTitulo)
        titulo?.text = "Fotos (${pendingFotos.size}/$MAX_FOTOS) • Arquivos (${pendingArquivos.size})"
        container.removeAllViews()

        // Thumbnails de fotos
        if (pendingFotos.isNotEmpty()) {
            val thumbsRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            pendingFotos.forEachIndexed { index, foto ->
                val frame = FrameLayout(this).apply {
                    val lp = LinearLayout.LayoutParams(120,120)
                    lp.setMargins(0,0,16,0)
                    layoutParams = lp
                }
                val img = ImageView(this).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setBackgroundColor(0xFF333333.toInt())
                    val bmp = decodeScaled(foto.path, 120, 120)
                    if (bmp != null) setImageBitmap(bmp)
                    contentDescription = java.io.File(foto.path).name
                }
                val btnRemove = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    text = "X"
                    textSize = 10f
                    insetTop = 0; insetBottom = 0
                    layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                        gravity = android.view.Gravity.END or android.view.Gravity.TOP
                    }
                    setOnClickListener { pendingFotos.removeAt(index); renderFotos(); salvarRascunho() }
                }
                frame.addView(img)
                frame.addView(btnRemove)
                thumbsRow.addView(frame)
            }
            container.addView(thumbsRow)
        }

        // Lista de arquivos em chips simples
        if (pendingArquivos.isNotEmpty()) {
            val header = TextView(this).apply {
                text = "Arquivos (${pendingArquivos.size})"
                setTextColor(ContextCompat.getColor(this@MisfireActivity, R.color.white))
                setPadding(0,16,0,8)
            }
            container.addView(header)
            val filesCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            pendingArquivos.forEachIndexed { index, arq ->
                val chipRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0,4,0,4)
                }
                val tv = TextView(this).apply {
                    text = "${arq.nome} (${formatSize(arq.tamanho)})"
                    setTextColor(ContextCompat.getColor(this@MisfireActivity, R.color.white))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                val btnDel = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    text = "Remover"
                    textSize = 10f
                    insetTop = 0; insetBottom = 0
                    setOnClickListener { pendingArquivos.removeAt(index); renderFotos(); salvarRascunho() }
                }
                chipRow.addView(tv)
                chipRow.addView(btnDel)
                filesCol.addView(chipRow)
            }
            container.addView(filesCol)
        }
    }

    private fun decodeScaled(path: String, reqW: Int, reqH: Int): android.graphics.Bitmap? {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, opts)
            var inSample = 1
            while (opts.outWidth / inSample > reqW * 2 || opts.outHeight / inSample > reqH * 2) {
                inSample *= 2
            }
            val finalOpts = BitmapFactory.Options().apply { inSampleSize = inSample }
            BitmapFactory.decodeFile(path, finalOpts)
        } catch (e: Exception) { null }
    }

    private fun checarPermissaoCamera(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) capturarFoto() else toast("Permissão negada")
    }
    private fun solicitarPermissoes() { permissionLauncher.launch(Manifest.permission.CAMERA) }
    // -------------------------------------------------------

    // ---------------- ARQUIVOS -----------------------------
    // Seleção de arquivos removida desta tela (fica apenas em detalhe futuramente)

    private fun formatSize(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return if (mb >= 1) String.format(Locale.getDefault(),"%.2f MB", mb) else String.format(Locale.getDefault(),"%.1f KB", kb)
    }
    // -------------------------------------------------------

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // --------------- CONCLUIR INVESTIGAÇÃO ----------------
    private fun abrirDialogConcluir(id: Int) {
        uiScope.launch(Dispatchers.IO) {
            val existente = repository.obterMissfire(id)
            if (existente?.statusInvestigacao == "CONCLUIDA") {
                launch(Dispatchers.Main) { toast("Já concluída") }
                return@launch
            }
            launch(Dispatchers.Main) {
                val layout = LinearLayout(this@MisfireActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(32,24,32,8)
                }
                val inputCausa = TextInputEditText(this@MisfireActivity).apply { hint = "Causa"; setTextColor(ContextCompat.getColor(this@MisfireActivity, R.color.white)) }
                val inputMedidas = TextInputEditText(this@MisfireActivity).apply { hint = "Medidas Preventivas"; setTextColor(ContextCompat.getColor(this@MisfireActivity, R.color.white)) }
                layout.addView(inputCausa)
                layout.addView(inputMedidas)
                androidx.appcompat.app.AlertDialog.Builder(this@MisfireActivity)
                    .setTitle("Concluir investigação")
                    .setView(layout)
                    .setPositiveButton("Concluir") { _, _ ->
                        val causa = inputCausa.text?.toString()?.trim()
                        val medidas = inputMedidas.text?.toString()?.trim()
                        if (causa.isNullOrBlank() || medidas.isNullOrBlank()) { toast("Preencha causa e medidas"); return@setPositiveButton }
                        concluirMissfire(id, causa, medidas)
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        }
    }

    private fun concluirMissfire(id: Int, causa: String, medidas: String) {
        uiScope.launch(Dispatchers.IO) {
            val existente = repository.obterMissfire(id) ?: return@launch
            val atualizado = existente.copy(
                statusInvestigacao = "CONCLUIDA",
                causa = causa,
                medidasPreventivas = medidas,
                lastUpdated = System.currentTimeMillis()
            )
            repository.atualizarMissfire(atualizado)
            val updates = repository.listarUpdates(id)
            val atts = repository.listarAttachments(id)
            launch(Dispatchers.Main) {
                toast("Investigação concluída")
                // Recarregar UI básica
                pendingFotos.clear(); pendingArquivos.clear()
                atts.forEach { a -> if (a.tipo == "FOTO") pendingFotos.add(PendingFoto(a.localPath)) else pendingArquivos.add(PendingArquivo(a.localPath, a.mimeType, java.io.File(a.localPath).name, a.tamanhoBytes)) }
                renderFotos(); renderUpdates(updates)
            }
        }
    }
}

