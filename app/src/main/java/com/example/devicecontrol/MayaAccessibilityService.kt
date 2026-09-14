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

    data class ScreenNodeItem(
        val text: String = "",
        val contentDescription: String = "",
        val viewId: String = "",
        val className: String = "",
        val bounds: Rect = Rect(),
        val isClickable: Boolean = false,
        val isCheckable: Boolean = false,
        val isChecked: Boolean = false,
        val isEnabled: Boolean = true
    )

    data class ScreenInspection(
        val packageName: String,
        val allTexts: List<String>,
        val interactiveNodes: List<ScreenNodeItem>,
        val fullHierarchySummary: String
    )

    /**
     * Traverses and inspects the entire active screen hierarchy.
     */
    fun inspectEntireScreen(): ScreenInspection {
        val root = rootInActiveWindow
        val pkg = root?.packageName?.toString() ?: ""
        val texts = mutableListOf<String>()
        val interactive = mutableListOf<ScreenNodeItem>()

        if (root != null) {
            findNodesRecursively(root) { node ->
                val t = node.text?.toString()?.trim() ?: ""
                val d = node.contentDescription?.toString()?.trim() ?: ""
                val id = node.viewIdResourceName ?: ""
                val cls = node.className?.toString() ?: ""
                val rect = Rect()
                node.getBoundsInScreen(rect)

                if (t.isNotBlank() && !texts.contains(t)) texts.add(t)
                if (d.isNotBlank() && !texts.contains(d)) texts.add(d)

                if (node.isClickable || node.isCheckable || t.isNotBlank() || d.isNotBlank() || id.isNotBlank()) {
                    interactive.add(
                        ScreenNodeItem(
                            text = t,
                            contentDescription = d,
                            viewId = id,
                            className = cls,
                            bounds = rect,
                            isClickable = node.isClickable,
                            isCheckable = node.isCheckable,
                            isChecked = node.isChecked,
                            isEnabled = node.isEnabled
                        )
                    )
                }
                false
            }
        }

        val summary = buildString {
            appendLine("App Package: $pkg")
            appendLine("Visible Texts (${texts.size}): ${texts.joinToString(" | ")}")
            appendLine("Interactive Elements: ${interactive.size}")
        }

        return ScreenInspection(pkg, texts, interactive, summary)
    }

    /**
     * Returns a clear, human-readable summary of all visible text on the active device screen.
     */
    fun readVisibleTextSummary(): String {
        val root = rootInActiveWindow ?: return "Screen is currently not accessible."
        val texts = mutableListOf<String>()
        findNodesRecursively(root) { node ->
            val t = node.text?.toString()?.trim()
            val d = node.contentDescription?.toString()?.trim()
            if (!t.isNullOrBlank() && !texts.contains(t)) texts.add(t)
            if (!d.isNullOrBlank() && !texts.contains(d)) texts.add(d)
            false
        }
        return if (texts.isEmpty()) {
            "No readable text found on the active screen."
        } else {
            texts.joinToString("\n") { "• $it" }
        }
    }

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
            try {
                if (predicate(node)) {
                    results.add(node)
                }
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
                    traverse(child)
                }
            } catch (e: Exception) {
                Log.w(TAG, "traverse error: ${e.message}")
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
    fun clickCoordinates(x: Float, y: Float): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path().apply {
                moveTo(x, y)
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
                .build()
            return dispatchGesture(gesture, null, null)
        }
        return false
    }

    /**
     * Dispatches a tap to the center of the active device display.
     */
    fun tapCenterScreen(): Boolean {
        val displayMetrics = resources.displayMetrics
        val centerX = displayMetrics.widthPixels / 2f
        val centerY = displayMetrics.heightPixels * 0.48f
        return clickCoordinates(centerX, centerY)
    }

    /**
     * Detects and dismisses popup advertisements, promotional dialogs, upgrade offers,
     * and close/cancel buttons on screen.
     * Crucially: Returns false without performing any exit or back actions if no popups exist.
     */
    fun dismissPopupsOrAds(): Boolean {
        val root = rootInActiveWindow ?: return false
        val closeKeywords = listOf(
            "close", "dismiss", "skip", "no thanks", "later", "not now",
            "continue with free", "got it", "✕", "✖", "x", "remind me later",
            "cancel", "decline", "not interested", "maybe later", "skip ad", "skip video",
            "free version", "continue free", "close dialog", "agree & continue"
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

        // 2. Look for keywords in text, contentDescription, or viewId
        val powerKeywords = if (customKeywords.isNotEmpty()) customKeywords else listOf(
            "turn on", "enable", "protection", "protect", "start", "connect",
            "activate", "switch on", "power", "shield", "turn protection on",
            "paused", "disabled", "off", "tap to enable", "resume"
        )
        val powerIdKeywords = listOf(
            "main_switch", "switch_protection", "protection_switch", "power_button",
            "btn_power", "btn_protect", "shield", "switch", "status_switch",
            "circle_switch", "protection_toggle", "main_protection"
        )

        val powerNodes = findNodesRecursively(root) { node ->
            val text = node.text?.toString()?.trim() ?: ""
            val desc = node.contentDescription?.toString()?.trim() ?: ""
            val viewId = node.viewIdResourceName?.lowercase() ?: ""

            val textMatch = powerKeywords.any { kw ->
                text.contains(kw, ignoreCase = true) || desc.contains(kw, ignoreCase = true)
            }
            val idMatch = powerIdKeywords.any { viewId.contains(it) }

            textMatch || idMatch
        }

        for (pNode in powerNodes) {
            if (clickNode(pNode)) {
                Log.d(TAG, "Activated power/protection button: ${pNode.text ?: pNode.contentDescription ?: pNode.viewIdResourceName}")
                return true
            }
        }

        // 3. Special handling for AdGuard / VPN style apps:
        val pkgName = root.packageName?.toString()?.lowercase() ?: ""
        val screenTexts = getVisibleScreenText()
        val isAdGuardOrSecurityApp = pkgName.contains("adguard") || screenTexts.any { it.contains("adguard", ignoreCase = true) }

        if (isAdGuardOrSecurityApp) {
            // Find any node in the upper-center of the screen representing the shield or status
            val displayMetrics = resources.displayMetrics
            val midY = displayMetrics.heightPixels * 0.48f
            val midX = displayMetrics.widthPixels / 2f

            // Look for any clickable view within 150px of center
            val centerCandidates = findNodesRecursively(root) { node ->
                val rect = Rect()
                node.getBoundsInScreen(rect)
                rect.contains(midX.toInt(), midY.toInt()) && (node.isClickable || node.childCount == 0)
            }
            if (centerCandidates.isNotEmpty()) {
                val candidate = centerCandidates.last()
                if (clickNode(candidate)) {
                    Log.d(TAG, "Clicked AdGuard center shield view: ${candidate.className}")
                    return true
                }
            }

            // Fallback: Gesture tap to center shield coordinates
            clickCoordinates(midX, midY)
            Log.d(TAG, "Dispatched gesture tap to AdGuard center shield coordinates ($midX, $midY)")
            return true
        }

        // 4. Fallback: if there are any switch widgets on screen, toggle the first one
        if (switches.isNotEmpty()) {
            if (clickNode(switches.first())) {
                return true
            }
        }

        // 5. Final fallback: tap center of screen
        return tapCenterScreen()
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
