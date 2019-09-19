package com.tbruyelle.rxpermissions2

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.provider.Settings.canDrawOverlays

enum class SystemPermission(val permissionName: String, val action: String, val requestCode: Int, val enabledChecker: (context: Context) -> Boolean) {

        @SuppressLint("InlinedApi")
        DRAW_OVERLAYS("android.permission.SYSTEM_ALERT_WINDOW", Settings.ACTION_MANAGE_OVERLAY_PERMISSION, 11, {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                true
            } else {
                canDrawOverlays(it)
            }
        })
    }