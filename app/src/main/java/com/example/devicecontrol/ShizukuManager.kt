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
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        const val SHIZUKU_PERMISSION = "moe.shizuku.manager.permission.API_V23"
    }

    fun getStatus(): ShizukuStatus {
        val isInstalled = isShizukuInstalled()
        if (!isInstalled) {
            return ShizukuStatus(
                isInstalled = false,
                isRunning = false,
                isPermissionGranted = false,
                summary = "Shizuku is not installed"
            )
        }

        // Check if Shizuku manager service provider or permission is available
        val hasPermission = context.checkCallingOrSelfPermission(SHIZUKU_PERMISSION) == PackageManager.PERMISSION_GRANTED

        // Check if Shizuku binder/service can be reached
        val isRunning = checkShizukuRunning()

        return ShizukuStatus(
            isInstalled = true,
            isRunning = isRunning,
            isPermissionGranted = hasPermission,
            version = if (isRunning) 13 else 0,
            summary = when {
                !isRunning -> "Installed but not running. Start it via Wireless Debugging."
                !hasPermission -> "Running. Permission not yet granted."
                else -> "Connected and authorized."
            }
        )
    }

    private fun isShizukuInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun checkShizukuRunning(): Boolean {
        return try {
            // Check if provider exists and responds
            val uri = Uri.parse("content://moe.shizuku.privileged.api.provider")
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            val available = cursor != null
            cursor?.close()
            available
        } catch (_: Exception) {
            // Alternatively check package info flag
            false
        }
    }

    fun openShizukuApp(): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
        return if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } else {
            false
        }
    }

    fun openPlayStoreForShizuku() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$SHIZUKU_PACKAGE")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$SHIZUKU_PACKAGE")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        }
    }
}
