package com.nova.ai.control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

/**
 * Lets NOVA press Back/Home, scroll, tap visible text and read the screen.
 * Only works after the user switches it on in Android Settings, and refuses to act on
 * payment, banking, password and app-installer screens.
 */
class NovaAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    fun foregroundPackage(): String? = rootInActiveWindow?.packageName?.toString()

    /** Returns a spoken refusal if NOVA must not touch the current screen, else null. */
    fun guard(): String? {
        val pkg = foregroundPackage()?.lowercase(Locale.ROOT) ?: return "Mujhe screen dikh nahi rahi."
        if (BLOCKED_WORDS.any { pkg.contains(it) }) {
            return "Is app par main kaam nahi karti kyunki ye payment, bank, password ya install se juda hai."
        }
        return null
    }

    fun back(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun home(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun recents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun notifications(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    fun readScreen(): String {
        val root = rootInActiveWindow ?: return ""
        val sb = StringBuilder()
        fun walk(n: AccessibilityNodeInfo?, depth: Int) {
            if (n == null || depth > 40 || sb.length > 1500) return
            if (n.isPassword) return
            if (n.isVisibleToUser) {
                val t = n.text ?: n.contentDescription
                if (!t.isNullOrBlank()) sb.append(t).append(". ")
            }
            for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1)
        }
        walk(root, 0)
        return sb.toString().trim()
    }

    fun clickByText(target: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = root.findAccessibilityNodeInfosByText(target)
            ?.firstOrNull { it.isVisibleToUser && !it.isPassword } ?: return false
        return clickNode(node)
    }

    /** Clicks the n-th (1-based) visible node whose content description contains [part], top to bottom. */
    fun clickNthByDescription(part: String, n: Int): Boolean {
        val root = rootInActiveWindow ?: return false
        val found = ArrayList<AccessibilityNodeInfo>()
        fun walk(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > 40) return
            val d = node.contentDescription?.toString()?.lowercase(Locale.ROOT)
            if (d != null && d.contains(part) && node.isVisibleToUser) found += node
            for (i in 0 until node.childCount) walk(node.getChild(i), depth + 1)
        }
        walk(root, 0)
        val sorted = found.sortedBy { Rect().also { r -> it.getBoundsInScreen(r) }.top }
        val node = sorted.getOrNull(n - 1) ?: return false
        return clickNode(node)
    }

    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        var cur: AccessibilityNodeInfo? = node
        var hops = 0
        while (cur != null && hops < 6) {
            if (cur.isClickable && cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            cur = cur.parent
            hops++
        }
        val r = Rect()
        node.getBoundsInScreen(r)
        return tap(r.centerX().toFloat(), r.centerY().toFloat())
    }

    fun scroll(down: Boolean): Boolean {
        val root = rootInActiveWindow
        val scrollable = root?.let { findScrollable(it, 0) }
        val action = if (down) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        if (scrollable != null && scrollable.performAction(action)) return true
        val m = resources.displayMetrics
        val x = m.widthPixels / 2f
        val from = m.heightPixels * (if (down) 0.70f else 0.30f)
        val to = m.heightPixels * (if (down) 0.30f else 0.70f)
        val path = Path().apply { moveTo(x, from); lineTo(x, to) }
        return dispatchGesture(
            GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 300)).build(),
            null, null
        )
    }

    private fun findScrollable(n: AccessibilityNodeInfo, depth: Int): AccessibilityNodeInfo? {
        if (depth > 40) return null
        if (n.isScrollable && n.isVisibleToUser) return n
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            findScrollable(c, depth + 1)?.let { return it }
        }
        return null
    }

    private fun tap(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        return dispatchGesture(
            GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 60)).build(),
            null, null
        )
    }

    companion object {
        @Volatile
        var instance: NovaAccessibilityService? = null

        private val BLOCKED_WORDS = listOf(
            "bank", "paytm", "phonepe", "paisa", "upi", "wallet", "payment", "pay.",
            "authenticator", "password", "keepass", "bitwarden", "lastpass",
            "packageinstaller", "com.android.vending"
        )
    }
}
