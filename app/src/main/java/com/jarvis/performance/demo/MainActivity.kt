package com.jarvis.performance.demo

import android.Manifest
import android.content.Intent
import android.content.pm.PermissionInfo
import android.net.Uri
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import com.jarvis.performance.PerformanceMonitorService

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),1000)

            if (!Settings.canDrawOverlays(this)) {
                startActivityForResult(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())),0)
            } else {
                PerformanceMonitorService.startMonitorCpuinfo(this)
            }
        } else {
            PerformanceMonitorService.startMonitorCpuinfo(this)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 0) {
            PerformanceMonitorService.startMonitorCpuinfo(this)
        }
    }
}
