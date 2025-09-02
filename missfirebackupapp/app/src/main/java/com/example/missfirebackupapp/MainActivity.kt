package com.example.missfirebackupapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import android.view.LayoutInflater
import android.widget.AutoCompleteTextView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.lifecycle.lifecycleScope
import com.example.missfirebackupapp.data.BackupDatabase
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.recyclerview.widget.RecyclerView
import com.example.missfirebackupapp.adapter.HistoricoAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.missfirebackupapp.model.HistoricoItem
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerHistorico: RecyclerView
    private lateinit var adapter: HistoricoAdapter

    private var listaBackup = mutableListOf<HistoricoItem>()
    private var listaMissfire = mutableListOf<HistoricoItem>() // futuramente Missfire
    private var filtroAtual = "Backup"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Botões principais
        val btnBackup = findViewById<Button>(R.id.btnBackup)
        val btnMissfire = findViewById<Button>(R.id.btnMissfire)

        // Botões de filtro
        val btnFiltroBackup = findViewById<Button>(R.id.btnFiltroBackup)
        val btnFiltroMissfire = findViewById<Button>(R.id.btnFiltroMissfire)

        // Botão voltar
        val btnVoltar = findViewById<ImageButton>(R.id.btnVoltar)
        val btnSistemaCoord = findViewById<Button>(R.id.btnSistemaCoord)

        val shared = getSharedPreferences("prefs", MODE_PRIVATE)
        fun currentSystem(): String = shared.getString("coordSystem", "WGS84") ?: "WGS84"
        btnSistemaCoord.text = currentSystem()

        val sistemas = listOf(
            "WGS84",
            "SIRGAS2000-21S","SIRGAS2000-22S","SIRGAS2000-23S","SIRGAS2000-24S",
            "SAD69-21S","SAD69-22S","SAD69-23S","SAD69-24S"
        )

        btnSistemaCoord.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Sistema de Coordenadas")
                .setItems(sistemas.toTypedArray()) { d, which ->
                    val sel = sistemas[which]
                    shared.edit().putString("coordSystem", sel).apply()
                    btnSistemaCoord.text = sel
                    Toast.makeText(this, "Sistema definido: $sel", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        // RecyclerView
        recyclerHistorico = findViewById(R.id.rvHistorico)
        recyclerHistorico.layoutManager = LinearLayoutManager(this)

        // Carregar dados reais de Backups (Flow Room)
        val dao = BackupDatabase.getDatabase(this).backupDao()
        lifecycleScope.launch {
            dao.getAllBackups().collectLatest { backups ->
                listaBackup = backups.map { b ->
                    val concluido = b.status == "SINCRONIZADO"
                    HistoricoItem(
                        id = b.id.toString(),
                        titulo = "Backup ${b.data} - ${b.unidade}",
                        status = b.status,
                        syncError = b.syncError,
                        concluido = concluido
                    )
                }.toMutableList()
                if (filtroAtual == "Backup") adapter.atualizarLista(listaBackup)
            }
        }

        // Adapter inicial
        adapter = HistoricoAdapter(
            listaBackup,
            onItemClick = { item -> abrirDetalhes(item) },
            onSyncClick = { item -> forceSync(item) },
            onDeleteClick = { item -> confirmarExclusao(item) }
        )
        recyclerHistorico.adapter = adapter

        // ✅ Botão "Registrar Backup" abre BackupActivity
        btnBackup.setOnClickListener {
            startActivity(Intent(this, BackupActivity::class.java))
        }

        // ✅ Botão "Registrar Missfire" abre MissfireActivity
        btnMissfire.setOnClickListener {
            startActivity(Intent(this, MissFireActivity::class.java))
        }

        // ✅ Botão Voltar agora abre um AlertDialog para confirmar logout
        btnVoltar.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Sair da conta")
                .setMessage("Deseja realmente voltar? Isso irá deslogar sua conta.")
                .setPositiveButton("Sim") { _, _ ->
                    FirebaseAuth.getInstance().signOut()
                    val intent = Intent(this, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        // ✅ Filtro: Backup
        btnFiltroBackup.setOnClickListener {
            filtroAtual = "Backup"
            atualizarLista()
            btnFiltroBackup.isSelected = true
            btnFiltroMissfire.isSelected = false
        }

        // ✅ Filtro: Missfire
        btnFiltroMissfire.setOnClickListener {
            filtroAtual = "Missfire"
            atualizarLista()
            btnFiltroBackup.isSelected = false
            btnFiltroMissfire.isSelected = true
        }

    // Inicia com filtro Backup selecionado
    btnFiltroBackup.isSelected = true

    // Botão de filtros avançados
    findViewById<Button>(R.id.btnAbrirFiltros).setOnClickListener { abrirDialogFiltrosNovo() }
    }

    private fun confirmarExclusao(item: HistoricoItem) {
        if (item.status != "SINCRONIZADO") {
            Toast.makeText(this, "Só é permitido excluir após sincronizar", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Excluir registro")
            .setMessage("Tem certeza que deseja excluir este registro e suas fotos?")
            .setPositiveButton("Excluir") { _, _ -> executarExclusao(item) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun executarExclusao(item: HistoricoItem) {
        lifecycleScope.launch {
            val dao = BackupDatabase.getDatabase(this@MainActivity).backupDao()
            val idInt = item.id.toIntOrNull() ?: return@launch
            withContext(Dispatchers.IO) {
                dao.deleteFotosByBackupId(idInt)
                dao.deleteBackupById(idInt)
            }
            Toast.makeText(this@MainActivity, "Excluído", Toast.LENGTH_SHORT).show()
            // Recarregar backups filtrados ou completos
            aplicarFiltros()
        }
    }

    // Campos filtro atuais em memória
    private var filtroMesAno: String? = null
    private var filtroUnidade: String? = null
    private var filtroCava: String? = null

    private fun abrirDialogFiltrosNovo() {
        if (filtroAtual != "Backup") { Toast.makeText(this, "Filtros apenas para Backups", Toast.LENGTH_SHORT).show(); return }
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_filtros_novo, null)
        val actMesAno = view.findViewById<AutoCompleteTextView>(R.id.actMesAno)
        val actUnidade = view.findViewById<AutoCompleteTextView>(R.id.actUnidadeFiltro)
        val actCava = view.findViewById<AutoCompleteTextView>(R.id.actCavaFiltro)

        // Extrair listas distintas da base atual (listaBackup original sem filtros no momento)
        val mesesAnos = listaBackup.mapNotNull { it.titulo.substringAfter("Backup ").substring(0,10).takeIf { s -> s.count{it=='/'}==2 } }
            .map { it.substring(3) } // pega MM/YYYY
            .distinct().sorted()
        val unidades = listaBackup.mapNotNull { it.titulo.substringAfterLast(" - ").ifBlank { null } }.distinct().sorted()
        val cavas = mutableListOf<String>() // Placeholder se quisermos extrair cavas (não presente no titulo atual)

        fun <T> setAdapter(act: AutoCompleteTextView, data: List<T>) {
            act.setAdapter(android.widget.ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, data))
        }
        setAdapter(actMesAno, mesesAnos)
        setAdapter(actUnidade, unidades)
        setAdapter(actCava, cavas)

        actMesAno.setText(filtroMesAno ?: "", false)
        actUnidade.setText(filtroUnidade ?: "", false)
        actCava.setText(filtroCava ?: "", false)

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()

        view.findViewById<Button>(R.id.btnLimparFiltros).setOnClickListener {
            filtroMesAno = null; filtroUnidade = null; filtroCava = null; aplicarFiltros(); dialog.dismiss()
        }
        view.findViewById<Button>(R.id.btnAplicarFiltros).setOnClickListener {
            filtroMesAno = actMesAno.text.toString().ifBlank { null }
            filtroUnidade = actUnidade.text.toString().ifBlank { null }
            filtroCava = actCava.text.toString().ifBlank { null }
            aplicarFiltros(); dialog.dismiss()
        }
        dialog.show()
    }

    private fun aplicarFiltros() {
        if (filtroAtual != "Backup") { atualizarLista(); return }
        val dao = BackupDatabase.getDatabase(this).backupDao()
        lifecycleScope.launch {
            val filtrados = withContext(Dispatchers.IO) {
                if (filtroMesAno == null && filtroUnidade == null && filtroCava == null) {
                    dao.filtrarBackups(null, null, null)
                } else {
                    dao.filtrarBackups(filtroMesAno, filtroUnidade, filtroCava)
                }
            }
            listaBackup = filtrados.map { b ->
                HistoricoItem(
                    id = b.id.toString(),
                    titulo = "Backup ${b.data} - ${b.unidade}",
                    status = b.status,
                    syncError = b.syncError,
                    concluido = b.status == "SINCRONIZADO"
                )
            }.toMutableList()
            adapter.atualizarLista(listaBackup)
            val indicador = if (filtroMesAno!=null || filtroUnidade!=null || filtroCava!=null) "(Filtros)" else ""
            Toast.makeText(this@MainActivity, "${listaBackup.size} registros $indicador", Toast.LENGTH_SHORT).show()
        }
    }

    private fun atualizarLista() {
        if (filtroAtual == "Backup") {
            adapter.atualizarLista(listaBackup)
        } else {
            adapter.atualizarLista(listaMissfire)
        }
    }

    private fun abrirDetalhes(item: HistoricoItem) {
    val intent = Intent(this, BackupDetailActivity::class.java)
    intent.putExtra("BACKUP_ID", item.id)
    startActivity(intent)
    }

    private fun forceSync(item: HistoricoItem) {
        // Dispara sync apenas para backups com status PRONTO
        lifecycleScope.launch {
            try {
                val dao = BackupDatabase.getDatabase(this@MainActivity).backupDao()
                val idInt = item.id.toIntOrNull() ?: return@launch
                val entity = withContext(Dispatchers.IO) { dao.getBackupById(idInt) }
                if (entity == null) {
                    Toast.makeText(this@MainActivity, "Registro não encontrado", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                if (entity.status == "SINCRONIZADO") {
                    Toast.makeText(this@MainActivity, "Já sincronizado", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val result = SyncManager.syncBackup(this@MainActivity, entity)
                result.onSuccess {
                    Toast.makeText(this@MainActivity, "Sincronizado", Toast.LENGTH_SHORT).show()
                }.onFailure { e ->
                    Toast.makeText(this@MainActivity, "Falha sync: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
