package com.miguel.cardtarefas

import android.graphics.Paint
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
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

    private lateinit var inGrupo: EditText
    private lateinit var btnGrupo: TextView
    private lateinit var inDesc: EditText
    private lateinit var btnAdd: TextView
    private lateinit var status: TextView
    private lateinit var lista: LinearLayout

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
        inGrupo = findViewById(R.id.in_grupo)
        btnGrupo = findViewById(R.id.btn_grupo)
        inDesc = findViewById(R.id.in_desc)
        btnAdd = findViewById(R.id.btn_add)
        status = findViewById(R.id.status)
        lista = findViewById(R.id.lista_container)

        inEmail.setText(store.email ?: "")
        inGrupo.setText(store.grupoWidget)

        btnEntrar.setOnClickListener { entrar() }
        btnSair.setOnClickListener { sair() }
        btnGrupo.setOnClickListener { salvarGrupo() }
        btnAdd.setOnClickListener { adicionar() }

        if (store.logado) mostrarLista() else mostrarLogin()
    }

    override fun onResume() {
        super.onResume()
        if (store.logado) carregar()
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
        carregar()
    }

    // ------------------------------------------------------------ acoes
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

    private fun salvarGrupo() {
        store.grupoWidget = inGrupo.text.toString().trim()
        esconderTeclado()
        ListWidget.atualizar(this)
        carregar()
        Toast.makeText(this, "Grupo do widget salvo.", Toast.LENGTH_SHORT).show()
    }

    private fun adicionar() {
        val desc = inDesc.text.toString().trim()
        if (desc.isEmpty()) return
        val grupo = inGrupo.text.toString().trim().ifEmpty { "Mercado" }
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
            carregar()
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
            carregar()
        }, { e -> status.text = amigavel(e) })
    }

    private fun excluir(t: Tarefa) {
        AlertDialog.Builder(this)
            .setTitle("Excluir")
            .setMessage("Excluir \"${t.descricao}\"?")
            .setPositiveButton("Excluir") { _, _ ->
                io({
                    val uid = store.uid!!
                    val idToken = Api.idTokenValido(store)
                    Api.excluir(idToken, uid, t.id)
                }, {
                    ListWidget.atualizar(this)
                    carregar()
                }, { e -> status.text = amigavel(e) })
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ------------------------------------------------------------ dados
    private fun carregar() {
        status.text = "Carregando..."
        val grupo = inGrupo.text.toString().trim()
        io({
            val uid = store.uid!!
            val idToken = Api.idTokenValido(store)
            Api.listarTarefas(idToken, uid)
        }, { todas ->
            val vis = todas.filter { !it.excluida }
                .filter { grupo.isEmpty() || it.grupo.equals(grupo, ignoreCase = true) }
            renderizar(vis)
            val pend = vis.count { !it.concluida }
            status.text = "$pend item(ns) na lista" +
                if (grupo.isNotEmpty()) " · grupo \"$grupo\"" else ""
        }, { e ->
            status.text = amigavel(e)
        })
    }

    private fun renderizar(itens: List<Tarefa>) {
        lista.removeAllViews()
        val pendentes = itens.filter { !it.concluida }
            .sortedBy { it.descricao.lowercase() }
        val concluidas = itens.filter { it.concluida }
            .sortedByDescending { it.atualizadoEm }

        for (t in pendentes) lista.addView(linhaItem(t))
        if (concluidas.isNotEmpty()) {
            val sep = TextView(this)
            sep.text = "Já pegos"
            sep.setTextColor(0xFF9A9AA2.toInt())
            sep.textSize = 12f
            sep.setPadding(4, 18, 4, 6)
            lista.addView(sep)
            for (t in concluidas) lista.addView(linhaItem(t))
        }
        if (itens.isEmpty()) {
            val vazio = TextView(this)
            vazio.text = "Nenhum item aqui ainda.\nDigite acima e toque em Adicionar."
            vazio.setTextColor(0xFF9A9AA2.toInt())
            vazio.textSize = 14f
            vazio.setPadding(6, 30, 6, 6)
            lista.addView(vazio)
        }
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

    // Executa 'trabalho' numa thread; chama 'ok' (ou 'falha') na thread de UI.
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
