package com.miguel.cardtarefas

import android.content.Context

// Guarda o token de acesso continuo (refresh_token), o uid e as preferencias locais.
// Simples e suficiente: os dados de verdade ficam na nuvem; aqui so' o suficiente
// para reconectar sem pedir a senha de novo.
class Store(context: Context) {
    private val sp = context.applicationContext
        .getSharedPreferences("cardtarefas", Context.MODE_PRIVATE)

    var refreshToken: String?
        get() = sp.getString("refresh_token", null)
        set(v) { sp.edit().putString("refresh_token", v).apply() }

    var uid: String?
        get() = sp.getString("uid", null)
        set(v) { sp.edit().putString("uid", v).apply() }

    var email: String?
        get() = sp.getString("email", null)
        set(v) { sp.edit().putString("email", v).apply() }

    // grupos (tipo Lista) que o widget da tela inicial mostra. Vazio = todos os grupos-lista.
    var gruposWidget: Set<String>
        get() {
            val s = sp.getStringSet("grupos_widget", null)
            if (s != null) return s
            // migracao do formato antigo (um unico grupo em texto)
            val antigo = sp.getString("grupo_widget", null)
            return if (antigo.isNullOrEmpty()) emptySet() else setOf(antigo)
        }
        set(v) { sp.edit().putStringSet("grupos_widget", v).apply() }

    // hash da senha das tarefas que este aparelho "lembrou" (desbloqueio persistente)
    var tarefasUnlockHash: String?
        get() = sp.getString("tarefas_unlock_hash", null)
        set(v) { sp.edit().putString("tarefas_unlock_hash", v).apply() }

    val logado: Boolean
        get() = !refreshToken.isNullOrEmpty() && !uid.isNullOrEmpty()

    fun salvarLogin(refreshToken: String, uid: String, email: String) {
        sp.edit()
            .putString("refresh_token", refreshToken)
            .putString("uid", uid)
            .putString("email", email)
            .apply()
    }

    fun sair() {
        sp.edit()
            .remove("refresh_token")
            .remove("uid")
            .apply()
    }
}
