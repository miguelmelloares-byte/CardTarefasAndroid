package com.miguel.cardtarefas

import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService

class ListWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return ListFactory(applicationContext)
    }
}

// Fornece as linhas do widget. onDataSetChanged roda numa thread do sistema,
// entao pode buscar os dados na rede (Firestore) de forma sincrona.
class ListFactory(private val context: android.content.Context) : RemoteViewsService.RemoteViewsFactory {

    private var itens: List<Tarefa> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        val store = Store(context)
        if (!store.logado) {
            itens = emptyList()
            return
        }
        itens = try {
            val uid = store.uid!!
            val idToken = Api.idTokenValido(store)
            val grupo = store.grupoWidget
            Api.listarTarefas(idToken, uid)
                .filter { !it.excluida && !it.concluida }
                .filter { grupo.isEmpty() || it.grupo.equals(grupo, ignoreCase = true) }
                .sortedBy { it.descricao.lowercase() }
        } catch (_: Throwable) {
            itens   // mantem o que ja tinha se a rede falhar
        }
    }

    override fun onDestroy() { itens = emptyList() }

    override fun getCount(): Int = itens.size

    override fun getViewAt(position: Int): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.widget_item)
        if (position >= itens.size) return rv
        val t = itens[position]
        rv.setTextViewText(R.id.item_texto, t.descricao)
        rv.setImageViewResource(R.id.item_check, R.drawable.ic_check_off)

        // ao tocar, preenche o template com o id do item para marcar
        val fill = Intent().apply {
            putExtra(ListWidget.EXTRA_ID, t.id)
            putExtra(ListWidget.EXTRA_CONCLUIDA, t.concluida)
        }
        rv.setOnClickFillInIntent(R.id.item_root, fill)
        return rv
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true
}
