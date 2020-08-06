package com.jarvis.performance;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;

import com.jarvis.performance.cpu.IUsageUpdateCallback;
import com.jarvis.performance.cpu.IUsageUpdateService;
import com.jarvis.performance.cpu.MyUtil;

import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;


public class FloatingCpuInfoService extends Service {

    private static final String KEY_START_MONITOR_STICKY = "START_INFO";
    private static final String KEY_WARN_CONFIG = "WARN_CONFIG";

    private WindowManager windowManager;
    private WindowManager.LayoutParams layoutParams;

    private Button button;

    public static boolean isStarted = false;

    private IUsageUpdateService mServiceIf = null;
    private List<Integer> mTempTotalCpuUsage = new ArrayList<>();

    private WarnConfig mWarnConfig;

    public static class WarnConfig implements Serializable {
        // 发生警告时的平均使用率
        int warnAverageUsage;

        // 计算平均使用率使用的时间
        int seconds;

        public WarnConfig(int warnAverageUsage, int seconds) {
            this.warnAverageUsage = warnAverageUsage;
            this.seconds = seconds;
        }
    }


    private IUsageUpdateCallback.Stub mCallback = new IUsageUpdateCallback.Stub(){

        @Override
        public void updateUsage(int[] cpuUsages, int[] freqs, int[] minFreqs, int[] maxFreqs) throws RemoteException {
            showCpuUsages(cpuUsages,freqs,minFreqs,maxFreqs);
        }
    };

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mServiceIf = IUsageUpdateService.Stub.asInterface(service);

            try {
                mServiceIf.registerCallback(mCallback);
            } catch (RemoteException e) {
                e.fillInStackTrace();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mServiceIf = null;
        }
    };


    public static void startMonitorCpuinfo(Context context) {
        Intent intent = new Intent(context, FloatingCpuInfoService.class);
        intent.putExtra(KEY_START_MONITOR_STICKY, true);
        context.startService(intent);
    }

    public static void startMonitorCpuinfoOnlyUsageAlert(Context context, WarnConfig warnConfig) {
        if (warnConfig == null) {
            return;
        }

        Intent intent = new Intent(context, FloatingCpuInfoService.class);
        intent.putExtra(KEY_WARN_CONFIG, warnConfig);
        intent.putExtra(KEY_START_MONITOR_STICKY, true);
        context.startService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        layoutParams = createFloatWindowParam(0, WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT);
        layoutParams.x = 0; //放在左边

        WindowManager.LayoutParams logLayoutParam = createFloatWindowParam(Resources.getSystem().getDisplayMetrics().heightPixels, Resources.getSystem().getDisplayMetrics().widthPixels/3, Resources.getSystem().getDisplayMetrics().heightPixels);

        // start
        showMonitorFloatingWindow(false);
        //启动获取cpu service
        Intent intent = new Intent("com.jarvis.performance.cpu.IUsageUpdateService");
        intent.setPackage(getPackageName());
        bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
    }

    WindowManager.LayoutParams createFloatWindowParam(int y, int width, int height) {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            params.type = WindowManager.LayoutParams.TYPE_PHONE;
        }
        params.format = PixelFormat.RGBA_8888;
        params.gravity = Gravity.LEFT|Gravity.TOP;
        params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        params.width = width;
        params.height = height;
        params.x = Resources.getSystem().getDisplayMetrics().widthPixels - params.width;
        params.y = y;
        return params;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.e("进入服务2", "进入服务2");

        return null;
    }

    @Override
    public void onDestroy() {
        if (button != null) {
            windowManager.removeView(button);
        }

        //解除绑定
        if (mServiceIf != null) {
            try {
                mServiceIf.unregisterCallback(mCallback);
            } catch (RemoteException e) {
                e.fillInStackTrace();
            }

        }
        if (mServiceConnection != null) {
            unbindService(mServiceConnection);
        }
        super.onDestroy();

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        boolean shouldMonitor = intent.getBooleanExtra(KEY_START_MONITOR_STICKY, false);

        if (shouldMonitor) {
            mWarnConfig = (WarnConfig) intent.getSerializableExtra(KEY_WARN_CONFIG);
            showMonitorFloatingWindow(true);
        }
        return super.onStartCommand(intent, flags, startId);
    }


    private void showMonitorFloatingWindow(final boolean show) {
        if (button == null) {
            button = new Button(getApplicationContext());
            button.setText("暂时未获取到系统信息");
            button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            button.setTextColor(Color.WHITE);
            button.setBackgroundColor(Color.BLACK);
            button.setAlpha(.5f);
            int padding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
            button.setPadding(padding,padding,padding,padding);
            button.setGravity(Gravity.LEFT);
            button.setOnTouchListener(new FloatingOnTouchListener());

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {//判断系统版本
                if (Settings.canDrawOverlays(this)) {
                    windowManager.addView(button, layoutParams);
                }
            } else {
                windowManager.addView(button, layoutParams);
            }
        }

        button.post(new Runnable() {
            @Override
            public void run() {
                button.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
            }
        });
    }

    private void  showCpuUsages(int[] cpuUsages, int[] freqs,int[] minFreqs,int[] maxFreqs) {

        final StringBuilder ssb = new StringBuilder();

        for (int i = 0;i < cpuUsages.length;i++) {

            int cpuUsage = 0;
            if (cpuUsages != null) {
                cpuUsage = cpuUsages[i];
            }

            String name = "";
            if (i ==0) {
                name = "cpu";
            } else {
                name = "cpu"+(i-1);
            }
            ssb.append(name + "  " + cpuUsage + "%  ");

            // 频率
            if (i>0) {
                String freqText = MyUtil.INSTANCE.formatFreq(freqs[i-1]);
                ssb.append(freqText);
            }

            if (i < cpuUsages.length) {
                ssb.append("\n");
            }
        }

        if (mWarnConfig != null) {
            //统计CPU使用率是否连续超过设定的值
            if (mTempTotalCpuUsage.size() > mWarnConfig.seconds) {
                mTempTotalCpuUsage.remove(0);
            }
            mTempTotalCpuUsage.add(cpuUsages[0]);
            int tempTotal = 0;
            for (int perSecUsage : mTempTotalCpuUsage) {
                tempTotal+=perSecUsage;
            }
            if (tempTotal/mTempTotalCpuUsage.size() >= mWarnConfig.warnAverageUsage) {
                ssb.append(mWarnConfig.seconds + "秒内平均使用率:" + tempTotal/mTempTotalCpuUsage.size() + "% !");
                showMonitorFloatingWindow(true);
            } else {
                showMonitorFloatingWindow(false);
                mTempTotalCpuUsage.clear();
            }

        }

        ssb.append("\n");

        try {
            int M = 1024*1024;
            Runtime r = Runtime.getRuntime();
            ssb.append("最大可用内存:" + r.maxMemory() / M + " MB").append("\n");
            //ssb.append("当前可用内存:" + r.totalMemory()/ M + " MB").append("\n");
            //ssb.append("当前空闲内存:" + r.freeMemory() / M + " MB").append("\n");
            ssb.append("当前堆内存使用:" + (r.totalMemory() - r.freeMemory()) / M + " MB").append("\n");

        } catch (Exception e) {
            // ignore
            e.printStackTrace();
        }

        try {
            String procMemory = MemoryUtil.INSTANCE.getProcessMemeryInfo(getApplicationContext()) + " MB";
            ssb.append("实际内存使用: ").append(procMemory).append("\n");
        } catch (Exception e) {
            // ignore
            e.printStackTrace();
        }

        try {
            String memoryRate = MemoryUtil.INSTANCE.getUsedPercentValue(getApplicationContext());
            ssb.append("系统总内存占用: ").append(memoryRate);
        } catch (Exception e) {
            // ignore
            e.printStackTrace();
        }

        if (button != null) {
            button.post(new Runnable() {
                @Override
                public void run() {
                    button.setText(ssb.toString());
                }
            });
        }

    };

    private class FloatingOnTouchListener implements View.OnTouchListener {
        private int x;
        private int y;

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    x = (int) event.getRawX();
                    y = (int) event.getRawY();
                    break;
                case MotionEvent.ACTION_MOVE:
                    int nowX = (int) event.getRawX();
                    int nowY = (int) event.getRawY();
                    int movedX = nowX - x;
                    int movedY = nowY - y;
                    x = nowX;
                    y = nowY;
                    layoutParams.x = layoutParams.x + movedX;
                    layoutParams.y = layoutParams.y + movedY;
                    windowManager.updateViewLayout(view, layoutParams);
                    break;
                default:
                    break;
            }
            return false;
        }
    }
}

