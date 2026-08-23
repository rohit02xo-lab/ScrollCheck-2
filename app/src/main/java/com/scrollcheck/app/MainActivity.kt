package com.scrollcheck.app

import android.app.Activity
import android.app.AlertDialog
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : Activity() {

    private lateinit var root: LinearLayout

    private val prefs by lazy {
        getSharedPreferences("scrollcheck", Context.MODE_PRIVATE)
    }

    private var dailyGoal = 60L

    data class AppInfo(
        val name: String,
        val packageName: String,
        val icon: String
    )

    private val apps = listOf(
        AppInfo("Instagram", "com.instagram.android", "IG"),
        AppInfo("YouTube", "com.google.android.youtube", "YT"),
        AppInfo("WhatsApp", "com.whatsapp", "WA"),
        AppInfo("X", "com.twitter.android", "X")
    )

    private val navy = Color.rgb(20, 28, 45)
    private val purple = Color.rgb(92, 88, 230)
    private val pageBackground = Color.rgb(246, 247, 251)
    private val secondary = Color.rgb(105, 112, 130)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        dailyGoal = prefs.getLong("daily_goal", 60L)

        buildUi()
    }

    override fun onResume() {
        super.onResume()

        if (::root.isInitialized) {
            refreshDashboard()
        }
    }

    // =========================================================
    // REAL ANDROID USAGE DATA
    // =========================================================

    private fun hasUsageAccess(): Boolean {

        val appOps =
            getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager

        return appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        ) == AppOpsManager.MODE_ALLOWED
    }

    private fun startOfToday(): Long {

        val calendar = Calendar.getInstance()

        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        return calendar.timeInMillis
    }

    private fun todayUsage(packageName: String): Long {

        if (!hasUsageAccess()) return 0L

        val manager =
            getSystemService(Context.USAGE_STATS_SERVICE)
                    as UsageStatsManager

        val stats =
            manager.queryAndAggregateUsageStats(
                startOfToday(),
                System.currentTimeMillis()
            )

        return (
            stats[packageName]?.totalTimeInForeground ?: 0L
        ) / 60000L
    }

    /*
     * Android does not provide an exact number of finger swipes.
     * This counts real foreground/resume events instead.
     */
    private fun todaySessions(packageName: String): Int {

        if (!hasUsageAccess()) return 0

        val manager =
            getSystemService(Context.USAGE_STATS_SERVICE)
                    as UsageStatsManager

        val events =
            manager.queryEvents(
                startOfToday(),
                System.currentTimeMillis()
            )

        val event = UsageEvents.Event()

        var sessions = 0

        while (events.hasNextEvent()) {

            events.getNextEvent(event)

            if (
                event.packageName == packageName &&
                event.eventType ==
                UsageEvents.Event.ACTIVITY_RESUMED
            ) {
                sessions++
            }
        }

        return sessions
    }

    private fun usageForDay(day: Calendar): Long {

        if (!hasUsageAccess()) return 0L

        val start =
            day.clone() as Calendar

        start.set(Calendar.HOUR_OF_DAY, 0)
        start.set(Calendar.MINUTE, 0)
        start.set(Calendar.SECOND, 0)
        start.set(Calendar.MILLISECOND, 0)

        val end =
            start.clone() as Calendar

        end.add(Calendar.DAY_OF_YEAR, 1)

        val manager =
            getSystemService(Context.USAGE_STATS_SERVICE)
                    as UsageStatsManager

        val stats =
            manager.queryAndAggregateUsageStats(
                start.timeInMillis,
                end.timeInMillis
            )

        var total = 0L

        for (app in apps) {

            total += (
                stats[app.packageName]
                    ?.totalTimeInForeground
                    ?: 0L
            ) / 60000L
        }

        return total
    }

    // =========================================================
    // UI SETUP
    // =========================================================

    private fun buildUi() {

        root = LinearLayout(this).apply {

            orientation = LinearLayout.VERTICAL

            setPadding(
                dp(18),
                dp(24),
                dp(18),
                dp(35)
            )

            setBackgroundColor(pageBackground)
        }

        val scrollView = ScrollView(this)

        scrollView.setBackgroundColor(pageBackground)

        scrollView.addView(root)

        setContentView(scrollView)

        refreshDashboard()
    }

    // =========================================================
    // DASHBOARD
    // =========================================================

    private fun refreshDashboard() {

        root.removeAllViews()

        val usage =
            apps.associate {
                it.name to todayUsage(it.packageName)
            }

        val sessions =
            apps.associate {
                it.name to todaySessions(it.packageName)
            }

        val total = usage.values.sum()

        addHeader()

        if (!hasUsageAccess()) {
