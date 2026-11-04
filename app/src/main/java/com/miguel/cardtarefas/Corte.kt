package com.miguel.cardtarefas

import java.util.Calendar

// Corte da fatura: decide em que mes (aba "AAAA-MM") entra uma compra lida da notificacao.
// Vale so' para lancamentos automaticos da notificacao.
object Corte {
    // feriados nacionais de data fixa (dia-mes)
    private val FERIADOS = setOf("1-1", "21-4", "1-5", "7-9", "12-10", "2-11", "15-11", "20-11", "25-12")

    // dia do corte no mes (m de 0 a 11). modo "fixo" = dia do mes; "util" = N-esimo dia util.
    fun diaCorte(y: Int, m: Int, modo: String, dia: Int): Int {
        val c = Calendar.getInstance(); c.clear(); c.set(y, m, 1)
        val ultimo = c.getActualMaximum(Calendar.DAY_OF_MONTH)
        if (modo != "util") return dia.coerceIn(1, ultimo)
        var n = 0
        for (d in 1..ultimo) {
            c.set(Calendar.DAY_OF_MONTH, d)
            val w = c.get(Calendar.DAY_OF_WEEK)
            if (w == Calendar.SATURDAY || w == Calendar.SUNDAY) continue
            if (FERIADOS.contains("$d-${m + 1}")) continue
            n++
            if (n == dia) return d
        }
        return ultimo
    }

    // aba "AAAA-MM" em que entra uma compra feita em y/m(0-11)/d, segundo o corte configurado.
    fun abaDaCompra(y: Int, m: Int, d: Int, modo: String, dia: Int): String {
        var t = y * 12 + m
        // o cartao "vira" no dia do corte: do corte em diante a compra ja vai para o mes seguinte
        if (modo.isNotEmpty() && d >= diaCorte(y, m, modo, dia)) t += 1
        return "%04d-%02d".format(t / 12, t % 12 + 1)
    }

    fun previa(modo: String, dia: Int): String {
        if (modo.isEmpty()) return "Sem corte: a compra entra no mês mais recente da lista."
        val c = Calendar.getInstance()
        val y = c.get(Calendar.YEAR); val m = c.get(Calendar.MONTH); val d = c.get(Calendar.DAY_OF_MONTH)
        val corte = diaCorte(y, m, modo, dia)
        val aba = abaDaCompra(y, m, d, modo, dia).split("-")
        return "Corte de %02d/%02d: dia %d.\nCompra de hoje (%02d/%02d) → mês %s/%s".format(m + 1, y % 100, corte, d, m + 1, aba[1], aba[0])
    }
}
