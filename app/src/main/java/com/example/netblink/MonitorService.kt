package com.example.netblink

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors

/**
 * مراقبة خفيفة لجودة الشبكة:
 * - استدعاء من النظام عند فقدان الشبكة (بدون استهلاك).
 * - فحص اتصال TCP صغير كل بضع ثوانٍ (بضع مئات من البايتات).
 * عند فشل عدة فحوصات متتالية يُعاد الاتصال تلقائيًا مع فترة تهدئة.
 */
class MonitorService : Service() {

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var prefs: Prefs
    private lateinit var cm: ConnectivityManager

    private var fails = 0
    private var lastCutAt = -1_000_000L
    private var lastStatus = ""

    private val tick = object : Runnable {
        override fun run() {
            io.execute { check() }
            main.postDelayed(this, prefs.intervalSec * 1000L)
        }
    }

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onLost(network: Network) {
            // انخفاض حاد: قرّب العدّاد من الحد، ثم افحص بعد مهلة قصيرة (قد يكون مجرد تبديل واي فاي/بيانات)
            fails = maxOf(fails, prefs.failThreshold - 1)
            main.postDelayed({ io.execute { check() } }, 3000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        NetController.recover(this)
        startInForeground(getString(R.string.notif_monitoring))
        try {
            cm.registerDefaultNetworkCallback(callback)
        } catch (_: Throwable) {
        }
        main.postDelayed(tick, 5000)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        try {
            cm.unregisterNetworkCallback(callback)
        } catch (_: Throwable) {
        }
        io.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun check() {
        if (NetController.busy.get() || NetController.airplaneOn(this)) return

        val latency = probe()
        val bad = latency == null || latency > prefs.latencyMs
        fails = if (bad) fails + 1 else 0

        updateNotification(
            if (latency == null) getString(R.string.notif_no_net)
            else getString(R.string.notif_ok, latency)
        )

        val now = SystemClock.elapsedRealtime()
        val cooled = now - lastCutAt >= prefs.cooldownSec * 1000L
        if (fails >= prefs.failThreshold && cooled && NetController.shizukuReady()) {
            lastCutAt = now
            fails = 0
            updateNotification(getString(R.string.notif_reconnecting))
            NetController.cut(this) {
                lastCutAt = SystemClock.elapsedRealtime()
                fails = 0
            }
        }
    }

    /** زمن الاستجابة بالمللي ثانية، أو null إن فشل الاتصال. */
    private fun probe(): Long? {
        val targets = listOf("1.1.1.1" to 443, "8.8.8.8" to 53)
        for ((host, port) in targets) {
            try {
                val t0 = SystemClock.elapsedRealtime()
                Socket().use { it.connect(InetSocketAddress(host, port), 2500) }
                return SystemClock.elapsedRealtime() - t0
            } catch (_: Exception) {
            }
        }
        return null
    }

    // ---------- الإشعار ----------

    private fun channel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            val ch = NotificationChannel(CHANNEL, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_MIN)
            nm.createNotificationChannel(ch)
        }
    }

    private fun build(text: String): Notification {
        channel()
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    private fun startInForeground(text: String) {
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIF_ID, build(text), type)
    }

    private fun updateNotification(text: String) {
        if (text == lastStatus) return
        lastStatus = text
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, build(text))
    }

    companion object {
        private const val CHANNEL = "monitor"
        private const val NOTIF_ID = 1

        fun start(context: Context) {
            context.startForegroundService(Intent(context, MonitorService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MonitorService::class.java))
        }
    }
}
