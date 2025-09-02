package com.example.missfirebackupapp

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.*
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

class BackupActivity : AppCompatActivity() {

    private lateinit var repository: BackupRepository
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var currentPhotoPath: String? = null
    private var currentLatitude: Double? = null
    private var currentLongitude: Double? = null
    private var currentAltitude: Double? = null
    private val pendingPhotos = mutableListOf<PendingPhoto>()
    private var lastBackupId: Long? = null

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
            }
            currentPhotoPath = null
        } else {
            currentPhotoPath = null
            Toast.makeText(this, "Captura cancelada", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_backup)

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
    val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
    val sistemaPreferido = { prefs.getString("coordSystem", "WGS84") ?: "WGS84" }

        fun setManualCoordinatesEnabled(enabled: Boolean) {
            inputX.isEnabled = enabled
            inputY.isEnabled = enabled
            inputZ.isEnabled = enabled
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

        // ✅ Rascunho (draft) - restaurar valores salvos antes de permissões
        val draft = getSharedPreferences("draft", MODE_PRIVATE)
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
        }
        restoreDraft()

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
                .apply()
        }

        // Listener genérico simples (poderia otimizar com TextWatcher único)
        listOf(
            inputData,inputUnidade,inputCava,inputBanco,inputFogo,inputFuro,inputDetonador,inputEspoleta,
            inputMotivo,inputTipoDetonador,inputCaboDetonador,inputMetragem,inputRecuperacao,inputX,inputY,inputZ
        ).forEach { v -> v.setOnFocusChangeListener { _, _ -> saveDraft() } }

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
                launchCamera()
                tvStatusCoordenadas?.text = "Coordenadas: capturando... (${sistemaPreferido()})"
            } else {
                ensurePermissions()
            }
        }

        // ✅ Botão Salvar Localmente
        val btnSalvar = findViewById<Button>(R.id.btnSalvarLocal)
        btnSalvar.setOnClickListener {
            // Validação mínima
            if (inputData.text.isNullOrBlank() || inputUnidade.text.isNullOrBlank() || inputCava.text.isNullOrBlank()) {
                Toast.makeText(this, "Preencha Data, Unidade e Cava", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (pendingPhotos.isEmpty()) {
                Toast.makeText(this, "Capture pelo menos 1 foto", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val (x,y,z, sistema) = buildCoordinatesForStorage(inputX,inputY,inputZ)
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
                status = "INCOMPLETO"
            )

            lifecycleScope.launch(Dispatchers.IO) {
                val newId = repository.insertBackup(backup)
                lastBackupId = newId
                // Salvar fotos pendentes agora vinculadas
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
                    Toast.makeText(this@BackupActivity, "Backup criado (#$newId)", Toast.LENGTH_SHORT).show()
                    resetForm(
                        inputData, inputUnidade, inputCava, inputBanco, inputFogo, inputFuro,
                        inputDetonador, inputEspoleta, inputMotivo, inputTipoDetonador,
                        inputCaboDetonador, inputMetragem, inputRecuperacao, inputX, inputY, inputZ
                    )
                    // Limpa rascunho após salvar
                    draft.edit().clear().apply()
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
        val photoFile = File.createTempFile("photo_${System.currentTimeMillis()}", ".jpg", cacheDir)
        currentPhotoPath = photoFile.absolutePath
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", photoFile)
        takePictureLauncher.launch(uri)
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
            val (x,y,zoneOrSys) = if (system.startsWith("WGS84")) {
                Triple(lat, lon, "WGS84")
            } else if (system.startsWith("SIRGAS2000") || system.startsWith("SAD69")) {
                // Treat as UTM zone based on system suffix (e.g., SIRGAS2000-23S)
                val (_, zonePart) = system.split('-', limit = 2).let { if (it.size==2) it[0] to it[1] else system to "23S" }
                val (adjLat, adjLon) = CoordinateUtils.adjustDatum(lat, lon, system)
                val (easting, northing, zone) = CoordinateUtils.wgs84ToUTM(adjLat, adjLon)
                Triple(easting, northing, "$system")
            } else {
                Triple(lat, lon, system)
            }
            tv.text = "Coordenadas: capturadas ($zoneOrSys)"
            // Disable manual edits
            findViewById<TextInputEditText>(R.id.inputCoordenadaX)?.apply { setText(String.format("%.3f", x)); isEnabled = false }
            findViewById<TextInputEditText>(R.id.inputCoordenadaY)?.apply { setText(String.format("%.3f", y)); isEnabled = false }
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

    private fun renderPendingPhotos() {
        val container = findViewById<LinearLayout>(R.id.containerFotos) ?: return
        val titulo = findViewById<TextView>(R.id.tvFotosTitulo)
        titulo?.text = "Fotos (${pendingPhotos.size}/3)"
        container.removeAllViews()
        pendingPhotos.forEachIndexed { index, p ->
            val tv = TextView(this).apply {
                setTextColor(getColor(R.color.white))
                textSize = 12f
                text = "#${index+1} ${p.timestamp}\nLat:${p.latitude.formatOrDash()} Lon:${p.longitude.formatOrDash()} Alt:${p.altitude.formatOrDash(2)} (${p.sistema})"
                setPadding(0,8,0,8)
            }
            container.addView(tv)
        }
    }
    private fun Double?.formatOrDash(decimals: Int = 5): String = if (this == null) "-" else String.format(Locale.getDefault(), "% .${decimals}f", this)
}
