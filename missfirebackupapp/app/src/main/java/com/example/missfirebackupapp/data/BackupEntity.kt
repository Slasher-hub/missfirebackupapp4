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
    val coordenadaZ: Double
)