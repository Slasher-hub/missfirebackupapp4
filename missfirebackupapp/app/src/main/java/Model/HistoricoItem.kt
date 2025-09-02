package com.example.missfirebackupapp.model

data class HistoricoItem(
    val id: String,
    val titulo: String,
    val status: String,
    val syncError: Boolean,
    var concluido: Boolean = false
)
