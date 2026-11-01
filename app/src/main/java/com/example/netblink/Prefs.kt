package com.example.netblink

import android.content.Context

/** إعدادات التطبيق. القيم الافتراضية مناسبة للاستخدام العادي. */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("netblink", Context.MODE_PRIVATE)

    /** تشغيل المراقبة التلقائية */
    var auto: Boolean
        get() = sp.getBoolean("auto", false)
        set(v) = sp.edit().putBoolean("auto", v).apply()

    /** مدة قطع الاتصال بالثواني */
    var cutSeconds: Int
        get() = sp.getInt("cutSeconds", 10)
        set(v) = sp.edit().putInt("cutSeconds", v).apply()

    /** الفاصل بين فحوصات الجودة بالثواني */
    var intervalSec: Int
        get() = sp.getInt("intervalSec", 20)
        set(v) = sp.edit().putInt("intervalSec", v).apply()

    /** عدد الفحوصات الفاشلة المتتالية قبل إعادة الاتصال */
    var failThreshold: Int
        get() = sp.getInt("failThreshold", 3)
        set(v) = sp.edit().putInt("failThreshold", v).apply()

    /** أقل مدة بين إعادتي اتصال تلقائيتين بالثواني */
    var cooldownSec: Int
        get() = sp.getInt("cooldownSec", 60)
        set(v) = sp.edit().putInt("cooldownSec", v).apply()

    /** زمن استجابة أعلى منه يُعدّ فحصًا فاشلًا (مللي ثانية) */
    var latencyMs: Int
        get() = sp.getInt("latencyMs", 1500)
        set(v) = sp.edit().putInt("latencyMs", v).apply()

    /** علامة أمان: هل كان هناك قطع جارٍ عند آخر تشغيل؟ */
    var cutting: Boolean
        get() = sp.getBoolean("cutting", false)
        set(v) = sp.edit().putBoolean("cutting", v).apply()
}
