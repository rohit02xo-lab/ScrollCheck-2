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

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        dailyGoal =
            prefs.getLong(
                "daily_goal",
                60L
            )

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

        addTodayCard(total)

        addSectionTitle(
            "Tracked apps"
        )

        addTrackedApps(usage)

        addSectionTitle(
            "Daily goal"
        )

        addGoalCard(total)

        addSectionTitle(
            "Scroll Balance"
        )

        addScoreCard(total)

        addSectionTitle(
            "Most used"
        )

        addMostUsedCard(usage)

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
            SimpleDateFormat(
                "EEEE, d MMMM",
                Locale.getDefault()
            ).format(Date()),
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
    // ACTUAL USAGE CALCULATION
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

            if (
                event.packageName !=
                targetPackage
            ) {
                continue
            }

            when (event.eventType) {

                /*
                 * Newer Android versions.
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
                 * Older Android versions.
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
                 * Newer Android versions.
                 */
                UsageEvents.Event.ACTIVITY_PAUSED -> {

                    if (
                        foregroundStart >= 0L
                    ) {

                        val duration =
                            event.timeStamp -
                                foregroundStart

                        if (duration > 0L) {
                            totalMilliseconds +=
                                duration
                        }

                        foregroundStart =
                            -1L
                    }
                }

                /*
                 * Older Android versions.
                 */
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {

                    if (
                        foregroundStart >= 0L
                    ) {

                        val duration =
                            event.timeStamp -
                                foregroundStart

                        if (duration > 0L) {
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
         * If the app is currently open,
         * count the unfinished session up to now.
         */
        if (
            foregroundStart >= 0L
        ) {

            val duration =
                end -
                    foregroundStart

            if (duration > 0L) {
                totalMilliseconds +=
                    duration
            }
        }

        val minutes =
            (
                totalMilliseconds /
                    60000.0
            ).roundToInt()
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
            "TODAY'S TRACKED TIME"
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
            if (total <= dailyGoal) {
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
            "Daily goal: ${formatTime(dailyGoal)}"
        )

        addCard(card)
    }

    // =========================================================
    // APP LIST
    // =========================================================

    private fun addTrackedApps(
        usage: Map<AppInfo, UsageResult>
    ) {

        val card =
            createCard(white)

        val sorted =
            trackedApps.sortedByDescending {
                usage[it]?.minutes ?: 0L
            }

        for (index in sorted.indices) {

            val app =
                sorted[index]

            val result =
                usage[app]
                    ?: UsageResult(
                        0L,
                        0
                    )

            addAppRow(
                card,
                app,
                result
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

                setTextColor(
                    navy
                )

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )
            }

        information.addView(name)

        val sessionText =
            TextView(this).apply {

                text =
                    "${result.sessions} sessions today"

                textSize = 12f

                setTextColor(
                    gray
                )

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

                setTextColor(
                    navy
                )

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )
            }

        row.addView(time)

        parent.addView(row)
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
            "${formatTime(total)} used of ${formatTime(dailyGoal)}"
        )

        val progress =
            TextView(this).apply {

                text =
                    "█".repeat(filled) +
                    "░".repeat(
                        20 - filled
                    )

                textSize = 12f

                setTextColor(
                    purple
                )

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

        if (total <= dailyGoal) {
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

                setTextColor(
                    navy
                )

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

                setTextColor(
                    gray
                )
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

                setTextColor(
                    gray
                )

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
            }
                ?: return

        val result =
            usage[app]
                ?: UsageResult(
                    0L,
                    0
                )

        val card =
            createCard(
                lightPurple
            )

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
    // ACCURACY INFORMATION
    // =========================================================

    private fun addAccuracyCard() {

        val card =
            createCard(white)

        addCardTitle(
            card,
            "ℹ️ How ScrollCheck measures time"
        )

        addCardBody(
            card,
            "App time is calculated from Android Usage Events when the tracked app enters and leaves the foreground."
        )

        addCardBody(
            card,
            "ScrollCheck does not invent screen-time numbers or estimate individual swipes."
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

                setTextColor(
                    navy
                )

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

                setTextColor(
                    gray
                )

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

                setTextColor(
                    navy
                )

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

                setTextColor(
                    color
                )

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

        val remainingMinutes =
            minutes % 60L

        return if (
            hours > 0L
        ) {
            "${hours}h ${remainingMinutes}m"
        } else {
            "$remainingMinutes min"
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
