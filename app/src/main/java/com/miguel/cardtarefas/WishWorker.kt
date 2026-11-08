package com.miguel.cardtarefas

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

// Wishlist: 1x por dia (7:30) confere se o cartao virou para a fatura do mes previsto de algum item
// e cria o compromisso "Eba..." na agenda (WishAgenda); o aviso de compromisso novo sai na hora.
class WishWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val ctx = applicationContext; val store = Store(ctx)
        if (!store.logado) return Result.success()
        try {
            val uid = store.uid!!
            val (tok, novoRt) = Api.refresh(store.refreshToken!!)
            if (novoRt.isNotEmpty() && novoRt != store.refreshToken) store.refreshToken = novoRt
            if (WishAgenda.verificar(store, tok, uid, Api.listarWishlist(tok, uid)) > 0)
                Notificador.verificarAgenda(ctx, Api.listarAgenda(tok, uid).filter { !it.excluida })
        } catch (_: Throwable) { }
        return Result.success()
    }

    companion object {
        // 1x por dia, as 7:30 (a virada do cartao acontece 1x por mes; nao precisa checar mais vezes)
        fun agendar(ctx: Context) {
            val agora = java.util.Calendar.getInstance()
            val alvo = (agora.clone() as java.util.Calendar).apply { set(java.util.Calendar.HOUR_OF_DAY, 7); set(java.util.Calendar.MINUTE, 30); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0) }
            if (!alvo.after(agora)) alvo.add(java.util.Calendar.DAY_OF_MONTH, 1)
            val req = PeriodicWorkRequestBuilder<WishWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(alvo.timeInMillis - agora.timeInMillis, TimeUnit.MILLISECONDS).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("wishlist_aviso", ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, req)
        }
    }
}
