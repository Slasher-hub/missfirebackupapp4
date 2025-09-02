package com.example.missfirebackupapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
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
            onSyncClick = { item -> forceSync(item) }
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
