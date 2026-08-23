package com.scrollcheck.app

import android.app.Activity
import android.app.AlertDialog
import android.app.AppOpsManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.usage.UsageEvents
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : Activity() {

    private lateinit var root: LinearLayout

    private val prefs by lazy {
        getSharedPreferences(
            "scrollcheck",
            Context.MODE_PRIVATE
        )
    }

    private var dailyGoal = 60L

    // =========================================================
    // DATA MODELS
    // =========================================================

    data class AppInfo(
        val name: String,
        val packageName: String,
        val shortName: String,
        val iconColor: Int
    )

    data class UsageResult(
        val minutes: Long,
        val sessions: Int
    )

    // =========================================================
    // TRACKED APPS
    // =========================================================

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

    // =========================================================
    // COLORS
    // =========================================================

    private val pageBackground =
        Color.rgb(246, 247, 251)

    private val navy =
        Color.rgb(24, 30, 46)

    private val purple =
        Color.rgb(91, 88, 230)

    private val lightPurple =
        Color.rgb(239, 238, 255)

    private val gray =
        Color.rgb(105, 112, 130)

    private val white =
        Color.WHITE

    private val divider =
        Color.rgb(230, 232, 238)

    private val orange =
        Color.rgb(235, 145, 50)

    // =========================================================
    // ACTIVITY
    // =========================================================

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        dailyGoal =
            prefs.getLong(
                "daily_goal",
                60L
            )

        createNotificationChannels()

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

        root =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(18),
                    dp(24),
                    dp(18),
                    dp(30)
                )

                setBackgroundColor(
                    pageBackground
                )
            }

        val scroll =
            ScrollView(this).apply {

                isFillViewport = true

                setBackgroundColor(
                    pageBackground
                )

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

        /*
         * IMPORTANT:
         *
         * Every section uses this SAME usage map.
         *
         * This prevents different parts of ScrollCheck
         * from calculating different values.
         */

        val usage =
            trackedApps.associateWith { app ->

                getTodayUsage(
                    app.packageName
                )
            }

        val total =
            usage.values.sumOf {
                it.minutes
            }

        // -----------------------------------------------------
        // TODAY
        // -----------------------------------------------------

        addTodayCard(total)

        // -----------------------------------------------------
        // TRACKED APPS
        // -----------------------------------------------------

        addSectionTitle(
            "Tracked apps"
        )

        addTrackedApps(usage)

        // -----------------------------------------------------
        // APP GOALS
        // -----------------------------------------------------

        addSectionTitle(
            "App goals"
        )

        addAppGoalsCard(usage)

        // -----------------------------------------------------
        // 7 DAY USAGE
        // -----------------------------------------------------

        addSectionTitle(
            "7-day usage"
        )

        addSevenDayUsageCard()

        // -----------------------------------------------------
        // TREND
        // -----------------------------------------------------

        addSectionTitle(
            "Usage trend"
        )

        addTrendCard()

        // -----------------------------------------------------
        // LATE NIGHT
        // -----------------------------------------------------

        addSectionTitle(
            "🌙 Late-night usage"
        )

        addLateNightCard()

        // -----------------------------------------------------
        // DAILY GOAL
        // -----------------------------------------------------

        addSectionTitle(
            "Daily goal"
        )

        addGoalCard(total)

        // -----------------------------------------------------
        // SCORE
        // -----------------------------------------------------

        addSectionTitle(
            "Scroll Balance"
        )

        addScoreCard(total)

        // -----------------------------------------------------
        // MOST USED
        // -----------------------------------------------------

        addSectionTitle(
            "Most used"
        )

        addMostUsedCard(usage)

        // -----------------------------------------------------
        // BREAK
        // -----------------------------------------------------

        addSectionTitle(
            "Break"
        )

        addBreakCard()

        // -----------------------------------------------------
        // ACCURACY
        // -----------------------------------------------------

        addAccuracyCard()

        // -----------------------------------------------------
        // REFRESH
        // -----------------------------------------------------

        addRefreshButton()

        // -----------------------------------------------------
        // FOOTER
        // -----------------------------------------------------

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

        addText(
            SimpleDateFormat(
                "EEEE, d MMMM",
                Locale.getDefault()
            ).format(Date()),
            13,
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
            getSystemService(
                Context.APP_OPS_SERVICE
            ) as AppOpsManager

        return appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        ) == AppOpsManager.MODE_ALLOWED
    }

    private fun addAccessCard() {

        val card =
            createCard(white)

        addCardTitle(
            card,
            "🔐 Usage access required"
        )

        addCardBody(
            card,
            "Allow ScrollCheck to read Android app usage so it can calculate actual foreground time."
        )

        val button =
            createButton(
                "Grant Usage Access"
            )

        button.setOnClickListener {

            startActivity(
                Intent(
                    Settings.ACTION_USAGE_ACCESS_SETTINGS
                )
            )
        }

        card.addView(button)

        addCard(card)
    }

    // =========================================================
    // START OF TODAY
    // =========================================================

    private fun startOfToday(): Long {

        val calendar =
            Calendar.getInstance()

        calendar.set(
            Calendar.HOUR_OF_DAY,
            0
        )

        calendar.set(
            Calendar.MINUTE,
            0
        )

        calendar.set(
            Calendar.SECOND,
            0
        )

        calendar.set(
            Calendar.MILLISECOND,
            0
        )

        return calendar.timeInMillis
    }

    // =========================================================
    // ACCURATE USAGE
    // =========================================================

    private fun getTodayUsage(
        targetPackage: String
    ): UsageResult {

        if (!hasUsageAccess()) {

            return UsageResult(
                0L,
                0
            )
        }

        val usageManager =
            getSystemService(
                Context.USAGE_STATS_SERVICE
            ) as UsageStatsManager

        val start =
            startOfToday()

        val end =
            System.currentTimeMillis()

        /*
         * Query from midnight until now.
         */
        val events =
            usageManager.queryEvents(
                start,
                end
            )

        val event =
            UsageEvents.Event()

        var foregroundStart =
            -1L

        var totalMilliseconds =
            0L

        var sessions =
            0

        while (
            events.hasNextEvent()
        ) {

            events.getNextEvent(event)

            /*
             * Ignore all other applications.
             */
            if (
                event.packageName !=
                targetPackage
            ) {
                continue
            }

            when (event.eventType) {

                /*
                 * Modern Android.
                 */
                UsageEvents.Event.ACTIVITY_RESUMED -> {

                    if (
                        foregroundStart < 0L
                    ) {

                        foregroundStart =
                            event.timeStamp

                        sessions++
                    }
                }

                /*
                 * Older Android.
                 */
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {

                    if (
                        foregroundStart < 0L
                    ) {

                        foregroundStart =
                            event.timeStamp

                        sessions++
                    }
                }

                /*
                 * Modern Android.
                 */
                UsageEvents.Event.ACTIVITY_PAUSED -> {

                    if (
                        foregroundStart >= 0L
                    ) {

                        val duration =
                            event.timeStamp -
                                foregroundStart

                        if (
                            duration > 0L
                        ) {

                            totalMilliseconds +=
                                duration
                        }

                        foregroundStart =
                            -1L
                    }
                }

                /*
                 * Modern Android fallback.
                 */
                UsageEvents.Event.ACTIVITY_STOPPED -> {

                    if (
                        foregroundStart >= 0L
                    ) {

                        val duration =
                            event.timeStamp -
                                foregroundStart

                        if (
                            duration > 0L
                        ) {

                            totalMilliseconds +=
                                duration
                        }

                        foregroundStart =
                            -1L
                    }
                }

                /*
                 * Older Android.
                 */
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {

                    if (
                        foregroundStart >= 0L
                    ) {

                        val duration =
                            event.timeStamp -
                                foregroundStart

                        if (
                            duration > 0L
                        ) {

                            totalMilliseconds +=
                                duration
                        }

                        foregroundStart =
                            -1L
                    }
                }
            }
        }

        /*
         * If the application is currently open,
         * close the active session at the current time.
         */
        if (
            foregroundStart >= 0L
        ) {

            val duration =
                end -
                    foregroundStart

            if (
                duration > 0L
            ) {

                totalMilliseconds +=
                    duration
            }
        }

        /*
         * Convert milliseconds to minutes.
         *
         * We round only once, after calculating the
         * complete foreground duration.
         */
        val minutes =
            (
                totalMilliseconds /
                    60000.0
            )
                .roundToInt()
                .toLong()

        return UsageResult(
            minutes,
            sessions
        )
    }

    // =========================================================
    // TODAY CARD
    // =========================================================

    private fun addTodayCard(
        total: Long
    ) {

        val card =
            createCard(navy)

        addSmallText(
            card,
            "TODAY'S ACTUAL TRACKED TIME"
        )

        val time =
            TextView(this).apply {

                text =
                    formatTime(total)

                textSize = 40f

                setTextColor(
                    Color.WHITE
                )

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
            if (
                total <= dailyGoal
            ) {

                "✓ Within your goal"

            } else {

                "⚠ Above your goal"
            }

        val statusView =
            TextView(this).apply {

                text =
                    status

                textSize = 14f

                setTextColor(
                    Color.rgb(
                        205,
                        210,
                        220
                    )
                )
            }

        card.addView(statusView)

        addCardBody(
            card,
            "Daily goal: ${
                formatTime(dailyGoal)
            }"
        )

        addCard(card)
    }

    // =========================================================
    // TRACKED APPS
    // =========================================================

    private fun addTrackedApps(
        usage: Map<AppInfo, UsageResult>
    ) {

        val card =
            createCard(white)

        val sorted =
            trackedApps.sortedByDescending {

                usage[it]?.minutes
                    ?: 0L
            }

        sorted.forEachIndexed {
                index,
                app ->

            addAppRow(
                card,
                app,
                usage[app]
                    ?: UsageResult(
                        0L,
                        0
                    )
            )

            if (
                index <
                sorted.lastIndex
            ) {

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

        val row =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL

                setPadding(
                    0,
                    dp(13),
                    0,
                    dp(13)
                )
            }

        val icon =
            TextView(this).apply {

                text =
                    app.shortName

                textSize = 12f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    Color.WHITE
                )

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

        val name =
            TextView(this).apply {

                text =
                    app.name

                textSize = 17f

                setTextColor(navy)

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )
            }

        information.addView(name)

        val sessionText =
            TextView(this).apply {

                text =
                    if (
                        result.sessions == 1
                    ) {

                        "1 session today"

                    } else {

                        "${result.sessions} sessions today"
                    }

                textSize = 12f

                setTextColor(gray)

                setPadding(
                    0,
                    dp(3),
                    0,
                    0
                )
            }

        information.addView(sessionText)

        row.addView(information)

        val time =
            TextView(this).apply {

                text =
                    formatTime(
                        result.minutes
                    )

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

    private fun appGoalKey(
        packageName: String
    ): String {

        return "goal_$packageName"
    }

    private fun getAppGoal(
        app: AppInfo
    ): Long {

        return prefs.getLong(
            appGoalKey(
                app.packageName
            ),
            30L
        )
    }

    private fun setAppGoal(
        app: AppInfo,
        value: Long
    ) {

        prefs.edit()
            .putLong(
                appGoalKey(
                    app.packageName
                ),
                value
            )
            .apply()
    }

    private fun addAppGoalsCard(
        usage: Map<AppInfo, UsageResult>
    ) {

        val card =
            createCard(white)

        trackedApps.forEachIndexed {
                index,
                app ->

            val result =
                usage[app]
                    ?: UsageResult(
                        0L,
                        0
                    )

            val goal =
                getAppGoal(app)

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

            val title =
                TextView(this).apply {

                    text =
                        "${app.name} • ${
                            formatTime(goal)
                        } goal"

                    textSize = 15f

                    setTextColor(navy)

                    setTypeface(
                        typeface,
                        Typeface.BOLD
                    )
                }

            info.addView(title)

            val status =
                TextView(this).apply {

                    text =
                        if (
                            result.minutes >= goal
                        ) {

                            "⚠ Goal reached"

                        } else {

                            "${formatTime(
                                goal -
                                    result.minutes
                            )} remaining"
                        }

                    textSize = 12f

                    setTextColor(
                        if (
                            result.minutes >= goal
                        ) {

                            orange

                        } else {

                            gray
                        }
                    )
                }

            info.addView(status)

            row.addView(info)

            val button =
                createSmallButton(
                    "Edit"
                )

            button.setOnClickListener {

                showAppGoalDialog(
                    app
                )
            }

            row.addView(button)

            card.addView(row)

            if (
                index <
                trackedApps.lastIndex
            ) {

                addDivider(card)
            }
        }

        addCard(card)

        checkGoalWarnings(usage)
    }

    private fun showAppGoalDialog(
        app: AppInfo
    ) {

        val choices =
            arrayOf(
                "15 minutes",
                "20 minutes",
                "30 minutes",
                "45 minutes",
                "60 minutes",
                "90 minutes",
                "120 minutes"
            )

        AlertDialog.Builder(this)
            .setTitle(
                "${app.name} daily goal"
            )
            .setItems(
                choices
            ) { _, which ->

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

                setAppGoal(
                    app,
                    value
                )

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

        trackedApps.forEach { app ->

            val result =
                usage[app]
                    ?: UsageResult(
                        0L,
                        0
                    )

            val goal =
                getAppGoal(app)

            if (
                result.minutes >= goal
            ) {

                val warningKey =
                    "warn_${app.packageName}_${startOfToday()}"

                val alreadyShown =
                    prefs.getBoolean(
                        warningKey,
                        false
                    )

                if (!alreadyShown) {

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
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {

            if (
                checkSelfPermission(
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) !=
                PackageManager.PERMISSION_GRANTED
            ) {

                return
            }
        }

        val manager =
            getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

        val intent =
            Intent(
                this,
                MainActivity::class.java
            )

        val pendingIntent =
            PendingIntent.getActivity(
                this,
                100,
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
                    "${formatTime(used)} used of ${
                        formatTime(goal)
                    }"
                )
                .setContentIntent(
                    pendingIntent
                )
                .setAutoCancel(true)
                .build()

        manager.notify(
            app.packageName.hashCode(),
            notification
        )
    }

    // =========================================================
    // 7-DAY USAGE
    // =========================================================

    private fun getSevenDayTotal(): List<Long> {

        val values =
            mutableListOf<Long>()

        for (
            daysAgo in 6 downTo 0
        ) {

            val start =
                startOfDay(daysAgo)

            val end =
                if (
                    daysAgo == 0
                ) {

                    System.currentTimeMillis()

                } else {

                    startOfDay(
                        daysAgo - 1
                    )
                }

            var total =
                0L

            trackedApps.forEach { app ->

                total +=
                    getUsageForRangeEvents(
                        app.packageName,
                        start,
                        end
                    ).minutes
            }

            values.add(total)
        }

        return values
    }

    private fun startOfDay(
        daysAgo: Int
    ): Long {

        val calendar =
            Calendar.getInstance()

        calendar.add(
            Calendar.DAY_OF_YEAR,
            -daysAgo
        )

        calendar.set(
            Calendar.HOUR_OF_DAY,
            0
        )

        calendar.set(
            Calendar.MINUTE,
            0
        )

        calendar.set(
            Calendar.SECOND,
            0
        )

        calendar.set(
            Calendar.MILLISECOND,
            0
        )

        return calendar.timeInMillis
    }

    /*
     * Event-based calculation for historical ranges.
     *
     * Today's main number still uses getTodayUsage().
     */
    private fun getUsageForRangeEvents(
        targetPackage: String,
        requestedStart: Long,
        requestedEnd: Long
    ): UsageResult {

        if (
            !hasUsageAccess() ||
            requestedEnd <= requestedStart
        ) {

            return UsageResult(
                0L,
                0
            )
        }

        val manager =
            getSystemService(
                Context.USAGE_STATS_SERVICE
            ) as UsageStatsManager

        val queryStart =
            maxOf(
                0L,
                requestedStart -
                    24L * 60L * 60L * 1000L
            )

        val events =
            manager.queryEvents(
                queryStart,
                requestedEnd
            )

        val event =
            UsageEvents.Event()

        var foregroundStart =
            -1L

        var totalMilliseconds =
            0L

        var sessions =
            0

        while (
            events.hasNextEvent()
        ) {

            events.getNextEvent(event)

            if (
                event.packageName !=
                targetPackage
            ) {
                continue
            }

            val timestamp =
                event.timeStamp

            if (
                timestamp > requestedEnd
            ) {
                break
            }

            when (event.eventType) {

                UsageEvents.Event.ACTIVITY_RESUMED,
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {

                    if (
                        foregroundStart < 0L
                    ) {

                        foregroundStart =
                            timestamp

                        sessions++
                    }
                }

                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED,
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {

                    if (
                        foregroundStart >= 0L
                    ) {

                        val clippedStart =
                            maxOf(
                                foregroundStart,
                                requestedStart
                            )

                        val clippedEnd =
                            minOf(
                                timestamp,
                                requestedEnd
                            )

                        if (
                            clippedEnd >
                            clippedStart
                        ) {

                            totalMilliseconds +=
                                clippedEnd -
                                    clippedStart
                        }

                        foregroundStart =
                            -1L
                    }
                }
            }
        }

        if (
            foregroundStart >= 0L
        ) {

            val clippedStart =
                maxOf(
                    foregroundStart,
                    requestedStart
                )

            if (
                requestedEnd >
                clippedStart
            ) {

                totalMilliseconds +=
                    requestedEnd -
                        clippedStart
            }
        }

        val minutes =
            (
                totalMilliseconds /
                    60000.0
            )
                .roundToInt()
                .toLong()

        return UsageResult(
            minutes,
            sessions
        )
    }

    private fun addSevenDayUsageCard() {

        val card =
            createCard(white)

        val values =
            getSevenDayTotal()

        val max =
            (
                values.maxOrNull()
                    ?: 1L
            )
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

        values.forEachIndexed {
                index,
                minutes ->

            val barLength =
                (
                    minutes.toDouble() /
                        max.toDouble() *
                        22.0
                )
                    .roundToInt()
                    .coerceIn(
                        if (
                            minutes > 0L
                        ) {
                            1
                        } else {
                            0
                        },
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

            val label =
                TextView(this).apply {

                    text =
                        labels[index]

                    textSize = 12f

                    setTextColor(gray)

                    gravity =
                        Gravity.CENTER

                    layoutParams =
                        LinearLayout.LayoutParams(
                            dp(32),
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                }

            row.addView(label)

            val bar =
                TextView(this).apply {

                    text =
                        "█".repeat(
                            barLength
                        )

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

            val time =
                TextView(this).apply {

                    text =
                        formatTime(minutes)

                    textSize = 12f

                    setTextColor(navy)

                    gravity =
                        Gravity.RIGHT

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
            "7-day total: ${
                formatTime(
                    values.sum()
                )
            }"
        )

        addCardBody(
            card,
            "Historical values use the same foreground-event method."
        )

        addCard(card)
    }

    // =========================================================
    // TREND
    // =========================================================

    private fun addTrendCard() {

        val card =
            createCard(lightPurple)

        val values =
            getSevenDayTotal()

        if (
            values.size < 2
        ) {

            addCardBody(
                card,
                "Not enough data yet."
            )

            addCard(card)

            return
        }

        val today =
            values.last()

        val yesterday =
            values[
                values.lastIndex - 1
            ]

        val average =
            values
                .average()
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
            "Today: ${
                formatTime(today)
            }\n" +
                "Yesterday: ${
                    formatTime(yesterday)
                }\n" +
                "7-day average: ${
                    formatTime(average)
                }"
        )

        addCard(card)
    }

    // =========================================================
    // LATE NIGHT
    // =========================================================

    private fun lateNightStart(
        daysAgo: Int
    ): Long {

        val calendar =
            Calendar.getInstance()

        calendar.add(
            Calendar.DAY_OF_YEAR,
            -daysAgo
        )

        calendar.set(
            Calendar.HOUR_OF_DAY,
            22
        )

        calendar.set(
            Calendar.MINUTE,
            0
        )

        calendar.set(
            Calendar.SECOND,
            0
        )

        calendar.set(
            Calendar.MILLISECOND,
            0
        )

        return calendar.timeInMillis
    }

    private fun lateNightEnd(
        daysAgo: Int
    ): Long {

        val calendar =
            Calendar.getInstance()

        calendar.add(
            Calendar.DAY_OF_YEAR,
            -daysAgo
        )

        calendar.set(
            Calendar.HOUR_OF_DAY,
            23
        )

        calendar.set(
            Calendar.MINUTE,
            59
        )

        calendar.set(
            Calendar.SECOND,
            59
        )

        calendar.set(
            Calendar.MILLISECOND,
            999
        )

        return calendar.timeInMillis
    }

    private fun getLateNightTotal(
        daysAgo: Int
    ): Long {

        val start =
            lateNightStart(daysAgo)

        val end =
            if (
                daysAgo == 0
            ) {

                minOf(
                    System.currentTimeMillis(),
                    lateNightEnd(daysAgo)
                )

            } else {

                lateNightEnd(daysAgo)
            }

        if (
            end <= start
        ) {

            return 0L
        }

        var total =
            0L

        trackedApps.forEach { app ->

            total +=
                getUsageForRangeEvents(
                    app.packageName,
                    start,
                    end
                ).minutes
        }

        return total
    }

    private fun addLateNightCard() {

        val card =
            createCard(white)

        val today =
            getLateNightTotal(0)

        addCardTitle(
            card,
            "After 10:00 PM"
        )

        addCardBody(
            card,
            "Tracked usage between 10:00 PM and midnight."
        )

        val big =
            TextView(this).apply {

                text =
                    formatTime(today)

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
            trackedApps.joinToString(
                "\n"
            ) { app ->

                val value =
                    if (
                        end >
                        lateNightStart(0)
                    ) {

                        getUsageForRangeEvents(
                            app.packageName,
                            lateNightStart(0),
                            end
                        ).minutes

                    } else {

                        0L
                    }

                "${app.name}: ${
                    formatTime(value)
                }"
            }

        addCardBody(
            card,
            byApp
        )

        addCardBody(
            card,
            "Previous nights:\n" +
                "Yesterday: ${
                    formatTime(
                        getLateNightTotal(1)
                    )
                }\n" +
                "2 days ago: ${
                    formatTime(
                        getLateNightTotal(2)
                    )
                }\n" +
                "3 days ago: ${
                    formatTime(
                        getLateNightTotal(3)
                    )
                }"
        )

        addCard(card)
    }

    // =========================================================
    // DAILY GOAL
    // =========================================================

    private fun addGoalCard(
        total: Long
    ) {

        val card =
            createCard(white)

        val percentage =
            if (
                dailyGoal > 0L
            ) {

                (
                    total.toDouble() /
                        dailyGoal.toDouble() *
                        100.0
                )
                    .roundToInt()

            } else {

                0
            }

        val filled =
            (
                percentage / 5
            ).coerceIn(
                0,
                20
            )

        addCardTitle(
            card,
            "🎯 Daily goal"
        )

        addCardBody(
            card,
            "${formatTime(total)} used of ${
                formatTime(dailyGoal)
            }"
        )

        val progress =
            TextView(this).apply {

                text =
                    "█".repeat(filled) +
                        "░".repeat(
                            20 - filled
                        )

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
            createButton(
                "Change goal"
            )

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
            .setTitle(
                "Choose daily goal"
            )
            .setItems(
                choices
            ) { _, which ->

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

    private fun calculateScore(
        total: Long
    ): Int {

        if (
            total <= dailyGoal
        ) {

            return 100
        }

        val excess =
            total - dailyGoal

        return (
            100.0 -
                excess.toDouble() *
                0.35
            )
            .roundToInt()
            .coerceIn(
                0,
                100
            )
    }

    private fun addScoreCard(
        total: Long
    ) {

        val card =
            createCard(white)

        val score =
            calculateScore(total)

        val number =
            TextView(this).apply {

                text =
                    "$score"

                textSize = 42f

                gravity =
                    Gravity.CENTER

                setTextColor(navy)

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )
            }

        card.addView(number)

        val outOf =
            TextView(this).apply {

                text =
                    "/ 100"

                textSize = 14f

                gravity =
                    Gravity.CENTER

                setTextColor(gray)
            }

        card.addView(outOf)

        val status =
            when {

                score >= 80 ->
                    "🟢 Excellent"

                score >= 60 ->
                    "🟡 Good"

                score >= 40 ->
                    "🟠 Needs attention"

                else ->
                    "🔴 Take a break"
            }

        val statusView =
            TextView(this).apply {

                text =
                    status

                textSize = 15f

                gravity =
                    Gravity.CENTER

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

                usage[it]?.minutes
                    ?: 0L
            }
                ?: return

        val result =
            usage[app]
                ?: UsageResult(
                    0L,
                    0
                )

        val card =
            createCard(lightPurple)

        addCardTitle(
            card,
            "${app.shortName}  ${app.name}"
        )

        addCardBody(
            card,
            "Most used tracked app today • ${
                formatTime(result.minutes)
            }"
        )

        addCard(card)
    }

    // =========================================================
    // BREAK
    // =========================================================

    private fun addBreakCard() {

        val card =
            createCard(white)

        addCardTitle(
            card,
            "🧘 5-minute reset"
        )

        addCardBody(
            card,
            "Run a visible 5-minute break timer."
        )

        val button =
            createButton(
                "Start 5-minute break"
            )

        button.setOnClickListener {

            try {

                val intent =
                    Intent(
                        this,
                        BreakTimerService::class.java
                    ).apply {

                        action =
                            BreakTimerService.ACTION_START
                    }

                if (
                    Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.O
                ) {

                    startForegroundService(
                        intent
                    )

                } else {

                    startService(intent)
                }

                Toast.makeText(
                    this,
                    "5-minute break started",
                    Toast.LENGTH_SHORT
                ).show()

            } catch (
                e: Exception
            ) {

                Toast.makeText(
                    this,
                    "Could not start break timer",
                    Toast.LENGTH_SHORT
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

        val card =
            createCard(white)

        addCardTitle(
            card,
            "ℹ️ Data source"
        )

        addCardBody(
            card,
            "ScrollCheck measures foreground activity using Android UsageEvents."
        )

        addCardBody(
            card,
            "Today's app values are calculated from foreground sessions from midnight until now."
        )

        addCardBody(
            card,
            "ScrollCheck does not invent or randomly estimate usage time."
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
                "Usage data refreshed",
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
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.O
        ) {

            return
        }

        val manager =
            getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

        manager.createNotificationChannel(
            NotificationChannel(
                "scrollcheck_goals",
                "ScrollCheck Goals",
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )

        manager.createNotificationChannel(
            NotificationChannel(
                "scrollcheck_break",
                "ScrollCheck Break Timer",
                NotificationManager.IMPORTANCE_LOW
            )
        )
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
                dp(17),
                dp(18),
                dp(17)
            )

            background =
                rounded(
                    color,
                    dp(18)
                )

            elevation =
                dp(2).toFloat()
        }
    }

    private fun addCard(
        card: View
    ) {

        root.addView(
            card,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {

                setMargins(
                    0,
                    dp(4),
                    0,
                    dp(10)
                )
            }
        )
    }

    private fun addCardTitle(
        card: LinearLayout,
        value: String
    ) {

        val text =
            TextView(this).apply {

                this.text =
                    value

                textSize = 17f

                setTextColor(navy)

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )
            }

        card.addView(text)
    }

    private fun addCardBody(
        card: LinearLayout,
        value: String
    ) {

        val text =
            TextView(this).apply {

                this.text =
                    value

                textSize = 13f

                setTextColor(gray)

                setPadding(
                    0,
                    dp(6),
                    0,
                    dp(7)
                )
            }

        card.addView(text)
    }

    private fun addSmallText(
        card: LinearLayout,
        value: String
    ) {

        val text =
            TextView(this).apply {

                this.text =
                    value

                textSize = 12f

                setTextColor(
                    Color.rgb(
                        190,
                        196,
                        210
                    )
                )

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )
            }

        card.addView(text)
    }

    private fun addSectionTitle(
        value: String
    ) {

        val text =
            TextView(this).apply {

                this.text =
                    value

                textSize = 20f

                setTextColor(navy)

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )

                setPadding(
                    0,
                    dp(14),
                    0,
                    dp(7)
                )
            }

        root.addView(text)
    }

    private fun addDivider(
        parent: LinearLayout
    ) {

        parent.addView(
            View(this).apply {

                setBackgroundColor(
                    divider
                )
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1)
            )
        )
    }

    private fun createButton(
        value: String
    ): Button {

        return Button(this).apply {

            text =
                value

            textSize = 13f

            setTextColor(
                Color.WHITE
            )

            background =
                rounded(
                    purple,
                    dp(13)
                )

            stateListAnimator = null
        }
    }

    private fun createSmallButton(
        value: String
    ): Button {

        return Button(this).apply {

            text =
                value

            textSize = 11f

            setTextColor(
                Color.WHITE
            )

            background =
                rounded(
                    purple,
                    dp(10)
                )

            stateListAnimator = null

            setPadding(
                dp(8),
                0,
                dp(8),
                0
            )
        }
    }

    private fun addText(
        value: String,
        size: Int,
        color: Int,
        bold: Boolean
    ) {

        val text =
            TextView(this).apply {

                this.text =
                    value

                textSize =
                    size.toFloat()

                setTextColor(color)

                if (bold) {

                    setTypeface(
                        typeface,
                        Typeface.BOLD
                    )
                }

                setPadding(
                    0,
                    dp(3),
                    0,
                    dp(3)
                )
            }

        root.addView(text)
    }

    private fun space(
        value: Int
    ) {

        root.addView(
            View(this),
            LinearLayout.LayoutParams(
                1,
                dp(value)
            )
        )
    }

    private fun rounded(
        color: Int,
        radius: Int
    ): GradientDrawable {

        return GradientDrawable().apply {

            setColor(color)

            cornerRadius =
                radius.toFloat()
        }
    }

    private fun dp(
        value: Int
    ): Int {

        return (
            value *
                resources.displayMetrics.density
        ).roundToInt()
    }

    private fun formatTime(
        minutes: Long
    ): String {

        val hours =
            minutes / 60L

        val remaining =
            minutes % 60L

        return if (
            hours > 0L
        ) {

            "${hours}h ${remaining}m"

        } else {

            "$remaining min"
        }
    }

    private fun addFooter() {

        val time =
            SimpleDateFormat(
                "h:mm a",
                Locale.getDefault()
            ).format(Date())

        addText(
            "Last updated $time",
            12,
            gray,
            false
        )
    }
}
