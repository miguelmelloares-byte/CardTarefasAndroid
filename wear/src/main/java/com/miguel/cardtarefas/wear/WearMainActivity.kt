package com.miguel.cardtarefas.wear

import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable

class WearMainActivity : AppCompatActivity(), DataClient.OnDataChangedListener {

    private lateinit var titulo: TextView
    private lateinit var statusView: TextView
    private lateinit var container: LinearLayout

    private var refreshToken: String? = null
    private var uid: String? = null

    private var grupos: List<String> = emptyList()
    private var tarefas: List<Tarefa> = emptyList()
    private var grupoAberto: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wear)
        titulo = findViewById(R.id.w_titulo)
        statusView = findViewById(R.id.w_status)
        container = findViewById(R.id.w_container)
        lerLoginECarregar()
    }

    override fun onResume() {
        super.onResume()
        try { Wearable.getDataClient(this).addListener(this) } catch (_: Throwable) {}
    }

    override fun onPause() {
        super.onPause()
        try { Wearable.getDataClient(this).removeListener(this) } catch (_: Throwable) {}
    }

    // recebe o login publicado pelo celular
    override fun onDataChanged(events: DataEventBuffer) {
        try {
            for (e in events) {
                if (e.dataItem.uri.path == "/login") {
                    val dm = DataMapItem.fromDataItem(e.dataItem).dataMap
                    refreshToken = dm.getString("refresh_token")
                    uid = dm.getString("uid")
                }
            }
            events.release()
        } catch (_: Throwable) {}
        if (!refreshToken.isNullOrEmpty() && !uid.isNullOrEmpty()) carregar()
    }

    private fun lerLoginECarregar() {
        mostraStatus("Conectando ao celular...")
        try {
            Wearable.getDataClient(this).getDataItems().addOnSuccessListener { buffer ->
                try {
                    for (item in buffer) {
                        if (item.uri.path == "/login") {
                            val dm = DataMapItem.fromDataItem(item).dataMap
                            refreshToken = dm.getString("refresh_token")
                            uid = dm.getString("uid")
                        }
                    }
                } finally {
                    buffer.release()
                }
                if (!refreshToken.isNullOrEmpty() && !uid.isNullOrEmpty()) {
                    carregar()
                } else {
                    mostraStatus("Abra o app no celular e faça login.")
                }
            }.addOnFailureListener {
                mostraStatus("Nao foi possivel ler o login do celular.")
            }
        } catch (e: Throwable) {
            mostraStatus("Erro ao conectar com o celular.")
        }
    }

    private fun carregar() {
        mostraStatus("Carregando...")
        val rt = refreshToken ?: return
        val u = uid ?: return
        Thread {
            try {
                val (idToken, novoRt) = Api.refresh(rt)
                if (novoRt.isNotEmpty()) refreshToken = novoRt
                val g = Api.listarGruposRelogio(idToken, u)
                val t = Api.listarTarefas(idToken, u).filter { !it.excluida }
                runOnUiThread { grupos = g; tarefas = t; render() }
            } catch (e: Throwable) {
                runOnUiThread { mostraStatus("Sem conexao. Aproxime do celular ou use Wi-Fi.") }
            }
        }.start()
    }

    // ---------------------------------------------------------- render
    private fun render() {
        statusView.visibility = View.GONE
        container.removeAllViews()
        if (grupoAberto == null) renderGrupos() else renderItens(grupoAberto!!)
    }

    private fun renderGrupos() {
        titulo.text = "Listas"
        if (grupos.isEmpty()) {
            container.addView(aviso("Nenhuma lista marcada para o relogio.\nMarque no app do PC."))
            return
        }
        for (g in grupos) {
            val pend = tarefas.count { it.grupo.equals(g, true) && !it.concluida }
            container.addView(botaoGrupo(g, pend))
        }
    }

    private fun renderItens(grupo: String) {
        titulo.text = grupo
        container.addView(botaoVoltar())
        val doGrupo = tarefas.filter { it.grupo.equals(grupo, true) }
        val pend = doGrupo.filter { !it.concluida }.sortedBy { it.descricao.lowercase() }
        val feitos = doGrupo.filter { it.concluida }.sortedByDescending { it.atualizadoEm }
        if (pend.isEmpty() && feitos.isEmpty()) {
            container.addView(aviso("Lista vazia."))
            return
        }
        for (t in pend) container.addView(linhaItem(t))
        if (feitos.isNotEmpty()) {
            val sep = TextView(this)
            sep.text = "JA PEGOS"
            sep.setTextColor(0xFF9A9AA2.toInt()); sep.textSize = 10f
            sep.setPadding(dp(4), dp(8), 0, dp(2))
            container.addView(sep)
            for (t in feitos) container.addView(linhaItem(t))
        }
    }

    private fun botaoGrupo(nome: String, pendentes: Int): View {
        val b = TextView(this)
        b.text = "$nome   ($pendentes)"
        b.setTextColor(0xFFECECF0.toInt())
        b.textSize = 16f
        b.gravity = Gravity.CENTER
        b.setBackgroundResource(R.drawable.wear_btn)
        b.setPadding(dp(12), dp(14), dp(12), dp(14))
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = dp(8)
        b.layoutParams = lp
        b.setOnClickListener { grupoAberto = nome; render() }
        return b
    }

    private fun botaoVoltar(): View {
        val b = TextView(this)
        b.text = "‹ Listas"
        b.setTextColor(0xFF89B4FA.toInt())
        b.textSize = 14f
        b.setPadding(dp(6), dp(4), dp(6), dp(10))
        b.setOnClickListener { grupoAberto = null; render() }
        return b
    }

    private fun linhaItem(t: Tarefa): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setBackgroundResource(R.drawable.wear_btn)
        row.setPadding(dp(10), dp(12), dp(10), dp(12))
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = dp(6)
        row.layoutParams = lp

        val chk = ImageView(this)
        chk.setImageResource(if (t.concluida) R.drawable.ic_check_on else R.drawable.ic_check_off)
        val clp = LinearLayout.LayoutParams(dp(28), dp(28))
        clp.marginEnd = dp(8)
        chk.layoutParams = clp
        row.addView(chk)

        val tx = TextView(this)
        tx.text = t.descricao
        tx.textSize = 15f
        tx.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        if (t.concluida) {
            tx.setTextColor(0xFF9A9AA2.toInt())
            tx.paintFlags = tx.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            tx.setTextColor(0xFFECECF0.toInt())
        }
        row.addView(tx)

        if (t.valor != null) {
            val v = TextView(this)
            v.text = fmtValor(t.valor)
            v.textSize = 13f
            v.setTextColor(if (t.valor < 0) Color.parseColor("#F38BA8") else Color.parseColor("#A6E3A1"))
            v.setPadding(dp(6), 0, 0, 0)
            row.addView(v)
        }

        row.setOnClickListener { marcar(t, !t.concluida) }
        return row
    }

    private fun marcar(t: Tarefa, novo: Boolean) {
        // atualiza na tela ja (otimista)
        tarefas = tarefas.map { if (it.id == t.id) it.copy(concluida = novo) else it }
        render()
        val rt = refreshToken ?: return
        val u = uid ?: return
        Thread {
            try {
                val (idToken, _) = Api.refresh(rt)
                Api.marcar(idToken, u, t.id, novo)
            } catch (_: Throwable) {
                runOnUiThread { mostraStatus("Falha ao salvar. Verifique a conexao.") }
            }
        }.start()
    }

    // ---------------------------------------------------------- util
    private fun aviso(txt: String): View {
        val tv = TextView(this)
        tv.text = txt
        tv.gravity = Gravity.CENTER
        tv.setTextColor(0xFF9A9AA2.toInt())
        tv.textSize = 14f
        tv.setPadding(dp(6), dp(20), dp(6), dp(6))
        return tv
    }

    private fun mostraStatus(txt: String) {
        statusView.text = txt
        statusView.visibility = View.VISIBLE
        container.removeAllViews()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
