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

// Widget de tela inicial: mostra os itens pendentes de um grupo (ex.: "Mercado")
// com caixinhas. Tocar em um item marca como pego e grava direto na nuvem.
class ListWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) {
            desenhar(context, appWidgetManager, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TOGGLE -> {
                val tid = intent.getStringExtra(EXTRA_ID) ?: return
                val jaConcluida = intent.getBooleanExtra(EXTRA_CONCLUIDA, false)
                val pending = goAsync()
                Thread {
                    try {
                        val store = Store(context)
                        if (store.logado) {
                            val uid = store.uid!!
                            val idToken = Api.idTokenValido(store)
                            Api.marcar(idToken, uid, tid, !jaConcluida)
                        }
                    } catch (_: Throwable) {
                    } finally {
                        atualizar(context)
                        pending.finish()
                    }
                }.start()
            }
            ACTION_REFRESH -> atualizar(context)
        }
    }

    private fun desenhar(context: Context, mgr: AppWidgetManager, id: Int) {
        val rv = RemoteViews(context.packageName, R.layout.widget_list)

        // titulo: se so ha um grupo selecionado, mostra o nome dele; senao "Listas"
        val sel = Store(context).gruposWidget
        val titulo = if (sel.size == 1) sel.first() else "Listas"
        rv.setTextViewText(R.id.widget_titulo, titulo)

        // adaptador da lista (RemoteViewsService)
        val svc = Intent(context, ListWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            data = Uri.parse("cardtarefas://widget/$id")
        }
        rv.setRemoteAdapter(R.id.widget_lista, svc)
        rv.setEmptyView(R.id.widget_lista, R.id.widget_vazio)

        // botao atualizar
        rv.setOnClickPendingIntent(R.id.widget_refresh, broadcast(context, ACTION_REFRESH, id))

        // botao adicionar -> abre o app
        val abrir = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        rv.setOnClickPendingIntent(
            R.id.widget_add,
            PendingIntent.getActivity(context, id, abrir, flags(false))
        )

        // template de clique dos itens (marcar/desmarcar)
        val toggle = Intent(context, ListWidget::class.java).apply {
            action = ACTION_TOGGLE
            data = Uri.parse("cardtarefas://toggle/$id")
        }
        rv.setPendingIntentTemplate(
            R.id.widget_lista,
            PendingIntent.getBroadcast(context, id, toggle, flags(true))
        )

        mgr.updateAppWidget(id, rv)
        mgr.notifyAppWidgetViewDataChanged(id, R.id.widget_lista)
    }

    private fun broadcast(context: Context, action: String, id: Int): PendingIntent {
        val i = Intent(context, ListWidget::class.java).apply {
            this.action = action
            data = Uri.parse("cardtarefas://$action/$id")
        }
        return PendingIntent.getBroadcast(context, id, i, flags(false))
    }

    companion object {
        const val ACTION_TOGGLE = "com.miguel.cardtarefas.TOGGLE"
        const val ACTION_REFRESH = "com.miguel.cardtarefas.REFRESH"
        const val EXTRA_ID = "extra_id"
        const val EXTRA_CONCLUIDA = "extra_concluida"

        // Redesenha e recarrega todos os widgets na tela inicial.
        fun atualizar(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val comp = ComponentName(context, ListWidget::class.java)
            val ids = mgr.getAppWidgetIds(comp)
            if (ids.isEmpty()) return
            val intent = Intent(context, ListWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }

        private fun flags(mutable: Boolean): Int {
            var f = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                f = f or if (mutable) PendingIntent.FLAG_MUTABLE
                else PendingIntent.FLAG_IMMUTABLE
            }
            return f
        }
    }
}
