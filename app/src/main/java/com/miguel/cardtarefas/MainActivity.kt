package com.miguel.cardtarefas

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.speech.RecognizerIntent
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.widget.FrameLayout
import android.media.MediaRecorder
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.work.WorkManager
import java.io.File
import androidx.activity.result.contract.ActivityResultContracts
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
    private lateinit var tarefasLocked: View
    private lateinit var tarefasContent: View
    private lateinit var btnDesbloqTarefas: TextView
    private lateinit var listasContainer: LinearLayout
    private lateinit var gruposWidgetBox: LinearLayout
    private lateinit var gruposHint: TextView
    private lateinit var btnIa: TextView
    private lateinit var lblTransito: TextView

    // reconhecimento de voz -> interpreta -> confirma
    private val vozLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val fala = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!fala.isNullOrBlank()) confirmarVoz(interpretarComando(fala, listaGrupos))
        }
    }

    // ----- importacao por IA (foto / imagem / audio) -----
    private var cameraFileIA: File? = null
    private var audioFileIA: File? = null
    private var recIA: MediaRecorder? = null
    private var iaAtivos = 0

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        val f = cameraFileIA
        if (ok && f != null) enfileirarArquivoIA(f, "image/jpeg") else f?.delete()
        cameraFileIA = null
    }
    private val galeriaLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val f = novoArqIA("jpg")
                contentResolver.openInputStream(uri)?.use { input -> f.outputStream().use { input.copyTo(it) } }
                enfileirarArquivoIA(f, "image/jpeg")
            } catch (e: Throwable) { status.text = "Nao consegui ler a imagem." }
        }
    }
    private val audioPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) iniciarGravacaoIA() else status.text = "Preciso do microfone para gravar o audio." }

    private var tarefasGrupos: List<String> = emptyList()
    private var listaGrupos: List<String> = emptyList()
    private var todasTarefas: List<Tarefa> = emptyList()
    private var gruposMeta: List<GrupoMeta> = emptyList()
    private val abaAtiva = HashMap<String, String>()
    private var tarefasHash: String? = null            // senha (hash) da nuvem; null = sem protecao
    private var desbloqueioSessao = false              // liberado nesta sessao do app

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
        tarefasLocked = findViewById(R.id.tarefas_locked)
        tarefasContent = findViewById(R.id.tarefas_content)
        btnDesbloqTarefas = findViewById(R.id.btn_desbloq_tarefas)
        listasContainer = findViewById(R.id.listas_container)
        gruposWidgetBox = findViewById(R.id.grupos_widget_container)
        gruposHint = findViewById(R.id.grupos_hint)
        btnIa = findViewById(R.id.btn_ia)
        lblTransito = findViewById(R.id.lbl_transito)

        inEmail.setText(store.email ?: "")
        btnEntrar.setOnClickListener { entrar() }
        btnSair.setOnClickListener { sair() }
        tabTarefas.setOnClickListener { trocarModo("tarefas") }
        tabListas.setOnClickListener { trocarModo("listas") }
        tabWidget.setOnClickListener { trocarModo("widget") }
        btnNovaTarefa.setOnClickListener { abrirDialogTarefa(null) }
        btnIa.setOnClickListener { importarIA() }
        WorkManager.getInstance(this).getWorkInfosByTagLiveData(ImportWorker.TAG)
            .observe(this) { infos ->
                val ativos = infos.count { !it.state.isFinished }
                if (ativos > 0) {
                    lblTransito.visibility = View.VISIBLE
                    lblTransito.text = "\u23f3 $ativos item(ns) em tr\u00e2nsito na IA\u2026"
                } else lblTransito.visibility = View.GONE
                if (ativos < iaAtivos && store.logado) carregarTudo()
                iaAtivos = ativos
            }
        btnDesbloqTarefas.setOnClickListener { pedirSenhaTarefas() }

        spinFiltroStatus.adapter = adaptador(listOf("Pendentes", "Todas", "Concluidas"))

        Notificador.garantirCanal(this)
        NotifWorker.agendar(this)
        pedirPermissaoNotificacao()

        if (store.logado) mostrarApp() else mostrarLogin()
        tratarIntentIA(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        tratarIntentIA(intent)
    }

    private fun tratarIntentIA(i: Intent?) {
        if (i?.getBooleanExtra("abrir_ia", false) == true) {
            i.removeExtra("abrir_ia")
            if (store.logado) {
                trocarModo("listas")
                listasContainer.post { abrirImportacaoIA() }
            }
        }
    }

    private fun pedirPermissaoNotificacao() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val ok = androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!ok) androidx.core.app.ActivityCompat.requestPermissions(
                this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }
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
        WearBridge.publicar(this)          // entrega o login para o relogio
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
                WearBridge.publicar(this)      // avisa o relogio que deslogou
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
            val grupos = Api.listarGruposFull(idToken, uid)
            val tarefasAll = Api.listarTarefasAtivas(idToken, uid)   // so' ativas: economiza cota
            val hash = Api.lerTarefasHash(idToken, uid)
            Triple(grupos, tarefasAll, hash)
        }, { tri ->
            val grupos = tri.first
            gruposMeta = grupos
            tarefasGrupos = grupos.filter { it.tipo == "tarefas" }.map { it.nome }
            listaGrupos = grupos.filter { it.tipo == "lista" }.map { it.nome }
            todasTarefas = tri.second.filter { !it.excluida }
            tarefasHash = tri.third
            Notificador.verificar(this, grupos, tri.second)   // detecta mudancas e notifica
            configurarFiltroGrupo()
            renderTarefas()
            renderListas()
            renderWidget()
            ListWidget.atualizar(this)   // reflete no widget da tela inicial dados vindos do PC/celular
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
    private fun tarefasDesbloqueado(): Boolean {
        val h = tarefasHash
        if (h.isNullOrEmpty()) return true             // sem protecao definida
        if (desbloqueioSessao) return true
        return store.tarefasUnlockHash == h            // lembrado neste aparelho
    }

    private fun pedirSenhaTarefas() {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(12), dp(20), dp(4))
        val ed = EditText(this)
        ed.hint = "Senha"
        ed.setHintTextColor(0xFF9A9AA2.toInt())
        ed.setTextColor(0xFFECECF0.toInt())
        ed.setBackgroundResource(R.drawable.field_bg)
        ed.setPadding(dp(10), dp(8), dp(10), dp(8))
        ed.inputType = android.text.InputType.TYPE_CLASS_TEXT or
            android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        box.addView(ed)
        val cb = CheckBox(this)
        cb.text = "Lembrar neste aparelho"
        cb.setTextColor(0xFFECECF0.toInt())
        box.addView(cb)

        AlertDialog.Builder(this)
            .setTitle("Senha das tarefas")
            .setView(box)
            .setPositiveButton("Entrar") { _, _ ->
                if (hashSenha(ed.text.toString()) == tarefasHash) {
                    desbloqueioSessao = true
                    if (cb.isChecked) store.tarefasUnlockHash = tarefasHash
                    renderTarefas()
                } else {
                    status.text = "Senha incorreta."
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun renderTarefas() {
        // aba Tarefas protegida por senha (as listas ficam livres)
        if (!tarefasDesbloqueado()) {
            tarefasLocked.visibility = View.VISIBLE
            tarefasContent.visibility = View.GONE
            return
        }
        tarefasLocked.visibility = View.GONE
        tarefasContent.visibility = View.VISIBLE

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
        lista = lista.sortedWith(compareBy({ it.concluida }, { it.data ?: "9999" }, { rankPrio(it.prioridade) }))

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

    // ---------------------------------------------------- LISTAS (checklists) / meses
    private fun renderListas() {
        listasContainer.removeAllViews()
        if (listaGrupos.isEmpty()) {
            listasContainer.addView(aviso(
                "Nenhum grupo do tipo Lista.\nCrie um no app do PC (Gerenciar grupos -> tipo Lista), ex.: Mercado."))
            return
        }
        for (g in listaGrupos) listasContainer.addView(blocoLista(g))
    }

    // ---- helpers de meses (abas) ----
    private fun ymHoje(): String {
        val c = java.util.Calendar.getInstance()
        return "%04d-%02d".format(c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1)
    }
    private fun mesLabel(ym: String): String { val p = ym.split("-"); return if (p.size == 2) p[1] + "/" + p[0] else ym }
    private fun ehMesId(id: String): Boolean = Regex("^\\d{4}-\\d{2}$").matches(id)
    private fun parseMes(str: String): String? {
        val s = str.trim()
        var mm = 0; var yy = 0
        var m = Regex("^(\\d{1,2})[/\\-.](\\d{4})$").find(s)
        if (m != null) { mm = m.groupValues[1].toInt(); yy = m.groupValues[2].toInt() }
        else { m = Regex("^(\\d{4})[/\\-.](\\d{1,2})$").find(s); if (m != null) { yy = m.groupValues[1].toInt(); mm = m.groupValues[2].toInt() } }
        if (mm < 1 || mm > 12 || yy < 2000 || yy > 2100) return null
        return "%04d-%02d".format(yy, mm)
    }
    private fun ymAdd(ym: String, n: Int): String { val p = ym.split("-"); val t = p[0].toInt() * 12 + (p[1].toInt() - 1) + n; return "%04d-%02d".format(t / 12, t % 12 + 1) }
    private fun ymDiff(a: String, b: String): Int { val pa = a.split("-"); val pb = b.split("-"); return (pb[0].toInt() * 12 + pb[1].toInt()) - (pa[0].toInt() * 12 + pa[1].toInt()) }

    private fun abasDeLista(nome: String): List<Aba> {
        val g = gruposMeta.firstOrNull { it.nome == nome }
        val ab = g?.abas?.filter { ehMesId(it.id) } ?: emptyList()
        val base = if (ab.isEmpty()) listOf(Aba(ymHoje(), mesLabel(ymHoje()))) else ab
        return base.sortedBy { it.id }.map { Aba(it.id, mesLabel(it.id)) }
    }
    private fun abaAtivaDeLista(nome: String): String {
        val abas = abasDeLista(nome); val id = abaAtiva[nome]
        if (id != null && abas.any { it.id == id }) return id
        // sem selecao manual: sempre o mes mais recente existente
        return abas.maxByOrNull { it.id }?.id ?: abas.last().id
    }
    private fun abaDoItemLista(t: Tarefa): String {
        val abas = abasDeLista(t.grupo)
        return if (t.aba != null && abas.any { it.id == t.aba }) t.aba!! else (abas.firstOrNull()?.id ?: ymHoje())
    }
    private fun itensDaAba(nome: String, abaId: String): List<Tarefa> =
        todasTarefas.filter { it.grupo.equals(nome, true) && abaDoItemLista(it) == abaId }
    private fun saldoAba(nome: String, abaId: String): Double = itensDaAba(nome, abaId).mapNotNull { it.valor }.sum()
    private fun ordenarPend(arr: List<Tarefa>): List<Tarefa> =
        arr.sortedWith(compareBy<Tarefa> { it.ordem ?: 1e15 }.thenByDescending { it.criadaEm ?: "" })

    private fun blocoLista(nome: String): View {
        val abaId = abaAtivaDeLista(nome)
        val itens = itensDaAba(nome, abaId)
        val pend = ordenarPend(itens.filter { !it.concluida })
        val feitos = itens.filter { it.concluida }.sortedByDescending { it.atualizadoEm }
        val saldo = itens.mapNotNull { it.valor }.sum()

        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setBackgroundResource(R.drawable.row_bg)
        card.setPadding(dp(12), dp(12), dp(12), dp(12))
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = dp(10); card.layoutParams = lp

        val cab = LinearLayout(this); cab.orientation = LinearLayout.HORIZONTAL; cab.gravity = Gravity.CENTER_VERTICAL
        val titulo = TextView(this)
        titulo.text = "$nome  ·  ${pend.size} pendente(s)"
        titulo.setTextColor(0xFF89B4FA.toInt()); titulo.textSize = 15f
        titulo.setTypeface(titulo.typeface, android.graphics.Typeface.BOLD)
        titulo.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        cab.addView(titulo)
        val lblSaldo = TextView(this)
        lblSaldo.text = "Saldo do mês: " + fmtValor(saldo)
        lblSaldo.setTextColor(if (saldo < 0) Color.parseColor("#F38BA8") else Color.parseColor("#A6E3A1"))
        lblSaldo.textSize = 13f
        lblSaldo.setTypeface(lblSaldo.typeface, android.graphics.Typeface.BOLD)
        cab.addView(lblSaldo)
        card.addView(cab)

        card.addView(barraAbas(nome, abaId))

        // adicionar item (desc, valor, recorrencia, +)
        val addRow = LinearLayout(this); addRow.orientation = LinearLayout.HORIZONTAL; addRow.gravity = Gravity.CENTER_VERTICAL
        addRow.setPadding(0, dp(8), 0, dp(6))
        val ent = EditText(this); ent.hint = "Novo item..."; ent.setHintTextColor(0xFF9A9AA2.toInt())
        ent.setTextColor(0xFFECECF0.toInt()); ent.setBackgroundResource(R.drawable.field_bg)
        ent.setPadding(dp(10), dp(8), dp(10), dp(8)); ent.maxLines = 1
        ent.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        addRow.addView(ent)
        val entValor = EditText(this); entValor.hint = "valor"; entValor.setHintTextColor(0xFF9A9AA2.toInt())
        entValor.setTextColor(0xFFECECF0.toInt()); entValor.setBackgroundResource(R.drawable.field_bg)
        entValor.setPadding(dp(8), dp(8), dp(8), dp(8)); entValor.maxLines = 1
        entValor.inputType = android.text.InputType.TYPE_CLASS_NUMBER or
            android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
        val evLp = LinearLayout.LayoutParams(dp(68), ViewGroup.LayoutParams.WRAP_CONTENT); evLp.marginStart = dp(6)
        entValor.layoutParams = evLp; addRow.addView(entValor)
        val rec = TextView(this); rec.text = "🔁"; rec.textSize = 16f; rec.gravity = Gravity.CENTER
        rec.setBackgroundResource(R.drawable.field_bg); rec.setPadding(dp(10), dp(8), dp(10), dp(8))
        val recLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); recLp.marginStart = dp(6)
        rec.layoutParams = recLp; addRow.addView(rec)
        val mais = TextView(this); mais.text = "+"; mais.setTextColor(Color.parseColor("#11111B")); mais.textSize = 18f
        mais.setTypeface(mais.typeface, android.graphics.Typeface.BOLD); mais.gravity = Gravity.CENTER
        mais.setBackgroundResource(R.drawable.btn_accent); mais.setPadding(dp(14), dp(8), dp(14), dp(8))
        val maisLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); maisLp.marginStart = dp(6)
        mais.layoutParams = maisLp; addRow.addView(mais)
        card.addView(addRow)

        mais.setOnClickListener { addItemLista(nome, ent, entValor, false) }
        rec.setOnClickListener { addItemLista(nome, ent, entValor, true) }
        ent.setOnEditorActionListener { _, _, _ -> addItemLista(nome, ent, entValor, false); true }
        entValor.setOnEditorActionListener { _, _, _ -> addItemLista(nome, ent, entValor, false); true }
        if (focoGrupoLista == nome) { focoGrupoLista = null; ent.requestFocus() }

        if (pend.isEmpty() && feitos.isEmpty()) {
            val vazio = TextView(this); vazio.text = "Mês vazio. Adicione itens acima."
            vazio.setTextColor(0xFF9A9AA2.toInt()); vazio.textSize = 13f; card.addView(vazio)
        }
        for (t in pend) card.addView(linhaItemLista(t))
        if (feitos.isNotEmpty()) {
            val sep = TextView(this); sep.text = "JA PEGOS"; sep.setTextColor(0xFF9A9AA2.toInt())
            sep.textSize = 10f; sep.setPadding(0, dp(8), 0, dp(2)); card.addView(sep)
            for (t in feitos) card.addView(linhaItemLista(t))
        }
        return card
    }

    private fun barraAbas(nome: String, abaAtivaId: String): View {
        val sv = android.widget.HorizontalScrollView(this); sv.isHorizontalScrollBarEnabled = false
        val svLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        svLp.topMargin = dp(6); svLp.bottomMargin = dp(4); sv.layoutParams = svLp
        val row = LinearLayout(this); row.orientation = LinearLayout.HORIZONTAL
        for (a in abasDeLista(nome)) {
            val ativo = a.id == abaAtivaId
            val chip = LinearLayout(this); chip.orientation = LinearLayout.VERTICAL; chip.gravity = Gravity.CENTER
            chip.setBackgroundResource(if (ativo) R.drawable.btn_accent else R.drawable.field_bg)
            chip.setPadding(dp(12), dp(6), dp(12), dp(6))
            val clp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); clp.marginEnd = dp(6)
            chip.layoutParams = clp
            val mes = TextView(this); mes.text = a.nome; mes.textSize = 12f
            mes.setTypeface(mes.typeface, android.graphics.Typeface.BOLD)
            mes.setTextColor(if (ativo) Color.parseColor("#11111B") else 0xFFECECF0.toInt()); mes.gravity = Gravity.CENTER
            chip.addView(mes)
            val sa = saldoAba(nome, a.id)
            val sl = TextView(this); sl.text = fmtValor(sa); sl.textSize = 11f
            sl.setTextColor(if (ativo) Color.parseColor("#11111B") else if (sa < 0) Color.parseColor("#F38BA8") else Color.parseColor("#A6E3A1"))
            chip.addView(sl)
            chip.setOnClickListener { abaAtiva[nome] = a.id; renderListas() }
            chip.setOnLongClickListener { menuAba(nome, a.id); true }
            row.addView(chip)
        }
        row.addView(chipBarra("+ mês") { dialogCriarMes(nome) })
        row.addView(chipBarra("⋯") { menuAba(nome, abaAtivaId) })
        sv.addView(row); return sv
    }
    private fun chipBarra(txt: String, acao: () -> Unit): View {
        val b = TextView(this); b.text = txt; b.textSize = 12f; b.gravity = Gravity.CENTER
        b.setTextColor(0xFFECECF0.toInt()); b.setBackgroundResource(R.drawable.field_bg)
        b.setPadding(dp(12), dp(10), dp(12), dp(10))
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT); lp.marginEnd = dp(6)
        b.layoutParams = lp; b.setOnClickListener { acao() }; return b
    }

    private fun seloRec(t: Tarefa): String {
        if (t.recorrente) return "  🔁"
        if (t.fimMes != null) {
            val ini = t.inicioMes ?: (t.aba ?: "")
            val aba = t.aba ?: ""
            if (ehMesId(ini) && ehMesId(aba)) {
                val x = ymDiff(ini, aba) + 1
                val n = t.parcelas ?: (ymDiff(ini, t.fimMes!!) + 1)
                return "  ($x/$n)"
            }
        }
        return ""
    }

    private fun linhaItemLista(t: Tarefa): View {
        val row = layoutInflater.inflate(R.layout.item_app, listasContainer, false)
        val check = row.findViewById<ImageView>(R.id.item_check)
        val texto = row.findViewById<TextView>(R.id.item_texto)
        val valor = row.findViewById<TextView>(R.id.item_valor)
        val lixo = row.findViewById<ImageView>(R.id.item_lixo)
        texto.text = t.descricao + seloRec(t)
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
        } else { valor.text = "+ valor"; valor.setTextColor(0xFF9A9AA2.toInt()) }
        valor.setOnClickListener { editarValor(t) }
        val toggle = View.OnClickListener { marcar(t, !t.concluida) }
        check.setOnClickListener(toggle); texto.setOnClickListener(toggle)
        texto.setOnLongClickListener { menuItemLista(t); true }
        row.setOnLongClickListener { menuItemLista(t); true }
        lixo.setOnClickListener { excluirItemLista(t) }
        return row
    }

    private fun menuItemLista(t: Tarefa) {
        val opcoes = ArrayList<String>(); val acoes = ArrayList<() -> Unit>()
        opcoes.add("Editar descrição"); acoes.add { dialogEditarDescricao(t) }
        if (!t.concluida) {
            opcoes.add("Subir"); acoes.add { moverItemLista(t, -1) }
            opcoes.add("Descer"); acoes.add { moverItemLista(t, 1) }
        }
        if (t.recorrente || t.fimMes != null) {
            opcoes.add("Editar recorrência"); acoes.add { dialogRecorrencia(t) }
            opcoes.add("Remover recorrência"); acoes.add { removerRecorrencia(t) }
        } else { opcoes.add("Repetir / parcelar"); acoes.add { dialogRecorrencia(t) } }
        opcoes.add("Excluir"); acoes.add { excluirItemLista(t) }
        AlertDialog.Builder(this).setTitle(t.descricao)
            .setItems(opcoes.toTypedArray()) { _, w -> acoes[w]() }.show()
    }

    private fun menuAba(nome: String, abaId: String) {
        AlertDialog.Builder(this).setTitle("Mês ${mesLabel(abaId)}")
            .setItems(arrayOf("Alterar mês/ano", "Excluir mês")) { _, w ->
                if (w == 0) dialogAlterarMes(nome, abaId) else excluirMes(nome, abaId)
            }.show()
    }

    private fun dialogEditarDescricao(t: Tarefa) {
        val ed = EditText(this); ed.setText(t.descricao)
        AlertDialog.Builder(this).setTitle("Editar item").setView(ed)
            .setPositiveButton("Salvar") { _, _ ->
                val d = ed.text.toString().trim()
                if (d.isNotEmpty()) io({
                    val uid = store.uid!!; val tok = Api.idTokenValido(store)
                    Api.patch(tok, uid, t.id, hashMapOf("descricao" to d, "atualizado_em" to Api.agoraMs()))
                }, { carregarTudo() }, { e -> status.text = amigavel(e) })
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun removerRecorrencia(t: Tarefa) {
        io({
            val uid = store.uid!!; val tok = Api.idTokenValido(store)
            Api.patch(tok, uid, t.id, hashMapOf("recorrente" to false, "fimMes" to null, "parcelas" to null, "atualizado_em" to Api.agoraMs()))
        }, { carregarTudo() }, { e -> status.text = amigavel(e) })
    }

    private fun moverItemLista(t: Tarefa, dir: Int) {
        val abaId = abaDoItemLista(t)
        val arr = ordenarPend(itensDaAba(t.grupo, abaId).filter { !it.concluida }).toMutableList()
        val i = arr.indexOfFirst { it.id == t.id }; if (i < 0) return
        val j = i + dir; if (j < 0 || j >= arr.size) return
        val tmp = arr[i]; arr[i] = arr[j]; arr[j] = tmp
        io({
            val uid = store.uid!!; val tok = Api.idTokenValido(store)
            for ((k, x) in arr.withIndex()) Api.patch(tok, uid, x.id, hashMapOf("ordem" to k.toDouble(), "atualizado_em" to Api.agoraMs()))
            Unit
        }, { carregarTudo() }, { e -> status.text = amigavel(e) })
    }

    private fun dialogRecorrencia(t: Tarefa) {
        val inicio = if (t.aba != null && ehMesId(t.aba!!)) t.aba!! else ymHoje()
        val cont = LinearLayout(this); cont.orientation = LinearLayout.VERTICAL; cont.setPadding(dp(18), dp(8), dp(18), dp(4))
        val rg = android.widget.RadioGroup(this)
        val rbP = android.widget.RadioButton(this); rbP.text = "Nº de parcelas"; rbP.id = View.generateViewId()
        val rbF = android.widget.RadioButton(this); rbF.text = "Até um mês final"; rbF.id = View.generateViewId()
        val rbR = android.widget.RadioButton(this); rbR.text = "Recorrente (todos os meses)"; rbR.id = View.generateViewId()
        for (rb in listOf(rbP, rbF, rbR)) { rb.setTextColor(0xFFECECF0.toInt()); rg.addView(rb) }
        rg.check(rbP.id); cont.addView(rg)
        val edP = EditText(this); edP.hint = "Nº de parcelas (ex.: 12)"; edP.setHintTextColor(0xFF9A9AA2.toInt())
        edP.setTextColor(0xFFECECF0.toInt()); edP.inputType = android.text.InputType.TYPE_CLASS_NUMBER; edP.setText("2"); cont.addView(edP)
        val edF = EditText(this); edF.hint = "Mês final MM/AAAA"; edF.setHintTextColor(0xFF9A9AA2.toInt())
        edF.setTextColor(0xFFECECF0.toInt()); cont.addView(edF)
        val calc = TextView(this); calc.setTextColor(0xFF9A9AA2.toInt()); calc.textSize = 12f; calc.setPadding(0, dp(6), 0, 0); cont.addView(calc)
        val atualiza = {
            when (rg.checkedRadioButtonId) {
                rbP.id -> { val n = (edP.text.toString().toIntOrNull() ?: 1).coerceAtLeast(1); calc.text = "Última parcela em " + mesLabel(ymAdd(inicio, n - 1)) }
                rbF.id -> { val fm = parseMes(edF.text.toString()); calc.text = if (fm != null) "${ymDiff(inicio, fm) + 1} mês(es), até ${mesLabel(fm)}" else "Informe MM/AAAA" }
                else -> calc.text = "Aparece em todos os meses."
            }
        }
        rg.setOnCheckedChangeListener { _, _ -> atualiza() }
        onTextChange(edP) { atualiza() }; onTextChange(edF) { atualiza() }; atualiza()
        AlertDialog.Builder(this).setTitle("Repetir \"${t.descricao}\"").setView(cont)
            .setPositiveButton("Confirmar") { _, _ ->
                val campos = HashMap<String, Any?>()
                campos["serie"] = t.serie ?: Api.novoId()
                campos["inicioMes"] = inicio
                campos["atualizado_em"] = Api.agoraMs()
                when (rg.checkedRadioButtonId) {
                    rbR.id -> { campos["recorrente"] = true; campos["fimMes"] = null; campos["parcelas"] = null }
                    rbF.id -> {
                        val fm = parseMes(edF.text.toString())
                        if (fm == null || ymDiff(inicio, fm) < 0) { status.text = "Mês final inválido."; return@setPositiveButton }
                        campos["recorrente"] = false; campos["fimMes"] = fm; campos["parcelas"] = ymDiff(inicio, fm) + 1
                    }
                    else -> {
                        val n = (edP.text.toString().toIntOrNull() ?: 1).coerceAtLeast(1)
                        campos["recorrente"] = false; campos["fimMes"] = ymAdd(inicio, n - 1); campos["parcelas"] = n
                    }
                }
                io({ val uid = store.uid!!; val tok = Api.idTokenValido(store); Api.patch(tok, uid, t.id, campos) },
                   { carregarTudo() }, { e -> status.text = amigavel(e) })
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun onTextChange(ed: EditText, fn: () -> Unit) {
        ed.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { fn() }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
    }

    private fun dialogCriarMes(nome: String) {
        val abas = abasDeLista(nome)
        val ed = EditText(this); ed.hint = "MM/AAAA"; ed.setText(mesLabel(ymAdd(abas.last().id, 1)))
        AlertDialog.Builder(this).setTitle("Novo mês").setView(ed)
            .setPositiveButton("Criar") { _, _ ->
                val ym = parseMes(ed.text.toString())
                if (ym == null) { status.text = "Use MM/AAAA, ex.: 11/2026."; return@setPositiveButton }
                if (abas.any { it.id == ym }) { abaAtiva[nome] = ym; renderListas(); return@setPositiveButton }
                val origem = abaAtivaDeLista(nome)
                val recs = itensDaAba(nome, origem).filter {
                    (it.recorrente || it.fimMes != null) &&
                    (if (it.recorrente) true else ym <= it.fimMes!!) &&
                    (it.inicioMes == null || ym >= it.inicioMes!!)
                }
                val curAbas = abasDeLista(nome)
                io({
                    val uid = store.uid!!; val tok = Api.idTokenValido(store)
                    val raw = Api.lerGruposRaw(tok, uid)
                    val alvo = raw.firstOrNull { (it["nome"] as? String) == nome }
                    val listaAbas = ArrayList<HashMap<String, Any?>>()
                    for (a in curAbas) { val m = HashMap<String, Any?>(); m["id"] = a.id; m["nome"] = mesLabel(a.id); listaAbas.add(m) }
                    val nm = HashMap<String, Any?>(); nm["id"] = ym; nm["nome"] = mesLabel(ym); listaAbas.add(nm)
                    listaAbas.sortBy { it["id"] as String }
                    if (alvo != null) alvo["abas"] = listaAbas
                    Api.salvarGrupos(tok, uid, raw)
                    var k = 0.0
                    for (t in recs) {
                        val nt = Tarefa(
                            id = Api.novoId(), grupo = nome, aba = ym, descricao = t.descricao,
                            concluida = false, criadaEm = nowIso(), atualizadoEm = Api.agoraMs(),
                            excluida = false, valor = t.valor, recorrente = t.recorrente,
                            serie = (t.serie ?: Api.novoId()), inicioMes = (t.inicioMes ?: origem),
                            fimMes = t.fimMes, parcelas = t.parcelas, ordem = k)
                        Api.gravarTarefa(tok, uid, nt); k += 1.0
                    }
                    Unit
                }, { abaAtiva[nome] = ym; carregarTudo() }, { e -> status.text = amigavel(e) })
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun dialogAlterarMes(nome: String, abaId: String) {
        val ed = EditText(this); ed.setText(mesLabel(abaId))
        AlertDialog.Builder(this).setTitle("Alterar mês").setView(ed)
            .setPositiveButton("Salvar") { _, _ ->
                val ym = parseMes(ed.text.toString())
                if (ym == null) { status.text = "Use MM/AAAA."; return@setPositiveButton }
                val abas = abasDeLista(nome)
                if (ym != abaId && abas.any { it.id == ym }) { status.text = "Já existe o mês ${mesLabel(ym)}."; return@setPositiveButton }
                io({
                    val uid = store.uid!!; val tok = Api.idTokenValido(store)
                    val raw = Api.lerGruposRaw(tok, uid)
                    val alvo = raw.firstOrNull { (it["nome"] as? String) == nome }
                    val listaAbas = ArrayList<HashMap<String, Any?>>()
                    for (a in abas) { val id = if (a.id == abaId) ym else a.id; val m = HashMap<String, Any?>(); m["id"] = id; m["nome"] = mesLabel(id); listaAbas.add(m) }
                    listaAbas.sortBy { it["id"] as String }
                    if (alvo != null) alvo["abas"] = listaAbas
                    Api.salvarGrupos(tok, uid, raw)
                    for (t in itensDaAba(nome, abaId)) Api.patch(tok, uid, t.id, hashMapOf("aba" to ym, "atualizado_em" to Api.agoraMs()))
                    Unit
                }, { abaAtiva[nome] = ym; carregarTudo() }, { e -> status.text = amigavel(e) })
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun excluirMes(nome: String, abaId: String) {
        val abas = abasDeLista(nome)
        if (abas.size <= 1) { status.text = "A lista precisa de ao menos um mês."; return }
        val itens = itensDaAba(nome, abaId)
        if (itens.isNotEmpty()) { status.text = "O mês ${mesLabel(abaId)} só pode ser excluído vazio (tem ${itens.size})."; return }
        AlertDialog.Builder(this).setTitle("Excluir mês").setMessage("Excluir o mês ${mesLabel(abaId)}?")
            .setPositiveButton("Excluir") { _, _ ->
                val restante = abas.filter { it.id != abaId }
                io({
                    val uid = store.uid!!; val tok = Api.idTokenValido(store)
                    val raw = Api.lerGruposRaw(tok, uid)
                    val alvo = raw.firstOrNull { (it["nome"] as? String) == nome }
                    val listaAbas = ArrayList<HashMap<String, Any?>>()
                    for (a in restante) { val m = HashMap<String, Any?>(); m["id"] = a.id; m["nome"] = mesLabel(a.id); listaAbas.add(m) }
                    if (alvo != null) alvo["abas"] = listaAbas
                    Api.salvarGrupos(tok, uid, raw)
                    Unit
                }, { abaAtiva[nome] = restante.last().id; carregarTudo() }, { e -> status.text = amigavel(e) })
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun excluirItemLista(t: Tarefa) {
        if (!(t.recorrente || t.fimMes != null)) {
            io({ val uid = store.uid!!; val tok = Api.idTokenValido(store); Api.excluir(tok, uid, t.id) },
               { ListWidget.atualizar(this); carregarTudo() }, { e -> status.text = amigavel(e) })
            return
        }
        AlertDialog.Builder(this).setTitle("Item que se repete")
            .setMessage("\"${t.descricao}\" se repete em outros meses. O que deseja fazer?")
            .setPositiveButton("Parar de repetir") { _, _ -> pararRecorrencia(t) }
            .setNeutralButton("Só deste mês") { _, _ ->
                io({ val uid = store.uid!!; val tok = Api.idTokenValido(store); Api.excluir(tok, uid, t.id) },
                   { ListWidget.atualizar(this); carregarTudo() }, { e -> status.text = amigavel(e) })
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun pararRecorrencia(t: Tarefa) {
        io({
            val uid = store.uid!!; val tok = Api.idTokenValido(store)
            Api.excluir(tok, uid, t.id)
            if (t.serie != null) for (x in todasTarefas.filter { it.serie == t.serie && it.id != t.id && (it.recorrente || it.fimMes != null) })
                Api.patch(tok, uid, x.id, hashMapOf("recorrente" to false, "fimMes" to null, "parcelas" to null, "atualizado_em" to Api.agoraMs()))
            Unit
        }, { ListWidget.atualizar(this); carregarTudo() }, { e -> status.text = amigavel(e) })
    }

    private fun editarValor(t: Tarefa) {
        val ed = EditText(this)
        ed.inputType = android.text.InputType.TYPE_CLASS_NUMBER or
            android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
        ed.hint = "valor (use - para negativo)"
        if (t.valor != null) ed.setText(fmtValor(t.valor))
        AlertDialog.Builder(this).setTitle("Valor de \"${t.descricao}\"").setView(ed)
            .setPositiveButton("Salvar") { _, _ ->
                val v = parseValor(ed.text.toString())
                io({ val uid = store.uid!!; val tok = Api.idTokenValido(store); Api.atualizarValor(tok, uid, t.id, v) },
                   { ListWidget.atualizar(this); carregarTudo() }, { e -> status.text = amigavel(e) })
            }
            .setNeutralButton("Remover") { _, _ ->
                io({ val uid = store.uid!!; val tok = Api.idTokenValido(store); Api.atualizarValor(tok, uid, t.id, null) },
                   { ListWidget.atualizar(this); carregarTudo() }, { e -> status.text = amigavel(e) })
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun addItemLista(grupo: String, entry: EditText, entryValor: EditText?, recorrente: Boolean = false) {
        val desc = entry.text.toString().trim()
        if (desc.isEmpty()) { if (recorrente) status.text = "Digite o item antes de marcar recorrência."; return }
        val valor = entryValor?.let { parseValor(it.text.toString()) }
        val abaId = abaAtivaDeLista(grupo)
        entry.setText(""); entryValor?.setText(""); focoGrupoLista = grupo
        io({
            val uid = store.uid!!; val tok = Api.idTokenValido(store)
            val menor = itensDaAba(grupo, abaId).filter { !it.concluida }.mapNotNull { it.ordem }.minOrNull() ?: 0.0
            val t = Tarefa(id = Api.novoId(), grupo = grupo, aba = abaId, descricao = desc,
                concluida = false, criadaEm = nowIso(), atualizadoEm = Api.agoraMs(),
                excluida = false, valor = valor, ordem = menor - 1.0)
            Api.gravarTarefa(tok, uid, t)
            t.id
        }, { novoId ->
            ListWidget.atualizar(this); carregarTudo()
            if (recorrente) dialogRecorrencia(Tarefa(id = novoId, grupo = grupo, aba = abaId, descricao = desc, valor = valor))
        }, { e -> status.text = amigavel(e) })
    }


    // ============================================ IMPORTACAO POR IA
    private fun importarIA() {
        if (listaGrupos.isEmpty()) { status.text = "Crie um grupo do tipo Lista primeiro (no PC ou web)."; return }
        abrirImportacaoIA()
    }

    private fun abrirImportacaoIA() {
        garantirCodigoIa { escolherFonteIA() }
    }

    private fun garantirCodigoIa(aoConcluir: () -> Unit) {
        if (!store.codigoIa.isNullOrBlank()) { aoConcluir(); return }
        val ed = EditText(this); ed.setText("murilo"); ed.setSelectAllOnFocus(true)
        ed.setTextColor(0xFFECECF0.toInt()); ed.setBackgroundResource(R.drawable.field_bg)
        ed.setPadding(dp(10), dp(8), dp(10), dp(8))
        AlertDialog.Builder(this)
            .setTitle("C\u00f3digo da IA")
            .setMessage("Informe o c\u00f3digo que completa a chave da IA (o mesmo definido no app web/PC). Fica guardado s\u00f3 neste aparelho.")
            .setView(ed)
            .setPositiveButton("Salvar") { _, _ ->
                val c = ed.text.toString().trim()
                if (c.isEmpty()) { status.text = "C\u00f3digo vazio."; return@setPositiveButton }
                store.codigoIa = c; aoConcluir()
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun escolherFonteIA() {
        val opcoes = arrayOf("\ud83c\udfa4 Voz", "\ud83d\uddbc\ufe0f Imagem", "\ud83d\udcf7 C\u00e2mera")
        AlertDialog.Builder(this).setTitle("Importar com IA")
            .setItems(opcoes) { _, w ->
                when (w) { 0 -> pedirAudioIA(); 1 -> galeriaLauncher.launch("image/*"); else -> abrirCameraIA() }
            }.show()
    }

    private fun novoArqIA(ext: String): File {
        val dir = File(cacheDir, "ia"); dir.mkdirs()
        return File(dir, "ia_${System.currentTimeMillis()}.$ext")
    }

    private fun abrirCameraIA() {
        try {
            val f = novoArqIA("jpg"); cameraFileIA = f
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
            cameraLauncher.launch(uri)
        } catch (e: Throwable) { status.text = "C\u00e2mera indispon\u00edvel neste celular." }
    }

    private fun pedirAudioIA() {
        val ok = androidx.core.content.ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (ok) iniciarGravacaoIA() else audioPermLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
    }

    @Suppress("DEPRECATION")
    private fun iniciarGravacaoIA() {
        val f = novoArqIA("aac"); audioFileIA = f
        try {
            val r = if (android.os.Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(96000); r.setAudioSamplingRate(44100)
            r.setOutputFile(f.absolutePath)
            r.prepare(); r.start(); recIA = r
        } catch (e: Throwable) {
            recIA = null; f.delete(); status.text = "N\u00e3o consegui iniciar a grava\u00e7\u00e3o."; return
        }
        val cont = LinearLayout(this); cont.orientation = LinearLayout.VERTICAL
        cont.gravity = Gravity.CENTER_HORIZONTAL; cont.setPadding(dp(20), dp(26), dp(20), dp(8))
        val fl = FrameLayout(this)
        fl.layoutParams = LinearLayout.LayoutParams(dp(130), dp(130))
        val ring = View(this); ring.setBackgroundResource(R.drawable.pulse_circle)
        val ringLp = FrameLayout.LayoutParams(dp(64), dp(64)); ringLp.gravity = Gravity.CENTER; ring.layoutParams = ringLp
        val mic = ImageView(this); mic.setImageResource(R.drawable.ic_mic)
        val micLp = FrameLayout.LayoutParams(dp(54), dp(54)); micLp.gravity = Gravity.CENTER; mic.layoutParams = micLp
        fl.addView(ring); fl.addView(mic); cont.addView(fl)
        val txtL = TextView(this); txtL.text = "Ouvindo\u2026 fale os itens e valores."
        txtL.setTextColor(0xFFECECF0.toInt()); txtL.gravity = Gravity.CENTER; txtL.setPadding(0, dp(10), 0, 0)
        cont.addView(txtL)
        val anim = AnimatorSet()
        val sx = ObjectAnimator.ofFloat(ring, "scaleX", 1f, 2.3f)
        val sy = ObjectAnimator.ofFloat(ring, "scaleY", 1f, 2.3f)
        val al = ObjectAnimator.ofFloat(ring, "alpha", 0.45f, 0f)
        for (a in listOf(sx, sy, al)) { a.repeatCount = ObjectAnimator.INFINITE; a.repeatMode = ObjectAnimator.RESTART; a.duration = 1300 }
        val mx = ObjectAnimator.ofFloat(mic, "scaleX", 1f, 1.12f)
        val my = ObjectAnimator.ofFloat(mic, "scaleY", 1f, 1.12f)
        for (a in listOf(mx, my)) { a.repeatCount = ObjectAnimator.INFINITE; a.repeatMode = ObjectAnimator.REVERSE; a.duration = 650 }
        anim.playTogether(sx, sy, al, mx, my); anim.start()
        val dlg = AlertDialog.Builder(this).setCancelable(false).setView(cont)
            .setPositiveButton("Parar e enviar") { _, _ -> pararGravacaoIA(true) }
            .setNegativeButton("Cancelar") { _, _ -> pararGravacaoIA(false) }
            .create()
        dlg.setOnDismissListener { try { anim.cancel() } catch (_: Throwable) {} }
        dlg.show()
    }

    private fun pararGravacaoIA(enviar: Boolean) {
        val r = recIA; recIA = null
        try { r?.stop() } catch (_: Throwable) {}
        try { r?.release() } catch (_: Throwable) {}
        val f = audioFileIA; audioFileIA = null
        if (enviar && f != null && f.exists() && f.length() > 0) enfileirarArquivoIA(f, "audio/aac")
        else f?.delete()
    }

    private fun enfileirarArquivoIA(arquivo: File, mime: String) {
        ImportWorker.enfileirar(this, arquivo.absolutePath, mime)
        store.iaPendentes = store.iaPendentes + 1
        ListWidget.atualizar(this)
        lblTransito.visibility = View.VISIBLE
        lblTransito.text = "\u23f3 Enviado para a IA \u2014 em instantes na lista\u2026"
        Toast.makeText(this, "Enviado para a IA \u2014 em instantes aparece na lista.", Toast.LENGTH_LONG).show()
    }

    // ---------------------------------------------------- VOZ (ditar item)
    private fun iniciarDitado() {
        if (listaGrupos.isEmpty()) {
            status.text = "Crie um grupo do tipo Lista primeiro."
            return
        }
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Ex.: adicionar leite valor 10 na lista Mercado")
        try {
            vozLauncher.launch(i)
        } catch (e: Exception) {
            status.text = "Reconhecimento de voz indisponivel neste celular."
        }
    }

    private fun confirmarVoz(cmd: ComandoVoz) {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(12), dp(20), dp(4))

        fun rotulo(txt: String): TextView {
            val t = TextView(this)
            t.text = txt; t.setTextColor(0xFF9A9AA2.toInt()); t.textSize = 12f
            t.setTypeface(t.typeface, android.graphics.Typeface.BOLD)
            t.setPadding(0, dp(8), 0, dp(2))
            return t
        }
        fun campo(): EditText {
            val e = EditText(this)
            e.setTextColor(0xFFECECF0.toInt())
            e.setBackgroundResource(R.drawable.field_bg)
            e.setPadding(dp(10), dp(8), dp(10), dp(8))
            e.maxLines = 1
            return e
        }

        box.addView(rotulo("Item"))
        val edItem = campo(); edItem.setText(cmd.item); box.addView(edItem)

        box.addView(rotulo("Valor (opcional)"))
        val edValor = campo()
        edValor.inputType = android.text.InputType.TYPE_CLASS_NUMBER or
            android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or
            android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
        if (cmd.valor != null) edValor.setText(fmtValor(cmd.valor))
        box.addView(edValor)

        box.addView(rotulo("Lista"))
        val sp = Spinner(this)
        sp.adapter = adaptador(listaGrupos)
        val gi = listaGrupos.indexOfFirst { it.equals(cmd.grupo, true) }
        if (gi >= 0) sp.setSelection(gi)
        sp.setBackgroundResource(R.drawable.field_bg)
        box.addView(sp)

        AlertDialog.Builder(this)
            .setTitle("Confirmar item")
            .setView(box)
            .setPositiveButton("Adicionar") { _, _ ->
                val desc = edItem.text.toString().trim()
                if (desc.isEmpty()) return@setPositiveButton
                val grupo = (sp.selectedItem as? String) ?: listaGrupos.first()
                val valor = parseValor(edValor.text.toString())
                status.text = "Adicionando..."
                io({
                    val uid = store.uid!!
                    val idToken = Api.idTokenValido(store)
                    Api.criarItem(idToken, uid, grupo, desc, valor)
                }, {
                    ListWidget.atualizar(this)
                    trocarModo("listas")
                    carregarTudo()
                }, { e -> status.text = amigavel(e) })
            }
            .setNegativeButton("Cancelar", null)
            .show()
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
