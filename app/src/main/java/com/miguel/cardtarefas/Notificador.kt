package com.miguel.cardtarefas

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.json.JSONObject

// Emite notificacoes no celular quando listas marcadas "notificar" mudam.
// Estrategia: compara um retrato (snapshot) do estado anterior com o atual e
// avisa o que foi criado / adicionado / excluido / editado. Marcar concluido
// nao gera notificacao.
object Notificador {

    private const val CANAL = "listas"
    private const val PREF = "notif_snapshot"
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

    private data class ItemSnap(val d: String, val v: Double?, val x: Boolean)

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
                    curItens[t.id] = ItemSnap(t.descricao, t.valor, t.excluida)
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

            salvar(sp, todosNomes, curItens)
        } catch (_: Throwable) {
        }
    }

    private fun msgItem(desc: String, valor: Double?, acao: String, grupo: String): String {
        val v = if (valor != null) " de ${fmtValor(valor)} reais" else ""
        return "item \"$desc\"$v $acao na lista \"$grupo\""
    }

    private fun notificar(ctx: Context, texto: String) {
        try {
            val n = NotificationCompat.Builder(ctx, CANAL)
                .setSmallIcon(R.drawable.ic_check_on)
                .setContentTitle("NestShare")
                .setContentText(texto)
                .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            NotificationManagerCompat.from(ctx).notify(idSeq++, n)
        } catch (_: SecurityException) {
            // sem permissao de notificacao; ignora
        } catch (_: Throwable) {
        }
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
                    itens[id] = ItemSnap(io.optString("d", ""), v, io.optBoolean("x", false))
                }
            }
        } catch (_: Throwable) {
        }
        return Pair(grupos, itens)
    }
}
