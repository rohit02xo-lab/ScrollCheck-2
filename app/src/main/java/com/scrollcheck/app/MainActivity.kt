cd ScrollCheck-2 && git checkout main && git pull && rm -f app/src/main/java/com/scrollcheck/app/MainActivity.kt && cat > app/src/main/java/com/scrollcheck/app/MainActivity.kt <<'EOF'
package com.scrollcheck.app

import android.app.Activity
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

    private fun hasUsageAccess(): Boolean {

        val appOps =
            getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager

        return appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        ) == AppOpsManager.MODE_ALLOWED
    }

    private fun todayUsage(packageName: String): Long {

        if (!hasUsageAccess()) return 0L

        val usageManager =
            getSystemService(Context.USAGE_STATS_SERVICE)
                    as UsageStatsManager

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val stats = usageManager.queryAndAggregateUsageStats(
            calendar.timeInMillis,
            System.currentTimeMillis()
        )

        return (stats[packageName]?.totalTimeInForeground ?: 0L) / 60000L
    }

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

        val youtube = todayUsage(youtubePackage)
        val instagram = todayUsage(instagramPackage)
        val total = youtube + instagram

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
                "Android needs permission before ScrollCheck can measure YouTube and Instagram usage.",
                "Grant Access"
            ) {
                startActivity(
                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                )
            }
        }

        addSectionTitle("Today's Scroll")

        addBigCard(
            "⏱️ $total minutes",
            if (total <= dailyGoal) {
                "✅ Within your $dailyGoal minute goal"
            } else {
                "⚠️ $dailyGoal minute goal exceeded"
            }
        )

        val score = calculateScore(total)

        addSectionTitle("Scroll Balance")

        addScoreCard(score)

        addSectionTitle("Where You Scrolled")

        addUsageCard(
            "▶️ YouTube",
            youtube
        )

        addUsageCard(
            "📸 Instagram",
            instagram
        )

        addSectionTitle("Usage Categories")

        val estimatedEntertainment =
            (total * 0.60).roundToInt()

        val estimatedLearning =
            (total * 0.25).roundToInt()

        val estimatedUseful =
            (total * 0.10).roundToInt()

        val estimatedUnclassified =
            (total * 0.05).roundToInt()

        addCategory(
            "📚 Educational",
            estimatedLearning
        )

        addCategory(
            "🛠️ Skill / Useful",
            estimatedUseful
        )

        addCategory(
            "🎭 Entertainment",
            estimatedEntertainment
        )

        addCategory(
            "🔍 Unclassified",
            estimatedUnclassified
        )

        addSectionTitle("Your Insight")

        addFeedback(
            total,
            estimatedLearning,
            estimatedUseful,
            estimatedEntertainment
        )

        addSectionTitle("🎯 Daily Goal")

        addGoalCard(total)

        addSectionTitle("🏆 Rewards")

        addRewardsCard(total)

        val refreshButton = Button(this).apply {

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

    private fun calculateScore(total: Long): Int {

        if (total == 0L) return 100

        var score = 100.0

        if (total > dailyGoal) {

            val excess = total - dailyGoal
            score -= excess * 0.35
        }

        if (total > 120) {
            score -= 10
        }

        return score
            .roundToInt()
            .coerceIn(0, 100)
    }

    private fun scoreStatus(score: Int): String {

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

    private fun addScoreCard(score: Int) {

        val box = createCard()

        val scoreText = TextView(this).apply {

            text = "$score / 100"
            textSize = 36f

            setTextColor(
                Color.rgb(23, 32, 51)
            )

            gravity = Gravity.CENTER

            setTypeface(
                typeface,
                android.graphics.Typeface.BOLD
            )
        }

        val status = TextView(this).apply {

            text = scoreStatus(score)
            textSize = 16f
            gravity = Gravity.CENTER

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

    private fun addFeedback(
        total: Long,
        learning: Int,
        useful: Int,
        entertainment: Int
    ) {

        val productive = learning + useful

        val productivePercentage =
            if (total > 0)
                ((productive.toDouble() / total) * 100)
                    .roundToInt()
            else 0

        val message = when {

            total == 0L ->
                "📱 Start using YouTube or Instagram and ScrollCheck will show your usage here."

            productivePercentage >= 50 ->
                "📚 Great! A significant portion of your tracked time is estimated to be educational or useful."

            entertainment > total * 0.5 ->
                "🎭 Entertainment scrolling is taking up most of your tracked short-video time.\n\nTry a 5-minute reset before your next session."

            total > dailyGoal ->
                "🎯 You're above today's goal. Try a shorter session next time and see if you can get closer to your target."

            else ->
                "👍 Your scrolling is being tracked. Keep checking your patterns and make small improvements."
        }

        val productiveText =
            "Estimated educational + useful: $productivePercentage%"

        addCard(
            "💡 ScrollCheck Insight",
            "$message\n\n$productiveText",
            "Got it"
        ) {}
    }

    private fun addGoalCard(total: Long) {

        val box = createCard()

        val goalText = TextView(this).apply {

            text = "Daily goal: $dailyGoal minutes"
            textSize = 18f

            setTextColor(
                Color.rgb(23, 32, 51)
            )

            setTypeface(
                typeface,
                android.graphics.Typeface.BOLD
            )
        }

        box.addView(goalText)

        val seek = SeekBar(this).apply {

            max = 180

            progress =
                dailyGoal.toInt().coerceIn(0, 180)

            setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {

                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {

                        val value =
                            progress.coerceAtLeast(15)

                        goalText.text =
                            "Daily goal: $value minutes"

                        if (fromUser) {

                            dailyGoal = value.toLong()

                            prefs.edit()
                                .putLong(
                                    "daily_goal",
                                    dailyGoal
                                )
                                .apply()
                        }
                    }

                    override fun onStartTrackingTouch(
                        seekBar: SeekBar?
                    ) {}

                    override fun onStopTrackingTouch(
                        seekBar: SeekBar?
                    ) {}
                }
            )
        }

        box.addView(seek)

        val status = TextView(this).apply {

            text = when {
                total == 0L ->
                    "📱 No tracked usage yet."

                total <= dailyGoal ->
                    "✅ You are currently within your goal."

                else ->
                    "⚠️ You have exceeded today's goal."
            }

            textSize = 14f
            setTextColor(Color.DKGRAY)

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

    private fun addRewardsCard(total: Long) {

        val box = createCard()

        val completedToday =
            lastCompletedDate == todayKey()

        val goalCompleted =
            total > 0 && total <= dailyGoal

        val title = TextView(this).apply {

            text = "🔥 $streak Day Streak"
            textSize = 22f

            setTextColor(
                Color.rgb(23, 32, 51)
            )

            setTypeface(
                typeface,
                android.graphics.Typeface.BOLD
            )
        }

        val pointsText = TextView(this).apply {

            text = "⭐ $scrollPoints ScrollPoints"
            textSize = 18f

            setTextColor(
                Color.rgb(23, 32, 51)
            )

            setPadding(
                0,
                dp(8),
                0,
                dp(4)
            )
        }

        val levelText = TextView(this).apply {

            text = getLevelText()
            textSize = 15f

            setTextColor(Color.DKGRAY)

            setPadding(
                0,
                dp(4),
                0,
                dp(10)
            )
        }

        box.addView(title)
        box.addView(pointsText)
        box.addView(levelText)

        if (goalCompleted && !completedToday) {

            val button = Button(this).apply {

                text = "✅ Complete Today's Goal"

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

            val completedText = TextView(this).apply {

                text = "🎉 Today's goal completed!"
                textSize = 15f

                setTextColor(
                    Color.rgb(40, 130, 70)
                )
            }

            box.addView(completedText)

        } else {

            val info = TextView(this).apply {

                text =
                    "Complete your daily goal to earn +50 ScrollPoints."

                textSize = 14f
                setTextColor(Color.DKGRAY)
            }

            box.addView(info)
        }

        root.addView(
            box,
            cardParams()
        )
    }

    private fun completeTodayGoal() {

        val today = todayKey()

        if (lastCompletedDate == today) {
            return
        }

        streak++
        scrollPoints += 50
        lastCompletedDate = today

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

    private fun todayKey(): String {

        return SimpleDateFormat(
            "yyyy-MM-dd",
            Locale.getDefault()
        ).format(Date())
    }

    private fun addUsageCard(
        name: String,
        minutes: Long
    ) {

        val box = createCard()

        val title = TextView(this).apply {

            text = name
            textSize = 18f

            setTextColor(
                Color.rgb(23, 32, 51)
            )

            setTypeface(
                typeface,
                android.graphics.Typeface.BOLD
            )
        }

        val time = TextView(this).apply {

            text = "$minutes minutes today"
            textSize = 15f

            setTextColor(Color.DKGRAY)

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

        val box = createCard()

        val text = TextView(this).apply {

            this.text = "$name     $minutes min"
            textSize = 16f

            setTextColor(
                Color.rgb(23, 32, 51)
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

        val box = createCard()

        val titleText = TextView(this).apply {

            text = title
            textSize = 30f

            setTextColor(
                Color.rgb(23, 32, 51)
            )

            setTypeface(
                typeface,
                android.graphics.Typeface.BOLD
            )
        }

        val sub = TextView(this).apply {

            text = subtitle
            textSize = 15f

            setTextColor(Color.DKGRAY)

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

    private fun addSectionTitle(title: String) {

        val text = TextView(this).apply {

            this.text = title
            textSize = 20f

            setTextColor(
                Color.rgb(23, 32, 51)
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

        val box = createCard()

        val titleText = TextView(this).apply {

            text = title
            textSize = 19f

            setTextColor(
                Color.rgb(23, 32, 51)
            )

            setTypeface(
                typeface,
                android.graphics.Typeface.BOLD
            )
        }

        val bodyText = TextView(this).apply {

            text = body
            textSize = 14f

            setTextColor(Color.DKGRAY)

            setPadding(
                0,
                dp(8),
                0,
                dp(10)
            )
        }

        val buttonView = Button(this).apply {

            text = button

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

    private fun createCard(): LinearLayout {

        return LinearLayout(this).apply {

            orientation = LinearLayout.VERTICAL

            setPadding(
                dp(20),
                dp(18),
                dp(20),
                dp(18)
            )

            setBackgroundColor(Color.WHITE)

            elevation = dp(4).toFloat()
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

        val textView = TextView(this).apply {

            text = value
            textSize = size.toFloat()
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

    private fun space(height: Int) {

        val view = View(this)

        root.addView(
            view,
            LinearLayout.LayoutParams(
                1,
                dp(height)
            )
        )
    }

    private fun dp(value: Int): Int {

        return (
            value *
                    resources.displayMetrics.density
        ).roundToInt()
    }
}
EOF
git add app/src/main/java/com/scrollcheck/app/MainActivity.kt && git commit -m "Add ScrollPoints streaks and levels" && git push origin main
