package com.miguel.cardtarefas

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Build
import android.util.Base64
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Processa em segundo plano uma midia (foto/imagem/audio): chama o Gemini,
// recebe os itens e lanca direto na lista do mes atual. Avisa por notificacao
// quando termina. O app pode estar fechado durante o processamento.
class ImportWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    override fun doWork(): Result {
        val ctx = applicationContext
        val caminho = inputData.getString("arquivo") ?: return Result.success()
        var mime = inputData.getString("mime") ?: "image/jpeg"
        val arquivo = File(caminho)
        val store = Store(ctx)

        try {
            if (!store.logado) { notificar(ctx, "Entre na sua conta para usar a IA."); return ok(arquivo) }
            val codigo = store.codigoIa
            if (codigo.isNullOrBlank()) { notificar(ctx, "Defina o codigo da IA no app e tente de novo."); return ok(arquivo) }

            // prepara a midia (imagem: reduz e recomprime; audio: cru)
            val dadosB64: String
            if (mime.startsWith("image")) {
                val jpg = prepararImagem(arquivo)
                dadosB64 = Base64.encodeToString(jpg, Base64.NO_WRAP)
                mime = "image/jpeg"
            } else {
                dadosB64 = Base64.encodeToString(arquivo.readBytes(), Base64.NO_WRAP)
            }

            val uid = store.uid!!
            val idToken = Api.idTokenValido(store)

            val enc = Api.lerGeminiEnc(idToken, uid)
                ?: run { notificar(ctx, "IA ainda nao configurada. Configure no app web ou do PC."); return ok(arquivo) }

            val chave = try {
                Cripto.decifrar(enc.first, enc.second, enc.third, codigo.trim())
            } catch (e: Throwable) {
                store.codigoIa = null
                notificar(ctx, "Codigo da IA incorreto. Abra o app e informe o codigo de novo.")
                return ok(arquivo)
            }

            val itens = try {
                Gemini.extrair(chave, mime, dadosB64)
            } catch (e: Throwable) {
                notificar(ctx, "Erro na IA: " + (e.message ?: "falha").take(120))
                return if (arquivo.exists()) { arquivo.delete(); Result.success() } else Result.success()
            }

            if (itens.isEmpty()) { notificar(ctx, "A IA nao encontrou itens na midia enviada."); return ok(arquivo) }

            val lista = lancar(idToken, uid, itens)
            arquivo.delete()
            notificar(ctx, "${itens.size} item(ns) adicionado(s) a lista \"$lista\" (${mesLabel(ymHoje())}).")
            return Result.success()
        } catch (e: Throwable) {
            notificar(ctx, "Nao consegui processar agora: " + (e.message ?: "erro").take(100))
            return Result.success()
        } finally {
            try { atualizarPendentes(ctx) } catch (_: Throwable) {}
            try { ListWidget.atualizar(ctx) } catch (_: Throwable) {}
        }
    }

    // Reconta quantas importacoes por IA ainda nao terminaram (exclui esta, que esta
    // rodando agora) e guarda para o widget mostrar o icone "em transito".
    private fun atualizarPendentes(ctx: Context) {
        val infos = androidx.work.WorkManager.getInstance(ctx).getWorkInfosByTag(TAG).get()
        val ativos = infos.count { !it.state.isFinished }
        Store(ctx).iaPendentes = (ativos - 1).coerceAtLeast(0)
    }

    private fun ok(arquivo: File): Result {
        try { if (arquivo.exists()) arquivo.delete() } catch (_: Throwable) {}
        return Result.success()
    }

    // --------------------------------------------------- lancar itens (= app web)
    private fun lancar(idToken: String, uid: String, itens: List<ItemIA>): String {
        val grupos = Api.listarGruposFull(idToken, uid)
        val listas = grupos.filter { it.tipo == "lista" }
        val lista = listas.firstOrNull()?.nome ?: "Acerto"
        val abaPedida = inputData.getString("aba")
        val ym = if (abaPedida != null && Regex("^\\d{4}-\\d{2}$").matches(abaPedida)) abaPedida else ymHoje()

        // garante que o mes atual existe nas abas do grupo
        val alvoMeta = grupos.firstOrNull { it.nome == lista }
        val temAba = alvoMeta?.abas?.any { it.id == ym } ?: false
        if (!temAba) {
            try {
                val raw = Api.lerGruposRaw(idToken, uid)
                val alvo = raw.firstOrNull { (it["nome"] as? String) == lista }
                if (alvo != null) {
                    val abas = ArrayList<HashMap<String, Any?>>()
                    val atuais = alvoMeta?.abas ?: emptyList()
                    for (a in atuais) { val m = HashMap<String, Any?>(); m["id"] = a.id; m["nome"] = mesLabel(a.id); abas.add(m) }
                    if (atuais.none { it.id == ym }) { val m = HashMap<String, Any?>(); m["id"] = ym; m["nome"] = mesLabel(ym); abas.add(m) }
                    abas.sortBy { it["id"] as String }
                    alvo["abas"] = abas
                    Api.salvarGrupos(idToken, uid, raw)
                }
            } catch (_: Throwable) {}
        }

        // ordem: continua a partir do menor valor atual do mes
        val ativas = Api.listarTarefasAtivas(idToken, uid)
        val menor = ativas.filter { it.grupo == lista && (it.aba ?: "") == ym && !it.concluida }
            .mapNotNull { it.ordem }.minOrNull() ?: 0.0
        var ordem = menor - 1.0

        for (r in itens) {
            var serie: String? = null; var inicioMes: String? = null; var fimMes: String? = null; var parcelas: Int? = null
            if (r.pTotal != null && r.pTotal > 1) {
                val atual = (r.pAtual ?: 1).coerceIn(1, r.pTotal)
                val inicio = ymAdd(ym, -(atual - 1))
                serie = Api.novoId(); inicioMes = inicio; parcelas = r.pTotal; fimMes = ymAdd(inicio, r.pTotal - 1)
            }
            val t = Tarefa(
                id = Api.novoId(), grupo = lista, aba = ym, descricao = r.descricao,
                concluida = false, criadaEm = nowIso(), atualizadoEm = Api.agoraMs(),
                excluida = false, valor = r.valor, recorrente = false,
                serie = serie, inicioMes = inicioMes, fimMes = fimMes, parcelas = parcelas, ordem = ordem)
            Api.gravarTarefa(idToken, uid, t)
            ordem -= 1.0
        }
        return lista
    }

    // --------------------------------------------------- imagem
    private fun prepararImagem(arquivo: File): ByteArray {
        val max = 1280
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(arquivo.absolutePath, bounds)
        var sample = 1
        val maior = maxOf(bounds.outWidth, bounds.outHeight)
        while (maior / sample > max * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = BitmapFactory.decodeFile(arquivo.absolutePath, opts)
            ?: return arquivo.readBytes()
        // corrige rotacao pelo EXIF
        try {
            val exif = ExifInterface(arquivo.absolutePath)
            val o = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            val g = when (o) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (g != 0f) { val mm = Matrix(); mm.postRotate(g); bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, mm, true) }
        } catch (_: Throwable) {}
        // escala final
        val m2 = maxOf(bmp.width, bmp.height)
        if (m2 > max) {
            val f = max.toFloat() / m2
            bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * f).toInt(), (bmp.height * f).toInt(), true)
        }
        val bos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 72, bos)
        return bos.toByteArray()
    }

    // --------------------------------------------------- helpers de mes
    private fun ymHoje(): String {
        val c = Calendar.getInstance()
        return "%04d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
    }
    private fun mesLabel(ym: String): String { val p = ym.split("-"); return if (p.size == 2) p[1] + "/" + p[0] else ym }
    private fun ymAdd(ym: String, n: Int): String { val p = ym.split("-"); val t = p[0].toInt() * 12 + (p[1].toInt() - 1) + n; return "%04d-%02d".format(t / 12, t % 12 + 1) }
    private fun nowIso(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())

    // --------------------------------------------------- notificacao
    private fun notificar(ctx: Context, texto: String) {
        try {
            garantirCanal(ctx)
            val abrir = Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pi = PendingIntent.getActivity(ctx, 0, abrir,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0))
            val n = NotificationCompat.Builder(ctx, CANAL)
                .setSmallIcon(R.drawable.ic_check_on)
                .setContentTitle("NestShare · IA")
                .setContentText(texto)
                .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            NotificationManagerCompat.from(ctx).notify((System.currentTimeMillis() % 100000).toInt(), n)
        } catch (_: Throwable) {}
    }

    companion object {
        const val TAG = "ia_import"
        private const val CANAL = "ia"

        fun garantirCanal(ctx: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (nm.getNotificationChannel(CANAL) == null) {
                    val c = NotificationChannel(CANAL, "Importacao por IA", NotificationManager.IMPORTANCE_DEFAULT)
                    c.description = "Avisos de itens extraidos por IA (foto, imagem e audio)"
                    nm.createNotificationChannel(c)
                }
            }
        }

        fun enfileirar(ctx: Context, arquivo: String, mime: String, aba: String? = null) {
            garantirCanal(ctx)
            val dados = Data.Builder().putString("arquivo", arquivo).putString("mime", mime)
            if (aba != null) dados.putString("aba", aba)
            val req = OneTimeWorkRequestBuilder<ImportWorker>()
                .addTag(TAG)
                .setInputData(dados.build())
                .build()
            WorkManager.getInstance(ctx).enqueue(req)
        }
    }
}
