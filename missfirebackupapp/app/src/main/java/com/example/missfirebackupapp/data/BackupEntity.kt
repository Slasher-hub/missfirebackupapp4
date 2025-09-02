package com.example.missfirebackupapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "backup_table")
data class BackupEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val data: String,
    val unidade: String,
    val cava: String,
    val banco: String,
    val fogoId: String,
    val furoNumero: String,
    val detonadorNumero: String,
    val espoletaId: String,
    val motivo: String,
    val tipoDetonador: String,
    val caboDetonador: String,
    val metragem: String,
    val tentativaRecuperacao: String,

    val coordenadaX: Double,
    val coordenadaY: Double,
    val coordenadaZ: Double,

    // Sistema / Datum das coordenadas armazenadas (ex: WGS84, SIRGAS2000-23S, SAD69-22S, UTM-23S)
    val sistemaCoordenadas: String = "WGS84",

    // Status do ciclo de vida do registro: INCOMPLETO, PRONTO, SINCRONIZADO
    val status: String = "INCOMPLETO",
    // Flag indica se última tentativa de sync falhou (para exibir botão retry)
    val syncError: Boolean = false,
    // Timestamp criação (epoch millis) para ordenação/histórico
    val createdAt: Long = System.currentTimeMillis()
)