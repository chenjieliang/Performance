package com.jarvis.performance

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Resources
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.RemoteException
import android.provider.Settings
import android.util.TypedValue
import android.view.*
import android.widget.Button
import com.jarvis.performance.cpu.IUsageUpdateCallback
import com.jarvis.performance.cpu.IUsageUpdateService
import com.jarvis.performance.cpu.MyUtil
import java.io.Serializable
import java.util.ArrayList

/**
 *@author chenjieliang on 20-8-4
 */
class PerformanceMonitorService : Service(){


    private var windowManager: WindowManager? = null
    private var layoutParams : WindowManager.LayoutParams? = null
    private var button : Button? = null

    private var mServiceIf : IUsageUpdateService? = null

    private val mTempTotalCpuUsage = ArrayList<Int>()

    private var mWarnConfig: WarnConfig? = null

    class WarnConfig(
            // 发生警告时的平均使用率
            internal var warnAverageUsage: Int,
            // 计算平均使用率使用的时间
            internal var seconds: Int) : Serializable

    companion object {
        val KEY_START_MONITOR_STICKY = "START_INFO"
        val KEY_WARN_CONFIG = "WARN_CONFIG"

        fun startMonitorCpuinfo(context: Context) {
            val intent = Intent(context, PerformanceMonitorService::class.java)
            intent.putExtra(KEY_START_MONITOR_STICKY, true)
            context.startService(intent)
        }

        fun startMonitorCpuinfoOnlyUsageAlert(context: Context, warnConfig: WarnConfig) {
            if (warnConfig == null) {
                return
            }
            var intent = Intent(context, PerformanceMonitorService::class.java)
            intent.putExtra(KEY_WARN_CONFIG, warnConfig)
            intent.putExtra(KEY_START_MONITOR_STICKY, true)
            context.startService(intent)
        }
    }

    private var mCallback = object : IUsageUpdateCallback.Stub() {

        @Throws(RemoteException::class)
        override fun updateUsage(cpuUsages: IntArray, freqs: IntArray, minFreqs: IntArray, maxFreqs: IntArray) {
            showCpuUsages(cpuUsages, freqs, minFreqs, maxFreqs)
        }
    }

    private var mServiceConnection  = object : ServiceConnection {

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            mServiceIf = IUsageUpdateService.Stub.asInterface(service)

            try {
                mServiceIf?.registerCallback(mCallback)
            } catch (e: RemoteException) {
                e.fillInStackTrace()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mServiceIf = null

        }

    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        layoutParams = createFloatWindowParam(0, WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT)
        layoutParams?.x = 0
        // start
        showMonitorFloatingWindow(false)
        var intent = Intent("com.jarvis.performance.cpu.IUsageUpdateService")
        intent.setPackage(getPackageName())
        bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE)
    }

    fun createFloatWindowParam(y:Int, width:Int, height:Int) : WindowManager.LayoutParams {
        var params = WindowManager.LayoutParams()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            params.type = WindowManager.LayoutParams.TYPE_PHONE
        }
        params.format = PixelFormat.RGBA_8888
        params.gravity = Gravity.LEFT or Gravity.TOP
        params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        params.width = width
        params.height = height
        params.x = Resources.getSystem().displayMetrics.widthPixels - params.width
        params.y = y
        return params
    }

    override fun onBind(intent: Intent?): IBinder {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        var shouldMonitor = intent?.getBooleanExtra(KEY_START_MONITOR_STICKY, false)
        if (shouldMonitor!=false) {
            mWarnConfig = intent?.getSerializableExtra(KEY_WARN_CONFIG) as WarnConfig?
            showMonitorFloatingWindow(true)
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        if (button != null) {
            windowManager?.removeView(button)
        }
        //解除绑定
        if (mServiceIf != null) {
            try {
                mServiceIf?.unregisterCallback(mCallback)
            } catch (e: RemoteException) {
                e.fillInStackTrace()
            }

        }
        if (mServiceConnection != null) {
            unbindService(mServiceConnection)
        }
        super.onDestroy()
    }

    fun showMonitorFloatingWindow(show:Boolean) {
        if (button == null) {
            button = Button(applicationContext)
            button?.setText("暂时未获取到系统信息")
            button?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
            button?.setTextColor(Color.WHITE)
            button?.setBackgroundColor(Color.BLACK)
            button?.setAlpha(.5f)
            var padding = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16f,resources.displayMetrics).toInt();
            button?.setPadding(padding, padding, padding, padding)
            button?.setGravity(Gravity.LEFT)
            button?.setOnTouchListener(FloatingOnTouchListener())

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {//判断系统版本
                if (Settings.canDrawOverlays(this)) {
                    windowManager?.addView(button, layoutParams)
                }
            } else {
                windowManager?.addView(button, layoutParams)
            }
        }
        button?.post(Runnable {
            button?.setVisibility(if (show) View.VISIBLE else View.INVISIBLE)
        })
    }

    fun showCpuUsages (cpuUsages:IntArray,freqs:IntArray,minFreqs:IntArray,maxFreqs:IntArray) {
        var ssb = StringBuilder();
        for (i in 0 until cpuUsages.size) {
            var cpuUsage = 0
            if (cpuUsages != null) {
                cpuUsage = cpuUsages[i]
            }
            var name = ""
            if (i == 0) {
                name = "cpu"
            } else {
                name = "cpu" + (i - 1)
            }
            ssb.append("$name  $cpuUsage%  ")
            // 频率
            if (i > 0) {
                val freqText = MyUtil.formatFreq(freqs[i - 1])
                ssb.append(freqText)
            }
            if (i < cpuUsages.size) {
                ssb.append("\n")
            }
        }
        if (mWarnConfig != null) {
            //统计CPU使用率是否连续超过设定的值
            if (mTempTotalCpuUsage.size > mWarnConfig!!.seconds) {
                mTempTotalCpuUsage.removeAt(0)
            }
            mTempTotalCpuUsage.add(cpuUsages[0])
            var tempTotal = 0
            for (perSecUsage in mTempTotalCpuUsage) {
                tempTotal += perSecUsage
            }
            if (tempTotal / mTempTotalCpuUsage.size >= mWarnConfig!!.warnAverageUsage) {
                ssb.append(mWarnConfig?.seconds.toString() + "秒内平均使用率:" + tempTotal / mTempTotalCpuUsage.size + "% !")
                showMonitorFloatingWindow(true)
            } else {
                showMonitorFloatingWindow(false)
                mTempTotalCpuUsage.clear()
            }

        }

        ssb.append("\n")

        try {
            val M = 1024 * 1024
            val r = Runtime.getRuntime()
            ssb.append("最大可用内存:" + r.maxMemory() / M + " MB").append("\n")
            //ssb.append("当前可用内存:" + r.totalMemory()/ M + " MB").append("\n");
            //ssb.append("当前空闲内存:" + r.freeMemory() / M + " MB").append("\n");
            ssb.append("当前堆内存使用:" + (r.totalMemory() - r.freeMemory()) / M + " MB").append("\n")

        } catch (e: Exception) {
            // ignore
            e.printStackTrace()
        }

        try {
            val procMemory = MemoryUtil.getProcessMemeryInfo(applicationContext).toString() + " MB"
            ssb.append("实际内存使用: ").append(procMemory).append("\n")
        } catch (e: Exception) {
            // ignore
            e.printStackTrace()
        }


        try {
            val memoryRate = MemoryUtil.getUsedPercentValue(applicationContext)
            ssb.append("系统总内存占用: ").append(memoryRate)
        } catch (e: Exception) {
            // ignore
            e.printStackTrace()
        }

        button?.post(Runnable {
            button?.setText(ssb.toString())
        })
    }

    private inner class FloatingOnTouchListener : View.OnTouchListener {

        private var x: Int = 0
        private var y: Int = 0

        override fun onTouch(v: View?, event: MotionEvent?): Boolean {
            when (event?.getAction()) {
                MotionEvent.ACTION_DOWN -> {
                    x = event.getRawX().toInt()
                    y = event.getRawY().toInt()
                }
                MotionEvent.ACTION_MOVE -> {
                    val nowX = event.getRawX().toInt()
                    val nowY = event.getRawY().toInt()
                    val movedX = nowX - x
                    val movedY = nowY - y
                    x = nowX
                    y = nowY
                    layoutParams?.x = layoutParams!!.x + movedX
                    layoutParams?.y = layoutParams!!.y + movedY
                    windowManager?.updateViewLayout(v, layoutParams)
                }
                else -> {
                }
            }
            return false
        }

    }

}