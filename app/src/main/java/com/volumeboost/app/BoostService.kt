package com.volumeboost.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log

/**
 * Servicio en primer plano: no hace audio, solo mantiene vivo el proceso (y por tanto los efectos
 * de [BoostEngine]) mientras el refuerzo está por encima de 100 %, aunque cierres la pantalla
 * o salgas de la app. Todo el trabajo de audio lo hace [BoostEngine].
 *
 * Reglas para evitar carreras:
 *  - Siempre llama a startForeground() al inicio de onStartCommand (Android exige hacerlo en 5 s).
 *  - Nunca se detiene en el acto al volver a 100 %: espera unos segundos por si el usuario vuelve a subir.
 */
class BoostService : Service(), BoostEngine.Listener {

    private val handler = Handler(Looper.getMainLooper())
    private var foregroundActive = false
    private var notifyPending = false
    private var stopScheduled = false

    private val notifyRunnable = Runnable {
        notifyPending = false
        updateNotification()
    }

    private val idleStopRunnable = Runnable {
        stopScheduled = false
        if (BoostEngine.percent <= BoostMath.MIN_PERCENT) stopNow()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        BoostEngine.init(this)
        BoostEngine.serviceAlive = true
        BoostEngine.addListener(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // Botón "Volver a 100 %" de la notificación.
            BoostEngine.setPercent(this, BoostMath.MIN_PERCENT)
            stopNow()
            return START_NOT_STICKY
        }

        enterForeground()

        if (BoostEngine.percent <= BoostMath.MIN_PERCENT) {
            scheduleIdleStop()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        BoostEngine.removeListener(this)
        BoostEngine.serviceAlive = false
        if (BoostEngine.percent > BoostMath.MIN_PERCENT) {
            if (BoostEngine.uiVisible) {
                // El servicio murió con la app abierta: que la pantalla lo vuelva a levantar.
                BoostEngine.requestRefresh()
            } else {
                // Sin servicio y sin pantalla nadie protege el proceso: apagamos el refuerzo.
                BoostEngine.setPercent(this, BoostMath.MIN_PERCENT)
            }
        }
        super.onDestroy()
    }

    // ------------------------------------------------------------------ BoostEngine.Listener

    override fun onBoostChanged() {
        if (BoostEngine.percent <= BoostMath.MIN_PERCENT) {
            handler.removeCallbacks(notifyRunnable)
            notifyPending = false
            updateNotification()
            scheduleIdleStop()
        } else {
            if (stopScheduled) {
                handler.removeCallbacks(idleStopRunnable)
                stopScheduled = false
            }
            // Agrupa las actualizaciones: al mantener presionado cambia 10 veces por segundo
            // y Android descarta notificaciones demasiado seguidas.
            if (!notifyPending) {
                notifyPending = true
                handler.postDelayed(notifyRunnable, 300L)
            }
        }
    }

    // ------------------------------------------------------------------ utilidades

    private fun scheduleIdleStop() {
        if (stopScheduled) return
        stopScheduled = true
        handler.postDelayed(idleStopRunnable, IDLE_STOP_DELAY_MS)
    }

    private fun stopNow() {
        handler.removeCallbacksAndMessages(null)
        notifyPending = false
        stopScheduled = false
        if (foregroundActive) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foregroundActive = false
        }
        stopSelf()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun enterForeground() {
        try {
            val n = buildNotification()
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIF_ID, n)
            }
            foregroundActive = true
        } catch (e: Exception) {
            // Sin notificación visible el servicio sigue siendo útil mientras la app esté abierta.
            Log.w(TAG, "startForeground falló: ${e.message}")
            foregroundActive = false
        }
    }

    private fun updateNotification() {
        if (!foregroundActive) return
        try {
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
        } catch (e: Exception) {
            Log.w(TAG, "notify falló: ${e.message}")
        }
    }

    private fun buildNotification(): Notification {
        val percent = BoostEngine.percent
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, BoostService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopAction = Notification.Action.Builder(
            Icon.createWithResource(this, R.drawable.ic_stat_volume),
            getString(R.string.notif_action), stop
        ).build()

        val text = if (percent <= BoostMath.MIN_PERCENT) {
            getString(R.string.notif_idle)
        } else {
            getString(R.string.notif_text, percent, BoostMath.gainMillibels(percent) / 100.0)
        }

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_volume)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(open)
            .addAction(stopAction)
            .build()
    }

    companion object {
        private const val TAG = "BoostService"
        private const val CHANNEL_ID = "boost"
        private const val NOTIF_ID = 1
        private const val IDLE_STOP_DELAY_MS = 2500L
        const val ACTION_START = "com.volumeboost.app.START"
        const val ACTION_STOP = "com.volumeboost.app.STOP"

        /** Debe llamarse con la app a la vista (Android 12+ no deja arrancarlo desde segundo plano). */
        fun start(context: Context) {
            context.startForegroundService(
                Intent(context, BoostService::class.java).setAction(ACTION_START)
            )
        }
    }
}
