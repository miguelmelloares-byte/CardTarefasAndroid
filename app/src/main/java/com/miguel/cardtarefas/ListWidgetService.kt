package com.miguel.cardtarefas

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import kotlin.math.abs

// Formata um numero no estilo brasileiro (1.234,56).
fun fmtValor(v: Double): String {
    val neg = v < 0
    // Locale.US garante ponto como separador decimal (senao o proprio Android
    // ja usaria virgula no pt-BR e quebraria o split abaixo).
    val s = String.format(java.util.Locale.US, "%.2f", abs(v))
    val parts = s.split(".")
    val inteiro = parts[0]
    val dec = parts.getOrElse(1) { "00" }
    val agrupado = inteiro.reversed().chunked(3).joinToString(".").reversed()
    return (if (neg) "-" else "") + agrupado + "," + dec
}

// Hash SHA-256 (hex) da senha, com prefixo fixo (igual ao app do PC).
fun hashSenha(s: String): String {
    val t = s.trim()
    if (t.isEmpty()) return ""
    val bytes = java.security.MessageDigest.getInstance("SHA-256")
        .digest(("cardtarefas:" + t).toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
}

// Converte texto em numero (aceita virgula e negativo). Vazio -> null.
fun parseValor(s: String): Double? {
    var t = s.trim().replace(" ", "").replace("R$", "")
    if (t.isEmpty()) return null
    t = if (t.contains(",")) t.replace(".", "").replace(",", ".") else t
    return t.toDoubleOrNull()
}

class ListWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return ListFactory(applicationContext)
    }
}

// Uma linha do widget: cabecalho de grupo (com saldo) OU item marcavel (com valor).
private data class WRow(
    val header: Boolean,
    val texto: String,
    val saldo: Double = 0.0,
    val id: String = "",
    val concluida: Boolean = false,
    val temValor: Boolean = false,
    val valor: Double = 0.0
)

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

            val grupos = Api.listarGruposFull(idToken, uid).filter { it.tipo == "lista" }
            val selecionados = store.gruposWidget
            val efetivos = if (selecionados.isEmpty()) grupos
                           else grupos.filter { selecionados.contains(it.nome) }

            val tarefas = Api.listarTarefasAtivas(idToken, uid)
            val hoje = ymHojeW()

            val out = ArrayList<WRow>()
            for (g in efetivos) {
                // meses (abas) do grupo; default = mes atual
                val ms = g.abas.map { it.id }.filter { ehMesIdW(it) }.sorted()
                val meses = if (ms.isEmpty()) listOf(hoje) else ms
                val ativo = meses.last()   // sempre o mes mais recente existente
                val primeiro = meses.first()
                val doGrupo = tarefas.filter { it.grupo.equals(g.nome, ignoreCase = true) }
                val doMes = doGrupo.filter {
                    val m = if (it.aba != null && meses.contains(it.aba)) it.aba else primeiro
                    m == ativo
                }
                val saldo = doMes.mapNotNull { it.valor }.sum()
                val pendentes = doMes.filter { !it.concluida }
                    .sortedWith(compareBy<Tarefa> { it.ordem ?: 1e15 }.thenByDescending { it.criadaEm ?: "" })
                out.add(WRow(header = true, texto = g.nome + "  (" + mesLabelW(ativo) + ")", saldo = saldo))
                for (t in pendentes) {
                    out.add(WRow(header = false, texto = t.descricao, id = t.id,
                                 concluida = t.concluida,
                                 temValor = t.valor != null, valor = t.valor ?: 0.0))
                }
            }
            out
        } catch (_: Throwable) {
            rows
        }
    }

    private fun ymHojeW(): String {
        val c = java.util.Calendar.getInstance()
        return "%04d-%02d".format(c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1)
    }
    private fun ehMesIdW(id: String): Boolean = Regex("^\\d{4}-\\d{2}$").matches(id)
    private fun mesLabelW(ym: String): String { val p = ym.split("-"); return if (p.size == 2) p[1] + "/" + p[0] else ym }

    override fun onDestroy() { rows = emptyList() }

    override fun getCount(): Int = rows.size

    override fun getViewAt(position: Int): RemoteViews {
        if (position >= rows.size) return RemoteViews(context.packageName, R.layout.widget_item)
        val r = rows[position]
        if (r.header) {
            val rv = RemoteViews(context.packageName, R.layout.widget_group_header)
            rv.setTextViewText(R.id.header_texto, r.texto)
            rv.setTextViewText(R.id.header_saldo, "Saldo: " + fmtValor(r.saldo))
            rv.setTextColor(R.id.header_saldo, corValor(r.saldo))
            return rv
        }
        val rv = RemoteViews(context.packageName, R.layout.widget_item)
        rv.setTextViewText(R.id.item_texto, r.texto)
        rv.setImageViewResource(R.id.item_check, R.drawable.ic_check_off)
        if (r.temValor) {
            rv.setTextViewText(R.id.item_valor, fmtValor(r.valor))
            rv.setTextColor(R.id.item_valor, corValor(r.valor))
        } else {
            rv.setTextViewText(R.id.item_valor, "")
        }
        val fill = Intent().apply {
            putExtra(ListWidget.EXTRA_ID, r.id)
            putExtra(ListWidget.EXTRA_CONCLUIDA, r.concluida)
        }
        rv.setOnClickFillInIntent(R.id.item_root, fill)
        return rv
    }

    private fun corValor(v: Double): Int =
        if (v < 0) Color.parseColor("#F38BA8") else Color.parseColor("#A6E3A1")

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 2
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = false
}
