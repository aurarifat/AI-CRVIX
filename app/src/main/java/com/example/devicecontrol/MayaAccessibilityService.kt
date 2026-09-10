package com.example.devicecontrol

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class MayaAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "MayaAccessibility"
        @Volatile
        var instance: MayaAccessibilityService? = null
            private set

        fun isRunning(): Boolean = instance != null

        // Pending automation state
        @Volatile
        var pendingSendPackage: String? = null
        @Volatile
        var pendingSendExpiresAt: Long = 0L

        fun queueAutoSend(packageName: String, timeoutMs: Long = 12000L) {
            pendingSendPackage = packageName
            pendingSendExpiresAt = System.currentTimeMillis() + timeoutMs
        }

        fun clearPendingAutomation() {
            pendingSendPackage = null
            pendingSendExpiresAt = 0L
        }

        fun openAccessibilitySettings(context: Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "MayaAccessibilityService connected and active")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d(TAG, "MayaAccessibilityService disconnected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val currentPackage = event.packageName?.toString() ?: return

        // Check if there's a pending auto-send for this package (e.g. WhatsApp)
        val targetPkg = pendingSendPackage
        val expiresAt = pendingSendExpiresAt

        if (targetPkg != null && System.currentTimeMillis() < expiresAt) {
            if (currentPackage.startsWith("com.whatsapp") || currentPackage == targetPkg) {
                attemptWhatsAppAutoSend()
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "MayaAccessibilityService interrupted")
    }

    private fun attemptWhatsAppAutoSend() {
        val root = rootInActiveWindow ?: return
        try {
            // WhatsApp Send button usually has contentDescription = "Send" or viewId containing "send"
            val sendNodes = mutableListOf<AccessibilityNodeInfo>()

            findNodesMatchingSend(root, sendNodes)

            for (node in sendNodes) {
                if (node.isClickable && node.isEnabled) {
                    mainHandler.postDelayed({
                        try {
                            val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            if (clicked) {
                                clearPendingAutomation()
                                Log.d(TAG, "Successfully auto-clicked send button in WhatsApp!")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error clicking send node: ${e.message}")
                        }
                    }, 600)
                    return
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "attemptWhatsAppAutoSend error: ${e.message}")
        }
    }

    private fun findNodesMatchingSend(node: AccessibilityNodeInfo, results: MutableList<AccessibilityNodeInfo>) {
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val text = node.text?.toString()?.lowercase() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""

        val isSend = desc == "send" || desc == "পাঠান" ||
                text == "send" || text == "পাঠান" ||
                viewId.endsWith(":id/send") || viewId.endsWith(":id/send_btn") ||
                viewId.contains("send_button")

        if (isSend) {
            results.add(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findNodesMatchingSend(child, results)
        }
    }

    fun performBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun performHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun performRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun performNotifications(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    fun performQuickSettings(): Boolean = performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
}
