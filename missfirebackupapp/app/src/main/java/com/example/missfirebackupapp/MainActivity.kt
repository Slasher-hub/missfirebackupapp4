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
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.lifecycle.lifecycleScope
import com.example.missfirebackupapp.data.BackupDatabase
import com.example.missfirebackupapp.data.MisfireRepository
import com.example.missfirebackupapp.data.MisfireEntity
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
    private var listaBackupEntities = mutableListOf<com.example.missfirebackupapp.data.BackupEntity>()
    private var listaMissfire = mutableListOf<HistoricoItem>() // futuramente Missfire
    private var filtroAtual = "Backup"
    private lateinit var tvCount: TextView
    private lateinit var btnFiltroBackup: Button
    private lateinit var btnFiltroMissfire: Button

    private lateinit var missfireRepository: MisfireRepository
    private var listaMissfireEntities = mutableListOf<MisfireEntity>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Botões principais
        val btnBackup = findViewById<Button>(R.id.btnBackup)
    val btnMissfire = findViewById<Button>(R.id.btnMissfire)

        // Botões de filtro
    btnFiltroBackup = findViewById(R.id.btnFiltroBackup)
    btnFiltroMissfire = findViewById(R.id.btnFiltroMissfire)
    tvCount = findViewById(R.id.tvCount)

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
        val badgePendentes = findViewById<TextView>(R.id.badgePendentes)
        lifecycleScope.launch {
            dao.getAllBackups().collectLatest { backups ->
                listaBackupEntities = backups.toMutableList()
                listaBackup = backups.map { b ->
                    val concluido = b.status == "SINCRONIZADO"
                    HistoricoItem(
                        id = b.id.toString(),
                        titulo = "Backup ${b.data} - ${b.unidade}",
                        status = b.status,
                        syncError = b.syncError,
                        syncErrorMessage = b.syncErrorMessage,
                        concluido = concluido
                    )
                }.toMutableList()
                if (filtroAtual == "Backup") adapter.atualizarLista(listaBackup)
                atualizarContagem()
                val pendentes = backups.count { it.status != "SINCRONIZADO" }
                if (pendentes > 0) {
                    badgePendentes?.text = pendentes.toString()
                    badgePendentes?.visibility = android.view.View.VISIBLE
                } else {
                    badgePendentes?.visibility = android.view.View.GONE
                }
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

        // Long press manual via addOnItemTouchListener simples (adapter não tem callback de long click builtin)
        recyclerHistorico.addOnItemTouchListener(object: androidx.recyclerview.widget.RecyclerView.SimpleOnItemTouchListener() {
            private val gesture = android.view.GestureDetector(this@MainActivity, object: android.view.GestureDetector.SimpleOnGestureListener() {
                override fun onLongPress(e: android.view.MotionEvent) {
                    val child = recyclerHistorico.findChildViewUnder(e.x, e.y) ?: return
                    val pos = recyclerHistorico.getChildAdapterPosition(child)
                    if (pos != androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
                        val item = adapter.getItem(pos)
                        if (item.id.startsWith("MISSFIRE_")) mostrarMenuMissfire(item)
                    }
                }
            })
            override fun onInterceptTouchEvent(rv: RecyclerView, e: android.view.MotionEvent): Boolean {
                gesture.onTouchEvent(e)
                return false
            }
        })

        // ✅ Botão "Registrar Backup" abre BackupActivity
        btnBackup.setOnClickListener {
            startActivity(Intent(this, BackupActivity::class.java))
        }

        // ✅ Botão "Registrar Misfire" abre MisfireActivity
        btnMissfire.setOnClickListener {
            startActivity(Intent(this, MisfireActivity::class.java))
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
            aplicarFiltros()
            btnFiltroBackup.isSelected = true
            btnFiltroMissfire.isSelected = false
            atualizarContagem()
            aplicarEstiloToggles()
        }

        // ✅ Filtro: Misfire
        btnFiltroMissfire.setOnClickListener {
            filtroAtual = "Misfire"
            aplicarFiltros()
            btnFiltroBackup.isSelected = false
            btnFiltroMissfire.isSelected = true
            atualizarContagem()
            aplicarEstiloToggles()
        }

    // Inicia com filtro Backup selecionado
    btnFiltroBackup.isSelected = true
    atualizarContagem()
    aplicarEstiloToggles()

    // Estado inicial filtros
    findViewById<ImageButton>(R.id.btnAbrirFiltros).setOnClickListener { abrirDialogFiltrosMultiselect() }
    atualizarIconeFiltro()

        val db = BackupDatabase.getDatabase(this)
    missfireRepository = MisfireRepository(db.missfireDao())
        lifecycleScope.launch {
            missfireRepository.observarMissfires().collectLatest { list ->
                listaMissfireEntities = list.toMutableList()
                atualizarLista()
            }
        }
    }

    private fun confirmarExclusao(item: HistoricoItem) {
        if (item.id.startsWith("MISSFIRE_")) {
            val rawId = item.id.removePrefix("MISSFIRE_").toIntOrNull()
            val entidade = rawId?.let { id -> listaMissfireEntities.firstOrNull { it.id == id } }
            val concluida = entidade?.statusInvestigacao == "CONCLUIDA"
            val syncFail = entidade?.syncError == true
            val msg = when {
                concluida && !syncFail && entidade?.remoteId != null -> "Relatório concluído e sincronizado, deseja excluir do histórico?"
                concluida && syncFail -> "Relatório concluído porém não sincronizado para a nuvem. Deseja excluir mesmo assim?"
                !concluida -> "Relatório em andamento, sem conclusão. Tem certeza que deseja deletar?"
                else -> "Deseja excluir?"
            }
            AlertDialog.Builder(this)
                .setTitle("Excluir Misfire")
                .setMessage(msg)
                .setPositiveButton("Excluir") { _, _ -> executarExclusaoMisfire(rawId) }
                .setNegativeButton("Cancelar", null)
                .show()
            return
        }
        // Backup
        val syncOk = item.status == "SINCRONIZADO"
        val msg = if (syncOk) "Excluir backup sincronizado?" else "Backup não sincronizado. Tem certeza que deseja excluir?"
        AlertDialog.Builder(this)
            .setTitle("Excluir Backup")
            .setMessage(msg)
            .setPositiveButton("Excluir") { _, _ -> executarExclusao(item) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun executarExclusaoMisfire(id: Int?) {
        if (id == null) return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val entity = listaMissfireEntities.firstOrNull { it.id == id }
                if (entity != null) {
                    missfireRepository.deletarMissfire(entity)
                    listaMissfireEntities.removeAll { it.id == id }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Misfire excluído", Toast.LENGTH_SHORT).show()
                    if (filtroAtual == "Misfire") atualizarLista() else atualizarContagem()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(this@MainActivity, "Erro ao excluir: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }
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

    // Multi-seleção filtros
    private var selecionadosMesAno = mutableSetOf<String>()
    private var selecionadosUnidade = mutableSetOf<String>()
    private var selecionadosCava = mutableSetOf<String>()
    private var filtrosAtivos = false

    // Filtros Misfire
    private var selecionadosMesAnoMisfire = mutableSetOf<String>()
    private var selecionadosUnidadeMisfire = mutableSetOf<String>()

    private fun abrirDialogFiltrosMultiselect() {
        val isBackup = filtroAtual == "Backup"
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_filtros_multiselect, null)
        val contMesAno = view.findViewById<android.widget.LinearLayout>(R.id.containerMesAno)
        val contUnidade = view.findViewById<android.widget.LinearLayout>(R.id.containerUnidade)
        val contCava = view.findViewById<android.widget.LinearLayout>(R.id.containerCava)

        val mesesAnos: List<String>
        val unidades: List<String>
        val cavas: List<String>
        if (isBackup) {
            mesesAnos = listaBackupEntities.mapNotNull { it.data }
                .mapNotNull { d -> if (d.length>=7) d.substring(3) else null }
                .distinct().sorted()
            unidades = listaBackupEntities.mapNotNull { it.unidade }.distinct().sorted()
            cavas = listaBackupEntities.mapNotNull { it.cava }.filter { it.isNotBlank() }.distinct().sorted()
        } else {
            val sdf = java.text.SimpleDateFormat("MM/yyyy")
            mesesAnos = listaMissfireEntities.map { sdf.format(java.util.Date(it.dataOcorrencia)) }
                .distinct().sorted()
            unidades = listaMissfireEntities.map { it.local }.filter { it.isNotBlank() }.distinct().sorted()
            cavas = emptyList() // não aplicável
            contCava.visibility = android.view.View.GONE
        }

        fun addChecks(container: android.widget.LinearLayout, values: List<String>, selecionados: MutableSet<String>) {
            container.removeAllViews()
            values.forEach { v ->
                val cb = android.widget.CheckBox(this).apply {
                    text = v
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.white))
                    isChecked = selecionados.contains(v)
                }
                cb.setOnCheckedChangeListener { _, checked -> if (checked) selecionados.add(v) else selecionados.remove(v) }
                container.addView(cb)
            }
            if (values.isEmpty()) {
                val tv = android.widget.TextView(this).apply { text = "(vazio)"; setTextColor(ContextCompat.getColor(this@MainActivity, R.color.white)) }
                container.addView(tv)
            }
        }
        if (isBackup) {
            addChecks(contMesAno, mesesAnos, selecionadosMesAno)
            addChecks(contUnidade, unidades, selecionadosUnidade)
            addChecks(contCava, cavas, selecionadosCava)
        } else {
            addChecks(contMesAno, mesesAnos, selecionadosMesAnoMisfire)
            addChecks(contUnidade, unidades, selecionadosUnidadeMisfire)
        }

        val dialog = AlertDialog.Builder(this).setView(view).create()
        view.findViewById<Button>(R.id.btnLimparFiltrosMulti).setOnClickListener {
            if (isBackup) {
                selecionadosMesAno.clear(); selecionadosUnidade.clear(); selecionadosCava.clear()
            } else {
                selecionadosMesAnoMisfire.clear(); selecionadosUnidadeMisfire.clear()
            }
            aplicarFiltros(); dialog.dismiss()
        }
        view.findViewById<Button>(R.id.btnAplicarFiltrosMulti).setOnClickListener {
            aplicarFiltros(); dialog.dismiss()
        }
        dialog.show()
    }

    private fun aplicarFiltros() {
        if (filtroAtual != "Backup") {
            if (filtroAtual == "Misfire") {
                val filtrados = listaMissfireEntities.filter { m ->
                    val sdf = java.text.SimpleDateFormat("MM/yyyy")
                    val mesAno = sdf.format(java.util.Date(m.dataOcorrencia))
                    val matchMes = selecionadosMesAnoMisfire.isEmpty() || selecionadosMesAnoMisfire.contains(mesAno)
                    val matchUni = selecionadosUnidadeMisfire.isEmpty() || selecionadosUnidadeMisfire.contains(m.local)
                    matchMes && matchUni
                }
                val items = filtrados.map { ent ->
                    HistoricoItem(
                        id = "MISSFIRE_${ent.id}",
                        titulo = "${ent.local} - ${java.text.SimpleDateFormat("dd/MM/yyyy").format(java.util.Date(ent.dataOcorrencia))}",
                        status = ent.statusInvestigacao,
                        syncError = ent.syncError,
                        syncErrorMessage = ent.syncErrorMessage,
                        concluido = ent.statusInvestigacao == "CONCLUIDA",
                        causa = ent.causa,
                        medidas = ent.medidasPreventivas
                    )
                }.toMutableList()
                adapter.atualizarLista(items)
                filtrosAtivos = selecionadosMesAnoMisfire.isNotEmpty() || selecionadosUnidadeMisfire.isNotEmpty()
                atualizarIconeFiltro()
                val indicador = if (filtrosAtivos) "(Filtros)" else ""
                Toast.makeText(this, "${items.size} registros $indicador", Toast.LENGTH_SHORT).show()
                atualizarContagem()
            } else {
                atualizarLista();
            }
            return
        }
        val filtrados = listaBackupEntities.filter { b ->
            val mesAno = b.data?.let { if (it.length>=7) it.substring(3) else null }
            val matchMes = selecionadosMesAno.isEmpty() || (mesAno!=null && selecionadosMesAno.contains(mesAno))
            val matchUni = selecionadosUnidade.isEmpty() || selecionadosUnidade.contains(b.unidade)
            val matchCava = selecionadosCava.isEmpty() || selecionadosCava.contains(b.cava)
            matchMes && matchUni && matchCava
        }
                listaBackup = filtrados.map { b ->
            HistoricoItem(
                id = b.id.toString(),
                titulo = "Backup ${b.data} - ${b.unidade}",
                status = b.status,
                syncError = b.syncError,
                syncErrorMessage = b.syncErrorMessage,
                concluido = b.status == "SINCRONIZADO"
            )
        }.toMutableList()
        adapter.atualizarLista(listaBackup)
        filtrosAtivos = if (filtroAtual == "Backup") {
            selecionadosMesAno.isNotEmpty() || selecionadosUnidade.isNotEmpty() || selecionadosCava.isNotEmpty()
        } else {
            selecionadosMesAnoMisfire.isNotEmpty() || selecionadosUnidadeMisfire.isNotEmpty()
        }
        atualizarIconeFiltro()
        val indicador = if (filtrosAtivos) "(Filtros)" else ""
        Toast.makeText(this, "${listaBackup.size} registros $indicador", Toast.LENGTH_SHORT).show()
    atualizarContagem()
    }

    private fun atualizarIconeFiltro() {
        val btn = findViewById<ImageButton>(R.id.btnAbrirFiltros)
        if (filtrosAtivos) {
            btn.setColorFilter(ContextCompat.getColor(this, R.color.redAccent))
            btn.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setStroke(3, ContextCompat.getColor(this@MainActivity, R.color.redAccent))
                setColor(android.graphics.Color.TRANSPARENT)
            }
        } else {
            btn.setColorFilter(ContextCompat.getColor(this, R.color.white))
            btn.background = null
        }
    }

    private fun atualizarLista() {
        val historicoItems = when (filtroAtual) {
            "Misfire" -> listaMissfireEntities.map { ent ->
                com.example.missfirebackupapp.model.HistoricoItem(
                    id = "MISSFIRE_${ent.id}",
                    titulo = "${ent.local} - ${java.text.SimpleDateFormat("dd/MM/yyyy").format(java.util.Date(ent.dataOcorrencia))}",
                    status = ent.statusInvestigacao, // Keep internal tokens EM_ANDAMENTO | CONCLUIDA
                    syncError = ent.syncError,
                    syncErrorMessage = ent.syncErrorMessage,
                    concluido = ent.statusInvestigacao == "CONCLUIDA",
                    causa = ent.causa,
                    medidas = ent.medidasPreventivas
                )
            }
            else -> listaBackupEntities.map { backup ->
                com.example.missfirebackupapp.model.HistoricoItem(
                    id = backup.id.toString(),
                    titulo = "${backup.unidade} - ${backup.data}",
                    status = backup.status,
                    syncError = backup.syncError,
                    syncErrorMessage = backup.syncErrorMessage
                )
            }
        }
        adapter.atualizarLista(historicoItems.toMutableList())
        atualizarContagem()
    }

    private fun atualizarContagem() {
        if (!::tvCount.isInitialized) return
    // Para Missfire devemos usar a lista real de entidades carregadas
    val total = if (filtroAtual == "Backup") listaBackup.size else listaMissfireEntities.size
        tvCount.text = "$total registros"
    }

    private fun aplicarEstiloToggles() {
        if (!::btnFiltroBackup.isInitialized || !::btnFiltroMissfire.isInitialized) return
        fun style(btn: Button, selected: Boolean) {
            btn.isAllCaps = true
            btn.stateListAnimator = null
            btn.backgroundTintList = null
            if (selected) {
                btn.background = ContextCompat.getDrawable(this, R.drawable.history_toggle_active)
                btn.setTextColor(ContextCompat.getColor(this, R.color.redAccent))
            } else {
                btn.background = ContextCompat.getDrawable(this, R.drawable.history_toggle_inactive)
                btn.setTextColor(ContextCompat.getColor(this, R.color.white))
            }
        }
        style(btnFiltroBackup, filtroAtual == "Backup")
    style(btnFiltroMissfire, filtroAtual == "Misfire")
    }

    private fun abrirDetalhes(item: HistoricoItem) {
        if (item.id.startsWith("MISSFIRE_")) {
            val rawId = item.id.removePrefix("MISSFIRE_").toIntOrNull()
            if (rawId == null) { Toast.makeText(this, "ID inválido", Toast.LENGTH_SHORT).show(); return }
            val intent = Intent(this, MisfireDetailActivity::class.java)
            intent.putExtra("misfire_id", rawId)
            startActivity(intent)
        } else {
            val intent = Intent(this, BackupDetailActivity::class.java)
            intent.putExtra("BACKUP_ID", item.id)
            startActivity(intent)
        }
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

    private fun mostrarMenuMissfire(item: HistoricoItem) {
        val rawId = item.id.removePrefix("MISSFIRE_").toIntOrNull() ?: return
        val entidade = listaMissfireEntities.firstOrNull { it.id == rawId }
        val concluida = entidade?.statusInvestigacao == "CONCLUIDA"
        val opcoes = mutableListOf("Abrir")
        if (!concluida) {
            opcoes.add("Nova atualização")
            opcoes.add("Concluir relatório")
        }
        AlertDialog.Builder(this)
            .setTitle("Ações Missfire")
            .setItems(opcoes.toTypedArray()) { d, which ->
                when(opcoes[which]) {
                    "Abrir" -> abrirDetalhes(item)
                    "Nova atualização" -> abrirDialogUpdateMissfire(rawId)
                    "Concluir relatório" -> abrirMissfireParaConcluir(rawId)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun abrirDialogUpdateMissfire(id: Int) {
        val input = android.widget.EditText(this).apply { hint = "Descrição da atualização" }
        AlertDialog.Builder(this)
            .setTitle("Nova atualização")
            .setView(input)
            .setPositiveButton("Salvar") { _, _ ->
                val texto = input.text.toString().trim()
                if (texto.isBlank()) { Toast.makeText(this, "Vazio", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                lifecycleScope.launch(Dispatchers.IO) {
                    val repo = missfireRepository
                    repo.adicionarUpdate(com.example.missfirebackupapp.data.MissfireUpdateEntity(missfireId = id, texto = texto))
                    // opcional: sync imediato apenas do update
                    withContext(Dispatchers.Main) { Toast.makeText(this@MainActivity, "Atualização adicionada", Toast.LENGTH_SHORT).show() }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }


    private fun abrirMissfireParaConcluir(id: Int) {
    val intent = Intent(this, MisfireDetailActivity::class.java)
    intent.putExtra("misfire_id", id)
        startActivity(intent)
    }
}
