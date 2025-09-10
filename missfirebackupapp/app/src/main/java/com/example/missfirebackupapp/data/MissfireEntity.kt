package com.example.missfirebackupapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "missfire_table")
data class MisfireEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val remoteId: String? = null,

    val dataOcorrencia: Long,              // epoch millis
    val local: String,
    val responsavel: String,
    val itensEncontrados: String,
    val descricaoOcorrencia: String,

    val statusInvestigacao: String = "EM_ANDAMENTO", // EM_ANDAMENTO | CONCLUIDA

    val dataDesmonte: Long? = null,
    val causa: String? = null,
    val infoAdicionais: String? = null,
    val medidasPreventivas: String? = null,

    val createdAt: Long = System.currentTimeMillis(),
    val lastUpdated: Long = System.currentTimeMillis(),

    // Campos de sincronização futuros (mantém simetria com Backup)
    val syncError: Boolean = false,
    val syncErrorMessage: String? = null,
    val lastSyncAt: Long? = null
)
