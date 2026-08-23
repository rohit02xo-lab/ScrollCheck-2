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

    data class TrackedApp(
        val name: String,
        val packageName: String,
        val shortName: String,
        val color: Int
    )

    private val trackedApps = listOf(

        TrackedApp(
            "Instagram",
            "com.instagram.android",
            "IG",
            Color.rgb(220, 70, 120)
        ),

        TrackedApp(
            "YouTube",
            "com.google.android.youtube",
            "YT",
            Color.rgb(220, 50, 50)
        ),

        TrackedApp(
            "WhatsApp",
            "com.whatsapp",
            "WA",
            Color.rgb(35, 175, 105)
        ),

        TrackedApp(
            "X",
            "com.twitter.android",
            "X",
            Color.rgb(25, 30, 38)
        )
    )

    private val pageBackground =
        Color.rgb(246, 247, 251)

    private val navy =
        Color.rgb(24, 30, 46)

    private val purple =
        Color.rgb(91, 88, 230)

    private val lightPurple =
        Color.rgb(238, 238, 255)

    private val gray =
        Color.rgb(105, 112, 130)

    private val divider =
        Color.rgb(232, 234, 240)

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        dailyGoal =
            prefs.getLong(
                "daily_goal",
                60L
            )

        createScreen()
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

    private fun createScreen() {

        root =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(18),
                    dp(24),
                    dp(18),
                    dp(32)
                )

                setBackgroundColor(
                    pageBackground
                )
            }

        val scroll =
            ScrollView(this).apply {

                setBackgroundColor(
                    pageBackground
                )

                isFillViewport = true

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
            addPermissionCard()
        }

        val usage =
            trackedApps.associate {
                it.name to
                    getTodayUsage(
                        it.packageName
                    )
            }

        val sessions =
            trackedApps.associate {
                it.name to
                    getTodaySessions(
                        it.packageName
                    )
            }

        val total =
            usage.values.sum()

        addTodayCard(total)

        addSectionTitle(
            "Tracked apps"
        )

        addTrackedApps(
            usage,
            sessions
        )

        addSectionTitle(
            "Daily goal"
        )

        addGoalCard(total)

        addSectionTitle(
            "Scroll Balance"
        )

        addScoreCard(total)

        addSectionTitle(
            "Most used app"
        )

        addMostUsedCard(usage)

        addSectionTitle(
            "7-day overview"
        )

        addWeeklyCard()

        addDataNote()

        addRefreshButton()

        addFooter()
    }

    // =========================================================
    // HEADER
    // =========================================================

    private fun addHeader() {

        val date =
            SimpleDateFormat(
                "EEEE, d MMMM",
                Locale.getDefault()
            ).format(Date())

        addText(
            "SCROLLCHECK",
            13,
            purple,
            true
        )

        addText(
            "Digital wellbeing",
            30,
            navy,
            true
        )

        addText(
            date,
            14,
            gray,
            false
        )

        space(12)
    }

    // =========================================================
    // USAGE PERMISSION
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

    private fun addPermissionCard() {

        val card =
            createCard(Color.WHITE)

        addCardTitle(
            card,
            "🔐 Usage access required"
        )

        addCardBody(
            card,
            "Android must allow ScrollCheck to read app usage before real usage data can be displayed."
        )

        val button =
            createButton(
                "Grant usage access"
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
    // TODAY USAGE
    // =========================================================

    private fun getStartOfDay(): Long {

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

    private fun getTodayUsage(
        packageName: String
    ): Long {

        if (!hasUsageAccess()) {
            return 0L
        }

        val manager =
            getSystemService(
                Context.USAGE_STATS_SERVICE
            ) as UsageStatsManager

        val stats =
            manager.queryAndAggregateUsageStats(
                getStartOfDay(),
                System.currentTimeMillis()
            )

        return (
            stats[packageName]
                ?.totalTimeInForeground
                ?: 0L
        ) / 60000L
    }

    // =========================================================
    // REAL APP SESSIONS
    // =========================================================

    private fun getTodaySessions(
        packageName: String
    ): Int {

        if (!hasUsageAccess()) {
            return 0
        }

        val manager =
            getSystemService(
                Context.USAGE_STATS_SERVICE
            ) as UsageStatsManager

        val events =
            manager.queryEvents(
                getStartOfDay(),
                System.currentTimeMillis()
            )

        val event =
            UsageEvents.Event()

        var count = 0

        while (events.hasNextEvent()) {

            events.getNextEvent(event)

            if (
                event.packageName ==
                packageName &&
                event.eventType ==
                UsageEvents.Event.ACTIVITY_RESUMED
            ) {
                count++
            }
        }

        return count
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
            "TODAY'S TRACKED USAGE"
        )

        val totalText =
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
                    dp(5),
                    0,
                    dp(4)
                )
            }

        card.addView(totalText)

        val message =
            TextView(this).apply {

                text =
                    if (total <= dailyGoal) {
                        "✓ Within your daily goal"
                    } else {
                        "⚠ Above your daily goal"
                    }

                textSize = 14f

                setTextColor(
                    Color.rgb(
                        205,
                        210,
                        220
                    )
                )
            }

        card.addView(message)

        val goalText =
            TextView(this).apply {

                text =
                    "Goal: ${formatTime(dailyGoal)}"

                textSize = 13f

                setTextColor(
                    Color.rgb(
                        180,
                        187,
                        202
                    )
                )

                setPadding(
                    0,
                    dp(6),
                    0,
                    0
                )
            }

        card.addView(goalText)

        addCard(card)
    }

    // =========================================================
    // TRACKED APPS
    // =========================================================

    private fun addTrackedApps(
        usage: Map<String, Long>,
        sessions: Map<String, Int>
    ) {

        val card =
            createCard(Color.WHITE)

        val sorted =
            trackedApps.sortedByDescending {
                usage[it.name] ?: 0L
            }

        for (index in sorted.indices) {

            val app =
                sorted[index]

            addAppRow(
                card,
                app,
                usage[app.name] ?: 0L,
                sessions[app.name] ?: 0
            )

            if (index <
                sorted.lastIndex
            ) {

                addDivider(card)
            }
        }

        addCard(card)
    }

    private fun addAppRow(
        parent: LinearLayout,
        app: TrackedApp,
        minutes: Long,
        sessions: Int
    ) {

        val row =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL

                setPadding(
                    0,
                    dp(12),
                    0,
                    dp(12)
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
                        app.color,
                        dp(14)
                    )

                layoutParams =
                    LinearLayout.LayoutParams(
                        dp(46),
                        dp(46)
                    )
            }

        row.addView(icon)

        val info =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(12),
                    0,
                    dp(8),
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

        info.addView(name)

        val sessionText =
            TextView(this).apply {

                text =
                    "$sessions sessions today"

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

        info.addView(sessionText)

        row.addView(info)

        val time =
            TextView(this).apply {

                text =
                    formatTime(minutes)

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
            createCard(Color.WHITE)

        val titleRow =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL
            }

        val title =
            TextView(this).apply {

                text =
                    "Daily target"

                textSize = 17f

                setTextColor(
                    navy
                )

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )

                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        dp(35),
                        1f
                    )
            }

        titleRow.addView(title)

        val goal =
            TextView(this).apply {

                text =
                    formatTime(
                        dailyGoal
                    )

                textSize = 17f

                setTextColor(
                    purple
                )

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )
            }

        titleRow.addView(goal)

        card.addView(titleRow)

        val percentage =
            if (dailyGoal > 0) {

                (
                    total.toDouble() /
                        dailyGoal *
                        100
                )
                    .roundToInt()
                    .coerceIn(
                        0,
                        100
                    )

            } else {
                0
            }

        val progress =
            TextView(this).apply {

                text =
                    "████████████████████"
                        .take(
                            percentage /
                                5
                        )
                    +
                    "░░░░░░░░░░░░░░░░░░░░"
                        .take(
                            20 -
                                (
                                    percentage /
                                        5
                                )
                        )

                textSize = 12f

                setTextColor(
                    purple
                )

                setPadding(
                    0,
                    dp(10),
                    0,
                    dp(5)
                )
            }

        card.addView(progress)

        addCardBody(
            card,
            "${formatTime(total)} used • $percentage% of goal"
        )

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
            ) { _, position ->

                dailyGoal =
                    when (position) {

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

        if (total == 0L) {
            return 100
        }

        var score =
            100.0

        if (total > dailyGoal) {

            score -=
                (
                    total -
                        dailyGoal
                ) * 0.35
        }

        if (total > 120) {
            score -= 10
        }

        return score
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
            createCard(Color.WHITE)

        val score =
            calculateScore(total)

        val scoreText =
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

        card.addView(scoreText)

        val label =
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

        card.addView(label)

        val status =
            TextView(this).apply {

                text =
                    when {

                        score >= 80 ->
                            "🟢 Excellent balance"

                        score >= 60 ->
                            "🟡 Good balance"

                        score >= 40 ->
                            "🟠 Needs attention"

                        else ->
                            "🔴 Time for a reset"
                    }

                textSize = 15f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    gray
                )

                setPadding(
                    0,
                    dp(7),
                    0,
                    0
                )
            }

        card.addView(status)

        addCard(card)
    }

    // =========================================================
    // MOST USED
    // =========================================================

    private fun addMostUsedCard(
        usage: Map<String, Long>
    ) {

        val app =
            trackedApps.maxByOrNull {
                usage[it.name] ?: 0L
            }

        if (app == null) {
            return
        }

        val minutes =
            usage[app.name] ?: 0L

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
            "Most used tracked app today • ${formatTime(minutes)}"
        )

        addCard(card)
    }

    // =========================================================
    // WEEKLY DATA
    // =========================================================

    private fun getDayUsage(
        offset: Int
    ): Long {

        if (!hasUsageAccess()) {
            return 0L
        }

        val day =
            Calendar.getInstance()

        day.add(
            Calendar.DAY_OF_YEAR,
            offset
        )

        day.set(
            Calendar.HOUR_OF_DAY,
            0
        )

        day.set(
            Calendar.MINUTE,
            0
        )

        day.set(
            Calendar.SECOND,
            0
        )

        day.set(
            Calendar.MILLISECOND,
            0
        )

        val start =
            day.timeInMillis

        val end =
            if (offset == 0) {

                System.currentTimeMillis()

            } else {

                val tomorrow =
                    day.clone()
                        as Calendar

                tomorrow.add(
                    Calendar.DAY_OF_YEAR,
                    1
                )

                tomorrow.timeInMillis
            }

        val manager =
            getSystemService(
                Context.USAGE_STATS_SERVICE
            ) as UsageStatsManager

        val stats =
            manager.queryAndAggregateUsageStats(
                start,
                end
            )

        var total =
            0L

        for (app in trackedApps) {

            total += (
                stats[
                    app.packageName
                ]?.totalTimeInForeground
                    ?: 0L
            ) / 60000L
        }

        return total
    }

    private fun addWeeklyCard() {

        val card =
            createCard(Color.WHITE)

        val values =
            ArrayList<Long>()

        for (i in -6..0) {

            values.add(
                getDayUsage(i)
            )
        }

        val maximum =
            (values.maxOrNull() ?: 1L)
                .coerceAtLeast(1L)

        for (i in values.indices) {

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

            val day =
                Calendar.getInstance()

            day.add(
                Calendar.DAY_OF_YEAR,
                -6 + i
            )

            val dayName =
                SimpleDateFormat(
                    "EEE",
                    Locale.getDefault()
                ).format(
                    day.time
                )

            val label =
                TextView(this).apply {

                    text =
                        dayName

                    textSize = 12f

                    setTextColor(
                        gray
                    )

                    layoutParams =
                        LinearLayout.LayoutParams(
                            dp(40),
                            dp(30)
                        )
                }

            row.addView(label)

            val barLength =
                (
                    values[i].toDouble() /
                        maximum *
                        18
                )
                    .roundToInt()
                    .coerceAtLeast(
                        if (values[i] > 0) 1
                        else 0
                    )

            val bar =
                TextView(this).apply {

                    text =
                        "█".repeat(
                            barLength
                        )

                    textSize = 14f

                    setTextColor(
                        purple
                    )

                    layoutParams =
                        LinearLayout.LayoutParams(
                            0,
                            dp(30),
                            1f
                        )
                }

            row.addView(bar)

            val time =
                TextView(this).apply {

                    text =
                        formatTime(
                            values[i]
                        )

                    textSize = 11f

                    setTextColor(
                        gray
                    )
                }

            row.addView(time)

            card.addView(row)
        }

        val weekTotal =
            values.sum()

        addCardBody(
            card,
            "This week: ${formatTime(weekTotal)}"
        )

        addCard(card)
    }

    // =========================================================
    // DATA NOTE
    // =========================================================

    private fun addDataNote() {

        val card =
            createCard(Color.WHITE)

        addCardTitle(
            card,
            "About the data"
        )

        addCardBody(
            card,
            "Usage time comes directly from Android Usage Stats. Sessions represent Android app-resume events. Android does not provide an exact count of individual feed swipes, so ScrollCheck does not pretend that it does."
        )

        addCard(card)
    }

    // =========================================================
    // REFRESH
    // =========================================================

    private fun addRefreshButton() {

        val button =
            createButton(
                "↻  Refresh data"
            )

        button.setOnClickListener {

            refreshDashboard()

            Toast.makeText(
                this,
                "Data refreshed",
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
                    dp(12),
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
        title: String
    ) {

        val text =
            TextView(this).apply {

                this.text =
                    title

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
        body: String
    ) {

        val text =
            TextView(this).apply {

                this.text =
                    body

                textSize = 13f

                setTextColor(
                    gray
                )

                setPadding(
                    0,
                    dp(6),
                    0,
                    dp(8)
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

    private fun addSectionTitle(
        title: String
    ) {

        val text =
            TextView(this).apply {

                this.text =
                    title

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
                    dp(15),
                    0,
                    dp(7)
                )
            }

        root.addView(text)
    }

    private fun createButton(
        text: String
    ): Button {

        return Button(this).apply {

            this.text =
                text

            textSize = 13f

            setTextColor(
                Color.WHITE
            )

            background =
                rounded(
                    purple,
                    dp(13)
                )

            stateListAnimator =
                null
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
        height: Int
    ) {

        root.addView(
            View(this),
            LinearLayout.LayoutParams(
                1,
                dp(height)
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
            minutes / 60

        val mins =
            minutes % 60

        return if (hours > 0) {
            "${hours}h ${mins}m"
        } else {
            "$mins min"
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
