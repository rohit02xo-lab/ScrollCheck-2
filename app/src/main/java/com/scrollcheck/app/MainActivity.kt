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
        val icon: String
    )

    private val apps = listOf(
        AppInfo(
            "Instagram",
            "com.instagram.android",
            "IG"
        ),
        AppInfo(
            "YouTube",
            "com.google.android.youtube",
            "YT"
        ),
        AppInfo(
            "WhatsApp",
            "com.whatsapp",
            "WA"
        ),
        AppInfo(
            "X",
            "com.twitter.android",
            "X"
        )
    )

    private val navy =
        Color.rgb(20, 28, 45)

    private val purple =
        Color.rgb(92, 88, 230)

    private val background =
        Color.rgb(246, 247, 251)

    private val secondary =
        Color.rgb(105, 112, 130)

    private val green =
        Color.rgb(35, 170, 105)

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        dailyGoal =
            prefs.getLong(
                "daily_goal",
                60L
            )

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
            getSystemService(
                Context.APP_OPS_SERVICE
            ) as AppOpsManager

        return appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        ) == AppOpsManager.MODE_ALLOWED
    }

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

    private fun todayUsage(
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
                startOfToday(),
                System.currentTimeMillis()
            )

        return (
            stats[packageName]
                ?.totalTimeInForeground
                ?: 0L
        ) / 60000L
    }

    private fun todaySessions(
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
                startOfToday(),
                System.currentTimeMillis()
            )

        val event =
            UsageEvents.Event()

        var sessions = 0

        while (events.hasNextEvent()) {

            events.getNextEvent(event)

            if (
                event.packageName ==
                packageName &&
                event.eventType ==
                UsageEvents.Event.ACTIVITY_RESUMED
            ) {
                sessions++
            }
        }

        return sessions
    }

    private fun usageForDay(
        day: Calendar
    ): Long {

        if (!hasUsageAccess()) {
            return 0L
        }

        val start =
            day.clone() as Calendar

        start.set(
            Calendar.HOUR_OF_DAY,
            0
        )

        start.set(
            Calendar.MINUTE,
            0
        )

        start.set(
            Calendar.SECOND,
            0
        )

        start.set(
            Calendar.MILLISECOND,
            0
        )

        val end =
            start.clone() as Calendar

        end.add(
            Calendar.DAY_OF_YEAR,
            1
        )

        val manager =
            getSystemService(
                Context.USAGE_STATS_SERVICE
            ) as UsageStatsManager

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
    // MAIN UI
    // =========================================================

    private fun buildUi() {

        root =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(18),
                    dp(24),
                    dp(18),
                    dp(35)
                )

                setBackgroundColor(
                    background
                )
            }

        val scroll =
            ScrollView(this)

        scroll.setBackgroundColor(
            background
        )

        scroll.addView(root)

        setContentView(scroll)

        refreshDashboard()
    }

    private fun refreshDashboard() {

        root.removeAllViews()

        val usage =
            apps.associate {
                it.name to
                    todayUsage(
                        it.packageName
                    )
            }

        val sessions =
            apps.associate {
                it.name to
                    todaySessions(
                        it.packageName
                    )
            }

        val total =
            usage.values.sum()

        addHeader()

        if (!hasUsageAccess()) {

            addAccessCard()
        }

        addHeroCard(total)

        addSectionTitle(
            "Your apps"
        )

        addAppsCard(
            usage,
            sessions
        )

        addSectionTitle(
            "Weekly overview"
        )

        addWeeklyCard()

        addSectionTitle(
            "Your goal"
        )

        addGoalCard(total)

        addSectionTitle(
            "Most used"
        )

        addMostUsedCard(usage)

        addInfoCard()

        addRefreshButton()

        addFooter()
    }

    // =========================================================
    // HEADER
    // =========================================================

    private fun addHeader() {

        val today =
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
            "Your digital wellbeing",
            29,
            navy,
            true
        )

        addText(
            today,
            14,
            secondary,
            false
        )

        space(14)
    }

    // =========================================================
    // ACCESS CARD
    // =========================================================

    private fun addAccessCard() {

        val card =
            createCard(
                Color.WHITE
            )

        addCardTitle(
            card,
            "Usage access needed"
        )

        addCardBody(
            card,
            "Allow ScrollCheck to read your real app usage."
        )

        val button =
            createButton(
                "Grant access"
            )

        button.setOnClickListener {

            startActivity(
                Intent(
                    Settings.ACTION_USAGE_ACCESS_SETTINGS
                )
            )
        }

        card.addView(button)

        addCardToRoot(card)
    }

    // =========================================================
    // HERO
    // =========================================================

    private fun addHeroCard(
        total: Long
    ) {

        val card =
            createCard(
                navy
            )

        val small =
            TextView(this).apply {

                text =
                    "TODAY'S TRACKED USAGE"

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

        card.addView(small)

        val totalText =
            TextView(this).apply {

                text =
                    formatMinutes(total)

                textSize = 42f

                setTextColor(
                    Color.WHITE
                )

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )

                setPadding(
                    0,
                    dp(7),
                    0,
                    dp(2)
                )
            }

        card.addView(totalText)

        val goalText =
            TextView(this).apply {

                text =
                    if (
                        total <= dailyGoal
                    ) {
                        "You're within your daily goal"
                    } else {
                        "You've passed your daily goal"
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

        card.addView(goalText)

        spaceInside(card, 15)

        val score =
            calculateScore(total)

        val scoreRow =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL
            }

        val scoreLabel =
            TextView(this).apply {

                text =
                    "Balance Score"

                textSize = 14f

                setTextColor(
                    Color.WHITE
                )

                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        dp(35),
                        1f
                    )
            }

        scoreRow.addView(scoreLabel)

        val scoreText =
            TextView(this).apply {

                text =
                    "$score"

                textSize = 24f

                setTextColor(
                    Color.WHITE
                )

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )
            }

        scoreRow.addView(scoreText)

        card.addView(scoreRow)

        val progress =
            ProgressBar(
                this,
                null,
                android.R.attr.progressBarStyleHorizontal
            )

        progress.max = 100

        progress.progress = score

        progress.progressDrawable =
            roundedDrawable(
                Color.rgb(
                    116,
                    110,
                    245
                ),
                dp(8)
            )

        card.addView(
            progress,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(8)
            )
        )

        addCardToRoot(
            card,
            navy
        )
    }

    // =========================================================
    // APP CARDS
    // =========================================================

    private fun addAppsCard(
        usage: Map<String, Long>,
        sessions: Map<String, Int>
    ) {

        val card =
            createCard(
                Color.WHITE
            )

        val sorted =
            apps.sortedByDescending {
                usage[it.name] ?: 0L
            }

        for (index in sorted.indices) {

            val app =
                sorted[index]

            val minutes =
                usage[app.name] ?: 0L

            val sessionCount =
                sessions[app.name] ?: 0

            addAppRow(
                card,
                app,
                minutes,
                sessionCount
            )

            if (
                index <
                sorted.lastIndex
            ) {

                val divider =
                    View(this).apply {

                        setBackgroundColor(
                            Color.rgb(
                                235,
                                237,
                                242
                            )
                        )
                    }

                card.addView(
                    divider,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        1
                    )
                )
            }
        }

        addCardToRoot(card)
    }

    private fun addAppRow(
        parent: LinearLayout,
        app: AppInfo,
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
                    dp(14),
                    0,
                    dp(14)
                )
            }

        val icon =
            TextView(this).apply {

                text =
                    app.icon

                textSize = 14f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    Color.WHITE
                )

                background =
                    roundedDrawable(
                        appColor(app),
                        dp(14)
                    )

                layoutParams =
                    LinearLayout.LayoutParams(
                        dp(46),
                        dp(46)
                    )
            }

        row.addView(icon)

        val middle =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(13),
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

        middle.addView(name)

        val sessionText =
            TextView(this).apply {

                text =
                    "$sessions sessions today"

                textSize = 12f

                setTextColor(
                    secondary
                )

                setPadding(
                    0,
                    dp(3),
                    0,
                    0
                )
            }

        middle.addView(
            sessionText
        )

        row.addView(middle)

        val time =
            TextView(this).apply {

                text =
                    formatMinutes(minutes)

                textSize = 16f

                setTextColor(
                    navy
                )

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )

                gravity =
                    Gravity.CENTER_VERTICAL
            }

        row.addView(time)

        parent.addView(row)
    }

    // =========================================================
    // WEEKLY CARD
    // =========================================================

    private fun addWeeklyCard() {

        val card =
            createCard(
                Color.WHITE
            )

        val days =
            ArrayList<Pair<String, Long>>()

        var maxUsage = 1L

        for (i in 6 downTo 0) {

            val calendar =
                Calendar.getInstance()

            calendar.add(
                Calendar.DAY_OF_YEAR,
                -i
            )

            val day =
                SimpleDateFormat(
                    "EEE",
                    Locale.getDefault()
                ).format(
                    calendar.time
                )

            val minutes =
                usageForDay(
                    calendar
                )

            days.add(
                Pair(
                    day,
                    minutes
                )
            )

            if (
                minutes > maxUsage
            ) {
                maxUsage =
                    minutes
            }
        }

        val chart =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.BOTTOM

                setPadding(
                    0,
                    dp(10),
                    0,
                    dp(4)
                )

                layoutParams =
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(175)
                    )
            }

        for (day in days) {

            val column =
                LinearLayout(this).apply {

                    orientation =
                        LinearLayout.VERTICAL

                    gravity =
                        Gravity.BOTTOM or
                                Gravity.CENTER_HORIZONTAL

                    layoutParams =
                        LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            1f
                        )
                }

            val minutes =
                day.second

            val barHeight =
                if (
                    minutes == 0L
                ) {
                    5
                } else {
                    (
                        115.0 *
                                minutes /
                                maxUsage
                    )
                        .roundToInt()
                        .coerceAtLeast(8)
                }

            val bar =
                View(this).apply {

                    background =
                        roundedDrawable(
                            purple,
                            dp(7)
                        )
                }

            column.addView(
                bar,
                LinearLayout.LayoutParams(
                    dp(24),
                    dp(barHeight)
                )
            )

            val label =
                TextView(this).apply {

                    text =
                        day.first

                    textSize = 11f

                    setTextColor(
                        secondary
                    )

                    gravity =
                        Gravity.CENTER

                    setPadding(
                        0,
                        dp(7),
                        0,
                        0
                    )
                }

            column.addView(
                label,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(25)
                )
            )

            chart.addView(column)
        }

        card.addView(chart)

        val totalWeek =
            days.sumOf {
                it.second
            }

        val summary =
            TextView(this).apply {

                text =
                    "7-day tracked usage: " +
                            formatMinutes(
                                totalWeek
                            )

                textSize = 13f

                setTextColor(
                    secondary
                )

                setPadding(
                    0,
                    dp(8),
                    0,
                    0
                )
            }

        card.addView(summary)

        addCardToRoot(card)
    }

    // =========================================================
    // GOAL
    // =========================================================

    private fun addGoalCard(
        total: Long
    ) {

        val card =
            createCard(
                Color.WHITE
            )

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

        val top =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL
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

        top.addView(title)

        val percent =
            TextView(this).apply {

                text =
                    "$percentage%"

                textSize = 17f

                setTextColor(
                    purple
                )

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )
            }

        top.addView(percent)

        card.addView(top)

        val progress =
            ProgressBar(
                this,
                null,
                android.R.attr.progressBarStyleHorizontal
            )

        progress.max = 100

        progress.progress =
            percentage

        progress.progressDrawable =
            roundedDrawable(
                purple,
                dp(8)
            )

        card.addView(
            progress,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(8)
            )
        )

        val description =
            TextView(this).apply {

                text =
                    "${formatMinutes(total)} of " +
                            "${formatMinutes(dailyGoal)} goal"

                textSize = 13f

                setTextColor(
                    secondary
                )

                setPadding(
                    0,
                    dp(9),
                    0,
                    dp(7)
                )
            }

        card.addView(
            description
        )

        val change =
            createButton(
                "Change daily goal"
            )

        change.setOnClickListener {

            showGoalDialog()
        }

        card.addView(change)

        addCardToRoot(card)
    }

    // =========================================================
    // MOST USED
    // =========================================================

    private fun addMostUsedCard(
        usage: Map<String, Long>
    ) {

        val app =
            apps.maxByOrNull {
                usage[it.name] ?: 0L
            }

        if (app == null) {
            return
        }

        val minutes =
            usage[app.name] ?: 0L

        val card =
            createCard(
                Color.WHITE
            )

        addCardTitle(
            card,
            "${app.icon}  ${app.name}"
        )

        addCardBody(
            card,
            "${formatMinutes(minutes)} of tracked usage today."
        )

        addCardToRoot(card)
    }

    // =========================================================
    // INFORMATION
    // =========================================================

    private fun addInfoCard() {

        val card =
            createCard(
                Color.rgb(
                    237,
                    238,
                    255
                )
            )

        addCardTitle(
            card,
            "About your data"
        )

        addCardBody(
            card,
            "Usage time and sessions come from Android Usage Stats. ScrollCheck does not invent app usage numbers."
        )

        addCardToRoot(
            card,
            Color.rgb(
                237,
                238,
                255
            )
        )
    }

    // =========================================================
    // REFRESH
    // =========================================================

    private fun addRefreshButton() {

        val button =
            createButton(
                "Refresh data"
            )

        button.setOnClickListener {

            refreshDashboard()

            android.widget.Toast.makeText(
                this,
                "Data refreshed",
                Toast.LENGTH_SHORT
            ).show()
        }

        root.addView(
            button,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(50)
            ).apply {

                setMargins(
                    0,
                    dp(15),
                    0,
                    dp(5)
                )
            }
        )
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
            secondary,
            false
        )
    }

    // =========================================================
    // GOAL DIALOG
    // =========================================================

    private fun showGoalDialog() {

        val options =
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
                options
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

        if (total == 0L) {
            return 100
        }

        var score = 100.0

        if (
            total > dailyGoal
        ) {

            score -=
                (total - dailyGoal) *
                        0.35
        }

        if (
            total > 120
        ) {

            score -= 10
        }

        return score
            .roundToInt()
            .coerceIn(
                0,
                100
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
                dp(16),
                dp(18),
                dp(16)
            )

            background =
                roundedDrawable(
                    color,
                    dp(18)
                )

            elevation =
                dp(2).toFloat()
        }
    }

    private fun addCardToRoot(
        card: View,
        backgroundColor: Int = Color.WHITE
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
        text: String
    ) {

        val title =
            TextView(this).apply {

                this.text =
                    text

                textSize = 17f

                setTextColor(
                    navy
                )

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )
            }

        card.addView(title)
    }

    private fun addCardBody(
        card: LinearLayout,
        text: String
    ) {

        val body =
            TextView(this).apply {

                this.text =
                    text

                textSize = 13f

                setTextColor(
                    secondary
                )

                setPadding(
                    0,
                    dp(7),
                    0,
                    dp(7)
                )
            }

        card.addView(body)
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
                roundedDrawable(
                    purple,
                    dp(13)
                )

            stateListAnimator = null

            setPadding(
                dp(12),
                0,
                dp(12),
                0
            )
        }
    }

    private fun roundedDrawable(
        color: Int,
        radius: Int
    ): GradientDrawable {

        return GradientDrawable().apply {

            setColor(color)

            cornerRadius =
                radius.toFloat()
        }
    }

    private fun appColor(
        app: AppInfo
    ): Int {

        return when (app.name) {

            "Instagram" ->
                Color.rgb(
                    225,
                    70,
                    120
                )

            "YouTube" ->
                Color.rgb(
                    225,
                    55,
                    55
                )

            "WhatsApp" ->
                Color.rgb(
                    35,
                    175,
                    105
                )

            else ->
                Color.rgb(
                    25,
                    30,
                    40
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
                    dp(4),
                    0,
                    dp(4)
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

    private fun spaceInside(
        parent: LinearLayout,
        height: Int
    ) {

        parent.addView(
            View(this),
            LinearLayout.LayoutParams(
                1,
                dp(height)
            )
        )
    }

    private fun dp(
        value: Int
    ): Int {

        return (
            value *
                    resources.displayMetrics.density
            ).roundToInt()
    }

    private fun formatMinutes(
        minutes: Long
    ): String {

        val hours =
            minutes / 60

        val mins =
            minutes % 60

        return if (
            hours > 0
        ) {

            "${hours}h ${mins}m"

        } else {

            "${mins} min"
        }
    }
}
