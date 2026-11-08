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
import android.net.Uri
import android.provider.Settings
import android.widget.ScrollView
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
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
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import androidx.appcompat.widget.SwitchCompat
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
    private lateinit var tabAgenda: TextView
    private lateinit var boxAgenda: View
    private lateinit var agendaContainer: LinearLayout
    private lateinit var tabWish: TextView
    private lateinit var boxWish: View
    private lateinit var wishContainer: LinearLayout
    private var desejos: List<Desejo> = emptyList()
    private var wishGraf = false             // tela de graficos da wishlist
    private var wishAgr = "Tipo"             // agrupar por: Tipo | Dono | Mes | Prioridade
    private var wishDono = "Todos"
    private lateinit var gruposWidgetBox: LinearLayout
    private lateinit var gruposHint: TextView
    private lateinit var btnIa: TextView
    private lateinit var lblTransito: TextView
    private lateinit var swNotif: SwitchCompat
    private lateinit var notifStatus: TextView
    private lateinit var btnNotifAcesso: TextView
    private var corteModoTela = ""
    private var projetado: MutableMap<String, MutableMap<String, Double>> = HashMap()   // limite por lista e mes (aba)
    private var quitacoes: MutableMap<String, MutableMap<String, MutableList<Quitacao>>> = HashMap()   // quitacoes parciais

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
    private var iaModoAtual = "lista"
    private var abrirCompromissoId: String? = null
    private var abrirItemId: String? = null

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

    // ----- anexos da Agenda -----
    private var anexoAlvo: Compromisso? = null
    private var anexoLista: ArrayList<HashMap<String, Any?>>? = null
    private var anexoRefresh: (() -> Unit)? = null
    private val anexoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onAnexoEscolhido(uri)
    }

    private var tarefasGrupos: List<String> = emptyList()
    private var listaGrupos: List<String> = emptyList()
    private var todasTarefas: List<Tarefa> = emptyList()
    private var gruposMeta: List<GrupoMeta> = emptyList()
    private var compromissos: List<Compromisso> = emptyList()
    private var agendaDataSel: String? = null
    // agenda: visao "lista" ou "cal" (calendario), mes exibido (y*12+m) e dia escolhido
    private var agVista = "lista"
    private var agMesCal = -1
    private var agDiaSel: String? = null
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
        tabAgenda = findViewById(R.id.tab_agenda)
        boxAgenda = findViewById(R.id.box_agenda)
        agendaContainer = findViewById(R.id.agenda_container)
        tabWish = findViewById(R.id.tab_wish)
        boxWish = findViewById(R.id.box_wish)
        wishContainer = findViewById(R.id.wish_container)
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
        swNotif = findViewById(R.id.sw_notif)
        notifStatus = findViewById(R.id.notif_status)
        btnNotifAcesso = findViewById(R.id.btn_notif_acesso)
        configurarCorte()
        swNotif.isChecked = store.notifLerLigado
        swNotif.setOnCheckedChangeListener { _, v ->
            store.notifLerLigado = v
            atualizarStatusNotif()
        }
        btnNotifAcesso.setOnClickListener {
            try { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            catch (_: Throwable) { try { startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) } catch (_: Throwable) {} }
        }
        atualizarStatusNotif()

        inEmail.setText(store.email ?: "")
        btnEntrar.setOnClickListener { entrar() }
        btnSair.setOnClickListener { sair() }
        tabTarefas.setOnClickListener { trocarModo("tarefas") }
        tabListas.setOnClickListener { trocarModo("listas") }
        tabWidget.setOnClickListener { trocarModo("widget") }
        tabWish.setOnClickListener { trocarModo("wish") }
        tabAgenda.setOnClickListener { trocarModo("agenda") }
        btnNovaTarefa.setOnClickListener { abrirDialogTarefa(null) }
        btnIa.setOnClickListener { importarIA() }
        WorkManager.getInstance(this).getWorkInfosByTagLiveData(ImportWorker.TAG)
            .observe(this) { infos ->
                val ativos = infos.count { !it.state.isFinished }
                if (ativos > 0) {
                    lblTransito.visibility = View.VISIBLE
                    lblTransito.text = "\u23f3 Carregando\u2026"
                } else lblTransito.visibility = View.GONE
                if (ativos < iaAtivos && store.logado) carregarTudo()
                iaAtivos = ativos
            }
        btnDesbloqTarefas.setOnClickListener { pedirSenhaTarefas() }

        spinFiltroStatus.adapter = adaptador(listOf("Pendentes", "Todas", "Concluidas"))

        Notificador.garantirCanal(this)
        NotifWorker.agendar(this)
        WishWorker.agendar(this)   // wishlist: 1x por dia, 7:30
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
            if (store.logado) { trocarModo("listas"); listasContainer.post { abrirImportacaoIA() } }
        }
        val cid = i?.getStringExtra("abrir_compromisso")
        if (cid != null) {
            i.removeExtra("abrir_compromisso")
            if (store.logado) { abrirCompromissoId = cid; trocarModo("agenda"); carregarAgenda() }
        }
        // toque na notificacao: lista/aba do mes (e, se houver, o item para editar)
        val nl = i?.getStringExtra("abrir_lista"); val na = i?.getStringExtra("abrir_aba")
        if (nl != null) { i.removeExtra("abrir_lista"); i.removeExtra("abrir_aba")
            if (store.logado) { if (na != null) abaAtiva[nl] = na; trocarModo("listas") } }
        val iid = i?.getStringExtra("abrir_item")
        if (iid != null) {
            i.removeExtra("abrir_item")
            if (store.logado) { abrirItemId = iid; trocarModo("listas") }
        }
        if (store.logado && (nl != null || iid != null)) carregarTudo()
        val add = i?.getStringExtra("abrir_add")
        if (add != null) {
            i.removeExtra("abrir_add")
            if (store.logado) {
                if (add == "agenda") { trocarModo("agenda"); agendaContainer.post { popupAgenda() } }
                else { trocarModo("listas"); listasContainer.post { popupLista() } }
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
        atualizarStatusNotif()
    }

    // ------------------------------------------------------------ corte da fatura (Ajustes)
    private fun configurarCorte() {
        val bFixo = findViewById<TextView>(R.id.corte_fixo)
        val bUtil = findViewById<TextView>(R.id.corte_util)
        val linha = findViewById<View>(R.id.corte_linha)
        val antes = findViewById<TextView>(R.id.corte_antes)
        val depois = findViewById<TextView>(R.id.corte_depois)
        val edDia = findViewById<EditText>(R.id.corte_dia)
        val prev = findViewById<TextView>(R.id.corte_prev)
        corteModoTela = store.corteModo
        edDia.setText(store.corteDia.toString())
        fun salvar() {
            val max = if (corteModoTela == "util") 23 else 31
            val d = (edDia.text.toString().toIntOrNull() ?: 5).coerceIn(1, max)
            store.corteModo = corteModoTela
            store.corteDia = d
            prev.text = Corte.previa(corteModoTela, d)
        }
        fun pintar() {
            for ((b, k) in listOf(bFixo to "fixo", bUtil to "util")) {
                val on = k == corteModoTela
                if (on) { b.setBackgroundResource(R.drawable.tab_on); b.setTextColor(Color.parseColor("#2A0F22")) }
                else { b.background = null; b.setTextColor(0xFFB99BB4.toInt()) }
            }
            linha.visibility = if (corteModoTela.isEmpty()) View.GONE else View.VISIBLE
            antes.visibility = if (corteModoTela == "fixo") View.VISIBLE else View.GONE
            depois.text = if (corteModoTela == "util") "º dia útil do mês" else "de cada mês"
            salvar()
        }
        bFixo.setOnClickListener { corteModoTela = if (corteModoTela == "fixo") "" else "fixo"; pintar() }
        bUtil.setOnClickListener { corteModoTela = if (corteModoTela == "util") "" else "util"; pintar() }
        edDia.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { salvar() }
        })
        pintar()
    }

    private fun acessoNotifConcedido(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    private fun atualizarStatusNotif() {
        if (!::swNotif.isInitialized) return
        swNotif.isChecked = store.notifLerLigado
        val concedido = acessoNotifConcedido()
        notifStatus.text = when {
            !store.notifLerLigado -> "Desligado."
            concedido -> "Ativo. Compras do cartão 5261 serão lançadas sozinhas."
            else -> "Ligado, mas falta conceder \"Acesso a notificações\" para o app abaixo."
        }
        btnNotifAcesso.visibility = if (store.notifLerLigado && !concedido) View.VISIBLE else View.GONE
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
        boxAgenda.visibility = if (m == "agenda") View.VISIBLE else View.GONE
        boxWish.visibility = if (m == "wish") View.VISIBLE else View.GONE
        for (t in listOf(tabTarefas, tabListas, tabAgenda, tabWish, tabWidget)) { t.setTextColor(0xFFB99BB4.toInt()); t.background = null }
        val ativa = when (m) { "listas" -> tabListas; "agenda" -> tabAgenda; "wish" -> tabWish; "widget" -> tabWidget; else -> tabTarefas }
        ativa.setTextColor(Color.parseColor("#2A0F22")); ativa.setBackgroundResource(R.drawable.tab_on)
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
            carregarAgenda()
            carregarWishlist()
            ListWidget.atualizar(this)   // reflete no widget da tela inicial dados vindos do PC/celular
            io({ Api.lerProjetado(Api.idTokenValido(store), store.uid!!) }, { pj -> projetado = pj; renderListas() }, { })
            io({ Api.lerQuitacoes(Api.idTokenValido(store), store.uid!!) }, { q -> quitacoes = q; renderListas() }, { })
            TarefasWidget.atualizar(this)
            status.text = ""
            abrirItemId?.let { id -> abrirItemId = null; todasTarefas.firstOrNull { it.id == id }?.let {
                abaAtiva[it.grupo] = abaDoItemLista(it); trocarModo("listas"); renderListas(); editorItemLista(it) } }
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
        ed.setHintTextColor(0xFFB99BB4.toInt())
        ed.setTextColor(0xFFFBEEF6.toInt())
        ed.setBackgroundResource(R.drawable.field_bg)
        ed.setPadding(dp(10), dp(8), dp(10), dp(8))
        ed.inputType = android.text.InputType.TYPE_CLASS_TEXT or
            android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        box.addView(ed)
        val cb = CheckBox(this)
        cb.text = "Lembrar neste aparelho"
        cb.setTextColor(0xFFFBEEF6.toInt())
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
            desc.setTextColor(0xFFB99BB4.toInt())
            done.setImageResource(R.drawable.ic_check_on)
        } else {
            desc.paintFlags = desc.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            desc.setTextColor(0xFFFBEEF6.toInt())
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
            TarefasWidget.atualizar(this)
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
        val btCom = TextView(this); btCom.text = "\uD83D\uDCC8  Comprometimento futuro"; btCom.gravity = Gravity.CENTER
        btCom.setTextColor(0xFFFBEEF6.toInt()); btCom.setBackgroundResource(R.drawable.field_bg); btCom.textSize = 14f
        btCom.setTypeface(btCom.typeface, android.graphics.Typeface.BOLD)
        btCom.setPadding(dp(14), dp(12), dp(14), dp(12))
        val bcLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); bcLp.bottomMargin = dp(10); btCom.layoutParams = bcLp
        btCom.setOnClickListener { telaComprometimento() }
        listasContainer.addView(btCom)
        for (g in listaGrupos) listasContainer.addView(blocoLista(g))
    }

    // Comprometimento futuro: do mes atual ate o ultimo mes com parcela (lista ou wishlist).
    // Meses com aba criada: soma o que esta na aba (todas as listas). Meses sem aba: parcelas finitas
    // ativas + recorrentes. "Simular com a wishlist" encaixa as parcelas da wishlist DENTRO do projetado
    // (o projetado nao muda: o que passar dele aparece em vermelho).
    private var comprSimular = false
    private fun telaComprometimento() {
        val listas = gruposMeta.filter { it.tipo == "lista" }
        if (listas.isEmpty()) { status.text = "Nenhuma lista criada ainda."; return }
        val nomesListas = listas.map { it.nome }
        val hoje = ymHoje()
        val abasCriadas = listas.flatMap { it.abas }.map { it.id }.filter { ehMesId(it) }.toSet()
        val ultimo = abasCriadas.maxOrNull() ?: hoje
        val todas = todasTarefas.filter { !it.excluida && listaGrupos.any { g -> g.equals(it.grupo, true) } }
        val finitas = todas.filter { it.fimMes != null && !it.recorrente }.distinctBy { it.serie ?: it.id }
        val recorrentes = todas.filter { it.recorrente }.distinctBy { it.serie ?: it.id }
        // simulacao: so itens nao comprados, com mes previsto e cuja compra e responsabilidade da Ariane
        val wlTodos = desejos.filter { !it.comprado && it.previsao.isNotEmpty() }
        val wl = wlTodos.filter { it.responsavel == "Ariane" }
        val semResp = wlTodos.count { it.responsavel.isEmpty() }
        val fimWl = wl.map { ymAdd(it.previsao, it.parcelas - 1) }.maxOrNull() ?: hoje
        val tetoMax = projetado.values.flatMap { it.keys }.filter { ehMesId(it) }.maxOrNull() ?: hoje
        var horizon = listOf(ultimo, finitas.mapNotNull { it.fimMes }.maxOrNull() ?: ultimo, fimWl, tetoMax, ymAdd(hoje, 11)).maxOrNull()!!
        if (ymDiff(hoje, horizon) > 24) horizon = ymAdd(hoje, 24)

        data class Mes(val ym: String, val base: Double, val itens: List<Pair<String, Double>>, val wl: Double, val wlItens: List<Pair<String, Double>>)
        val meses = ArrayList<Mes>()
        var m = hoje
        while (m <= horizon) {
            val itens = ArrayList<Pair<String, Double>>()
            if (abasCriadas.contains(m)) {
                for (g in listas) for (t in itensDaAba(g.nome, m)) if (t.valor != null) itens.add(t.descricao to t.valor)
            } else {
                for (f in finitas) { val ini = f.inicioMes ?: f.aba ?: m
                    if (m >= ini && m <= f.fimMes!!) { val n = f.parcelas ?: (ymDiff(ini, f.fimMes!!) + 1); itens.add("${f.descricao} (${ymDiff(ini, m) + 1}/$n)" to (f.valor ?: 0.0)) } }
                for (r in recorrentes) itens.add("${r.descricao} (todo mês)" to (r.valor ?: 0.0))
            }
            val wi = wl.filter { m >= it.previsao && ymDiff(it.previsao, m) < it.parcelas }
                .map { "${it.nome} (${ymDiff(it.previsao, m) + 1}/${it.parcelas})" to it.parcela }
            meses.add(Mes(m, kotlin.math.abs(itens.sumOf { it.second }), itens, wi.sumOf { it.second }, wi))
            m = ymAdd(m, 1)
        }
        // teto do mes = soma do teto de cada lista (lido sempre na hora, pois pode ser editado aqui)
        fun tetoMes(ym: String): Double? = nomesListas.mapNotNull { projetado[it]?.get(ym) }.let { if (it.isEmpty()) null else it.sum() }

        val box = LinearLayout(this); box.orientation = LinearLayout.VERTICAL; box.setPadding(dp(16), dp(8), dp(16), dp(8))
        val cab = LinearLayout(this); cab.orientation = LinearLayout.VERTICAL; box.addView(cab)
        val corpo = LinearLayout(this); corpo.orientation = LinearLayout.VERTICAL; box.addView(corpo)
        val ck = CheckBox(this); ck.text = "Simular com a wishlist"; ck.isChecked = comprSimular; ck.setTextColor(0xFFFBEEF6.toInt()); ck.textSize = 14f
        ck.setTypeface(ck.typeface, android.graphics.Typeface.BOLD)
        ck.setBackgroundResource(R.drawable.proj_btn); ck.setPadding(dp(8), dp(6), dp(12), dp(6))
        cab.addView(ck)
        val aviso = TextView(this); aviso.textSize = 11.5f; aviso.setTextColor(0xFFC7A8F7.toInt()); aviso.setPadding(dp(4), dp(6), 0, 0)
        aviso.text = "Só soma itens da wishlist cujo responsável é a Ariane." + (if (semResp == 1) " 1 item sem responsável ficou de fora." else if (semResp > 1) " $semResp itens sem responsável ficaram de fora." else "")
        cab.addView(aviso)
        val leg = TextView(this); leg.textSize = 11.5f; leg.setTextColor(0xFFB99BB4.toInt()); leg.setPadding(dp(2), dp(8), 0, dp(4))
        val sp = android.text.SpannableStringBuilder()
        fun cor(t: String, c: String) { val i = sp.length; sp.append(t); sp.setSpan(android.text.style.ForegroundColorSpan(Color.parseColor(c)), i, sp.length, 0) }
        cor("■ ", "#FF86BD"); sp.append("comprometido   "); cor("■ ", "#C7A8F7"); sp.append("wishlist   "); cor("┃ ", "#F6C177"); sp.append("teto")
        leg.text = sp; cab.addView(leg)

        val mono = android.graphics.Typeface.MONOSPACE
        val monoB = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
        fun celula(rotulo: String, valor: String, cor: Int): LinearLayout {
            val c = LinearLayout(this); c.orientation = LinearLayout.VERTICAL
            c.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            val r = TextView(this); r.text = rotulo; r.textSize = 10.5f; r.maxLines = 1; r.setTextColor(0xFF9A7B96.toInt()); c.addView(r)
            val v = TextView(this); v.text = valor; v.textSize = 12f; v.typeface = monoB; v.setTextColor(cor); c.addView(v)
            return c
        }
        lateinit var desenhar: () -> Unit
        desenhar = {
            corpo.removeAllViews()
            val sim = comprSimular
            val escala = meses.maxOf { maxOf(tetoMes(it.ym) ?: 0.0, it.base + if (sim) it.wl else 0.0) }
            for (x in meses) {
                val teto = tetoMes(x.ym)
                val consumido = x.base + if (sim) x.wl else 0.0
                val bloco = LinearLayout(this); bloco.orientation = LinearLayout.VERTICAL; bloco.setPadding(0, dp(12), 0, dp(8))
                // cabecalho: mes + teto (toque para definir/editar)
                val lin = LinearLayout(this); lin.orientation = LinearLayout.HORIZONTAL; lin.gravity = Gravity.CENTER_VERTICAL
                val a = TextView(this); a.text = mesLabel(x.ym); a.textSize = 14f; a.typeface = monoB; a.setTextColor(0xFFFBEEF6.toInt())
                a.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); lin.addView(a)
                val bt = TextView(this); bt.textSize = 12.5f; bt.typeface = monoB; bt.setTextColor(0xFFFFD9EA.toInt())
                bt.text = if (teto == null) "🎯 Definir teto  ›" else "🎯 Teto R$ " + fmtValor(teto) + "  ›"
                bt.setBackgroundResource(R.drawable.proj_btn); bt.setPadding(dp(12), dp(7), dp(12), dp(7))
                bt.setOnClickListener { dialogTeto(x.ym, nomesListas) { desenhar(); renderListas() } }
                lin.addView(bt); bloco.addView(lin)
                val barra = BarraComp(this); barra.setDados(escala, teto, x.base, if (sim) x.wl else 0.0)
                val bLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); bLp.topMargin = dp(8); barra.layoutParams = bLp
                bloco.addView(barra)
                // numeros: comprometido | wishlist | consumido | sobra/passa
                val nums = LinearLayout(this); nums.orientation = LinearLayout.HORIZONTAL; nums.setPadding(0, dp(6), 0, 0)
                nums.addView(celula("Comprometido", fmtValor(x.base), 0xFFFF86BD.toInt()))
                if (sim) nums.addView(celula("Wishlist ✦", fmtValor(x.wl), 0xFFC7A8F7.toInt()))
                nums.addView(celula("Consumido", fmtValor(consumido), 0xFFFBEEF6.toInt()))
                if (teto != null) { val sobra = teto - consumido
                    nums.addView(celula(if (sobra >= 0) "Sobra" else "Passa", fmtValor(kotlin.math.abs(sobra)),
                        if (sobra >= 0) Color.parseColor("#7EE8D2") else Color.parseColor("#FF8FA3"))) }
                else nums.addView(celula("Sobra", "sem teto", 0xFF7A5C76.toInt()))
                bloco.addView(nums)
                // detalhes (toque para abrir)
                val det = LinearLayout(this); det.orientation = LinearLayout.VERTICAL; det.visibility = View.GONE; det.setPadding(dp(8), dp(6), 0, dp(4))
                val linhas = x.itens + if (sim) x.wlItens.map { ("✦ " + it.first) to it.second } else emptyList()
                if (linhas.isEmpty()) { val v = TextView(this); v.text = "Nada neste mês."; v.textSize = 12f; v.setTextColor(0xFFB99BB4.toInt()); det.addView(v) }
                for ((d, v) in linhas) { val r = LinearLayout(this); r.orientation = LinearLayout.HORIZONTAL; r.setPadding(0, dp(2), 0, dp(2))
                    val dd = TextView(this); dd.text = d; dd.textSize = 12.5f; dd.setTextColor(if (d.startsWith("✦")) 0xFFC7A8F7.toInt() else 0xFFFBEEF6.toInt())
                    dd.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); r.addView(dd)
                    val vv = TextView(this); vv.text = fmtValor(kotlin.math.abs(v)); vv.textSize = 12.5f; vv.typeface = mono; vv.setTextColor(0xFFB99BB4.toInt()); vv.setPadding(dp(8), 0, 0, 0); r.addView(vv); det.addView(r) }
                bloco.addView(det)
                bloco.setOnClickListener { det.visibility = if (det.visibility == View.GONE) View.VISIBLE else View.GONE }
                corpo.addView(bloco)
                val sep = View(this); sep.setBackgroundColor(0x334A2C4A); corpo.addView(sep, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))
            }
        }
        ck.setOnCheckedChangeListener { _, on -> comprSimular = on; desenhar() }
        desenhar()
        val scroll = ScrollView(this); scroll.addView(box)
        AlertDialog.Builder(this).setTitle("Comprometimento futuro").setView(scroll).setPositiveButton("Fechar", null).show()
    }

    // ================================================ WISHLIST
    private val WL_DONOS = listOf("Ariane", "Miguel", "Casa", "Murilo")
    private val WL_COR_DONO = mapOf("Ariane" to "#FF86BD", "Miguel" to "#9FC5F8", "Casa" to "#F6C177", "Murilo" to "#7EE8D2")
    private val WL_TIPOS = listOf("Obrigatorio" to "Obrigatório", "Luxo" to "Luxo", "CustoBeneficio" to "Custo-benefício")
    private val WL_COR_TIPO = mapOf("Obrigatorio" to "#FF86BD", "Luxo" to "#C7A8F7", "CustoBeneficio" to "#7EE8D2")
    private val WL_PRIOS = listOf("Alta" to "Alta", "Media" to "Média", "Baixa" to "Baixa")
    private val WL_COR_PRIO = mapOf("Alta" to "#FF8FA3", "Media" to "#F6C177", "Baixa" to "#7EE8D2")
    private fun rotTipo(k: String) = WL_TIPOS.firstOrNull { it.first == k }?.second ?: k
    private fun rotPrio(k: String) = WL_PRIOS.firstOrNull { it.first == k }?.second ?: k
    private fun rsBR(v: Double) = "R$ " + fmtValor(v)

    private fun carregarWishlist() {
        io({ Api.listarWishlist(Api.idTokenValido(store), store.uid!!) },
           { l -> desejos = l.filter { !it.excluida }; renderWishlist() }, { })
    }

    private fun pilula(txt: String, cor: String, fundo: Int = 0x14FFFFFF): TextView {
        val b = TextView(this); b.text = txt; b.textSize = 11f; b.setTextColor(Color.parseColor(cor))
        b.setTypeface(b.typeface, android.graphics.Typeface.BOLD)
        val bg = android.graphics.drawable.GradientDrawable(); bg.cornerRadius = dp(99).toFloat(); bg.setColor(fundo); b.background = bg
        b.setPadding(dp(8), dp(3), dp(8), dp(3))
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); lp.marginEnd = dp(5); lp.topMargin = dp(4); b.layoutParams = lp
        return b
    }

    // botoes em pilula, um selecionado (devolve a linha; onSel recebe a chave)
    private fun segmento(ops: List<Pair<String, String>>, atual: String, onSel: (String) -> Unit): LinearLayout {
        val row = LinearLayout(this); row.orientation = LinearLayout.HORIZONTAL; row.setBackgroundResource(R.drawable.tabs_bg); row.setPadding(dp(3), dp(3), dp(3), dp(3))
        val bts = ArrayList<TextView>()
        fun pintar(sel: String) { for ((i, b) in bts.withIndex()) { val on = ops[i].first == sel
            if (on) { b.setBackgroundResource(R.drawable.tab_on); b.setTextColor(Color.parseColor("#2A0F22")) } else { b.background = null; b.setTextColor(0xFFB99BB4.toInt()) } } }
        for ((k, r) in ops) { val b = TextView(this); b.text = r; b.gravity = Gravity.CENTER; b.textSize = 12f
            b.setTypeface(b.typeface, android.graphics.Typeface.BOLD); b.setPadding(dp(4), dp(8), dp(4), dp(8))
            b.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            b.setOnClickListener { pintar(k); onSel(k) }; bts.add(b); row.addView(b) }
        pintar(atual); return row
    }

    private fun renderWishlist() {
        if (!::wishContainer.isInitialized) return
        wishContainer.removeAllViews()
        if (wishGraf) { renderWishGraf(); return }
        val novo = TextView(this); novo.text = "+  Novo item"; novo.gravity = Gravity.CENTER
        novo.setTextColor(Color.parseColor("#2A0F22")); novo.setTypeface(novo.typeface, android.graphics.Typeface.BOLD); novo.textSize = 14f
        novo.setBackgroundResource(R.drawable.btn_accent); novo.setPadding(dp(14), dp(12), dp(14), dp(12))
        val nLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); nLp.bottomMargin = dp(10); novo.layoutParams = nLp
        novo.setOnClickListener { dialogDesejo(null) }
        wishContainer.addView(novo)

        val ativos = desejos.filter { !it.comprado }
        // resumo + botao de graficos
        val res = LinearLayout(this); res.orientation = LinearLayout.HORIZONTAL; res.gravity = Gravity.CENTER_VERTICAL
        res.setBackgroundResource(R.drawable.saldo_bg); res.setPadding(dp(14), dp(10), dp(10), dp(10))
        val col = LinearLayout(this); col.orientation = LinearLayout.VERTICAL; col.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        val r1 = TextView(this); r1.text = "WISHLIST · ${ativos.size} ITENS"; r1.textSize = 10f; r1.letterSpacing = 0.12f; r1.setTextColor(0xFFB99BB4.toInt()); col.addView(r1)
        val r2 = TextView(this); r2.text = rsBR(ativos.sumOf { it.valor }); r2.textSize = 22f; r2.setTextColor(0xFFFFD9EA.toInt())
        r2.typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD); col.addView(r2)
        res.addView(col)
        val gb = TextView(this); gb.text = "▁▃▅"; gb.textSize = 16f; gb.gravity = Gravity.CENTER; gb.setTextColor(0xFFFFD9EA.toInt())
        gb.setBackgroundResource(R.drawable.circle_btn); gb.layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)); gb.contentDescription = "Gráficos"
        gb.setOnClickListener { wishGraf = true; renderWishlist() }
        res.addView(gb)
        val rLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); rLp.bottomMargin = dp(8); res.layoutParams = rLp
        wishContainer.addView(res)
        // filtro por dono
        val fil = LinearLayout(this); fil.orientation = LinearLayout.HORIZONTAL; fil.setPadding(0, 0, 0, dp(6))
        for (d in listOf("Todos") + WL_DONOS) {
            val on = d == wishDono
            val p = pilula(d, if (on) "#2A0F22" else (WL_COR_DONO[d] ?: "#FBEEF6"), if (on) Color.parseColor("#FF86BD") else 0x14FFFFFF)
            p.textSize = 12f; p.setPadding(dp(10), dp(6), dp(10), dp(6))
            p.setOnClickListener { wishDono = d; renderWishlist() }; fil.addView(p)
        }
        val hs = android.widget.HorizontalScrollView(this); hs.isHorizontalScrollBarEnabled = false; hs.addView(fil); wishContainer.addView(hs)

        val lista = desejos.filter { wishDono == "Todos" || it.dono == wishDono }
            .sortedWith(compareBy<Desejo>({ it.comprado }, { if (it.previsao.isEmpty()) "9999" else it.previsao }, { it.prioridade != "Alta" }))
        if (lista.isEmpty()) { val v = TextView(this); v.text = "Nenhum item."; v.setTextColor(0xFFB99BB4.toInt()); v.setPadding(0, dp(10), 0, 0); wishContainer.addView(v) }
        for (d in lista) wishContainer.addView(cartaoDesejo(d))
    }

    private fun cartaoDesejo(d: Desejo): View {
        val card = LinearLayout(this); card.orientation = LinearLayout.VERTICAL
        card.setBackgroundResource(R.drawable.row_bg); card.setPadding(dp(14), dp(12), dp(14), dp(10))
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); lp.bottomMargin = dp(8); card.layoutParams = lp
        if (d.comprado) card.alpha = 0.55f
        val top = LinearLayout(this); top.orientation = LinearLayout.HORIZONTAL
        val dot = View(this); val dbg = android.graphics.drawable.GradientDrawable(); dbg.shape = android.graphics.drawable.GradientDrawable.OVAL
        dbg.setColor(Color.parseColor(WL_COR_DONO[d.dono] ?: "#FBEEF6")); dot.background = dbg
        val dLp = LinearLayout.LayoutParams(dp(10), dp(10)); dLp.topMargin = dp(6); dLp.marginEnd = dp(10); dot.layoutParams = dLp; top.addView(dot)
        val col = LinearLayout(this); col.orientation = LinearLayout.VERTICAL; col.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        val n = TextView(this); n.text = d.nome + if (d.comprado) "  ✓ comprado" else ""; n.textSize = 15f; n.setTextColor(0xFFFBEEF6.toInt())
        n.setTypeface(n.typeface, android.graphics.Typeface.BOLD); col.addView(n)
        if (d.descricao.isNotBlank()) { val ds = TextView(this); ds.text = d.descricao; ds.maxLines = 2; ds.ellipsize = android.text.TextUtils.TruncateAt.END
            ds.textSize = 12f; ds.setTextColor(0xFFB99BB4.toInt()); col.addView(ds) }
        top.addView(col)
        val vcol = LinearLayout(this); vcol.orientation = LinearLayout.VERTICAL; vcol.gravity = Gravity.END
        val v1 = TextView(this); v1.text = rsBR(d.valor); v1.textSize = 14f; v1.setTextColor(0xFFFFD9EA.toInt())
        v1.typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD); vcol.addView(v1)
        val v2 = TextView(this); v2.text = "${d.parcelas}x ${fmtValor(d.parcela)}"; v2.textSize = 11f; v2.setTextColor(0xFFB99BB4.toInt())
        v2.typeface = android.graphics.Typeface.MONOSPACE; v2.gravity = Gravity.END; vcol.addView(v2)
        top.addView(vcol); card.addView(top)
        val chips = LinearLayout(this); chips.orientation = LinearLayout.HORIZONTAL
        chips.addView(if (d.previsao.isNotEmpty()) pilula(mesLabel(d.previsao), "#FBEEF6") else pilula("sem data", "#B99BB4"))
        chips.addView(pilula(rotPrio(d.prioridade), WL_COR_PRIO[d.prioridade] ?: "#FBEEF6"))
        chips.addView(pilula(rotTipo(d.tipo), WL_COR_TIPO[d.tipo] ?: "#FBEEF6"))
        chips.addView(pilula(d.dono, WL_COR_DONO[d.dono] ?: "#FBEEF6"))
        if (d.responsavel.isNotEmpty()) chips.addView(pilula("Resp. " + d.responsavel, WL_COR_DONO[d.responsavel] ?: "#FBEEF6"))
        val hs = android.widget.HorizontalScrollView(this); hs.isHorizontalScrollBarEnabled = false; hs.addView(chips); card.addView(hs)
        card.setOnClickListener { dialogDesejo(d) }
        return card
    }

    private fun dialogDesejo(d: Desejo?) {
        val box = LinearLayout(this); box.orientation = LinearLayout.VERTICAL; box.setPadding(dp(18), dp(8), dp(18), dp(4))
        fun rot(t: String) { val l = TextView(this); l.text = t; l.textSize = 11f; l.letterSpacing = 0.08f; l.setTextColor(0xFFB99BB4.toInt())
            l.setTypeface(l.typeface, android.graphics.Typeface.BOLD); l.setPadding(0, dp(10), 0, dp(4)); box.addView(l) }
        fun campo(hint: String): EditText { val e = EditText(this); e.hint = hint; e.setHintTextColor(0xFF7A5C76.toInt()); e.setTextColor(0xFFFBEEF6.toInt())
            e.setBackgroundResource(R.drawable.field_bg); e.setPadding(dp(12), dp(10), dp(12), dp(10)); return e }
        rot("NOME"); val edNome = campo("ex.: Air fryer"); edNome.setText(d?.nome ?: ""); box.addView(edNome)
        rot("DESCRIÇÃO"); val edDesc = campo("detalhes, link da loja..."); edDesc.setText(d?.descricao ?: "")
        edDesc.minLines = 4; edDesc.maxLines = 10; edDesc.gravity = Gravity.TOP
        edDesc.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        box.addView(edDesc)
        rot("VALOR")
        val modo = arrayOf(if (d != null && d.parcelas > 1) "parc" else "total")
        val edV = campo("0,00"); tecladoValor(edV); edV.typeface = android.graphics.Typeface.MONOSPACE
        val edN = campo("1"); edN.inputType = android.text.InputType.TYPE_CLASS_NUMBER; edN.typeface = android.graphics.Typeface.MONOSPACE
        edN.setText((d?.parcelas ?: 1).toString())
        val prev = TextView(this); prev.textSize = 12f; prev.setTextColor(Color.parseColor("#DCCDF7")); prev.typeface = android.graphics.Typeface.MONOSPACE; prev.setPadding(dp(2), dp(6), 0, 0)
        val prevMes = arrayOf(d?.previsao?.ifEmpty { null } ?: ymAdd(ymHoje(), 1))
        fun total(): Double { val v = parseValor(edV.text.toString()) ?: 0.0; val n = (edN.text.toString().toIntOrNull() ?: 1).coerceAtLeast(1)
            return kotlin.math.abs(if (modo[0] == "parc") v * n else v) }
        val semData = booleanArrayOf(d != null && d.previsao.isEmpty())
        fun atualizar() { val n = (edN.text.toString().toIntOrNull() ?: 1).coerceAtLeast(1); val t = total()
            prev.text = (if (modo[0] == "parc") "= " + rsBR(t) else n.toString() + "x " + rsBR(t / n)) +
                (if (semData[0]) " · sem data" else " · " + mesLabel(prevMes[0]) + (if (n > 1) " → " + mesLabel(ymAdd(prevMes[0], n - 1)) else "")) }
        if (d != null) edV.setText(fmtValor(if (modo[0] == "parc") d.parcela else d.valor))
        box.addView(segmento(listOf("total" to "Valor total", "parc" to "Parcela × nº"), modo[0]) { k ->
            val t = total(); val n = (edN.text.toString().toIntOrNull() ?: 1).coerceAtLeast(1); modo[0] = k
            if (t > 0) edV.setText(fmtValor(if (k == "parc") t / n else t)); atualizar() })
        val linV = LinearLayout(this); linV.orientation = LinearLayout.HORIZONTAL; linV.setPadding(0, dp(8), 0, 0)
        edV.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f)
        val nLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); nLp.marginStart = dp(8); edN.layoutParams = nLp
        edN.hint = "parcelas"
        linV.addView(edV); linV.addView(edN); box.addView(linV); box.addView(prev)
        val tw = object : android.text.TextWatcher { override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {} override fun afterTextChanged(s: android.text.Editable?) { atualizar() } }
        edV.addTextChangedListener(tw); edN.addTextChangedListener(tw)
        rot("PREVISÃO")
        val btMes = TextView(this); btMes.setBackgroundResource(R.drawable.field_bg); btMes.setTextColor(0xFFFBEEF6.toInt()); btMes.textSize = 15f
        btMes.setPadding(dp(12), dp(11), dp(12), dp(11))
        val nomesMes = listOf("janeiro","fevereiro","março","abril","maio","junho","julho","agosto","setembro","outubro","novembro","dezembro")
        fun pintaMes() { val p = prevMes[0].split("-"); btMes.text = nomesMes[p[1].toInt() - 1] + " de " + p[0] + "  ›"; atualizar() }
        btMes.setOnClickListener {
            val p = prevMes[0].split("-")
            DatePickerDialog(this, { _, y, m, _ -> prevMes[0] = "%04d-%02d".format(y, m + 1); pintaMes() }, p[0].toInt(), p[1].toInt() - 1, 1).show()
        }
        val ckSd = CheckBox(this); ckSd.text = "Sem data planejada (só um desejo)"; ckSd.setTextColor(0xFFFBEEF6.toInt()); ckSd.textSize = 13.5f
        ckSd.isChecked = semData[0]; box.addView(ckSd); box.addView(btMes); pintaMes()
        fun aplicaSd() { btMes.isEnabled = !semData[0]; btMes.alpha = if (semData[0]) 0.4f else 1f; atualizar() }
        ckSd.setOnCheckedChangeListener { _, on -> semData[0] = on; aplicaSd() }; aplicaSd()
        val prio = arrayOf(d?.prioridade ?: "Media"); val tipo = arrayOf(d?.tipo ?: "Obrigatorio"); val dono = arrayOf(d?.dono ?: "Casa")
        rot("PRIORIDADE"); box.addView(segmento(WL_PRIOS, prio[0]) { prio[0] = it })
        rot("TIPO"); box.addView(segmento(WL_TIPOS, tipo[0]) { tipo[0] = it })
        rot("DONO"); box.addView(segmento(WL_DONOS.map { it to it }, dono[0]) { dono[0] = it })
        val resp = arrayOf(if (d == null) "Ariane" else d.responsavel)
        rot("RESPONSÁVEL PELA COMPRA"); box.addView(segmento(listOf("Ariane" to "Ariane", "Miguel" to "Miguel"), resp[0]) { resp[0] = it })
        val scroll = ScrollView(this); scroll.addView(box)
        val b = AlertDialog.Builder(this).setTitle(if (d == null) "Novo item" else "Editar item").setView(scroll)
            .setPositiveButton("Salvar") { _, _ ->
                val nome = edNome.text.toString().trim(); if (nome.isEmpty()) { Toast.makeText(this, "Informe o nome", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                val n = (edN.text.toString().toIntOrNull() ?: 1).coerceAtLeast(1)
                val novo = Desejo(id = d?.id ?: Api.novoId(), nome = nome, descricao = edDesc.text.toString().trim(), valor = total(),
                    parcelas = if (modo[0] == "parc" || n > 1) n else 1, previsao = if (semData[0]) "" else prevMes[0], prioridade = prio[0], tipo = tipo[0], dono = dono[0], responsavel = resp[0],
                    comprado = d?.comprado ?: false, agendaCriada = (d?.agendaCriada ?: false) && !semData[0] && d?.previsao == prevMes[0], criadaEm = d?.criadaEm)
                val mudouMes = d != null && d.agendaCriada && (semData[0] || d.previsao != prevMes[0])
                io({ val tok = Api.idTokenValido(store); Api.gravarDesejo(tok, store.uid!!, novo)
                    if (mudouMes) Api.excluirAgenda(tok, store.uid!!, "wl_" + d!!.id) },   // mudou o mes: o lembrete antigo sai da agenda
                   { carregarWishlist(); if (mudouMes) carregarAgenda() }, { e -> status.text = amigavel(e) })
            }.setNegativeButton("Cancelar", null)
        if (d != null) b.setNeutralButton("Excluir") { _, _ ->
            AlertDialog.Builder(this).setMessage("Excluir \"${d.nome}\" da wishlist?").setPositiveButton("Excluir") { _, _ ->
                io({ val tok = Api.idTokenValido(store); Api.patchDesejo(tok, store.uid!!, d.id, linkedMapOf("excluida" to true))
                    if (d.agendaCriada) Api.excluirAgenda(tok, store.uid!!, "wl_" + d.id) }, { carregarWishlist(); carregarAgenda() }, { e -> status.text = amigavel(e) })
            }.setNegativeButton("Cancelar", null).show() }
        b.show()
    }

    // graficos: agrupa os itens (nao comprados) por tipo, dono, mes (parcelas) ou prioridade
    private fun renderWishGraf() {
        val volta = TextView(this); volta.text = "‹  Wishlist"; volta.textSize = 14f; volta.setTextColor(0xFFB99BB4.toInt()); volta.setPadding(dp(2), dp(4), 0, dp(10))
        volta.setOnClickListener { wishGraf = false; renderWishlist() }; wishContainer.addView(volta)
        wishContainer.addView(segmento(listOf("Tipo" to "Tipo", "Dono" to "Dono", "Mes" to "Mês", "Prioridade" to "Prioridade"), wishAgr) { wishAgr = it; renderWishlist() })
        val ativos = desejos.filter { !it.comprado }
        val grupos = LinkedHashMap<String, Double>(); val cores = HashMap<String, String>()
        when (wishAgr) {
            "Dono" -> { for (k in WL_DONOS) { val v = ativos.filter { it.dono == k }.sumOf { it.valor }; if (v > 0) { grupos[k] = v; cores[k] = WL_COR_DONO[k]!! } } }
            "Prioridade" -> { for ((k, r) in WL_PRIOS) { val v = ativos.filter { it.prioridade == k }.sumOf { it.valor }; if (v > 0) { grupos[r] = v; cores[r] = WL_COR_PRIO[k]!! } } }
            "Mes" -> { val m = java.util.TreeMap<String, Double>()
                for (d in ativos) { if (d.previsao.isEmpty()) continue; for (i in 0 until d.parcelas) { val ym = ymAdd(d.previsao, i); m[ym] = (m[ym] ?: 0.0) + d.parcela } }
                for ((k, v) in m) { grupos[mesLabel(k)] = v; cores[mesLabel(k)] = "#FF86BD" } }
            else -> { for ((k, r) in WL_TIPOS) { val v = ativos.filter { it.tipo == k }.sumOf { it.valor }; if (v > 0) { grupos[r] = v; cores[r] = WL_COR_TIPO[k]!! } } }
        }
        val card = LinearLayout(this); card.orientation = LinearLayout.VERTICAL; card.setBackgroundResource(R.drawable.row_bg); card.setPadding(dp(14), dp(12), dp(14), dp(12))
        val cLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); cLp.topMargin = dp(10); card.layoutParams = cLp
        val tot = grupos.values.sum(); val mx = grupos.values.maxOrNull() ?: 1.0
        val h = TextView(this); h.text = "TOTAL " + rsBR(if (wishAgr == "Mes") tot else ativos.sumOf { it.valor }); h.textSize = 11f; h.letterSpacing = 0.1f; h.setTextColor(0xFFB99BB4.toInt()); card.addView(h)
        if (grupos.isEmpty()) { val v = TextView(this); v.text = "Nenhum item."; v.setTextColor(0xFFB99BB4.toInt()); card.addView(v) }
        for ((k, v) in grupos) {
            val lin = LinearLayout(this); lin.orientation = LinearLayout.HORIZONTAL; lin.setPadding(0, dp(10), 0, dp(3))
            val a = TextView(this); a.text = k; a.textSize = 13f; a.setTextColor(0xFFFBEEF6.toInt()); a.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); lin.addView(a)
            val b = TextView(this); b.text = rsBR(v) + "  " + String.format(java.util.Locale.US, "%.0f%%", if (tot > 0) v / tot * 100 else 0.0)
            b.textSize = 12.5f; b.setTextColor(0xFFFFD9EA.toInt()); b.typeface = android.graphics.Typeface.MONOSPACE; lin.addView(b)
            card.addView(lin)
            val trilho = FrameLayout(this); val tbg = android.graphics.drawable.GradientDrawable(); tbg.cornerRadius = dp(99).toFloat(); tbg.setColor(Color.parseColor("#120912")); trilho.background = tbg
            trilho.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8))
            val barra = View(this); val bbg = android.graphics.drawable.GradientDrawable(); bbg.cornerRadius = dp(99).toFloat(); bbg.setColor(Color.parseColor(cores[k] ?: "#FF86BD")); barra.background = bbg
            trilho.addView(barra); card.addView(trilho)
            trilho.post { barra.layoutParams = FrameLayout.LayoutParams((trilho.width * (v / mx)).toInt().coerceAtLeast(dp(4)), dp(8)) }
        }
        wishContainer.addView(card)
    }

    // ================================================ AGENDA (compromissos datados)
    private fun carregarAgenda() {
        io({ val uid = store.uid!!; val tok = Api.idTokenValido(store); Api.listarAgenda(tok, uid) },
           { lista ->
               compromissos = lista.filter { !it.excluida }; renderAgenda(); TarefasWidget.atualizar(this)
               Notificador.verificarAgenda(this, compromissos)
               abrirCompromissoId?.let { id -> abrirCompromissoId = null; compromissos.firstOrNull { it.id == id }?.let { editorCompromisso(it) } }
           },
           { })
    }

    private fun popupAgenda() {
        val box = LinearLayout(this); box.orientation = LinearLayout.VERTICAL; box.setPadding(dp(18), dp(14), dp(18), dp(6))
        val rot = TextView(this); rot.text = "Importar com IA"; rot.setTextColor(0xFFB99BB4.toInt()); rot.textSize = 12f
        rot.setTypeface(rot.typeface, android.graphics.Typeface.BOLD); rot.setPadding(0, 0, 0, dp(6)); box.addView(rot)
        val iaRow = LinearLayout(this); iaRow.orientation = LinearLayout.HORIZONTAL
        fun iaBtn(txt: String): TextView {
            val b = TextView(this); b.text = txt; b.gravity = Gravity.CENTER; b.textSize = 13f
            b.setBackgroundResource(R.drawable.field_bg); b.setTextColor(0xFFFBEEF6.toInt()); b.setPadding(dp(4), dp(12), dp(4), dp(12))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); lp.marginEnd = dp(6); b.layoutParams = lp
            iaRow.addView(b); return b
        }
        val bVoz = iaBtn("🎤 Voz"); val bImg = iaBtn("🖼 Imagem"); val bCam = iaBtn("📷 Câmera")
        box.addView(iaRow)
        val rot2 = TextView(this); rot2.text = "ou adicione manualmente"; rot2.setTextColor(0xFFB99BB4.toInt()); rot2.textSize = 12f
        rot2.setTypeface(rot2.typeface, android.graphics.Typeface.BOLD); rot2.setPadding(0, dp(16), 0, dp(6)); box.addView(rot2)
        val ent = EditText(this); ent.hint = "Compromisso..."; ent.setHintTextColor(0xFFB99BB4.toInt()); ent.setTextColor(0xFFFBEEF6.toInt())
        ent.setBackgroundResource(R.drawable.field_bg); ent.setPadding(dp(10), dp(10), dp(10), dp(10)); box.addView(ent)
        val linM = LinearLayout(this); linM.orientation = LinearLayout.HORIZONTAL
        val lmLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); lmLp.topMargin = dp(8); linM.layoutParams = lmLp
        val dataLoc = arrayOf(if (agVista == "cal" && agDiaSel != null) agDiaSel!! else hojeData())
        val btData = TextView(this); btData.setBackgroundResource(R.drawable.field_bg); btData.setTextColor(0xFFFBEEF6.toInt())
        btData.setPadding(dp(10), dp(10), dp(10), dp(10)); btData.textSize = 13f
        btData.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        fun pintaD() { btData.text = if (dataLoc[0].isEmpty()) "📅 Sem data" else "📅 " + ddmmLabel(dataLoc[0]) }
        pintaD()
        btData.setOnClickListener {
            val base = dataLoc[0].ifEmpty { hojeData() }.split("-"); val cal = Calendar.getInstance()
            try { cal.set(base[0].toInt(), base[1].toInt() - 1, base[2].toInt()) } catch (_: Exception) {}
            val d = DatePickerDialog(this, { _, y, m, dd -> dataLoc[0] = "%04d-%02d-%02d".format(y, m + 1, dd); pintaD() }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
            d.setButton(DatePickerDialog.BUTTON_NEUTRAL, "Sem data") { _, _ -> dataLoc[0] = ""; pintaD() }
            d.show()
        }
        val btAdd = TextView(this); btAdd.text = "Adicionar"; btAdd.gravity = Gravity.CENTER
        btAdd.setTextColor(Color.parseColor("#2A0F22")); btAdd.setTypeface(btAdd.typeface, android.graphics.Typeface.BOLD)
        btAdd.setBackgroundResource(R.drawable.btn_accent); btAdd.setPadding(dp(16), dp(10), dp(16), dp(10))
        val baLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); baLp.marginStart = dp(6); btAdd.layoutParams = baLp
        linM.addView(btData); linM.addView(btAdd); box.addView(linM)

        val dlg = AlertDialog.Builder(this).setTitle("Novo compromisso").setView(box).setNegativeButton("Fechar", null).create()
        fun lancarIA(acao: () -> Unit) { iaModoAtual = "agenda"; dlg.dismiss(); garantirCodigoIa { acao() } }
        bVoz.setOnClickListener { lancarIA { pedirAudioIA() } }
        bImg.setOnClickListener { lancarIA { galeriaLauncher.launch("image/*") } }
        bCam.setOnClickListener { lancarIA { abrirCameraIA() } }
        btAdd.setOnClickListener {
            val d = ent.text.toString().trim(); if (d.isEmpty()) { ent.requestFocus(); return@setOnClickListener }
            dlg.dismiss(); val data = dataLoc[0]
            io({ val uid = store.uid!!; val tok = Api.idTokenValido(store)
                val c = Compromisso(id = Api.novoId(), descricao = d, data = data, hora = "", corpo = "",
                    concluida = false, criadaEm = nowIso(), atualizadoEm = Api.agoraMs(), excluida = false)
                Api.gravarCompromisso(tok, uid, c) }, { carregarAgenda() }, { e -> status.text = amigavel(e) })
        }
        dlg.show()
    }

    private fun hojeData(): String {
        val c = Calendar.getInstance()
        return "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }
    private fun dataMais(iso: String, dias: Int): String {
        val p = iso.split("-"); val c = Calendar.getInstance()
        try { c.set(p[0].toInt(), p[1].toInt() - 1, p[2].toInt()); c.add(Calendar.DAY_OF_MONTH, dias) } catch (_: Exception) {}
        return "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }
    private fun ddmmLabel(iso: String): String { val p = iso.split("-"); return if (p.size == 3) p[2] + "/" + p[1] else iso }
    private fun diaSemana(iso: String): String {
        val p = iso.split("-"); if (p.size != 3) return ""
        val c = Calendar.getInstance(); try { c.set(p[0].toInt(), p[1].toInt() - 1, p[2].toInt()) } catch (_: Exception) { return "" }
        return arrayOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb")[c.get(Calendar.DAY_OF_WEEK) - 1]
    }
    private fun labelDiaAgenda(iso: String, hoje: String): String {
        if (iso.isEmpty()) return "Sem data"
        return when {
            iso == hoje -> "Hoje · " + ddmmLabel(iso)
            iso == dataMais(hoje, 1) -> "Amanhã · " + ddmmLabel(iso)
            iso < hoje -> "Atrasado · " + ddmmLabel(iso)
            else -> diaSemana(iso) + " · " + ddmmLabel(iso)
        }
    }

    private fun popupLista() {
        val lista = listaGrupos.firstOrNull()
        if (lista == null) { status.text = "Crie um grupo do tipo Lista primeiro."; return }
        val aba = abaAtivaDeLista(lista)
        val box = LinearLayout(this); box.orientation = LinearLayout.VERTICAL; box.setPadding(dp(18), dp(14), dp(18), dp(6))
        val rot = TextView(this); rot.text = "Importar com IA"; rot.setTextColor(0xFFB99BB4.toInt()); rot.textSize = 12f
        rot.setTypeface(rot.typeface, android.graphics.Typeface.BOLD); rot.setPadding(0, 0, 0, dp(6)); box.addView(rot)
        val iaRow = LinearLayout(this); iaRow.orientation = LinearLayout.HORIZONTAL
        fun iaBtn(txt: String): TextView {
            val b = TextView(this); b.text = txt; b.gravity = Gravity.CENTER; b.textSize = 13f
            b.setBackgroundResource(R.drawable.field_bg); b.setTextColor(0xFFFBEEF6.toInt()); b.setPadding(dp(4), dp(12), dp(4), dp(12))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); lp.marginEnd = dp(6); b.layoutParams = lp
            iaRow.addView(b); return b
        }
        val bVoz = iaBtn("🎤 Voz"); val bImg = iaBtn("🖼 Imagem"); val bCam = iaBtn("📷 Câmera")
        box.addView(iaRow)
        val rot2 = TextView(this); rot2.text = "ou adicione manualmente (" + mesLabel(aba) + ")"; rot2.setTextColor(0xFFB99BB4.toInt()); rot2.textSize = 12f
        rot2.setTypeface(rot2.typeface, android.graphics.Typeface.BOLD); rot2.setPadding(0, dp(16), 0, dp(6)); box.addView(rot2)
        val ent = EditText(this); ent.hint = "Item..."; ent.setHintTextColor(0xFFB99BB4.toInt()); ent.setTextColor(0xFFFBEEF6.toInt())
        ent.setBackgroundResource(R.drawable.field_bg); ent.setPadding(dp(10), dp(10), dp(10), dp(10)); box.addView(ent)
        val linM = LinearLayout(this); linM.orientation = LinearLayout.HORIZONTAL
        val lmLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); lmLp.topMargin = dp(8); linM.layoutParams = lmLp
        val edVal = EditText(this); edVal.hint = "valor"; edVal.setHintTextColor(0xFFB99BB4.toInt()); edVal.setTextColor(0xFFFBEEF6.toInt())
        edVal.setBackgroundResource(R.drawable.field_bg); edVal.setPadding(dp(10), dp(10), dp(10), dp(10))
        tecladoValor(edVal)
        edVal.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        val btAdd = TextView(this); btAdd.text = "Adicionar"; btAdd.gravity = Gravity.CENTER
        btAdd.setTextColor(Color.parseColor("#2A0F22")); btAdd.setTypeface(btAdd.typeface, android.graphics.Typeface.BOLD)
        btAdd.setBackgroundResource(R.drawable.btn_accent); btAdd.setPadding(dp(16), dp(10), dp(16), dp(10))
        val baLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); baLp.marginStart = dp(6); btAdd.layoutParams = baLp
        val btParc = TextView(this); btParc.text = "\uD83D\uDD01"; btParc.textSize = 16f; btParc.gravity = Gravity.CENTER
        btParc.setBackgroundResource(R.drawable.field_bg); btParc.setTextColor(0xFFFBEEF6.toInt()); btParc.setPadding(dp(10), dp(10), dp(10), dp(10))
        val pLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); pLp.marginStart = dp(6); btParc.layoutParams = pLp
        linM.addView(edVal); linM.addView(btParc); linM.addView(btAdd); box.addView(linM)
        val dlg = AlertDialog.Builder(this).setTitle("Novo item — " + lista).setView(box).setNegativeButton("Fechar", null).create()
        fun lancarIA(acao: () -> Unit) { iaModoAtual = "lista"; dlg.dismiss(); garantirCodigoIa { acao() } }
        bVoz.setOnClickListener { lancarIA { pedirAudioIA() } }
        bImg.setOnClickListener { lancarIA { galeriaLauncher.launch("image/*") } }
        bCam.setOnClickListener { lancarIA { abrirCameraIA() } }
        fun adicionar(parcelar: Boolean) {
            val d = ent.text.toString().trim(); if (d.isEmpty()) { ent.requestFocus(); return }
            val v = parseValor(edVal.text.toString()); dlg.dismiss()
            io({ val uid = store.uid!!; val tok = Api.idTokenValido(store)
                val menor = itensDaAba(lista, aba).filter { !it.concluida }.mapNotNull { it.ordem }.minOrNull() ?: 0.0
                val id = Api.novoId()
                val t = Tarefa(id = id, grupo = lista, aba = aba, descricao = d, data = hojeData(), concluida = false,
                    criadaEm = nowIso(), atualizadoEm = Api.agoraMs(), excluida = false, valor = v, ordem = menor - 1.0)
                Api.gravarTarefa(tok, uid, t); id
            }, { novoId ->
                carregarTudo()
                if (parcelar) dialogRecorrencia(Tarefa(id = novoId, grupo = lista, aba = aba, descricao = d, valor = v))
            }, { e -> status.text = amigavel(e) })
        }
        btAdd.setOnClickListener { adicionar(false) }
        btParc.setOnClickListener { adicionar(true) }
        dlg.show()
    }

    private fun renderAgenda() {
        agendaContainer.removeAllViews()
        val novo = TextView(this); novo.text = "➕  Novo compromisso"; novo.gravity = Gravity.CENTER
        novo.setTextColor(Color.parseColor("#2A0F22")); novo.setTypeface(novo.typeface, android.graphics.Typeface.BOLD); novo.textSize = 14f
        novo.setBackgroundResource(R.drawable.btn_accent); novo.setPadding(dp(14), dp(12), dp(14), dp(12))
        val nLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); nLp.bottomMargin = dp(10); novo.layoutParams = nLp
        novo.setOnClickListener { popupAgenda() }
        agendaContainer.addView(novo)

        // switch Lista / Calendario
        agVista = getSharedPreferences("cardtarefas", MODE_PRIVATE).getString("ag_vista", agVista) ?: "lista"
        val sw = LinearLayout(this); sw.orientation = LinearLayout.HORIZONTAL; sw.setBackgroundResource(R.drawable.tabs_bg); sw.setPadding(dp(4), dp(4), dp(4), dp(4))
        for ((k, rot) in listOf("lista" to "📋 Lista", "cal" to "📅 Calendário")) {
            val b = TextView(this); b.text = rot; b.gravity = Gravity.CENTER; b.textSize = 13f
            b.setTypeface(b.typeface, android.graphics.Typeface.BOLD); b.setPadding(dp(8), dp(9), dp(8), dp(9))
            if (agVista == k) { b.setBackgroundResource(R.drawable.tab_on); b.setTextColor(Color.parseColor("#2A0F22")) } else b.setTextColor(0xFFB99BB4.toInt())
            b.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            b.setOnClickListener { agVista = k; getSharedPreferences("cardtarefas", MODE_PRIVATE).edit().putString("ag_vista", k).apply(); renderAgenda() }
            sw.addView(b)
        }
        val swLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); swLp.bottomMargin = dp(8); sw.layoutParams = swLp
        agendaContainer.addView(sw)
        if (agVista == "cal") { calendarioAgenda(); return }

        val hoje = hojeData()
        val pend = compromissos.filter { !it.concluida }.sortedWith(
            compareBy<Compromisso>({ if (it.data.isEmpty()) "9999-99-99" else it.data }, { it.hora }, { it.criadaEm ?: "" }))
        val feitos = compromissos.filter { it.concluida }.sortedByDescending { it.atualizadoEm }.take(20)
        if (pend.isEmpty()) {
            val v = TextView(this); v.text = "Nenhum compromisso. Adicione acima."
            v.setTextColor(0xFFB99BB4.toInt()); v.textSize = 13f; v.setPadding(0, dp(10), 0, 0); agendaContainer.addView(v)
        }
        var ultDia: String? = null
        for (c in pend) {
            if (c.data != ultDia) { ultDia = c.data; agendaContainer.addView(cabecalhoDia(labelDiaAgenda(c.data, hoje), c.data.isNotEmpty() && c.data < hoje)) }
            agendaContainer.addView(chipCompromisso(c))
        }
        if (feitos.isNotEmpty()) {
            agendaContainer.addView(cabecalhoDia("Concluídos", false))
            for (c in feitos) agendaContainer.addView(chipCompromisso(c))
        }
    }

    // "Hoje", "Amanha", "Faltam N dias", "Ontem", "Atrasado ha N dias" + cor
    private fun faltaDias(iso: String): Pair<String, Int>? {
        if (iso.isEmpty()) return null
        fun ms(s: String): Long? { val p = s.split("-"); if (p.size != 3) return null
            val c = Calendar.getInstance(); c.clear(); c.set(p[0].toIntOrNull() ?: return null, (p[1].toIntOrNull() ?: return null) - 1, p[2].toIntOrNull() ?: return null); return c.timeInMillis }
        val d = ms(iso) ?: return null; val h = ms(hojeData()) ?: return null
        val n = Math.round((d - h) / 86400000.0).toInt()
        return when {
            n == 0 -> "Hoje" to Color.parseColor("#FF86BD")
            n == 1 -> "Amanhã" to Color.parseColor("#F6C177")
            n > 1 -> ("Faltam $n dias") to (if (n <= 7) Color.parseColor("#F6C177") else Color.parseColor("#7EE8D2"))
            n == -1 -> "Ontem" to Color.parseColor("#FF8FA3")
            else -> ("Atrasado há ${-n} dias") to Color.parseColor("#FF8FA3")
        }
    }

    private fun calendarioAgenda() {
        val hoje = hojeData()
        if (agDiaSel == null) agDiaSel = hoje
        if (agMesCal < 0) { val p = agDiaSel!!.split("-"); agMesCal = p[0].toInt() * 12 + p[1].toInt() - 1 }
        val y = agMesCal / 12; val m = agMesCal % 12
        val nomes = listOf("Janeiro","Fevereiro","Março","Abril","Maio","Junho","Julho","Agosto","Setembro","Outubro","Novembro","Dezembro")
        // cabecalho: < Mes Ano >
        val hd = LinearLayout(this); hd.orientation = LinearLayout.HORIZONTAL; hd.gravity = Gravity.CENTER_VERTICAL
        fun nav(txt: String, d: Int): TextView { val b = TextView(this); b.text = txt; b.textSize = 20f; b.gravity = Gravity.CENTER
            b.setTextColor(0xFFFBEEF6.toInt()); b.setBackgroundResource(R.drawable.circle_btn)
            b.layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)); b.setOnClickListener { agMesCal += d; renderAgenda() }; return b }
        hd.addView(nav("‹", -1))
        val tit = TextView(this); tit.text = nomes[m] + " " + y; tit.gravity = Gravity.CENTER; tit.textSize = 18f
        tit.setTextColor(0xFFFFD9EA.toInt()); tit.typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD_ITALIC)
        tit.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        tit.setOnClickListener { agDiaSel = hoje; agMesCal = -1; renderAgenda() }
        hd.addView(tit); hd.addView(nav("›", 1))
        agendaContainer.addView(hd)
        // dias da semana
        val wk = LinearLayout(this); wk.orientation = LinearLayout.HORIZONTAL; wk.setPadding(0, dp(8), 0, dp(4))
        for (w in listOf("D","S","T","Q","Q","S","S")) { val t = TextView(this); t.text = w; t.gravity = Gravity.CENTER; t.textSize = 11f
            t.setTextColor(0xFF7A5C76.toInt()); t.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); wk.addView(t) }
        agendaContainer.addView(wk)
        // grade do mes
        val porDia = compromissos.filter { it.data.isNotEmpty() }.groupBy { it.data }
        val c = Calendar.getInstance(); c.clear(); c.set(y, m, 1)
        val primeiro = c.get(Calendar.DAY_OF_WEEK) - 1; val ult = c.getActualMaximum(Calendar.DAY_OF_MONTH)
        var dia = 1 - primeiro
        while (dia <= ult) {
            val lin = LinearLayout(this); lin.orientation = LinearLayout.HORIZONTAL
            for (k in 0 until 7) {
                val cel = LinearLayout(this); cel.orientation = LinearLayout.VERTICAL; cel.gravity = Gravity.CENTER_HORIZONTAL
                val clp = LinearLayout.LayoutParams(0, dp(50), 1f); clp.setMargins(dp(2), dp(2), dp(2), dp(2)); cel.layoutParams = clp
                if (dia in 1..ult) {
                    val iso = "%04d-%02d-%02d".format(y, m + 1, dia)
                    val itens = porDia[iso] ?: emptyList(); val pend = itens.count { !it.concluida }
                    // dia com agenda: quadrado inteiro pintado de lilas (bem visivel); so' concluidos = lilas claro
                    cel.setBackgroundResource(when {
                        pend > 0 && iso == agDiaSel -> R.drawable.dia_ag_sel
                        pend > 0 -> R.drawable.dia_ag
                        iso == agDiaSel -> R.drawable.proj_btn
                        itens.isNotEmpty() -> R.drawable.dia_ag_feito
                        else -> R.drawable.row_bg })
                    val n = TextView(this); n.text = dia.toString(); n.textSize = 13f; n.gravity = Gravity.CENTER
                    n.setTextColor(if (pend > 0) 0xFFFFFFFF.toInt() else if (iso < hoje) 0xFF7A5C76.toInt() else 0xFFFBEEF6.toInt())
                    if (pend > 0) n.setTypeface(n.typeface, android.graphics.Typeface.BOLD)
                    val nlp = LinearLayout.LayoutParams(dp(26), dp(26)); nlp.topMargin = dp(4); n.layoutParams = nlp
                    if (iso == hoje) { n.setBackgroundResource(R.drawable.tab_on); n.setTextColor(Color.parseColor("#2A0F22")); n.setTypeface(n.typeface, android.graphics.Typeface.BOLD) }
                    cel.addView(n)
                    val pts = TextView(this); pts.gravity = Gravity.CENTER; pts.textSize = 9f
                    pts.text = if (pend > 0) pend.toString() + (if (pend == 1) " item" else " itens") else ""
                    pts.setTextColor(0xFFFFFFFF.toInt()); pts.setTypeface(pts.typeface, android.graphics.Typeface.BOLD); cel.addView(pts)
                    cel.setOnClickListener { agDiaSel = iso; renderAgenda() }
                }
                lin.addView(cel); dia++
            }
            agendaContainer.addView(lin)
        }
        // compromissos do dia escolhido
        val sel = agDiaSel!!
        val doDia = (porDia[sel] ?: emptyList()).sortedWith(compareBy<Compromisso>({ it.concluida }, { it.hora }))
        val f = faltaDias(sel)
        agendaContainer.addView(cabecalhoDia(labelDiaAgenda(sel, hoje) + (if (f != null && sel != hoje) " · " + f.first else "") + " · " + doDia.size + " compromisso(s)", sel < hoje))
        if (doDia.isEmpty()) { val v = TextView(this); v.text = "Nada neste dia. Toque em \"Novo compromisso\" (a data já vem preenchida)."
            v.setTextColor(0xFFB99BB4.toInt()); v.textSize = 13f; agendaContainer.addView(v) }
        for (x in doDia) agendaContainer.addView(chipCompromisso(x))
    }

    private fun cabecalhoDia(txt: String, atrasado: Boolean): View {
        val t = TextView(this); t.text = txt; t.textSize = 12f
        t.setTypeface(t.typeface, android.graphics.Typeface.BOLD)
        t.setTextColor(if (atrasado) Color.parseColor("#FF8FA3") else 0xFFB99BB4.toInt())
        t.setPadding(dp(2), dp(14), 0, dp(4)); return t
    }

    private fun chipCompromisso(c: Compromisso): View {
        val card = LinearLayout(this); card.orientation = LinearLayout.HORIZONTAL; card.gravity = Gravity.CENTER_VERTICAL
        card.setBackgroundResource(R.drawable.row_bg); card.setPadding(dp(10), dp(10), dp(12), dp(10))
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); lp.bottomMargin = dp(6); card.layoutParams = lp
        val check = ImageView(this); check.setImageResource(if (c.concluida) R.drawable.ic_check_on else R.drawable.ic_check_off)
        val cLp = LinearLayout.LayoutParams(dp(26), dp(26)); cLp.marginEnd = dp(10); check.layoutParams = cLp
        check.setOnClickListener { toggleCompromisso(c) }
        card.addView(check)
        val col = LinearLayout(this); col.orientation = LinearLayout.VERTICAL
        col.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        val desc = TextView(this); desc.text = c.descricao; desc.textSize = 15f
        if (c.concluida) { desc.paintFlags = desc.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG; desc.setTextColor(0xFFB99BB4.toInt()) } else desc.setTextColor(0xFFFBEEF6.toInt())
        col.addView(desc)
        val sub = StringBuilder()
        if (c.hora.isNotEmpty()) sub.append("🕐 ").append(c.hora)
        if (c.corpo.isNotBlank()) { if (sub.isNotEmpty()) sub.append("  ·  "); val b = c.corpo.trim(); sub.append(if (b.length > 50) b.substring(0, 50) + "…" else b) }
        if (sub.isNotEmpty()) { val st = TextView(this); st.text = sub.toString(); st.setTextColor(0xFFB99BB4.toInt()); st.textSize = 12f; col.addView(st) }
        card.addView(col)
        // quantos dias faltam (Hoje / Amanha / Faltam N dias / Atrasado)
        val fd = if (c.concluida) null else faltaDias(c.data)
        if (fd != null) {
            val b = TextView(this); b.text = fd.first; b.textSize = 11f; b.setTextColor(fd.second)
            b.setTypeface(b.typeface, android.graphics.Typeface.BOLD)
            val bg = android.graphics.drawable.GradientDrawable(); bg.cornerRadius = dp(99).toFloat()
            bg.setColor((fd.second and 0x00FFFFFF) or 0x26000000); b.background = bg
            b.setPadding(dp(8), dp(3), dp(8), dp(3))
            val bLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); bLp.marginStart = dp(6); b.layoutParams = bLp
            card.addView(b)
        }
        val lixo = ImageView(this); lixo.setImageResource(R.drawable.ic_trash)
        val xLp = LinearLayout.LayoutParams(dp(22), dp(22)); xLp.marginStart = dp(8); lixo.layoutParams = xLp
        lixo.setOnClickListener { excluirCompromisso(c) }
        card.addView(lixo)
        col.setOnClickListener { editorCompromisso(c) }
        desc.setOnClickListener { editorCompromisso(c) }
        return card
    }

    private fun escolherDataAgenda(onPick: (String) -> Unit) {
        val base = (agendaDataSel ?: hojeData()).ifEmpty { hojeData() }.split("-")
        val cal = Calendar.getInstance()
        try { cal.set(base[0].toInt(), base[1].toInt() - 1, base[2].toInt()) } catch (_: Exception) {}
        val dlg = DatePickerDialog(this, { _, y, m, d -> onPick("%04d-%02d-%02d".format(y, m + 1, d)) },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
        dlg.setButton(DatePickerDialog.BUTTON_NEUTRAL, "Sem data") { _, _ -> onPick("") }
        dlg.show()
    }

    private fun addCompromisso(ent: EditText) {
        val d = ent.text.toString().trim(); if (d.isEmpty()) return
        val data = agendaDataSel ?: ""
        ent.setText("")
        io({
            val uid = store.uid!!; val tok = Api.idTokenValido(store)
            val c = Compromisso(id = Api.novoId(), descricao = d, data = data, hora = "", corpo = "",
                concluida = false, criadaEm = nowIso(), atualizadoEm = Api.agoraMs(), excluida = false)
            Api.gravarCompromisso(tok, uid, c); c
        }, { carregarAgenda() }, { e -> status.text = amigavel(e) })
    }

    private fun toggleCompromisso(c: Compromisso) {
        io({ val uid = store.uid!!; val tok = Api.idTokenValido(store); Api.marcarAgenda(tok, uid, c.id, !c.concluida)
            if (c.id.startsWith("wl_")) Api.patchDesejo(tok, uid, c.id.removePrefix("wl_"), linkedMapOf("comprado" to !c.concluida)) },
           { carregarAgenda(); if (c.id.startsWith("wl_")) carregarWishlist() }, { e -> status.text = amigavel(e) })
    }

    private fun excluirCompromisso(c: Compromisso) {
        AlertDialog.Builder(this).setTitle("Excluir").setMessage("Excluir \"${c.descricao}\"?")
            .setPositiveButton("Excluir") { _, _ ->
                io({ val uid = store.uid!!; val tok = Api.idTokenValido(store); Api.excluirAgenda(tok, uid, c.id) },
                   { carregarAgenda() }, { e -> status.text = amigavel(e) })
            }.setNegativeButton("Cancelar", null).show()
    }

    private val LEMBRETE_LABELS = listOf("Não notificar", "Na hora", "30 min antes", "1 hora antes", "2 horas antes", "1 dia antes")
    private val LEMBRETE_MIN = listOf<Int?>(null, 0, 30, 60, 120, 1440)

    private fun editorCompromisso(c: Compromisso) {
        val box = LinearLayout(this); box.orientation = LinearLayout.VERTICAL; box.setPadding(dp(20), dp(10), dp(20), dp(4))
        fun campo(hint: String): EditText { val e = EditText(this); e.hint = hint; e.setHintTextColor(0xFFB99BB4.toInt()); e.setTextColor(0xFFFBEEF6.toInt()); e.setBackgroundResource(R.drawable.field_bg); e.setPadding(dp(10), dp(8), dp(10), dp(8)); return e }
        val edDesc = campo("Título"); edDesc.setText(c.descricao); edDesc.maxLines = 2; box.addView(edDesc)
        val linDH = LinearLayout(this); linDH.orientation = LinearLayout.HORIZONTAL
        val dataLoc = arrayOf(c.data)
        val btData = TextView(this); btData.setBackgroundResource(R.drawable.field_bg); btData.setTextColor(0xFFFBEEF6.toInt()); btData.setPadding(dp(10), dp(10), dp(10), dp(10))
        val lp1 = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); lp1.topMargin = dp(8); lp1.marginEnd = dp(6); btData.layoutParams = lp1
        fun pintaD() { btData.text = if (dataLoc[0].isEmpty()) "📅 Sem data" else "📅 " + ddmmLabel(dataLoc[0]) }
        pintaD()
        btData.setOnClickListener {
            val base = dataLoc[0].ifEmpty { hojeData() }.split("-"); val cal = Calendar.getInstance()
            try { cal.set(base[0].toInt(), base[1].toInt() - 1, base[2].toInt()) } catch (_: Exception) {}
            val dlg = DatePickerDialog(this, { _, y, m, d -> dataLoc[0] = "%04d-%02d-%02d".format(y, m + 1, d); pintaD() }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
            dlg.setButton(DatePickerDialog.BUTTON_NEUTRAL, "Sem data") { _, _ -> dataLoc[0] = ""; pintaD() }
            dlg.show()
        }
        val edHora = campo("HH:MM"); edHora.setText(c.hora)
        val lp2 = LinearLayout.LayoutParams(dp(96), ViewGroup.LayoutParams.WRAP_CONTENT); lp2.topMargin = dp(8); edHora.layoutParams = lp2
        linDH.addView(btData); linDH.addView(edHora); box.addView(linDH)
        val lblLem = TextView(this); lblLem.text = "Notificar antes (precisa de horário)"; lblLem.setTextColor(0xFFB99BB4.toInt()); lblLem.textSize = 12f
        lblLem.setTypeface(lblLem.typeface, android.graphics.Typeface.BOLD); lblLem.setPadding(dp(2), dp(12), 0, dp(2)); box.addView(lblLem)
        val spLembrete = Spinner(this); spLembrete.adapter = adaptador(LEMBRETE_LABELS)
        spLembrete.setSelection(LEMBRETE_MIN.indexOf(c.notificarAntes).let { if (it >= 0) it else 0 })
        box.addView(spLembrete)
        val edCorpo = campo("Anotações (texto livre)..."); edCorpo.setText(c.corpo); edCorpo.minLines = 3; edCorpo.maxLines = 8; edCorpo.gravity = Gravity.TOP
        val lpc = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); lpc.topMargin = dp(8); edCorpo.layoutParams = lpc; box.addView(edCorpo)

        // anexos
        anexoAlvo = c
        val anexos = ArrayList<HashMap<String, Any?>>(c.anexos.map { HashMap(it) })
        anexoLista = anexos
        val lblAnx = TextView(this); lblAnx.text = "Anexos (até 20 MB no total)"; lblAnx.setTextColor(0xFFB99BB4.toInt()); lblAnx.textSize = 12f
        lblAnx.setTypeface(lblAnx.typeface, android.graphics.Typeface.BOLD); lblAnx.setPadding(dp(2), dp(14), 0, dp(4)); box.addView(lblAnx)
        val boxAnx = LinearLayout(this); boxAnx.orientation = LinearLayout.VERTICAL; box.addView(boxAnx)
        fun pintarAnx() {
            boxAnx.removeAllViews()
            if (anexos.isEmpty()) { val v = TextView(this); v.text = "Sem anexos."; v.setTextColor(0xFFB99BB4.toInt()); v.textSize = 12f; boxAnx.addView(v); return }
            for (an in anexos) {
                val row = LinearLayout(this); row.orientation = LinearLayout.HORIZONTAL; row.gravity = Gravity.CENTER_VERTICAL
                row.setBackgroundResource(R.drawable.field_bg); row.setPadding(dp(10), dp(8), dp(10), dp(8))
                val rlp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); rlp.topMargin = dp(5); row.layoutParams = rlp
                val nm = TextView(this); nm.text = (an["nome"] as? String) ?: "arquivo"; nm.setTextColor(0xFFFBEEF6.toInt()); nm.textSize = 13f; nm.maxLines = 2
                nm.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                row.addView(nm)
                val ab = TextView(this); ab.text = "↗"; ab.setTextColor(0xFFFF86BD.toInt()); ab.textSize = 16f; ab.setPadding(dp(8), 0, dp(8), 0)
                ab.setOnClickListener { abrirAnexo(an) }; row.addView(ab)
                val rm = TextView(this); rm.text = "✕"; rm.setTextColor(0xFFB99BB4.toInt()); rm.textSize = 15f; rm.setPadding(dp(6), 0, dp(2), 0)
                rm.setOnClickListener {
                    anexos.remove(an)
                    val pth = (an["path"] as? String) ?: ""
                    io({ val tok = Api.idTokenValido(store); Api.apagarAnexo(tok, pth); Api.patchAgenda(tok, store.uid!!, c.id, linkedMapOf("anexos" to anexos, "atualizado_em" to Api.agoraMs())) }, { carregarAgenda() }, { })
                    pintarAnx()
                }
                row.addView(rm); boxAnx.addView(row)
            }
        }
        anexoRefresh = { pintarAnx() }
        pintarAnx()
        val btAnexar = TextView(this); btAnexar.text = "📎 Anexar arquivo"; btAnexar.gravity = Gravity.CENTER
        btAnexar.setBackgroundResource(R.drawable.field_bg); btAnexar.setTextColor(0xFFFBEEF6.toInt()); btAnexar.textSize = 13f
        btAnexar.setPadding(dp(10), dp(10), dp(10), dp(10))
        val baLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); baLp.topMargin = dp(8); btAnexar.layoutParams = baLp
        btAnexar.setOnClickListener { anexoLauncher.launch("*/*") }
        box.addView(btAnexar)

        val scroll = ScrollView(this); scroll.addView(box)
        // salva o que foi editado; com "concluir" != null tambem marca/desmarca como concluido
        fun gravar(concluir: Boolean?) {
            val nd = edDesc.text.toString().trim()
            val campos = linkedMapOf<String, Any?>(
                "descricao" to (if (nd.isEmpty()) c.descricao else nd),
                "data" to dataLoc[0], "hora" to edHora.text.toString().trim(),
                "corpo" to edCorpo.text.toString(), "anexos" to anexos,
                "notificarAntes" to LEMBRETE_MIN[spLembrete.selectedItemPosition],
                "atualizado_em" to Api.agoraMs())
            if (concluir != null) {
                campos["concluida"] = concluir
                campos["concluida_em"] = if (concluir) java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).format(java.util.Date()) else null
            }
            io({ val uid = store.uid!!; val tok = Api.idTokenValido(store); Api.patchAgenda(tok, uid, c.id, campos)
                   if (concluir != null && c.id.startsWith("wl_")) Api.patchDesejo(tok, uid, c.id.removePrefix("wl_"), linkedMapOf("comprado" to concluir)) },
               { carregarAgenda(); if (concluir != null && c.id.startsWith("wl_")) carregarWishlist(); if (concluir == true) Toast.makeText(this, "Compromisso concluído ✓", Toast.LENGTH_SHORT).show() },
               { e -> status.text = amigavel(e) })
        }
        AlertDialog.Builder(this).setTitle(if (c.concluida) "Compromisso · concluído ✓" else "Compromisso").setView(scroll)
            .setPositiveButton("Salvar") { _, _ -> gravar(null) }
            .setNeutralButton(if (c.concluida) "↺ Reabrir" else "✓ Concluir") { _, _ -> gravar(!c.concluida) }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun onAnexoEscolhido(uri: Uri) {
        val alvo = anexoAlvo ?: return
        val lista = anexoLista ?: return
        val bytes = try { contentResolver.openInputStream(uri)?.use { it.readBytes() } } catch (e: Exception) { null }
        if (bytes == null) { status.text = "Nao consegui ler o arquivo."; return }
        val lim = 20 * 1024 * 1024
        val usado = lista.sumOf { ((it["tamanho"] as? Number)?.toLong() ?: 0L) }
        if (usado + bytes.size > lim) { Toast.makeText(this, "Limite de 20 MB por compromisso. Livre: " + ((lim - usado) / 1024 / 1024) + " MB.", Toast.LENGTH_LONG).show(); return }
        val nome = nomeDoUri(uri)
        val tipo = contentResolver.getType(uri) ?: "application/octet-stream"
        val safe = nome.replace(Regex("[^\\w.\\-]+"), "_").takeLast(60)
        val path = "users/${store.uid}/agenda/${alvo.id}/${System.currentTimeMillis()}_$safe"
        Toast.makeText(this, "Enviando $nome…", Toast.LENGTH_SHORT).show()
        io({
            val tok = Api.idTokenValido(store)
            Api.uploadAnexo(tok, path, bytes, tipo)
            val an = HashMap<String, Any?>(); an["nome"] = nome; an["path"] = path; an["tamanho"] = bytes.size; an["tipo"] = tipo
            lista.add(an)
            Api.patchAgenda(tok, store.uid!!, alvo.id, linkedMapOf("anexos" to lista, "atualizado_em" to Api.agoraMs()))
            an
        }, { anexoRefresh?.invoke(); carregarAgenda() }, { e -> status.text = amigavel(e) })
    }

    private fun nomeDoUri(uri: Uri): String {
        var nome = "arquivo"
        try {
            contentResolver.query(uri, null, null, null, null)?.use { cur ->
                val idx = cur.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cur.moveToFirst()) { val n = cur.getString(idx); if (!n.isNullOrEmpty()) nome = n }
            }
        } catch (_: Exception) {}
        return nome
    }

    private fun comBiometria(acao: () -> Unit) {
        val bm = androidx.biometric.BiometricManager.from(this)
        val auth = androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (bm.canAuthenticate(auth) != androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS) { acao(); return }
        val prompt = androidx.biometric.BiometricPrompt(this, androidx.core.content.ContextCompat.getMainExecutor(this),
            object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) { acao() }
            })
        val info = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
            .setTitle("Desbloquear anexo").setSubtitle("Confirme para abrir o arquivo")
            .setAllowedAuthenticators(auth).build()
        try { prompt.authenticate(info) } catch (e: Exception) { acao() }
    }

    private fun abrirAnexo(an: Map<String, Any?>) {
        val path = an["path"] as? String ?: return
        comBiometria {
            Toast.makeText(this, "Abrindo…", Toast.LENGTH_SHORT).show()
            io({ val tok = Api.idTokenValido(store); Api.baixarAnexo(tok, path) },
               { bytes -> abrirBytes(bytes, (an["nome"] as? String) ?: "arquivo", an["tipo"] as? String) },
               { e -> status.text = amigavel(e) })
        }
    }

    private fun abrirBytes(bytes: ByteArray, nome: String, tipo: String?) {
        try {
            val dir = File(cacheDir, "anexos"); dir.mkdirs()
            val safe = nome.replace(Regex("[^\\w.\\-]+"), "_").ifEmpty { "arquivo" }
            val f = File(dir, safe); f.writeBytes(bytes)
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
            val i = Intent(Intent.ACTION_VIEW)
            i.setDataAndType(uri, tipo ?: "*/*")
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        } catch (e: Exception) { status.text = "Nenhum app pra abrir esse arquivo." }
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

        val cab = LinearLayout(this); cab.orientation = LinearLayout.HORIZONTAL; cab.gravity = Gravity.BOTTOM
        val titulo = TextView(this)
        titulo.text = nome
        titulo.setTextColor(0xFFFFD9EA.toInt()); titulo.textSize = 20f
        titulo.typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD_ITALIC)
        titulo.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        cab.addView(titulo)
        val lblPend = TextView(this)
        lblPend.text = "${pend.size} pendente(s)"
        lblPend.setTextColor(0xFFB99BB4.toInt()); lblPend.textSize = 12f
        cab.addView(lblPend)
        card.addView(cab)

        card.addView(cartaoSaldo(nome, abaId, saldo))
        card.addView(barraAbas(nome, abaId))

        // adicionar item (desc, valor, recorrencia, +)
        val addRow = LinearLayout(this); addRow.orientation = LinearLayout.HORIZONTAL; addRow.gravity = Gravity.CENTER_VERTICAL
        addRow.setPadding(0, dp(8), 0, dp(6))
        val ent = EditText(this); ent.hint = "Novo item..."; ent.setHintTextColor(0xFFB99BB4.toInt())
        ent.setTextColor(0xFFFBEEF6.toInt()); ent.setBackgroundResource(R.drawable.field_bg)
        ent.setPadding(dp(10), dp(8), dp(10), dp(8)); ent.maxLines = 1
        ent.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        addRow.addView(ent)
        val entValor = EditText(this); entValor.hint = "valor"; entValor.setHintTextColor(0xFFB99BB4.toInt())
        entValor.setTextColor(0xFFFBEEF6.toInt()); entValor.setBackgroundResource(R.drawable.field_bg)
        entValor.setPadding(dp(8), dp(8), dp(8), dp(8)); entValor.maxLines = 1
        tecladoValor(entValor)
        val evLp = LinearLayout.LayoutParams(dp(68), ViewGroup.LayoutParams.WRAP_CONTENT); evLp.marginStart = dp(6)
        entValor.layoutParams = evLp; addRow.addView(entValor)
        val rec = TextView(this); rec.text = "🔁"; rec.textSize = 16f; rec.gravity = Gravity.CENTER
        rec.setBackgroundResource(R.drawable.field_bg); rec.setPadding(dp(10), dp(8), dp(10), dp(8))
        val recLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); recLp.marginStart = dp(6)
        rec.layoutParams = recLp; addRow.addView(rec)
        val mais = TextView(this); mais.text = "+"; mais.setTextColor(Color.parseColor("#2A0F22")); mais.textSize = 18f
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
            vazio.setTextColor(0xFFB99BB4.toInt()); vazio.textSize = 13f; card.addView(vazio)
        }
        for (t in pend) card.addView(linhaItemLista(t))
        val parc = parciaisDe(nome, abaId)
        if (parc.isNotEmpty()) {
            val sep = TextView(this); sep.text = "QUITAÇÃO PARCIAL"; sep.setTextColor(0xFFB99BB4.toInt())
            sep.textSize = 10f; sep.setPadding(0, dp(8), 0, dp(2)); card.addView(sep)
            for (q in parc) card.addView(linhaParcial(nome, abaId, q))
        }
        if (feitos.isNotEmpty()) {
            val sep = TextView(this); sep.text = "QUITADOS"; sep.setTextColor(0xFFB99BB4.toInt())
            sep.textSize = 10f; sep.setPadding(0, dp(8), 0, dp(2)); card.addView(sep)
            for (t in feitos) card.addView(linhaItemLista(t))
        }
        return card
    }

    // cartao de saldo: valor grande, variacao vs. mes anterior e grafico da evolucao (ate 4 meses)
    private fun cartaoSaldo(nome: String, abaId: String, saldo: Double): View {
        val box = LinearLayout(this); box.orientation = LinearLayout.VERTICAL
        box.setBackgroundResource(R.drawable.saldo_bg); box.setPadding(dp(14), dp(12), dp(14), dp(6))
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(10); box.layoutParams = lp

        val rot = TextView(this); rot.text = "SALDO DE " + mesLabel(abaId)
        rot.setTextColor(0xFFB99BB4.toInt()); rot.textSize = 10f; rot.letterSpacing = 0.12f
        box.addView(rot)

        val lin = LinearLayout(this); lin.orientation = LinearLayout.HORIZONTAL; lin.gravity = Gravity.CENTER_VERTICAL
        val v = TextView(this); v.text = "R$ " + fmtValor(saldo); v.textSize = 26f
        v.typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
        v.setTextColor(if (saldo < 0) Color.parseColor("#FF8FA3") else Color.parseColor("#7EE8D2"))
        v.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        lin.addView(v)

        val abas = abasDeLista(nome).filter { it.id <= abaId }.takeLast(4)
        val vals = abas.map { saldoAba(nome, it.id) }
        if (vals.size >= 2) {
            val ant = vals[vals.size - 2]
            val dif = saldo - ant
            val chip = TextView(this); chip.textSize = 11f
            chip.typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
            val sobe = dif >= 0
            val pct = if (ant != 0.0) " (" + String.format(java.util.Locale.US, "%.0f", dif / kotlin.math.abs(ant) * 100) + "%)" else ""
            chip.text = (if (sobe) "▲ " else "▼ ") + fmtValor(kotlin.math.abs(dif)) + pct
            chip.setTextColor(if (sobe) Color.parseColor("#7EE8D2") else Color.parseColor("#FF8FA3"))
            chip.setBackgroundResource(if (sobe) R.drawable.chip_up else R.drawable.chip_down)
            chip.setPadding(dp(9), dp(4), dp(9), dp(4))
            lin.addView(chip)
        }
        box.addView(lin)

        // a pagar / quitado + icone de quitar (o teto fica na linha tracejada; tocar no cartao edita o teto)
        val itensMes = itensDaAba(nome, abaId)
        val ap = aPagar(nome, abaId, itensMes); val qt = kotlin.math.abs(saldo) - ap
        val linPq = LinearLayout(this); linPq.orientation = LinearLayout.HORIZONTAL; linPq.gravity = Gravity.CENTER_VERTICAL
        fun mini(rot: String, v: Double, cor: String): TextView {
            val tv = TextView(this); tv.textSize = 11.5f; tv.typeface = android.graphics.Typeface.MONOSPACE
            val sp = android.text.SpannableStringBuilder(rot + " ")
            sp.setSpan(android.text.style.ForegroundColorSpan(0xFFB99BB4.toInt()), 0, sp.length, 0)
            sp.setSpan(android.text.style.RelativeSizeSpan(0.9f), 0, sp.length, 0)
            val i = sp.length; sp.append(fmtValor(v))
            sp.setSpan(android.text.style.ForegroundColorSpan(Color.parseColor(cor)), i, sp.length, 0)
            sp.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), i, sp.length, 0)
            tv.text = sp
            val bg = android.graphics.drawable.GradientDrawable(); bg.cornerRadius = dp(99).toFloat(); bg.setColor(0x38000000); tv.background = bg
            tv.setPadding(dp(10), dp(5), dp(10), dp(5))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); lp.marginEnd = dp(6); tv.layoutParams = lp
            return tv
        }
        linPq.addView(mini("a pagar", ap, "#FF86BD")); linPq.addView(mini("quitado", qt, "#7EE8D2"))
        val esp = View(this); esp.layoutParams = LinearLayout.LayoutParams(0, 1, 1f); linPq.addView(esp)
        val tudoQuitado = ap <= 0.0 && itensMes.isNotEmpty()
        val ic = TextView(this); ic.text = if (tudoQuitado) "↺" else "✓"; ic.textSize = 16f; ic.gravity = Gravity.CENTER
        ic.setTypeface(ic.typeface, android.graphics.Typeface.BOLD)
        ic.setTextColor(Color.parseColor(if (tudoQuitado) "#FF86BD" else "#7EE8D2"))
        val icBg = android.graphics.drawable.GradientDrawable(); icBg.shape = android.graphics.drawable.GradientDrawable.OVAL
        icBg.setColor(if (tudoQuitado) 0x24FF86BD else 0x247EE8D2); icBg.setStroke(dp(1), if (tudoQuitado) 0x8CFF86BD.toInt() else 0x8C7EE8D2.toInt())
        ic.background = icBg; ic.layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
        ic.contentDescription = if (tudoQuitado) "Reabrir mês" else "Quitar"
        ic.setOnClickListener { if (tudoQuitado) reabrirMes(nome, abaId) else if (itensMes.isNotEmpty()) dialogQuitar(nome, abaId) }
        linPq.addView(ic)
        val lpp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lpp.topMargin = dp(4); lpp.bottomMargin = dp(2); box.addView(linPq, lpp)
        box.isClickable = true; box.setOnClickListener { dialogProjetado(nome, abaId) }   // tocar no cartao abre o teto

        if (vals.size >= 2) {
            val mesesCurtos = listOf("jan","fev","mar","abr","mai","jun","jul","ago","set","out","nov","dez")
            val evo = EvoView(this)
            evo.setDados(abas.map { a -> val p = a.id.split("-"); mesesCurtos[p[1].toInt() - 1] + "/" + p[0].takeLast(2) }, vals,
                abas.map { projetado[nome]?.get(it.id) })
            val elp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            elp.topMargin = dp(4); evo.layoutParams = elp
            evo.setOnClickListener { dialogProjetado(nome, abaId) }
            box.addView(evo)
        } else {
            val dica = TextView(this); dica.text = "A evolução aparece quando houver mais de um mês."
            dica.setTextColor(0xFF7A5C76.toInt()); dica.textSize = 11f; dica.setPadding(0, dp(4), 0, dp(6))
            box.addView(dica)
        }
        return box
    }

    // ================================================ QUITADO
    // Item quitado = concluida. O total do mes nao muda; "a pagar" = itens em aberto - quitacoes parciais.
    private fun parciaisDe(nome: String, ym: String): List<Quitacao> = quitacoes[nome]?.get(ym) ?: emptyList()
    private fun aPagar(nome: String, ym: String, itens: List<Tarefa> = itensDaAba(nome, ym)): Double {
        val total = itens.mapNotNull { it.valor }.sum(); val sinal = if (total < 0) -1.0 else 1.0
        val aberto = sinal * itens.filter { !it.concluida }.mapNotNull { it.valor }.sum()
        return kotlin.math.max(0.0, aberto - parciaisDe(nome, ym).sumOf { it.valor })
    }
    private fun copiaQuitacoes() = quitacoes.mapValues { (_, m) -> m.mapValues { (_, l) -> l.toList() } }
    private fun salvarQuitacoesAsync() {
        val copia = copiaQuitacoes()
        io({ Api.salvarQuitacoes(Api.idTokenValido(store), store.uid!!, copia) }, { }, { e -> status.text = amigavel(e) })
    }

    private fun linhaParcial(nome: String, ym: String, q: Quitacao): View {
        val row = layoutInflater.inflate(R.layout.item_app, listasContainer, false)
        row.findViewById<ImageView>(R.id.item_check).setImageDrawable(null)
        val texto = row.findViewById<TextView>(R.id.item_texto); texto.text = "↓  Quitação parcial"; texto.setTextColor(0xFFC7A8F7.toInt())
        val dataTv = row.findViewById<TextView>(R.id.item_data); dataTv.text = if (q.data.length >= 10) ddmmLabel(q.data) else ""; dataTv.setTextColor(0xFFB99BB4.toInt())
        val valor = row.findViewById<TextView>(R.id.item_valor); valor.text = "-" + fmtValor(q.valor); valor.setTextColor(0xFFC7A8F7.toInt())
        row.findViewById<ImageView>(R.id.item_lixo).setOnClickListener {
            AlertDialog.Builder(this).setMessage("Apagar a quitação parcial de R$ ${fmtValor(q.valor)}?")
                .setPositiveButton("Apagar") { _, _ ->
                    val antes = copiaQuitacoes()
                    quitacoes[nome]?.get(ym)?.removeAll { it.id == q.id }
                    renderListas(); salvarQuitacoesAsync()
                    desfazerBarra("Quitação parcial apagada.") { restaurarQuitado(antes, emptyList(), false) }
                }.setNegativeButton("Cancelar", null).show()
        }
        return row
    }

    private fun dialogQuitar(nome: String, ym: String) {
        val itens = itensDaAba(nome, ym); val ap = aPagar(nome, ym, itens)
        val box = LinearLayout(this); box.orientation = LinearLayout.VERTICAL; box.setPadding(dp(20), dp(4), dp(20), dp(4))
        val info = TextView(this); info.text = "A pagar: R$ " + fmtValor(ap); info.typeface = android.graphics.Typeface.MONOSPACE
        info.setTextColor(0xFFB99BB4.toInt()); info.textSize = 13f; info.setPadding(0, 0, 0, dp(8)); box.addView(info)
        val rg = RadioGroup(this)
        val r1 = RadioButton(this); r1.text = "Marcar tudo como quitado"; r1.id = View.generateViewId(); r1.setTextColor(0xFFFBEEF6.toInt())
        val r2 = RadioButton(this); r2.text = "Lançar valor pago"; r2.id = View.generateViewId(); r2.setTextColor(0xFFFBEEF6.toInt())
        rg.addView(r1); rg.addView(r2); rg.check(r1.id); box.addView(rg)
        val ed = EditText(this); ed.hint = "0,00"; ed.setHintTextColor(0xFF7A5C76.toInt()); ed.setTextColor(0xFFFBEEF6.toInt())
        tecladoValor(ed); ed.typeface = android.graphics.Typeface.MONOSPACE; ed.textSize = 18f
        ed.setBackgroundResource(R.drawable.field_bg); ed.setPadding(dp(12), dp(10), dp(12), dp(10)); box.addView(ed)
        val prev = TextView(this); prev.textSize = 12.5f; prev.setTextColor(0xFFC7A8F7.toInt()); prev.setPadding(dp(2), dp(8), 0, 0); box.addView(prev)
        fun atualizar() {
            if (rg.checkedRadioButtonId == r1.id) { prev.text = "Todos os itens ficam quitados."; return }
            val v = parseValor(ed.text.toString())?.let { kotlin.math.abs(it) } ?: 0.0
            prev.text = if (v <= 0.0) "" else if (v >= ap) "Valor cobre o a pagar → tudo quitado."
                else "Entra quitação parcial de " + fmtValor(v) + ". Fica a pagar: " + fmtValor(ap - v) + "."
        }
        rg.setOnCheckedChangeListener { _, _ -> atualizar() }
        ed.setOnFocusChangeListener { _, f -> if (f) rg.check(r2.id) }
        ed.addTextChangedListener(object : android.text.TextWatcher { override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {} override fun afterTextChanged(s: android.text.Editable?) { if (rg.checkedRadioButtonId != r2.id) rg.check(r2.id); atualizar() } })
        atualizar()
        val dlg = AlertDialog.Builder(this).setTitle("Quitar " + mesLabel(ym)).setView(box)
            .setPositiveButton("Confirmar", null).setNegativeButton("Cancelar", null).create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val abertos = itens.filter { !it.concluida }.map { it.id }
                val antes = copiaQuitacoes()
                if (rg.checkedRadioButtonId == r1.id) { quitarTudo(abertos); desfazerBarra("Tudo quitado.") { restaurarQuitado(antes, abertos, false) } }
                else {
                    val v = parseValor(ed.text.toString())?.let { kotlin.math.abs(it) } ?: 0.0
                    if (v <= 0.0) { ed.requestFocus(); return@setOnClickListener }
                    if (v >= ap) { quitarTudo(abertos); desfazerBarra("Tudo quitado.") { restaurarQuitado(antes, abertos, false) } }
                    else {
                        quitacoes.getOrPut(nome) { HashMap() }.getOrPut(ym) { ArrayList() }.add(Quitacao(Api.novoId(), v, hojeData()))
                        renderListas(); salvarQuitacoesAsync()
                        desfazerBarra("Quitação parcial lançada.") { restaurarQuitado(antes, emptyList(), false) }
                    }
                }
                dlg.dismiss()
            }
        }
        dlg.show()
    }

    // aplica na hora na tela e grava todos num commit so
    private fun quitarTudo(ids: List<String>, quitado: Boolean = true) {
        if (ids.isEmpty()) return
        val set = ids.toSet()
        todasTarefas = todasTarefas.map { if (it.id in set) it.copy(concluida = quitado) else it }
        renderListas()
        io({ Api.marcarVarios(Api.idTokenValido(store), store.uid!!, ids, quitado) },
           { ListWidget.atualizar(this); carregarTudo() }, { e -> status.text = amigavel(e); carregarTudo() })
    }

    // desfazer: devolve as quitacoes parciais como estavam e volta os itens ao estado anterior
    private fun restaurarQuitado(antes: Map<String, Map<String, List<Quitacao>>>, ids: List<String>, quitado: Boolean) {
        quitacoes = HashMap(antes.mapValues { (_, m) -> HashMap(m.mapValues { (_, l) -> l.toMutableList() }) as MutableMap<String, MutableList<Quitacao>> })
        salvarQuitacoesAsync()
        if (ids.isNotEmpty()) quitarTudo(ids, quitado) else renderListas()
    }

    private fun reabrirMes(nome: String, ym: String) {
        AlertDialog.Builder(this).setTitle("Reabrir " + mesLabel(ym) + "?")
            .setMessage("Todos os itens voltam para em aberto e as quitações parciais do mês são apagadas.")
            .setPositiveButton("Reabrir") { _, _ ->
                val quitados = itensDaAba(nome, ym).filter { it.concluida }.map { it.id }
                val antes = copiaQuitacoes()
                quitacoes[nome]?.remove(ym); salvarQuitacoesAsync()
                if (quitados.isNotEmpty()) quitarTudo(quitados, false) else renderListas()
                desfazerBarra("Mês reaberto.") { restaurarQuitado(antes, quitados, true) }
            }.setNegativeButton("Cancelar", null).show()
    }

    // barra no rodape com DESFAZER (some em 8 s)
    private var barraDesfazer: View? = null
    private fun desfazerBarra(msg: String, desfazer: () -> Unit) {
        val raiz = findViewById<android.widget.FrameLayout>(android.R.id.content)
        barraDesfazer?.let { raiz.removeView(it) }
        val bar = LinearLayout(this); bar.orientation = LinearLayout.HORIZONTAL; bar.gravity = Gravity.CENTER_VERTICAL
        val bg = android.graphics.drawable.GradientDrawable(); bg.cornerRadius = dp(16).toFloat(); bg.setColor(0xFF3A2238.toInt()); bg.setStroke(dp(1), 0x66FF86BD)
        bar.background = bg; bar.setPadding(dp(16), dp(6), dp(6), dp(6)); bar.elevation = dp(8).toFloat()
        val t = TextView(this); t.text = msg; t.setTextColor(0xFFFBEEF6.toInt()); t.textSize = 14f
        t.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); bar.addView(t)
        val b = TextView(this); b.text = "DESFAZER"; b.setTextColor(Color.parseColor("#7EE8D2")); b.textSize = 14f
        b.setTypeface(b.typeface, android.graphics.Typeface.BOLD); b.setPadding(dp(14), dp(10), dp(14), dp(10)); bar.addView(b)
        val lp = android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
        lp.setMargins(dp(14), 0, dp(14), dp(24)); raiz.addView(bar, lp); barraDesfazer = bar
        val fechar = Runnable { raiz.removeView(bar); if (barraDesfazer === bar) barraDesfazer = null }
        b.setOnClickListener { raiz.removeCallbacks(fechar); fechar.run(); desfazer() }
        raiz.postDelayed(fechar, 8000)
    }

    private fun dialogProjetado(nome: String, abaId: String) { dialogTeto(abaId, listOf(nome)) { renderListas() } }

    // Teto de gastos do mes (um campo por lista). Fica gravado por mes ("YYYY-MM"), entao vale tambem
    // para meses futuros sem aba: quando a aba do mes for criada, ela ja nasce com esse teto.
    private fun dialogTeto(ym: String, nomes: List<String>, depois: () -> Unit) {
        val box = LinearLayout(this); box.orientation = LinearLayout.VERTICAL; box.setPadding(dp(20), dp(8), dp(20), dp(4))
        val eds = ArrayList<Pair<String, EditText>>()
        for (nome in nomes) {
            if (nomes.size > 1) { val r = TextView(this); r.text = nome; r.textSize = 12f; r.setTextColor(0xFFB99BB4.toInt()); r.setPadding(0, dp(8), 0, dp(4)); box.addView(r) }
            val ed = EditText(this); ed.hint = "vazio = sem teto"; ed.setHintTextColor(0xFF7A5C76.toInt()); ed.setTextColor(0xFFFBEEF6.toInt())
            tecladoValor(ed); ed.typeface = android.graphics.Typeface.MONOSPACE; ed.textSize = 18f
            ed.setBackgroundResource(R.drawable.field_bg); ed.setPadding(dp(12), dp(10), dp(12), dp(10))
            projetado[nome]?.get(ym)?.let { ed.setText(fmtValor(it)) }
            box.addView(ed); eds.add(nome to ed)
        }
        val dlg = AlertDialog.Builder(this).setTitle("🎯 Teto · " + mesLabel(ym) + (if (nomes.size == 1) " · " + nomes[0] else "")).setView(box)
            .setPositiveButton("Salvar") { _, _ ->
                for ((nome, ed) in eds) {
                    val v = parseValor(ed.text.toString())?.let { kotlin.math.abs(it) }
                    val mp = projetado.getOrPut(nome) { HashMap() }
                    if (v == null || v == 0.0) mp.remove(ym) else mp[ym] = v
                }
                depois()
                val copia = HashMap(projetado.mapValues { HashMap(it.value) })
                io({ Api.salvarProjetado(Api.idTokenValido(store), store.uid!!, copia) }, { }, { e -> Toast.makeText(this, "Não consegui salvar o teto: " + (e.message ?: ""), Toast.LENGTH_LONG).show() })
            }
            .setNegativeButton("Cancelar", null).create()
        dlg.setOnShowListener { eds.firstOrNull()?.second?.let { it.requestFocus(); it.selectAll() }
            dlg.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE) }
        dlg.show()
    }

    private fun barraAbas(nome: String, abaAtivaId: String): View {
        val sv = android.widget.HorizontalScrollView(this); sv.isHorizontalScrollBarEnabled = false
        val svLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        svLp.topMargin = dp(6); svLp.bottomMargin = dp(4); sv.layoutParams = svLp
        val row = LinearLayout(this); row.orientation = LinearLayout.HORIZONTAL
        for (a in abasDeLista(nome)) {
            val ativo = a.id == abaAtivaId
            val chip = LinearLayout(this); chip.orientation = LinearLayout.VERTICAL; chip.gravity = Gravity.CENTER
            chip.setBackgroundResource(if (ativo) R.drawable.tab_on else R.drawable.field_bg)
            chip.setPadding(dp(12), dp(6), dp(12), dp(6))
            val clp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); clp.marginEnd = dp(6)
            chip.layoutParams = clp
            val mes = TextView(this); mes.text = a.nome; mes.textSize = 12f
            mes.setTypeface(mes.typeface, android.graphics.Typeface.BOLD)
            mes.setTextColor(if (ativo) Color.parseColor("#2A0F22") else 0xFFFBEEF6.toInt()); mes.gravity = Gravity.CENTER
            chip.addView(mes)
            val sa = saldoAba(nome, a.id)
            val sl = TextView(this); sl.text = fmtValor(sa); sl.textSize = 11f
            sl.setTextColor(if (ativo) Color.parseColor("#2A0F22") else if (sa < 0) Color.parseColor("#FF8FA3") else Color.parseColor("#7EE8D2"))
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
        b.setTextColor(0xFFFBEEF6.toInt()); b.setBackgroundResource(R.drawable.field_bg)
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
        val dataTv = row.findViewById<TextView>(R.id.item_data)
        val lixo = row.findViewById<ImageView>(R.id.item_lixo)
        texto.text = t.descricao + seloRec(t)
        dataTv.text = if (!t.data.isNullOrEmpty()) ddmmLabel(t.data) else "+ data"
        dataTv.setTextColor(if (!t.data.isNullOrEmpty()) 0xFFB99BB4.toInt() else 0xFF7A5C76.toInt())
        dataTv.setOnClickListener { editorItemLista(t) }
        if (t.concluida) {   // quitado: fica apagado (sem riscar), o valor continua no total do mes
            check.setImageResource(R.drawable.ic_check_on)
            texto.paintFlags = texto.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            texto.setTextColor(0xFFB99BB4.toInt()); row.alpha = 0.6f
        } else {
            check.setImageResource(R.drawable.ic_check_off)
            texto.paintFlags = texto.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            texto.setTextColor(0xFFFBEEF6.toInt())
        }
        if (t.valor != null) {
            valor.text = fmtValor(t.valor)
            valor.setTextColor(if (t.valor < 0) Color.parseColor("#FF8FA3") else Color.parseColor("#7EE8D2"))
        } else { valor.text = "+ valor"; valor.setTextColor(0xFFB99BB4.toInt()) }
        valor.setOnClickListener { editarValor(t) }
        check.setOnClickListener { marcar(t, !t.concluida) }
        texto.setOnClickListener { editorItemLista(t) }
        texto.setOnLongClickListener { menuItemLista(t); true }
        row.setOnLongClickListener { menuItemLista(t); true }
        lixo.setOnClickListener { excluirItemLista(t) }
        return row
    }

    private fun menuItemLista(t: Tarefa) {
        val opcoes = ArrayList<String>(); val acoes = ArrayList<() -> Unit>()
        opcoes.add("Editar item (data, valor)"); acoes.add { editorItemLista(t) }
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
        for (rb in listOf(rbP, rbF, rbR)) { rb.setTextColor(0xFFFBEEF6.toInt()); rg.addView(rb) }
        rg.check(rbP.id); cont.addView(rg)
        val edP = EditText(this); edP.hint = "Nº de parcelas (ex.: 12)"; edP.setHintTextColor(0xFFB99BB4.toInt())
        edP.setTextColor(0xFFFBEEF6.toInt()); edP.inputType = android.text.InputType.TYPE_CLASS_NUMBER; edP.setText("2"); cont.addView(edP)
        val edF = EditText(this); edF.hint = "Mês final MM/AAAA"; edF.setHintTextColor(0xFFB99BB4.toInt())
        edF.setTextColor(0xFFFBEEF6.toInt()); cont.addView(edF)
        val calc = TextView(this); calc.setTextColor(0xFFB99BB4.toInt()); calc.textSize = 12f; calc.setPadding(0, dp(6), 0, 0); cont.addView(calc)
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
        tecladoValor(ed)
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

    // Editor completo de um item da lista: descricao + data (default hoje) + valor.
    // Aberto pelo toque no item no widget e pelo menu/data do item no app.
    // Editor completo de um item da lista: descricao + data (mascara dd/mm/aaaa, default hoje) + valor.
    // Se a nova data cair em outro mes, move o item para a aba desse mes (ou erro se nao existir).
    private fun editorItemLista(t: Tarefa) {
        val box = LinearLayout(this); box.orientation = LinearLayout.VERTICAL; box.setPadding(dp(20), dp(10), dp(20), dp(4))
        fun campo(hint: String): EditText { val e = EditText(this); e.hint = hint; e.setHintTextColor(0xFFB99BB4.toInt()); e.setTextColor(0xFFFBEEF6.toInt()); e.setBackgroundResource(R.drawable.field_bg); e.setPadding(dp(10), dp(8), dp(10), dp(8)); return e }
        val edDesc = campo("Item"); edDesc.setText(t.descricao); edDesc.maxLines = 2; box.addView(edDesc)

        val lin = LinearLayout(this); lin.orientation = LinearLayout.HORIZONTAL
        val linLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); linLp.topMargin = dp(8); lin.layoutParams = linLp
        val edData = campo("dd/mm/aaaa"); mascaraDataBr(edData)
        edData.setText(ddmmaaaa((t.data ?: "").ifEmpty { hojeData() }))
        val lpd = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); lpd.marginEnd = dp(6); edData.layoutParams = lpd
        val btCal = TextView(this); btCal.text = "\uD83D\uDCC5"; btCal.textSize = 18f; btCal.gravity = Gravity.CENTER
        btCal.setBackgroundResource(R.drawable.field_bg); btCal.setPadding(dp(12), dp(8), dp(12), dp(8))
        btCal.setOnClickListener {
            val iso = parseDataBr(edData.text.toString()) ?: ""
            val base = iso.ifEmpty { hojeData() }.split("-"); val cal = Calendar.getInstance()
            try { cal.set(base[0].toInt(), base[1].toInt() - 1, base[2].toInt()) } catch (_: Exception) {}
            val dlg = DatePickerDialog(this, { _, y, m, d -> edData.setText("%02d/%02d/%04d".format(d, m + 1, y)) }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
            dlg.setButton(DatePickerDialog.BUTTON_NEUTRAL, "Sem data") { _, _ -> edData.setText("") }
            dlg.show()
        }
        lin.addView(edData); lin.addView(btCal); box.addView(lin)

        val edVal = campo("valor")
        tecladoValor(edVal)
        if (t.valor != null) edVal.setText(fmtValor(t.valor))
        val lpv = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); lpv.topMargin = dp(8); edVal.layoutParams = lpv
        box.addView(edVal)

        val btParc = TextView(this)
        btParc.text = when {
            t.recorrente -> "\uD83D\uDD01 Recorrente \u2014 editar"
            t.parcelas != null -> "\uD83D\uDD01 Parcelado \u2014 editar"
            else -> "\uD83D\uDD01 Parcelar / repetir"
        }
        btParc.gravity = Gravity.CENTER; btParc.textSize = 13f
        btParc.setBackgroundResource(R.drawable.field_bg); btParc.setTextColor(0xFFFBEEF6.toInt())
        btParc.setPadding(dp(10), dp(10), dp(10), dp(10))
        val lpP = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); lpP.topMargin = dp(8); btParc.layoutParams = lpP
        btParc.setOnClickListener { dialogRecorrencia(t) }
        box.addView(btParc)

        val dlg = AlertDialog.Builder(this).setTitle("Editar item").setView(box)
            .setPositiveButton("Salvar", null).setNegativeButton("Cancelar", null).create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val d = edDesc.text.toString().trim(); if (d.isEmpty()) { edDesc.error = "Informe o item"; return@setOnClickListener }
                val iso = parseDataBr(edData.text.toString())
                if (iso == null) { edData.error = "Data inválida (dd/mm ou dd/mm/aaaa)"; return@setOnClickListener }
                val v = parseValor(edVal.text.toString())
                val campos = hashMapOf<String, Any?>("descricao" to d, "data" to iso, "valor" to v, "atualizado_em" to Api.agoraMs())
                if (iso.isNotEmpty()) {
                    val novoMes = iso.substring(0, 7)
                    val abaAtual = t.aba ?: ""
                    if (novoMes != abaAtual) {
                        val existe = (gruposMeta.firstOrNull { it.nome == t.grupo }?.abas?.any { it.id == novoMes }) == true
                        if (!existe) {
                            AlertDialog.Builder(this).setMessage("Não existe lista para o mês " + mesLabel(novoMes) + ".")
                                .setPositiveButton("OK", null).show()
                            return@setOnClickListener
                        }
                        campos["aba"] = novoMes
                    }
                }
                dlg.dismiss()
                io({ val uid = store.uid!!; val tok = Api.idTokenValido(store); Api.patch(tok, uid, t.id, campos) },
                   { ListWidget.atualizar(this); carregarTudo() }, { e -> status.text = amigavel(e) })
            }
        }
        dlg.show()
    }

    private fun ddmmaaaa(iso: String): String { val p = iso.split("-"); return if (p.size == 3) p[2] + "/" + p[1] + "/" + p[0] else "" }

    // dd/mm[/aaaa] -> "AAAA-MM-DD". "" se vazio, null se invalido. 4 digitos = ddmm (ano atual),
    // 6 = ddmmaa, 8 = ddmmaaaa.
    private fun parseDataBr(txt: String): String? {
        val d = txt.filter { it.isDigit() }
        if (d.isEmpty()) return ""
        val dd: String; val mm: String; val yyyy: String
        when (d.length) {
            4 -> { dd = d.substring(0, 2); mm = d.substring(2, 4); yyyy = Calendar.getInstance().get(Calendar.YEAR).toString() }
            6 -> { dd = d.substring(0, 2); mm = d.substring(2, 4); yyyy = "20" + d.substring(4, 6) }
            8 -> { dd = d.substring(0, 2); mm = d.substring(2, 4); yyyy = d.substring(4, 8) }
            else -> return null
        }
        val dN = dd.toIntOrNull() ?: return null
        val mN = mm.toIntOrNull() ?: return null
        val yN = yyyy.toIntOrNull() ?: return null
        if (dN !in 1..31 || mN !in 1..12 || yN !in 2000..2100) return null
        return "%04d-%02d-%02d".format(yN, mN, dN)
    }

    // Aplica mascara dd/mm/aaaa enquanto digita.
    private fun mascaraDataBr(ed: EditText) {
        ed.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        ed.addTextChangedListener(object : android.text.TextWatcher {
            private var editando = false
            override fun afterTextChanged(s: android.text.Editable?) {
                if (editando || s == null) return
                editando = true
                val d = s.toString().filter { it.isDigit() }.take(8)
                val out = when {
                    d.length >= 5 -> d.substring(0, 2) + "/" + d.substring(2, 4) + "/" + d.substring(4)
                    d.length >= 3 -> d.substring(0, 2) + "/" + d.substring(2)
                    else -> d
                }
                ed.setText(out); ed.setSelection(out.length)
                editando = false
            }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })
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
                data = hojeData(),
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
        iaModoAtual = "lista"
        garantirCodigoIa { escolherFonteIA() }
    }

    private fun garantirCodigoIa(aoConcluir: () -> Unit) {
        if (!store.codigoIa.isNullOrBlank()) { aoConcluir(); return }
        val ed = EditText(this); ed.setText("murilo"); ed.setSelectAllOnFocus(true)
        ed.setTextColor(0xFFFBEEF6.toInt()); ed.setBackgroundResource(R.drawable.field_bg)
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
        txtL.setTextColor(0xFFFBEEF6.toInt()); txtL.gravity = Gravity.CENTER; txtL.setPadding(0, dp(10), 0, 0)
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
        val agenda = iaModoAtual == "agenda"
        val aba = if (!agenda) listaGrupos.firstOrNull()?.let { abaAtivaDeLista(it) } else null
        ImportWorker.enfileirar(this, arquivo.absolutePath, mime, aba, iaModoAtual)
        store.iaPendentes = store.iaPendentes + 1
        ListWidget.atualizar(this)
        val msgCarregar = if (agenda) "Carregando novas tarefas\u2026" else "Carregando novo item da lista\u2026"
        lblTransito.visibility = View.VISIBLE
        lblTransito.text = "\u23f3 " + msgCarregar
        Toast.makeText(this, msgCarregar, Toast.LENGTH_LONG).show()
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
            t.text = txt; t.setTextColor(0xFFB99BB4.toInt()); t.textSize = 12f
            t.setTypeface(t.typeface, android.graphics.Typeface.BOLD)
            t.setPadding(0, dp(8), 0, dp(2))
            return t
        }
        fun campo(): EditText {
            val e = EditText(this)
            e.setTextColor(0xFFFBEEF6.toInt())
            e.setBackgroundResource(R.drawable.field_bg)
            e.setPadding(dp(10), dp(8), dp(10), dp(8))
            e.maxLines = 1
            return e
        }

        box.addView(rotulo("Item"))
        val edItem = campo(); edItem.setText(cmd.item); box.addView(edItem)

        box.addView(rotulo("Valor (opcional)"))
        val edValor = campo()
        tecladoValor(edValor)
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
            cb.setTextColor(0xFFFBEEF6.toInt())
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
        tv.setTextColor(0xFFB99BB4.toInt())
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
                v.setBackgroundColor(0xFF2A1829.toInt())
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
        "Alta" -> Color.parseColor("#FF8FA3")
        "Baixa" -> Color.parseColor("#7EE8D2")
        else -> Color.parseColor("#F6C177")
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
