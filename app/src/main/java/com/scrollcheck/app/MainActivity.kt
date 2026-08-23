package com.scrollcheck.app

import android.app.Activity
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : Activity() {

    private lateinit var root: LinearLayout

    private val youtubePackage = "com.google.android.youtube"
    private val instagramPackage = "com.instagram.android"

    private val prefs by lazy {
        getSharedPreferences("scrollcheck", Context.MODE_PRIVATE)
    }

    private var dailyGoal = 60L
    private var scrollPoints = 0
    private var streak = 0
    private var lastCompletedDate = ""

    private var resetTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        dailyGoal = prefs.getLong("daily_goal", 60L)
        scrollPoints = prefs.getInt("scroll_points", 0)
        streak = prefs.getInt("streak", 0)
        lastCompletedDate =
            prefs.getString("last_completed_date", "") ?: ""

        buildUi()
    }

    override fun onResume() {
        super.onResume()

        if (::root.isInitialized) {
            refreshDashboard()
        }
    }

    override fun onDestroy() {
        resetTimer?.cancel()
        super.onDestroy()
    }

    // =========================================================
    // USAGE ACCESS
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

    // =========================================================
    // USAGE
    // =========================================================

    private fun todayUsage(packageName: String): Long {

        if (!hasUsageAccess()) return 0L

        val manager =
            getSystemService(Context.USAGE_STATS_SERVICE)
                    as UsageStatsManager

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val stats = manager.queryAndAggregateUsageStats(
            calendar.timeInMillis,
            System.currentTimeMillis()
        )

        return (
            stats[packageName]?.totalTimeInForeground ?: 0L
        ) / 60000L
    }

    private fun lateNightUsage(packageName: String): Long {

        if (!hasUsageAccess()) return 0L

        val manager =
            getSystemService(Context.USAGE_STATS_SERVICE)
                    as UsageStatsManager

        val calendar = Calendar.getInstance()

        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val end = Calendar.getInstance()

        val events = manager.queryEvents(
            start.timeInMillis,
            end.timeInMillis
        )

        val event = UsageEvents.Event()

        var lastStart = 0L
        var total = 0L

        while (events.hasNextEvent()) {

            events.getNextEvent(event)

            if (event.packageName != packageName) continue

            if (
                event.eventType ==
                UsageEvents.Event.ACTIVITY_RESUMED
            ) {
                lastStart = event.timeStamp

            } else if (
                event.eventType ==
                UsageEvents.Event.ACTIVITY_PAUSED
            ) {

                if (lastStart > 0L) {

                    val startDate =
                        Calendar.getInstance().apply {
                            timeInMillis = lastStart
                        }

                    val hour =
                        startDate.get(Calendar.HOUR_OF_DAY)

                    if (hour >= 23 || hour < 5) {
                        total +=
                            (event.timeStamp - lastStart)
                    }

                    lastStart = 0L
                }
            }
        }

        return total / 60000L
    }

    // =========================================================
    // MAIN DASHBOARD
    // =========================================================

    private fun buildUi() {

        root = LinearLayout(this).apply {

            orientation = LinearLayout.VERTICAL

            setPadding(
                dp(20),
                dp(28),
                dp(20),
                dp(30)
            )

            setBackgroundColor(
                Color.rgb(246, 247, 251)
            )
        }

        val scrollView = ScrollView(this)

        scrollView.addView(root)

        setContentView(scrollView)

        refreshDashboard()
    }

    private fun refreshDashboard() {

        root.removeAllViews()

        val youtube =
            todayUsage(youtubePackage)

        val instagram =
            todayUsage(instagramPackage)

        val total =
            youtube + instagram

        val lateNight =
            lateNightUsage(youtubePackage) +
                    lateNightUsage(instagramPackage)

        addText(
            "SCROLLCHECK",
            14,
            Color.rgb(91, 92, 226),
            true
        )

        addText(
            "Take control of your scroll.",
            30,
            Color.rgb(23, 32, 51),
            true
        )

        addText(
            "Track → Understand → Improve → Reward",
            15,
            Color.DKGRAY,
            false
        )

        space(10)

        if (!hasUsageAccess()) {

            addCard(
                "🔐 Usage Access Required",
                "ScrollCheck needs Android Usage Access to measure app usage. Your usage data is kept locally in this prototype.",
                "Grant Access"
            ) {

                startActivity(
                    Intent(
                        Settings.ACTION_USAGE_ACCESS_SETTINGS
                    )
                )
            }
        }

        // -----------------------------------------------------
        // TODAY
        // -----------------------------------------------------

        addSectionTitle("Today's Scroll")

        addBigCard(
            "⏱️ $total minutes",
            when {
                total == 0L ->
                    "No tracked usage yet."

                total <= dailyGoal ->
                    "✅ Within your $dailyGoal minute goal"

                else ->
                    "⚠️ $dailyGoal minute goal exceeded"
            }
        )

        // -----------------------------------------------------
        // SCORE
        // -----------------------------------------------------

        val score =
            calculateScore(total)

        addSectionTitle("Scroll Balance")

        addScoreCard(score)

        // -----------------------------------------------------
        // APP BREAKDOWN
        // -----------------------------------------------------

        addSectionTitle("Where You Scrolled")

        addUsageCard(
            "▶️ YouTube",
            youtube
        )

        addUsageCard(
            "📸 Instagram",
            instagram
        )

        // -----------------------------------------------------
        // CATEGORIES
        // -----------------------------------------------------

        addSectionTitle("Usage Categories")

        val educational =
            (total * 0.25).roundToInt()

        val useful =
            (total * 0.10).roundToInt()

        val entertainment =
            (total * 0.60).roundToInt()

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

        addText(
            "ⓘ Categories are estimated in this prototype.",
            12,
            Color.GRAY,
            false
        )

        // -----------------------------------------------------
        // LATE NIGHT
        // -----------------------------------------------------

        addSectionTitle("🌙 Late-Night Scrolling")

        addBigCard(
            "$lateNight minutes",
            if (lateNight > 0) {
                "Late-night usage detected between 11 PM and 5 AM."
            } else {
                "No late-night usage detected."
            }
        )

        // -----------------------------------------------------
        // FEEDBACK
        // -----------------------------------------------------

        addSectionTitle("Your Insight")

        addFeedback(
            total,
            educational,
            useful,
            entertainment,
            lateNight
        )

        // -----------------------------------------------------
        // DAILY GOAL
        // -----------------------------------------------------

        addSectionTitle("🎯 Daily Goal")

        addGoalCard(total)

        // -----------------------------------------------------
        // CHALLENGE
        // -----------------------------------------------------

        addSectionTitle("🎯 Today's Challenge")

        addChallengeCard(
            total,
            entertainment
        )

        // -----------------------------------------------------
        // REWARDS
        // -----------------------------------------------------

        addSectionTitle("🏆 Rewards")

        addRewardsCard(total)

        // -----------------------------------------------------
        // RESET
        // -----------------------------------------------------

        addSectionTitle("🧘 Improve")

        addResetCard()

        // -----------------------------------------------------
        // WEEKLY
        // -----------------------------------------------------

        addSectionTitle("📊 Weekly Dashboard")

        addWeeklyDashboard()

        // -----------------------------------------------------
        // REFRESH
        // -----------------------------------------------------

        val refreshButton =
            Button(this).apply {

                text = "🔄 Refresh Usage"

                setOnClickListener {

                    refreshDashboard()

                    Toast.makeText(
                        this@MainActivity,
                        "Usage data refreshed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

        root.addView(refreshButton)

        addText(
            "Privacy-first prototype • Data stays on this device",
            12,
            Color.GRAY,
            false
        )

        addText(
            "Last updated: " +
                    SimpleDateFormat(
                        "h:mm a",
                        Locale.getDefault()
                    ).format(Date()),
            12,
            Color.GRAY,
            false
        )
    }

    // =========================================================
    // SCORE
    // =========================================================

    private fun calculateScore(
        total: Long
    ): Int {

        if (total == 0L) return 100

        var score = 100.0

        if (total > dailyGoal) {

            score -=
                (total - dailyGoal) * 0.35
        }

        if (total > 120) {
            score -= 10
        }

        return score
            .roundToInt()
            .coerceIn(0, 100)
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

                textSize = 36f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    Color.rgb(
                        23,
                        32,
                        51
                    )
                )

                setTypeface(
                    typeface,
                    android.graphics.Typeface.BOLD
                )
            }

        val status =
            TextView(this).apply {

                text =
                    scoreStatus(score)

                textSize = 16f

                gravity =
                    Gravity.CENTER

                setPadding(
                    0,
                    dp(8),
                    0,
                    dp(8)
                )
            }

        box.addView(scoreText)
        box.addView(status)

        root.addView(
            box,
            cardParams()
        )
    }

    // =========================================================
    // FEEDBACK
    // =========================================================

    private fun addFeedback(
        total: Long,
        educational: Int,
        useful: Int,
        entertainment: Int,
        lateNight: Long
    ) {

        val productive =
            educational + useful

        val percentage =
            if (total > 0) {

                (
                    productive.toDouble() /
                            total.toDouble() *
                            100
                ).roundToInt()

            } else {
                0
            }

        val message =
            when {

                total == 0L ->
                    "📱 Start using supported apps and ScrollCheck will begin building your picture."

                lateNight >= 30 ->
                    "🌙 Late-night scrolling is noticeable today. Consider creating a no-scroll period before bedtime."

                percentage >= 50 ->
                    "📚 Great! A significant portion of your tracked time is estimated to be educational or useful."

                entertainment >
                        total * 0.5 ->
                    "🎭 Entertainment scrolling is taking up most of your tracked time. Try a 5-minute reset before your next session."

                total > dailyGoal ->
                    "🎯 You're above today's goal. Try reducing your next session and aim to get closer to your target."

                else ->
                    "👍 Your usage is within your current target. Keep watching your pattern and make small improvements."
            }

        addCard(
            "💡 ScrollCheck Insight",
            "$message\n\nEstimated educational + useful: $percentage%",
            "Got it"
        ) {}
    }

    // =========================================================
    // DAILY GOAL
    // =========================================================

    private fun addGoalCard(
        total: Long
    ) {

        val box =
            createCard()

        val goalText =
            TextView(this).apply {

                text =
                    "Daily goal: $dailyGoal minutes"

                textSize = 18f

                setTextColor(
                    Color.rgb(
                        23,
                        32,
                        51
                    )
                )

                setTypeface(
                    typeface,
                    android.graphics.Typeface.BOLD
                )
            }

        box.addView(goalText)

        val seek =
            SeekBar(this).apply {

                max = 180

                progress =
                    dailyGoal
                        .toInt()
                        .coerceIn(
                            15,
                            180
                        )

                setOnSeekBarChangeListener(
                    object :
                        SeekBar.OnSeekBarChangeListener {

                        override fun onProgressChanged(
                            seekBar: SeekBar?,
                            progress: Int,
                            fromUser: Boolean
                        ) {

                            val value =
                                progress.coerceAtLeast(
                                    15
                                )

                            goalText.text =
                                "Daily goal: $value minutes"

                            if (fromUser) {

                                dailyGoal =
                                    value.toLong()

                                prefs.edit()
                                    .putLong(
                                        "daily_goal",
                                        dailyGoal
                                    )
                                    .apply()
                            }
                        }

                        override fun
                            onStartTrackingTouch(
                                seekBar: SeekBar?
                            ) {}

                        override fun
                            onStopTrackingTouch(
                                seekBar: SeekBar?
                            ) {}
                    }
                )
            }

        box.addView(seek)

        val status =
            TextView(this).apply {

                text =
                    when {

                        total == 0L ->
                            "📱 No tracked usage yet."

                        total <= dailyGoal ->
                            "✅ Within your goal."

                        else ->
                            "⚠️ Goal exceeded."
                    }

                textSize = 14f

                setTextColor(
                    Color.DKGRAY
                )

                setPadding(
                    0,
                    dp(8),
                    0,
                    0
                )
            }

        box.addView(status)

        root.addView(
            box,
            cardParams()
        )
    }

    // =========================================================
    // CHALLENGE
    // =========================================================

    private fun addChallengeCard(
        total: Long,
        entertainment: Int
    ) {

        val box =
            createCard()

        val challengeText =
            TextView(this).apply {

                text =
                    "Reduce entertainment scrolling by 10% today."

                textSize = 17f

                setTextColor(
                    Color.rgb(
                        23,
                        32,
                        51
                    )
                )

                setTypeface(
                    typeface,
                    android.graphics.Typeface.BOLD
                )
            }

        box.addView(challengeText)

        val target =
            (dailyGoal * 0.6)
                .roundToInt()

        val progress =
            if (entertainment <= target) {
                100
            } else {
                (
                    target.toDouble() /
                            entertainment.coerceAtLeast(
                                1
                            ).toDouble() *
                            100
                ).roundToInt()
                    .coerceIn(0, 99)
            }

        val progressText =
            TextView(this).apply {

                text =
                    "Progress: $progress%"

                textSize = 15f

                setTextColor(
                    Color.DKGRAY
                )

                setPadding(
                    0,
                    dp(8),
                    0,
                    dp(8)
                )
            }

        box.addView(progressText)

        val reward =
            TextView(this).apply {

                text =
                    "⭐ Reward: +50 ScrollPoints"

                textSize = 14f

                setTextColor(
                    Color.DKGRAY
                )
            }

        box.addView(reward)

        root.addView(
            box,
            cardParams()
        )
    }

    // =========================================================
    // REWARDS
    // =========================================================

    private fun addRewardsCard(
        total: Long
    ) {

        val box =
            createCard()

        val completedToday =
            lastCompletedDate ==
                    todayKey()

        val goalCompleted =
            total > 0 &&
                    total <= dailyGoal

        val title =
            TextView(this).apply {

                text =
                    "🔥 $streak Day Streak"

                textSize = 22f

                setTextColor(
                    Color.rgb(
                        23,
                        32,
                        51
                    )
                )

                setTypeface(
                    typeface,
                    android.graphics.Typeface.BOLD
                )
            }

        val points =
            TextView(this).apply {

                text =
                    "⭐ $scrollPoints ScrollPoints"

                textSize = 18f

                setTextColor(
                    Color.rgb(
                        23,
                        32,
                        51
                    )
                )

                setPadding(
                    0,
                    dp(8),
                    0,
                    dp(4)
                )
            }

        val level =
            TextView(this).apply {

                text =
                    getLevelText()

                textSize = 15f

                setTextColor(
                    Color.DKGRAY
                )

                setPadding(
                    0,
                    dp(4),
                    0,
                    dp(10)
                )
            }

        box.addView(title)
        box.addView(points)
        box.addView(level)

        if (
            goalCompleted &&
            !completedToday
        ) {

            val button =
                Button(this).apply {

                    text =
                        "✅ Complete Today's Goal"

                    setOnClickListener {

                        completeTodayGoal()

                        refreshDashboard()

                        Toast.makeText(
                            this@MainActivity,
                            "+50 ScrollPoints! 🎉",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

            box.addView(button)

        } else if (completedToday) {

            val completed =
                TextView(this).apply {

                    text =
                        "🎉 Today's goal completed!"

                    textSize = 15f

                    setTextColor(
                        Color.rgb(
                            40,
                            130,
                            70
                        )
                    )
                }

            box.addView(completed)

        } else {

            val info =
                TextView(this).apply {

                    text =
                        "Complete your daily goal to earn +50 ScrollPoints."

                    textSize = 14f

                    setTextColor(
                        Color.DKGRAY
                    )
                }

            box.addView(info)
        }

        root.addView(
            box,
            cardParams()
        )
    }

    private fun completeTodayGoal() {

        val today =
            todayKey()

        if (
            lastCompletedDate ==
                    today
        ) {
            return
        }

        streak++

        scrollPoints += 50

        lastCompletedDate =
            today

        prefs.edit()
            .putInt(
                "scroll_points",
                scrollPoints
            )
            .putInt(
                "streak",
                streak
            )
            .putString(
                "last_completed_date",
                lastCompletedDate
            )
            .apply()
    }

    private fun getLevelText(): String {

        return when {

            scrollPoints >= 1000 ->
                "🏆 Level 6 — Scroll Champion"

            scrollPoints >= 500 ->
                "🏆 Level 5 — Focus Champion"

            scrollPoints >= 300 ->
                "🌟 Level 4 — Focus Master"

            scrollPoints >= 150 ->
                "🌳 Level 3 — Focus Builder"

            scrollPoints >= 50 ->
                "⭐ Level 2 — Getting Focused"

            else ->
                "🌱 Level 1 — Starting Out"
        }
    }

    // =========================================================
    // 5 MINUTE RESET
    // =========================================================

    private fun addResetCard() {

        val box =
            createCard()

        val title =
            TextView(this).apply {

                text =
                    "🧘 5-Minute Reset"

                textSize = 21f

                setTextColor(
                    Color.rgb(
                        23,
                        32,
                        51
                    )
                )

                setTypeface(
                    typeface,
                    android.graphics.Typeface.BOLD
                )
            }

        box.addView(title)

        val description =
            TextView(this).apply {

                text =
                    "Look away 👀\nStretch 🧘\nDrink water 💧\nWalk 🚶"

                textSize = 15f

                setTextColor(
                    Color.DKGRAY
                )

                setPadding(
                    0,
                    dp(8),
                    0,
                    dp(10)
                )
            }

        box.addView(description)

        val timer =
            TextView(this).apply {

                text =
                    "05:00"

                textSize = 34f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    Color.rgb(
                        23,
                        32,
                        51
                    )
                )

                setTypeface(
                    typeface,
                    android.graphics.Typeface.BOLD
                )
            }

        box.addView(timer)

        val button =
            Button(this).apply {

                text =
                    "▶️ Start Reset"

                setOnClickListener {

                    resetTimer?.cancel()

                    text =
                        "⏸ Reset Running..."

                    isEnabled =
                        false

                    resetTimer =
                        object :
                            CountDownTimer(
                                300000L,
                                1000L
                            ) {

                            override fun
                                onTick(
                                    millisUntilFinished:
                                        Long
                                ) {

                                val seconds =
                                    millisUntilFinished /
                                            1000L

                                val minutes =
                                    seconds / 60

                                val remaining =
                                    seconds % 60

                                timer.text =
                                    String.format(
                                        Locale.getDefault(),
                                        "%02d:%02d",
                                        minutes,
                                        remaining
                                    )
                            }

                            override fun
                                onFinish() {

                                timer.text =
                                    "00:00"

                                scrollPoints +=
                                    20

                                prefs.edit()
                                    .putInt(
                                        "scroll_points",
                                        scrollPoints
                                    )
                                    .apply()

                                Toast.makeText(
                                    this@MainActivity,
                                    "Reset complete! +20 ScrollPoints 🎉",
                                    Toast.LENGTH_LONG
                                ).show()

                                refreshDashboard()
                            }

                        }.start()
                }
            }

        box.addView(button)

        root.addView(
            box,
            cardParams()
        )
    }

    // =========================================================
    // WEEKLY DASHBOARD
    // =========================================================

    private fun addWeeklyDashboard() {

        val box =
            createCard()

        val today =
            Calendar.getInstance()

        val totalToday =
            todayUsage(youtubePackage) +
                    todayUsage(instagramPackage)

        saveTodayUsage(totalToday)

        val weekTotal =
            getStoredWeekTotal()

        val lastWeekTotal =
            getStoredLastWeekTotal()

        val comparison =
            if (lastWeekTotal > 0) {

                val difference =
                    (
                        (
                            lastWeekTotal -
                                    weekTotal
                        ).toDouble() /
                                lastWeekTotal.toDouble() *
                                100
                    ).roundToInt()

                if (difference >= 0) {
                    "📉 You reduced your scrolling by $difference%."
                } else {
                    "📈 Your scrolling increased by ${-difference}%."
                }

            } else {
                "Build more history to compare weeks."
            }

        val title =
            TextView(this).apply {

                text =
                    "This week: ${formatMinutes(weekTotal)}"

                textSize = 20f

                setTextColor(
                    Color.rgb(
                        23,
                        32,
                        51
                    )
                )

                setTypeface(
                    typeface,
                    android.graphics.Typeface.BOLD
                )
            }

        box.addView(title)

        val previous =
            TextView(this).apply {

                text =
                    "Last week: ${formatMinutes(lastWeekTotal)}"

                textSize = 16f

                setTextColor(
                    Color.DKGRAY
                )

                setPadding(
                    0,
                    dp(6),
                    0,
                    dp(6)
                )
            }

        box.addView(previous)

        val chart =
            TextView(this).apply {

                text =
                    buildWeeklyBars()

                textSize = 13f

                setTextColor(
                    Color.rgb(
                        23,
                        32,
                        51
                    )
                )

                setPadding(
                    0,
                    dp(10),
                    0,
                    dp(10)
                )
            }

        box.addView(chart)

        val comparisonText =
            TextView(this).apply {

                text =
                    comparison

                textSize = 15f

                setTextColor(
                    Color.DKGRAY
                )
            }

        box.addView(comparisonText)

        root.addView(
            box,
            cardParams()
        )
    }

    private fun saveTodayUsage(
        total: Long
    ) {

        prefs.edit()
            .putLong(
                "usage_" + todayKey(),
                total
            )
            .apply()
    }

    private fun getStoredWeekTotal(): Long {

        var total = 0L

        for (i in 0..6) {

            val calendar =
                Calendar.getInstance()

            calendar.add(
                Calendar.DAY_OF_YEAR,
                -i
            )

            val key =
                SimpleDateFormat(
                    "yyyy-MM-dd",
                    Locale.getDefault()
                ).format(
                    calendar.time
                )

            total +=
                prefs.getLong(
                    "usage_$key",
                    if (i == 0) {
                        todayUsage(
                            youtubePackage
                        ) +
                                todayUsage(
                                    instagramPackage
                                )
                    } else {
                        0L
                    }
                )
        }

        return total
    }

    private fun getStoredLastWeekTotal(): Long {

        var total = 0L

        for (i in 7..13) {

            val calendar =
                Calendar.getInstance()

            calendar.add(
                Calendar.DAY_OF_YEAR,
                -i
            )

            val key =
                SimpleDateFormat(
                    "yyyy-MM-dd",
                    Locale.getDefault()
                ).format(
                    calendar.time
                )

            total +=
                prefs.getLong(
                    "usage_$key",
                    0L
                )
        }

        return total
    }

    private fun buildWeeklyBars(): String {

        val labels =
            listOf(
                "M",
                "T",
                "W",
                "T",
                "F",
                "S",
                "S"
            )

        val output =
            StringBuilder()

        output.append(
            "WEEKLY SCREEN TIME\n\n"
        )

        for (label in labels) {

            output.append(
                "$label  "
            )

            val bar =
                "█".repeat(
                    ((dailyGoal / 10)
                        .coerceAtLeast(1))
                )

            output.append(
                bar
            )

            output.append("\n")
        }

        return output.toString()
    }

    private fun formatMinutes(
        minutes: Long
    ): String {

        val hours =
            minutes / 60

        val mins =
            minutes % 60

        return "${hours}h ${mins}m"
    }

    // =========================================================
    // COMMON UI
    // =========================================================

    private fun addUsageCard(
        name: String,
        minutes: Long
    ) {

        val box =
            createCard()

        val title =
            TextView(this).apply {

                text =
                    name

                textSize = 18f

                setTextColor(
                    Color.rgb(
                        23,
                        32,
                        51
                    )
                )

                setTypeface(
                    typeface,
                    android.graphics.Typeface.BOLD
                )
            }

        val time =
            TextView(this).apply {

                text =
                    "$minutes minutes today"

                textSize = 15f

                setTextColor(
                    Color.DKGRAY
                )

                setPadding(
                    0,
                    dp(6),
                    0,
                    0
                )
            }

        box.addView(title)
        box.addView(time)

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

        val text =
            TextView(this).apply {

                this.text =
                    "$name     $minutes min"

                textSize = 16f

                setTextColor(
                    Color.rgb(
                        23,
                        32,
                        51
                    )
                )
            }

        box.addView(text)

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

        val titleText =
            TextView(this).apply {

                text =
                    title

                textSize = 30f

                setTextColor(
                    Color.rgb(
                        23,
                        32,
                        51
                    )
                )

                setTypeface(
                    typeface,
                    android.graphics.Typeface.BOLD
                )
            }

        val sub =
            TextView(this).apply {

                text =
                    subtitle

                textSize = 15f

                setTextColor(
                    Color.DKGRAY
                )

                setPadding(
                    0,
                    dp(8),
                    0,
                    0
                )
            }

        box.addView(titleText)
        box.addView(sub)

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
                    Color.rgb(
                        23,
                        32,
                        51
                    )
                )

                setTypeface(
                    typeface,
                    android.graphics.Typeface.BOLD
                )

                setPadding(
                    0,
                    dp(12),
                    0,
                    dp(4)
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

        val titleText =
            TextView(this).apply {

                text =
                    title

                textSize = 19f

                setTextColor(
                    Color.rgb(
                        23,
                        32,
                        51
                    )
                )

                setTypeface(
                    typeface,
                    android.graphics.Typeface.BOLD
                )
            }

        val bodyText =
            TextView(this).apply {

                text =
                    body

                textSize = 14f

                setTextColor(
                    Color.DKGRAY
                )

                setPadding(
                    0,
                    dp(8),
                    0,
                    dp(10)
                )
            }

        val buttonView =
            Button(this).apply {

                text =
                    button

                setOnClickListener {
                    action()
                }
            }

        box.addView(titleText)
        box.addView(bodyText)
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
                dp(20),
                dp(18),
                dp(20),
                dp(18)
            )

            setBackgroundColor(
                Color.WHITE
            )

            elevation =
                dp(4).toFloat()
        }
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
            dp(6),
            0,
            dp(10)
        )

        return params
    }

    private fun addText(
        value: String,
        size: Int,
        color: Int,
        bold: Boolean
    ) {

        val textView =
            TextView(this).apply {

                text =
                    value

                textSize =
                    size.toFloat()

                setTextColor(color)

                if (bold) {

                    setTypeface(
                        typeface,
                        android.graphics.Typeface.BOLD
                    )
                }

                setPadding(
                    0,
                    dp(10),
                    0,
                    dp(10)
                )
            }

        root.addView(textView)
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
                    resources
                        .displayMetrics
                        .density
            ).roundToInt()
    }

    private fun todayKey(): String {

        return SimpleDateFormat(
            "yyyy-MM-dd",
            Locale.getDefault()
        ).format(Date())
    }
}
    
