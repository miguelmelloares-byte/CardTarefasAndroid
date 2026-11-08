package com.miguel.cardtarefas

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View

// Grafico evolutivo: mes atual + ate 3 anteriores, com o atual em destaque.
// Segunda linha (tracejada): projetado de cada mes, com quanto falta (ou passou) em R$ e %.
class EvoView(context: Context) : View(context) {

    private var rotulos: List<String> = emptyList()
    private var valores: List<Double> = emptyList()
    private var proj: List<Double?> = emptyList()
    private var comparar: List<Double> = emptyList()
    private val dens = resources.displayMetrics.density
    private fun dp(v: Float) = v * dens
    private val temProj get() = proj.any { it != null }

    private val linha = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = dp(2f); color = Color.parseColor("#FF86BD")
        strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
    }
    private val linhaProj = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = dp(2f); color = Color.parseColor("#C7A8F7")
        strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
        pathEffect = DashPathEffect(floatArrayOf(dp(7f), dp(4f)), 0f)
    }
    private val pontoProj = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#C7A8F7") }
    private val gap = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(5f); strokeCap = Paint.Cap.ROUND }
    private val area = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ponto = Paint(Paint.ANTI_ALIAS_FLAG)
    private val brilho = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#66FF86BD") }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE; textAlign = Paint.Align.CENTER }
    private val leg = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE; textAlign = Paint.Align.LEFT }
    private val caixa = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(1.2f); color = Color.parseColor("#C7A8F7") }

    // comparar: valores usados no "falta/passou" (padrao: os proprios valores)
    fun setDados(rotulos: List<String>, valores: List<Double>, proj: List<Double?> = emptyList(), comparar: List<Double>? = null) {
        this.rotulos = rotulos; this.valores = valores; this.proj = proj
        this.comparar = comparar ?: valores
        requestLayout(); invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, dp(if (temProj) 138f else 100f).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val n = valores.size
        if (n < 2) return
        val w = width.toFloat()
        val padX = dp(34f)
        val sobe = if (temProj) dp(14f) else 0f
        val topo = dp(22f) + sobe; val base = dp(62f) + sobe; val fundoY = dp(72f) + sobe
        // linha do projetado continua em todos os meses: mes sem projetado usa o mais proximo definido
        val primeiro = proj.firstOrNull { it != null }
        val projLinha = ArrayList<Double?>(); var ult: Double? = primeiro
        for (k in 0 until n) { val v = proj.getOrNull(k); if (v != null) ult = v; projLinha.add(if (temProj) ult else null) }
        val todos = valores + projLinha.filterNotNull()
        val mx = todos.maxOrNull() ?: 0.0; val mn = todos.minOrNull() ?: 0.0
        val faixa = if (mx - mn == 0.0) 1.0 else mx - mn
        val xs = (0 until n).map { padX + it * (w - 2 * padX) / (n - 1) }
        val yDe = { v: Double -> (base - ((v - mn) / faixa) * (base - topo)).toFloat() }
        val ys = valores.map(yDe)

        if (temProj) {
            leg.textSize = dp(9f)
            leg.color = Color.parseColor("#FF86BD"); canvas.drawText("━ realizado", 0f, dp(10f), leg)
            leg.color = Color.parseColor("#C7A8F7"); canvas.drawText("╍ projetado", dp(84f), dp(10f), leg)
        }

        val p = Path()
        for (k in 0 until n) if (k == 0) p.moveTo(xs[k], ys[k]) else p.lineTo(xs[k], ys[k])
        val a = Path(p); a.lineTo(xs[n - 1], fundoY); a.lineTo(xs[0], fundoY); a.close()
        area.shader = LinearGradient(0f, topo, 0f, fundoY, Color.parseColor("#40FF86BD"), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawPath(a, area)
        canvas.drawPath(p, linha)

        // linha do projetado (liga so' meses consecutivos que tenham projetado)
        if (temProj) {
            // diferenca visual: barra entre o realizado e o projetado (verde = ainda cabe, vermelho = passou)
            for (k in 0 until n) { val v = proj.getOrNull(k) ?: continue
                val real = kotlin.math.abs(comparar.getOrElse(k) { 0.0 }); val ok = kotlin.math.abs(v) >= real
                gap.color = Color.parseColor(if (ok) "#597EE8D2" else "#59FF8FA3")
                canvas.drawLine(xs[k], yDe(v), xs[k], ys[k], gap) }
            val pp = Path()
            for (k in 0 until n) { val y = yDe(projLinha[k]!!); if (k == 0) pp.moveTo(xs[k], y) else pp.lineTo(xs[k], y) }
            canvas.drawPath(pp, linhaProj)
            for (k in 0 until n) { val y = yDe(projLinha[k]!!)
                val r = if (proj.getOrNull(k) != null) dp(4f) else dp(2.5f)
                canvas.drawRect(xs[k] - r, y - r, xs[k] + r, y + r, pontoProj) }
            // valor do teto no fim da linha tracejada
            leg.textSize = dp(10.5f); leg.color = Color.parseColor("#C7A8F7"); leg.isFakeBoldText = true
            canvas.drawText(fmtCurto(projLinha[n - 1]!!), xs[n - 1] + dp(8f), yDe(projLinha[n - 1]!!) + dp(4f), leg)
            leg.isFakeBoldText = false
        }

        for (k in 0 until n) {
            val atual = k == n - 1
            if (atual) {
                canvas.drawCircle(xs[k], ys[k], dp(10f), brilho)
                ponto.style = Paint.Style.FILL; ponto.color = Color.parseColor("#FF86BD")
                canvas.drawCircle(xs[k], ys[k], dp(6f), ponto)
                ponto.style = Paint.Style.STROKE; ponto.strokeWidth = dp(2f); ponto.color = Color.parseColor("#2A1829")
                canvas.drawCircle(xs[k], ys[k], dp(6f), ponto)
            } else {
                ponto.style = Paint.Style.FILL; ponto.color = Color.parseColor("#2A1829")
                canvas.drawCircle(xs[k], ys[k], dp(3.5f), ponto)
                ponto.style = Paint.Style.STROKE; ponto.strokeWidth = dp(1.6f); ponto.color = Color.parseColor("#BFFF86BD")
                canvas.drawCircle(xs[k], ys[k], dp(3.5f), ponto)
            }
            // valor acima do ponto
            txt.textSize = dp(if (atual) 12f else 10f)
            txt.isFakeBoldText = atual
            txt.color = if (atual) Color.parseColor("#FFD9EA") else Color.parseColor("#B99BB4")
            canvas.drawText(fmtCurto(valores[k]), xs[k], ys[k] - dp(11f), txt)
            // mes embaixo
            val yMes = dp(92f) + sobe
            txt.textSize = dp(if (atual) 11f else 10f)
            txt.color = if (atual) Color.parseColor("#FBEEF6") else Color.parseColor("#7A5C76")
            val rot = rotulos.getOrElse(k) { "" }.uppercase()
            canvas.drawText(rot, xs[k], yMes, txt)
            if (atual) {
                val lw = txt.measureText(rot) / 2 + dp(8f)
                canvas.drawRoundRect(RectF(xs[k] - lw, yMes - dp(13f), xs[k] + lw, yMes + dp(5f)), dp(5f), dp(5f), caixa)
            }
            // projetado: quanto falta (verde) ou quanto passou (vermelho), em R$ e %
            val pv = proj.getOrNull(k)
            if (pv != null) {
                val real = kotlin.math.abs(comparar.getOrElse(k) { 0.0 })
                val dif = kotlin.math.abs(pv) - real
                val pct = if (pv != 0.0) kotlin.math.abs(dif) / kotlin.math.abs(pv) * 100 else 0.0
                txt.isFakeBoldText = false; txt.textSize = dp(9f)
                txt.color = if (dif >= 0) Color.parseColor("#7EE8D2") else Color.parseColor("#FF8FA3")
                canvas.drawText((if (dif >= 0) "falta " else "+") + fmtCurto(kotlin.math.abs(dif)), xs[k], yMes + dp(15f), txt)
                canvas.drawText(String.format(java.util.Locale.US, "%.0f%%", pct) + (if (dif >= 0) "" else " acima"), xs[k], yMes + dp(26f), txt)
            }
        }
    }
}

fun fmtCurto(v: Double): String {
    val a = kotlin.math.abs(v)
    return if (a >= 1000) {
        val s = String.format(java.util.Locale.US, "%.1f", a / 1000).replace(".", ",").removeSuffix(",0")
        (if (v < 0) "-" else "") + s + "k"
    } else String.format(java.util.Locale.US, "%.0f", v)
}
