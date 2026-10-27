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
    val pTotal: Int?,
    val data: String = ""
)

// Chama a API do Gemini mandando a midia crua (imagem ou audio) e recebe os itens.
// O app e' so' um "cano": nao faz OCR nem transcricao, tudo acontece no Gemini.
object Gemini {

    const val MODELO = "gemini-3.5-flash-lite"

    private fun prompt(hoje: String): String =
        "Hoje e $hoje (formato AAAA-MM-DD). " +
        "Voce extrai itens de compra/despesa de uma imagem (print de fatura, extrato, notificacao de banco ou planilha) ou de um audio. " +
        "Para cada item retorne uma descricao curta, o valor, a data da despesa e, se for compra parcelada, o numero da parcela atual e o total de parcelas. " +
        "Responda APENAS um array JSON, sem nada fora dele, no formato: " +
        "[{\"descricao\":\"texto\",\"valor\":numero,\"data\":\"AAAA-MM-DD\",\"parcela_atual\":numero,\"parcela_total\":numero}]. " +
        "valor = numero com ponto decimal (ex.: 12.50), referente a UMA parcela (valor mensal), ou null se nao houver. " +
        "data: a data da despesa. Resolva expressoes relativas a partir de hoje ($hoje): hoje, ontem, anteontem, dia N, dias da semana. " +
        "Se NAO houver data mencionada, use data vazia (\"\"). NUNCA invente uma data. " +
        "Se NAO for parcelado, use parcela_atual=1 e parcela_total=1. " +
        "Em faturas/extratos, textos como \"PARC 03/10\", \"3/10\" ou \"PARCELA 3 DE 10\" indicam parcela 3 de 10. " +
        "Nao invente itens."

    data class ItemAgendaIA(
    val descricao: String,
    val data: String,
    val hora: String
)

class GeminiException(msg: String) : Exception(msg)

    // mimeType ex.: "image/jpeg" ou "audio/aac". dadosB64 = base64 da midia.
    // Faz a chamada ao Gemini com os `parts` dados e devolve o texto (array JSON) cru.
    private fun responder(apiKey: String, parts: JSONArray): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$MODELO:generateContent?key=" +
            URLEncoder.encode(apiKey, "UTF-8")
        fun gerConfig(modo: Int): JSONObject {
            val gc = JSONObject().put("responseMimeType", "application/json").put("temperature", 0).put("maxOutputTokens", 2048)
            when (modo) {
                0 -> gc.put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
                1 -> gc.put("thinkingConfig", JSONObject().put("thinkingLevel", "low"))
            }
            return gc
        }
        fun corpo(modo: Int): String = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", parts)))
            .put("generationConfig", gerConfig(modo)).toString()
        fun requisitar(body: String): Pair<Int, String> {
            val con = URL(url).openConnection() as HttpURLConnection
            con.requestMethod = "POST"; con.connectTimeout = 15000; con.readTimeout = 45000; con.doOutput = true
            con.setRequestProperty("Content-Type", "application/json")
            OutputStreamWriter(con.outputStream, Charsets.UTF_8).use { it.write(body) }
            val code = con.responseCode
            val stream = if (code in 200..299) con.inputStream else con.errorStream
            val txt = stream?.let { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> r.readText() } } ?: ""
            con.disconnect()
            return Pair(code, txt)
        }
        var resposta = ""; var ok = false; var ultimo = ""; var ultimoCode = 0
        for (modo in 0..2) {
            val body = corpo(modo)
            for (tentativa in 0 until 2) {
                if (tentativa > 0) Thread.sleep(600L)
                val (code, txt) = requisitar(body)
                if (code in 200..299) { resposta = txt; ok = true; break }
                ultimo = txt; ultimoCode = code
                if (code == 400) break
                if (!(code == 503 || code == 429 || code == 500)) break
            }
            if (ok) break
            if (ultimoCode != 400) break
        }
        if (!ok) {
            if (ultimoCode == 503) throw GeminiException("O modelo esta sobrecarregado. Tente de novo em instantes.")
            var msg = ultimo
            try { JSONObject(ultimo).optJSONObject("error")?.let { msg = it.optString("message", ultimo) } } catch (_: Exception) {}
            throw GeminiException("$ultimoCode ${msg.take(160)}")
        }
        var txt = ""
        try {
            val cand = JSONObject(resposta).optJSONArray("candidates")?.optJSONObject(0)
            val ps = cand?.optJSONObject("content")?.optJSONArray("parts")
            if (ps != null) { val sb = StringBuilder(); for (i in 0 until ps.length()) sb.append(ps.getJSONObject(i).optString("text", "")); txt = sb.toString() }
        } catch (_: Exception) {}
        if (txt.isBlank()) throw GeminiException("Resposta vazia da IA.")
        return txt
    }

    private fun partesMidia(prompt: String, mimeType: String, dadosB64: String): JSONArray {
        val parts = JSONArray()
        parts.put(JSONObject().put("text", prompt))
        parts.put(JSONObject().put("inline_data", JSONObject().put("mime_type", mimeType).put("data", dadosB64)))
        return parts
    }

    // mimeType ex.: "image/jpeg" ou "audio/aac". Extrai itens de lista (gasto).
    fun extrair(apiKey: String, mimeType: String, dadosB64: String, hoje: String): List<ItemIA> {
        val arr = parseArray(responder(apiKey, partesMidia(prompt(hoje), mimeType, dadosB64)))
        val out = ArrayList<ItemIA>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val desc = (o.opt("descricao") ?: o.opt("item") ?: o.opt("nome") ?: "").toString().trim()
            if (desc.isEmpty()) continue
            out.add(ItemIA(desc, lerValor(o.opt("valor")), pn(o.opt("parcela_atual")), pn(o.opt("parcela_total")), dataIso(o.opt("data"))))
        }
        return out
    }

    private fun promptAgenda(hoje: String): String =
        "Hoje e $hoje (formato AAAA-MM-DD). Voce extrai COMPROMISSOS/afazeres de uma imagem, audio ou texto. " +
        "Para cada compromisso retorne descricao curta, data e hora. Responda APENAS um array JSON, nada fora dele, no formato: " +
        "[{\"descricao\":\"texto\",\"data\":\"AAAA-MM-DD\",\"hora\":\"HH:MM\"}]. " +
        "data: resolva expressoes relativas a partir de hoje ($hoje): hoje, amanha, depois de amanha, dias da semana (a proxima ocorrencia), dia N (o proximo dia N do mes), semana que vem. " +
        "Se NAO houver data clara, use data vazia. hora no formato HH:MM se for mencionada, senao vazia. Nao invente compromissos."

    // Extrai compromissos (descricao + data + hora). `hoje` = AAAA-MM-DD para datas relativas.
    fun extrairAgenda(apiKey: String, mimeType: String, dadosB64: String, hoje: String): List<ItemAgendaIA> {
        val arr = parseArray(responder(apiKey, partesMidia(promptAgenda(hoje), mimeType, dadosB64)))
        val out = ArrayList<ItemAgendaIA>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val desc = (o.opt("descricao") ?: o.opt("item") ?: o.opt("nome") ?: "").toString().trim()
            if (desc.isEmpty()) continue
            out.add(ItemAgendaIA(desc, dataIso(o.opt("data")), horaDe(o.opt("hora"))))
        }
        return out
    }

    private fun dataIso(v: Any?): String { val s = (v?.toString() ?: "").trim(); return if (Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(s)) s else "" }
    private fun horaDe(v: Any?): String { val s = (v?.toString() ?: "").trim(); return if (Regex("^\\d{1,2}:\\d{2}$").matches(s)) s else "" }

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
