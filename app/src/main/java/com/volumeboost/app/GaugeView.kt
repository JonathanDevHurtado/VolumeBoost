package com.volumeboost.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Medidor circular de 270° para 100 % .. 150 %.
 *
 * Rendimiento (Helio G99 / pantalla de 120 Hz): todos los Paint, RectF y el degradado se crean
 * una sola vez; onDraw() no reserva memoria. El texto central solo se reconstruye cuando cambia
 * el número entero mostrado.
 */
class GaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** Texto pequeño bajo el porcentaje (p. ej. "+3,5 dB"). */
    var subtitle: String = ""
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private val dm = resources.displayMetrics
    private var shown: Float = BoostMath.MIN_PERCENT.toFloat()
    private var shownInt: Int = BoostMath.MIN_PERCENT
    private var valueText: String = "${BoostMath.MIN_PERCENT}%"
    private var animator: ValueAnimator? = null

    private var cx = 0f
    private var cy = 0f
    private var radius = 0f
    private var stroke = 0f
    private val arcRect = RectF()

    private val green = context.getColor(R.color.gauge_green)
    private val yellow = context.getColor(R.color.gauge_yellow)
    private val orange = context.getColor(R.color.gauge_orange)
    private val red = context.getColor(R.color.gauge_red)

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = context.getColor(R.color.gauge_track)
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = context.getColor(R.color.gauge_tick)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = context.getColor(R.color.gauge_label)
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, dm)
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = context.getColor(R.color.text_secondary)
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    init {
        contentDescription = context.getString(R.string.gauge_desc, shownInt)
    }

    // ------------------------------------------------------------------ API

    /** Muestra [target] (100..150). Con [animate] el aguja se desliza; sin él salta (para mantener presionado). */
    fun setPercent(target: Float, animate: Boolean) {
        val t = target.coerceIn(BoostMath.MIN_PERCENT.toFloat(), BoostMath.MAX_PERCENT.toFloat())
        animator?.cancel()
        animator = null
        if (!animate || !isAttachedToWindow || abs(t - shown) < 0.01f) {
            applyShown(t)
            return
        }
        animator = ValueAnimator.ofFloat(shown, t).apply {
            duration = 220L
            interpolator = DecelerateInterpolator()
            addUpdateListener { applyShown(it.animatedValue as Float) }
            start()
        }
    }

    private fun applyShown(value: Float) {
        shown = value
        val asInt = value.roundToInt()
        if (asInt != shownInt) {
            shownInt = asInt
            valueText = "$asInt%"
            contentDescription = context.getString(R.string.gauge_desc, asInt)
        }
        invalidate()
    }

    // ------------------------------------------------------------------ medidas y dibujo

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val max = (320 * dm.density).toInt()
        val available = when (MeasureSpec.getMode(widthMeasureSpec)) {
            MeasureSpec.UNSPECIFIED -> max
            else -> MeasureSpec.getSize(widthMeasureSpec)
        }
        val size = min(available, max)
        setMeasuredDimension(size, size)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val side = min(w, h).toFloat()
        cx = w / 2f
        cy = h / 2f
        stroke = side * 0.085f
        radius = side / 2f - stroke * 1.25f
        arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius)

        trackPaint.strokeWidth = stroke
        progressPaint.strokeWidth = stroke
        tickPaint.strokeWidth = side * 0.006f
        valuePaint.textSize = side * 0.25f
        subPaint.textSize = side * 0.055f

        // El SweepGradient empieza a las 3 en punto; lo giramos para que arranque donde arranca el arco.
        val colors = intArrayOf(green, yellow, orange, red, red)
        val positions = floatArrayOf(0f, 0.28f, 0.52f, 0.75f, 1f)
        val shader = SweepGradient(cx, cy, colors, positions)
        shader.setLocalMatrix(Matrix().apply { setRotate(START_ANGLE, cx, cy) })
        progressPaint.shader = shader
    }

    override fun onDraw(canvas: Canvas) {
        val f = BoostMath.fraction(shown)

        // Pista de fondo
        canvas.drawArc(arcRect, START_ANGLE, SWEEP_ANGLE, false, trackPaint)

        // Marcas cada 5 % (largas en 100 / 125 / 150) y etiquetas
        for (i in 0..10) {
            val deg = START_ANGLE + SWEEP_ANGLE * i / 10f
            val rad = Math.toRadians(deg.toDouble())
            val c = cos(rad).toFloat()
            val s = sin(rad).toFloat()
            val major = i % 5 == 0
            val outer = radius - stroke * 0.85f
            val inner = outer - stroke * (if (major) 0.75f else 0.4f)
            canvas.drawLine(cx + c * inner, cy + s * inner, cx + c * outer, cy + s * outer, tickPaint)
            if (major) {
                // El 125 % va dentro del arco; los extremos, pegados al arco para no
                // cruzarse con el subtítulo central.
                val lr = if (i == 5) radius - stroke * 1.9f else radius - stroke * 1.0f
                val text = when (i) {
                    0 -> "100"
                    5 -> "125"
                    else -> "150"
                }
                canvas.drawText(
                    text, cx + c * lr,
                    cy + s * lr - (labelPaint.ascent() + labelPaint.descent()) / 2f,
                    labelPaint
                )
            }
        }

        // Progreso con degradado verde -> amarillo -> naranja -> rojo
        canvas.drawArc(arcRect, START_ANGLE, SWEEP_ANGLE * f, false, progressPaint)

        // Tapa el extremo inicial con el verde del degradado: el remate redondo del arco
        // muestrea el rojo final del SweepGradient y dejaba un punto rojo al principio.
        val startRad = Math.toRadians(START_ANGLE.toDouble())
        val startX = cx + cos(startRad).toFloat() * radius
        val startY = cy + sin(startRad).toFloat() * radius
        glowPaint.color = green
        canvas.drawCircle(startX, startY, stroke * 0.5f, glowPaint)

        // Perilla al final del progreso
        val endRad = Math.toRadians((START_ANGLE + SWEEP_ANGLE * f).toDouble())
        val kx = cx + cos(endRad).toFloat() * radius
        val ky = cy + sin(endRad).toFloat() * radius
        val tone = colorAt(f)
        glowPaint.color = Color.argb(90, Color.red(tone), Color.green(tone), Color.blue(tone))
        canvas.drawCircle(kx, ky, stroke * 1.0f, glowPaint)
        canvas.drawCircle(kx, ky, stroke * 0.58f, knobPaint)

        // Texto central
        valuePaint.color = tone
        val valueBaseline = cy - (valuePaint.ascent() + valuePaint.descent()) / 2f
        canvas.drawText(valueText, cx, valueBaseline - stroke * 0.2f, valuePaint)
        if (subtitle.isNotEmpty()) {
            canvas.drawText(subtitle, cx, valueBaseline + stroke * 0.88f, subPaint)
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    // ------------------------------------------------------------------ color

    /** Mismo recorrido de colores que el degradado, sin reservar objetos. */
    private fun colorAt(f: Float): Int {
        val s1 = 0.28f / 0.75f
        val s2 = 0.52f / 0.75f
        return when {
            f <= s1 -> lerp(green, yellow, f / s1)
            f <= s2 -> lerp(yellow, orange, (f - s1) / (s2 - s1))
            else -> lerp(orange, red, (f - s2) / (1f - s2))
        }
    }

    private fun lerp(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * k).roundToInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * k).roundToInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k).roundToInt()
        )
    }

    companion object {
        private const val START_ANGLE = 135f
        private const val SWEEP_ANGLE = 270f
    }
}
