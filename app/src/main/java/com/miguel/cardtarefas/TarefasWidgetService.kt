package com.miguel.cardtarefas

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import android.widget.RemoteViewsService

// Segundo widget: mostra os COMPROMISSOS da Agenda (pendentes, por data).
class TarefasWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = AgendaFactory(applicationContext)
}

private data class ARow(val id: String, val texto: String, val data: String)

class AgendaFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private var rows: List<ARow> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        val store = Store(context)
        if (!store.logado) { rows = emptyList(); return }
        rows = try {
            val uid = store.uid!!
            val idToken = Api.idTokenValido(store)
            Api.listarAgenda(idToken, uid)
                .filter { !it.concluida && !it.excluida }
                .sortedWith(compareBy<Compromisso>({ if (it.data.isEmpty()) "9999-99-99" else it.data }, { it.hora }, { it.criadaEm ?: "" }))
                .map { ARow(it.id, it.descricao, it.data) }
        } catch (_: Throwable) {
            rows
        }
    }

    private fun hojeIso(): String {
        val c = java.util.Calendar.getInstance()
        return "%04d-%02d-%02d".format(c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH))
    }
    private fun rotulo(iso: String, hoje: String): String {
        if (iso.isEmpty()) return "Sem data"
        val p = iso.split("-"); val ddmm = if (p.size == 3) p[2] + "/" + p[1] else iso
        return when { iso == hoje -> "Hoje"; iso < hoje -> "Atrasado"; else -> ddmm }
    }

    override fun onDestroy() { rows = emptyList() }
    override fun getCount(): Int = rows.size

    override fun getViewAt(position: Int): RemoteViews {
        if (position >= rows.size) return RemoteViews(context.packageName, R.layout.widget_item_agenda)
        val r = rows[position]
        val rv = RemoteViews(context.packageName, R.layout.widget_item_agenda)
        rv.setTextViewText(R.id.item_texto, r.texto)
        val hoje = hojeIso()
        rv.setTextViewText(R.id.item_valor, rotulo(r.data, hoje))
        val atrasado = r.data.isNotEmpty() && r.data < hoje
        rv.setTextColor(R.id.item_valor, if (atrasado) Color.parseColor("#F38BA8") else Color.parseColor("#9A9AA2"))
        val fill = Intent().apply { putExtra("abrir_compromisso", r.id) }
        rv.setOnClickFillInIntent(R.id.item_root, fill)
        return rv
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = false
}
