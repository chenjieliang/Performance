package com.jarvis.performance.cpu

import android.util.Log
import com.jarvis.performance.cpu.C

object MyLog {

    var debugMode = false

    fun v(msg: String) {
        if (debugMode) {
            Log.v(C.LOG_NAME, msg)
        }
    }

    fun v(msg: String, th: Throwable) {
        if (debugMode) {
            Log.v(C.LOG_NAME, msg, th)
        }
    }

    fun d(msg: String) {
        if (debugMode) {
            Log.d(C.LOG_NAME, msg)
        }

        MyLog.dumpToExternalLogFile(Log.DEBUG, msg)
    }

    /**
     *
     * @param msg
     * @param startTick 開始時刻[ms]
     */
    fun dWithElapsedTime(msg: String, startTick: Long) {

        if (debugMode) {
            d(msg.replace("{elapsed}", (System.currentTimeMillis() - startTick).toString() + ""))
        }
    }

    fun d(msg: String, th: Throwable) {

        if (debugMode) {
            Log.d(C.LOG_NAME, msg, th)
        }

        MyLog.dumpToExternalLogFile(Log.DEBUG, msg)
        MyLog.dumpToExternalLogFile(Log.DEBUG, Log.getStackTraceString(th))
    }

    fun i(msg: String) {
        Log.i(C.LOG_NAME, msg)

        MyLog.dumpToExternalLogFile(Log.INFO, msg)
    }

    fun iWithElapsedTime(msg: String, startTick: Long) {

        i(msg.replace("{elapsed}", (System.currentTimeMillis() - startTick).toString() + ""))
    }

    fun i(msg: String, th: Throwable) {
        Log.i(C.LOG_NAME, msg, th)

        MyLog.dumpToExternalLogFile(Log.INFO, msg)
        MyLog.dumpToExternalLogFile(Log.INFO, Log.getStackTraceString(th))
    }

    fun w(msg: String) {
        Log.w(C.LOG_NAME, msg)

        MyLog.dumpToExternalLogFile(Log.WARN, msg)
    }

    fun wWithElapsedTime(msg: String, startTick: Long) {

        if (debugMode) {
            w(msg.replace("{elapsed}", (System.currentTimeMillis() - startTick).toString() + ""))
        }
    }

    fun w(msg: String, th: Throwable) {
        Log.w(C.LOG_NAME, msg, th)

        MyLog.dumpToExternalLogFile(Log.WARN, msg)
        MyLog.dumpToExternalLogFile(Log.WARN, Log.getStackTraceString(th))
    }

    fun w(th: Throwable) {
        Log.w(C.LOG_NAME, th.message, th)

        MyLog.dumpToExternalLogFile(Log.WARN, Log.getStackTraceString(th))
    }

    fun e(msg: String) {
        Log.e(C.LOG_NAME, msg)

        MyLog.dumpToExternalLogFile(Log.ERROR, msg)
    }

    fun e(msg: String, th: Throwable) {
        Log.e(C.LOG_NAME, msg, th)

        MyLog.dumpToExternalLogFile(Log.ERROR, msg)
        MyLog.dumpToExternalLogFile(Log.ERROR, Log.getStackTraceString(th))
    }

    fun e(th: Throwable) {
        Log.e(C.LOG_NAME, th.message, th)

        MyLog.dumpToExternalLogFile(Log.ERROR, Log.getStackTraceString(th))
    }

    /**
     *
     * @param error
     * @param msg
     */
    private fun dumpToExternalLogFile(error: Int, msg: String) {
        if (!debugMode) {
            return
        }

//      try {
//          // 保存先の決定
//          final File fout = TkUtil.getExternalStorageFile(C.EXTERNAL_FILE_DIRNAME, null);
//          if (fout == null) {
//              // メディア非マウントなど
//              return;
//          }
//          final String path = fout.getAbsolutePath() + "/" + C.EXTERNAL_LOG_FILENAME;
//
//          // ファイルに書き込む
//          final FileOutputStream out = new FileOutputStream(path, true);  // append
//
//          // 日付時刻
//          final SimpleDateFormat sdf = new SimpleDateFormat("[yyyy/MM/dd HH:mm:ss.SSS]");
//          out.write(sdf.format(new Date()).getBytes());
//
//          // エラーレベル
//          switch (error) {
//          case Log.INFO:  out.write("[INFO] ".getBytes());    break;
//          case Log.WARN:  out.write("[WARN] ".getBytes());    break;
//          case Log.ERROR: out.write("[ERROR] ".getBytes());   break;
//          case Log.DEBUG: out.write("[DEBUG] ".getBytes());   break;
//          default:
//              break;
//          }
//
//          // ログ本文
//          out.write(msg.getBytes("UTF-8"));
//          out.write("\n".getBytes());
//
//          out.flush();
//          out.close();
//
//      } catch (Exception e) {
////            Log.e(C.LOG_NAME, e.getMessage(), e);
//      }
    }

    /**
     * 关于有外部存储的日志文件的一定大小以上删除
     *
     * 通常让启动时，检查
     */
    fun deleteBigExternalLogFile() {
        if (!debugMode) {
            return
        }

//      try {
//          // 保存先の決定
//          final File fout = TkUtil.getExternalStorageFile(C.EXTERNAL_FILE_DIRNAME, null);
//          if (fout == null) {
//              // メディア非マウントなど
//              return;
//          }
//          final String path = fout.getAbsolutePath() + "/" + C.EXTERNAL_LOG_FILENAME;
//
//          // チェック＆削除
//          final File file = new File(path);
//          final int MAXFILESIZE = 2 * 1024 * 1024;    // [MB]
//
//          Log.i(C.LOG_NAME, "external log size check, size[" + file.length() + "], limit[" + MAXFILESIZE + "]");
//
//          if (file.length() > MAXFILESIZE) {
//              file.delete();
//          }
//
//      } catch (Exception e) {
//          Log.e(C.LOG_NAME, e.getMessage(), e);
//      }
    }

}
