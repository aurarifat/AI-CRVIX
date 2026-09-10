package com.example.devicecontrol

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat

class PermissionManager(private val context: Context) {

    fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasAudioPermission(): Boolean = hasRecordAudioPermission()

    fun hasNetworkPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.INTERNET
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun hasContactsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Checks if MayaX AI has the "Display over other apps" (Overlay / SYSTEM_ALERT_WINDOW)
     * permission granted to communicate with the phone and perform agent tasks on top of other apps.
     */
    fun hasDisplayOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    /**
     * Opens system settings for Display Over Other Apps permission.
     */
    fun requestDisplayOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun isAccessibilityServiceEnabled(): Boolean {
        return MayaAccessibilityService.isRunning()
    }

    fun hasShizukuPermission(): Boolean {
        return context.checkCallingOrSelfPermission(ShizukuManager.SHIZUKU_PERMISSION) == PackageManager.PERMISSION_GRANTED
    }

    fun allCorePermissionsGranted(): Boolean {
        return hasRecordAudioPermission() && hasNotificationPermission() && hasContactsPermission()
    }

    fun getMissingPermissions(): Array<String> {
        val missing = mutableListOf<String>()
        if (!hasRecordAudioPermission()) {
            missing.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission()) {
            missing.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (!hasContactsPermission()) {
            missing.add(Manifest.permission.READ_CONTACTS)
        }
        return missing.toTypedArray()
    }

    fun isActionConfirmedAlways(actionKey: String, alwaysAllowSet: Set<String>): Boolean {
        return alwaysAllowSet.contains(actionKey)
    }
}
