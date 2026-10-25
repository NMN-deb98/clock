package com.example.alarmclock

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.content.res.ResourcesCompat

/** Shared animation and typography helpers so every screen feels the same. */
object UiFx {

    /** Vazirmatn in the closest available weight (300 / 400 / 500 / 700). */
    fun font(context: Context, weight: Int = 400): Typeface {
        val res = when {
            weight <= 300 -> R.font.vazirmatn_light
            weight <= 400 -> R.font.vazirmatn_regular
            weight <= 500 -> R.font.vazirmatn_medium
            else -> R.font.vazirmatn_bold
        }
        return try {
            ResourcesCompat.getFont(context, res) ?: Typeface.DEFAULT
        } catch (_: Exception) {
            Typeface.DEFAULT
        }
    }

    fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()

    fun rounded(context: Context, color: Int, radiusDp: Int, strokeColor: Int? = null): GradientDrawable {
        val d = GradientDrawable()
        d.setColor(color)
        d.cornerRadius = dp(context, radiusDp).toFloat()
        if (strokeColor != null) d.setStroke(dp(context, 1), strokeColor)
        return d
    }

    /** Fade + slide up. Call before the view is first drawn. */
    fun enter(view: View, delayMs: Long = 0L, offsetDp: Float = 22f) {
        val density = view.resources.displayMetrics.density
        view.alpha = 0f
        view.translationY = offsetDp * density
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(delayMs)
            .setDuration(440L)
            .setInterpolator(DecelerateInterpolator(1.8f))
            .start()
    }

    /** Staggered entrance for every direct child of [parent]. */
    fun enterAll(parent: ViewGroup, startDelayMs: Long = 0L, stepMs: Long = 70L) {
        for (i in 0 until parent.childCount) {
            enter(parent.getChildAt(i), startDelayMs + i * stepMs)
        }
    }

    /** Springy press feedback that never consumes the touch (clicks keep working). */
    @SuppressLint("ClickableViewAccessibility")
    fun pressable(view: View, pressedScale: Float = 0.94f) {
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN ->
                    v.animate().scaleX(pressedScale).scaleY(pressedScale).setDuration(90L)
                        .setInterpolator(DecelerateInterpolator()).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.animate().scaleX(1f).scaleY(1f).setDuration(220L)
                        .setInterpolator(OvershootInterpolator(2.2f)).start()
            }
            false
        }
    }

    /** Gentle endless "breathing" scale. Cancel the returned animator in onPause/onDestroy. */
    fun breathe(view: View, to: Float = 1.06f, durationMs: Long = 1500L): ObjectAnimator {
        val animator = ObjectAnimator.ofPropertyValuesHolder(
            view,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, to),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, to)
        )
        animator.duration = durationMs
        animator.repeatCount = ObjectAnimator.INFINITE
        animator.repeatMode = ObjectAnimator.REVERSE
        animator.interpolator = AccelerateDecelerateInterpolator()
        animator.start()
        return animator
    }
}
