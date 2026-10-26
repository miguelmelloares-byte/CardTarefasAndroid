package com.miguel.cardtarefas

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews

// Segundo widget da tela inicial: mostra as TAREFAS pendentes (datadas, por
// prioridade). Tocar numa tarefa marca como feita. Tem o clipe da IA.
class TarefasWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) desenhar(context, mgr, id)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TOGGLE_T -> {
                val tid = intent.getStringExtra(ListWidget.EXTRA_ID) ?: return
                val ja = intent.getBooleanExtra(ListWidget.EXTRA_CONCLUIDA, false)
                val pending = goAsync()
                Thread {
                    try {
                        val store = Store(context)
                        if (store.logado) {
                            val uid = store.uid!!
                            val idToken = Api.idTokenValido(store)
                            Api.marcarAgenda(idToken, uid, tid, !ja)
                        }
                    } catch (_: Throwable) {
                    } finally {
                        atualizar(context)
                        pending.finish()
                    }
                }.start()
            }
            ACTION_REFRESH_T -> atualizar(context)
        }
    }

    private fun desenhar(context: Context, mgr: AppWidgetManager, id: Int) {
        val rv = RemoteViews(context.packageName, R.layout.widget_tarefas)
        rv.setTextViewText(R.id.widget_titulo, "Agenda")

        val svc = Intent(context, TarefasWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            data = Uri.parse("cardtarefas://twidget/$id/${System.currentTimeMillis()}")
        }
        rv.setRemoteAdapter(R.id.widget_lista, svc)
        rv.setEmptyView(R.id.widget_lista, R.id.widget_vazio)

        rv.setOnClickPendingIntent(R.id.widget_refresh, broadcast(context, ACTION_REFRESH_T, id))

        val abrir = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("abrir_add", "agenda")
        rv.setOnClickPendingIntent(R.id.widget_add,
            PendingIntent.getActivity(context, id + 300000, abrir, flags(false)))

        val pend = Store(context).iaPendentes
        rv.setViewVisibility(R.id.widget_transito, if (pend > 0) android.view.View.VISIBLE else android.view.View.GONE)

        val toggle = Intent(context, TarefasWidget::class.java).apply {
            action = ACTION_TOGGLE_T
            data = Uri.parse("cardtarefas://ttoggle/$id")
        }
        rv.setPendingIntentTemplate(R.id.widget_lista,
            PendingIntent.getBroadcast(context, id, toggle, flags(true)))

        mgr.updateAppWidget(id, rv)
        mgr.notifyAppWidgetViewDataChanged(id, R.id.widget_lista)
    }

    private fun broadcast(context: Context, action: String, id: Int): PendingIntent {
        val i = Intent(context, TarefasWidget::class.java).apply {
            this.action = action
            data = Uri.parse("cardtarefas://$action/$id")
        }
        return PendingIntent.getBroadcast(context, id, i, flags(false))
    }

    companion object {
        const val ACTION_TOGGLE_T = "com.miguel.cardtarefas.TOGGLE_T"
        const val ACTION_REFRESH_T = "com.miguel.cardtarefas.REFRESH_T"

        fun atualizar(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val comp = ComponentName(context, TarefasWidget::class.java)
            val ids = mgr.getAppWidgetIds(comp)
            if (ids.isEmpty()) return
            val intent = Intent(context, TarefasWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }

        private fun flags(mutable: Boolean): Int {
            var f = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                f = f or if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
            }
            return f
        }
    }
}
