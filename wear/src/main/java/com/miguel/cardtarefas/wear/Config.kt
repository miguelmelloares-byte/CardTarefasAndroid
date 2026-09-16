package com.miguel.cardtarefas.wear

// Configuracao publica do projeto Firebase (as mesmas chaves do app web / widget do PC).
// A apiKey do Firebase e' publica por design; a seguranca vem das regras do Firestore
// (cada usuario so' acessa users/{seu-uid}).
object Config {
    const val API_KEY = "AIzaSyBvc6s5BJKR2sMNUH7f0yhyxlbKjPj_Prg"
    const val PROJECT = "card-tarefas"

    fun firestoreBase(): String =
        "https://firestore.googleapis.com/v1/projects/$PROJECT/databases/(default)/documents"
}

// formata numero estilo brasileiro (1.234,56)
fun fmtValor(v: Double): String {
    val neg = v < 0
    val s = String.format(java.util.Locale.US, "%.2f", kotlin.math.abs(v))
    val parts = s.split(".")
    val inteiro = parts[0]
    val dec = parts.getOrElse(1) { "00" }
    val agr = inteiro.reversed().chunked(3).joinToString(".").reversed()
    return (if (neg) "-" else "") + agr + "," + dec
}
