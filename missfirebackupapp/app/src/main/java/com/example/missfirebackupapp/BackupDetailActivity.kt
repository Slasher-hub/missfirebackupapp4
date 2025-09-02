package com.example.missfirebackupapp

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.missfirebackupapp.data.BackupDatabase
import com.example.missfirebackupapp.data.BackupEntity
import com.example.missfirebackupapp.data.BackupRepository
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputEditText
import com.example.missfirebackupapp.SyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.missfirebackupapp.data.FotoEntity
import android.view.View
import java.util.Locale
import java.util.Calendar

class BackupDetailActivity : AppCompatActivity() {

    private var backupId: Int = -1
    private lateinit var repository: BackupRepository
    private var current: BackupEntity? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_backup_detail)

        backupId = intent.getStringExtra("BACKUP_ID")?.toIntOrNull() ?: -1
        if (backupId == -1) {
            Toast.makeText(this, "ID inválido", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        repository = BackupRepository(BackupDatabase.getDatabase(this).backupDao())

        val tvStatus = findViewById<TextView>(R.id.tvStatusAtual)
        val toolbar = findViewById<MaterialToolbar>(R.id.topAppBarDetail)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        // Shared form fields
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

    val btnSalvar = findViewById<Button>(R.id.btnSalvarEdicao)
        val btnFinalizar = findViewById<Button>(R.id.btnFinalizar)
    val containerFotos = findViewById<LinearLayout>(R.id.containerFotosDetail)
    val tvFotosTitulo = findViewById<TextView>(R.id.tvFotosTituloDetail)

        fun setAdapter(v: AutoCompleteTextView, list: List<String>) {
            v.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, list))
        }

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
            "US Anglo American","US Atlantic Nickel","US CSN","US CMOC - Nióbio","US CMOC - Fosfato","US MVV","US Colomi","US Maracá","US Cajati","US Taboca","US Vanádio","Usiminas","US Ciplan","US Almas","US Belocal - Matozinhos","US Belocal - Limeira","US Caraíba - Pilar","US Caraíba - Vermelhos","US Oz Minerals","US Jacobina","US Anglo Gold Ashanti - Crixás","US Aripuanã","Ferbasa","Vale Urucum","US Carajás","US S11D","US Sossego","Vale Onça Puma","US Vale Sul - Itabira","US Vale Sul - Mariana","US Vale Sul - Brucutu","US Vale Sul - CPX","US Vale Sul - Vargem grande","US Vale Sul - Água Limpa","US Viga","US Morro da Mina","CD São Paulo","CD Jardinópolis","CD Minas Gerais","CD Paraná","CD Bahia","CD Goiás","CD Pernambuco","CD Rio Grande do Sul","PD Matriz","N/A","CD NOVA ROMA"
        )
        val tiposDetonador = listOf("SP","UG","OP","XD","SW","SP - DT5","WP")
        val cabos = listOf("Standard - STD","Reforçado - HD","Reforçado - HD2","Super reforçado - XO","Super reforçado - M95","Super reforçado - M105","UG2 - SW","UG3 - SW","WP - DT5")
        val metragens = listOf("6","8","10","15","20","30","40","60","0")
        val recuperacao = listOf("Sim","Não")

        setAdapter(inputMotivo, motivos)
        setAdapter(inputUnidade, unidades)
        setAdapter(inputTipoDetonador, tiposDetonador)
        setAdapter(inputCaboDetonador, cabos)
        setAdapter(inputMetragem, metragens)
        setAdapter(inputRecuperacao, recuperacao)

        // Date picker
        inputData.setOnClickListener {
            val c = Calendar.getInstance()
            DatePickerDialog(this, { _, y, m, d ->
                inputData.setText(String.format("%02d/%02d/%d", d, m + 1, y))
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
        }

        // Load data
        lifecycleScope.launch(Dispatchers.IO) {
            current = repository.getBackupById(backupId)
            // Carregar fotos relacionadas
            val fotosDao = BackupDatabase.getDatabase(this@BackupDetailActivity).backupDao()
            val fotosList = fotosDao.getFotosListByBackupId(backupId)
            val b = current
            withContext(Dispatchers.Main) {
                if (b == null) {
                    Toast.makeText(this@BackupDetailActivity, "Backup não encontrado", Toast.LENGTH_SHORT).show(); finish(); return@withContext
                }
                tvStatus.text = "Status: ${b.status}"
                inputData.setText(b.data)
                inputUnidade.setText(b.unidade, false)
                inputCava.setText(b.cava)
                inputBanco.setText(b.banco)
                inputFogo.setText(b.fogoId)
                inputFuro.setText(b.furoNumero)
                inputDetonador.setText(b.detonadorNumero)
                inputEspoleta.setText(b.espoletaId)
                inputMotivo.setText(b.motivo, false)
                inputTipoDetonador.setText(b.tipoDetonador, false)
                inputCaboDetonador.setText(b.caboDetonador, false)
                inputMetragem.setText(b.metragem, false)
                inputRecuperacao.setText(b.tentativaRecuperacao, false)
                inputX.setText(b.coordenadaX.toString())
                inputY.setText(b.coordenadaY.toString())
                inputZ.setText(b.coordenadaZ.toString())
                val tvCoordStatus = findViewById<TextView>(R.id.tvStatusCoordenadas)
                tvCoordStatus?.text = if (b.coordenadaX != 0.0 || b.coordenadaY != 0.0) {
                    "Coordenadas: capturadas (${b.sistemaCoordenadas})"
                } else {
                    "Coordenadas: não informadas"
                }
                if (b.coordenadaX != 0.0 || b.coordenadaY != 0.0) {
                    // lock coordinate fields regardless of status
                    listOf(R.id.inputCoordenadaX,R.id.inputCoordenadaY,R.id.inputCoordenadaZ).forEach { id ->
                        findViewById<android.view.View>(id)?.isEnabled = false
                    }
                }
                if (b.status == "PRONTO") lockFields()
                // Render fotos e coordenadas
                renderFotos(containerFotos, tvFotosTitulo, fotosList)
            }
        }

        fun validateBasic(): Boolean {
            if (inputData.text.isNullOrBlank() || inputUnidade.text.isNullOrBlank() || inputCava.text.isNullOrBlank()) {
                Toast.makeText(this, "Preencha Data, Unidade e Cava", Toast.LENGTH_SHORT).show(); return false
            }
            return true
        }

        fun validateAll(): Boolean {
            val fields = listOf(
                inputData, inputUnidade, inputCava, inputBanco, inputFogo, inputFuro,
                inputDetonador, inputEspoleta, inputMotivo, inputTipoDetonador, inputCaboDetonador,
                inputMetragem, inputRecuperacao
            )
            val empty = fields.any { it.text.isNullOrBlank() }
            if (empty) {
                Toast.makeText(this, "Preencha todos os campos antes de finalizar", Toast.LENGTH_LONG).show(); return false
            }
            val x = inputX.text.toString().toDoubleOrNull() ?: 0.0
            val y = inputY.text.toString().toDoubleOrNull() ?: 0.0
            if (x == 0.0 && y == 0.0) {
                Toast.makeText(this, "Capture ou informe coordenadas antes de finalizar", Toast.LENGTH_LONG).show(); return false
            }
            return true
        }

        btnSalvar.setOnClickListener {
            val base = current ?: return@setOnClickListener
            if (!validateBasic()) return@setOnClickListener
            val atualizado = base.copy(
                data = inputData.text.toString(), unidade = inputUnidade.text.toString(), cava = inputCava.text.toString(),
                banco = inputBanco.text.toString(), fogoId = inputFogo.text.toString(), furoNumero = inputFuro.text.toString(),
                detonadorNumero = inputDetonador.text.toString(), espoletaId = inputEspoleta.text.toString(), motivo = inputMotivo.text.toString(),
                tipoDetonador = inputTipoDetonador.text.toString(), caboDetonador = inputCaboDetonador.text.toString(), metragem = inputMetragem.text.toString(),
                tentativaRecuperacao = inputRecuperacao.text.toString(), coordenadaX = inputX.text.toString().toDoubleOrNull() ?: 0.0,
                coordenadaY = inputY.text.toString().toDoubleOrNull() ?: 0.0, coordenadaZ = inputZ.text.toString().toDoubleOrNull() ?: 0.0
            )
            lifecycleScope.launch(Dispatchers.IO) {
                repository.updateFull(atualizado)
                current = atualizado
                withContext(Dispatchers.Main) {
                    tvStatus.text = "Status: ${atualizado.status}"
                    Toast.makeText(this@BackupDetailActivity, "Alterações salvas", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnFinalizar.setOnClickListener {
            val base = current ?: return@setOnClickListener
            if (!validateAll()) return@setOnClickListener
            val atualizado = base.copy(
                data = inputData.text.toString(), unidade = inputUnidade.text.toString(), cava = inputCava.text.toString(),
                banco = inputBanco.text.toString(), fogoId = inputFogo.text.toString(), furoNumero = inputFuro.text.toString(),
                detonadorNumero = inputDetonador.text.toString(), espoletaId = inputEspoleta.text.toString(), motivo = inputMotivo.text.toString(),
                tipoDetonador = inputTipoDetonador.text.toString(), caboDetonador = inputCaboDetonador.text.toString(), metragem = inputMetragem.text.toString(),
                tentativaRecuperacao = inputRecuperacao.text.toString(), coordenadaX = inputX.text.toString().toDoubleOrNull() ?: 0.0,
                coordenadaY = inputY.text.toString().toDoubleOrNull() ?: 0.0, coordenadaZ = inputZ.text.toString().toDoubleOrNull() ?: 0.0,
                status = "PRONTO",
                syncError = false
            )
            lifecycleScope.launch(Dispatchers.IO) {
                repository.updateFull(atualizado)
                current = atualizado
                withContext(Dispatchers.Main) {
                    tvStatus.text = "Status: ${atualizado.status}"
                    Toast.makeText(this@BackupDetailActivity, "Atualizado para PRONTO. Enviando...", Toast.LENGTH_SHORT).show()
                    lockFields()
                    lifecycleScope.launch {
                        val result = SyncManager.syncBackup(this@BackupDetailActivity, atualizado)
                        result.onSuccess { synced ->
                            tvStatus.text = "Status: ${synced.status}"
                            Toast.makeText(this@BackupDetailActivity, "Backup sincronizado", Toast.LENGTH_SHORT).show()
                            setResult(RESULT_OK)
                            finish()
                        }.onFailure { err ->
                            // Marcar syncError true localmente
                            val failed = atualizado.copy(syncError = true)
                            lifecycleScope.launch(Dispatchers.IO) { repository.updateFull(failed) }
                            tvStatus.text = "Status: PRONTO (A sincronizar)"
                            Toast.makeText(this@BackupDetailActivity, "Falha ao sincronizar: ${err.message}", Toast.LENGTH_LONG).show()
                            setResult(RESULT_OK)
                        }
                    }
                }
            }
        }
    }

    private fun lockFields() {
        // Disable all interactive form elements & buttons
        val ids = listOf(
            R.id.inputData, R.id.inputUnidade, R.id.inputCava, R.id.inputBanco, R.id.inputFogo,
            R.id.inputFuro, R.id.inputDetonador, R.id.inputEspoleta, R.id.inputMotivo, R.id.inputTipoDetonador,
            R.id.inputCaboDetonador, R.id.inputMetragem, R.id.inputRecuperacao, R.id.inputCoordenadaX,
            R.id.inputCoordenadaY, R.id.inputCoordenadaZ, R.id.btnSalvarEdicao, R.id.btnFinalizar
        )
        ids.forEach { findViewById<android.view.View>(it)?.isEnabled = false }
    }

    // Upload now handled by SyncManager

    private fun renderFotos(container: LinearLayout?, titulo: TextView?, fotos: List<FotoEntity>) {
        if (container == null || titulo == null) return
        titulo.text = "Fotos (${fotos.size})"
        container.removeAllViews()
        if (fotos.isEmpty()) {
            val tv = TextView(this).apply {
                text = "Nenhuma foto"
                setTextColor(getColor(R.color.white))
            }
            container.addView(tv)
            return
        }
        fotos.forEachIndexed { index, f ->
            val tv = TextView(this).apply {
                setTextColor(getColor(R.color.white))
                textSize = 12f
                text = "#${index+1} ${f.dataHora}\nLat:${f.latitude?.formatOrDash()} Lon:${f.longitude?.formatOrDash()} Alt:${f.altitude?.formatOrDash(2)} (${f.sistemaCoordenadas ?: "?"})"
                setPadding(0,8,0,8)
            }
            container.addView(tv)
        }
    }
    private fun Double?.formatOrDash(decimals: Int = 5): String = if (this == null) "-" else String.format(Locale.getDefault(), "% .${decimals}f", this)
}
