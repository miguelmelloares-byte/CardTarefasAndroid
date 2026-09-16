package com.miguel.cardtarefas

import android.content.Context

// Ponte para o relogio (Wear OS) - PAUSADA por enquanto.
// Vira um no-op para nao exigir a dependencia do play-services no app.
// Quando retomarmos o Wear, esta classe volta a publicar o login na Data Layer.
object WearBridge {
    fun publicar(context: Context) {
        // no-op enquanto o modulo do relogio estiver pausado
    }
}
