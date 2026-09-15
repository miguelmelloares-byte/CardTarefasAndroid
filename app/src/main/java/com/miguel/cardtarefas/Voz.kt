package com.miguel.cardtarefas

// Resultado da interpretacao de um comando de voz.
data class ComandoVoz(val item: String, val valor: Double?, val grupo: String?)

// Interpreta uma frase ditada em {item, valor, grupo}.
// PECA ISOLADA de proposito: para trocar por IA (Gemini) no futuro, basta
// substituir esta funcao mantendo a mesma entrada (texto + grupos) e saida.
// Ex.: "adicionar leite com valor 10 reais na lista Mercado"
//   -> ComandoVoz(item="leite", valor=10.0, grupo="Mercado")
fun interpretarComando(textoOriginal: String, grupos: List<String>): ComandoVoz {
    var texto = " ${textoOriginal.trim()} "

    // 1) LISTA: procura um nome de grupo conhecido no texto
    var grupo: String? = null
    for (g in grupos) {
        if (Regex("(?i)\\b${Regex.escape(g)}\\b").containsMatchIn(texto)) {
            grupo = g
            texto = texto.replace(
                Regex("(?i)\\b(na\\s+|a\\s+|para\\s+a\\s+|pra\\s+|em\\s+)?lista\\s+${Regex.escape(g)}\\b"), " ")
            texto = texto.replace(Regex("(?i)\\b${Regex.escape(g)}\\b"), " ")
            break
        }
    }

    // 2) VALOR: um numero que tenha uma "pista" (valor / reais / R$ / menos / sinal -)
    var valor: Double? = null
    val re = Regex("(?i)(menos\\s+|-\\s*)?(valor\\s+de\\s+|valor\\s+|r\\$\\s*)?(\\d+(?:[.,]\\d+)?)\\s*(reais|real)?")
    for (m in re.findAll(texto)) {
        val neg = m.groupValues[1].isNotBlank()
        val cueAntes = m.groupValues[2].isNotBlank()
        val cueDepois = m.groupValues[4].isNotBlank()
        if (neg || cueAntes || cueDepois) {
            val n = m.groupValues[3].replace(".", "").replace(",", ".").toDoubleOrNull()
            if (n != null) {
                valor = if (neg) -n else n
                texto = texto.replace(m.value, " ")
                break
            }
        }
    }

    // 3) ITEM: tira verbos de comando e palavras de ligacao que sobraram
    var item = texto
    item = item.replace(
        Regex("(?i)^\\s*(por favor\\s+)?(adicionar|adiciona|acrescentar|acrescenta|colocar|coloca|poe|põe|inserir|insere|incluir|inclui|criar|cria|add|adicione|coloque|inclua)\\b"), " ")
    item = item.replace(Regex("(?i)\\b(na lista|à lista|a lista|pra lista|para a lista|lista)\\b"), " ")
    item = item.replace(Regex("(?i)\\b(novo item|um item|item)\\b"), " ")
    item = item.replace(Regex("(?i)\\b(com valor|no valor de|valor|reais|real|de r\\$|r\\$)\\b"), " ")
    item = item.replace(Regex("\\s+"), " ").trim()
    // tira conectores soltos nas pontas
    item = item.replace(Regex("(?i)^(de|com|na|no|em|a|o|um|uma|e)\\s+"), "")
    item = item.replace(Regex("(?i)\\s+(de|com|na|no|em|a|o|e)$"), "")
    item = item.trim()
    if (item.isNotEmpty()) item = item[0].uppercaseChar() + item.substring(1)

    return ComandoVoz(item, valor, grupo)
}
