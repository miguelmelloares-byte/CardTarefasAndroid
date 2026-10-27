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

    // quando rodou a ultima limpeza de tarefas apagadas antigas (ms epoch). 0 = nunca.
    var ultimaLimpeza: Long
        get() = sp.getLong("ultima_limpeza", 0L)
        set(v) { sp.edit().putLong("ultima_limpeza", v).apply() }

    // codigo local para completar a decifragem da chave da IA (ex.: "murilo").
    // Fica so' neste aparelho; nunca vai para o codigo nem para o banco.
    var codigoIa: String?
        get() = sp.getString("codigo_ia", null)
        set(v) { sp.edit().putString("codigo_ia", v).apply() }

    // quantos itens estao sendo processados pela IA agora (para o icone "em transito").
    var iaPendentes: Int
        get() = sp.getInt("ia_pendentes", 0)
        set(v) { sp.edit().putInt("ia_pendentes", v.coerceAtLeast(0)).apply() }

    // mes exibido no widget de listas (navegacao por ano-mes "AAAA-MM"; "" = mes atual)
    var widgetMes: String
        get() = sp.getString("widget_mes", "") ?: ""
        set(v) { sp.edit().putString("widget_mes", v).apply() }

    // mes efetivamente exibido no widget (para o titulo e para a navegacao saber onde esta)
    var widgetMesMostrado: String
        get() = sp.getString("widget_mes_mostrado", "") ?: ""
        set(v) { sp.edit().putString("widget_mes_mostrado", v).apply() }

    // meses (abas) que realmente existem nos grupos do widget, em ordem crescente,
    // separados por quebra de linha. A navegacao de meses so' anda entre eles.
    var widgetMeses: String
        get() = sp.getString("widget_meses", "") ?: ""
        set(v) { sp.edit().putString("widget_meses", v).apply() }

    // leitura automatica de notificacoes do banco (lanca na lista)
    var notifLerLigado: Boolean
        get() = sp.getBoolean("notif_ler_ligado", false)
        set(v) { sp.edit().putBoolean("notif_ler_ligado", v).apply() }

    fun notifJaProcessou(chave: String): Boolean =
        (sp.getString("notif_chaves", "") ?: "").split("\n").contains(chave)

    fun notifRegistrar(chave: String) {
        val cur = (sp.getString("notif_chaves", "") ?: "").split("\n").filter { it.isNotEmpty() }.toMutableList()
        cur.add(chave)
        val keep = if (cur.size > 40) cur.takeLast(40) else cur
        sp.edit().putString("notif_chaves", keep.joinToString("\n")).apply()
    }

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
