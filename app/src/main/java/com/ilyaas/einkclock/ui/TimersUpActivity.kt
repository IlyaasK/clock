package com.ilyaas.einkclock.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.ilyaas.einkclock.R
import com.ilyaas.einkclock.alarm.RingActions
import com.ilyaas.einkclock.data.Repo
import com.ilyaas.einkclock.data.TimerRow
import com.ilyaas.einkclock.data.TimerState
import com.ilyaas.einkclock.util.Async
import com.ilyaas.einkclock.util.Fmt

/** "Timers up": dismiss or +1 min. Same chrome as the ring screen, no animation. */
class TimersUpActivity : Activity() {

    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.WHITE)
            setPadding(Ui.dp(this@TimersUpActivity, 16f), Ui.dp(this@TimersUpActivity, 32f), Ui.dp(this@TimersUpActivity, 16f), Ui.dp(this@TimersUpActivity, 24f))
            addView(Ui.text(this@TimersUpActivity, getString(R.string.timers_up), 56f, Ui.BLACK).apply { gravity = Gravity.CENTER })
            addView(Ui.space(this@TimersUpActivity, 16))
            addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(
                Ui.button(this@TimersUpActivity, getString(R.string.dismiss), filled = true, sp = 24f, heightDp = 72).apply {
                    setOnClickListener {
                        RingActions.dismissAllTimers(this@TimersUpActivity)
                        finish()
                        overridePendingTransition(0, 0)
                    }
                },
            )
        }
        setContentView(column)
        load()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        load()
    }

    private fun load() {
        Async.ioAsync(
            work = { Repo.timers().filter { it.state == TimerState.IDLE && it.remainingMs <= 0L && it.lengthMs > 0L } },
            then = { timers -> render(timers) },
        )
    }

    private fun render(timers: List<TimerRow>) {
        list.removeAllViews()
        for (timer in timers) {
            val row = Ui.horizontal(this).apply { setPadding(0, Ui.dp(this@TimersUpActivity, 8f), 0, Ui.dp(this@TimersUpActivity, 8f)) }
            row.addView(
                Ui.text(this, timer.label.ifBlank { getString(R.string.timer_label_default) } + "  " + Fmt.hms(0), 24f, Ui.BLACK),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
            row.addView(
                Ui.button(this, getString(R.string.add_minute), filled = false, sp = 16f, heightDp = 56).apply {
                    layoutParams = LinearLayout.LayoutParams(Ui.dp(this@TimersUpActivity, 130f), Ui.dp(this@TimersUpActivity, 56f))
                    setOnClickListener {
                        RingActions.extendTimer(this@TimersUpActivity, timer.id)
                        finish()
                        overridePendingTransition(0, 0)
                    }
                },
            )
            list.addView(row)
        }
        if (timers.isEmpty()) {
            list.addView(Ui.text(this, getString(R.string.no_timers), 20f, Ui.GRAY).apply { gravity = Gravity.CENTER })
        }
    }

    /** Only the buttons exit. */
    @Deprecated("Required override: back must not silently dismiss a timer.")
    override fun onBackPressed() {
        // deliberately ignored
    }
}
