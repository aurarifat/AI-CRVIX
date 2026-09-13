package com.example.devicecontrol

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
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

    // =========================================================================
    // Agentic UI Automation & Element Interaction Engine
    // =========================================================================

    /**
     * Traverses the active window node hierarchy and collects nodes matching predicate.
     */
    fun findNodesRecursively(
        root: AccessibilityNodeInfo? = rootInActiveWindow,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): List<AccessibilityNodeInfo> {
        val results = mutableListOf<AccessibilityNodeInfo>()
        if (root == null) return results
        fun traverse(node: AccessibilityNodeInfo) {
            if (predicate(node)) {
                results.add(node)
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                traverse(child)
            }
        }
        traverse(root)
        return results
    }

    /**
     * Clicks a target AccessibilityNodeInfo. If the node itself is not marked clickable,
     * traverses up to parent ancestors. If still not clickable, dispatches a hardware
     * touch gesture to the center coordinates of the node bounding box.
     */
    fun clickNode(node: AccessibilityNodeInfo): Boolean {
        try {
            if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                Log.d(TAG, "Clicked node directly: ${node.text ?: node.contentDescription}")
                return true
            }
            var parent = node.parent
            var depth = 0
            while (parent != null && depth < 5) {
                if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    Log.d(TAG, "Clicked parent node at depth $depth")
                    return true
                }
                parent = parent.parent
                depth++
            }
            // Fallback: Dispatch gesture tap to bounding box center
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (rect.width() > 0 && rect.height() > 0) {
                clickCoordinates(rect.centerX().toFloat(), rect.centerY().toFloat())
                Log.d(TAG, "Dispatched gesture tap to center: (${rect.centerX()}, ${rect.centerY()})")
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "clickNode error: ${e.message}")
        }
        return false
    }

    /**
     * Dispatches a tap gesture to exact screen coordinates using Android Accessibility Gestures.
     */
    fun clickCoordinates(x: Float, y: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path().apply {
                moveTo(x, y)
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
                .build()
            dispatchGesture(gesture, null, null)
        }
    }

    /**
     * Detects and dismisses popup advertisements, promotional dialogs, upgrade offers,
     * and close/cancel buttons on screen.
     */
    fun dismissPopupsOrAds(): Boolean {
        val root = rootInActiveWindow ?: return false
        val closeKeywords = listOf(
            "close", "dismiss", "skip", "no thanks", "later", "not now",
            "continue with free", "got it", "✕", "✖", "x", "remind me later",
            "cancel", "decline", "not interested", "maybe later", "skip ad", "skip video"
        )
        val idKeywords = listOf(
            "close", "dismiss", "cancel", "btn_close", "cross", "iv_close",
            "dialog_close", "skip", "ad_close", "close_button", "dismiss_button", "btn_skip"
        )

        val candidates = findNodesRecursively(root) { node ->
            val text = node.text?.toString()?.trim() ?: ""
            val desc = node.contentDescription?.toString()?.trim() ?: ""
            val viewId = node.viewIdResourceName?.lowercase() ?: ""

            val textMatches = closeKeywords.any { kw ->
                if (kw.length <= 2) text.equals(kw, ignoreCase = true) || desc.equals(kw, ignoreCase = true)
                else text.contains(kw, ignoreCase = true) || desc.contains(kw, ignoreCase = true)
            }
            val idMatches = idKeywords.any { viewId.contains(it) }

            textMatches || idMatches
        }

        for (cand in candidates) {
            if (clickNode(cand)) {
                Log.d(TAG, "Successfully dismissed popup/ad using element: ${cand.text ?: cand.contentDescription ?: cand.viewIdResourceName}")
                return true
            }
        }

        return false
    }

    /**
     * Finds and turns on the primary toggle, switch, or power protection button
     * in apps like AdGuard, VPNs, security tools, or system utilities.
     */
    fun toggleSwitchOrProtection(customKeywords: List<String> = emptyList()): Boolean {
        val root = rootInActiveWindow ?: return false

        // 1. Look for unselected/unchecked Switch, ToggleButton, or CompoundButton
        val switches = findNodesRecursively(root) { node ->
            val className = node.className?.toString() ?: ""
            className.contains("Switch", ignoreCase = true) ||
            className.contains("ToggleButton", ignoreCase = true) ||
            className.contains("CompoundButton", ignoreCase = true)
        }

        for (sw in switches) {
            if (!sw.isChecked) {
                if (clickNode(sw)) {
                    Log.d(TAG, "Toggled unchecked switch to ON")
                    return true
                }
            }
        }

        // 2. Look for keywords in text or contentDescription (e.g. Turn on, Protection, Enable, Start)
        val powerKeywords = if (customKeywords.isNotEmpty()) customKeywords else listOf(
            "turn on", "enable", "protection", "start", "connect",
            "activate", "switch on", "power", "shield", "turn protection on", "protect"
        )

        val powerNodes = findNodesRecursively(root) { node ->
            val text = node.text?.toString()?.trim() ?: ""
            val desc = node.contentDescription?.toString()?.trim() ?: ""
            val viewId = node.viewIdResourceName?.lowercase() ?: ""

            powerKeywords.any { kw ->
                text.contains(kw, ignoreCase = true) ||
                desc.contains(kw, ignoreCase = true) ||
                viewId.contains(kw.replace(" ", "_"))
            }
        }

        for (pNode in powerNodes) {
            if (clickNode(pNode)) {
                Log.d(TAG, "Activated power/protection button: ${pNode.text ?: pNode.contentDescription}")
                return true
            }
        }

        // 3. Fallback: if there are any switch widgets on screen, toggle the first one
        if (switches.isNotEmpty()) {
            if (clickNode(switches.first())) {
                return true
            }
        }

        return false
    }

    /**
     * Searches for a button or text on screen matching query and clicks it.
     */
    fun clickByText(query: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val candidates = findNodesRecursively(root) { node ->
            val text = node.text?.toString()?.trim() ?: ""
            val desc = node.contentDescription?.toString()?.trim() ?: ""
            text.contains(query, ignoreCase = true) || desc.contains(query, ignoreCase = true)
        }
        for (cand in candidates) {
            if (clickNode(cand)) return true
        }
        return false
    }

    /**
     * Collects visible text strings currently displayed in the active window.
     */
    fun getVisibleScreenText(): List<String> {
        val root = rootInActiveWindow ?: return emptyList()
        val texts = mutableListOf<String>()
        findNodesRecursively(root) { node ->
            val t = node.text?.toString()?.trim()
            val d = node.contentDescription?.toString()?.trim()
            if (!t.isNullOrBlank() && !texts.contains(t)) texts.add(t)
            if (!d.isNullOrBlank() && !texts.contains(d)) texts.add(d)
            false
        }
        return texts
    }

    // =========================================================================
    // Legacy Helpers & System Navigation
    // =========================================================================

    private fun attemptWhatsAppAutoSend() {
        val root = rootInActiveWindow ?: return
        try {
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
