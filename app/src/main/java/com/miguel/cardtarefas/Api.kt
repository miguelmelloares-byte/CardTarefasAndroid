package com.miguel.cardtarefas

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

// Uma tarefa/item como vive na nuvem (Firestore). Mesmos campos do app do PC e do web.
data class Tarefa(
    val id: String,
    val grupo: String = "",
    val topico: String = "",
    val descricao: String = "",
    val data: String? = null,
    val prioridade: String = "Media",
    val complexidade: String = "Media",
    val concluida: Boolean = false,
    val concluidaEm: String? = null,
    val criadaEm: String? = null,
    val atualizadoEm: Long = 0L,
    val excluida: Boolean = false,
    val valor: Double? = null
)

class ApiException(message: String) : Exception(message)

object Api {

    fun agoraMs(): Long = System.currentTimeMillis()

    private fun agoraIso(): String {
        val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        return f.format(Date())
    }

    fun novoId(): String = UUID.randomUUID().toString().replace("-", "")

    // ---------------------------------------------------------------- HTTP
    private fun http(
        url: String,
        method: String = "GET",
        body: String? = null,
        bearer: String? = null,
        form: Boolean = false
    ): JSONObject {
        val con = URL(url).openConnection() as HttpURLConnection
        // HttpURLConnection nao aceita o metodo PATCH; usamos POST + cabecalho de
        // override, que as APIs do Google (Firestore) reconhecem.
        if (method == "PATCH") {
            con.requestMethod = "POST"
            con.setRequestProperty("X-HTTP-Method-Override", "PATCH")
        } else {
            con.requestMethod = method
        }
        con.connectTimeout = 20000
        con.readTimeout = 25000
        if (bearer != null) con.setRequestProperty("Authorization", "Bearer $bearer")
        if (body != null) {
            con.doOutput = true
            con.setRequestProperty(
                "Content-Type",
                if (form) "application/x-www-form-urlencoded" else "application/json"
            )
            OutputStreamWriter(con.outputStream, Charsets.UTF_8).use { it.write(body) }
        }
        val code = con.responseCode
        val stream = if (code in 200..299) con.inputStream else con.errorStream
        val text = stream?.let {
            BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> r.readText() }
        } ?: ""
        con.disconnect()
        if (code !in 200..299) {
            var msg = text
            try {
                val err = JSONObject(text).optJSONObject("error")
                if (err != null) msg = err.optString("message", text)
            } catch (_: Exception) { }
            throw ApiException(msg.ifEmpty { "HTTP $code" })
        }
        return if (text.isEmpty()) JSONObject() else JSONObject(text)
    }

    // ---------------------------------------------------------------- Auth
    // Retorna Triple(idToken, refreshToken, uid)
    fun login(email: String, senha: String): Triple<String, String, String> {
        val url = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${Config.API_KEY}"
        val body = JSONObject()
            .put("email", email)
            .put("password", senha)
            .put("returnSecureToken", true)
            .toString()
        val r = http(url, "POST", body)
        return Triple(
            r.getString("idToken"),
            r.getString("refreshToken"),
            r.getString("localId")
        )
    }

    // Retorna Pair(idToken, refreshToken) — troca o refresh_token por um id_token fresco.
    fun refresh(refreshToken: String): Pair<String, String> {
        val url = "https://securetoken.googleapis.com/v1/token?key=${Config.API_KEY}"
        val body = "grant_type=refresh_token&refresh_token=" +
            URLEncoder.encode(refreshToken, "UTF-8")
        val r = http(url, "POST", body, form = true)
        return Pair(r.getString("id_token"), r.getString("refresh_token"))
    }

    // Garante um idToken valido a partir do que esta guardado; atualiza o refresh_token
    // se o servidor devolver um novo. Lanca ApiException se nao houver login.
    fun idTokenValido(store: Store): String {
        val rt = store.refreshToken ?: throw ApiException("Nao conectado")
        val (idToken, novoRt) = refresh(rt)
        if (novoRt.isNotEmpty() && novoRt != rt) store.refreshToken = novoRt
        return idToken
    }

    // ---------------------------------------------------------- Firestore
    private fun encode(v: Any?): JSONObject {
        val o = JSONObject()
        when (v) {
            null -> o.put("nullValue", JSONObject.NULL)
            is Boolean -> o.put("booleanValue", v)
            is Int -> o.put("integerValue", v.toString())
            is Long -> o.put("integerValue", v.toString())
            is Double -> o.put("doubleValue", v)
            is Map<*, *> -> {
                val f = JSONObject()
                for ((k, x) in v) f.put(k.toString(), encode(x))
                o.put("mapValue", JSONObject().put("fields", f))
            }
            is List<*> -> {
                val arr = JSONArray()
                for (x in v) arr.put(encode(x))
                o.put("arrayValue", JSONObject().put("values", arr))
            }
            else -> o.put("stringValue", v.toString())
        }
        return o
    }

    private fun decode(v: JSONObject): Any? {
        return when {
            v.has("booleanValue") -> v.getBoolean("booleanValue")
            v.has("integerValue") -> v.getString("integerValue").toLongOrNull() ?: 0L
            v.has("doubleValue") -> v.getDouble("doubleValue")
            v.has("nullValue") -> null
            v.has("stringValue") -> v.getString("stringValue")
            v.has("timestampValue") -> v.getString("timestampValue")
            v.has("arrayValue") -> {
                val out = ArrayList<Any?>()
                val values = v.getJSONObject("arrayValue").optJSONArray("values")
                if (values != null) for (i in 0 until values.length())
                    out.add(decode(values.getJSONObject(i)))
                out
            }
            v.has("mapValue") -> {
                val out = HashMap<String, Any?>()
                val f = v.getJSONObject("mapValue").optJSONObject("fields")
                if (f != null) for (k in f.keys()) out[k] = decode(f.getJSONObject(k))
                out
            }
            else -> null
        }
    }

    private fun fieldsFrom(doc: JSONObject): JSONObject {
        val fields = JSONObject()
        val f = doc.optJSONObject("fields") ?: return fields
        for (k in f.keys()) fields.put(k, decode(f.getJSONObject(k)))
        return fields
    }

    private fun tarefaFrom(doc: JSONObject): Tarefa {
        val name = doc.optString("name", "")
        val id = if (name.contains("/")) name.substringAfterLast("/") else name
        val f = fieldsFrom(doc)
        return Tarefa(
            id = id,
            grupo = (f.opt("grupo") as? String) ?: "",
            topico = (f.opt("topico") as? String) ?: "",
            descricao = (f.opt("descricao") as? String) ?: "",
            data = f.opt("data") as? String,
            prioridade = (f.opt("prioridade") as? String) ?: "Media",
            complexidade = (f.opt("complexidade") as? String) ?: "Media",
            concluida = (f.opt("concluida") as? Boolean) ?: false,
            concluidaEm = f.opt("concluida_em") as? String,
            criadaEm = f.opt("criada_em") as? String,
            atualizadoEm = when (val a = f.opt("atualizado_em")) {
                is Long -> a
                is Int -> a.toLong()
                is Double -> a.toLong()
                else -> 0L
            },
            excluida = (f.opt("excluida") as? Boolean) ?: false,
            valor = when (val v = f.opt("valor")) {
                is Double -> v
                is Long -> v.toDouble()
                is Int -> v.toDouble()
                else -> null
            }
        )
    }

    fun listarTarefas(idToken: String, uid: String): List<Tarefa> {
        val out = ArrayList<Tarefa>()
        var page: String? = null
        do {
            var url = "${Config.firestoreBase()}/users/$uid/tarefas?pageSize=300"
            if (page != null) url += "&pageToken=" + URLEncoder.encode(page, "UTF-8")
            val r = http(url, "GET", bearer = idToken)
            val docs = r.optJSONArray("documents")
            if (docs != null) for (i in 0 until docs.length())
                out.add(tarefaFrom(docs.getJSONObject(i)))
            page = r.optString("nextPageToken", "").ifEmpty { null }
        } while (page != null)
        return out
    }

    // Grava (cria ou substitui) uma tarefa inteira.
    fun gravarTarefa(idToken: String, uid: String, t: Tarefa) {
        val url = "${Config.firestoreBase()}/users/$uid/tarefas/${t.id}"
        val campos = linkedMapOf<String, Any?>(
            "grupo" to t.grupo,
            "topico" to t.topico,
            "descricao" to t.descricao,
            "data" to t.data,
            "prioridade" to t.prioridade,
            "complexidade" to t.complexidade,
            "concluida" to t.concluida,
            "concluida_em" to t.concluidaEm,
            "criada_em" to t.criadaEm,
            "atualizado_em" to t.atualizadoEm,
            "excluida" to t.excluida,
            "valor" to t.valor
        )
        val fields = JSONObject()
        for ((k, v) in campos) fields.put(k, encode(v))
        val body = JSONObject().put("fields", fields).toString()
        http(url, "PATCH", body, bearer = idToken)
    }

    // Atualiza apenas alguns campos (nao apaga o resto) usando updateMask.
    private fun patchCampos(idToken: String, uid: String, id: String, campos: Map<String, Any?>) {
        val sb = StringBuilder("${Config.firestoreBase()}/users/$uid/tarefas/$id?")
        val partes = campos.keys.map { "updateMask.fieldPaths=" + URLEncoder.encode(it, "UTF-8") }
        sb.append(partes.joinToString("&"))
        val fields = JSONObject()
        for ((k, v) in campos) fields.put(k, encode(v))
        val body = JSONObject().put("fields", fields).toString()
        http(sb.toString(), "PATCH", body, bearer = idToken)
    }

    fun marcar(idToken: String, uid: String, id: String, concluida: Boolean) {
        patchCampos(
            idToken, uid, id, linkedMapOf(
                "concluida" to concluida,
                "concluida_em" to if (concluida) agoraIso() else null,
                "atualizado_em" to agoraMs()
            )
        )
    }

    fun excluir(idToken: String, uid: String, id: String) {
        patchCampos(
            idToken, uid, id, linkedMapOf(
                "excluida" to true,
                "atualizado_em" to agoraMs()
            )
        )
    }

    // Cria um item novo no grupo indicado (valor opcional).
    fun criarItem(idToken: String, uid: String, grupo: String, descricao: String,
                  valor: Double? = null): Tarefa {
        val t = Tarefa(
            id = novoId(),
            grupo = grupo,
            descricao = descricao,
            concluida = false,
            criadaEm = agoraIso(),
            atualizadoEm = agoraMs(),
            excluida = false,
            valor = valor
        )
        gravarTarefa(idToken, uid, t)
        return t
    }

    // Atualiza apenas o valor de um item.
    fun atualizarValor(idToken: String, uid: String, id: String, valor: Double?) {
        patchCampos(idToken, uid, id, linkedMapOf(
            "valor" to valor,
            "atualizado_em" to agoraMs()
        ))
    }

    // Grupos definidos em meta/estado, com seu tipo ("lista" ou "tarefas").
    fun listarGruposComTipo(idToken: String, uid: String): List<Pair<String, String>> {
        return try {
            val url = "${Config.firestoreBase()}/users/$uid/meta/estado"
            val r = http(url, "GET", bearer = idToken)
            val f = fieldsFrom(r)
            val grupos = f.opt("grupos")
            val out = ArrayList<Pair<String, String>>()
            if (grupos is List<*>) {
                for (g in grupos) if (g is Map<*, *>) {
                    val nome = g["nome"]
                    val tipo = if (g["tipo"] == "lista") "lista" else "tarefas"
                    if (nome is String && nome.isNotEmpty()) out.add(Pair(nome, tipo))
                }
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    // Apenas os nomes dos grupos do tipo Lista (os unicos que o widget pode mostrar).
    fun listarGruposLista(idToken: String, uid: String): List<String> {
        return listarGruposComTipo(idToken, uid)
            .filter { it.second == "lista" }
            .map { it.first }
    }

    // Hash da senha das tarefas guardado em meta/estado ("" ou null = sem protecao).
    fun lerTarefasHash(idToken: String, uid: String): String? {
        return try {
            val url = "${Config.firestoreBase()}/users/$uid/meta/estado"
            val r = http(url, "GET", bearer = idToken)
            val f = fieldsFrom(r)
            (f.opt("tarefas_hash") as? String)?.ifEmpty { null }
        } catch (_: Exception) {
            null
        }
    }
}
