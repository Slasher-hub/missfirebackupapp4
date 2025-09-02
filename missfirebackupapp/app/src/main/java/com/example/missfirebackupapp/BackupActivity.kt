package com.example.missfirebackupapp

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.*

class BackupActivity : AppCompatActivity() {

    private lateinit var repository: BackupRepository
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var currentPhotoPath: String? = null
    private var currentLatitude: Double? = null
    private var currentLongitude: Double? = null

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

        // ✅ Botão Foto
        val btnFoto = findViewById<Button>(R.id.btnFoto)
        btnFoto.setOnClickListener {
            if (checkPermissions()) {
                openCamera()
                getCurrentLocation()
            } else {
                requestPermissions(
                    arrayOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ), 100
                )
            }
        }

        // ✅ Botão Salvar Localmente
        val btnSalvar = findViewById<Button>(R.id.btnSalvarLocal)
        btnSalvar.setOnClickListener {
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
                coordenadaX = inputX.text.toString().toDoubleOrNull() ?: 0.0,
                coordenadaY = inputY.text.toString().toDoubleOrNull() ?: 0.0,
                coordenadaZ = inputZ.text.toString().toDoubleOrNull() ?: 0.0
            )

            if (inputData.text.isNullOrEmpty() || inputUnidade.text.isNullOrEmpty() || inputCava.text.isNullOrEmpty()) {
                Toast.makeText(this, "Preencha todos os campos obrigatórios", Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }

            lifecycleScope.launch(Dispatchers.IO) {
                repository.insertBackup(backup)
                runOnUiThread {
                    Toast.makeText(
                        this@BackupActivity,
                        "Backup salvo localmente!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    // ✅ Verifica permissões
    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    // ✅ Abrir câmera
    private fun openCamera() {
        val photoFile = File.createTempFile("photo_${System.currentTimeMillis()}", ".jpg", cacheDir)
        currentPhotoPath = photoFile.absolutePath

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val photoUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", photoFile)
        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
        startActivityForResult(intent, 101)
    }

    // ✅ Pegar localização
    private fun getCurrentLocation() {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    currentLatitude = location.latitude
                    currentLongitude = location.longitude
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    // ✅ Recebe resultado da câmera
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 101 && resultCode == RESULT_OK) {
            val foto = FotoEntity(
                backupId = backupIdCriado // ID retornado quando salvou o Backup ,
                caminhoFoto = currentPhotoPath "",
                latitude = currentLatitude ?: 0.0,
                longitude = currentLongitude ?: 0.0,
                dataHora = "" // Data/hora atual
            )

            lifecycleScope.launch(Dispatchers.IO) {
                repository.insertFoto(foto)
            }

            Toast.makeText(this, "Foto salva com coordenadas!", Toast.LENGTH_SHORT).show()
        }
    }
}
