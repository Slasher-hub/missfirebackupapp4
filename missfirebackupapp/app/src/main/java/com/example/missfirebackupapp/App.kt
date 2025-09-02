package com.example.missfirebackupapp

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            FirebaseApp.initializeApp(this)
            // Ativar logging do Firestore para facilitar debug (apenas debug builds)
            runCatching { FirebaseFirestore.getInstance().useEmulatorIfConfigured() }
            // Autenticação anônima (caso regras exijam request.auth != null)
            val auth = FirebaseAuth.getInstance()
            if (auth.currentUser == null) {
                auth.signInAnonymously()
                    .addOnSuccessListener { Log.d("App", "Anon sign-in OK uid=${'$'}{it.user?.uid}") }
                    .addOnFailureListener { Log.e("App", "Anon sign-in FAIL", it) }
            } else {
                Log.d("App", "Usuário já logado uid=${'$'}{auth.currentUser?.uid}")
            }
            Log.d("App", "Firebase inicializado")
        } catch (e: Exception) {
            Log.e("App", "Falha ao inicializar Firebase", e)
        }
    }
}

// Extensão opcional para usar emulador se variável de sistema setada (evita crash em prod)
private fun FirebaseFirestore.useEmulatorIfConfigured() {
    val host = System.getenv("FIRESTORE_EMULATOR_HOST") ?: return
    val parts = host.split(":")
    if (parts.size == 2) {
        val port = parts[1].toIntOrNull() ?: return
        this.useEmulator(parts[0], port)
    }
}