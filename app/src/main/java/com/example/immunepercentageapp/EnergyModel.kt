package com.example.immunepercentageapp

import android.content.Context
import android.content.SharedPreferences
import java.util.Calendar

/**
 * Estimated energy level (0-100), similar in spirit to Garmin "Body Battery".
 *
 * Energy drains while heart rate is well above your resting baseline and recharges
 * while it is near baseline (faster at night). The baseline is learned from your
 * own lowest readings, so the estimate improves over the first few days.
 * This is an ESTIMATE, not a medical measurement.
 */
object EnergyModel {
    private const val SAVE_EVERY_MS = 60_000L
    private const val MAX_GAP_MIN = 2.0   // ignore long gaps (e.g. watch removed)

    private var prefs: SharedPreferences? = null
    @Volatile private var energy = 80f
    private var restingHr = 65f
    private var lastTime = 0L
    private var lastSave = 0L
    @Volatile var trend = 0   // +1 recharging, -1 draining, 0 steady
        private set

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("energy", Context.MODE_PRIVATE)
        prefs = p
        energy = p.getFloat("energy", 80f)
        restingHr = p.getFloat("resting", 65f)
        lastTime = 0L
    }

    fun energy(): Int = energy.toInt().coerceIn(0, 100)

    fun onHeartRate(hr: Int, now: Long = System.currentTimeMillis()) {
        // Learn the resting baseline: follow low readings down quickly, drift up slowly.
        restingHr = if (hr < restingHr) restingHr * 0.98f + hr * 0.02f
        else restingHr + (hr - restingHr) * 0.0005f
        restingHr = restingHr.coerceIn(40f, 90f)

        if (lastTime != 0L) {
            val minutes = ((now - lastTime) / 60_000.0).coerceIn(0.0, MAX_GAP_MIN).toFloat()
            val delta = hr - restingHr
            val night = Calendar.getInstance().get(Calendar.HOUR_OF_DAY).let { it >= 22 || it < 7 }

            val ratePerMin = when {
                delta <= 3f && night -> 0.5f
                delta <= 5f -> 0.25f
                else -> -((delta - 5f) * 0.02f).coerceAtMost(1.5f)
            }
            trend = when {
                ratePerMin > 0f -> 1
                ratePerMin < -0.05f -> -1
                else -> 0
            }
            energy = (energy + ratePerMin * minutes).coerceIn(0f, 100f)
        }
        lastTime = now

        if (now - lastSave > SAVE_EVERY_MS) {
            lastSave = now
            prefs?.edit()?.putFloat("energy", energy)?.putFloat("resting", restingHr)?.apply()
        }
    }
}
