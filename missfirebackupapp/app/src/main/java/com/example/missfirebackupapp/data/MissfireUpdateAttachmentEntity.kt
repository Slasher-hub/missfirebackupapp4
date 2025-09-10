package com.example.missfirebackupapp.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Anexo vinculado a uma atualização específica de Missfire.
 * Permite fotos ou arquivos adicionais adicionados durante a investigação.
 */
@Entity(
    tableName = "missfire_update_attachment_table",
    foreignKeys = [
        ForeignKey(
            entity = MissfireUpdateEntity::class,
            parentColumns = ["id"],
            childColumns = ["updateId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["updateId"])]
)
data class MissfireUpdateAttachmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val updateId: Int,
    val tipo: String,              // FOTO | ARQUIVO
    val localPath: String,
    val remoteUrl: String? = null,
    val mimeType: String,
    val tamanhoBytes: Long,
    val createdAt: Long = System.currentTimeMillis()
)
