package com.example.missfirebackupapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Registro incremental de atualização/investigação para um Missfire.
 * Cada entrada representa uma anotação feita em momento posterior à criação.
 * Futuro: poderá ser sincronizada com backend, mantendo userId/remoteId.
 */
@Entity(tableName = "missfire_update_table")
data class MissfireUpdateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val missfireId: Int,
    val userId: String? = null,          // placeholder futura integração login
    val texto: String,
    val createdAt: Long = System.currentTimeMillis(),
    val remoteId: String? = null,
    val lastSyncAt: Long? = null,
    val syncError: Boolean = false,
    val syncErrorMessage: String? = null
)