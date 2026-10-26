package com.example.alarmclock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * A calm, slowly moving landscape: mountains, a lake with shimmering reflections,
 * drifting clouds and mist, birds by day, stars / moon / fireflies / shooting stars at night.
 * The sky follows the real time of day.
 */
class NatureSceneView(context: Context) : View(context) {
    private var timeOfDay = 12f
    private val startedAt = SystemClock.uptimeMillis()
    private val rnd = Random(7)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val path = Path()

    private class Star(val x: Float, val y: Float, val size: Float, val phase: Float)
    private class Cloud(val y: Float, val scale: Float, val speed: Float, val offset: Float, val puffs: List<FloatArray>)
    private class Firefly(val x: Float, val y: Float, val rx: Float, val ry: Float, val speed: Float, val phase: Float)
    private class Bird(val y: Float, val speed: Float, val offset: Float, val size: Float)

    private val stars = List(110) { Star(rnd.nextFloat(), rnd.nextFloat() * 0.55f, 0.6f + rnd.nextFloat() * 1.6f, rnd.nextFloat() * 6.28f) }
    private val clouds = List(6) {
        Cloud(
            y = 0.10f + rnd.nextFloat() * 0.22f, scale = 0.7f + rnd.nextFloat() * 0.9f,
            speed = 5f + rnd.nextFloat() * 9f, offset = rnd.nextFloat(),
            puffs = List(5) { j -> floatArrayOf((j - 2) * 0.55f + rnd.nextFloat() * 0.2f, rnd.nextFloat() * 0.25f - 0.12f, 0.55f + rnd.nextFloat() * 0.45f) }
        )
    }
    private val fireflies = List(14) { Firefly(rnd.nextFloat(), 0.74f + rnd.nextFloat() * 0.2f, 0.02f + rnd.nextFloat() * 0.05f, 0.01f + rnd.nextFloat() * 0.03f, 0.3f + rnd.nextFloat() * 0.6f, rnd.nextFloat() * 6.28f) }
    private val birds = List(4) { Bird(0.17f + rnd.nextFloat() * 0.18f, 14f + rnd.nextFloat() * 10f, rnd.nextFloat(), 7f + rnd.nextFloat() * 6f) }

    private var ridges: List<FloatArray> = emptyList()
    private var cloudShader: RadialGradient? = null

    private class SkyKey(val hour: Float, val top: Int, val bottom: Int)
    private val skyKeys = listOf(
        SkyKey(0f, 0xFF050B22.toInt(), 0xFF111B40.toInt()),
        SkyKey(5f, 0xFF171C4A.toInt(), 0xFF5B4A82.toInt()),
        SkyKey(6.5f, 0xFF3E6FB5.toInt(), 0xFFF6B27A.toInt()),
        SkyKey(9f, 0xFF4A90D9.toInt(), 0xFFA9D4F5.toInt()),
        SkyKey(12f, 0xFF3B82D6.toInt(), 0xFF9ED0F5.toInt()),
        SkyKey(16f, 0xFF4A86CC.toInt(), 0xFFBFD8EE.toInt()),
        SkyKey(18f, 0xFF3A5290.toInt(), 0xFFF2894F.toInt()),
        SkyKey(19.3f, 0xFF1E2457.toInt(), 0xFF7A4F7A.toInt()),
        SkyKey(21f, 0xFF0A1030.toInt(), 0xFF1B2550.toInt()),
        SkyKey(24f, 0xFF050B22.toInt(), 0xFF111B40.toInt())
    )

    fun setTime(hourFraction: Float) {
        timeOfDay = ((hourFraction % 24f) + 24f) % 24f
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        postInvalidateOnAnimation()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == 0 || h == 0) return
        val r = Random(21)
        ridges = listOf(
            buildRidge(r, 0.40f, 0.12f), buildRidge(r, 0.47f, 0.10f), buildRidge(r, 0.55f, 0.07f)
        )
        cloudShader = RadialGradient(0f, 0f, 1f, intArrayOf(Color.argb(200, 255, 255, 255), Color.argb(0, 255, 255, 255)), null, Shader.TileMode.CLAMP)
    }

    /** Midpoint-displacement ridge line: x in 0..1 and y as a fraction of the view height. */
    private fun buildRidge(r: Random, baseY: Float, amp: Float): FloatArray {
        val n = 65
        val ys = FloatArray(n)
        ys[0] = baseY - amp * r.nextFloat(); ys[n - 1] = baseY - amp * r.nextFloat()
        var step = n - 1
        var scale = amp
        while (step > 1) {
            val half = step / 2
            var i = half
            while (i < n - 1) {
                ys[i] = (ys[i - half] + ys[i + half]) / 2f + (r.nextFloat() - 0.5f) * scale
                i += step
            }
            step = half
            scale *= 0.55f
        }
        return ys
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    private fun mix(c1: Int, c2: Int, t: Float): Int = Color.argb(
        lerp(Color.alpha(c1).toFloat(), Color.alpha(c2).toFloat(), t).toInt(),
        lerp(Color.red(c1).toFloat(), Color.red(c2).toFloat(), t).toInt(),
        lerp(Color.green(c1).toFloat(), Color.green(c2).toFloat(), t).toInt(),
        lerp(Color.blue(c1).toFloat(), Color.blue(c2).toFloat(), t).toInt()
    )

    private fun skyColors(): IntArray {
        for (i in 0 until skyKeys.size - 1) {
            val a = skyKeys[i]
            val b = skyKeys[i + 1]
            if (timeOfDay >= a.hour && timeOfDay <= b.hour) {
                val t = (timeOfDay - a.hour) / (b.hour - a.hour)
                return intArrayOf(mix(a.top, b.top, t), mix(a.bottom, b.bottom, t))
            }
        }
        return intArrayOf(skyKeys[0].top, skyKeys[0].bottom)
    }

    /** 1 at deep night, 0 in full daylight. */
    private fun nightFactor(): Float {
        val h = timeOfDay
        return when {
            h < 5f || h > 21f -> 1f
            h < 7f -> 1f - (h - 5f) / 2f
            h < 17f -> 0f
            h < 19.5f -> (h - 17f) / 2.5f * 0.8f
            else -> 0.8f + (h - 19.5f) / 1.5f * 0.2f
        }.coerceIn(0f, 1f)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return
        val t = (SystemClock.uptimeMillis() - startedAt) / 1000f
        val night = nightFactor()
        val sky = skyColors()
        val waterTop = h * 0.70f

        // sky
        paint.shader = LinearGradient(0f, 0f, 0f, waterTop, sky[0], sky[1], Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, waterTop + 2f, paint)
        paint.shader = null

        // stars
        if (night > 0.05f) {
            for (s in stars) {
                val tw = 0.55f + 0.45f * sin(t * 1.4f + s.phase)
                paint.color = Color.argb((255 * night * tw).toInt().coerceIn(0, 255), 255, 255, 255)
                canvas.drawCircle(s.x * w, s.y * h, s.size, paint)
            }
            shootingStar(canvas, t, w, h, night)
        }

        // sun and moon
        val dayT = (timeOfDay - 6f) / 12f
        if (timeOfDay in 5.2f..18.8f) {
            val x = w * (0.12f + 0.76f * dayT.coerceIn(0f, 1f))
            val y = waterTop * 0.92f - sin(PI.toFloat() * dayT.coerceIn(0f, 1f)) * waterTop * 0.62f
            val warm = if (dayT < 0.12f || dayT > 0.88f) 1f else 0f
            val sunColor = mix(Color.rgb(255, 244, 200), Color.rgb(255, 150, 70), warm)
            glow(canvas, x, y, w * 0.30f, sunColor, 110)
            paint.color = sunColor
            canvas.drawCircle(x, y, w * 0.045f, paint)
            sunX = x; sunY = y; sunColorNow = sunColor; sunVisible = true
        } else sunVisible = false
        if (night > 0.2f) {
            val nt = (((timeOfDay - 18f + 24f) % 24f) / 12f).coerceIn(0f, 1f)
            val x = w * (0.85f - 0.70f * nt)
            val y = waterTop * 0.92f - sin(PI.toFloat() * nt) * waterTop * 0.58f
            glow(canvas, x, y, w * 0.28f, Color.rgb(190, 205, 255), (90 * night).toInt())
            paint.color = Color.argb((255 * night).toInt(), 244, 246, 255)
            canvas.drawCircle(x, y, w * 0.038f, paint)
            paint.color = Color.argb((70 * night).toInt(), 160, 170, 200)
            canvas.drawCircle(x - w * 0.012f, y - w * 0.008f, w * 0.008f, paint)
            canvas.drawCircle(x + w * 0.014f, y + w * 0.012f, w * 0.006f, paint)
            moonX = x; moonY = y; moonVisible = true
        } else moonVisible = false

        // clouds
        val cloudAlpha = (1f - night * 0.78f)
        for (c in clouds) {
            val span = w * 1.6f
            val cx = ((c.offset * span + t * c.speed) % span) - w * 0.3f
            drawCloud(canvas, c, cx, c.y * h, w, cloudAlpha, sky[1])
        }

        // birds
        if (night < 0.5f) for (b in birds) drawBird(canvas, b, t, w, h, 1f - night * 2f)

        // mountains (far to near)
        val baseColors = intArrayOf(mix(sky[1], Color.rgb(70, 90, 140), 0.45f), mix(sky[1], Color.rgb(40, 60, 110), 0.65f), mix(sky[1], Color.rgb(18, 35, 70), 0.8f))
        for (i in ridges.indices) {
            val ys = ridges[i]
            val dark = 1f - night * 0.55f
            paint.color = darken(baseColors[i], if (i == 0) dark else dark * (1f - 0.05f * i))
            path.reset()
            path.moveTo(0f, waterTop + 4f)
            for (k in ys.indices) path.lineTo(w * k / (ys.size - 1), ys[k] * h)
            path.lineTo(w, waterTop + 4f)
            path.close()
            canvas.drawPath(path, paint)
            mist(canvas, t, w, h, i, sky[1], night)
        }
        pines(canvas, w, h, waterTop, night)

        // lake
        paint.shader = LinearGradient(0f, waterTop, 0f, h, darken(mix(sky[1], Color.rgb(30, 70, 120), 0.55f), 0.85f - night * 0.4f), darken(mix(sky[0], Color.rgb(8, 20, 50), 0.6f), 0.7f - night * 0.35f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, waterTop, w, h, paint)
        paint.shader = null
        reflections(canvas, t, w, h, waterTop)
        waves(canvas, t, w, h, waterTop, night)

        // fireflies
        if (night > 0.4f) for (f in fireflies) {
            val x = (f.x + sin(t * f.speed + f.phase) * f.rx) * w
            val y = (f.y + cos(t * f.speed * 0.8f + f.phase) * f.ry) * h
            val a = ((0.5f + 0.5f * sin(t * 2.2f + f.phase)) * 230f * night).toInt().coerceIn(0, 255)
            glow(canvas, x, y, 14f * resources.displayMetrics.density, Color.rgb(220, 255, 140), a / 3)
            paint.color = Color.argb(a, 240, 255, 170)
            canvas.drawCircle(x, y, 2f * resources.displayMetrics.density, paint)
        }

        postInvalidateDelayed(33)
    }

    private var sunX = 0f; private var sunY = 0f; private var sunColorNow = Color.WHITE; private var sunVisible = false
    private var moonX = 0f; private var moonY = 0f; private var moonVisible = false

    private fun darken(c: Int, f: Float): Int {
        val k = f.coerceIn(0f, 1f)
        return Color.argb(Color.alpha(c), (Color.red(c) * k).toInt(), (Color.green(c) * k).toInt(), (Color.blue(c) * k).toInt())
    }

    private fun glow(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int, alpha: Int) {
        paint.shader = RadialGradient(x, y, radius, intArrayOf(Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color)), Color.argb(0, Color.red(color), Color.green(color), Color.blue(color))), null, Shader.TileMode.CLAMP)
        canvas.drawCircle(x, y, radius, paint)
        paint.shader = null
    }

    private fun drawCloud(canvas: Canvas, c: Cloud, cx: Float, cy: Float, w: Float, alphaScale: Float, tint: Int) {
        val base = w * 0.11f * c.scale
        val color = mix(Color.WHITE, tint, 0.25f)
        for (p in c.puffs) {
            val r = base * p[2]
            glow(canvas, cx + p[0] * base, cy + p[1] * base, r, color, (150 * alphaScale).toInt())
        }
    }

    private fun drawBird(canvas: Canvas, b: Bird, t: Float, w: Float, h: Float, alpha: Float) {
        val span = w + 120f
        val x = ((b.offset * span + t * b.speed) % span) - 60f
        val y = b.y * h + sin(t * 0.7f + b.offset * 9f) * 10f
        val flap = sin(t * 7f + b.offset * 20f) * b.size * 0.55f
        strokePaint.color = Color.argb((190 * alpha.coerceIn(0f, 1f)).toInt(), 20, 30, 50)
        strokePaint.strokeWidth = 1.6f * resources.displayMetrics.density
        path.reset()
        path.moveTo(x - b.size, y - flap)
        path.quadTo(x - b.size * 0.4f, y - b.size * 0.15f, x, y)
        path.quadTo(x + b.size * 0.4f, y - b.size * 0.15f, x + b.size, y - flap)
        canvas.drawPath(path, strokePaint)
    }

    private fun mist(canvas: Canvas, t: Float, w: Float, h: Float, layer: Int, tint: Int, night: Float) {
        val y = h * (0.55f + layer * 0.045f)
        val drift = sin(t * 0.05f + layer) * w * 0.08f
        val col = mix(tint, Color.WHITE, 0.35f)
        paint.shader = RadialGradient(w * 0.5f + drift, y, w * 0.75f, intArrayOf(Color.argb((70 * (1f - night * 0.6f)).toInt(), Color.red(col), Color.green(col), Color.blue(col)), Color.argb(0, Color.red(col), Color.green(col), Color.blue(col))), null, Shader.TileMode.CLAMP)
        canvas.save()
        canvas.scale(1f, 0.22f, w * 0.5f, y)
        canvas.drawCircle(w * 0.5f + drift, y, w * 0.75f, paint)
        canvas.restore()
        paint.shader = null
    }

    private fun pines(canvas: Canvas, w: Float, h: Float, waterTop: Float, night: Float) {
        paint.color = darken(Color.rgb(12, 32, 40), 1f - night * 0.5f)
        val density = resources.displayMetrics.density
        val rr = Random(5)
        var x = -10f
        while (x < w + 10f) {
            val th = (14f + rr.nextFloat() * 26f) * density
            val tw = th * 0.38f
            val base = waterTop + 3f
            path.reset()
            path.moveTo(x, base)
            path.lineTo(x + tw / 2f, base - th)
            path.lineTo(x + tw, base)
            path.close()
            canvas.drawPath(path, paint)
            x += tw * (0.55f + rr.nextFloat() * 0.5f)
        }
    }

    private fun reflections(canvas: Canvas, t: Float, w: Float, h: Float, waterTop: Float) {
        val cx: Float
        val color: Int
        val strength: Float
        when {
            sunVisible -> { cx = sunX; color = sunColorNow; strength = 1f }
            moonVisible -> { cx = moonX; color = Color.rgb(200, 215, 255); strength = 0.7f }
            else -> return
        }
        val density = resources.displayMetrics.density
        for (i in 0 until 16) {
            val f = i / 15f
            val y = waterTop + 6f * density + f * (h - waterTop) * 0.9f
            val halfW = (10f + f * 70f) * density * (0.6f + 0.4f * sin(t * 1.8f + i * 1.3f))
            val off = sin(t * 0.9f + i) * 6f * density
            val a = ((1f - f) * 150f * strength * (0.5f + 0.5f * sin(t * 2f + i * 0.8f))).toInt().coerceIn(0, 255)
            paint.color = Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
            canvas.drawRoundRect(cx + off - halfW, y, cx + off + halfW, y + 2.2f * density, 3f * density, 3f * density, paint)
        }
    }

    private fun waves(canvas: Canvas, t: Float, w: Float, h: Float, waterTop: Float, night: Float) {
        val density = resources.displayMetrics.density
        strokePaint.strokeWidth = 1.4f * density
        for (k in 0 until 6) {
            val f = k / 5f
            val y0 = waterTop + (h - waterTop) * (0.08f + f * 0.88f)
            val amp = (1.2f + f * 4f) * density
            val len = (60f + f * 120f) * density
            val speed = 0.6f + f * 0.5f
            strokePaint.color = Color.argb(((22 + 26 * f) * (1f - night * 0.35f)).toInt(), 255, 255, 255)
            path.reset()
            var x = 0f
            path.moveTo(0f, y0 + sin(t * speed + k) * amp)
            while (x <= w) {
                x += 14f * density
                path.lineTo(x, y0 + sin(x / len * 2f * PI.toFloat() + t * speed + k) * amp)
            }
            canvas.drawPath(path, strokePaint)
        }
    }

    private fun shootingStar(canvas: Canvas, t: Float, w: Float, h: Float, night: Float) {
        val cycle = 11f
        val p = (t % cycle) / 1.1f
        if (p > 1f) return
        val seed = (t / cycle).toInt()
        val r = Random(seed)
        val sx = w * (0.15f + 0.6f * r.nextFloat())
        val sy = h * (0.05f + 0.2f * r.nextFloat())
        val dx = w * 0.22f
        val dy = h * 0.09f
        val x = sx + dx * p
        val y = sy + dy * p
        val a = ((1f - abs(p - 0.4f)) * 230f * night).toInt().coerceIn(0, 255)
        strokePaint.strokeWidth = 1.8f * resources.displayMetrics.density
        strokePaint.shader = LinearGradient(x, y, x - dx * 0.4f, y - dy * 0.4f, Color.argb(a, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP)
        canvas.drawLine(x, y, x - dx * 0.4f, y - dy * 0.4f, strokePaint)
        strokePaint.shader = null
    }
}
