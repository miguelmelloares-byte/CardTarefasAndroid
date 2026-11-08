package com.miguel.cardtarefas

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Wishlist -> agenda: quando o CARTAO VIRA para a fatura que sera paga no mes previsto do item
// (ex.: item de 01/2027 -> no corte de 12/2026, p.ex. 5o dia util), cria UMA vez o compromisso
// "Eba..." na agenda; o aviso de compromisso novo da agenda e' o push no celular.
// Sem corte configurado, vale o proprio mes previsto.
object WishAgenda {
    // aba (mes de pagamento "AAAA-MM") em que cai uma compra feita hoje
    fun abaAberta(store: Store): String {
        val c = Calendar.getInstance()
        return Corte.abaDaCompra(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH), store.corteModo, store.corteDia)
    }

    // ultimo dia para comprar nessa fatura: vespera do corte do mes previsto (sem corte: fim do mes previsto)
    private fun dataLimite(ym: String, store: Store): String {
        val p = ym.split("-"); val y = p[0].toInt(); val m = p[1].toInt() - 1
        val c = Calendar.getInstance(); c.clear(); c.set(y, m, 1)
        if (store.corteModo.isEmpty()) { c.set(Calendar.DAY_OF_MONTH, c.getActualMaximum(Calendar.DAY_OF_MONTH)) }
        else { c.set(Calendar.DAY_OF_MONTH, Corte.diaCorte(y, m, store.corteModo, store.corteDia)); c.add(Calendar.DAY_OF_MONTH, -1) }
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.time)
    }

    // devolve quantos compromissos criou
    fun verificar(store: Store, tok: String, uid: String, desejos: List<Desejo>): Int {
        val aberta = abaAberta(store)
        val pend = desejos.filter { !it.excluida && !it.comprado && !it.agendaCriada && it.previsao.isNotEmpty() && it.previsao <= aberta }
        for (d in pend) {
            val p = d.previsao.split("-")
            Api.gravarCompromisso(tok, uid, Compromisso(id = "wl_" + d.id,
                descricao = "Eba! O cartão virou: já dá para comprar o \"${d.nome}\" da wishlist (fatura paga em ${p[1]}/${p[0].takeLast(2)})",
                data = dataLimite(d.previsao, store), hora = "",
                corpo = (d.descricao + "\n" + d.parcelas + "x R$ " + fmtValor(d.parcela)).trim(),
                concluida = false, criadaEm = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date()),
                atualizadoEm = Api.agoraMs(), excluida = false))
            Api.patchDesejo(tok, uid, d.id, linkedMapOf("agenda_criada" to true))
        }
        return pend.size
    }
}
