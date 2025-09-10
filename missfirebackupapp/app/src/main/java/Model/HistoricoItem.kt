package com.example.missfirebackupapp.model

data class HistoricoItem(
    val id: String,
    val titulo: String,
    val status: String,
    val syncError: Boolean,
    val syncErrorMessage: String? = null,
    var concluido: Boolean = false,
    val causa: String? = null,
    val medidas: String? = null
)
