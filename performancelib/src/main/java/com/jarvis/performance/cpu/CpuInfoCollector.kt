package com.jarvis.performance.cpu


import com.jarvis.performance.cpu.C
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.*
import java.util.regex.Pattern

object CpuInfoCollector {

    // core count cache
    private var sLastCpuCoreCount = -1

    /**
     * Gets the number of cores available in this device, across all processors.
     * Requires: Ability to peruse the filesystem at "/sys/devices/system/cpu"
     *
     * @return The number of cores, or "availableProcessors" if failed to get result
     */
    // from http://stackoverflow.com/questions/7962155/how-can-you-detect-a-dual-core-cpu-on-an-android-device-from-code
    fun calcCpuCoreCount(): Int {

        if (sLastCpuCoreCount >= 1) {
            // 缓存
            return sLastCpuCoreCount
        }

        sLastCpuCoreCount = try {
            // Get directory containing CPU info
            val dir = File("/sys/devices/system/cpu/")
            // Filter to only list the devices we care about
            val files = dir.listFiles { pathname ->
                //Check if filename is "cpu", followed by a single digit number
                Pattern.matches("cpu[0-9]", pathname.name)
            }

            // Return the number of cores (virtual CPU devices)
            files.size

        } catch (e: Exception) {
            Runtime.getRuntime().availableProcessors()
        }

        return sLastCpuCoreCount
    }

    /**
     * 取得现在的CPU时钟频率
     *
     * @return 384000 类似的数值(取得错误时0)
     */
    private fun takeCurrentCpuFreq(coreIndex: Int): Int {
        return readIntegerFile("/sys/devices/system/cpu/cpu$coreIndex/cpufreq/scaling_cur_freq")
    }

    /**
     * 最小CPU时钟频率
     *
     * @return
     */
    private fun takeMinCpuFreq(coreIndex: Int): Int {
        return readIntegerFile("/sys/devices/system/cpu/cpu$coreIndex/cpufreq/cpuinfo_min_freq")
    }

    /**
     * 最大CPU时钟频率
     *
     * @return
     */
    private fun takeMaxCpuFreq(coreIndex: Int): Int {
        return readIntegerFile("/sys/devices/system/cpu/cpu$coreIndex/cpufreq/cpuinfo_max_freq")
    }

    /**
     * 总的CPU频率
     */
    fun takeAllCoreFreqs(fi: AllCoreFrequencyInfo) {
        val n = calcCpuCoreCount()

        for (i in 0 until n) {
            fi.freqs[i] = takeCurrentCpuFreq(i)
            fi.minFreqs[i] = takeMinCpuFreq(i)
            fi.maxFreqs[i] = takeMaxCpuFreq(i)
        }
    }

    private fun readIntegerFile(filePath: String): Int {

        try {
            BufferedReader(
                    InputStreamReader(FileInputStream(filePath)), 1000).use { reader ->

                val line = reader.readLine()
                return Integer.parseInt(line)
            }

        } catch (e: Exception) {
            //MyLog.e(e);

            return 0
        }

    }

    /**
     * 通过/proc/stat 取得CPU总用量
     *
     * @return
     */
    fun takeCpuUsageSnapshot(): ArrayList<OneCpuInfo>? {

        // [0] 总的、[1]各CPU
        val result = ArrayList<OneCpuInfo>()

        try {
            BufferedReader(InputStreamReader(FileInputStream("/proc/stat")), C.READ_BUFFER_SIZE).use { it ->
                it.forEachLine { line ->

                    if (!line.startsWith("cpu")) {
                        return@forEachLine
                    }
//                  MyLog.i(" load:" + load);

                    //     user     nice    system  idle    iowait  irq     softirq     steal
                    //cpu  48200 4601 35693 979258 5095 1 855 0 0 0
                    //cpu0 26847 1924 25608 212324 2212 1 782 0 0 0
                    //cpu1 8371 1003 4180 254096 1026 0 50 0 0 0
                    //cpu2 8450 983 3916 252872 1304 0 9 0 0 0
                    //cpu3 4532 691 1989 259966 553 0 14 0 0 0

                    val tokens = line.split(" +".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    val oci = OneCpuInfo()
                    oci.idle = java.lang.Long.parseLong(tokens[4])
                    oci.total = (java.lang.Long.parseLong(tokens[1])
                            + java.lang.Long.parseLong(tokens[2])
                            + java.lang.Long.parseLong(tokens[3])
                            + oci.idle
                            + java.lang.Long.parseLong(tokens[5])
                            + java.lang.Long.parseLong(tokens[6])
                            + java.lang.Long.parseLong(tokens[7]))
                    result.add(oci)
                }
            }
            return result

        } catch (ex: Exception) {
            MyLog.e(ex)
            return null
        }

    }
}
