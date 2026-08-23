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
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var root: LinearLayout

    private val youtube = "com.google.android.youtube"
    private val instagram = "com.instagram.android"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    override fun onResume() {
        super.onResume()

        if (::root.isInitialized) {
            refresh()
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

    private fun todayUsage(pkg: String): Long {
        if (!hasUsageAccess()) return 0L

        val usm =
            getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val stats = usm.queryAndAggregateUsageStats(
            cal.timeInMillis,
            System.currentTimeMillis()
        )

        return (stats[pkg]?.totalTimeInForeground ?: 0L) / 60000L
    }

    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 30, 28, 20)
            setBackgroundColor(Color.rgb(246, 247, 251))
        }

        val scrollView = ScrollView(this)
        scrollView.addView(root)

        setContentView(scrollView)

        refresh()
    }

    private fun refresh() {
        root.removeAllViews()

        text(
            "SCROLLCHECK",
            13,
            Color.rgb(91, 92, 226),
            true
        )

        text(
            "Take control of your scroll.",
            30,
            Color.rgb(23, 32, 51),
            true
        )

        text(
            "Track → Understand → Improve → Reward",
            15,
            Color.DKGRAY,
            false
        )

        if (!hasUsageAccess()) {
            card(
                "🔐 Usage Access required",
                "ScrollCheck needs permission to read app usage time. It does not secretly monitor other apps.",
                "Grant Usage Access"
            ) {
                startActivity(
                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                )
            }
        }

        val yt = todayUsage(youtube)
        val ig = todayUsage(instagram)
        val total = yt + ig

        card(
            "▶️ YouTube",
            "$yt min today",
            "Open YouTube details"
        ) {
            showApp("YouTube", yt)
        }

        card(
            "📸 Instagram",
            "$ig min today",
            "Open Instagram details"
        ) {
            showApp("Instagram", ig)
        }

        text(
            "Today's Scroll",
            20,
            Color.rgb(23, 32, 51),
            true
        )

        text(
            "$total minutes tracked",
            27,
            Color.rgb(23, 32, 51),
            true
        )

        val score = calculateScore(total)

        card(
            "Scroll Balance",
            "$score / 100",
            scoreStatus(score)
        ) {}

        card(
            "Privacy-first",
            "Only usage statistics are read after you grant Android Usage Access. Data is kept locally in this prototype. Content categories are estimates.",
            "Refresh data"
        ) {
            refresh()
        }

        text(
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

        var score = 100

        if (total > 60) {
            score -= ((total - 60) * 0.25).toInt()
        }

        if (total > 120) {
            score -= 15
        }

        return score.coerceIn(0, 100)
    }

    private fun scoreStatus(score: Int): String {
        return when {
            score >= 80 -> "🟢 Excellent balance"
            score >= 60 -> "🟡 Good balance"
            score >= 40 -> "🟠 Needs attention"
            else -> "🔴 Time to reset"
        }
    }

    private fun showApp(
        name: String,
        minutes: Long
    ) {
        AlertDialog.Builder(this)
            .setTitle("$name · Today")
            .setMessage(
                "$minutes minutes of foreground usage detected.\n\n" +
                        "This prototype measures app usage time. " +
                        "It does not claim to know whether every individual " +
                        "video was educational or entertaining."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun text(
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

            setPadding(0, 10, 0, 10)
        }

        root.addView(textView)
    }

    private fun card(
        title: String,
        body: String,
        button: String,
        action: () -> Unit
    ) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 18, 22, 18)
            setBackgroundColor(Color.WHITE)
            elevation = 5f
        }

        val titleView = TextView(this).apply {
            text = title
            textSize = 19f
            setTextColor(Color.rgb(23, 32, 51))
            setTypeface(
                typeface,
                android.graphics.Typeface.BOLD
            )
        }

        val bodyView = TextView(this).apply {
            text = body
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(0, 8, 0, 10)
        }

        val buttonView = Button(this).apply {
            text = button
            setOnClickListener {
                action()
            }
        }

        box.addView(titleView)
        box.addView(bodyView)
        box.addView(buttonView)

        val layoutParams =
            LinearLayout.LayoutParams(-1, -2)

        layoutParams.setMargins(0, 12, 0, 12)

        root.addView(box, layoutParams)
    }
}
