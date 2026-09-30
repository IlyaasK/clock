package com.ilyaas.einkclock.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import com.ilyaas.einkclock.R
import com.ilyaas.einkclock.alarm.Scheduler
import com.ilyaas.einkclock.data.Prefs

/** Minimal settings: the one dial the ring feature needs, plus two status rows. */
class SettingsActivity : Activity() {

    private lateinit var windowRow: LinearLayout
    private lateinit var snoozeRow: LinearLayout
    private lateinit var statusColumn: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.WHITE)
            setPadding(Ui.dp(this@SettingsActivity, 16f), Ui.dp(this@SettingsActivity, 24f), Ui.dp(this@SettingsActivity, 16f), Ui.dp(this@SettingsActivity, 24f))
        }
        column.addView(Ui.text(this, getString(R.string.settings), 28f, Ui.BLACK, bold = true))
        column.addView(Ui.space(this, 16))
        column.addView(section(getString(R.string.im_awake_window)))
        windowRow = Ui.horizontal(this)
        column.addView(windowRow)
        column.addView(section(getString(R.string.default_snooze)))
        snoozeRow = Ui.horizontal(this)
        column.addView(snoozeRow)
        column.addView(section(""))
        statusColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(statusColumn)
        column.addView(Ui.space(this, 24))
        column.addView(
            Ui.button(this, getString(R.string.save), filled = true, sp = 20f, heightDp = 64).apply {
                setOnClickListener { finish() }
            },
        )
        setContentView(ScrollView(this).apply { setBackgroundColor(Ui.WHITE); addView(column, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) })
        render()
    }

    private fun section(title: String): View = Ui.text(this, title, 15f, Ui.GRAY).apply {
        setPadding(0, Ui.dp(this@SettingsActivity, 16f), 0, Ui.dp(this@SettingsActivity, 8f))
    }

    private fun render() {
        renderOptions(windowRow, WINDOW_OPTIONS, Prefs.of().imAwakeWindowMinutes) { minutes ->
            Prefs.of().imAwakeWindowMinutes = minutes
            render()
        }
        renderOptions(snoozeRow, SNOOZE_OPTIONS, Prefs.of().defaultSnoozeMinutes) { minutes ->
            Prefs.of().defaultSnoozeMinutes = minutes
            render()
        }
        statusColumn.removeAllViews()

        val pm = getSystemService(PowerManager::class.java)
        val batteryOk = pm.isIgnoringBatteryOptimizations(packageName)
        statusColumn.addView(
            statusRow(
                getString(R.string.battery_title),
                if (batteryOk) getString(R.string.battery_ok) else getString(R.string.battery_body),
                if (batteryOk) null else getString(R.string.battery_action),
            ) {
                runCatching {
                    startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            .setData(Uri.parse("package:$packageName")),
                    )
                }
                overridePendingTransition(0, 0)
            },
        )

        val overlay = Scheduler.canDrawOverlays(this)
        statusColumn.addView(
            statusRow(
                getString(R.string.overlay_title),
                if (overlay) getString(R.string.granted) else getString(R.string.overlay_body),
                if (overlay) null else getString(R.string.overlay_action),
            ) {
                runCatching {
                    startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                            .setData(Uri.parse("package:$packageName")),
                    )
                }
                overridePendingTransition(0, 0)
            },
        )

        val exact = Scheduler.canScheduleExact(this)
        statusColumn.addView(
            statusRow(
                getString(R.string.exact_alarms),
                if (exact) getString(R.string.granted) else getString(R.string.not_granted),
                if (exact) null else getString(R.string.grant),
            ) {
                runCatching { startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)) }
                overridePendingTransition(0, 0)
            },
        )

        val fsi = Scheduler.canUseFullScreenIntent(this)
        statusColumn.addView(
            statusRow(
                getString(R.string.full_screen_intent),
                if (fsi) getString(R.string.granted) else getString(R.string.not_granted),
                if (fsi) null else getString(R.string.grant),
            ) {
                runCatching {
                    startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).setData(Uri.parse("package:$packageName")))
                }
                overridePendingTransition(0, 0)
            },
        )
    }

    private fun renderOptions(row: LinearLayout, options: IntArray, current: Int, onPick: (Int) -> Unit) {
        row.removeAllViews()
        for (option in options) {
            row.addView(
                Ui.chip(this, getString(R.string.minutes_format, option), current == option, heightDp = 48).apply {
                    layoutParams = LinearLayout.LayoutParams(0, Ui.dp(this@SettingsActivity, 48f), 1f).apply {
                        rightMargin = Ui.dp(this@SettingsActivity, 4f)
                    }
                    setOnClickListener { onPick(option) }
                },
            )
        }
    }

    private fun statusRow(title: String, body: String, action: String?, onClick: () -> Unit): View {
        val row = Ui.horizontal(this).apply { setPadding(0, Ui.dp(this@SettingsActivity, 8f), 0, Ui.dp(this@SettingsActivity, 8f)) }
        val text = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(Ui.text(this@SettingsActivity, title, 18f, Ui.BLACK))
            addView(Ui.text(this@SettingsActivity, body, 14f, Ui.GRAY))
        }
        row.addView(text, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (action != null) {
            row.addView(
                Ui.button(this, action, filled = false, sp = 16f, heightDp = 48).apply {
                    layoutParams = LinearLayout.LayoutParams(Ui.dp(this@SettingsActivity, 110f), Ui.dp(this@SettingsActivity, 48f))
                    setOnClickListener { onClick() }
                },
            )
        }
        return row
    }

    companion object {
        private val WINDOW_OPTIONS = intArrayOf(5, 10, 15)
        private val SNOOZE_OPTIONS = intArrayOf(5, 10, 15, 20, 30)
    }
}
