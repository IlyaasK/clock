package com.ilyaas.einkclock

import android.app.Application
import com.ilyaas.einkclock.alarm.Scheduler
import com.ilyaas.einkclock.data.Prefs
import com.ilyaas.einkclock.data.Repo
import com.ilyaas.einkclock.util.Async
import java.time.LocalDate

class ClockApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
        Prefs.init(this)
        // Nothing that can block the first frame: channels are created by
        // AlarmService before it posts anything, and pruning waits for idle.
        val start = Runnable {
            Async.io {
                // Self-heal: a force-stop (or any reschedule the platform dropped)
                // leaves the alarm set empty, so every process start re-registers.
                Scheduler.rescheduleAll(this)
                Repo.pruneSkips(LocalDate.now().minusDays(7))
            }
        }
        Async.mainDelayed(1_500L, start)
    }
}
