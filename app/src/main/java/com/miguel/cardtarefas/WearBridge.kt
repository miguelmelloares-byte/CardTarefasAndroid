package com.miguel.cardtarefas

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

// Publica o login (token) na Data Layer para o app do relogio herdar,
// sem precisar digitar nada no Galaxy Watch. Se deslogado, publica vazio.
object WearBridge {
    fun publicar(context: Context) {
        try {
            val store = Store(context)
            val req = PutDataMapRequest.create("/login")
            req.dataMap.putString("refresh_token", if (store.logado) store.refreshToken ?: "" else "")
            req.dataMap.putString("uid", if (store.logado) store.uid ?: "" else "")
            req.dataMap.putString("email", store.email ?: "")
            val pdr = req.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context.applicationContext).putDataItem(pdr)
        } catch (_: Throwable) {
        }
    }
}
