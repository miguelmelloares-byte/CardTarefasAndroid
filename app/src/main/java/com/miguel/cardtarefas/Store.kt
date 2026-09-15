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

    // grupo que o widget da tela inicial mostra (ex.: "Mercado"). Vazio = todos.
    var grupoWidget: String
        get() = sp.getString("grupo_widget", "Mercado") ?: "Mercado"
        set(v) { sp.edit().putString("grupo_widget", v).apply() }

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
