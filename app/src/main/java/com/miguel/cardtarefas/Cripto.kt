package com.miguel.cardtarefas

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// Decifra a chave do Gemini guardada no banco (meta/ia.gemini_enc).
// Compativel com o app web: AES-GCM 256 + PBKDF2-HMAC-SHA256 (120000 iteracoes).
// O codigo local (ex.: "murilo") nunca sai do aparelho e completa a decifragem.
object Cripto {

    private const val ITER = 120000
    private const val KEY_BYTES = 32   // 256 bits

    private fun b64(s: String): ByteArray = Base64.decode(s, Base64.DEFAULT)

    // PBKDF2-HMAC-SHA256 implementado na mao (serve em qualquer versao do Android).
    private fun pbkdf2(senha: ByteArray, salt: ByteArray, iter: Int, dkLen: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(senha, "HmacSHA256"))
        val hLen = mac.macLength
        val blocos = (dkLen + hLen - 1) / hLen
        val saida = ByteArray(dkLen)
        var offset = 0
        for (i in 1..blocos) {
            // INT_32_BE(i)
            val bloco = ByteArray(salt.size + 4)
            System.arraycopy(salt, 0, bloco, 0, salt.size)
            bloco[salt.size] = (i ushr 24).toByte()
            bloco[salt.size + 1] = (i ushr 16).toByte()
            bloco[salt.size + 2] = (i ushr 8).toByte()
            bloco[salt.size + 3] = i.toByte()
            var u = mac.doFinal(bloco)
            val t = u.copyOf()
            for (j in 1 until iter) {
                u = mac.doFinal(u)
                for (k in t.indices) t[k] = (t[k].toInt() xor u[k].toInt()).toByte()
            }
            val copiar = minOf(hLen, dkLen - offset)
            System.arraycopy(t, 0, saida, offset, copiar)
            offset += copiar
        }
        return saida
    }

    // Recebe o mapa {salt, iv, ct} (base64) e o codigo; devolve o texto claro (a chave).
    // Lanca excecao se o codigo estiver errado (GCM falha a verificacao).
    fun decifrar(salt: String, iv: String, ct: String, codigo: String): String {
        val keyBytes = pbkdf2(codigo.toByteArray(Charsets.UTF_8), b64(salt), ITER, KEY_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, b64(iv)))
        val claro = cipher.doFinal(b64(ct))
        return String(claro, Charsets.UTF_8)
    }
}
