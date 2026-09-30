package com.ilyaas.einkclock.util

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/**
 * One background thread for every SQLite / AlarmManager write, plus main-thread
 * posting. No coroutines, no AndroidX: the brief allows exactly this much.
 */
object Async {

    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "clock-io") }
    private val main = Handler(Looper.getMainLooper())

    fun io(block: () -> Unit) {
        io.execute(block)
    }

    fun <T> ioAsync(work: () -> T, then: (T) -> Unit) {
        io.execute {
            val result = work()
            main.post { then(result) }
        }
    }

    fun main(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    fun mainDelayed(delayMs: Long, runnable: Runnable) {
        main.postDelayed(runnable, delayMs)
    }

    fun cancel(runnable: Runnable) {
        main.removeCallbacks(runnable)
    }
}
