package com.jarvis.performance.cpu

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.os.RemoteCallbackList
import android.os.RemoteException
import android.os.SystemClock
import com.jarvis.performance.cpu.C
import com.jarvis.performance.cpu.MyConfig
import com.jarvis.performance.cpu.IUsageUpdateCallback
import com.jarvis.performance.cpu.IUsageUpdateService
import java.util.*


class UsageUpdateService : Service() {

    // 設定値
    private val mConfig = MyConfig()

    // 通知管理
    //private val mNotificationPresenter = NotificationPresenter(this, mConfig)

    // 常駐停止
    private var mStopResident = false

    // (Android 8.0 以下用) BOOT_COMPLETED  startForeground
    private var mRequestForeground = false

    private var mSleeping = false

    // 上次的 CPU 时钟频率
    private var mLastCpuClock = -1

    private var mLastCpuUsageSnapshot: ArrayList<OneCpuInfo>? = null

    // 上个CPU使用率
    private var mLastCpuUsages: IntArray? = null

    // CPU使用率计算(Android 8.0以下用)
    private var mUseFreqForCpuUsage = false

    private val mCallbackList = RemoteCallbackList<IUsageUpdateCallback>()

    private var mCallbackListSize = 0

    private var mLastExecTask = System.currentTimeMillis()

    private var mThread: GatherThread? = null
    private var mThreadActive = false


    private val mBinder = object : IUsageUpdateService.Stub() {

        @Throws(RemoteException::class)
        override fun registerCallback(callback: IUsageUpdateCallback) {

            // 注册
            mCallbackList.register(callback)

            mCallbackListSize++
        }

        @Throws(RemoteException::class)
        override fun unregisterCallback(callback: IUsageUpdateCallback) {

            // 解除
            mCallbackList.unregister(callback)

            if (mCallbackListSize > 0) {
                mCallbackListSize--
            }
        }

        @Throws(RemoteException::class)
        override fun stopResident() {

            // 停止
            this@UsageUpdateService.stopResident()
        }

        @Throws(RemoteException::class)
        override fun startResident() {

            // 解除
            mStopResident = false

            this@UsageUpdateService.scheduleNextTime(C.ALARM_STARTUP_DELAY_MSEC.toLong())

            if (mThread == null) {
                startThread()
            }
        }

        @Throws(RemoteException::class)
        override fun reloadSettings() {

            MyLog.d("reloadSettings")
            mConfig.loadSettings(this@UsageUpdateService)

            //mNotificationPresenter.cancelNotifications()
        }
    }

    private val mReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return

            if (action == Intent.ACTION_SCREEN_ON) {

                MyLog.d("screen on")

                mSleeping = false

                //mNotificationPresenter.mNotificationTimeKeep = System.currentTimeMillis() + 30 * 1000

                this@UsageUpdateService.scheduleNextTime(C.ALARM_STARTUP_DELAY_MSEC.toLong())

                if (!mStopResident) {
                    startThread()
                }

            } else if (action == Intent.ACTION_SCREEN_OFF) {

                MyLog.d("screen off")

                mSleeping = true

                stopAlarm()

                stopThread()
            }
        }
    }

    override fun onBind(intent: Intent): IBinder? {

        MyLog.i("UsageUpdateService.onBind")

        if (IUsageUpdateService::class.java.name == intent.action) {
            return mBinder
        }

        startThread()

        return null
    }

    override fun onCreate() {
        super.onCreate()

        MyLog.i("UsageUpdateService.onCreate")

        mConfig.loadSettings(this)

        if (mLastCpuUsageSnapshot == null) {
            mLastCpuUsageSnapshot = CpuInfoCollector.takeCpuUsageSnapshot()
        }

        applicationContext.registerReceiver(mReceiver, IntentFilter(Intent.ACTION_SCREEN_ON))
        applicationContext.registerReceiver(mReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))

        scheduleNextTime(C.ALARM_STARTUP_DELAY_MSEC.toLong())

        startThread()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val result = super.onStartCommand(intent, flags, startId)

        mRequestForeground = intent != null && intent.getBooleanExtra("FOREGROUND_REQUEST", false)
        MyLog.i("UsageUpdateService.onStartCommand[$mRequestForeground]")

        if (mThread == null) {
            startThread()
        }

        scheduleNextTime(C.ALARM_INTERVAL_MSEC.toLong())

        return result
    }

    private fun execTask() {

        //-------------------------------------------------
        // CPU 时钟频率的取得
        //-------------------------------------------------
        val fi = AllCoreFrequencyInfo(CpuInfoCollector.calcCpuCoreCount())
        CpuInfoCollector.takeAllCoreFreqs(fi)

        val activeCoreIndex = MyUtil.getActiveCoreIndex(fi.freqs)
        val currentCpuClock = fi.freqs[activeCoreIndex]

        // CPU 时钟频率 min/max
        val minFreq = fi.minFreqs[activeCoreIndex]
        val maxFreq = fi.maxFreqs[activeCoreIndex]

        if (MyLog.debugMode) {
            MyLog.d("* CPU: " + currentCpuClock + " [" + minFreq + "," + maxFreq + "] [" + (System.currentTimeMillis() - mLastExecTask) + "ms]")
        }


        //-------------------------------------------------
        // CPU 使用率取得
        //-------------------------------------------------
        // CPU 使用率 snapshot 取得
        var cpuUsages: IntArray? = null
        if (!mUseFreqForCpuUsage) {
            //通过/proc/stat取得
            val currentCpuUsageSnapshot = CpuInfoCollector.takeCpuUsageSnapshot()
            if (currentCpuUsageSnapshot != null) {
                // CPU 使用率 算出
                cpuUsages = MyUtil.calcCpuUsages(currentCpuUsageSnapshot, mLastCpuUsageSnapshot)
                // snapshot 保存
                mLastCpuUsageSnapshot = currentCpuUsageSnapshot
            } else {
                mUseFreqForCpuUsage = true
            }
        }
        if (cpuUsages == null) {
            // android 8.0 无法通过/proc/stat取得
            cpuUsages = MyUtil.calcCpuUsagesByCoreFrequencies(fi)
        }


        //-------------------------------------------------
        // 通知判定
        //-------------------------------------------------
        val updated = isUpdated(currentCpuClock, cpuUsages)
        mLastCpuUsages = cpuUsages
        mLastCpuClock = currentCpuClock

        //-------------------------------------------------
        // 通知
        //-------------------------------------------------
        if (!updated) {
            if (MyLog.debugMode) {
                MyLog.d("- skipping caused by no diff.")
            }
        } else {
            //mNotificationPresenter.updateNotifications(cpuUsages, currentCpuClock, minFreq, maxFreq, mRequestForeground)
            mRequestForeground = false

            distributeToCallbacks(cpuUsages, fi)
        }

        mLastExecTask = System.currentTimeMillis()
    }

    private fun isUpdated(currentCpuClock: Int, cpuUsages: IntArray?): Boolean {

        if (mLastCpuClock != currentCpuClock) {
            return true
        } else if (mLastCpuUsages == null || cpuUsages == null) {
            return true
        } else if (cpuUsages.size != mLastCpuUsages!!.size) {

            return true
        } else {

            val n = cpuUsages.size
            for (i in 0 until n) {
                if (cpuUsages[i] != mLastCpuUsages!![i]) {
                    return true
                }
            }
        }
        return false
    }

    private fun distributeToCallbacks(cpuUsages: IntArray, fi: AllCoreFrequencyInfo) {

        if (mCallbackListSize >= 1) {
            val n = mCallbackList.beginBroadcast()

            mCallbackListSize = n

//          if (MyLog.debugMode) {
//              MyLog.d("- broadcast:" + n);
//          }

            for (i in 0 until n) {
                try {
                    mCallbackList.getBroadcastItem(i).updateUsage(cpuUsages,
                            fi.freqs, fi.minFreqs, fi.maxFreqs)
                } catch (e: RemoteException) {
//                  MyLog.e(e);
                }

            }
            mCallbackList.finishBroadcast()
        }
    }

    override fun onDestroy() {

        MyLog.d("UsageUpdateService.onDestroy")

        stopAlarm()

        stopThread()

        applicationContext.unregisterReceiver(mReceiver)

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancelAll()

        super.onDestroy()
    }

    fun scheduleNextTime(intervalMs: Long) {


        if (mStopResident) {
            return
        }
        if (mSleeping) {
            return
        }

        val now = System.currentTimeMillis()

        val intent = Intent(this, this.javaClass)
        val alarmSender = PendingIntent.getService(
                this,
                0,
                intent,
                0
        )

        val am = this.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        am.set(AlarmManager.RTC, now + intervalMs, alarmSender)

        MyLog.d("-- scheduled[" + intervalMs + "ms]")
    }

    /**
     * 常駐停止
     */
    fun stopResident() {

        mStopResident = true

        stopThread()

        stopAlarm()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancelAll()

        stopSelf()
    }


    private fun stopAlarm() {


        val intent = Intent(this, this.javaClass)


        val pendingIntent = PendingIntent.getService(
                this,
                0, // ここを-1にすると解除に成功しない
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT
        )

        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent)
        // @see http://creadorgranoeste.blogspot.com/2011/06/alarmmanager.html
    }

    private fun startThread() {

        if (mThread == null) {
            mThread = GatherThread()
            mThreadActive = true
            mThread!!.start()
            MyLog.d("UsageUpdateService.startThread: thread start")
        } else {
            MyLog.d("UsageUpdateService.startThread: already running")
        }
    }

    private fun stopThread() {

        if (mThreadActive && mThread != null) {
            MyLog.d("UsageUpdateService.stopThread")

            mThreadActive = false
            while (true) {
                try {
                    mThread!!.join()
                    break
                } catch (ignored: InterruptedException) {
                    MyLog.e(ignored)
                }

            }
            mThread = null
        } else {
            MyLog.d("UsageUpdateService.stopThread: no thread")
        }
    }


    private inner class GatherThread : Thread() {

        override fun run() {

            MyLog.d("UsageUpdateService\$GatherThread: start")

            while (mThread != null && mThreadActive) {

                SystemClock.sleep(mConfig.intervalMs)

                if (mThreadActive && !mStopResident) {
                    execTask()
                }
            }

            MyLog.d("UsageUpdateService\$GatherThread: done")
        }
    }
}
