package com.example.immunepercentageapp

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.text.NumberFormat

class MainActivity : AppCompatActivity() {

    private lateinit var ring: RingView
    private lateinit var textHeart: TextView
    private lateinit var textSteps: TextView
    private lateinit var textStatus: TextView
    private lateinit var dotLive: View
    private lateinit var textTrend: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val listener: () -> Unit = { refresh() }
    private val ticker = object : Runnable {
        override fun run() {
            refresh() // also lets stale heart-rate readings expire
            handler.postDelayed(this, 5_000)
        }
    }

    private val basicPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { afterBasicPermissions() }
    private val backgroundPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { startMonitoring(); refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        EnergyModel.init(applicationContext)

        ring = findViewById(R.id.ring)
        textHeart = findViewById(R.id.textHeart)
        textSteps = findViewById(R.id.textSteps)
        textStatus = findViewById(R.id.textStatus)
        dotLive = findViewById(R.id.dotLive)
        textTrend = findViewById(R.id.textTrend)

        findViewById<View>(R.id.statusRow).setOnClickListener {
            if (!granted(Manifest.permission.BODY_SENSORS)) requestAccess()
        }

        ObjectAnimator.ofFloat(dotLive, View.ALPHA, 1f, 0.2f).apply {
            duration = 900
            repeatMode = ObjectAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }

        if (savedInstanceState == null) requestAccess()
    }

    override fun onStart() {
        super.onStart()
        VitalsStore.addListener(listener)
        startMonitoring()
        handler.post(ticker)
    }

    override fun onStop() {
        handler.removeCallbacks(ticker)
        VitalsStore.removeListener(listener)
        super.onStop()
    }

    private fun granted(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun requestAccess() {
        val needed = mutableListOf(Manifest.permission.BODY_SENSORS, Manifest.permission.ACTIVITY_RECOGNITION)
        if (Build.VERSION.SDK_INT >= 33) needed.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = needed.filter { !granted(it) }
        if (missing.isNotEmpty()) basicPermissions.launch(missing.toTypedArray()) else afterBasicPermissions()
    }

    private fun afterBasicPermissions() {
        if (Build.VERSION.SDK_INT >= 33 &&
            granted(Manifest.permission.BODY_SENSORS) &&
            !granted(Manifest.permission.BODY_SENSORS_BACKGROUND)
        ) {
            backgroundPermission.launch(Manifest.permission.BODY_SENSORS_BACKGROUND)
        } else {
            startMonitoring()
            refresh()
        }
    }

    private fun startMonitoring() {
        if (granted(Manifest.permission.BODY_SENSORS)) {
            ContextCompat.startForegroundService(this, Intent(this, HealthMonitorService::class.java))
        }
    }

    private fun refresh() {
        val hr = VitalsStore.currentHeartRate()
        val steps = VitalsStore.steps
        textHeart.text = hr?.toString() ?: "--"
        textSteps.text = steps?.let { NumberFormat.getIntegerInstance().format(it) } ?: "--"
        ring.setScore(EnergyModel.energy())
        textTrend.text = when {
            hr == null -> "WEAR WATCH TO UPDATE"
            EnergyModel.trend > 0 -> "RECHARGING"
            EnergyModel.trend < 0 -> "DRAINING"
            else -> "STEADY"
        }
        textStatus.text = when {
            !granted(Manifest.permission.BODY_SENSORS) -> "TAP TO ALLOW"
            !VitalsStore.hasHeartSensor -> "NO HR SENSOR"
            hr == null -> "SCANNING"
            else -> "LIVE"
        }
    }
}
