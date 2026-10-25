package com.miguel.cardtarefas

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// Um item extraido pela IA.
data class ItemIA(
    val descricao: String,
    val valor: Double?,
    val pAtual: Int?,
    val pTotal: Int?
)

// Chama a API do Gemini mandando a midia crua (imagem ou audio) e recebe os itens.
// O app e' so' um "cano": nao faz OCR nem transcricao, tudo acontece no Gemini.
object Gemini {

    const val MODELO = "gemini-3.8-flash"

    private val PROMPT =
        "Voce extrai itens de compra/despesa de uma imagem (print de fatura, extrato, notificacao de banco ou planilha) ou de um audio. " +
        "Para cada item retorne uma descricao curta, o valor e, se for compra parcelada, o numero da parcela atual e o total de parcelas. " +
        "Responda APENAS um array JSON, sem nada fora dele, no formato: " +
        "[{\"descricao\":\"texto\",\"valor\":numero,\"parcela_atual\":numero,\"parcela_total\":numero}]. " +
        "valor = numero com ponto decimal (ex.: 12.50), referente a UMA parcela (valor mensal), ou null se nao houver. " +
        "Se NAO for parcelado, use parcela_atual=1 e parcela_total=1. " +
        "Em faturas/extratos, textos como \"PARC 03/10\", \"3/10\" ou \"PARCELA 3 DE 10\" indicam parcela 3 de 10. " +
        "Nao invente itens."

    class GeminiException(msg: String) : Exception(msg)

    // mimeType ex.: "image/jpeg" ou "audio/aac". dadosB64 = base64 da midia.
    fun extrair(apiKey: String, mimeType: String, dadosB64: String): List<ItemIA> {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$MODELO:generateContent?key=" +
            URLEncoder.encode(apiKey, "UTF-8")
        val parts = JSONArray()
        parts.put(JSONObject().put("text", PROMPT))
        parts.put(JSONObject().put("inline_data",
            JSONObject().put("mime_type", mimeType).put("data", dadosB64)))

        // generationConfig: sem "thinking" (muito mais rapido para extrair JSON).
        // modo 0 = thinkingBudget 0 (Gemini 2.x); 1 = thinkingLevel low (Gemini 3.x);
        // 2 = sem config de thinking (fallback). Tentamos nessa ordem e, se o modelo
        // recusar o parametro (400 citando thinking), passamos para o proximo.
        fun gerConfig(modo: Int): JSONObject {
            val gc = JSONObject()
                .put("responseMimeType", "application/json")
                .put("temperature", 0)
                .put("maxOutputTokens", 2048)
            when (modo) {
                0 -> gc.put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
                1 -> gc.put("thinkingConfig", JSONObject().put("thinkingLevel", "low"))
            }
            return gc
        }

        fun corpo(modo: Int): String = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", parts)))
            .put("generationConfig", gerConfig(modo))
            .toString()

        // uma requisicao HTTP; devolve Pair(codigo, textoResposta).
        fun requisitar(body: String): Pair<Int, String> {
            val con = URL(url).openConnection() as HttpURLConnection
            con.requestMethod = "POST"
            con.connectTimeout = 15000
            con.readTimeout = 45000
            con.doOutput = true
            con.setRequestProperty("Content-Type", "application/json")
            OutputStreamWriter(con.outputStream, Charsets.UTF_8).use { it.write(body) }
            val code = con.responseCode
            val stream = if (code in 200..299) con.inputStream else con.errorStream
            val txt = stream?.let { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> r.readText() } } ?: ""
            con.disconnect()
            return Pair(code, txt)
        }

        var resposta = ""
        var ok = false
        var ultimo = ""
        var ultimoCode = 0
        for (modo in 0..2) {
            val body = corpo(modo)
            var feito = false
            for (tentativa in 0 until 2) {
                if (tentativa > 0) Thread.sleep(600L)
                val (code, txt) = requisitar(body)
                if (code in 200..299) { resposta = txt; ok = true; feito = true; break }
                ultimo = txt; ultimoCode = code
                // 400 citando thinking -> tenta o proximo modo de config
                if (code == 400 && (txt.contains("thinking", true) || txt.contains("thinkingBudget", true) || txt.contains("thinkingLevel", true))) { feito = true; break }
                // sobrecarga -> tenta de novo rapido; outros erros -> desiste
                if (!(code == 503 || code == 429 || code == 500)) { feito = true; break }
            }
            if (ok) break
            // so' avanca para o proximo modo se foi erro de thinking; senao para
            if (!(ultimoCode == 400 && (ultimo.contains("thinking", true) || ultimo.contains("thinkingBudget", true) || ultimo.contains("thinkingLevel", true)))) break
        }

        if (!ok) {
            if (ultimoCode == 503) throw GeminiException("O modelo esta sobrecarregado. Tente de novo em instantes.")
            var msg = ultimo
            try { JSONObject(ultimo).optJSONObject("error")?.let { msg = it.optString("message", ultimo) } } catch (_: Exception) {}
            throw GeminiException("$ultimoCode ${msg.take(160)}")
        }

        // extrai o texto da resposta
        var txt = ""
        try {
            val cand = JSONObject(resposta).optJSONArray("candidates")?.optJSONObject(0)
            val ps = cand?.optJSONObject("content")?.optJSONArray("parts")
            if (ps != null) { val sb = StringBuilder(); for (i in 0 until ps.length()) sb.append(ps.getJSONObject(i).optString("text", "")); txt = sb.toString() }
        } catch (_: Exception) {}
        if (txt.isBlank()) throw GeminiException("Resposta vazia da IA.")

        val arr = parseArray(txt)
        val out = ArrayList<ItemIA>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val desc = (o.opt("descricao") ?: o.opt("item") ?: o.opt("nome") ?: "").toString().trim()
            if (desc.isEmpty()) continue
            out.add(ItemIA(desc, lerValor(o.opt("valor")), pn(o.opt("parcela_atual")), pn(o.opt("parcela_total"))))
        }
        return out
    }

    private fun parseArray(texto: String): JSONArray {
        val t = texto.trim()
        try { return JSONArray(t) } catch (_: Exception) {}
        val limpo = t.replace("```json", "").replace("```", "").trim()
        try { return JSONArray(limpo) } catch (_: Exception) {}
        // talvez venha {itens:[...]} ou {items:[...]}
        try {
            val o = JSONObject(limpo)
            o.optJSONArray("itens")?.let { return it }
            o.optJSONArray("items")?.let { return it }
        } catch (_: Exception) {}
        return JSONArray()
    }

    private fun lerValor(v: Any?): Double? = when (v) {
        is Double -> v
        is Int -> v.toDouble()
        is Long -> v.toDouble()
        is String -> parseValor(v)
        else -> null
    }

    private fun pn(v: Any?): Int? {
        val n = when (v) { is Int -> v; is Long -> v.toInt(); is Double -> v.toInt(); is String -> v.trim().toIntOrNull(); else -> null }
        return if (n != null && n > 0) n else null
    }
}
