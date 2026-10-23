package com.volumeboost.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import kotlin.math.min
import kotlin.math.roundToInt

class MainActivity : Activity(), BoostEngine.Listener {

    private lateinit var gauge: GaugeView
    private lateinit var btnMinus: View
    private lateinit var btnPlus: View
    private lateinit var chips: List<TextView>
    private lateinit var holdDown: View
    private lateinit var holdUp: View
    private lateinit var swSound: Switch
    private lateinit var swMode: Switch
    private lateinit var tvVolume: TextView
    private lateinit var tvVolumeHint: TextView
    private lateinit var btnVolume: TextView
    private lateinit var btnTest: View
    private lateinit var btnTestLabel: TextView
    private lateinit var btnTestIcon: ImageView
    private lateinit var tvStatus: TextView

    private lateinit var prefs: SharedPreferences
    private val sfx = Sfx()
    private val testTone = TestTone()
    private val handler = Handler(Looper.getMainLooper())

    // Estado de "mantener presionado"
    private var holdDir = 0               // -1 bajando, 0 nada, +1 subiendo
    private var holdValue = 0f
    private var holdLastTime = 0L

    private var lastServiceStart = 0L
    private var serviceFailureShown = false
    private var volumeReceiver: BroadcastReceiver? = null
    private var warnDialog: AlertDialog? = null

    // ------------------------------------------------------------------ ciclo de vida

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        BoostEngine.init(this)
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE)

        gauge = findViewById(R.id.gauge)
        btnMinus = findViewById(R.id.btnMinus)
        btnPlus = findViewById(R.id.btnPlus)
        holdDown = findViewById(R.id.holdDown)
        holdUp = findViewById(R.id.holdUp)
        swSound = findViewById(R.id.swSound)
        swMode = findViewById(R.id.swMode)
        tvVolume = findViewById(R.id.tvVolume)
        tvVolumeHint = findViewById(R.id.tvVolumeHint)
        btnVolume = findViewById(R.id.btnVolume)
        btnTest = findViewById(R.id.btnTest)
        btnTestLabel = findViewById(R.id.btnTestLabel)
        btnTestIcon = findViewById(R.id.btnTestIcon)
        tvStatus = findViewById(R.id.tvStatus)

        chips = listOf(R.id.chip100, R.id.chip110, R.id.chip125, R.id.chip150).map { id ->
            findViewById<TextView>(id).also { chip ->
                val value = chip.text.toString().removeSuffix("%").toInt()
                chip.tag = value
                chip.setOnClickListener { stepTo(value) }
            }
        }

        btnMinus.setOnClickListener { stepTo(BoostMath.stepDown(BoostEngine.percent)) }
        btnPlus.setOnClickListener { stepTo(BoostMath.stepUp(BoostEngine.percent)) }
        bindHold(holdDown, -1)
        bindHold(holdUp, +1)

        // Ajustes guardados (se asignan ANTES de poner los oyentes para no disparar nada)
        sfx.enabled = prefs.getBoolean(KEY_SOUND, true)
        swSound.isChecked = sfx.enabled
        swSound.setOnCheckedChangeListener { _, on ->
            sfx.enabled = on
            prefs.edit().putBoolean(KEY_SOUND, on).apply()
        }

        val perApp = prefs.getBoolean(KEY_PER_APP, false)
        BoostEngine.setMode(this, if (perApp) BoostEngine.Mode.PER_APP else BoostEngine.Mode.GLOBAL)
        swMode.isChecked = perApp
        swMode.setOnCheckedChangeListener { _, on ->
            prefs.edit().putBoolean(KEY_PER_APP, on).apply()
            BoostEngine.setMode(
                this, if (on) BoostEngine.Mode.PER_APP else BoostEngine.Mode.GLOBAL
            )
        }

        btnVolume.setOnClickListener { setSystemVolumeToMax() }
        btnTest.setOnClickListener { toggleTest() }

        showFirstRunNotices()
    }

    override fun onStart() {
        super.onStart()
        BoostEngine.uiVisible = true
        BoostEngine.addListener(this)
        registerVolumeReceiver()
        render(animate = false)
        updateVolumeRow()
        updateTestButton()
        ensureService() // por si el servicio murió mientras la app estaba cerrada
    }

    override fun onPause() {
        cancelHold()
        super.onPause()
    }

    override fun onStop() {
        testTone.stop()
        updateTestButton()
        unregisterVolumeReceiver()
        BoostEngine.removeListener(this)
        BoostEngine.uiVisible = false
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        warnDialog?.dismiss()
        warnDialog = null
        testTone.stop()
        sfx.release()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ motor -> pantalla

    override fun onBoostChanged() {
        render(animate = holdDir == 0)
        ensureService()
    }

    private fun render(animate: Boolean) {
        val p = BoostEngine.percent
        if (holdDir == 0) gauge.setPercent(p.toFloat(), animate)
        gauge.contentDescription = getString(R.string.gauge_desc, p)
        gauge.subtitle = if (p <= BoostMath.MIN_PERCENT) {
            getString(R.string.sub_normal)
        } else {
            getString(R.string.sub_boost, BoostMath.gainMillibels(p) / 100.0)
        }
        for (chip in chips) chip.isSelected = (chip.tag as Int) == p
        setEnabledDim(btnMinus, p > BoostMath.MIN_PERCENT)
        setEnabledDim(btnPlus, p < BoostMath.MAX_PERCENT)
        tvStatus.text = statusText()
        testTone.syncSession()
    }

    private fun setEnabledDim(v: View, enabled: Boolean) {
        v.isEnabled = enabled
        v.alpha = if (enabled) 1f else 0.35f
    }

    private fun statusText(): String {
        val p = BoostEngine.percent
        if (p <= BoostMath.MIN_PERCENT) return getString(R.string.status_idle)
        return when {
            BoostEngine.mode == BoostEngine.Mode.GLOBAL &&
                BoostEngine.globalState == BoostEngine.GlobalState.ACTIVE ->
                getString(R.string.status_global, BoostMath.gainMillibels(p) / 100.0)
            BoostEngine.mode == BoostEngine.Mode.GLOBAL &&
                BoostEngine.globalState == BoostEngine.GlobalState.UNAVAILABLE ->
                getString(R.string.status_global_failed, BoostEngine.boundSessions)
            else -> getString(R.string.status_per_app, BoostEngine.boundSessions)
        }
    }

    // ------------------------------------------------------------------ cambios de nivel

    private fun setBoost(percent: Int) {
        BoostEngine.setPercent(this, percent)
    }

    /** Botones -, + y atajos: aplica, y avisa con sonido hasta dónde quedó. */
    private fun stepTo(percent: Int) {
        val target = BoostMath.clamp(percent)
        setBoost(target)
        announce(target)
    }

    /** El sonidito que te dice hasta dónde llegó el volumen. */
    private fun announce(percent: Int) {
        when {
            percent >= BoostMath.MAX_PERCENT -> {
                sfx.max()
                gauge.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
            percent <= BoostMath.MIN_PERCENT -> sfx.min()
            else -> sfx.level(percent)
        }
    }

    // ------------------------------------------------------------------ mantener presionado

    @SuppressLint("ClickableViewAccessibility")
    private fun bindHold(view: View, dir: Int) {
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    startHold(dir)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    stopHold(dir)
                    if (event.actionMasked == MotionEvent.ACTION_UP) v.performClick()
                }
            }
            true
        }
    }

    private fun startHold(dir: Int) {
        if (holdDir != 0) return // ya hay un botón de mantener activo: ignora el segundo dedo
        val p = BoostEngine.percent
        if ((dir > 0 && p >= BoostMath.MAX_PERCENT) || (dir < 0 && p <= BoostMath.MIN_PERCENT)) {
            announce(p) // ya está en el tope: solo avisa
            return
        }
        holdDir = dir
        holdValue = p.toFloat()
        holdLastTime = SystemClock.uptimeMillis()
        handler.post(holdRunnable)
    }

    private val holdRunnable = object : Runnable {
        override fun run() {
            if (holdDir == 0) return
            val now = SystemClock.uptimeMillis()
            val dt = min(now - holdLastTime, 100L) / 1000f // tope por si el hilo se atrasa
            holdLastTime = now

            holdValue = (holdValue + holdDir * HOLD_RATE * dt)
                .coerceIn(BoostMath.MIN_PERCENT.toFloat(), BoostMath.MAX_PERCENT.toFloat())
            gauge.setPercent(holdValue, false) // la aguja se mueve fluida (decimales)

            val ip = holdValue.roundToInt()
            if (ip != BoostEngine.percent) {
                setBoost(ip)
                if (ip % BoostMath.STEP == 0 && ip != BoostMath.MAX_PERCENT && ip != BoostMath.MIN_PERCENT) {
                    sfx.tick(ip)
                }
            }

            val atEnd = (holdDir > 0 && holdValue >= BoostMath.MAX_PERCENT) ||
                (holdDir < 0 && holdValue <= BoostMath.MIN_PERCENT)
            if (atEnd) {
                holdDir = 0
                gauge.setPercent(BoostEngine.percent.toFloat(), false)
                announce(BoostEngine.percent)
                return
            }
            handler.postDelayed(this, FRAME_MS)
        }
    }

    /** Soltaste el botón antes del final: te quedas donde estás y suena el aviso de ese nivel. */
    private fun stopHold(dir: Int) {
        if (holdDir != dir) return
        holdDir = 0
        handler.removeCallbacks(holdRunnable)
        val p = BoostEngine.percent
        gauge.setPercent(p.toFloat(), true)
        announce(p)
    }

    /** Cancelación silenciosa (la app pasa a segundo plano mientras mantenías presionado). */
    private fun cancelHold() {
        if (holdDir == 0) return
        holdDir = 0
        handler.removeCallbacks(holdRunnable)
        holdUp.isPressed = false
        holdDown.isPressed = false
        gauge.setPercent(BoostEngine.percent.toFloat(), false)
    }

    // ------------------------------------------------------------------ servicio en primer plano

    private fun ensureService() {
        if (BoostEngine.percent <= BoostMath.MIN_PERCENT || BoostEngine.serviceAlive) return
        val now = SystemClock.uptimeMillis()
        if (now - lastServiceStart < 800L) return
        lastServiceStart = now
        try {
            BoostService.start(this)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo iniciar el servicio: ${e.message}")
            if (!serviceFailureShown) {
                serviceFailureShown = true
                Toast.makeText(this, R.string.service_fail, Toast.LENGTH_LONG).show()
            }
        }
    }

    // ------------------------------------------------------------------ volumen del sistema

    private fun audioManager(): AudioManager = getSystemService(AudioManager::class.java)

    private fun updateVolumeRow() {
        val am = audioManager()
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val ok = cur >= max
        tvVolume.text = getString(R.string.vol_label, cur * 100 / max)
        tvVolumeHint.text = getString(if (ok) R.string.vol_ok else R.string.vol_low)
        tvVolumeHint.setTextColor(getColor(if (ok) R.color.text_ok else R.color.text_warn))
        btnVolume.visibility = if (ok) View.GONE else View.VISIBLE
    }

    private fun setSystemVolumeToMax() {
        try {
            val am = audioManager()
            am.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                am.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
                AudioManager.FLAG_SHOW_UI
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "No se pudo subir el volumen: ${e.message}")
        }
        updateVolumeRow()
    }

    private fun registerVolumeReceiver() {
        if (volumeReceiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                updateVolumeRow()
            }
        }
        val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(r, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(r, filter)
            }
            volumeReceiver = r
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo escuchar el volumen: ${e.message}")
        }
    }

    private fun unregisterVolumeReceiver() {
        val r = volumeReceiver ?: return
        volumeReceiver = null
        try {
            unregisterReceiver(r)
        } catch (e: IllegalArgumentException) {
            // ya estaba desregistrado
        }
    }

    // ------------------------------------------------------------------ sonido de prueba

    private fun toggleTest() {
        if (testTone.isPlaying) {
            testTone.stop()
        } else if (!testTone.start()) {
            Toast.makeText(this, R.string.test_fail, Toast.LENGTH_SHORT).show()
        }
        updateTestButton()
    }

    private fun updateTestButton() {
        val playing = testTone.isPlaying
        val label = if (playing) R.string.test_stop else R.string.test_start
        btnTestLabel.setText(label)
        btnTestIcon.setImageResource(
            if (playing) R.drawable.ic_stop else R.drawable.ic_play_arrow
        )
        btnTest.contentDescription = getString(label)
    }

    // ------------------------------------------------------------------ avisos iniciales

    private fun showFirstRunNotices() {
        if (prefs.getBoolean(KEY_WARNED, false)) {
            askNotificationPermission()
            return
        }
        warnDialog = AlertDialog.Builder(this)
            .setTitle(R.string.warn_title)
            .setMessage(R.string.warn_msg)
            .setCancelable(false)
            .setPositiveButton(R.string.warn_ok) { _, _ ->
                prefs.edit().putBoolean(KEY_WARNED, true).apply()
                askNotificationPermission()
            }
            .show()
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        if (prefs.getBoolean(KEY_ASKED_NOTIF, false)) return
        prefs.edit().putBoolean(KEY_ASKED_NOTIF, true).apply()
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATIONS)
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val PREFS = "volume_boost"
        private const val KEY_SOUND = "sound"
        private const val KEY_PER_APP = "per_app"
        private const val KEY_WARNED = "warned"
        private const val KEY_ASKED_NOTIF = "asked_notif"
        private const val REQ_NOTIFICATIONS = 10

        /** Puntos de porcentaje por segundo al mantener presionado: 100 -> 150 tarda ~5 s. */
        private const val HOLD_RATE = 10f
        private const val FRAME_MS = 16L
    }
}
