package com.example.immunepercentageapp

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.min

/** Glowing green progress ring that shows the immune index. */
class RingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val green = 0xFF22C55E.toInt()
    private val amber = 0xFFF59E0B.toInt()
    private val red = 0xFFEF4444.toInt()
    private val ringWidth = 9f * density
    private val glow = 10f * density

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ringWidth
        color = 0xFF10261A.toInt()
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ringWidth
        strokeCap = Paint.Cap.ROUND
        color = green
        setShadowLayer(glow, 0f, 0f, green)
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = green
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        letterSpacing = 0.2f
    }

    private val rect = RectF()
    private var shown = 0f
    private var target: Int? = null
    private var animator: ValueAnimator? = null

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null) // needed for the glow
    }

    fun setScore(score: Int?) {
        if (score == target) return
        target = score
        val c = when {
            score == null || score >= 60 -> green
            score >= 30 -> amber
            else -> red
        }
        arcPaint.color = c
        arcPaint.setShadowLayer(glow, 0f, 0f, c)
        labelPaint.color = c
        animator?.cancel()
        animator = ValueAnimator.ofFloat(shown, (score ?: 0).toFloat()).apply {
            duration = 700
            interpolator = DecelerateInterpolator()
            addUpdateListener { shown = it.animatedValue as Float; invalidate() }
            start()
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val size = min(width, height).toFloat()
        val inset = ringWidth / 2f + glow
        rect.set(
            (width - size) / 2f + inset, (height - size) / 2f + inset,
            (width + size) / 2f - inset, (height + size) / 2f - inset
        )
        canvas.drawArc(rect, 0f, 360f, false, trackPaint)
        if (target != null) canvas.drawArc(rect, -90f, 360f * shown / 100f, false, arcPaint)

        val cx = width / 2f
        val cy = height / 2f
        valuePaint.textSize = size * 0.27f
        labelPaint.textSize = size * 0.085f
        val text = target?.let { "$it%" } ?: "--"
        canvas.drawText(text, cx, cy + valuePaint.textSize * 0.12f, valuePaint)
        canvas.drawText("ENERGY", cx, cy + size * 0.24f, labelPaint)
    }
}
