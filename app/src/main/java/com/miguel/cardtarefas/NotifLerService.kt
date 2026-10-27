package com.miguel.cardtarefas

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

// Le notificacoes do aparelho e, quando reconhece uma compra do cartao (Santander,
// final 5261), lanca o item (estabelecimento + valor) na aba mais recente da lista.
// So' atua se o usuario ligou a opcao E concedeu "Acesso a notificacoes".
class NotifLerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            val store = Store(applicationContext)
            if (!store.notifLerLigado || !store.logado) return
            val ex = sbn.notification?.extras ?: return
            val titulo = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val texto = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: ""
            val corpo = "$titulo $texto"

            // 1) ESTORNO: se reconhecer um estorno, remove o item da lista.
            val e = RE_ESTORNO.find(corpo)
            if (e != null) {
                val cartao = e.groupValues[1]
                if (cartao != CARTAO) return
                val valor = parseValor(e.groupValues[2]) ?: return
                val desc = e.groupValues[3].trim().ifEmpty { return }
                val chave = "EST|$cartao|${e.groupValues[2]}|$desc|${sbn.postTime}"
                if (store.notifJaProcessou(chave)) return
                store.notifRegistrar(chave)
                Thread { try { remover(store, desc, valor) } catch (_: Throwable) {} }.start()
                return
            }
            // Se a notificacao fala de estorno mas o padrao nao bateu, nao deixa
            // o padrao de compra lanca-la por engano.
            if (corpo.contains("estorn", true)) return

            // 2) COMPRA APROVADA: lanca na lista.
            val m = RE.find(corpo) ?: return
            val cartao = m.groupValues[1]
            if (cartao != CARTAO) return
            val valor = parseValor(m.groupValues[2]) ?: return
            val desc = m.groupValues[4].trim().ifEmpty { return }
            val chave = "$cartao|${m.groupValues[2]}|${m.groupValues[3]}|$desc"
            if (store.notifJaProcessou(chave)) return
            store.notifRegistrar(chave)
            Thread {
                try { inserir(store, desc, valor) } catch (_: Throwable) {}
            }.start()
        } catch (_: Throwable) {}
    }

    private fun inserir(store: Store, desc: String, valor: Double) {
        val uid = store.uid ?: return
        val tok = Api.idTokenValido(store)
        val listas = Api.listarGruposFull(tok, uid).filter { it.tipo == "lista" }
        val grupo = (listas.firstOrNull { it.nome.equals("Acerto", true) } ?: listas.firstOrNull()) ?: return
        val aba = grupo.abas.maxByOrNull { it.id }?.id ?: mesAtual()
        val ativas = Api.listarTarefasAtivas(tok, uid)
        val menor = ativas.filter { it.grupo == grupo.nome && (it.aba ?: "") == aba && !it.concluida }
            .mapNotNull { it.ordem }.minOrNull() ?: 0.0
        val t = Tarefa(id = Api.novoId(), grupo = grupo.nome, aba = aba, descricao = desc, data = hojeDataIso(), concluida = false,
            criadaEm = isoAgora(), atualizadoEm = Api.agoraMs(), excluida = false, valor = valor, ordem = menor - 1.0)
        Api.gravarTarefa(tok, uid, t)
        ListWidget.atualizar(applicationContext)
        avisar(desc, valor)
    }

    private fun remover(store: Store, desc: String, valor: Double) {
        val uid = store.uid ?: return
        val tok = Api.idTokenValido(store)
        val listas = Api.listarGruposFull(tok, uid).filter { it.tipo == "lista" }.map { it.nome }.toSet()
        if (listas.isEmpty()) return
        val ativas = Api.listarTarefasAtivas(tok, uid)
        val alvo = ativas
            .filter { it.grupo in listas && !it.excluida }
            .filter { mesmoTexto(it.descricao, desc) && casaValor(it.valor, valor) }
            .maxByOrNull { it.atualizadoEm }
        if (alvo == null) { avisarEstorno(desc, valor, false); return }
        Api.gravarTarefa(tok, uid, alvo.copy(excluida = true, atualizadoEm = Api.agoraMs()))
        ListWidget.atualizar(applicationContext)
        avisarEstorno(desc, valor, true)
    }

    private fun mesmoTexto(a: String, b: String): Boolean {
        fun norm(s: String) = s.trim().lowercase().replace(Regex("\\s+"), " ")
        val x = norm(a); val y = norm(b)
        return x == y || (y.length >= 3 && (x.contains(y) || y.contains(x)))
    }
    private fun casaValor(a: Double?, b: Double): Boolean =
        a != null && kotlin.math.abs(a - b) < 0.005

    private fun avisarEstorno(desc: String, valor: Double, removido: Boolean) {
        try {
            Notificador.garantirCanal(applicationContext)
            val titulo = if (removido) "Estorno: item removido da lista" else "Estorno recebido (item nao encontrado)"
            val n = NotificationCompat.Builder(applicationContext, "listas")
                .setSmallIcon(R.drawable.ic_check_on)
                .setContentTitle(titulo)
                .setContentText("$desc \u2014 R$ " + fmtValor(valor))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            NotificationManagerCompat.from(applicationContext).notify((System.currentTimeMillis() % 100000).toInt() + 1, n)
        } catch (_: Throwable) {}
    }

    private fun avisar(desc: String, valor: Double) {
        try {
            Notificador.garantirCanal(applicationContext)
            val n = NotificationCompat.Builder(applicationContext, "listas")
                .setSmallIcon(R.drawable.ic_check_on)
                .setContentTitle("Compra lançada na lista")
                .setContentText("$desc — R$ " + fmtValor(valor))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            NotificationManagerCompat.from(applicationContext).notify((System.currentTimeMillis() % 100000).toInt(), n)
        } catch (_: Throwable) {}
    }

    private fun hojeDataIso(): String {
        val c = java.util.Calendar.getInstance()
        return "%04d-%02d-%02d".format(c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH))
    }
    private fun mesAtual(): String {
        val c = java.util.Calendar.getInstance()
        return "%04d-%02d".format(c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1)
    }
    private fun isoAgora(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).format(java.util.Date())

    companion object {
        private const val CARTAO = "5261"
        // "Compra no cartao final 5261, de R$ 1.081,01, em 02/08/26, as 15:46, em PETZ ALTO DA BO, aprovada."
        private val RE = Regex(
            "cart.o final (\\d{3,}).*?R\\\$\\s*([\\d.,]+).*?em\\s*(\\d{2}/\\d{2}/\\d{2}).*?em\\s*(.+?),\\s*aprovada",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        // PROVISORIO: padrao de estorno (espelhando o formato da compra: ... data ... em ESTABELECIMENTO).
        // Ajustar assim que tivermos o texto real da notificacao de estorno do Santander.
        private val RE_ESTORNO = Regex(
            "estorn\\w*.*?cart.o final (\\d{3,}).*?R\\\$\\s*([\\d.,]+).*?\\d{2}/\\d{2}/\\d{2}.*?em\\s*(.+?)\\s*[,.]",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
    }
}
