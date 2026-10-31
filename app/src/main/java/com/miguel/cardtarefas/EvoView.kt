package com.miguel.cardtarefas

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View

// Grafico evolutivo do saldo: aba ativa + ate 3 anteriores, com a ativa em destaque.
class EvoView(context: Context) : View(context) {

    private var rotulos: List<String> = emptyList()
    private var valores: List<Double> = emptyList()
    private val dens = resources.displayMetrics.density
    private fun dp(v: Float) = v * dens

    private val linha = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = dp(2f); color = Color.parseColor("#FF86BD")
        strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
    }
    private val area = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ponto = Paint(Paint.ANTI_ALIAS_FLAG)
    private val brilho = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#66FF86BD") }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE; textAlign = Paint.Align.CENTER }
    private val caixa = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(1.2f); color = Color.parseColor("#C7A8F7") }

    fun setDados(rotulos: List<String>, valores: List<Double>) {
        this.rotulos = rotulos; this.valores = valores; invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, dp(100f).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val n = valores.size
        if (n < 2) return
        val w = width.toFloat()
        val padX = dp(34f)
        val topo = dp(22f); val base = dp(62f)
        val mx = valores.maxOrNull() ?: 0.0; val mn = valores.minOrNull() ?: 0.0
        val faixa = if (mx - mn == 0.0) 1.0 else mx - mn
        val xs = (0 until n).map { padX + it * (w - 2 * padX) / (n - 1) }
        val ys = valores.map { (base - ((it - mn) / faixa) * (base - topo)).toFloat() }

        val p = Path()
        for (k in 0 until n) if (k == 0) p.moveTo(xs[k], ys[k]) else p.lineTo(xs[k], ys[k])
        val a = Path(p); a.lineTo(xs[n - 1], dp(72f)); a.lineTo(xs[0], dp(72f)); a.close()
        area.shader = LinearGradient(0f, topo, 0f, dp(72f), Color.parseColor("#40FF86BD"), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawPath(a, area)
        canvas.drawPath(p, linha)

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
            txt.textSize = dp(if (atual) 11f else 10f)
            txt.color = if (atual) Color.parseColor("#FBEEF6") else Color.parseColor("#7A5C76")
            val rot = rotulos.getOrElse(k) { "" }.uppercase()
            canvas.drawText(rot, xs[k], dp(92f), txt)
            if (atual) {
                val lw = txt.measureText(rot) / 2 + dp(8f)
                canvas.drawRoundRect(RectF(xs[k] - lw, dp(79f), xs[k] + lw, dp(97f)), dp(5f), dp(5f), caixa)
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
