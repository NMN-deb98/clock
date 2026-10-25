package com.example.alarmclock

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.OvershootInterpolator
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone
import kotlin.math.min

/** A small analog clock for any time zone. Feed it time with [setTime]. */
class AnalogClockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var zone: TimeZone = TimeZone.getDefault()
    var smoothSeconds: Boolean = false
    var accentColor: Int = context.getColor(R.color.accent_cyan)
        set(value) { field = value; invalidate() }

    private var timeMs: Long = System.currentTimeMillis()
    private var intro = 1f
    private var introAnimator: ValueAnimator? = null
    private val calendar: Calendar = GregorianCalendar()
    private val bounds = RectF()

    private val density = resources.displayMetrics.density
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = context.getColor(R.color.surface_elevated)
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = context.getColor(R.color.surface_border)
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = context.getColor(R.color.text_secondary)
    }
    private val handPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = context.getColor(R.color.text_primary)
    }
    private val secondPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    fun setTime(millis: Long) {
        timeMs = millis
        invalidate()
    }

    /** Ring draws itself, ticks fade in, hands swing from 12 o'clock to the real time. */
    fun playIntro(durationMs: Long = 1300L) {
        introAnimator?.cancel()
        intro = 0f
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = durationMs
        animator.interpolator = OvershootInterpolator(0.9f)
        animator.addUpdateListener {
            intro = it.animatedValue as Float
            invalidate()
        }
        introAnimator = animator
        animator.start()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val fallback = (56 * density).toInt()
        setMeasuredDimension(resolveSize(fallback, widthMeasureSpec), resolveSize(fallback, heightMeasureSpec))
    }

    override fun onDetachedFromWindow() {
        introAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val side = min(width, height).toFloat()
        val ringWidth = side * 0.045f
        val r = side / 2f - ringWidth
        if (r <= 0f) return

        canvas.drawCircle(cx, cy, r, fillPaint)
        ringPaint.strokeWidth = ringWidth
        bounds.set(cx - r, cy - r, cx + r, cy + r)
        canvas.drawArc(bounds, -90f, 360f * intro.coerceIn(0f, 1f), false, ringPaint)

        tickPaint.strokeWidth = side * 0.02f
        for (i in 0 until 12) {
            val appear = (intro * 1.5f - i / 12f * 0.5f).coerceIn(0f, 1f)
            if (appear <= 0f) continue
            tickPaint.alpha = (255 * appear).toInt()
            val isMajor = i % 3 == 0
            val inner = r * (if (isMajor) 0.72f else 0.8f)
            val outer = r * 0.9f
            canvas.save()
            canvas.rotate(i * 30f, cx, cy)
            canvas.drawLine(cx, cy - inner, cx, cy - outer, tickPaint)
            canvas.restore()
        }

        calendar.timeZone = zone
        calendar.timeInMillis = timeMs
        val hours = (calendar.get(Calendar.HOUR) % 12).toFloat()
        val minutes = calendar.get(Calendar.MINUTE).toFloat()
        var seconds = calendar.get(Calendar.SECOND).toFloat()
        if (smoothSeconds) seconds += calendar.get(Calendar.MILLISECOND) / 1000f

        drawHand(canvas, cx, cy, (hours + minutes / 60f) * 30f * intro, r * 0.5f, side * 0.06f, handPaint)
        drawHand(canvas, cx, cy, (minutes + seconds / 60f) * 6f * intro, r * 0.74f, side * 0.04f, handPaint)
        secondPaint.color = accentColor
        drawHand(canvas, cx, cy, seconds * 6f * intro, r * 0.82f, side * 0.018f, secondPaint)

        centerPaint.color = accentColor
        canvas.drawCircle(cx, cy, side * 0.045f, centerPaint)
    }

    private fun drawHand(
        canvas: Canvas, cx: Float, cy: Float, angle: Float, length: Float, width: Float, paint: Paint
    ) {
        paint.strokeWidth = width
        canvas.save()
        canvas.rotate(angle, cx, cy)
        canvas.drawLine(cx, cy + length * 0.18f, cx, cy - length, paint)
        canvas.restore()
    }
}
