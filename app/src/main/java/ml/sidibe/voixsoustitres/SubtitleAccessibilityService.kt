package ml.sidibe.voixsoustitres

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/** Mode 1 : lit le texte des sous-titres exposé par les applications (comme TalkBack). */
class SubtitleAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var running = false

        // Mots qui désignent clairement une zone de sous-titres
        private val STRONG = listOf(
            "subtitles", "subtitle_view", "subtitleview", "caption", "closedcaption",
            "cue", "srt", "sous_titre", "soustitre", "exo_subtitles"
        )
        private val IGNORED_PACKAGES = listOf("com.android.systemui", "inputmethod", "launcher", "keyboard")
    }

    private val handler = Handler(Looper.getMainLooper())
    private var scheduled = false
    private val seen = HashSet<String>()

    override fun onServiceConnected() {
        Prefs.init(this)
        Speaker.init(this)
        running = true
        AppLog.add("Mode accessibilité connecté")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !Prefs.enabled) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || IGNORED_PACKAGES.any { pkg.contains(it) }) return
        if (!scheduled) {
            scheduled = true
            handler.postDelayed({
                scheduled = false
                try { scan() } catch (_: Exception) {}
            }, 300)
        }
    }

    private fun scan() {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        try {
            for (w in windows) {
                if (w.type == AccessibilityWindowInfo.TYPE_APPLICATION) w.root?.let { roots.add(it) }
            }
        } catch (_: Exception) {}
        if (roots.isEmpty()) rootInActiveWindow?.let { roots.add(it) }

        val found = mutableListOf<String>()
        for (r in roots) {
            val pkg = r.packageName?.toString() ?: ""
            if (pkg == packageName || IGNORED_PACKAGES.any { pkg.contains(it) }) continue
            walk(r, pkg, found, 0)
        }
        if (found.isEmpty()) SubtitleProcessor.onEmpty()
        else SubtitleProcessor.onText(found.distinct().joinToString("\n"), "Accessibilité", false)
    }

    private fun walk(n: AccessibilityNodeInfo, pkg: String, out: MutableList<String>, depth: Int) {
        if (depth > 45) return
        val id = n.viewIdResourceName?.lowercase() ?: ""
        val cls = n.className?.toString()?.lowercase() ?: ""
        if (n.isVisibleToUser && isSubtitleNode(n, id, cls)) {
            val t = collectText(n, 0).trim()
            if (t.isNotEmpty()) {
                out.add(t)
                if (seen.add("$pkg|$id|$cls")) AppLog.add("Sous-titres repérés dans $pkg (${id.ifEmpty { cls }})")
                return
            }
        }
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            walk(c, pkg, out, depth + 1)
        }
    }

    private fun isSubtitleNode(n: AccessibilityNodeInfo, id: String, cls: String): Boolean {
        if (STRONG.any { id.contains(it) || cls.contains(it) }) return true
        // « subtitle » seul est parfois un simple sous-titre d'interface :
        // on ne l'accepte que dans le bas de l'écran.
        if (id.contains("subtitle")) {
            val r = Rect()
            n.getBoundsInScreen(r)
            val h = resources.displayMetrics.heightPixels
            return r.centerY() > h * 0.55
        }
        return false
    }

    private fun collectText(n: AccessibilityNodeInfo, depth: Int): String {
        if (depth > 6) return ""
        val parts = mutableListOf<String>()
        val t = n.text?.toString()
        if (!t.isNullOrBlank()) parts.add(t)
        else n.contentDescription?.toString()?.let { if (it.isNotBlank()) parts.add(it) }
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            val s = collectText(c, depth + 1)
            if (s.isNotBlank()) parts.add(s)
        }
        return parts.distinct().joinToString("\n")
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }
}
