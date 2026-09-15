package com.miguel.cardtarefas

import android.app.DatePickerDialog
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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.util.Calendar
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var store: Store

    private lateinit var loginBox: View
    private lateinit var mainBox: View
    private lateinit var btnSair: TextView

    private lateinit var inEmail: EditText
    private lateinit var inSenha: EditText
    private lateinit var loginMsg: TextView
    private lateinit var btnEntrar: TextView

    private lateinit var tabTarefas: TextView
    private lateinit var tabListas: TextView
    private lateinit var tabWidget: TextView
    private lateinit var boxTarefas: View
    private lateinit var boxListas: View
    private lateinit var boxWidget: View
    private lateinit var status: TextView

    private lateinit var spinFiltroGrupo: Spinner
    private lateinit var spinFiltroStatus: Spinner
    private lateinit var btnNovaTarefa: TextView
    private lateinit var tarefasContainer: LinearLayout
    private lateinit var listasContainer: LinearLayout
    private lateinit var gruposWidgetBox: LinearLayout
    private lateinit var gruposHint: TextView

    private var tarefasGrupos: List<String> = emptyList()
    private var listaGrupos: List<String> = emptyList()
    private var todasTarefas: List<Tarefa> = emptyList()

    private var modo = "tarefas"
    private var focoGrupoLista: String? = null

    private val PRIOS = listOf("Alta", "Media", "Baixa")

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

        tabTarefas = findViewById(R.id.tab_tarefas)
        tabListas = findViewById(R.id.tab_listas)
        tabWidget = findViewById(R.id.tab_widget)
        boxTarefas = findViewById(R.id.box_tarefas)
        boxListas = findViewById(R.id.box_listas)
        boxWidget = findViewById(R.id.box_widget)
        status = findViewById(R.id.status)

        spinFiltroGrupo = findViewById(R.id.spin_filtro_grupo)
        spinFiltroStatus = findViewById(R.id.spin_filtro_status)
        btnNovaTarefa = findViewById(R.id.btn_nova_tarefa)
        tarefasContainer = findViewById(R.id.tarefas_container)
        listasContainer = findViewById(R.id.listas_container)
        gruposWidgetBox = findViewById(R.id.grupos_widget_container)
        gruposHint = findViewById(R.id.grupos_hint)

        inEmail.setText(store.email ?: "")
        btnEntrar.setOnClickListener { entrar() }
        btnSair.setOnClickListener { sair() }
        tabTarefas.setOnClickListener { trocarModo("tarefas") }
        tabListas.setOnClickListener { trocarModo("listas") }
        tabWidget.setOnClickListener { trocarModo("widget") }
        btnNovaTarefa.setOnClickListener { abrirDialogTarefa(null) }

        spinFiltroStatus.adapter = adaptador(listOf("Pendentes", "Todas", "Concluidas"))

        if (store.logado) mostrarApp() else mostrarLogin()
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

    private fun mostrarApp() {
        loginBox.visibility = View.GONE
        mainBox.visibility = View.VISIBLE
        btnSair.visibility = View.VISIBLE
        trocarModo(modo)
        carregarTudo()
    }

    private fun trocarModo(m: String) {
        modo = m
        boxTarefas.visibility = if (m == "tarefas") View.VISIBLE else View.GONE
        boxListas.visibility = if (m == "listas") View.VISIBLE else View.GONE
        boxWidget.visibility = if (m == "widget") View.VISIBLE else View.GONE
        for (t in listOf(tabTarefas, tabListas, tabWidget)) t.setTextColor(0xFF9A9AA2.toInt())
        val ativa = when (m) { "listas" -> tabListas; "widget" -> tabWidget; else -> tabTarefas }
        ativa.setTextColor(0xFF89B4FA.toInt())
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
            mostrarApp()
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
            val grupos = Api.listarGruposComTipo(idToken, uid)
            val tarefas = Api.listarTarefas(idToken, uid).filter { !it.excluida }
            Pair(grupos, tarefas)
        }, { par ->
            tarefasGrupos = par.first.filter { it.second == "tarefas" }.map { it.first }
            listaGrupos = par.first.filter { it.second == "lista" }.map { it.first }
            todasTarefas = par.second
            configurarFiltroGrupo()
            renderTarefas()
            renderListas()
            renderWidget()
            status.text = ""
        }, { e ->
            status.text = amigavel(e)
        })
    }

    private fun configurarFiltroGrupo() {
        val nomes = listOf("Todos") + tarefasGrupos
        val atual = (spinFiltroGrupo.selectedItem as? String)
        spinFiltroGrupo.adapter = adaptador(nomes)
        val idx = if (atual != null) nomes.indexOf(atual) else -1
        if (idx >= 0) spinFiltroGrupo.setSelection(idx)
        val ouvinte = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) = renderTarefas()
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }
        spinFiltroGrupo.onItemSelectedListener = ouvinte
        spinFiltroStatus.onItemSelectedListener = ouvinte
    }

    // ---------------------------------------------------- TAREFAS
    private fun renderTarefas() {
        tarefasContainer.removeAllViews()
        val fg = (spinFiltroGrupo.selectedItem as? String) ?: "Todos"
        val fs = (spinFiltroStatus.selectedItem as? String) ?: "Pendentes"
        var lista = todasTarefas.filter { tarefasGrupos.any { g -> g.equals(it.grupo, true) } }
        if (fg != "Todos") lista = lista.filter { it.grupo.equals(fg, true) }
        lista = when (fs) {
            "Pendentes" -> lista.filter { !it.concluida }
            "Concluidas" -> lista.filter { it.concluida }
            else -> lista
        }
        lista = lista.sortedWith(compareBy({ it.concluida }, { rankPrio(it.prioridade) }, { it.data ?: "9999" }))

        if (tarefasGrupos.isEmpty()) {
            tarefasContainer.addView(aviso(
                "Nenhum grupo do tipo Tarefas.\nCrie um no app do PC (Gerenciar grupos → tipo Tarefas)."))
            return
        }
        if (lista.isEmpty()) {
            tarefasContainer.addView(aviso("Nenhuma tarefa aqui."))
            return
        }
        for (t in lista) tarefasContainer.addView(cardTarefa(t))
    }

    private fun cardTarefa(t: Tarefa): View {
        val row = layoutInflater.inflate(R.layout.item_task, tarefasContainer, false)
        val dot = row.findViewById<View>(R.id.task_prio_dot)
        val desc = row.findViewById<TextView>(R.id.task_desc)
        val meta = row.findViewById<TextView>(R.id.task_meta)
        val done = row.findViewById<ImageView>(R.id.task_done)
        val edit = row.findViewById<ImageView>(R.id.task_edit)
        val del = row.findViewById<ImageView>(R.id.task_del)

        desc.text = t.descricao
        dot.background?.setTint(corPrio(t.prioridade))
        val partes = ArrayList<String>()
        partes.add(t.grupo)
        if (!t.data.isNullOrEmpty()) partes.add(fmtBr(t.data))
        partes.add("⚑ " + t.prioridade)
        meta.text = partes.joinToString("   ")

        if (t.concluida) {
            desc.paintFlags = desc.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            desc.setTextColor(0xFF9A9AA2.toInt())
            done.setImageResource(R.drawable.ic_check_on)
        } else {
            desc.paintFlags = desc.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            desc.setTextColor(0xFFECECF0.toInt())
            done.setImageResource(R.drawable.ic_check_off)
        }
        done.setOnClickListener { marcar(t, !t.concluida) }
        edit.setOnClickListener { abrirDialogTarefa(t) }
        del.setOnClickListener { confirmarExcluir(t) }
        return row
    }

    // ---------------------------------------------------- NOVA/EDITAR TAREFA
    private fun abrirDialogTarefa(tarefa: Tarefa?) {
        if (tarefasGrupos.isEmpty()) {
            AlertDialog.Builder(this)
                .setMessage("Crie um grupo do tipo Tarefas no app do PC primeiro.")
                .setPositiveButton("OK", null).show()
            return
        }
        val view = layoutInflater.inflate(R.layout.dialog_task, null)
        val spGrupo = view.findViewById<Spinner>(R.id.dlg_grupo)
        val edDesc = view.findViewById<EditText>(R.id.dlg_desc)
        val tvData = view.findViewById<TextView>(R.id.dlg_data)
        val tvLimpar = view.findViewById<TextView>(R.id.dlg_data_limpar)
        val spPrio = view.findViewById<Spinner>(R.id.dlg_prio)
        val spCompl = view.findViewById<Spinner>(R.id.dlg_compl)

        spGrupo.adapter = adaptador(tarefasGrupos)
        spPrio.adapter = adaptador(PRIOS)
        spCompl.adapter = adaptador(PRIOS)

        val dataSel = arrayOf<String?>(tarefa?.data)
        fun mostraData() { tvData.text = if (dataSel[0].isNullOrEmpty()) "Sem data" else fmtBr(dataSel[0]!!) }
        mostraData()
        tvData.setOnClickListener {
            val cal = Calendar.getInstance()
            dataSel[0]?.let { iso ->
                val p = iso.split("-")
                if (p.size == 3) cal.set(p[0].toInt(), p[1].toInt() - 1, p[2].toInt())
            }
            DatePickerDialog(this, { _, y, m, d ->
                dataSel[0] = "%04d-%02d-%02d".format(y, m + 1, d)
                mostraData()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
        tvLimpar.setOnClickListener { dataSel[0] = null; mostraData() }

        if (tarefa != null) {
            selecionar(spGrupo, tarefasGrupos, tarefa.grupo)
            edDesc.setText(tarefa.descricao)
            selecionar(spPrio, PRIOS, tarefa.prioridade)
            selecionar(spCompl, PRIOS, tarefa.complexidade)
        } else {
            selecionar(spPrio, PRIOS, "Media")
            selecionar(spCompl, PRIOS, "Media")
        }

        AlertDialog.Builder(this)
            .setTitle(if (tarefa == null) "Nova tarefa" else "Editar tarefa")
            .setView(view)
            .setPositiveButton("Salvar") { _, _ ->
                val d = edDesc.text.toString().trim()
                if (d.isEmpty()) return@setPositiveButton
                val grupo = (spGrupo.selectedItem as? String) ?: tarefasGrupos.first()
                val nova = Tarefa(
                    id = tarefa?.id ?: UUID.randomUUID().toString().replace("-", ""),
                    grupo = grupo,
                    topico = tarefa?.topico ?: "",
                    descricao = d,
                    data = dataSel[0],
                    prioridade = (spPrio.selectedItem as? String) ?: "Media",
                    complexidade = (spCompl.selectedItem as? String) ?: "Media",
                    concluida = tarefa?.concluida ?: false,
                    concluidaEm = tarefa?.concluidaEm,
                    criadaEm = tarefa?.criadaEm ?: nowIso(),
                    atualizadoEm = Api.agoraMs(),
                    excluida = false
                )
                salvarTarefa(nova)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun salvarTarefa(t: Tarefa) {
        status.text = "Salvando..."
        io({
            val uid = store.uid!!
            val idToken = Api.idTokenValido(store)
            Api.gravarTarefa(idToken, uid, t)
        }, { carregarTudo() }, { e -> status.text = amigavel(e) })
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

    private fun confirmarExcluir(t: Tarefa) {
        AlertDialog.Builder(this)
            .setTitle("Excluir")
            .setMessage("Excluir \"${t.descricao}\"?")
            .setPositiveButton("Excluir") { _, _ -> excluir(t) }
            .setNegativeButton("Cancelar", null).show()
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

    // ---------------------------------------------------- LISTAS (checklists)
    private fun renderListas() {
        listasContainer.removeAllViews()
        if (listaGrupos.isEmpty()) {
            listasContainer.addView(aviso(
                "Nenhum grupo do tipo Lista.\nCrie um no app do PC (Gerenciar grupos → tipo Lista), ex.: Mercado."))
            return
        }
        val dica = TextView(this)
        dica.text = "💡 Digite um item e toque em + (ou Enter) para adicionar."
        dica.setTextColor(0xFF9A9AA2.toInt())
        dica.textSize = 12f
        dica.setPadding(2, 0, 2, 10)
        listasContainer.addView(dica)

        for (g in listaGrupos) listasContainer.addView(blocoLista(g))
    }

    private fun blocoLista(nome: String): View {
        val itens = todasTarefas.filter { it.grupo.equals(nome, true) }
        val pend = itens.filter { !it.concluida }.sortedBy { it.descricao.lowercase() }
        val feitos = itens.filter { it.concluida }.sortedByDescending { it.atualizadoEm }

        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setBackgroundResource(R.drawable.row_bg)
        card.setPadding(dp(12), dp(12), dp(12), dp(12))
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = dp(10)
        card.layoutParams = lp

        // cabecalho: nome + pendentes (esquerda) e saldo (direita)
        val saldo = itens.mapNotNull { it.valor }.sum()
        val cab = LinearLayout(this)
        cab.orientation = LinearLayout.HORIZONTAL
        cab.gravity = Gravity.CENTER_VERTICAL
        val titulo = TextView(this)
        titulo.text = "$nome  ·  ${pend.size} pendente(s)"
        titulo.setTextColor(0xFF89B4FA.toInt())
        titulo.textSize = 15f
        titulo.setTypeface(titulo.typeface, android.graphics.Typeface.BOLD)
        titulo.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        cab.addView(titulo)
        val lblSaldo = TextView(this)
        lblSaldo.text = "Saldo: " + fmtValor(saldo)
        lblSaldo.setTextColor(if (saldo < 0) Color.parseColor("#F38BA8") else Color.parseColor("#A6E3A1"))
        lblSaldo.textSize = 14f
        lblSaldo.setTypeface(lblSaldo.typeface, android.graphics.Typeface.BOLD)
        cab.addView(lblSaldo)
        card.addView(cab)

        // adicionar item
        val addRow = LinearLayout(this)
        addRow.orientation = LinearLayout.HORIZONTAL
        addRow.gravity = Gravity.CENTER_VERTICAL
        addRow.setPadding(0, dp(8), 0, dp(6))
        val ent = EditText(this)
        ent.hint = "Novo item..."
        ent.setHintTextColor(0xFF9A9AA2.toInt())
        ent.setTextColor(0xFFECECF0.toInt())
        ent.setBackgroundResource(R.drawable.field_bg)
        ent.setPadding(dp(10), dp(8), dp(10), dp(8))
        ent.maxLines = 1
        val entLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        ent.layoutParams = entLp
        addRow.addView(ent)
        val entValor = EditText(this)
        entValor.hint = "valor"
        entValor.setHintTextColor(0xFF9A9AA2.toInt())
        entValor.setTextColor(0xFFECECF0.toInt())
        entValor.setBackgroundResource(R.drawable.field_bg)
        entValor.setPadding(dp(8), dp(8), dp(8), dp(8))
        entValor.maxLines = 1
        entValor.inputType = android.text.InputType.TYPE_CLASS_NUMBER or
            android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or
            android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
        val evLp = LinearLayout.LayoutParams(dp(80), ViewGroup.LayoutParams.WRAP_CONTENT)
        evLp.marginStart = dp(6)
        entValor.layoutParams = evLp
        addRow.addView(entValor)
        val mais = TextView(this)
        mais.text = "+"
        mais.setTextColor(Color.parseColor("#11111B"))
        mais.textSize = 18f
        mais.setTypeface(mais.typeface, android.graphics.Typeface.BOLD)
        mais.gravity = Gravity.CENTER
        mais.setBackgroundResource(R.drawable.btn_accent)
        mais.setPadding(dp(16), dp(8), dp(16), dp(8))
        val maisLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        maisLp.marginStart = dp(8)
        mais.layoutParams = maisLp
        addRow.addView(mais)
        card.addView(addRow)

        val addAcao = { addItemLista(nome, ent, entValor) }
        mais.setOnClickListener { addAcao() }
        ent.setOnEditorActionListener { _, _, _ -> addAcao(); true }
        entValor.setOnEditorActionListener { _, _, _ -> addAcao(); true }
        if (focoGrupoLista == nome) {
            focoGrupoLista = null
            ent.requestFocus()
        }

        if (pend.isEmpty() && feitos.isEmpty()) {
            val vazio = TextView(this)
            vazio.text = "Lista vazia."
            vazio.setTextColor(0xFF9A9AA2.toInt())
            vazio.textSize = 13f
            card.addView(vazio)
        }
        for (t in pend) card.addView(linhaItemLista(t))
        if (feitos.isNotEmpty()) {
            val sep = TextView(this)
            sep.text = "JA PEGOS"
            sep.setTextColor(0xFF9A9AA2.toInt())
            sep.textSize = 10f
            sep.setPadding(0, dp(8), 0, dp(2))
            card.addView(sep)
            for (t in feitos) card.addView(linhaItemLista(t))
        }
        return card
    }

    private fun linhaItemLista(t: Tarefa): View {
        val row = layoutInflater.inflate(R.layout.item_app, listasContainer, false)
        val check = row.findViewById<ImageView>(R.id.item_check)
        val texto = row.findViewById<TextView>(R.id.item_texto)
        val valor = row.findViewById<TextView>(R.id.item_valor)
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
        if (t.valor != null) {
            valor.text = fmtValor(t.valor)
            valor.setTextColor(if (t.valor < 0) Color.parseColor("#F38BA8") else Color.parseColor("#A6E3A1"))
        } else {
            valor.text = "+ valor"
            valor.setTextColor(0xFF9A9AA2.toInt())
        }
        valor.setOnClickListener { editarValor(t) }
        val toggle = View.OnClickListener { marcar(t, !t.concluida) }
        check.setOnClickListener(toggle)
        texto.setOnClickListener(toggle)
        lixo.setOnClickListener { excluir(t) }
        return row
    }

    private fun editarValor(t: Tarefa) {
        val ed = EditText(this)
        ed.inputType = android.text.InputType.TYPE_CLASS_NUMBER or
            android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or
            android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
        ed.hint = "valor (use - para negativo)"
        if (t.valor != null) ed.setText(fmtValor(t.valor))
        AlertDialog.Builder(this)
            .setTitle("Valor de \"${t.descricao}\"")
            .setView(ed)
            .setPositiveButton("Salvar") { _, _ ->
                val v = parseValor(ed.text.toString())
                io({
                    val uid = store.uid!!
                    val idToken = Api.idTokenValido(store)
                    Api.atualizarValor(idToken, uid, t.id, v)
                }, {
                    ListWidget.atualizar(this)
                    carregarTudo()
                }, { e -> status.text = amigavel(e) })
            }
            .setNeutralButton("Remover") { _, _ ->
                io({
                    val uid = store.uid!!
                    val idToken = Api.idTokenValido(store)
                    Api.atualizarValor(idToken, uid, t.id, null)
                }, {
                    ListWidget.atualizar(this)
                    carregarTudo()
                }, { e -> status.text = amigavel(e) })
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun addItemLista(grupo: String, entry: EditText, entryValor: EditText? = null) {
        val desc = entry.text.toString().trim()
        if (desc.isEmpty()) return
        val valor = entryValor?.let { parseValor(it.text.toString()) }
        entry.setText("")
        entryValor?.setText("")
        focoGrupoLista = grupo
        io({
            val uid = store.uid!!
            val idToken = Api.idTokenValido(store)
            Api.criarItem(idToken, uid, grupo, desc, valor)
        }, {
            ListWidget.atualizar(this)
            carregarTudo()
        }, { e -> status.text = amigavel(e) })
    }

    // ---------------------------------------------------- WIDGET
    private fun renderWidget() {
        gruposWidgetBox.removeAllViews()
        if (listaGrupos.isEmpty()) {
            gruposHint.visibility = View.VISIBLE
            gruposHint.text = "Nenhum grupo do tipo Lista ainda. Crie um no app do PC."
            return
        }
        gruposHint.visibility = View.GONE
        val selecionados = store.gruposWidget
        for (g in listaGrupos) {
            val cb = CheckBox(this)
            cb.text = g
            cb.setTextColor(0xFFECECF0.toInt())
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

    // ------------------------------------------------------------ util
    private fun aviso(txt: String): View {
        val tv = TextView(this)
        tv.text = txt
        tv.setTextColor(0xFF9A9AA2.toInt())
        tv.textSize = 14f
        tv.setPadding(6, dp(30), 6, 6)
        return tv
    }

    private fun adaptador(itens: List<String>): ArrayAdapter<String> {
        return object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, itens) {
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
    }

    private fun selecionar(sp: Spinner, itens: List<String>, valor: String) {
        val i = itens.indexOf(valor)
        if (i >= 0) sp.setSelection(i)
    }

    private fun rankPrio(p: String): Int = when (p) { "Alta" -> 0; "Baixa" -> 2; else -> 1 }
    private fun corPrio(p: String): Int = when (p) {
        "Alta" -> Color.parseColor("#F38BA8")
        "Baixa" -> Color.parseColor("#A6E3A1")
        else -> Color.parseColor("#F9A825")
    }

    private fun fmtBr(iso: String): String {
        val p = iso.split("-")
        return if (p.size == 3) "${p[2]}/${p[1]}/${p[0]}" else iso
    }

    private fun nowIso(): String {
        val c = Calendar.getInstance()
        return "%04d-%02d-%02dT%02d:%02d:%02d".format(
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
            c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND))
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

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
