package com.miguel.cardtarefas

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

// Verifica as listas em segundo plano (a cada ~15 min) e emite notificacoes
// quando algo mudou desde a ultima checagem.
class NotifWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    override fun doWork(): Result {
        val ctx = applicationContext
        val store = Store(ctx)
        if (!store.logado) return Result.success()
        return try {
            val uid = store.uid!!
            val (idToken, novoRt) = Api.refresh(store.refreshToken!!)
            if (novoRt.isNotEmpty() && novoRt != store.refreshToken) store.refreshToken = novoRt
            val grupos = Api.listarGruposFull(idToken, uid)
            val tarefas = Api.listarTarefas(idToken, uid)   // inclui excluidos p/ detectar remocao
            Notificador.verificar(ctx, grupos, tarefas)
            Result.success()
        } catch (_: Throwable) {
            Result.retry()
        }
    }

    companion object {
        fun agendar(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<NotifWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                "notif", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
