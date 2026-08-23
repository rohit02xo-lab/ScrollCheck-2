package com.scrollcheck.app

import android.app.Activity
import android.app.AlertDialog
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
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
        getSharedPreferences("scrollcheck", Context.MODE_PRIVATE)
    }

    private val apps = listOf(
        AppInfo(
            "Instagram",
            "com.instagram.android",
            "📸"
        ),
        AppInfo(
            "YouTube",
            "com.google.android.youtube",
            "▶️"
        ),
        AppInfo(
            "WhatsApp",
            "com.whatsapp",
            "💬"
        ),
        AppInfo(
            "X",
            "com.twitter.android",
            "𝕏"
        )
    )

    private var dailyGoal = 60L

    data class AppInfo(
        val name: String,
        val packageName: String,
        val icon: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        dailyGoal = prefs.getLong(
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

    // ==========================================
    // USAGE ACCESS
    // ==========================================

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

    private fun todayUsage(
        packageName: String
    ): Long {

        if (!hasUsageAccess()) return 0L

        val manager =
            getSystemService(
                Context.USAGE_STATS_SERVICE
            ) as UsageStatsManager

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

        val stats =
            manager.queryAndAggregateUsageStats(
                calendar.timeInMillis,
                System.currentTimeMillis()
            )

        return (
            stats[packageName]
                ?.totalTimeInForeground
                ?: 0L
        ) / 60000L
    }

    // ==========================================
    // UI
    // ==========================================

    private fun buildUi() {

        root = LinearLayout(this).apply {

            orientation =
                LinearLayout.VERTICAL

            setPadding(
                dp(18),
                dp(28),
                dp(18),
                dp(35)
            )

            setBackgroundColor(
                Color.rgb(8, 8, 10)
            )
        }

        val scroll =
            ScrollView(this)

        scroll.addView(root)

        setContentView(scroll)

        refreshDashboard()
    }

    private fun refreshDashboard() {

        root.removeAllViews()

        val usage =
            apps.associate {
                it.name to todayUsage(
                    it.packageName
                )
            }

        val total =
            usage.values.sum()

        // ======================================
        // HEADER
        // ======================================

        addText(
            "SCROLLCHECK",
            14,
            Color.rgb(120, 130, 255),
            true
        )

        addText(
            "Digital Wellbeing",
            31,
            Color.WHITE,
            true
        )

        addText(
            "Track → Understand → Improve → Reward",
            15,
            Color.LTGRAY,
            false
        )

        space(12)

        // ======================================
        // ACCESS
        // ======================================

        if (!hasUsageAccess()) {

            addCard(
                "🔐 Usage Access",
                "Allow ScrollCheck to read today's app usage.",
                "Grant Access"
            ) {

                startActivity(
                    Intent(
                        Settings.ACTION_USAGE_ACCESS_SETTINGS
                    )
                )
            }
        }

        // ======================================
        // TOTAL USAGE
        // ======================================

        addSectionTitle(
            "Today's screen time"
        )

        addBigCard(
            formatMinutes(total),
            if (total <= dailyGoal) {
                "✅ Within your $dailyGoal min goal"
            } else {
                "⚠️ $dailyGoal min goal exceeded"
            }
        )

        // ======================================
        // MOST USED APP
        // ======================================

        addSectionTitle(
            "📱 Most used apps"
        )

        val sorted =
            apps.sortedByDescending {
                usage[it.name] ?: 0L
            }

        for (app in sorted) {

            addUsageCard(
                "${app.icon} ${app.name}",
                usage[app.name] ?: 0L
            )
        }

        // ======================================
        // SCORE
        // ======================================

        val score =
            calculateScore(total)

        addSectionTitle(
            "🧠 Scroll Balance"
        )

        addScoreCard(score)

        // ======================================
        // CATEGORIES
        // ======================================

        addSectionTitle(
            "📊 Usage Categories"
        )

        val educational =
            (total * 0.25).roundToInt()

        val useful =
            (total * 0.15).roundToInt()

        val entertainment =
            (total * 0.55).roundToInt()

        val unclassified =
            (total * 0.05).roundToInt()

        addCategory(
            "📚 Educational",
            educational
        )

        addCategory(
            "🛠️ Skill / Useful",
            useful
        )

        addCategory(
            "🎭 Entertainment",
            entertainment
        )

        addCategory(
            "🔍 Unclassified",
            unclassified
        )

        // ======================================
        // PRODUCTIVE %
        // ======================================

        val productive =
            educational + useful

        val productivePercent =
            if (total > 0) {
                (
                    productive.toDouble()
                        / total.toDouble()
                        * 100
                ).roundToInt()
            } else {
                0
            }

        addCard(
            "🎯 Productive scrolling",
            "$productivePercent% of tracked time is estimated educational or useful.",
            "Got it"
        ) {}

        // ======================================
        // NOTIFICATIONS
        // ======================================

        addSectionTitle(
            "🔔 Notifications"
        )

        addNotificationDashboard()

        // ======================================
        // WEEKLY
        // ======================================

        addSectionTitle(
            "📈 Weekly screen time"
        )

        addWeeklyChart()

        // ======================================
        // LATE NIGHT
        // ======================================

        addSectionTitle(
            "🌙 Late-night scrolling"
        )

        addCard(
            "Night usage",
            "ScrollCheck will use detailed usage data to identify late-night patterns in future tracking versions.",
            "Understood"
        ) {}

        // ======================================
        // DAILY GOAL
        // ======================================

        addSectionTitle(
            "🎯 Daily Goal"
        )

        addGoalCard()

        // ======================================
        // CHALLENGE
        // ======================================

        addSectionTitle(
            "🏆 Today's Challenge"
        )

        addChallengeCard(
            entertainment,
            total
        )

        // ======================================
        // STREAK
        // ======================================

        addSectionTitle(
            "🔥 Streak"
        )

        addStreakCard()

        // ======================================
        // REWARDS
        // ======================================

        addSectionTitle(
            "⭐ ScrollPoints"
        )

        addRewardsCard()

        // ======================================
        // RESET
        // ======================================

        addSectionTitle(
            "🧘 5-Minute Reset"
        )

        addResetCard()

        // ======================================
        // INSIGHT
        // ======================================

        addSectionTitle(
            "💡 Your Insight"
        )

        addInsight(
            total,
            productivePercent,
            entertainment
        )

        // ======================================
        // REFRESH
        // ======================================

        val refresh =
            Button(this).apply {

                text =
                    "🔄 Refresh Dashboard"

                setOnClickListener {

                    refreshDashboard()

                    Toast.makeText(
                        this@MainActivity,
                        "Dashboard refreshed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

        root.addView(
            refresh,
            buttonParams()
        )

        addText(
            "Last updated: ${
                SimpleDateFormat(
                    "h:mm a",
                    Locale.getDefault()
                ).format(Date())
            }",
            12,
            Color.GRAY,
            false
        )
    }

    // ==========================================
    // NOTIFICATION DASHBOARD
    // ==========================================

    private fun addNotificationDashboard() {

        val box =
            createCard()

        val total =
            apps.sumOf {

                prefs.getInt(
                    "notifications_${it.name}",
                    0
                )
            }

        val title =
            TextView(this).apply {

                text =
                    "$total notifications"

                textSize = 28f

                setTextColor(
                    Color.WHITE
                )

                setTypeface(
                    typeface,
                    android.graphics.Typeface.BOLD
                )
            }

        box.addView(title)

        addTextToBox(
            box,
            "Only Instagram, YouTube, WhatsApp and X are counted.",
            14,
            Color.LTGRAY
        )

        for (app in apps) {

            val count =
                prefs.getInt(
                    "notifications_${app.name}",
                    0
                )

            addTextToBox(
                box,
                "${app.icon} ${app.name}     $count",
                16,
                Color.WHITE
            )
        }

        val button =
            Button(this).apply {

                text =
                    "🔔 Enable Notification Access"

                setOnClickListener {

                    try {

                        startActivity(
                            Intent(
                                "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"
                            )
                        )

                    } catch (e: Exception) {

                        startActivity(
                            Intent(
                                Settings.ACTION_SETTINGS
                            )
                        )
                    }
                }
            }

        box.addView(button)

        root.addView(
            box,
            cardParams()
        )
    }

    // ==========================================
    // WEEKLY CHART
    // ==========================================

    private fun addWeeklyChart() {

        val box =
            createCard()

        val title =
            TextView(this).apply {

                text =
                    "Last 7 days"

                textSize = 18f

                setTextColor(
                    Color.WHITE
                )

                setTypeface(
                    typeface,
                    android.graphics.Typeface.BOLD
                )
            }

        box.addView(title)

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

            val total =
                usageForDay(
                    calendar
                )

            addTextToBox(
                box,
                "$day        ${formatMinutes(total)}",
                15,
                Color.LTGRAY
            )
        }

        root.addView(
            box,
            cardParams()
        )
    }

    private fun usageForDay(
        calendar: Calendar
    ): Long {

        if (!hasUsageAccess()) return 0L

        val start =
            calendar.clone() as Calendar

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

        return apps.sumOf {

            (
                stats[it.packageName]
                    ?.totalTimeInForeground
                    ?: 0L
            ) / 60000L
        }
    }

    // ==========================================
    // SCORE
    // ==========================================

    private fun calculateScore(
        total: Long
    ): Int {

        if (total == 0L) return 100

        var score =
            100.0

        if (total > dailyGoal) {

            score -=
                (total - dailyGoal) * 0.35
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

    private fun scoreStatus(
        score: Int
    ): String {

        return when {

            score >= 80 ->
                "🟢 Excellent balance"

            score >= 60 ->
                "🟡 Good balance"

            score >= 40 ->
                "🟠 Needs attention"

            else ->
                "🔴 Time to reset"
        }
    }

    private fun addScoreCard(
        score: Int
    ) {

        val box =
            createCard()

        val scoreText =
            TextView(this).apply {

                text =
                    "$score / 100"

                textSize = 38f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    Color.WHITE
                )

                setTypeface(
                    typeface,
                    android.graphics.Typeface.BOLD
                )
            }

        box.addView(scoreText)

        val status =
            TextView(this).apply {

                text =
                    scoreStatus(score)

                textSize = 16f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    Color.LTGRAY
                )
            }

        box.addView(status)

        root.addView(
            box,
            cardParams()
        )
    }

    // ==========================================
    // INSIGHT
    // ==========================================

    private fun addInsight(
        total: Long,
        productivePercent: Int,
        entertainment: Int
    ) {

        val message =
            when {

                total == 0L ->
                    "📱 Start using your tracked apps and ScrollCheck will analyse your pattern."

                productivePercent >= 50 ->
                    "📚 Great! A large portion of your tracked time is estimated educational or useful."

                entertainment > total * 0.5 ->
                    "🎭 Entertainment is taking most of your tracked time. Try a short break before your next session."

                total > dailyGoal ->
                    "🎯 You're above your daily goal. Try reducing your next scrolling session."

                else ->
                    "👍 Keep checking your patterns and make small improvements."
            }

        addCard(
            "ScrollCheck says",
            message,
            "Got it"
        ) {}
    }

    // ==========================================
    // GOAL
    // ==========================================

    private fun addGoalCard() {

        val box =
            createCard()

        addTextToBox(
            box,
            "Current goal: $dailyGoal minutes",
            18,
            Color.WHITE
        )

        val button =
            Button(this).apply {

                text =
                    "Change Goal"

                setOnClickListener {

                    val options =
                        arrayOf(
                            "30 minutes",
                            "45 minutes",
                            "60 minutes",
                            "90 minutes",
                            "120 minutes"
                        )

                    AlertDialog.Builder(
                        this@MainActivity
                    )
                        .setTitle(
                            "Choose daily goal"
                        )
                        .setItems(
                            options
                        ) { _, which ->

                            dailyGoal =
                                when (which) {
                                    0 -> 30
                                    1 -> 45
                                    2 -> 60
                                    3 -> 90
                                    else -> 120
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
            }

        box.addView(button)

        root.addView(
            box,
            cardParams()
        )
    }

    // ==========================================
    // CHALLENGE
    // ==========================================

    private fun addChallengeCard(
        entertainment: Int,
        total: Long
    ) {

        val target =
            if (total > 0)
                (entertainment * 0.90)
                    .roundToInt()
            else 0

        addCard(
            "🎯 Reduce entertainment scrolling",
            "Try to keep entertainment below $target minutes today.\n\nReward: +50 ⭐",
            "Challenge"
        ) {}
    }

    // ==========================================
    // STREAK
    // ==========================================

    private fun addStreakCard() {

        val streak =
            prefs.getInt(
                "streak",
                1
            )

        addCard(
            "🔥 $streak Day Streak",
            "Keep checking your daily goal and improving your scrolling habits.",
            "Continue"
        ) {}
    }

    // ==========================================
    // REWARDS
    // ==========================================

    private fun addRewardsCard() {

        val points =
            prefs.getInt(
                "points",
                0
            )

        val level =
            (points / 100) + 1

        addCard(
            "⭐ $points ScrollPoints",
            "Level $level — Focus Builder\n\nNext reward: ${100 - (points % 100)} points remaining.",
            "Rewards"
        ) {}
    }

    // ==========================================
    // RESET
    // ==========================================

    private fun addResetCard() {

        addCard(
            "🧘 Take a 5-minute reset",
            "Look away from the screen, stretch, drink some water and walk around.",
            "START RESET"
        ) {

            startResetTimer()
        }
    }

    private fun startResetTimer() {

        AlertDialog.Builder(this)
            .setTitle(
                "🧘 5-Minute Reset"
            )
            .setMessage(
                "Take a short break.\n\n" +
                        "👀 Look away\n" +
                        "🧘 Stretch\n" +
                        "💧 Drink water\n" +
                        "🚶 Walk around\n\n" +
                        "Timer: 5 minutes"
            )
            .setPositiveButton(
                "Done"
            ) { _, _ ->

                Toast.makeText(
                    this,
                    "+10 ScrollPoints ⭐",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton(
                "Cancel",
                null
            )
            .show()
    }

    // ==========================================
    // HELPERS
    // ==========================================

    private fun addUsageCard(
        name: String,
        minutes: Long
    ) {

        val box =
            createCard()

        addTextToBox(
            box,
            name,
            18,
            Color.WHITE,
            true
        )

        addTextToBox(
            box,
            formatMinutes(minutes),
            15,
            Color.LTGRAY
        )

        root.addView(
            box,
            cardParams()
        )
    }

    private fun addCategory(
        name: String,
        minutes: Int
    ) {

        val box =
            createCard()

        addTextToBox(
            box,
            "$name     $minutes min",
            16,
            Color.WHITE
        )

        root.addView(
            box,
            cardParams()
        )
    }

    private fun addBigCard(
        title: String,
        subtitle: String
    ) {

        val box =
            createCard()

        addTextToBox(
            box,
            title,
            32,
            Color.WHITE,
            true
        )

        addTextToBox(
            box,
            subtitle,
            15,
            Color.LTGRAY
        )

        root.addView(
            box,
            cardParams()
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
                    Color.WHITE
                )

                setTypeface(
                    typeface,
                    android.graphics.Typeface.BOLD
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

    private fun addCard(
        title: String,
        body: String,
        button: String,
        action: () -> Unit
    ) {

        val box =
            createCard()

        addTextToBox(
            box,
            title,
            19,
            Color.WHITE,
            true
        )

        addTextToBox(
            box,
            body,
            14,
            Color.LTGRAY
        )

        val buttonView =
            Button(this).apply {

                text =
                    button

                setOnClickListener {
                    action()
                }
            }

        box.addView(buttonView)

        root.addView(
            box,
            cardParams()
        )
    }

    private fun createCard():
            LinearLayout {

        return LinearLayout(this).apply {

            orientation =
                LinearLayout.VERTICAL

            setPadding(
                dp(18),
                dp(18),
                dp(18),
                dp(18)
            )

            setBackgroundColor(
                Color.rgb(
                    27,
                    27,
                    29
                )
            )

            elevation =
                dp(3).toFloat()
        }
    }

    private fun addTextToBox(
        box: LinearLayout,
        text: String,
        size: Int,
        color: Int,
        bold: Boolean = false
    ) {

        val view =
            TextView(this).apply {

                this.text =
                    text

                textSize =
                    size.toFloat()

                setTextColor(
                    color
                )

                if (bold) {

                    setTypeface(
                        typeface,
                        android.graphics.Typeface.BOLD
                    )
                }

                setPadding(
                    0,
                    dp(6),
                    0,
                    dp(6)
                )
            }

        box.addView(view)
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
                        android.graphics.Typeface.BOLD
                    )
                }

                setPadding(
                    0,
                    dp(6),
                    0,
                    dp(6)
                )
            }

        root.addView(text)
    }

    private fun cardParams():
            LinearLayout.LayoutParams {

        val params =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

        params.setMargins(
            0,
            dp(5),
            0,
            dp(10)
        )

        return params
    }

    private fun buttonParams():
            LinearLayout.LayoutParams {

        val params =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

        params.setMargins(
            0,
            dp(14),
            0,
            dp(8)
        )

        return params
    }

    private fun space(
        height: Int
    ) {

        val view =
            View(this)

        root.addView(
            view,
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

        return if (hours > 0) {
            "${hours}h ${mins}m"
        } else {
            "${mins}m"
        }
    }
}
