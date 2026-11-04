package com.miguel.cardtarefas

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

// Projetado (limite) do mes de uma lista: frase de quanto falta ou quanto passou, e o 2o aviso
// depois de cada lancamento. O mes e' a aba = mes em que a fatura e' paga (regime de caixa; o
// corte da fatura ja decide a aba quando a compra vem da notificacao).
object Limite {
    fun frase(gasto: Double, proj: Double): String {
        val dif = proj - gasto
        val pct = if (proj != 0.0) kotlin.math.abs(dif) / proj * 100 else 0.0
        val pc = String.format(java.util.Locale.US, "%.1f", pct).replace(".", ",")
        return if (dif >= 0) "Faltam R$ ${fmtValor(dif)} ($pc%) para o projetado de R$ ${fmtValor(proj)}. Lançado: R$ ${fmtValor(gasto)}."
        else "Projetado ultrapassado em R$ ${fmtValor(-dif)} ($pc%). Lançado: R$ ${fmtValor(gasto)} de R$ ${fmtValor(proj)}."
    }

    fun avisar(ctx: Context, idToken: String, uid: String, lista: String, aba: String, saldo: Double) {
        try {
            val proj = Api.lerProjetado(idToken, uid)[lista]?.get(aba) ?: return
            val gasto = kotlin.math.abs(saldo)
            val p = aba.split("-"); val mes = if (p.size == 2) p[1] + "/" + p[0] else aba
            val titulo = (if (gasto <= proj) "Limite de $mes" else "⚠ Limite de $mes estourado") + " · $lista"
            Notificador.garantirCanal(ctx)
            val n = NotificationCompat.Builder(ctx, "listas")
                .setSmallIcon(R.drawable.ic_check_on)
                .setContentTitle(titulo)
                .setContentText(frase(gasto, proj))
                .setStyle(NotificationCompat.BigTextStyle().bigText(frase(gasto, proj)))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            NotificationManagerCompat.from(ctx).notify((System.currentTimeMillis() % 100000).toInt() + 7, n)
        } catch (_: Throwable) { }
    }
}
