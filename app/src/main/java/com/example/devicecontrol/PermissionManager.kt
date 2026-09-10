package com.example.devicecontrol

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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

    fun isAccessibilityServiceEnabled(): Boolean {
        return MayaAccessibilityService.isRunning()
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
