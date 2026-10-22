package com.blindnav.trip

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.blindnav.MainActivity
import com.blindnav.R

object TripNotifications {
    private const val CHANNEL_ID = "trip"
    const val TRACKING_ID = 1
    private const val ARRIVED_ID = 2

    fun createChannel(context: Context) {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName("Trip tracking")
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    fun tracking(context: Context): Notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_blindnav)
        .setContentTitle("Trip in progress")
        .setContentText("Drive from memory. Arrival is detected automatically.")
        .setOngoing(true)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .setContentIntent(openApp(context, tripId = null))
        .build()

    fun showArrived(context: Context, tripId: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_blindnav)
            .setContentTitle("You've arrived")
            .setContentText("Tap to see your score")
            .setAutoCancel(true)
            .setContentIntent(openApp(context, tripId))
            .build()
        NotificationManagerCompat.from(context).notify(ARRIVED_ID, notification)
    }

    fun cancelArrived(context: Context) = NotificationManagerCompat.from(context).cancel(ARRIVED_ID)

    private fun openApp(context: Context, tripId: Long?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .apply { if (tripId != null) putExtra(MainActivity.EXTRA_TRIP_ID, tripId) }
        return PendingIntent.getActivity(
            context,
            if (tripId == null) 0 else 1,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
