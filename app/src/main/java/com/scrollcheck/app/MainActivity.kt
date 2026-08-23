package com.scrollcheck.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.AppOpsManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
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
        val shortName: String,
        val iconColor: Int
    )

    data class UsageResult(
        val minutes: Long
    )

    private val trackedApps = listOf(
        AppInfo(
            "Instagram",
            "com.instagram.android",
            "IG",
            Color.rgb(220, 70, 120)
        ),
        AppInfo(
            "YouTube",
            "com.google.android.youtube",
            "YT",
            Color.rgb(220, 45, 45)
        ),
        AppInfo(
            "WhatsApp",
            "com.whatsapp",
            "WA",
            Color.rgb(35, 175, 105)
        ),
        AppInfo(
            "X",
            "com.twitter.android",
            "X",
            Color.rgb(25, 30, 35)
        )
    )

    private val pageBackground = Color.rgb(246, 247, 251)
    private val navy = Color.rgb(24, 30, 46)
    private val purple = Color.rgb(91, 88, 230)
    private val lightPurple = Color.rgb(239, 238, 255)
    private val gray = Color.rgb(105, 112, 130)
    private val white = Color.WHITE
    private val divider = Color.rgb(230, 232, 238)
    private val orange = Color.rgb(235, 145, 50)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        dailyGoal = prefs.getLong("daily_goal", 60L)

        createNotificationChannels()
        requestNotificationPermission()

        buildScreen()
    }

    override fun onResume() {
        super.onResume()

        if (::root.isInitialized) {
            refreshDashboard()
        }
    }

    // =========================================================
    // SCREEN
    // =========================================================

    private fun buildScreen() {

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL

            setPadding(
                dp(18),
                dp(24),
                dp(18),
                dp(30)
            )

            setBackgroundColor(pageBackground)
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(pageBackground)
            addView(root)
        }

        setContentView(scroll)

        refreshDashboard()
    }

    // =========================================================
    // DASHBOARD
    // =========================================================

    private fun refreshDashboard() {

        root.removeAllViews()

        addHeader()

        if (!hasUsageAccess()) {
            addAccessCard()
            return
        }

        val now = System.currentTimeMillis()

        val usage = trackedApps.associateWith { app ->
            getUsageForRange(
                app.packageName,
                startOfToday(),
                now
            )
        }

        val total = usage.values.sumOf { it.minutes }

        addTodayCard(total)

        addSectionTitle("Tracked apps")
        addTrackedApps(usage)

        addSectionTitle("App goals")
        addAppGoalsCard(usage)

        addSectionTitle("7-day usage")
        addSevenDayUsageCard()

        addSectionTitle("Usage trend")
        addTrendCard()

        addSectionTitle("🌙 Late-night usage")
        addLateNightCard()

        addSectionTitle("Daily goal")
        addGoalCard(total)

        addSectionTitle("Scroll Balance")
        addScoreCard(total)

        addSectionTitle("Most used")
        addMostUsedCard(usage)

        addSectionTitle("Break")
        addBreakCard()

        addAccuracyCard()

        addRefreshButton()

        addFooter()
    }

    // =========================================================
    // HEADER
    // =========================================================

    private fun addHeader() {

        addText(
            "SCROLLCHECK",
            13,
            purple,
            true
        )

        addText(
            "Take control of your scroll.",
            29,
            navy,
            true
        )

        addText(
            "Track  •  Understand  •  Improve  •  Reward",
            14,
            gray,
            false
        )

        space(12)
    }

    // =========================================================
    // USAGE ACCESS
    // =========================================================

    private fun hasUsageAccess(): Boolean {

        val appOps =
            getSystemService(Context.APP_OPS_SERVICE)
                as AppOpsManager

        return appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        ) == AppOpsManager.MODE_ALLOWED
    }

    private fun addAccessCard() {

        val card = createCard(white)

        addCardTitle(
            card,
            "🔐 Usage access required"
        )

        addCardBody(
            card,
            "ScrollCheck needs Android Usage Access to read actual foreground app usage."
        )

        val button = createButton(
            "Grant Usage Access"
        )

        button.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            )
        }

        card.addView(button)

        addCard(card)
    }

    // =========================================================
    // DATE / TIME
    // =========================================================

    private fun startOfDay(daysAgo: Int): Long {

        val calendar = java.util.Calendar.getInstance()

        calendar.add(
            java.util.Calendar.DAY_OF_YEAR,
            -daysAgo
        )

        calendar.set(
            java.util.Calendar.HOUR_OF_DAY,
            0
        )
        calendar.set(
            java.util.Calendar.MINUTE,
            0
        )
        calendar.set(
            java.util.Calendar.SECOND,
            0
        )
        calendar.set(
            java.util.Calendar.MILLISECOND,
            0
        )

        return calendar.timeInMillis
    }

    private fun startOfToday(): Long {
        return startOfDay(0)
    }

    // =========================================================
    // ANDROID USAGE DATA
    // =========================================================

    private fun getUsageForRange(
        targetPackage: String,
        start: Long,
        end: Long
    ): UsageResult {

        if (!hasUsageAccess()) {
            return UsageResult(0L)
        }

        if (end <= start) {
            return UsageResult(0L)
        }

        val manager =
            getSystemService(Context.USAGE_STATS_SERVICE)
                as UsageStatsManager

        /*
         * Android calculates foreground usage for us.
         *
         * We deliberately do NOT reconstruct sessions from
         * UsageEvents because that can double-count activity.
         */
        val stats =
            manager.queryAndAggregateUsageStats(
                start,
                end
            )

        val appStats = stats[targetPackage]

        val milliseconds =
            appStats?.totalTimeInForeground ?: 0L

        /*
         * Keep the value in complete minutes.
         * No fake rounding is added.
         */
        val minutes =
            milliseconds / 60_000L

        return UsageResult(minutes)
    }

    // =========================================================
    // TODAY
    // =========================================================

    private fun addTodayCard(total: Long) {

        val card = createCard(navy)

        addSmallText(
            card,
            "TODAY'S ACTUAL TRACKED TIME"
        )

        val time = TextView(this).apply {

            text = formatTime(total)
            textSize = 40f

            setTextColor(Color.WHITE)

            setTypeface(
                typeface,
                Typeface.BOLD
            )

            setPadding(
                0,
                dp(6),
                0,
                dp(4)
            )
        }

        card.addView(time)

        val status =
            if (total <= dailyGoal) {
                "✓ Within your goal"
            } else {
                "⚠ Above your goal"
            }

        val statusView = TextView(this).apply {

            text = status
            textSize = 14f

            setTextColor(
                Color.rgb(205, 210, 220)
            )
        }

        card.addView(statusView)

        addCardBody(
            card,
            "Daily goal: ${formatTime(dailyGoal)}"
        )

        addCard(card)
    }

    // =========================================================
    // TRACKED APPS
    // =========================================================

    private fun addTrackedApps(
        usage: Map<AppInfo, UsageResult>
    ) {

        val card = createCard(white)

        val sorted =
            trackedApps.sortedByDescending {
                usage[it]?.minutes ?: 0L
            }

        sorted.forEachIndexed { index, app ->

            val result =
                usage[app] ?: UsageResult(0L)

            addAppRow(
                card,
                app,
                result
            )

            if (index < sorted.lastIndex) {
                addDivider(card)
            }
        }

        addCard(card)
    }

    private fun addAppRow(
        parent: LinearLayout,
        app: AppInfo,
        result: UsageResult
    ) {

        val row = LinearLayout(this).apply {

            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            setPadding(
                0,
                dp(13),
                0,
                dp(13)
            )
        }

        val icon = TextView(this).apply {

            text = app.shortName
            textSize = 12f
            gravity = Gravity.CENTER

            setTextColor(Color.WHITE)

            setTypeface(
                typeface,
                Typeface.BOLD
            )

            background =
                rounded(
                    app.iconColor,
                    dp(14)
                )

            layoutParams =
                LinearLayout.LayoutParams(
                    dp(46),
                    dp(46)
                )
        }

        row.addView(icon)

        val information =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(12),
                    0,
                    dp(6),
                    0
                )

                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
            }

        val name = TextView(this).apply {

            text = app.name
            textSize = 17f

            setTextColor(navy)

            setTypeface(
                typeface,
                Typeface.BOLD
            )
        }

        information.addView(name)

        val subtitle = TextView(this).apply {

            text = "Android foreground time"
            textSize = 12f

            setTextColor(gray)

            setPadding(
                0,
                dp(3),
                0,
                0
            )
        }

        information.addView(subtitle)

        row.addView(information)

        val time = TextView(this).apply {

            text = formatTime(result.minutes)
            textSize = 16f

            setTextColor(navy)

            setTypeface(
                typeface,
                Typeface.BOLD
            )
        }

        row.addView(time)

        parent.addView(row)
    }

    // =========================================================
    // APP GOALS
    // =========================================================

    private fun appGoalKey(packageName: String): String {
        return "goal_$packageName"
    }

    private fun getAppGoal(app: AppInfo): Long {
        return prefs.getLong(
            appGoalKey(app.packageName),
            30L
        )
    }

    private fun setAppGoal(
        app: AppInfo,
        value: Long
    ) {

        prefs.edit()
            .putLong(
                appGoalKey(app.packageName),
                value
            )
            .apply()
    }

    private fun addAppGoalsCard(
        usage: Map<AppInfo, UsageResult>
    ) {

        val card = createCard(white)

        trackedApps.forEachIndexed { index, app ->

            val result =
                usage[app] ?: UsageResult(0L)

            val goal = getAppGoal(app)

            val row =
                LinearLayout(this).apply {

                    orientation =
                        LinearLayout.HORIZONTAL

                    gravity =
                        Gravity.CENTER_VERTICAL

                    setPadding(
                        0,
                        dp(7),
                        0,
                        dp(7)
                    )
                }

            val info =
                LinearLayout(this).apply {

                    orientation =
                        LinearLayout.VERTICAL

                    layoutParams =
                        LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f
                        )
                }

            val title = TextView(this).apply {

                text =
                    "${app.name} • ${formatTime(goal)} goal"

                textSize = 15f
                setTextColor(navy)

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )
            }

            info.addView(title)

            val statusText =
                if (result.minutes >= goal) {
                    "⚠ Goal reached"
                } else {
                    "${formatTime(goal - result.minutes)} remaining"
                }

            val status = TextView(this).apply {

                text = statusText
                textSize = 12f

                setTextColor(
                    if (result.minutes >= goal) {
                        orange
                    } else {
                        gray
                    }
                )
            }

            info.addView(status)

            row.addView(info)

            val button =
                createSmallButton("Edit")

            button.setOnClickListener {
                showAppGoalDialog(app)
            }

            row.addView(button)

            card.addView(row)

            if (index < trackedApps.lastIndex) {
                addDivider(card)
            }
        }

        addCard(card)

        checkGoalWarnings(usage)
    }

    private fun showAppGoalDialog(
        app: AppInfo
    ) {

        val choices = arrayOf(
            "15 minutes",
            "20 minutes",
            "30 minutes",
            "45 minutes",
            "60 minutes",
            "90 minutes",
            "120 minutes"
        )

        AlertDialog.Builder(this)
            .setTitle("${app.name} daily goal")
            .setItems(choices) { _, which ->

                val value =
                    when (which) {
                        0 -> 15L
                        1 -> 20L
                        2 -> 30L
                        3 -> 45L
                        4 -> 60L
                        5 -> 90L
                        else -> 120L
                    }

                setAppGoal(app, value)
                refreshDashboard()
            }
            .show()
    }

    // =========================================================
    // GOAL WARNINGS
    // =========================================================

    private fun checkGoalWarnings(
        usage: Map<AppInfo, UsageResult>
    ) {

        for (app in trackedApps) {

            val result =
                usage[app] ?: UsageResult(0L)

            val goal = getAppGoal(app)

            if (result.minutes >= goal) {

                val warningKey =
                    "warn_${app.packageName}_${startOfToday()}"

                if (!prefs.getBoolean(warningKey, false)) {

                    showGoalNotification(
                        app,
                        result.minutes,
                        goal
                    )

                    prefs.edit()
                        .putBoolean(
                            warningKey,
                            true
                        )
                        .apply()
                }
            }
        }
    }

    private fun showGoalNotification(
        app: AppInfo,
        used: Long,
        goal: Long
    ) {

        if (
            Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val manager =
            getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

        val intent =
            Intent(this, MainActivity::class.java)

        val pendingIntent =
            PendingIntent.getActivity(
                this,
                app.packageName.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        val notification =
            android.app.Notification.Builder(
                this,
                "scrollcheck_goals"
            )
                .setSmallIcon(
                    android.R.drawable.ic_dialog_info
                )
                .setContentTitle(
                    "${app.name} goal reached"
                )
                .setContentText(
                    "${formatTime(used)} used of ${formatTime(goal)}"
                )
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

        manager.notify(
            app.packageName.hashCode(),
            notification
        )
    }

    // =========================================================
    // 7 DAY USAGE
    // =========================================================

    private fun getSevenDayTotal(): List<Long> {

        val values = mutableListOf<Long>()

        for (daysAgo in 6 downTo 0) {

            val start =
                startOfDay(daysAgo)

            val end =
                if (daysAgo == 0) {
                    System.currentTimeMillis()
                } else {
                    startOfDay(daysAgo - 1)
                }

            var total = 0L

            for (app in trackedApps) {

                total += getUsageForRange(
                    app.packageName,
                    start,
                    end
                ).minutes
            }

            values.add(total)
        }

        return values
    }

    private fun addSevenDayUsageCard() {

        val card = createCard(white)

        val values = getSevenDayTotal()

        val max =
            (values.maxOrNull() ?: 1L)
                .coerceAtLeast(1L)

        val labels =
            listOf(
                "6d",
                "5d",
                "4d",
                "3d",
                "2d",
                "Y",
                "T"
            )

        values.forEachIndexed { index, minutes ->

            val barLength =
                (
                    minutes.toDouble() /
                        max.toDouble() *
                        22.0
                )
                    .roundToInt()
                    .coerceIn(
                        if (minutes > 0L) 1 else 0,
                        22
                    )

            val row =
                LinearLayout(this).apply {

                    orientation =
                        LinearLayout.HORIZONTAL

                    gravity =
                        Gravity.CENTER_VERTICAL

                    setPadding(
                        0,
                        dp(5),
                        0,
                        dp(5)
                    )
                }

            val label = TextView(this).apply {

                text = labels[index]
                textSize = 12f
                setTextColor(gray)
                gravity = Gravity.CENTER

                layoutParams =
                    LinearLayout.LayoutParams(
                        dp(32),
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
            }

            row.addView(label)

            val bar = TextView(this).apply {

                text = "█".repeat(barLength)
                textSize = 13f
                setTextColor(purple)

                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
            }

            row.addView(bar)

            val time = TextView(this).apply {

                text = formatTime(minutes)
                textSize = 12f
                setTextColor(navy)
                gravity = Gravity.RIGHT

                layoutParams =
                    LinearLayout.LayoutParams(
                        dp(62),
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
            }

            row.addView(time)

            card.addView(row)
        }

        addCardBody(
            card,
            "7-day total: ${formatTime(values.sum())}"
        )

        addCardBody(
            card,
            "Source: Android UsageStatsManager."
        )

        addCard(card)
    }

    // =========================================================
    // TREND
    // =========================================================

    private fun addTrendCard() {

        val card = createCard(lightPurple)

        val values = getSevenDayTotal()

        val today = values.last()
        val yesterday = values[values.lastIndex - 1]

        val average =
            values.average()
                .roundToInt()
                .toLong()

        val trend =
            when {
                today < yesterday ->
                    "📉 Less tracked usage today."

                today > yesterday ->
                    "📈 More tracked usage today."

                else ->
                    "➡️ Same tracked usage as yesterday."
            }

        addCardTitle(
            card,
            "📈 Usage trend"
        )

        addCardBody(
            card,
            trend
        )

        addCardBody(
            card,
            "Today: ${formatTime(today)}\n" +
                "Yesterday: ${formatTime(yesterday)}\n" +
                "7-day average: ${formatTime(average)}"
        )

        addCard(card)
    }

    // =========================================================
    // LATE NIGHT
    // =========================================================

    private fun lateNightStart(daysAgo: Int): Long {

        val calendar =
            java.util.Calendar.getInstance()

        calendar.add(
            java.util.Calendar.DAY_OF_YEAR,
            -daysAgo
        )

        calendar.set(
            java.util.Calendar.HOUR_OF_DAY,
            22
        )

        calendar.set(
            java.util.Calendar.MINUTE,
            0
        )

        calendar.set(
            java.util.Calendar.SECOND,
            0
        )

        calendar.set(
            java.util.Calendar.MILLISECOND,
            0
        )

        return calendar.timeInMillis
    }

    private fun lateNightEnd(daysAgo: Int): Long {

        val calendar =
            java.util.Calendar.getInstance()

        calendar.add(
            java.util.Calendar.DAY_OF_YEAR,
            -daysAgo
        )

        calendar.set(
            java.util.Calendar.HOUR_OF_DAY,
            23
        )

        calendar.set(
            java.util.Calendar.MINUTE,
            59
        )

        calendar.set(
            java.util.Calendar.SECOND,
            59
        )

        calendar.set(
            java.util.Calendar.MILLISECOND,
            999
        )

        return calendar.timeInMillis
    }

    private fun getLateNightTotal(
        daysAgo: Int
    ): Long {

        val start = lateNightStart(daysAgo)

        val end =
            if (daysAgo == 0) {
                minOf(
                    System.currentTimeMillis(),
                    lateNightEnd(daysAgo)
                )
            } else {
                lateNightEnd(daysAgo)
            }

        if (end <= start) {
            return 0L
        }

        return trackedApps.sumOf { app ->
            getUsageForRange(
                app.packageName,
                start,
                end
            ).minutes
        }
    }

    private fun addLateNightCard() {

        val card = createCard(white)

        val today =
            getLateNightTotal(0)

        addCardTitle(
            card,
            "After 10:00 PM"
        )

        addCardBody(
            card,
            "Actual tracked foreground usage from 10 PM to midnight."
        )

        val big = TextView(this).apply {

            text = formatTime(today)
            textSize = 32f

            setTextColor(navy)

            setTypeface(
                typeface,
                Typeface.BOLD
            )

            setPadding(
                0,
                dp(5),
                0,
                dp(10)
            )
        }

        card.addView(big)

        val end =
            minOf(
                System.currentTimeMillis(),
                lateNightEnd(0)
            )

        val byApp =
            if (end > lateNightStart(0)) {

                trackedApps.joinToString("\n") { app ->

                    val value =
                        getUsageForRange(
                            app.packageName,
                            lateNightStart(0),
                            end
                        ).minutes

                    "${app.name}: ${formatTime(value)}"
                }

            } else {
                "Today's 10 PM window has not started."
            }

        addCardBody(card, byApp)

        addCardBody(
            card,
            "Previous nights:\n" +
                "Yesterday: ${formatTime(getLateNightTotal(1))}\n" +
                "2 days ago: ${formatTime(getLateNightTotal(2))}\n" +
                "3 days ago: ${formatTime(getLateNightTotal(3))}"
        )

        addCard(card)
    }

    // =========================================================
    // DAILY GOAL
    // =========================================================

    private fun addGoalCard(total: Long) {

        val card = createCard(white)

        val percentage =
            if (dailyGoal > 0L) {
                (
                    total.toDouble() /
                        dailyGoal.toDouble() *
                        100.0
                ).roundToInt()
            } else {
                0
            }

        val filled =
            (percentage / 5)
                .coerceIn(0, 20)

        addCardTitle(
            card,
            "🎯 Daily goal"
        )

        addCardBody(
            card,
            "${formatTime(total)} used of ${formatTime(dailyGoal)}"
        )

        val progress = TextView(this).apply {

            text =
                "█".repeat(filled) +
                    "░".repeat(20 - filled)

            textSize = 12f
            setTextColor(purple)

            setPadding(
                0,
                dp(5),
                0,
                dp(8)
            )
        }

        card.addView(progress)

        val button =
            createButton("Change goal")

        button.setOnClickListener {
            showGoalDialog()
        }

        card.addView(button)

        addCard(card)
    }

    private fun showGoalDialog() {

        val choices =
            arrayOf(
                "30 minutes",
                "45 minutes",
                "60 minutes",
                "90 minutes",
                "120 minutes"
            )

        AlertDialog.Builder(this)
            .setTitle("Choose daily goal")
            .setItems(choices) { _, which ->

                dailyGoal =
                    when (which) {
                        0 -> 30L
                        1 -> 45L
                        2 -> 60L
                        3 -> 90L
                        else -> 120L
                    }

                prefs.edit()
                    .putLong(
                        "daily_goal",
                        dailyGoal
                    )
                    .apply()

                refreshDashboard()
            }
            .show()
    }

    // =========================================================
    // SCORE
    // =========================================================

    private fun calculateScore(total: Long): Int {

        if (total <= dailyGoal) {
            return 100
        }

        val excess =
            total - dailyGoal

        return (
            100.0 -
                excess.toDouble() * 0.35
            )
            .roundToInt()
            .coerceIn(0, 100)
    }

    private fun addScoreCard(total: Long) {

        val card = createCard(white)

        val score = calculateScore(total)

        val number = TextView(this).apply {

            text = score.toString()
            textSize = 42f
            gravity = Gravity.CENTER

            setTextColor(navy)

            setTypeface(
                typeface,
                Typeface.BOLD
            )
        }

        card.addView(number)

        val outOf = TextView(this).apply {

            text = "/ 100"
            textSize = 14f
            gravity = Gravity.CENTER

            setTextColor(gray)
        }

        card.addView(outOf)

        val status =
            when {
                score >= 80 -> "🟢 Excellent"
                score >= 60 -> "🟡 Good"
                score >= 40 -> "🟠 Needs attention"
                else -> "🔴 Take a break"
            }

        val statusView = TextView(this).apply {

            text = status
            textSize = 15f
            gravity = Gravity.CENTER

            setTextColor(gray)

            setPadding(
                0,
                dp(8),
                0,
                0
            )
        }

        card.addView(statusView)

        addCard(card)
    }

    // =========================================================
    // MOST USED
    // =========================================================

    private fun addMostUsedCard(
        usage: Map<AppInfo, UsageResult>
    ) {

        val app =
            trackedApps.maxByOrNull {
                usage[it]?.minutes ?: 0L
            } ?: return

        val result =
            usage[app] ?: UsageResult(0L)

        val card =
            createCard(lightPurple)

        addCardTitle(
            card,
            "${app.shortName}  ${app.name}"
        )

        addCardBody(
            card,
            "Most used tracked app today • ${formatTime(result.minutes)}"
        )

        addCard(card)
    }

    // =========================================================
    // BREAK TIMER
    // =========================================================

    private fun addBreakCard() {

        val card = createCard(white)

        addCardTitle(
            card,
            "🧘 5-minute reset"
        )

        addCardBody(
            card,
            "Start a background break timer. The timer continues while ScrollCheck is closed."
        )

        val button =
            createButton(
                "Start 5-minute break"
            )

        button.setOnClickListener {

            val intent =
                Intent(
                    this,
                    BreakTimerService::class.java
                ).apply {
                    action =
                        BreakTimerService.ACTION_START
                }

            try {

                if (
                    Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.O
                ) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }

                Toast.makeText(
                    this,
                    "5-minute break started",
                    Toast.LENGTH_SHORT
                ).show()

            } catch (exception: Exception) {

                Toast.makeText(
                    this,
                    "Unable to start break timer.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        card.addView(button)

        addCard(card)
    }

    // =========================================================
    // ACCURACY
    // =========================================================

    private fun addAccuracyCard() {

        val card = createCard(white)

        addCardTitle(
            card,
            "ℹ️ Data source"
        )

        addCardBody(
            card,
            "Usage is read directly from Android UsageStatsManager."
        )

        addCardBody(
            card,
            "No random values, sample values, or estimated swipe time are generated."
        )

        addCardBody(
            card,
            "Important: ScrollCheck can only measure apps included in its tracked-app list."
        )

        addCard(card)
    }

    // =========================================================
    // REFRESH
    // =========================================================

    private fun addRefreshButton() {

        val button =
            createButton(
                "↻  Refresh actual usage"
            )

        button.setOnClickListener {

            refreshDashboard()

            Toast.makeText(
                this,
                "Actual usage refreshed",
                Toast.LENGTH_SHORT
            ).show()
        }

        root.addView(
            button,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52)
            ).apply {
                setMargins(
                    0,
                    dp(8),
                    0,
                    dp(8)
                )
            }
        )
    }

    // =========================================================
    // NOTIFICATION CHANNELS
    // =========================================================

    private fun createNotificationChannels() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            val manager =
                getSystemService(
                    Context.NOTIFICATION_SERVICE
                ) as NotificationManager

            val goals =
                NotificationChannel(
                    "scrollcheck_goals",
                    "ScrollCheck Goals",
                    NotificationManager.IMPORTANCE_DEFAULT
                )

            goals.description =
                "ScrollCheck app goal warnings."

            manager.createNotificationChannel(goals)

            val breakChannel =
                NotificationChannel(
                    "scrollcheck_break",
                    "ScrollCheck Break Timer",
                    NotificationManager.IMPORTANCE_LOW
                )

            breakChannel.description =
                "Background break timer."

            manager.createNotificationChannel(
                breakChannel
            )
        }
    }

    private fun requestNotificationPermission() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {

            if (
                checkSelfPermission(
                    Manifest.permission.POST_NOTIFICATIONS
                ) !=
                PackageManager.PERMISSION_GRANTED
            ) {

                requestPermissions(
                    arrayOf(
                        Manifest.permission.POST_NOTIFICATIONS
                    ),
                    501
                )
            }
        }
    }

    // =========================================================
    // UI HELPERS
    // =========================================================

    private fun createCard(
        color: Int
    ): LinearLayout {

        return LinearLayout(this).apply {

            orientation =
                LinearLayout.VERTICAL

            setPadding(
                dp(18),
                dp
