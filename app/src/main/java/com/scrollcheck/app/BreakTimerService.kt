package com.scrollcheck.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

class BreakTimerService : Service() {

    companion object {

        const val ACTION_START =
            "com.scrollcheck.app.START_BREAK"

        private const val CHANNEL_ID =
            "scrollcheck_break"

        private const val NOTIFICATION_ID =
            9001

        private const val FINISHED_NOTIFICATION_ID =
            9002

        private const val BREAK_TIME =
            5L * 60L * 1000L
    }

    private var endTime = 0L

    private val handler =
        Handler(Looper.getMainLooper())

    private val ticker =
        object : Runnable {

            override fun run() {

                val remaining =
                    endTime -
                        System.currentTimeMillis()

                if (remaining <= 0L) {

                    finishTimer()

                } else {

                    updateNotification(
                        remaining
                    )

                    handler.postDelayed(
                        this,
                        1000L
                    )
                }
            }
        }

    override fun onCreate() {
        super.onCreate()

        createChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (
            intent?.action ==
            ACTION_START
        ) {
            startTimer()
        }

        return START_NOT_STICKY
    }

    private fun startTimer() {

        endTime =
            System.currentTimeMillis() +
                BREAK_TIME

        val notification =
            buildNotification(
                BREAK_TIME
            )

        /*
         * Android 14+ requires the foreground
         * service type declared in the Manifest.
         */
        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        ) {

            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo
                    .FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )

        } else {

            startForeground(
                NOTIFICATION_ID,
                notification
            )
        }

        handler.removeCallbacks(ticker)

        handler.post(ticker)
    }

    private fun updateNotification(
        remaining: Long
    ) {

        val manager =
            getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

        manager.notify(
            NOTIFICATION_ID,
            buildNotification(remaining)
        )
    }

    private fun buildNotification(
        remaining: Long
    ): Notification {

        val totalSeconds =
            remaining / 1000L

        val minutes =
            totalSeconds / 60L

        val seconds =
            totalSeconds % 60L

        val time =
            String.format(
                java.util.Locale.getDefault(),
                "%02d:%02d",
                minutes,
                seconds
            )

        val intent =
            Intent(
                this,
                MainActivity::class.java
            )

        val pendingIntent =
            PendingIntent.getActivity(
                this,
                9003,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        return Notification.Builder(
            this,
            CHANNEL_ID
        )
            .setSmallIcon(
                android.R.drawable.ic_lock_idle_alarm
            )
            .setContentTitle(
                "ScrollCheck break"
            )
            .setContentText(
                "Break remaining: $time"
            )
            .setContentIntent(
                pendingIntent
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()
    }

    private fun finishTimer() {

        handler.removeCallbacks(ticker)

        val manager =
            getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

        manager.cancel(
            NOTIFICATION_ID
        )

        val finished =
            Notification.Builder(
                this,
                CHANNEL_ID
            )
                .setSmallIcon(
                    android.R.drawable.ic_dialog_info
                )
                .setContentTitle(
                    "Break complete"
                )
                .setContentText(
                    "Your 5-minute reset is finished."
                )
                .setAutoCancel(true)
                .build()

        manager.notify(
            FINISHED_NOTIFICATION_ID,
            finished
        )

        stopForeground(
            STOP_FOREGROUND_REMOVE
        )

        stopSelf()
    }

    private fun createChannel() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            val manager =
                getSystemService(
                    Context.NOTIFICATION_SERVICE
                ) as NotificationManager

            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "ScrollCheck Break Timer",
                    NotificationManager.IMPORTANCE_LOW
                )

            channel.description =
                "Background break timer."

            manager.createNotificationChannel(
                channel
            )
        }
    }

    override fun onDestroy() {

        handler.removeCallbacks(ticker)

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? {
        return null
    }
}
