package com.example.missfirebackupapp

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.*
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.example.missfirebackupapp.data.*
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputEditText
import com.example.missfirebackupapp.util.CoordinateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import org.json.JSONArray
import org.json.JSONObject

class BackupActivity : AppCompatActivity() {

    private lateinit var repository: BackupRepository
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var currentPhotoPath: String? = null
    private var currentLatitude: Double? = null
    private var currentLongitude: Double? = null
    private var currentAltitude: Double? = null
    private val pendingPhotos = mutableListOf<PendingPhoto>()
    private var selectedPhotoIndex: Int? = null // indice da foto cujas coordenadas estão aplicadas
    private var lastBackupId: Long? = null

    // Controle de estado de captura de foto (para sobreviver recriação da Activity / processo)
    private val CAMERA_STATE_PREF = "camera_state"

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    private data class PendingPhoto(
        val path: String,
        val latitude: Double?,
        val longitude: Double?,
        val altitude: Double?,
        val sistema: String?,
        val timestamp: String
    )

    // Launcher para permissões (câmera + localização) - usado se não já concedidas
    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* No-op: checaremos depois */ }

    // Launcher para captura de foto
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        try {
            // Recupera path salvo se a Activity foi recriada e o campo ficou null
            if (currentPhotoPath == null) {
                val tmp = getSharedPreferences("camera_tmp", MODE_PRIVATE).getString("lastPhotoPath", null)
                if (tmp != null) {
                    currentPhotoPath = tmp
                    Log.d("BackupActivity", "Restaurado currentPhotoPath de SharedPreferences: $tmp")
                }
            }
            if (success && currentPhotoPath != null) {
                if (pendingPhotos.size >= 3) {
                    Toast.makeText(this, "Limite de 3 fotos atingido", Toast.LENGTH_SHORT).show()
                } else {
                    val ts = dateFormat.format(Date())
                    val sistema = getSharedPreferences("prefs", MODE_PRIVATE).getString("coordSystem", "WGS84")
                    pendingPhotos.add(
                        PendingPhoto(
                            path = currentPhotoPath!!,
                            latitude = currentLatitude,
                            longitude = currentLongitude,
                            altitude = currentAltitude,
                            sistema = sistema,
                            timestamp = ts
                        )
                    )
                    Toast.makeText(this, "Foto ${pendingPhotos.size}/3 capturada", Toast.LENGTH_SHORT).show()
                    renderPendingPhotos()
                    persistPhotosDraft() // salva imediatamente
                }
            } else if (!success) {
                Toast.makeText(this, "Captura cancelada", Toast.LENGTH_SHORT).show()
            } else if (success && currentPhotoPath == null) {
                Toast.makeText(this, "Falha ao recuperar caminho da foto", Toast.LENGTH_LONG).show()
                Log.e("BackupActivity", "Success true mas currentPhotoPath null")
            }
        } catch (ex: Exception) {
            Log.e("BackupActivity", "Erro no callback de foto", ex)
            Toast.makeText(this, "Erro ao processar foto", Toast.LENGTH_LONG).show()
        } finally {
            // Limpa para evitar reuso indevido
            getSharedPreferences("camera_tmp", MODE_PRIVATE).edit().remove("lastPhotoPath").apply()
            // Limpa flag de captura em andamento
            getSharedPreferences(CAMERA_STATE_PREF, MODE_PRIVATE).edit().clear().apply()
            currentPhotoPath = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_backup)
    Log.d("BackupActivity", "onCreate - iniciado. savedInstanceState? ${savedInstanceState != null}")

    // ✅ Banco e Repositório
        val dao = BackupDatabase.getDatabase(this).backupDao()
        repository = BackupRepository(dao)

        // ✅ Inicializa localização
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // ✅ Botão voltar
        val topAppBarBackup = findViewById<MaterialToolbar>(R.id.topAppBarBackup)
        topAppBarBackup.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // ✅ Referências aos campos
        val inputData = findViewById<TextInputEditText>(R.id.inputData)
        val inputUnidade = findViewById<AutoCompleteTextView>(R.id.inputUnidade)
        val inputCava = findViewById<TextInputEditText>(R.id.inputCava)
        val inputBanco = findViewById<TextInputEditText>(R.id.inputBanco)
        val inputFogo = findViewById<TextInputEditText>(R.id.inputFogo)
        val inputFuro = findViewById<TextInputEditText>(R.id.inputFuro)
        val inputDetonador = findViewById<TextInputEditText>(R.id.inputDetonador)
        val inputEspoleta = findViewById<TextInputEditText>(R.id.inputEspoleta)
        val inputMotivo = findViewById<AutoCompleteTextView>(R.id.inputMotivo)
        val inputTipoDetonador = findViewById<AutoCompleteTextView>(R.id.inputTipoDetonador)
        val inputCaboDetonador = findViewById<AutoCompleteTextView>(R.id.inputCaboDetonador)
        val inputMetragem = findViewById<AutoCompleteTextView>(R.id.inputMetragem)
        val inputRecuperacao = findViewById<AutoCompleteTextView>(R.id.inputRecuperacao)
        val inputX = findViewById<TextInputEditText>(R.id.inputCoordenadaX)
        val inputY = findViewById<TextInputEditText>(R.id.inputCoordenadaY)
    val inputZ = findViewById<TextInputEditText>(R.id.inputCoordenadaZ)
    val containerFotos = findViewById<LinearLayout>(R.id.containerFotos)
    val tvFotosTitulo = findViewById<TextView>(R.id.tvFotosTitulo)
    val tvStatusCoordenadas = findViewById<TextView>(R.id.tvStatusCoordenadas)
    val cbSemFotos = findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.cbSemFotosRetiradas)
    val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
    val sistemaPreferido = { prefs.getString("coordSystem", "WGS84") ?: "WGS84" }

        fun setManualCoordinatesEnabled(enabled: Boolean) {
            inputX.isEnabled = enabled
            inputY.isEnabled = enabled
            inputZ.isEnabled = enabled
        }

        fun applySemFotosState(isChecked: Boolean) {
            if (isChecked) {
                setManualCoordinatesEnabled(true)
                tvStatusCoordenadas?.text = "Coordenadas: informar manualmente (sem fotos)"
            } else {
                // Se não houver coordenadas atuais, volta a bloquear edição manual até captura
                if (currentLatitude == null && currentLongitude == null) {
                    setManualCoordinatesEnabled(false)
                    tvStatusCoordenadas?.text = "Coordenadas: Aguardando captura da foto"
                }
            }
        }

        // Inicialmente usuário não deve editar manualmente até tentar capturar a foto
        setManualCoordinatesEnabled(false)
    tvStatusCoordenadas?.text = "Coordenadas: Aguardando captura da foto"

        // ✅ Configuração das listas suspensas
        val motivos = listOf(
            "Fuga excessiva/Cabo cortado (Tamponamento)",
            "Fuga excessiva/Cabo cortado (Bombeamento)",
            "Fuga excessiva/Cabo cortado (Movimentação de Equip.)",
            "Fuga excessiva/Cabo cortado (Queda de material externo)",
            "Fuga excessiva/Cabo cortado (Queda de material interno)",
            "Fuga excessiva/Cabo cortado (Vazamento de carga)",
            "Fuga excessiva/Cabo cortado (Abatimento de tampão)",
            "Descarga atmosférica",
            "Queda de escorva no furo",
            "Detonador com defeito (Descreva nas Obs.)",
            "Outro (Descreva nas Obs.)"
        )

        val unidades = listOf(
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

        val tiposDetonador = listOf("SP", "UG", "OP", "XD", "SW", "SP - DT5", "WP")
        val cabos = listOf(
            "Standard - STD",
            "Reforçado - HD",
            "Reforçado - HD2",
            "Super reforçado - XO",
            "Super reforçado - M95",
            "Super reforçado - M105",
            "UG2 - SW",
            "UG3 - SW",
            "WP - DT5"
        )
        val metragens = listOf("6", "8", "10", "15", "20", "30", "40", "60", "0")
        val recuperacao = listOf("Sim", "Não")

        fun setAdapter(view: AutoCompleteTextView, list: List<String>) {
            val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, list)
            view.setAdapter(adapter)
        }

        setAdapter(inputMotivo, motivos)
        setAdapter(inputUnidade, unidades)
        setAdapter(inputTipoDetonador, tiposDetonador)
        setAdapter(inputCaboDetonador, cabos)
        setAdapter(inputMetragem, metragens)
        setAdapter(inputRecuperacao, recuperacao)

    // ✅ DatePicker para campo de data
        inputData.setOnClickListener {
            val calendar = Calendar.getInstance()
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH)
            val day = calendar.get(Calendar.DAY_OF_MONTH)

            val datePicker = DatePickerDialog(this, { _, y, m, d ->
                inputData.setText(String.format("%02d/%02d/%d", d, m + 1, y))
            }, year, month, day)

            datePicker.show()
        }

        // ✅ Rascunho persistente (campos + fotos + seleção)
        val draft = getSharedPreferences("draft", MODE_PRIVATE)

    fun saveDraft() {
            draft.edit()
                .putString("data", inputData.text.toString())
                .putString("unidade", inputUnidade.text.toString())
                .putString("cava", inputCava.text.toString())
                .putString("banco", inputBanco.text.toString())
                .putString("fogo", inputFogo.text.toString())
                .putString("furo", inputFuro.text.toString())
                .putString("detonador", inputDetonador.text.toString())
                .putString("espoleta", inputEspoleta.text.toString())
                .putString("motivo", inputMotivo.text.toString())
                .putString("tipoDetonador", inputTipoDetonador.text.toString())
                .putString("caboDetonador", inputCaboDetonador.text.toString())
                .putString("metragem", inputMetragem.text.toString())
                .putString("recuperacao", inputRecuperacao.text.toString())
                .putString("x", inputX.text.toString())
                .putString("y", inputY.text.toString())
                .putString("z", inputZ.text.toString())
        .putBoolean("sem_fotos", cbSemFotos?.isChecked == true)
                .apply()
            persistPhotosDraft()
        }

    fun restoreDraft() {
            inputData.setText(draft.getString("data", ""))
            inputUnidade.setText(draft.getString("unidade", ""), false)
            inputCava.setText(draft.getString("cava", ""))
            inputBanco.setText(draft.getString("banco", ""))
            inputFogo.setText(draft.getString("fogo", ""))
            inputFuro.setText(draft.getString("furo", ""))
            inputDetonador.setText(draft.getString("detonador", ""))
            inputEspoleta.setText(draft.getString("espoleta", ""))
            inputMotivo.setText(draft.getString("motivo", ""), false)
            inputTipoDetonador.setText(draft.getString("tipoDetonador", ""), false)
            inputCaboDetonador.setText(draft.getString("caboDetonador", ""), false)
            inputMetragem.setText(draft.getString("metragem", ""), false)
            inputRecuperacao.setText(draft.getString("recuperacao", ""), false)
            inputX.setText(draft.getString("x", ""))
            inputY.setText(draft.getString("y", ""))
            inputZ.setText(draft.getString("z", ""))
            cbSemFotos?.isChecked = draft.getBoolean("sem_fotos", false)
            runCatching {
                val json = draft.getString("photos_json", null)
                if (!json.isNullOrBlank()) {
                    val arr = JSONArray(json)
                    pendingPhotos.clear()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val path = o.optString("path")
                        if (path.isNotBlank() && File(path).exists()) {
                            pendingPhotos.add(
                                PendingPhoto(
                                    path = path,
                                    latitude = if (o.isNull("lat")) null else o.optDouble("lat"),
                                    longitude = if (o.isNull("lon")) null else o.optDouble("lon"),
                                    altitude = if (o.isNull("alt")) null else o.optDouble("alt"),
                                    sistema = o.optString("sistema", null),
                                    timestamp = o.optString("ts", "")
                                )
                            )
                        }
                    }
                    selectedPhotoIndex = draft.getInt("selectedPhotoIndex", -1).let { if (it >= 0 && it < pendingPhotos.size) it else null }
                    renderPendingPhotos()
                    // Reaplica automaticamente as coordenadas da foto selecionada (caso sistema != WGS84 elas se perdiam visualmente)
                    selectedPhotoIndex?.let { idx ->
                        // Evita duplicar persistência desnecessária: aplicar já salva novamente
                        aplicarCoordenadasFoto(idx)
                    }
                }
            }.onFailure { Log.w("BackupActivity", "Falha ao restaurar fotos do rascunho: ${it.message}") }
        }
    restoreDraft()
    // Listener para liberar coordenadas manuais quando não há fotos
    cbSemFotos?.setOnCheckedChangeListener { _, checked -> applySemFotosState(checked) }
    applySemFotosState(cbSemFotos?.isChecked == true)

        // Restaura estado de captura em andamento (caso Activity tenha sido recriada durante a câmera)
        getSharedPreferences(CAMERA_STATE_PREF, MODE_PRIVATE).let { camState ->
            if (camState.getBoolean("in_capture", false)) {
                val possiblePath = getSharedPreferences("camera_tmp", MODE_PRIVATE).getString("lastPhotoPath", null)
                currentPhotoPath = possiblePath
                Log.d("BackupActivity", "Restauração: havia captura em andamento. path=$possiblePath")
                findViewById<TextView>(R.id.tvStatusCoordenadas)?.text = "Coordenadas: retomando captura... (${sistemaPreferido()})"
            }
        }

        listOf(
            inputData,inputUnidade,inputCava,inputBanco,inputFogo,inputFuro,inputDetonador,inputEspoleta,
            inputMotivo,inputTipoDetonador,inputCaboDetonador,inputMetragem,inputRecuperacao,inputX,inputY,inputZ
        ).forEach { v ->
            v.setOnFocusChangeListener { _, _ -> saveDraft() }
            v.addTextChangedListener(object: android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) { saveDraft() }
            })
        }

        // Garantir permissões no início desta Activity (já deveriam ter sido solicitadas na app, reforço)
        ensurePermissions()

        // ✅ Botão Foto
        val btnFoto = findViewById<Button>(R.id.btnFoto)
        btnFoto.setOnClickListener {
            if (pendingPhotos.size >= 3) {
                Toast.makeText(this, "Máximo de 3 fotos", Toast.LENGTH_SHORT).show(); return@setOnClickListener
            }
            if (checkPermissions()) {
                getCurrentLocation()
                // Marca estado de captura antes de abrir a câmera
                getSharedPreferences(CAMERA_STATE_PREF, MODE_PRIVATE).edit()
                    .putBoolean("in_capture", true)
                    .putLong("ts", System.currentTimeMillis())
                    .putString("coordSystem", sistemaPreferido())
                    .apply()
                Log.d("BackupActivity", "Iniciando captura - sistema=${sistemaPreferido()}")
                launchCamera()
                tvStatusCoordenadas?.text = "Coordenadas: capturando... (${sistemaPreferido()})"
            } else {
                ensurePermissions()
            }
        }

        // ✅ Botão Salvar Localmente
        val btnSalvar = findViewById<Button>(R.id.btnSalvarLocal)
        btnSalvar.setOnClickListener {
            val erros = mutableListOf<String>()
            if (inputData.text.isNullOrBlank()) erros.add("Data")
            if (inputUnidade.text.isNullOrBlank()) erros.add("Unidade")
            if (inputCava.text.isNullOrBlank()) erros.add("Cava")
            if (inputFuro.text.isNullOrBlank()) erros.add("Furo")
            val metragemVal = inputMetragem.text?.toString()?.toDoubleOrNull()
            if (metragemVal == null) erros.add("Metragem numérica")
            // Exigir 1 foto mínima, a menos que o usuário marque "Sem fotos retiradas".
            val semFotosMarcado = cbSemFotos?.isChecked == true
            if (!semFotosMarcado && pendingPhotos.isEmpty()) erros.add("1 foto mínima")

            val (x,y,z, sistema) = buildCoordinatesForStorage(inputX,inputY,inputZ)
            // Coordenadas são sempre desejáveis; tornam-se obrigatórias quando não há foto (semFotosMarcado)
            if (semFotosMarcado && x == 0.0 && y == 0.0) erros.add("Coordenadas")
            // Sempre salvar como INCOMPLETO (rascunho) até o usuário pressionar "Finalizar" na tela de detalhes.
            // Mantemos a lista de erros apenas para feedback imediato.
            val statusCalc = "INCOMPLETO"
        // Bloqueia salvamento se faltar os campos mínimos: Data, Unidade, Cava e (1 foto OU sem fotos marcada com coordenadas).
            val camposMinimosOk =
                !inputData.text.isNullOrBlank() &&
                !inputUnidade.text.isNullOrBlank() &&
                !inputCava.text.isNullOrBlank() &&
                (
            (pendingPhotos.isNotEmpty()) ||
            (semFotosMarcado && !(x == 0.0 && y == 0.0))
                )

            if (!camposMinimosOk) {
                val faltando = mutableListOf<String>()
                if (inputData.text.isNullOrBlank()) faltando.add("Data")
                if (inputUnidade.text.isNullOrBlank()) faltando.add("Unidade")
                if (inputCava.text.isNullOrBlank()) faltando.add("Cava")
                if (!semFotosMarcado && pendingPhotos.isEmpty()) faltando.add("1 foto mínima")
                if (semFotosMarcado && x == 0.0 && y == 0.0) faltando.add("Coordenadas obrigatórias sem foto")
                Toast.makeText(this, "Não foi possível salvar. Faltando: ${faltando.joinToString()}", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // Se passou nos mínimos, informamos o restante como incompleto no status, mas prosseguimos com o salvamento do rascunho.
            if (erros.isNotEmpty()) {
                Toast.makeText(this, "Rascunho salvo INCOMPLETO (faltando: ${erros.joinToString()})", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Rascunho salvo. Use 'Finalizar' depois para sincronizar.", Toast.LENGTH_LONG).show()
            }

            val backup = BackupEntity(
                data = inputData.text.toString(),
                unidade = inputUnidade.text.toString(),
                cava = inputCava.text.toString(),
                banco = inputBanco.text.toString(),
                fogoId = inputFogo.text.toString(),
                furoNumero = inputFuro.text.toString(),
                detonadorNumero = inputDetonador.text.toString(),
                espoletaId = inputEspoleta.text.toString(),
                motivo = inputMotivo.text.toString(),
                tipoDetonador = inputTipoDetonador.text.toString(),
                caboDetonador = inputCaboDetonador.text.toString(),
                metragem = inputMetragem.text.toString(),
                tentativaRecuperacao = inputRecuperacao.text.toString(),
                coordenadaX = x,
                coordenadaY = y,
                coordenadaZ = z,
                sistemaCoordenadas = sistema,
                status = statusCalc
            )

            lifecycleScope.launch(Dispatchers.IO) {
                val newId = repository.insertBackup(backup)
                lastBackupId = newId
                pendingPhotos.forEach { p ->
                    val foto = FotoEntity(
                        backupId = newId.toInt(),
                        caminhoFoto = p.path,
                        latitude = p.latitude,
                        longitude = p.longitude,
                        altitude = p.altitude,
                        sistemaCoordenadas = p.sistema,
                        dataHora = p.timestamp
                    )
                    repository.insertFoto(foto)
                }
                pendingPhotos.clear()
                runOnUiThread {
                    // Mensagem principal já exibida acima; aqui apenas reforço curto.
                    Toast.makeText(this@BackupActivity, "Backup salvo (#$newId) como INCOMPLETO", Toast.LENGTH_SHORT).show()
                    resetForm(
                        inputData, inputUnidade, inputCava, inputBanco, inputFogo, inputFuro,
                        inputDetonador, inputEspoleta, inputMotivo, inputTipoDetonador,
                        inputCaboDetonador, inputMetragem, inputRecuperacao, inputX, inputY, inputZ
                    )
                    draft.edit().clear().apply() // limpa tudo somente após salvar
                }
            }
        }
    }

    private fun ensurePermissions() {
        if (!checkPermissions()) {
            permissionsLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun checkPermissions(): Boolean = listOf(
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION
    ).all { perm -> ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED }

    private fun launchCamera() {
        val dir = File(filesDir, "backups_photos")
        if (!dir.exists()) dir.mkdirs()
        val photoFile = File(dir, "photo_${System.currentTimeMillis()}.jpg")
        currentPhotoPath = photoFile.absolutePath
        // Persistir para sobreviver a recriação da Activity
        getSharedPreferences("camera_tmp", MODE_PRIVATE).edit().putString("lastPhotoPath", currentPhotoPath).apply()
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", photoFile)
    Log.d("BackupActivity", "launchCamera - path=$currentPhotoPath uri=$uri")
        takePictureLauncher.launch(uri)
    }

    // Salva estado crítico (caso o sistema recrie a Activity durante a câmera)
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("currentPhotoPath", currentPhotoPath)
        currentLatitude?.let { outState.putDouble("lat", it) }
        currentLongitude?.let { outState.putDouble("lon", it) }
        currentAltitude?.let { outState.putDouble("alt", it) }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        if (currentPhotoPath == null) currentPhotoPath = savedInstanceState.getString("currentPhotoPath")
        if (savedInstanceState.containsKey("lat")) currentLatitude = savedInstanceState.getDouble("lat")
        if (savedInstanceState.containsKey("lon")) currentLongitude = savedInstanceState.getDouble("lon")
        if (savedInstanceState.containsKey("alt")) currentAltitude = savedInstanceState.getDouble("alt")
    }

    // ✅ Pegar localização
    private fun getCurrentLocation() {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    currentLatitude = location.latitude
                    currentLongitude = location.longitude
                    currentAltitude = location.altitude
                    updateCoordinateDisplay()
                } else {
                    findViewById<TextView>(R.id.tvStatusCoordenadas)?.text = "Coordenadas: não obtidas - preencha manualmente"
                    // Permitir edição manual
                    findViewById<TextInputEditText>(R.id.inputCoordenadaX)?.isEnabled = true
                    findViewById<TextInputEditText>(R.id.inputCoordenadaY)?.isEnabled = true
                    findViewById<TextInputEditText>(R.id.inputCoordenadaZ)?.isEnabled = true
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun updateCoordinateDisplay() {
        val tv = findViewById<TextView>(R.id.tvStatusCoordenadas) ?: return
        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        val system = prefs.getString("coordSystem", "WGS84") ?: "WGS84"
        val lat = currentLatitude
        val lon = currentLongitude
        if (lat != null && lon != null) {
            var xVal: Double
            var yVal: Double
            var labelSystem: String = system
            if (system.startsWith("WGS84")) {
                xVal = lat
                yVal = lon
                labelSystem = "WGS84"
            } else if (system.startsWith("SIRGAS2000") || system.startsWith("SAD69")) {
                val (adjLat, adjLon) = CoordinateUtils.adjustDatum(lat, lon, system)
                val (easting, northing, _) = CoordinateUtils.wgs84ToUTM(adjLat, adjLon)
                // Evita NumberFormatException em locales com vírgula
                xVal = kotlin.math.round(easting * 1000.0) / 1000.0
                yVal = kotlin.math.round(northing * 1000.0) / 1000.0
            } else {
                xVal = lat
                yVal = lon
            }
            tv.text = "Coordenadas: capturadas ($labelSystem)"
            findViewById<TextInputEditText>(R.id.inputCoordenadaX)?.apply { setText(String.format("%.3f", xVal)); isEnabled = false }
            findViewById<TextInputEditText>(R.id.inputCoordenadaY)?.apply { setText(String.format("%.3f", yVal)); isEnabled = false }
            findViewById<TextInputEditText>(R.id.inputCoordenadaZ)?.apply { setText(String.format("%.2f", currentAltitude ?: 0.0)); isEnabled = currentAltitude == null }
        }
    }

    private fun buildCoordinatesForStorage(xField: TextInputEditText, yField: TextInputEditText, zField: TextInputEditText): Quadruple {
        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        val system = prefs.getString("coordSystem", "WGS84") ?: "WGS84"
        val lat = currentLatitude
        val lon = currentLongitude
        return if (lat != null && lon != null) {
            if (system == "WGS84") {
                Quadruple(lat, lon, currentAltitude ?: 0.0, system)
            } else if (system.startsWith("SIRGAS2000") || system.startsWith("SAD69")) {
                val (adjLat, adjLon) = CoordinateUtils.adjustDatum(lat, lon, system)
                val (easting, northing, _) = CoordinateUtils.wgs84ToUTM(adjLat, adjLon)
                Quadruple(easting, northing, currentAltitude ?: 0.0, system)
            } else {
                Quadruple(lat, lon, currentAltitude ?: 0.0, system)
            }
        } else {
            Quadruple(
                xField.text.toString().toDoubleOrNull() ?: 0.0,
                yField.text.toString().toDoubleOrNull() ?: 0.0,
                zField.text.toString().toDoubleOrNull() ?: 0.0,
                system
            )
        }
    }

    private data class Quadruple(val x: Double, val y: Double, val z: Double, val sistema: String)

    private fun resetForm(vararg views: TextView) {
        views.forEach { it.text = "" }
        currentPhotoPath = null
        currentLatitude = null
        currentLongitude = null
    currentAltitude = null
        pendingPhotos.clear()
        renderPendingPhotos()
        findViewById<TextView>(R.id.tvStatusCoordenadas)?.text = "Coordenadas: Aguardando captura da foto"
        findViewById<TextInputEditText>(R.id.inputCoordenadaX)?.isEnabled = false
        findViewById<TextInputEditText>(R.id.inputCoordenadaY)?.isEnabled = false
        findViewById<TextInputEditText>(R.id.inputCoordenadaZ)?.isEnabled = false
    }

    private fun persistPhotosDraft() {
        val draft = getSharedPreferences("draft", MODE_PRIVATE)
        val arr = JSONArray()
        pendingPhotos.forEach { p ->
            val o = JSONObject()
            o.put("path", p.path)
            o.put("lat", p.latitude)
            o.put("lon", p.longitude)
            o.put("alt", p.altitude)
            o.put("sistema", p.sistema)
            o.put("ts", p.timestamp)
            arr.put(o)
        }
        draft.edit()
            .putString("photos_json", arr.toString())
            .putInt("selectedPhotoIndex", selectedPhotoIndex ?: -1)
            .apply()
    }

    private fun renderPendingPhotos() {
        val container = findViewById<LinearLayout>(R.id.containerFotos) ?: return
        val titulo = findViewById<TextView>(R.id.tvFotosTitulo)
        titulo?.text = "Fotos (${pendingPhotos.size}/3)"
        container.removeAllViews()
        val inflater = layoutInflater
        pendingPhotos.forEachIndexed { index, p ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0,8,0,8)
            }
            val radio = RadioButton(this).apply {
                isChecked = selectedPhotoIndex == index
                setOnClickListener { aplicarCoordenadasFoto(index) }
            }
            val info = TextView(this).apply {
                setTextColor(getColor(R.color.white))
                textSize = 12f
                text = "#${index+1} ${p.timestamp}\nLat:${p.latitude.formatOrDash()} Lon:${p.longitude.formatOrDash()} Alt:${p.altitude.formatOrDash(2)} (${p.sistema})"
                setOnClickListener { aplicarCoordenadasFoto(index) }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val btnDelete = ImageButton(this).apply {
                setImageResource(R.drawable.ic_delete)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setOnClickListener {
                    pendingPhotos.removeAt(index)
                    if (selectedPhotoIndex == index) {
                        selectedPhotoIndex = null
                    } else if (selectedPhotoIndex != null && selectedPhotoIndex!! > index) {
                        selectedPhotoIndex = selectedPhotoIndex!! - 1
                    }
                    renderPendingPhotos()
                    persistPhotosDraft()
                }
            }
            row.addView(radio)
            row.addView(info)
            row.addView(btnDelete)
            container.addView(row)
        }
        // Desabilitar botao foto se limite
        findViewById<Button>(R.id.btnFoto)?.isEnabled = pendingPhotos.size < 3
    }

    private fun aplicarCoordenadasFoto(index: Int) {
        selectedPhotoIndex = index
        val foto = pendingPhotos[index]
        // Aplica as coordenadas brutas (lat/lon ou utm) nos campos X/Y/Z de acordo com sistema preferido atual
        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        val system = prefs.getString("coordSystem", "WGS84") ?: "WGS84"
        val inputX = findViewById<TextInputEditText>(R.id.inputCoordenadaX)
        val inputY = findViewById<TextInputEditText>(R.id.inputCoordenadaY)
        val inputZ = findViewById<TextInputEditText>(R.id.inputCoordenadaZ)
        if (foto.latitude != null && foto.longitude != null) {
            if (system == "WGS84") {
                inputX?.setText(String.format(Locale.getDefault(),"%.5f", foto.latitude))
                inputY?.setText(String.format(Locale.getDefault(),"%.5f", foto.longitude))
                inputZ?.setText(String.format(Locale.getDefault(),"%.2f", foto.altitude ?: 0.0))
            } else if (system.startsWith("SIRGAS2000") || system.startsWith("SAD69")) {
                val (adjLat, adjLon) = CoordinateUtils.adjustDatum(foto.latitude, foto.longitude, system)
                val (easting, northing, _) = CoordinateUtils.wgs84ToUTM(adjLat, adjLon)
                inputX?.setText(String.format(Locale.getDefault(),"%.2f", easting))
                inputY?.setText(String.format(Locale.getDefault(),"%.2f", northing))
                inputZ?.setText(String.format(Locale.getDefault(),"%.2f", foto.altitude ?: 0.0))
            } else {
                inputX?.setText(String.format(Locale.getDefault(),"%.5f", foto.latitude))
                inputY?.setText(String.format(Locale.getDefault(),"%.5f", foto.longitude))
                inputZ?.setText(String.format(Locale.getDefault(),"%.2f", foto.altitude ?: 0.0))
            }
        }
        renderPendingPhotos() // para atualizar radios
        persistPhotosDraft()
    }
    private fun Double?.formatOrDash(decimals: Int = 5): String = if (this == null) "-" else String.format(Locale.getDefault(), "% .${decimals}f", this)
}
