package com.scrollcheck.app

import android.app.Activity
import android.app.AppOpsManager
import android.app.AlertDialog
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

    private val apps = listOf(
        App("Instagram", "com.instagram.android", "IG"),
        App("YouTube", "com.google.android.youtube", "YT"),
        App("WhatsApp", "com.whatsapp", "WA"),
        App("X", "com.twitter.android", "X")
    )

    data class App(
        val name: String,
        val packageName: String,
        val shortName: String
    )

    private val navy = Color.rgb(25, 31, 48)
    private val purple = Color.rgb(91, 88, 230)
    private val background = Color.rgb(246, 247, 251)
    private val gray = Color.rgb(105, 112, 130)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        dailyGoal = prefs.getLong("daily_goal", 60L)

        buildUi()
    }

    override fun onResume() {
        super.onResume()

        if (::root.isInitialized) {
            refresh()
        }
    }

    // ---------------------------------------------------------
    // USAGE ACCESS
    // ---------------------------------------------------------

    private fun hasUsageAccess(): Boolean {

        val manager =
            getSystemService(Context.APP_OPS_SERVICE)
                    as AppOpsManager

        return manager.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        ) == AppOpsManager.MODE_ALLOWED
    }

    private fun startOfDay(): Long {

        val calendar = Calendar.getInstance()

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

    // ---------------------------------------------------------
    // REAL USAGE TIME
    // ---------------------------------------------------------

    private fun getUsage(
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
                startOfDay(),
                System.currentTimeMillis()
            )

        return (
            stats[packageName]
                ?.totalTimeInForeground
                ?: 0L
        ) / 60000L
    }

    // ---------------------------------------------------------
    // REAL APP SESSIONS
    // ---------------------------------------------------------

    private fun getSessions(
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
                startOfDay(),
                System.currentTimeMillis()
            )

        val event = UsageEvents.Event()

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

    // ---------------------------------------------------------
    // UI
    // ---------------------------------------------------------

    private fun buildUi() {

        root = LinearLayout(this).apply {

            orientation =
                LinearLayout.VERTICAL

            setPadding(
                dp(18),
                dp(24),
                dp(18),
                dp(30)
            )

            setBackgroundColor(background)
        }

        val scroll =
            ScrollView(this)

        scroll.addView(root)

        setContentView(scroll)

        refresh()
    }

    private fun refresh() {

        root.removeAllViews()

        addHeader()

        if (!hasUsageAccess()) {
            addPermissionCard()
        }

        val usage =
            apps.associate {
                it.name to getUsage(
                    it.packageName
                )
            }

        val sessions =
            apps.associate {
                it.name to getSessions(
                    it.packageName
                )
            }

        val total =
            usage.values.sum()

        addTodayCard(total)

        addSectionTitle(
            "App usage"
        )

        addAppsCard(
            usage,
            sessions
        )

        addSectionTitle(
            "Daily goal"
        )

        addGoalCard(total)

        addSectionTitle(
            "Scroll balance"
        )

        addScoreCard(total)

        addSectionTitle(
            "Most used app"
        )

        addMostUsed(
            usage
        )

        addRefreshButton()

        addFooter()
    }

    // ---------------------------------------------------------
    // HEADER
    // ---------------------------------------------------------

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

    // ---------------------------------------------------------
    // PERMISSION
    // ---------------------------------------------------------

    private fun addPermissionCard() {

        val card =
            createCard(Color.WHITE)

        addCardTitle(
            card,
            "Usage access required"
        )

        addCardBody(
            card,
            "ScrollCheck needs Android Usage Access to show your actual app usage."
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

        addCard(card)
    }

    // ---------------------------------------------------------
    // TODAY
    // ---------------------------------------------------------

    private fun addTodayCard(
        total: Long
    ) {

        val card =
            createCard(navy)

        addSmallText(
            card,
            "TODAY'S TRACKED USAGE"
        )

        val big =
            TextView(this).apply {

                text =
                    formatMinutes(total)

                textSize = 40f

                setTextColor(Color.WHITE)

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )

                setPadding(
                    0,
                    dp(5),
                    0,
                    dp(3)
                )
            }

        card.addView(big)

        val status =
            TextView(this).apply {

                text =
                    if (total <= dailyGoal) {
                        "Within your daily goal"
                    } else {
                        "Daily goal exceeded"
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

        card.addView(status)

        addCard(card)
    }

    // ---------------------------------------------------------
    // APP USAGE
    // ---------------------------------------------------------

    private fun addAppsCard(
        usage: Map<String, Long>,
        sessions: Map<String, Int>
    ) {

        val card =
            createCard(Color.WHITE)

        val sorted =
            apps.sortedByDescending {
                usage[it.name] ?: 0L
            }

        for (app in sorted) {

            val minutes =
                usage[app.name] ?: 0L

            val sessionCount =
                sessions[app.name] ?: 0

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

                    gravity =
                        Gravity.CENTER

                    textSize = 12f

                    setTextColor(
                        Color.WHITE
                    )

                    background =
                        rounded(
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

                    setTextColor(navy)

                    setTypeface(
                        typeface,
                        Typeface.BOLD
                    )
                }

            info.addView(name)

            val session =
                TextView(this).apply {

                    text =
                        "$sessionCount sessions"

                    textSize = 12f

                    setTextColor(gray)

                    setPadding(
                        0,
                        dp(3),
                        0,
                        0
                    )
                }

            info.addView(session)

            row.addView(info)

            val time =
                TextView(this).apply {

                    text =
                        formatMinutes(
                            minutes
                        )

                    textSize = 16f

                    setTextColor(navy)

                    setTypeface(
                        typeface,
                        Typeface.BOLD
                    )
                }

            row.addView(time)

            card.addView(row)
        }

        addCard(card)
    }

    // ---------------------------------------------------------
    // GOAL
    // ---------------------------------------------------------

    private fun addGoalCard(
        total: Long
    ) {

        val card =
            createCard(Color.WHITE)

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

        addCardTitle(
            card,
            "Daily target"
        )

        val progress =
            ProgressBar(
                this,
                null,
                android.R.attr.progressBarStyleHorizontal
            )

        progress.max = 100
        progress.progress =
            percentage

        card.addView(
            progress,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(8)
            )
        )

        addCardBody(
            card,
            "${formatMinutes(total)} used of ${formatMinutes(dailyGoal)} goal"
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

    // ---------------------------------------------------------
    // SCORE
    // ---------------------------------------------------------

    private fun addScoreCard(
        total: Long
    ) {

        val card =
            createCard(Color.WHITE)

        val score =
            calculateScore(total)

        val text =
            TextView(this).apply {

                this.text =
                    "$score / 100"

                textSize = 34f

                gravity =
                    Gravity.CENTER

                setTextColor(navy)

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )
            }

        card.addView(text)

        val status =
            TextView(this).apply {

                this.text =
                    when {
                        score >= 80 ->
                            "Excellent balance"

                        score >= 60 ->
                            "Good balance"

                        score >= 40 ->
                            "Needs attention"

                        else ->
                            "Time for a reset"
                    }

                textSize = 15f

                gravity =
                    Gravity.CENTER

                setTextColor(gray)

                setPadding(
                    0,
                    dp(5),
                    0,
                    0
                )
            }

        card.addView(status)

        addCard(card)
    }

    private fun calculateScore(
        total: Long
    ): Int {

        if (total == 0L) {
            return 100
        }

        var score = 100.0

        if (total > dailyGoal) {

            score -=
                (total - dailyGoal) *
                        0.35
        }

        return score
            .roundToInt()
            .coerceIn(
                0,
                100
            )
    }

    // ---------------------------------------------------------
    // MOST USED
    // ---------------------------------------------------------

    private fun addMostUsed(
        usage: Map<String, Long>
    ) {

        val app =
            apps.maxByOrNull {
                usage[it.name] ?: 0L
            } ?: return

        val minutes =
            usage[app.name] ?: 0L

        val card =
            createCard(Color.WHITE)

        addCardTitle(
            card,
            "${app.shortName}  ${app.name}"
        )

        addCardBody(
            card,
            "${formatMinutes(minutes)} today"
        )

        addCard(card)
    }

    // ---------------------------------------------------------
    // SECTION
    // ---------------------------------------------------------

    private fun addSectionTitle(
        title: String
    ) {

        val text =
            TextView(this).apply {

                this.text =
                    title

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

    // ---------------------------------------------------------
    // CARD
    // ---------------------------------------------------------

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
        body: String
    ) {

        val text =
            TextView(this).apply {

                this.text =
                    body

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
        text: String
    ) {

        val view =
            TextView(this).apply {

                this.text =
                    text

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

        card.addView(view)
    }

    // ---------------------------------------------------------
    // BUTTON
    // ---------------------------------------------------------

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

            stateListAnimator = null
        }
    }

    private fun addRefreshButton() {

        val button =
            createButton(
                "Refresh data"
            )

        button.setOnClickListener {

            refresh()

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
                    dp(12),
                    0,
                    dp(8)
                )
            }
        )
    }

    // ---------------------------------------------------------
    // GOAL DIALOG
    // ---------------------------------------------------------

    private fun showGoalDialog() {

        val values =
            arrayOf(
                "30 minutes",
                "45 minutes",
                "60 minutes",
                "90 minutes",
                "120 minutes"
            )

        AlertDialog.Builder(this)
            .setTitle(
                "Daily usage goal"
            )
            .setItems(
                values
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

                refresh()
            }
            .show()
    }

    // ---------------------------------------------------------
    // FOOTER
    // ---------------------------------------------------------

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

    // ---------------------------------------------------------
    // TEXT
    // ---------------------------------------------------------

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

    // ---------------------------------------------------------
    // HELPERS
    // ---------------------------------------------------------

    private fun appColor(
        app: App
    ): Int {

        return when (app.name) {

            "Instagram" ->
                Color.rgb(
                    220,
                    70,
                    120
                )

            "YouTube" ->
                Color.rgb(
                    220,
                    50,
                    50
                )

            "WhatsApp" ->
                Color.rgb(
                    35,
                    175,
                    105
                )

            else ->
                Color.rgb(
                    30,
                    35,
                    45
                )
        }
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
            "$mins min"
        }
    }
}
