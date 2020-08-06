package com.jarvis.performance.cpu

object C {

    const val LOG_NAME = "CpuStats"


    const val PREF_KEY_UPDATE_INTERVAL_SEC = "UpdateIntervalSec"
    const val PREF_DEFAULT_UPDATE_INTERVAL_SEC = 1

    // Alarm的延迟时间[ms]
    const val ALARM_STARTUP_DELAY_MSEC = 1000

    // Service维持的Alarm的更新间隔[ms]
    const val ALARM_INTERVAL_MSEC = 60 * 1000

    const val READ_BUFFER_SIZE = 1024
}
