package com.example.immunepercentageapp

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import androidx.core.content.ContextCompat
import java.time.LocalDate

/** Foreground service that keeps reading body sensors while the watch is worn. */
class HealthMonitorService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var smoothedHr = 0.0

    override fun onCreate() {
        super.onCreate()
        EnergyModel.init(applicationContext)
        startForeground(NOTIFICATION_ID, buildNotification())
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        val heart = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        val step = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        VitalsStore.hasHeartSensor = heart != null
        VitalsStore.hasStepSensor = step != null

        if (heart != null && has(Manifest.permission.BODY_SENSORS)) {
            sensorManager.registerListener(this, heart, SensorManager.SENSOR_DELAY_NORMAL)
        }
        if (step != null && has(Manifest.permission.ACTIVITY_RECOGNITION)) {
            sensorManager.registerListener(this, step, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_HEART_RATE -> {
                val bpm = event.values[0]
                if (bpm <= 0f) return // sensor has no valid reading yet
                smoothedHr = if (smoothedHr == 0.0) bpm.toDouble() else smoothedHr * 0.7 + bpm * 0.3
                EnergyModel.onHeartRate(smoothedHr.toInt())
                VitalsStore.updateHeartRate(smoothedHr.toInt())
            }
            Sensor.TYPE_STEP_COUNTER -> VitalsStore.updateSteps(stepsToday(event.values[0].toLong()))
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /** The step counter counts since boot, so keep a per-day baseline. */
    private fun stepsToday(total: Long): Int {
        val prefs = getSharedPreferences("vitals", Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        var base = prefs.getLong("base_steps", -1L)
        if (prefs.getString("base_date", "") != today || base < 0 || total < base) {
            base = if (prefs.getString("base_date", "") == today) 0L else total
            prefs.edit().putString("base_date", today).putLong("base_steps", base).apply()
        }
        return (total - base).toInt().coerceAtLeast(0)
    }

    private fun has(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Health monitoring", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_heart)
            .setContentTitle("Encompass")
            .setContentText("Monitoring your vitals")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "health_monitor"
        private const val NOTIFICATION_ID = 1
    }
}
