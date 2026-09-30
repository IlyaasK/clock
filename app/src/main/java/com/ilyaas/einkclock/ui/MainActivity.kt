package com.ilyaas.einkclock.ui

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.ilyaas.einkclock.R
import com.ilyaas.einkclock.data.Prefs
import com.ilyaas.einkclock.alarm.Scheduler
import com.ilyaas.einkclock.data.Repo

/** The shell: white frame, one child view per tab, text-only tab bar. */
class MainActivity : Activity() {

    private lateinit var content: FrameLayout
    private lateinit var tabViews: List<TextView>
    private val tabs = arrayOfNulls<TabView>(4)
    private var current = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(this)
        Prefs.init(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }

        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.WHITE)
        }
        val labels = intArrayOf(R.string.tab_clock, R.string.tab_alarm, R.string.tab_stopwatch, R.string.tab_timer)
        tabViews = labels.mapIndexed { index, res ->
            Ui.text(this, getString(res), 14f, Ui.GRAY).apply {
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                setOnClickListener { show(index) }
            }.also { tv ->
                bar.addView(tv, LinearLayout.LayoutParams(0, Ui.dp(this, 56f), 1f))
            }
        }
        root.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 56f)))
        setContentView(root)

        show(intent?.getIntExtra(EXTRA_TAB, Prefs.of().lastTab) ?: Prefs.of().lastTab)
        askOnce()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        show(intent.getIntExtra(EXTRA_TAB, current))
    }

    override fun onResume() {
        super.onResume()
        tabs.getOrNull(current)?.onShown()
    }

    /** Tabs are built on first use: cold start only pays for the tab you land on. */
    private fun tabAt(index: Int): TabView {
        tabs[index]?.let { return it }
        val created = when (index) {
            TAB_ALARM -> AlarmTab(this)
            TAB_STOPWATCH -> StopwatchTab(this)
            TAB_TIMER -> TimerTab(this)
            else -> ClockTab(this)
        }
        tabs[index] = created
        return created
    }

    fun showTab(index: Int) {
        show(index)
    }

    private fun show(index: Int) {
        val target = index.coerceIn(0, 3)
        val before = current
        if (before == target) {
            tabAt(target).onShown()
            return
        }
        if (before >= 0) tabs[before]?.onHidden()
        content.removeAllViews()
        content.addView(
            tabAt(target).view,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        current = target
        Prefs.of().lastTab = target
        tabViews.forEachIndexed { i, tv ->
            val selected = i == target
            tv.setTextColor(if (selected) Ui.BLACK else Ui.GRAY)
            tv.background = Ui.tabBackground(this, selected)
        }
        tabAt(target).onShown()
    }

    // ------------------------------------------------------------ first-run dialogs

    private fun askOnce() {
        val prefs = Prefs.of()
        val notificationsGranted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!notificationsGranted && !prefs.notificationRationaleShown) {
            prefs.notificationRationaleShown = true
            AlertDialog.Builder(this)
                .setTitle(R.string.app_name)
                .setMessage(R.string.notifications_rationale)
                .setPositiveButton(R.string.continue_label) { _, _ ->
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATIONS)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            return
        }
        val pm = getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(packageName) && !prefs.batteryNudgeShown) {
            prefs.batteryNudgeShown = true
            AlertDialog.Builder(this)
                .setTitle(R.string.battery_title)
                .setMessage(R.string.battery_body)
                .setPositiveButton(R.string.battery_action) { _, _ -> openBatterySettings() }
                .setNegativeButton(R.string.cancel, null)
                .show()
            return
        }
        if (!Scheduler.canDrawOverlays(this) && !prefs.overlayNudgeShown) {
            prefs.overlayNudgeShown = true
            AlertDialog.Builder(this)
                .setTitle(R.string.overlay_title)
                .setMessage(R.string.overlay_body)
                .setPositiveButton(R.string.overlay_action) { _, _ -> openOverlaySettings() }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    fun openBatterySettings() {
        runCatching {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:$packageName")),
            )
        }
        overridePendingTransition(0, 0)
    }

    fun openOverlaySettings() {
        runCatching {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                    .setData(Uri.parse("package:$packageName")),
            )
        }
        overridePendingTransition(0, 0)
    }

    companion object {
        const val EXTRA_TAB = "tab"
        const val TAB_CLOCK = 0
        const val TAB_ALARM = 1
        const val TAB_STOPWATCH = 2
        const val TAB_TIMER = 3
        private const val REQ_NOTIFICATIONS = 1
    }
}
