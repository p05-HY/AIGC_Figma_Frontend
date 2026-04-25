package com.example.blueheartv.control

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AdbAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        event.packageName?.toString()?.takeIf { it.isNotBlank() }?.let {
            latestPackageName = it
        }
        event.className?.toString()?.takeIf { it.isNotBlank() }?.let {
            latestActivityName = it
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }

    fun snapshotUiTree(): String? {
        val root = rootInActiveWindow ?: return null
        return try {
            buildString {
                append("<hierarchy>")
                appendNode(root, this)
                append("</hierarchy>")
            }
        } finally {
            root.recycle()
        }
    }

    private fun appendNode(node: AccessibilityNodeInfo, out: StringBuilder) {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        out.append("<node")
        out.append(" class=\"${xmlEscape(node.className)}\"")
        out.append(" package=\"${xmlEscape(node.packageName)}\"")
        out.append(" text=\"${xmlEscape(node.text)}\"")
        out.append(" contentDesc=\"${xmlEscape(node.contentDescription)}\"")
        out.append(" viewId=\"${xmlEscape(node.viewIdResourceName)}\"")
        out.append(" clickable=\"${node.isClickable}\"")
        out.append(" enabled=\"${node.isEnabled}\"")
        out.append(" focused=\"${node.isFocused}\"")
        out.append(" checked=\"${node.isChecked}\"")
        out.append(" scrollable=\"${node.isScrollable}\"")
        out.append(" bounds=\"[${bounds.left},${bounds.top}][${bounds.right},${bounds.bottom}]\"")
        out.append(">")
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            appendNode(child, out)
            child.recycle()
        }
        out.append("</node>")
    }

    companion object {
        @Volatile
        private var instance: AdbAccessibilityService? = null

        @Volatile
        var latestPackageName: String? = null
            private set

        @Volatile
        var latestActivityName: String? = null
            private set

        fun dumpUiTree(): String? = instance?.snapshotUiTree()

        fun currentPackageName(): String? = latestPackageName

        fun currentActivityName(): String? = latestActivityName

        private fun xmlEscape(value: CharSequence?): String {
            if (value == null) return ""
            return value.toString()
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\n", "&#10;")
        }
    }
}
