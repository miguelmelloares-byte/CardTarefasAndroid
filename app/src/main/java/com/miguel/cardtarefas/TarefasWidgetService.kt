package com.miguel.cardtarefas

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import android.widget.RemoteViewsService

class TarefasWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = TarefasFactory(applicationContext)
}

private data class TRow(val id: String, val texto: String, val data: String?, val concluida: Boolean)

class TarefasFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private var rows: List<TRow> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        val store = Store(context)
        if (!store.logado) { rows = emptyList(); return }
        rows = try {
            val uid = store.uid!!
            val idToken = Api.idTokenValido(store)
            val tarefaNomes = Api.listarGruposFull(idToken, uid)
                .filter { it.tipo == "tarefas" }.map { it.nome.lowercase() }.toSet()
            val tarefas = Api.listarTarefasAtivas(idToken, uid)
            tarefas.filter { !it.concluida && it.grupo.lowercase() in tarefaNomes }
                .sortedWith(compareBy<Tarefa>({ it.data ?: "9999-99-99" }, { rank(it.prioridade) }, { it.criadaEm ?: "" }))
                .map { TRow(it.id, it.descricao, it.data, it.concluida) }
        } catch (_: Throwable) {
            rows
        }
    }

    private fun rank(p: String): Int = when (p) { "Alta" -> 0; "Baixa" -> 2; else -> 1 }
    private fun ddmm(iso: String?): String {
        if (iso.isNullOrBlank()) return ""
        val p = iso.split("-"); return if (p.size == 3) p[2] + "/" + p[1] else iso
    }
    private fun hojeIso(): String {
        val c = java.util.Calendar.getInstance()
        return "%04d-%02d-%02d".format(c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH))
    }

    override fun onDestroy() { rows = emptyList() }
    override fun getCount(): Int = rows.size

    override fun getViewAt(position: Int): RemoteViews {
        if (position >= rows.size) return RemoteViews(context.packageName, R.layout.widget_item)
        val r = rows[position]
        val rv = RemoteViews(context.packageName, R.layout.widget_item)
        rv.setTextViewText(R.id.item_texto, r.texto)
        rv.setImageViewResource(R.id.item_check, R.drawable.ic_check_off)
        rv.setTextViewText(R.id.item_valor, ddmm(r.data))
        val atrasado = !r.data.isNullOrBlank() && r.data < hojeIso()
        rv.setTextColor(R.id.item_valor, if (atrasado) Color.parseColor("#F38BA8") else Color.parseColor("#9A9AA2"))
        val fill = Intent().apply {
            putExtra(ListWidget.EXTRA_ID, r.id)
            putExtra(ListWidget.EXTRA_CONCLUIDA, r.concluida)
        }
        rv.setOnClickFillInIntent(R.id.item_root, fill)
        return rv
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = false
}
