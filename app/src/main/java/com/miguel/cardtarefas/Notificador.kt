package com.miguel.cardtarefas

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.json.JSONObject
import java.util.Calendar

// Emite notificacoes no celular quando listas marcadas "notificar" mudam.
// Estrategia: compara um retrato (snapshot) do estado anterior com o atual e
// avisa o que foi criado / adicionado / excluido / editado. Marcar concluido
// nao gera notificacao.
object Notificador {

    private const val CANAL = "listas"
    private const val PREF = "notif_snapshot"
    private const val PREF_AGENDA = "notif_snapshot_agenda"
    private var idSeq = 1000

    fun garantirCanal(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CANAL) == null) {
                val c = NotificationChannel(CANAL, "Listas",
                    NotificationManager.IMPORTANCE_DEFAULT)
                c.description = "Mudancas nas listas do NestShare"
                nm.createNotificationChannel(c)
            }
        }
    }

    private data class ItemSnap(val d: String, val v: Double?, val x: Boolean, val g: String = "")

    fun verificar(ctx: Context, grupos: List<GrupoMeta>, tarefasAll: List<Tarefa>) {
        try {
            garantirCanal(ctx)
            val notifNomes = grupos.filter { it.notificar }.map { it.nome.lowercase() }.toSet()
            val todosNomes = grupos.map { it.nome }

            // estado atual (itens dos grupos que notificam)
            val curItens = HashMap<String, ItemSnap>()
            val curGrupoDoItem = HashMap<String, String>()
            for (t in tarefasAll) {
                if (t.grupo.lowercase() in notifNomes && t.id.isNotEmpty()) {
                    curItens[t.id] = ItemSnap(t.descricao, t.valor, t.excluida, t.grupo)
                    curGrupoDoItem[t.id] = t.grupo
                }
            }

            val sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            val salvo = sp.getString("dados", null)

            if (salvo == null) {
                // primeira vez: so' grava a linha de base, sem notificar
                salvar(sp, todosNomes, curItens)
                return
            }

            val (prevGrupos, prevItens) = ler(salvo)

            // listas novas (criadas) que notificam
            for (g in grupos) {
                if (g.notificar && g.nome !in prevGrupos) {
                    notificar(ctx, "Lista \"${g.nome}\" criada no NestShare")
                }
            }

            // itens
            for ((id, cur) in curItens) {
                val prev = prevItens[id]
                val grupo = curGrupoDoItem[id] ?: ""
                if (prev == null) {
                    if (!cur.x) notificar(ctx, msgItem(cur.d, cur.v, "adicionado", grupo))
                } else {
                    if (!prev.x && cur.x) {
                        notificar(ctx, msgItem(cur.d, cur.v, "excluido", grupo))
                    } else if (!cur.x && !prev.x &&
                        (prev.d != cur.d || prev.v != cur.v)) {
                        notificar(ctx, msgItem(cur.d, cur.v, "editado", grupo))
                    }
                }
            }

            // exclusoes: itens que existiam (ativos) no retrato anterior e sumiram
            // do conjunto atual (agora so' lemos as tarefas ativas do servidor).
            for ((id, prev) in prevItens) {
                if (!prev.x && id !in curItens) {
                    notificar(ctx, msgItem(prev.d, prev.v, "excluido", prev.g))
                }
            }

            salvar(sp, todosNomes, curItens)
        } catch (_: Throwable) {
        }
    }

    private fun msgItem(desc: String, valor: Double?, acao: String, grupo: String): String {
        val v = if (valor != null) " de ${fmtValor(valor)} reais" else ""
        return "item \"$desc\"$v $acao na lista \"$grupo\""
    }

    private fun notificar(ctx: Context, texto: String) = enviar(ctx, "NestShare", texto)

    private fun enviar(ctx: Context, titulo: String, texto: String) {
        try {
            val n = NotificationCompat.Builder(ctx, CANAL)
                .setSmallIcon(R.drawable.ic_check_on)
                .setContentTitle(titulo)
                .setContentText(texto)
                .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            NotificationManagerCompat.from(ctx).notify(idSeq++, n)
        } catch (_: SecurityException) {
        } catch (_: Throwable) {
        }
    }

    // ---------------------------------------- LEMBRETES da agenda
    // 1) resumo diario (~7h): compromissos do dia + atrasados (fixo).
    // 2) "X antes": por compromisso com data+hora e notificarAntes definido.
    // Roda no NotifWorker (a cada ~15 min) em cada aparelho.
    fun lembretesAgenda(ctx: Context, comps: List<Compromisso>, store: Store) {
        try {
            garantirCanal(ctx)
            val cal = Calendar.getInstance()
            val hoje = "%04d-%02d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
            val minutosAgora = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            val nowMs = System.currentTimeMillis()

            if (minutosAgora >= 7 * 60 && store.ultimoLembreteDia != hoje) {
                store.ultimoLembreteDia = hoje
                val doDia = comps.filter { !it.concluida && it.data == hoje }.map { it.descricao }
                val atrasados = comps.filter { !it.concluida && it.data.isNotEmpty() && it.data < hoje }.map { it.descricao }
                if (doDia.isNotEmpty() || atrasados.isNotEmpty()) {
                    val sb = StringBuilder()
                    if (doDia.isNotEmpty()) sb.append("Hoje: ").append(doDia.joinToString(", "))
                    if (atrasados.isNotEmpty()) { if (sb.isNotEmpty()) sb.append("\n"); sb.append("Atrasados: ").append(atrasados.joinToString(", ")) }
                    enviar(ctx, "Agenda de hoje", sb.toString())
                }
            }

            for (c in comps) {
                if (c.concluida) continue
                val antes = c.notificarAntes ?: continue
                if (c.data.isEmpty() || c.hora.isEmpty()) continue
                val dt = instanteDe(c.data, c.hora) ?: continue
                val quando = dt - antes.toLong() * 60_000L
                if (quando <= nowMs && nowMs <= dt + 30L * 60_000L) {
                    val chave = "${c.id}|${c.data}|${c.hora}|$antes"
                    if (!store.lembreteJaEnviado(chave)) {
                        store.lembreteRegistrar(chave)
                        val quanto = when {
                            antes <= 0 -> "agora"
                            antes % 1440 == 0 -> "em ${antes / 1440} dia(s)"
                            antes % 60 == 0 -> "em ${antes / 60}h"
                            else -> "em $antes min"
                        }
                        enviar(ctx, "Lembrete: ${c.descricao}", "Compromisso $quanto (${c.hora})")
                    }
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun instanteDe(data: String, hora: String): Long? {
        return try {
            val d = data.split("-"); val h = hora.split(":")
            val c = Calendar.getInstance()
            c.set(d[0].toInt(), d[1].toInt() - 1, d[2].toInt(), h[0].toInt(), h[1].toInt(), 0)
            c.set(Calendar.MILLISECOND, 0)
            c.timeInMillis
        } catch (_: Exception) { null }
    }

    // ---------------------------------------- AGENDA (compromissos)
    private data class CompSnap(val d: String, val data: String, val hora: String, val x: Boolean)

    // Mesma estrategia da lista, mas para compromissos da Agenda: roda em cada
    // aparelho (primeiro plano e no NotifWorker) e avisa o que mudou.
    fun verificarAgenda(ctx: Context, compromissos: List<Compromisso>) {
        try {
            garantirCanal(ctx)
            val cur = HashMap<String, CompSnap>()
            for (c in compromissos) {
                if (c.id.isNotEmpty()) cur[c.id] = CompSnap(c.descricao, c.data, c.hora, c.excluida)
            }
            val sp = ctx.getSharedPreferences(PREF_AGENDA, Context.MODE_PRIVATE)
            val salvo = sp.getString("dados", null)
            if (salvo == null) { salvarAgenda(sp, cur); return }
            val prev = lerAgenda(salvo)
            for ((id, c) in cur) {
                val pv = prev[id]
                if (pv == null) {
                    if (!c.x) notificar(ctx, msgComp(c, "adicionado"))
                } else {
                    if (!pv.x && c.x) notificar(ctx, msgComp(c, "excluido"))
                    else if (!c.x && !pv.x && (pv.d != c.d || pv.data != c.data || pv.hora != c.hora))
                        notificar(ctx, msgComp(c, "editado"))
                }
            }
            for ((id, pv) in prev) {
                if (!pv.x && id !in cur) notificar(ctx, msgComp(pv, "excluido"))
            }
            salvarAgenda(sp, cur)
        } catch (_: Throwable) {
        }
    }

    private fun msgComp(c: CompSnap, acao: String): String {
        val dd = if (c.data.isNotEmpty()) {
            val p = c.data.split("-")
            val base = if (p.size == 3) p[2] + "/" + p[1] + "/" + p[0] else c.data
            " (" + base + (if (c.hora.isNotEmpty()) " " + c.hora else "") + ")"
        } else ""
        return "compromisso "${c.d}"$dd $acao na Agenda"
    }

    private fun salvarAgenda(sp: android.content.SharedPreferences, itens: Map<String, CompSnap>) {
        val o = JSONObject(); val its = JSONObject()
        for ((id, c) in itens) {
            val io = JSONObject(); io.put("d", c.d); io.put("data", c.data); io.put("hora", c.hora); io.put("x", c.x)
            its.put(id, io)
        }
        o.put("itens", its)
        sp.edit().putString("dados", o.toString()).apply()
    }

    private fun lerAgenda(json: String): Map<String, CompSnap> {
        val itens = HashMap<String, CompSnap>()
        try {
            val its = JSONObject(json).optJSONObject("itens") ?: return itens
            val keys = its.keys()
            while (keys.hasNext()) {
                val id = keys.next(); val io = its.getJSONObject(id)
                itens[id] = CompSnap(io.optString("d", ""), io.optString("data", ""), io.optString("hora", ""), io.optBoolean("x", false))
            }
        } catch (_: Throwable) {
        }
        return itens
    }

    // ------------------------------------------------ snapshot
    private fun salvar(sp: android.content.SharedPreferences,
                       grupos: List<String>, itens: Map<String, ItemSnap>) {
        val o = JSONObject()
        o.put("grupos", org.json.JSONArray(grupos))
        val its = JSONObject()
        for ((id, s) in itens) {
            val io = JSONObject()
            io.put("d", s.d)
            if (s.v != null) io.put("v", s.v) else io.put("v", JSONObject.NULL)
            io.put("x", s.x)
            io.put("g", s.g)
            its.put(id, io)
        }
        o.put("itens", its)
        sp.edit().putString("dados", o.toString()).apply()
    }

    private fun ler(json: String): Pair<Set<String>, Map<String, ItemSnap>> {
        val grupos = HashSet<String>()
        val itens = HashMap<String, ItemSnap>()
        try {
            val o = JSONObject(json)
            val ga = o.optJSONArray("grupos")
            if (ga != null) for (i in 0 until ga.length()) grupos.add(ga.getString(i))
            val its = o.optJSONObject("itens")
            if (its != null) {
                val keys = its.keys()
                while (keys.hasNext()) {
                    val id = keys.next()
                    val io = its.getJSONObject(id)
                    val v = if (io.isNull("v")) null else io.getDouble("v")
                    itens[id] = ItemSnap(io.optString("d", ""), v, io.optBoolean("x", false), io.optString("g", ""))
                }
            }
        } catch (_: Throwable) {
        }
        return Pair(grupos, itens)
    }
}
