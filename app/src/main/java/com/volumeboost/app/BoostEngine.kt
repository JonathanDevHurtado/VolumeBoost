package com.volumeboost.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.audiofx.AudioEffect
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.util.Log

/**
 * Motor del refuerzo de volumen. Vive en el proceso de la app (singleton) y SOLO se toca
 * desde el hilo principal (actividad, servicio y receptores corren todos ahí).
 *
 * Cómo sube el volumen por encima del 100 %:
 *  - Modo GLOBAL (por defecto): un [LoudnessEnhancer] enganchado a la sesión de audio 0
 *    (la mezcla de salida), así que afecta a todo lo que suena en el teléfono.
 *  - Modo PER_APP: algunos teléfonos/versiones de Android ignoran los efectos sobre la sesión 0.
 *    En ese caso (o si el usuario lo elige) se engancha un LoudnessEnhancer a cada sesión de audio
 *    que las apps de música anuncian con ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION, más la
 *    sesión del sonido de prueba integrado.
 *
 * Nunca se usan los dos a la vez sobre el mismo audio para no multiplicar la ganancia.
 */
object BoostEngine {

    private const val TAG = "BoostEngine"

    enum class Mode { GLOBAL, PER_APP }

    /** IDLE = aún no se intentó; ACTIVE = efecto global creado; UNAVAILABLE = el sistema lo rechazó. */
    enum class GlobalState { IDLE, ACTIVE, UNAVAILABLE }

    interface Listener {
        fun onBoostChanged()
    }

    var percent: Int = BoostMath.MIN_PERCENT
        private set

    var mode: Mode = Mode.GLOBAL
        private set

    var globalState: GlobalState = GlobalState.IDLE
        private set

    /** Cuántas sesiones de audio individuales tienen efecto (solo relevante en modo por app). */
    val boundSessions: Int
        get() = enhancers.size

    /** Lo marca el servicio en primer plano (hilo principal). */
    var serviceAlive: Boolean = false

    /** Lo marca la actividad: true entre onStart y onStop. */
    var uiVisible: Boolean = false

    private var appContext: Context? = null
    private var globalEffect: LoudnessEnhancer? = null
    private var receiverRegistered = false

    /** Sesiones que queremos reforzar en modo por app (anunciadas por apps o del sonido de prueba). */
    private val tracked = LinkedHashSet<Int>()
    private val enhancers = HashMap<Int, LoudnessEnhancer>()
    private val listeners = ArrayList<Listener>()

    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    fun addListener(l: Listener) {
        if (!listeners.contains(l)) listeners.add(l)
    }

    fun removeListener(l: Listener) {
        listeners.remove(l)
    }

    /** Pide a los oyentes que se refresquen (p. ej. para reiniciar el servicio si murió). */
    fun requestRefresh() = notifyListeners()

    // ------------------------------------------------------------------ API pública

    fun setPercent(context: Context, newPercent: Int) {
        init(context)
        val p = BoostMath.clamp(newPercent)
        if (p == percent) return
        val wasIdle = percent == BoostMath.MIN_PERCENT
        percent = p
        if (p == BoostMath.MIN_PERCENT) {
            releaseEffects()
        } else {
            if (wasIdle) ensureEffects()
            applyGain()
        }
        notifyListeners()
    }

    fun setMode(context: Context, newMode: Mode) {
        init(context)
        if (newMode == mode) return
        mode = newMode
        if (percent > BoostMath.MIN_PERCENT) {
            releaseEffects()
            ensureEffects()
            applyGain()
        }
        notifyListeners()
    }

    /** Engancha una sesión de audio propia (sonido de prueba). Idempotente. */
    fun trackSession(sessionId: Int) {
        if (sessionId <= 0) return
        tracked.add(sessionId)
        if (percent > BoostMath.MIN_PERCENT && usePerApp()) bind(sessionId)
    }

    fun untrackSession(sessionId: Int) {
        tracked.remove(sessionId)
        enhancers.remove(sessionId)?.let { safeRelease(it) }
    }

    // ------------------------------------------------------------------ internos

    private fun usePerApp(): Boolean =
        mode == Mode.PER_APP || globalState == GlobalState.UNAVAILABLE

    private fun ensureEffects() {
        val ctx = appContext ?: return
        if (mode == Mode.GLOBAL && globalEffect == null && globalState == GlobalState.IDLE) {
            val e = createEnhancer(0)
            if (e != null) {
                globalEffect = e
                globalState = GlobalState.ACTIVE
            } else {
                globalState = GlobalState.UNAVAILABLE
            }
        }
        registerReceiver(ctx)
        bindAllTracked()
    }

    private fun bindAllTracked() {
        if (!usePerApp()) return
        for (id in tracked.toList()) bind(id)
    }

    private fun bind(sessionId: Int) {
        if (enhancers.containsKey(sessionId)) return
        val e = createEnhancer(sessionId)
        if (e == null) {
            tracked.remove(sessionId)
            return
        }
        if (configure(e, BoostMath.gainMillibels(percent))) {
            enhancers[sessionId] = e
        } else {
            safeRelease(e)
            tracked.remove(sessionId)
        }
    }

    private fun applyGain() {
        val mb = BoostMath.gainMillibels(percent)

        globalEffect?.let { g ->
            if (!configure(g, mb)) {
                // El sistema lo soltó (audioserver reiniciado, etc.): pasamos a modo por app.
                safeRelease(g)
                globalEffect = null
                globalState = GlobalState.UNAVAILABLE
                bindAllTracked()
            }
        }

        val iter = enhancers.entries.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            if (!configure(entry.value, mb)) {
                safeRelease(entry.value)
                tracked.remove(entry.key)
                iter.remove()
            }
        }
    }

    private fun releaseEffects() {
        globalEffect?.let { safeRelease(it) }
        globalEffect = null
        for (e in enhancers.values) safeRelease(e)
        enhancers.clear()
        globalState = GlobalState.IDLE
        unregisterReceiver()
    }

    private fun createEnhancer(sessionId: Int): LoudnessEnhancer? = try {
        LoudnessEnhancer(sessionId)
    } catch (e: Exception) {
        // IllegalArgumentException, UnsupportedOperationException, RuntimeException según fabricante.
        Log.w(TAG, "No se pudo crear LoudnessEnhancer(session=$sessionId): ${e.message}")
        null
    }

    private fun configure(e: LoudnessEnhancer, gainMb: Int): Boolean = try {
        e.setTargetGain(gainMb)
        if (!e.getEnabled()) {
            e.setEnabled(true) == AudioEffect.SUCCESS
        } else {
            true
        }
    } catch (ex: Exception) {
        Log.w(TAG, "No se pudo configurar el efecto: ${ex.message}")
        false
    }

    private fun safeRelease(e: LoudnessEnhancer) {
        try {
            e.release()
        } catch (ex: Exception) {
            Log.w(TAG, "release(): ${ex.message}")
        }
    }

    private fun notifyListeners() {
        for (l in listeners.toTypedArray()) l.onBoostChanged()
    }

    // ------------------------------------------------------------------ sesiones anunciadas por apps

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val session = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioEffect.ERROR_BAD_VALUE)
            if (session <= 0) return
            when (intent.action) {
                AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> {
                    trackSession(session)
                    notifyListeners()
                }
                AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> {
                    untrackSession(session)
                    notifyListeners()
                }
            }
        }
    }

    private fun registerReceiver(ctx: Context) {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        }
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                // Los avisos vienen de OTRAS apps, así que el receptor debe ser exportado.
                ctx.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                ctx.registerReceiver(receiver, filter)
            }
            receiverRegistered = true
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo registrar el receptor de sesiones: ${e.message}")
        }
    }

    private fun unregisterReceiver() {
        if (!receiverRegistered) return
        try {
            appContext?.unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            // ya estaba desregistrado
        }
        receiverRegistered = false
    }
}
