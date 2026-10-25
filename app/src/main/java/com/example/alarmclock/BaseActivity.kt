package com.example.alarmclock

import androidx.appcompat.app.AppCompatActivity

/** Adds a smooth "going back" transition to every secondary screen. */
open class BaseActivity : AppCompatActivity() {
    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.screen_back_in, R.anim.screen_back_out)
    }
}
