package com.example.devicecontrol

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

data class ShizukuStatus(
    val isInstalled: Boolean,
    val isRunning: Boolean,
    val isPermissionGranted: Boolean,
    val version: Int = 0,
    val summary: String = ""
)

class ShizukuManager(private val context: Context) {

    companion object {
        const val SHIZUKU_PACKAGE = ShizukuBridge.SHIZUKU_PACKAGE
        const val SHIZUKU_PERMISSION = "moe.shizuku.manager.permission.API_V23"
    }

    val bridge: ShizukuBridge = ShizukuBridge.getInstance(context)

    init {
        bridge.initialize()
    }

    fun getStatus(): ShizukuStatus {
        val bridgeStatus = bridge.refreshStatus()
        return ShizukuStatus(
            isInstalled = bridgeStatus.isInstalled,
            isRunning = bridgeStatus.isRunning,
            isPermissionGranted = bridgeStatus.isPermissionGranted,
            version = bridgeStatus.version,
            summary = bridgeStatus.summary
        )
    }

    fun requestPermission(): Boolean {
        return bridge.requestPermission()
    }

    fun openShizukuApp(): Boolean {
        return bridge.openShizukuApp()
    }

    fun openWirelessDebugging(): Boolean {
        return bridge.openWirelessDebuggingSettings()
    }

    fun openPlayStoreForShizuku() {
        bridge.openPlayStoreForShizuku()
    }
}
