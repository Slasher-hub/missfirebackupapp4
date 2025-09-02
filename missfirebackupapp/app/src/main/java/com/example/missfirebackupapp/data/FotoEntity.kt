package com.example.missfirebackupapp.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "foto_table",
    foreignKeys = [
        ForeignKey(
            entity = BackupEntity::class,
            parentColumns = ["id"],
            childColumns = ["backupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["backupId"])]
)
data class FotoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val backupId: Int,           // Referência ao Backup
    val caminhoFoto: String,     // Caminho da foto no dispositivo
    val remoteUrl: String? = null, // URL no Firebase Storage (preenchido após upload)
    val latitude: Double?,       // Coordenada latitude
    val longitude: Double?,      // Coordenada longitude
    val dataHora: String         // Data/hora em que a foto foi tirada
)
