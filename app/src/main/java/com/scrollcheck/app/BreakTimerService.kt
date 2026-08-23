package com.scrollcheck.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder

class BreakTimerService : Service() {

    companion object {
        const val CHANNEL_ID = "scrollcheck_break_timer"
        const val NOTIFICATION_ID = 2001

        const val ACTION_START = "START_BREAK"
        const val ACTION_STOP = "STOP_BREAK"

        const val EXTRA_MINUTES = "minutes"
    }

    private var timer: CountDownTimer? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {

            ACTION_START -> {

                val minutes =
                    intent.getIntExtra(
                        EXTRA_MINUTES,
                        5
                    )

                startBreak(minutes)
            }

            ACTION_STOP -> {
                stopBreak()
            }
        }

        return START_NOT_STICKY
    }

    private fun startBreak(minutes: Int) {

        timer?.cancel()

        val safeMinutes =
            minutes.coerceAtLeast(1)

        val duration =
            safeMinutes * 60_000L

        startForeground(
            NOTIFICATION_ID,
            createNotification(duration)
        )

        timer =
            object : CountDownTimer(
                duration,
                1000L
            ) {

                override fun onTick(
                    millisUntilFinished: Long
                ) {

                    val manager =
                        getSystemService(
                            NotificationManager::class.java
                        )

                    manager.notify(
                        NOTIFICATION_ID,
                        createNotification(
                            millisUntilFinished
                        )
                    )
                }

                override fun onFinish() {

                    val manager =
                        getSystemService(
                            NotificationManager::class.java
                        )

                    manager.notify(
                        NOTIFICATION_ID,
                        createFinishedNotification()
                    )

                    stopForeground(
                        STOP_FOREGROUND_REMOVE
                    )

                    stopSelf()
                }
            }.start()
    }

    private fun stopBreak() {

        timer?.cancel()
        timer = null

        stopForeground(
            STOP_FOREGROUND_REMOVE
        )

        stopSelf()
    }

    private fun createNotification(
        millis: Long
    ): Notification {

        val totalSeconds =
            millis / 1000L

        val minutes =
            totalSeconds / 60L

        val seconds =
            totalSeconds % 60L

        val time =
            String.format(
                "%02d:%02d",
                minutes,
                seconds
            )

        return Notification.Builder(
            this,
            CHANNEL_ID
        )
            .setContentTitle(
                "ScrollCheck Break"
            )
            .setContentText(
                "Break remaining: $time"
            )
            .setSmallIcon(
                android.R.drawable.ic_lock_idle_alarm
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createFinishedNotification(): Notification {

        return Notification.Builder(
            this,
            CHANNEL_ID
        )
            .setContentTitle(
                "Break complete 🎉"
            )
            .setContentText(
                "Your ScrollCheck break is finished."
            )
            .setSmallIcon(
                android.R.drawable.ic_lock_idle_alarm
            )
            .setAutoCancel(true)
            .build()
    }

    private fun createNotificationChannel() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "ScrollCheck Break Timer",
                    NotificationManager.IMPORTANCE_LOW
                )

            channel.description =
                "Background break timer"

            val manager =
                getSystemService(
                    NotificationManager::class.java
                )

            manager.createNotificationChannel(
                channel
            )
        }
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? {
        return null
    }

    override fun onDestroy() {

        timer?.cancel()
        timer = null

        super.onDestroy()
    }
}
