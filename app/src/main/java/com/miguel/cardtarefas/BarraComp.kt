package com.miguel.cardtarefas

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

// Barra do comprometimento futuro: trilho com o projetado (tracejado + marcador amarelo),
// comprometido em rosa e wishlist (simulada) em lilas; o que passa do projetado fica em vermelho.
class BarraComp(context: Context) : View(context) {
    private var escala = 1.0; private var proj: Double? = null; private var base = 0.0; private var wl = 0.0
    private val dens = resources.displayMetrics.density
    private fun dp(v: Float) = v * dens
    private val trilho = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#120912") }
    private val pj = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(1f); color = Color.parseColor("#8A6886")
        pathEffect = DashPathEffect(floatArrayOf(dp(4f), dp(3f)), 0f) }
    private val pBase = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF86BD") }
    private val pWl = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#C7A8F7") }
    private val pOver = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF5A78") }
    private val pMarca = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F6C177") }

    fun setDados(escala: Double, proj: Double?, base: Double, wl: Double) {
        this.escala = if (escala <= 0) 1.0 else escala; this.proj = proj; this.base = base; this.wl = wl; invalidate()
    }

    override fun onMeasure(w: Int, h: Int) { setMeasuredDimension(MeasureSpec.getSize(w), dp(18f).toInt()) }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat(); val r = dp(5f)
        fun x(v: Double) = (v / escala * w).toFloat().coerceIn(0f, w)
        c.drawRoundRect(RectF(0f, 0f, w, h), r, r, trilho)
        val p = proj
        if (p != null) c.drawRoundRect(RectF(dp(0.5f), dp(0.5f), x(p) - dp(0.5f), h - dp(0.5f)), r, r, pj)
        val t = dp(3.5f); val b = h - dp(3.5f)
        val xb = x(base); val xw = x(base + wl)
        if (xb > 0) c.drawRect(0f, t, xb, b, pBase)
        if (xw > xb) c.drawRect(xb, t, xw, b, pWl)
        if (p != null) {
            val xp = x(p)
            if (xw > xp) c.drawRect(xp, t, xw, b, pOver)
            c.drawRect(xp - dp(1f), 0f, xp + dp(1f), h, pMarca)
        }
    }
}
