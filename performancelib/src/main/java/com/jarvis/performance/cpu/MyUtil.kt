package com.jarvis.performance.cpu

import java.util.*

object MyUtil {

    /**
     * CPU使用率 算出
     *
     * @param currentInfo
     * @param lastInfo
     * @return CPU活动率的排列(要素数必定1以上，[0]总CPU，[1]各cpu)，或null
     */
    fun calcCpuUsages(currentInfo: ArrayList<OneCpuInfo>?, lastInfo: ArrayList<OneCpuInfo>?): IntArray? {

        if (currentInfo == null || lastInfo == null) {
            // NPE(基本不会。服务死时可能有吗？)
            return null
        }

        val nLast = lastInfo.size
        val nCurr = currentInfo.size
        if (nLast == 0 || nCurr == 0) {
            MyLog.d(" no info: [$nLast][$nCurr]")
            return null
        }


        val n = if (nLast < nCurr) nLast else nCurr  // min(nLast, nCurr)
        val cpuUsages = IntArray(n)
        for (i in 0 until n) {
            val last = lastInfo[i]
            val curr = currentInfo[i]

            val totalDiff = (curr.total - last.total).toInt()
            if (totalDiff > 0) {
                val idleDiff = (curr.idle - last.idle).toInt()
//              final double cpuUsage = 1.0 - (double)idleDiff / totalDiff;
//              cpuUsages[i] = (int)(cpuUsage * 100.0);
                cpuUsages[i] = 100 - idleDiff * 100 / totalDiff

//              MyLog.i(" idle[" + idleDiff + "], total[" + totalDiff + "], " +
//                      "/[" + (100-idleDiff*100/totalDiff) + "], " +
//                      "rate[" + cpuUsages[i] + "], " +
//                      "rate[" + ((1.0-(double)idleDiff/totalDiff) * 100.0) + "]"
//                      );
            } else {
                cpuUsages[i] = 0
            }

//          MyLog.d(" [" + (i == 0 ? "all" : i) + "] : [" + (int)(cpuUsage * 100.0) + "%]" +
//                  " idle[" + idleDiff + "], total[" + totalDiff + "]");
        }

        return cpuUsages
    }

    /**
     * CPU使用率 通过频率计算得
     */
    fun calcCpuUsagesByCoreFrequencies(fi: AllCoreFrequencyInfo): IntArray {

        val coreCount = fi.freqs.size

        // [0] 总、[1]～[coreCount] 各个 CPU 使用率
        val cpuUsages = IntArray(coreCount + 1)

        // 各个 CPU 使用率
        for (i in 0 until coreCount) {
            cpuUsages[i + 1] = MyUtil.getClockPercent(fi.freqs[i], fi.minFreqs[i], fi.maxFreqs[i])
//            MyLog.i("calc core[" + i + "] = " + cpuUsages[i+1] + "% (max=" + fi.maxFreqs[i] + ")");
        }

        // 总CPU 使用率算出
        var freqSum = 0
        var minFreqSum = 0
        var maxFreqSum = 0
        for (i in 0 until coreCount) {
            freqSum += fi.freqs[i]
            minFreqSum += fi.minFreqs[i]
            maxFreqSum += fi.maxFreqs[i]
        }
        cpuUsages[0] = MyUtil.getClockPercent(freqSum, minFreqSum, maxFreqSum)

        return cpuUsages
    }

    /**
     * 时钟频率的表示用整形
     *
     * @param clockHz 时钟频率(KHz)
     * @return "XX MHz"或"X.X GHz"
     */
    fun formatFreq(clockHz: Int): String {

        if (clockHz < 1000 * 1000) {
            return (clockHz / 1000).toString() + " MHz"
        }

        // a.b GHz
        val a = clockHz / 1000 / 1000      // a.b GHz の a 値
        val b = clockHz / 1000 / 100 % 10  // a.b GHz の b 値
        return a.toString() + "." + b + " GHz"
    }

    /**
     * 当前活跃的Core的索引
     */
    fun getActiveCoreIndex(freqs: IntArray): Int {

        var targetCore = 0
        for (i in 1 until freqs.size) {
            if (freqs[i] > freqs[targetCore]) {
                targetCore = i
            }
        }
        return targetCore
    }

    /**
     * 时钟频率的 current/min/max从[0,100]%算出
     */
    fun getClockPercent(currentFreq: Int, minFreq: Int, maxFreq: Int): Int {
        if (maxFreq - minFreq <= 0) {
            return 0
        }
        return if (maxFreq >= 0) (currentFreq - minFreq) * 100 / (maxFreq - minFreq) else 0
    }

}
