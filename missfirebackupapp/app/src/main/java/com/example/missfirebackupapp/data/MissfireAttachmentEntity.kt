package com.example.missfirebackupapp.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "missfire_attachment_table",
    foreignKeys = [
        ForeignKey(
            entity = MisfireEntity::class,
            parentColumns = ["id"],
            childColumns = ["missfireId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["missfireId"])]
)
data class MissfireAttachmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val missfireId: Int,
    val updateId: Int? = null,        // referencia opcional a uma atualização específica
    val tipo: String,              // FOTO | ARQUIVO
    val localPath: String,         // Caminho local temporário
    val remoteUrl: String? = null, // URL após upload
    val mimeType: String,
    val tamanhoBytes: Long,
    val createdAt: Long = System.currentTimeMillis()
)
