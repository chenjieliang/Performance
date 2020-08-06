package com.jarvis.performance

import android.app.ActivityManager
import android.content.Context
import java.io.BufferedReader
import java.io.FileReader
import java.io.IOException

/**
 *@author chenjieliang on 20-8-4
 */
object MemoryUtil {

    /**
     * 计算已使用内存的百分比，并返回。
     *
     * @param context
     * 可传入应用程序上下文。
     * @return 已使用内存的百分比，以字符串形式返回。
     */
    fun getUsedPercentValue(context: Context): String {
        val totalMemorySize = getTotalMemory()
        val availableSize = getAvailableMemory(context) / 1024
        val percent = ((totalMemorySize - availableSize) / totalMemorySize.toFloat() * 100).toInt()
        return percent.toString() + "%"
    }

    /**
     * 获取当前可用内存，返回数据以字节为单位。
     *
     * @param context 可传入应用程序上下文。
     * @return 当前可用内存。
     */
    fun getAvailableMemory(context: Context): Long {
        val mi = ActivityManager.MemoryInfo()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        activityManager.getMemoryInfo(mi)
        return mi.availMem
    }

    fun getProcessMemeryInfo(context: Context): Float {
        val pid = android.os.Process.myPid()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfoArray = activityManager.getProcessMemoryInfo(intArrayOf(pid))
        return (memoryInfoArray[0].getTotalPrivateDirty() / 1024).toFloat()
    }

    /**
     * 获取系统总内存,返回字节单位为KB
     * @return 系统总内存
     */
    fun getTotalMemory(): Long {
        var totalMemorySize: Long = 0
        val dir = "/proc/meminfo"
        try {
            val fr = FileReader(dir)
            val br = BufferedReader(fr, 2048)
            val memoryLine = br.readLine()
            val subMemoryLine = memoryLine.substring(memoryLine.indexOf("MemTotal:"))
            br.close()
            //将非数字的字符替换为空
            totalMemorySize = Integer.parseInt(subMemoryLine.replace("\\D+".toRegex(), "")).toLong()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return totalMemorySize
    }
}