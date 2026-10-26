package com.stealthguardv2

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.*

class StealthService : Service() {

    companion object {
        private const val TAG = "StealthGuardV2"
        private const val CH_ID = "sgv2_core"
        private const val NOTIF_ID = 1001

        @Volatile var isRunning = false
        @Volatile var lastText = "-"
    }

    private lateinit var fused: FusedLocationProviderClient
    private lateinit var callback: LocationCallback

    override fun onCreate() {
        super.onCreate()
        fused = LocationServices.getFusedLocationProviderClient(this)
        callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val l: Location = result.lastLocation ?: return
                lastText = "${l.latitude}, ${l.longitude}  (±${l.accuracy.toInt()}m)"
                Log.i(TAG, "LOC $lastText")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        startLocation()
        isRunning = true
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        try { fused.removeLocationUpdates(callback) } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CH_ID) == null) {
            val ch = NotificationChannel(
                CH_ID, "StealthGuardV2", NotificationManager.IMPORTANCE_MIN
            )
            ch.setShowBadge(false)
            nm.createNotificationChannel(ch)
        }

        val tap = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notif = Notification.Builder(this, CH_ID)
            .setContentTitle("StealthGuardV2")
            .setContentText("Active")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(tap)
            .setOngoing(true)
            .setSilent(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } catch (e: Exception) {
                startForeground(NOTIF_ID, notif)
            }
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun startLocation() {
        val req = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 30_000L)
            .setMinUpdateIntervalMillis(15_000L)
            .setWaitForAccurateLocation(false)
            .build()
        try {
            fused.requestLocationUpdates(req, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.e(TAG, "location permission missing", e)
        }
    }
}
