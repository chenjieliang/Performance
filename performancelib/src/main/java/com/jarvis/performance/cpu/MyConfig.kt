package com.jarvis.performance.cpu
import android.content.Context
import android.preference.PreferenceManager


internal class MyConfig {

    // 更新間隔
    var intervalMs = (C.PREF_DEFAULT_UPDATE_INTERVAL_SEC * 1000).toLong()

    /**
     *
     */
    fun loadSettings(context: Context) {

        MyLog.i("load settings")

        val pref = PreferenceManager.getDefaultSharedPreferences(context)

        // 更新間隔
        val updateIntervalSec = pref.getString(C.PREF_KEY_UPDATE_INTERVAL_SEC, "" + C.PREF_DEFAULT_UPDATE_INTERVAL_SEC)
        try {
            intervalMs = (java.lang.Double.parseDouble(updateIntervalSec) * 1000.0).toInt().toLong()
            MyLog.i(" interval[" + intervalMs + "ms]")
        } catch (e: NumberFormatException) {
            MyLog.e(e)
        }

    }


}
