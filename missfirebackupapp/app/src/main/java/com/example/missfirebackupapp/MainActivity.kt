package com.example.missfirebackupapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.missfirebackupapp.adapter.HistoricoAdapter
import com.example.missfirebackupapp.model.HistoricoItem
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerHistorico: RecyclerView
    private lateinit var adapter: HistoricoAdapter

    private var listaBackup = mutableListOf<HistoricoItem>()
    private var listaMissfire = mutableListOf<HistoricoItem>()
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

        // RecyclerView
        recyclerHistorico = findViewById(R.id.rvHistorico)
        recyclerHistorico.layoutManager = LinearLayoutManager(this)

        // Simulação inicial de dados
        listaBackup.add(HistoricoItem("1", "Registro Backup #1", false))
        listaBackup.add(HistoricoItem("2", "Registro Backup #2", true))
        listaMissfire.add(HistoricoItem("3", "Registro Missfire #1", false))

        // Adapter inicial
        adapter = HistoricoAdapter(listaBackup) { item ->
            abrirDetalhes(item)
        }
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
        // Aqui você pode abrir uma Activity para detalhar o registro
        // Exemplo:
        // val intent = Intent(this, DetalheActivity::class.java)
        // intent.putExtra("ITEM_ID", item.id)
        // startActivity(intent)
    }
}
