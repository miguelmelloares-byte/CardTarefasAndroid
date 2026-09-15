package com.miguel.cardtarefas

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService

class ListWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return ListFactory(applicationContext)
    }
}

// Uma linha do widget: um cabecalho de grupo OU um item marcavel.
private data class WRow(
    val header: Boolean,
    val texto: String,
    val id: String = "",
    val concluida: Boolean = false
)

// Fornece as linhas do widget. onDataSetChanged roda numa thread do sistema,
// entao pode buscar os dados na rede (Firestore) de forma sincrona.
class ListFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private var rows: List<WRow> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        val store = Store(context)
        if (!store.logado) {
            rows = emptyList()
            return
        }
        rows = try {
            val uid = store.uid!!
            val idToken = Api.idTokenValido(store)

            // apenas grupos do tipo Lista podem aparecer no widget
            val listaGrupos = Api.listarGruposLista(idToken, uid)
            val selecionados = store.gruposWidget
            val efetivos = if (selecionados.isEmpty()) listaGrupos
                           else listaGrupos.filter { selecionados.contains(it) }

            val tarefas = Api.listarTarefas(idToken, uid)
                .filter { !it.excluida && !it.concluida }

            val out = ArrayList<WRow>()
            for (g in efetivos) {
                val itens = tarefas
                    .filter { it.grupo.equals(g, ignoreCase = true) }
                    .sortedBy { it.descricao.lowercase() }
                if (itens.isEmpty()) continue
                // com mais de um grupo, mostra o titulo de cada um
                if (efetivos.size > 1) out.add(WRow(header = true, texto = g))
                for (t in itens) out.add(WRow(header = false, texto = t.descricao, id = t.id,
                                              concluida = t.concluida))
            }
            out
        } catch (_: Throwable) {
            rows   // mantem o que ja tinha se a rede falhar
        }
    }

    override fun onDestroy() { rows = emptyList() }

    override fun getCount(): Int = rows.size

    override fun getViewAt(position: Int): RemoteViews {
        if (position >= rows.size) return RemoteViews(context.packageName, R.layout.widget_item)
        val r = rows[position]
        if (r.header) {
            val rv = RemoteViews(context.packageName, R.layout.widget_group_header)
            rv.setTextViewText(R.id.header_texto, r.texto)
            return rv
        }
        val rv = RemoteViews(context.packageName, R.layout.widget_item)
        rv.setTextViewText(R.id.item_texto, r.texto)
        rv.setImageViewResource(R.id.item_check, R.drawable.ic_check_off)
        val fill = Intent().apply {
            putExtra(ListWidget.EXTRA_ID, r.id)
            putExtra(ListWidget.EXTRA_CONCLUIDA, r.concluida)
        }
        rv.setOnClickFillInIntent(R.id.item_root, fill)
        return rv
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 2
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = false
}
