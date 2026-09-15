package com.miguel.cardtarefas

// Configuracao publica do projeto Firebase (as mesmas chaves do app web / widget do PC).
// A apiKey do Firebase e' publica por design; a seguranca vem das regras do Firestore
// (cada usuario so' acessa users/{seu-uid}).
object Config {
    const val API_KEY = "AIzaSyBvc6s5BJKR2sMNUH7f0yhyxlbKjPj_Prg"
    const val PROJECT = "card-tarefas"

    fun firestoreBase(): String =
        "https://firestore.googleapis.com/v1/projects/$PROJECT/databases/(default)/documents"
}
