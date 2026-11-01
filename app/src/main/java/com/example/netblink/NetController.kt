package com.example.netblink

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import rikka.shizuku.Shizuku
import java.util.concurrent.atomic.AtomicBoolean

/** تبديل وضع الطيران عبر Shizuku (بصلاحيات shell) دون روت. */
object NetController {

    val busy = AtomicBoolean(false)

    private const val ON =
        "cmd connectivity airplane-mode enable || " +
            "(settings put global airplane_mode_on 1; am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true)"
    private const val OFF =
        "cmd connectivity airplane-mode disable || " +
            "(settings put global airplane_mode_on 0; am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false)"

    fun shizukuRunning(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    fun shizukuReady(): Boolean = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    fun airplaneOn(context: Context): Boolean =
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1

    /** تنفيذ أمر shell عبر Shizuku. newProcess خاص في الإصدارات الحديثة لذلك نستدعيه بالانعكاس. */
    private fun sh(command: String): Int {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        method.isAccessible = true
        val process = method.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
        return process.waitFor()
    }

    /**
     * يقطع الاتصال لمدة [seconds] ثم يعيده. يعمل في خيط مستقل ولا يسمح بتشغيلين متزامنين.
     * [onDone] يُستدعى عند الانتهاء (من خيط العمل) ومعه نجاح العملية.
     */
    fun cut(context: Context, seconds: Int = Prefs(context).cutSeconds, onDone: ((Boolean) -> Unit)? = null) {
        val app = context.applicationContext
        if (!shizukuReady()) {
            onDone?.invoke(false)
            return
        }
        if (!busy.compareAndSet(false, true)) return
        val prefs = Prefs(app)
        Thread {
            var ok = false
            try {
                prefs.cutting = true
                sh(ON)
                Thread.sleep(seconds * 1000L)
                ok = true
            } catch (_: Throwable) {
            } finally {
                // دائمًا أعد الاتصال مهما حدث
                try {
                    sh(OFF)
                } catch (_: Throwable) {
                }
                prefs.cutting = false
                busy.set(false)
                onDone?.invoke(ok)
            }
        }.start()
    }

    /** إن أُغلق التطبيق أثناء القطع، يتم إطفاء وضع الطيران عند التشغيل التالي. */
    fun recover(context: Context) {
        val prefs = Prefs(context)
        if (prefs.cutting && !busy.get() && shizukuReady()) {
            Thread {
                try {
                    sh(OFF)
                } catch (_: Throwable) {
                }
                prefs.cutting = false
            }.start()
        }
    }
}
