package com.example.devicecontrol

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/**
 * Manages the floating agentic overlay banner displayed at the top of the screen
 * during multi-step autonomous UI tasks (e.g. "Opening AdGuard", "Closing ads (3s left)",
 * "Turning protection ON").
 */
object AgentOverlayManager {

    private const val TAG = "AgentOverlayManager"

    private val mainHandler = Handler(Looper.getMainLooper())

    private var windowManager: WindowManager? = null
    private var overlayRootView: View? = null
    private var isShowing = false

    // UI View References
    private var titleTextView: TextView? = null
    private var statusTextView: TextView? = null
    private var countdownBadgeView: TextView? = null
    private var progressBar: ProgressBar? = null
    private var statusIconView: TextView? = null

    private var autoDismissRunnable: Runnable? = null

    /**
     * Checks whether we have permission to draw overlays (either via Settings.canDrawOverlays
     * or via active MayaAccessibilityService TYPE_ACCESSIBILITY_OVERLAY).
     */
    fun canShowOverlay(context: Context): Boolean {
        if (MayaAccessibilityService.isRunning()) return true
        return Settings.canDrawOverlays(context)
    }

    /**
     * Attempts to acquire overlay capability using Shizuku or requests permission.
     */
    suspend fun ensureOverlayPermission(context: Context): Boolean {
        if (canShowOverlay(context)) return true
        val bridge = ShizukuBridge.getInstance(context)
        if (bridge.status.value.isRunning && bridge.status.value.isPermissionGranted) {
            bridge.tryGrantOverlayPermission()
            if (Settings.canDrawOverlays(context)) return true
        }
        return canShowOverlay(context)
    }

    /**
     * Shows or updates the floating HUD overlay at the top of the screen.
     */
    fun show(
        context: Context,
        appName: String,
        initialStatus: String,
        initialCountdown: Int? = null
    ) {
        mainHandler.post {
            try {
                autoDismissRunnable?.let { mainHandler.removeCallbacks(it) }

                if (isShowing && overlayRootView != null) {
                    update(title = appName, status = initialStatus, countdownSeconds = initialCountdown)
                    return@post
                }

                val a11y = MayaAccessibilityService.instance
                val targetContext = a11y ?: context.applicationContext
                val wm = targetContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return@post
                windowManager = wm

                val view = createOverlayLayout(targetContext, appName, initialStatus, initialCountdown)
                overlayRootView = view

                val windowType = if (a11y != null) {
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }

                val dm = targetContext.resources.displayMetrics
                val cardWidth = (dm.widthPixels * 0.94f).coerceAtMost(dpToPx(targetContext, 420).toFloat()).toInt()

                val params = WindowManager.LayoutParams(
                    cardWidth,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    windowType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    y = dpToPx(targetContext, 40) // Clearance below status bar / notch
                }

                wm.addView(view, params)
                isShowing = true
                Log.d(TAG, "Agent HUD overlay shown successfully for $appName")

                // Slide down entrance animation
                view.alpha = 0f
                view.translationY = -dpToPx(targetContext, 30).toFloat()
                view.animate().alpha(1f).translationY(0f).setDuration(280L).start()

            } catch (t: Throwable) {
                Log.e(TAG, "Failed to display agent overlay: ${t.message}", t)
                isShowing = false
            }
        }
    }

    /**
     * Updates the status message, countdown timer, and progress bar smoothly.
     */
    fun update(
        title: String? = null,
        status: String,
        countdownSeconds: Int? = null,
        progressPercent: Int? = null,
        icon: String? = null
    ) {
        mainHandler.post {
            try {
                if (!isShowing || overlayRootView == null) return@post

                title?.let { titleTextView?.text = it }
                statusTextView?.text = status

                if (icon != null) {
                    statusIconView?.text = icon
                }

                if (countdownSeconds != null && countdownSeconds > 0) {
                    countdownBadgeView?.visibility = View.VISIBLE
                    countdownBadgeView?.text = "${countdownSeconds}s left"
                } else {
                    countdownBadgeView?.visibility = View.GONE
                }

                if (progressPercent != null) {
                    progressBar?.isIndeterminate = false
                    progressBar?.progress = progressPercent.coerceIn(0, 100)
                } else {
                    progressBar?.isIndeterminate = true
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to update overlay: ${t.message}")
            }
        }
    }

    /**
     * Completes the task with a success badge and auto-dismisses after a short delay.
     */
    fun complete(
        successMessage: String,
        autoDismissDelayMs: Long = 2600L
    ) {
        mainHandler.post {
            try {
                if (!isShowing || overlayRootView == null) return@post

                statusIconView?.text = "✅"
                statusTextView?.text = successMessage
                countdownBadgeView?.visibility = View.GONE
                progressBar?.isIndeterminate = false
                progressBar?.progress = 100

                autoDismissRunnable?.let { mainHandler.removeCallbacks(it) }
                val runnable = Runnable { dismiss() }
                autoDismissRunnable = runnable
                mainHandler.postDelayed(runnable, autoDismissDelayMs)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to mark overlay complete: ${t.message}")
                dismiss()
            }
        }
    }

    /**
     * Dismisses and removes the overlay with an exit animation.
     */
    fun dismiss() {
        mainHandler.post {
            try {
                autoDismissRunnable?.let { mainHandler.removeCallbacks(it) }
                autoDismissRunnable = null

                val view = overlayRootView
                val wm = windowManager
                if (view != null && wm != null && isShowing) {
                    view.animate()
                        .alpha(0f)
                        .translationY(-dpToPx(view.context, 25).toFloat())
                        .setDuration(220L)
                        .withEndAction {
                            try {
                                wm.removeView(view)
                            } catch (_: Exception) {}
                            overlayRootView = null
                            isShowing = false
                            windowManager = null
                            titleTextView = null
                            statusTextView = null
                            countdownBadgeView = null
                            progressBar = null
                            statusIconView = null
                            Log.d(TAG, "Agent HUD overlay dismissed")
                        }
                        .start()
                } else {
                    overlayRootView = null
                    isShowing = false
                    windowManager = null
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to dismiss overlay: ${t.message}")
                overlayRootView = null
                isShowing = false
            }
        }
    }

    private fun createOverlayLayout(
        context: Context,
        appName: String,
        initialStatus: String,
        initialCountdown: Int?
    ): View {
        val root = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 0, 0, 0)
        }

        // Sleek dark pill / card container with golden amber border
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#1C1B24")) // Dark obsidian violet
                cornerRadius = dpToPx(context, 20).toFloat()
                setStroke(dpToPx(context, 1), Color.parseColor("#E6C652")) // Golden Maya border
            }
            background = bg
            val padH = dpToPx(context, 16)
            val padV = dpToPx(context, 12)
            setPadding(padH, padV, padH, padV)
            elevation = dpToPx(context, 10).toFloat()
        }

        // Top Row: [Icon] [Titles + Status Column] [Countdown Badge] [Close X]
        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Icon / Emoji indicator
        val iconView = TextView(context).apply {
            text = "🤖"
            textSize = 22f
            setPadding(0, 0, dpToPx(context, 12), 0)
        }
        statusIconView = iconView
        topRow.addView(iconView)

        // Column for App & Subtitle
        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tagRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val agentTag = TextView(context).apply {
            text = "MAYAX AGENT"
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#FFD54F")) // Maya Yellow
            letterSpacing = 0.08f
        }
        tagRow.addView(agentTag)

        val titleTv = TextView(context).apply {
            text = " • $appName"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            maxLines = 1
        }
        titleTextView = titleTv
        tagRow.addView(titleTv)
        textCol.addView(tagRow)

        val statusTv = TextView(context).apply {
            text = initialStatus
            textSize = 13f
            typeface = Typeface.DEFAULT
            setTextColor(Color.parseColor("#E2E8F0"))
            maxLines = 2
            setPadding(0, dpToPx(context, 2), 0, 0)
        }
        statusTextView = statusTv
        textCol.addView(statusTv)

        topRow.addView(textCol)

        // Countdown Badge (e.g. "3s left")
        val countdownBadge = TextView(context).apply {
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#1C1B24"))
            val badgeBg = GradientDrawable().apply {
                setColor(Color.parseColor("#FFD54F"))
                cornerRadius = dpToPx(context, 10).toFloat()
            }
            background = badgeBg
            val bPadH = dpToPx(context, 8)
            val bPadV = dpToPx(context, 3)
            setPadding(bPadH, bPadV, bPadH, bPadV)
            if (initialCountdown != null && initialCountdown > 0) {
                text = "${initialCountdown}s left"
                visibility = View.VISIBLE
            } else {
                visibility = View.GONE
            }
        }
        countdownBadgeView = countdownBadge
        topRow.addView(countdownBadge)

        // Dismiss X Button
        val closeBtn = TextView(context).apply {
            text = "✕"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#94A3B8"))
            val pad = dpToPx(context, 8)
            setPadding(pad, pad, pad, pad)
            setOnClickListener {
                dismiss()
            }
        }
        topRow.addView(closeBtn)

        card.addView(topRow)

        // Progress Bar underneath
        val pb = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(context, 4)
            ).apply {
                topMargin = dpToPx(context, 8)
            }
            isIndeterminate = true
        }
        progressBar = pb
        card.addView(pb)

        root.addView(card)
        return root
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }
}
