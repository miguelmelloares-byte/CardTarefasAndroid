package com.miguel.cardtarefas

import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var store: Store

    private lateinit var loginBox: View
    private lateinit var mainBox: View
    private lateinit var btnSair: TextView

    private lateinit var inEmail: EditText
    private lateinit var inSenha: EditText
    private lateinit var loginMsg: TextView
    private lateinit var btnEntrar: TextView

    private lateinit var gruposWidgetBox: LinearLayout
    private lateinit var gruposHint: TextView
    private lateinit var inDesc: EditText
    private lateinit var spinGrupo: Spinner
    private lateinit var btnAdd: TextView
    private lateinit var status: TextView
    private lateinit var lista: LinearLayout

    private var listaGrupos: List<String> = emptyList()
    private var itens: List<Tarefa> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        store = Store(this)

        loginBox = findViewById(R.id.login_container)
        mainBox = findViewById(R.id.main_container)
        btnSair = findViewById(R.id.btn_sair)
        inEmail = findViewById(R.id.in_email)
        inSenha = findViewById(R.id.in_senha)
        loginMsg = findViewById(R.id.login_msg)
        btnEntrar = findViewById(R.id.btn_entrar)
        gruposWidgetBox = findViewById(R.id.grupos_widget_container)
        gruposHint = findViewById(R.id.grupos_hint)
        inDesc = findViewById(R.id.in_desc)
        spinGrupo = findViewById(R.id.spin_grupo)
        btnAdd = findViewById(R.id.btn_add)
        status = findViewById(R.id.status)
        lista = findViewById(R.id.lista_container)

        inEmail.setText(store.email ?: "")

        btnEntrar.setOnClickListener { entrar() }
        btnSair.setOnClickListener { sair() }
        btnAdd.setOnClickListener { adicionar() }

        if (store.logado) mostrarLista() else mostrarLogin()
    }

    override fun onResume() {
        super.onResume()
        if (store.logado) carregarTudo()
    }

    // ------------------------------------------------------------ telas
    private fun mostrarLogin() {
        loginBox.visibility = View.VISIBLE
        mainBox.visibility = View.GONE
        btnSair.visibility = View.GONE
    }

    private fun mostrarLista() {
        loginBox.visibility = View.GONE
        mainBox.visibility = View.VISIBLE
        btnSair.visibility = View.VISIBLE
        carregarTudo()
    }

    // ------------------------------------------------------------ login
    private fun entrar() {
        val email = inEmail.text.toString().trim()
        val senha = inSenha.text.toString()
        if (email.isEmpty() || senha.isEmpty()) {
            loginMsg.text = "Preencha e-mail e senha."
            return
        }
        esconderTeclado()
        loginMsg.text = "Entrando..."
        btnEntrar.isEnabled = false
        io({
            val (_, rt, uid) = Api.login(email, senha)
            store.salvarLogin(rt, uid, email)
        }, {
            btnEntrar.isEnabled = true
            inSenha.setText("")
            ListWidget.atualizar(this)
            mostrarLista()
        }, { e ->
            btnEntrar.isEnabled = true
            loginMsg.text = amigavel(e)
        })
    }

    private fun sair() {
        AlertDialog.Builder(this)
            .setTitle("Sair")
            .setMessage("Desconectar esta conta do celular?")
            .setPositiveButton("Sair") { _, _ ->
                store.sair()
                ListWidget.atualizar(this)
                mostrarLogin()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ------------------------------------------------------------ dados
    private fun carregarTudo() {
        status.text = "Carregando..."
        io({
            val uid = store.uid!!
            val idToken = Api.idTokenValido(store)
            val grupos = Api.listarGruposLista(idToken, uid)
            val tarefas = Api.listarTarefas(idToken, uid)
            Pair(grupos, tarefas)
        }, { par ->
            listaGrupos = par.first
            itens = par.second.filter { !it.excluida && listaGrupos.any { g -> g.equals(it.grupo, true) } }
            renderGruposWidget()
            renderSpinner()
            renderItens()
            val pend = itens.count { !it.concluida }
            status.text = if (listaGrupos.isEmpty()) "" else "$pend item(ns) pendente(s)"
        }, { e ->
            status.text = amigavel(e)
        })
    }

    // caixas de selecao: quais grupos-lista aparecem no widget
    private fun renderGruposWidget() {
        gruposWidgetBox.removeAllViews()
        if (listaGrupos.isEmpty()) {
            gruposHint.visibility = View.VISIBLE
            gruposHint.text = "Nenhum grupo do tipo \"Lista\" ainda.\n" +
                "Crie um no app do PC (Gerenciar grupos → tipo Lista), por exemplo \"Mercado\"."
            spinGrupo.visibility = View.GONE
            inDesc.visibility = View.GONE
            btnAdd.visibility = View.GONE
            return
        }
        gruposHint.visibility = View.GONE
        spinGrupo.visibility = View.VISIBLE
        inDesc.visibility = View.VISIBLE
        btnAdd.visibility = View.VISIBLE

        val selecionados = store.gruposWidget
        for (g in listaGrupos) {
            val cb = CheckBox(this)
            cb.text = g
            cb.setTextColor(0xFFECECF0.toInt())
            // vazio = todos aparecem
            cb.isChecked = selecionados.isEmpty() || selecionados.contains(g)
            cb.setOnCheckedChangeListener { _, _ -> salvarSelecao() }
            gruposWidgetBox.addView(cb)
        }
    }

    private fun salvarSelecao() {
        val sel = HashSet<String>()
        for (i in 0 until gruposWidgetBox.childCount) {
            val cb = gruposWidgetBox.getChildAt(i) as? CheckBox ?: continue
            if (cb.isChecked) sel.add(cb.text.toString())
        }
        store.gruposWidget = sel
        ListWidget.atualizar(this)
    }

    private fun renderSpinner() {
        val adapter = object : ArrayAdapter<String>(
            this, android.R.layout.simple_spinner_item, listaGrupos
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent) as TextView
                v.setTextColor(Color.WHITE)
                return v
            }
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getDropDownView(position, convertView, parent) as TextView
                v.setTextColor(Color.WHITE)
                v.setBackgroundColor(0xFF2C2C2E.toInt())
                v.setPadding(24, 20, 24, 20)
                return v
            }
        }
        spinGrupo.adapter = adapter
    }

    private fun adicionar() {
        val desc = inDesc.text.toString().trim()
        if (desc.isEmpty() || listaGrupos.isEmpty()) return
        val grupo = (spinGrupo.selectedItem as? String) ?: listaGrupos.first()
        esconderTeclado()
        btnAdd.isEnabled = false
        io({
            val uid = store.uid!!
            val idToken = Api.idTokenValido(store)
            Api.criarItem(idToken, uid, grupo, desc)
        }, {
            btnAdd.isEnabled = true
            inDesc.setText("")
            ListWidget.atualizar(this)
            carregarTudo()
        }, { e ->
            btnAdd.isEnabled = true
            status.text = amigavel(e)
        })
    }

    private fun marcar(t: Tarefa, novo: Boolean) {
        io({
            val uid = store.uid!!
            val idToken = Api.idTokenValido(store)
            Api.marcar(idToken, uid, t.id, novo)
        }, {
            ListWidget.atualizar(this)
            carregarTudo()
        }, { e -> status.text = amigavel(e) })
    }

    private fun excluir(t: Tarefa) {
        io({
            val uid = store.uid!!
            val idToken = Api.idTokenValido(store)
            Api.excluir(idToken, uid, t.id)
        }, {
            ListWidget.atualizar(this)
            carregarTudo()
        }, { e -> status.text = amigavel(e) })
    }

    // ------------------------------------------------------------ lista agrupada
    private fun renderItens() {
        lista.removeAllViews()
        if (listaGrupos.isEmpty()) return
        for (g in listaGrupos) {
            val doGrupo = itens.filter { it.grupo.equals(g, true) }
            val pend = doGrupo.filter { !it.concluida }.sortedBy { it.descricao.lowercase() }
            val feitos = doGrupo.filter { it.concluida }.sortedByDescending { it.atualizadoEm }

            lista.addView(cabecalhoGrupo(g, pend.size))
            if (pend.isEmpty() && feitos.isEmpty()) {
                val vazio = TextView(this)
                vazio.text = "  (vazio)"
                vazio.setTextColor(0xFF9A9AA2.toInt())
                vazio.textSize = 13f
                vazio.setPadding(8, 2, 8, 8)
                lista.addView(vazio)
            }
            for (t in pend) lista.addView(linhaItem(t))
            for (t in feitos) lista.addView(linhaItem(t))
        }
    }

    private fun cabecalhoGrupo(nome: String, pendentes: Int): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(4, 18, 4, 6)
        val nomeLbl = TextView(this)
        nomeLbl.text = nome
        nomeLbl.setTextColor(0xFF89B4FA.toInt())
        nomeLbl.textSize = 15f
        nomeLbl.setTypeface(nomeLbl.typeface, android.graphics.Typeface.BOLD)
        row.addView(nomeLbl)
        val cont = TextView(this)
        cont.text = "   $pendentes pendente(s)"
        cont.setTextColor(0xFF9A9AA2.toInt())
        cont.textSize = 12f
        row.addView(cont)
        return row
    }

    private fun linhaItem(t: Tarefa): View {
        val row = layoutInflater.inflate(R.layout.item_app, lista, false)
        val check = row.findViewById<ImageView>(R.id.item_check)
        val texto = row.findViewById<TextView>(R.id.item_texto)
        val lixo = row.findViewById<ImageView>(R.id.item_lixo)
        texto.text = t.descricao
        if (t.concluida) {
            check.setImageResource(R.drawable.ic_check_on)
            texto.paintFlags = texto.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            texto.setTextColor(0xFF9A9AA2.toInt())
        } else {
            check.setImageResource(R.drawable.ic_check_off)
            texto.paintFlags = texto.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            texto.setTextColor(0xFFECECF0.toInt())
        }
        val toggle = View.OnClickListener { marcar(t, !t.concluida) }
        check.setOnClickListener(toggle)
        texto.setOnClickListener(toggle)
        lixo.setOnClickListener { excluir(t) }
        return row
    }

    // ------------------------------------------------------------ util
    private fun esconderTeclado() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
    }

    private fun amigavel(e: Throwable): String {
        val m = (e.message ?: "").uppercase()
        return when {
            m.contains("INVALID_PASSWORD") || m.contains("INVALID_LOGIN_CREDENTIALS")
                || m.contains("INVALID_EMAIL") -> "E-mail ou senha incorretos."
            m.contains("EMAIL_NOT_FOUND") -> "Esse e-mail nao esta cadastrado."
            m.contains("TOO_MANY_ATTEMPTS") -> "Muitas tentativas. Aguarde um pouco."
            m.contains("NAO CONECTADO") -> "Sessao expirou. Entre de novo."
            m.contains("TIMEOUT") || m.contains("TIMED OUT") || m.contains("UNABLE TO RESOLVE")
                || m.contains("FAILED TO CONNECT") -> "Sem conexao com a internet."
            else -> "Erro: " + (e.message ?: "desconhecido").take(80)
        }
    }

    private fun <T> io(trabalho: () -> T, ok: (T) -> Unit, falha: (Throwable) -> Unit) {
        Thread {
            try {
                val r = trabalho()
                runOnUiThread { ok(r) }
            } catch (e: Throwable) {
                runOnUiThread { falha(e) }
            }
        }.start()
    }
}
