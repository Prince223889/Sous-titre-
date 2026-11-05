package ml.sidibe.voixsoustitres

import android.os.Handler
import android.os.Looper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Petit journal affiché dans l'application. */
object AppLog {
    private val lines = ArrayDeque<String>()
    private val main = Handler(Looper.getMainLooper())
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.FRANCE)

    @Volatile
    var listener: (() -> Unit)? = null

    @Synchronized
    fun add(s: String) {
        lines.addFirst("${fmt.format(Date())}  $s")
        while (lines.size > 60) lines.removeLast()
        val l = listener
        if (l != null) main.post { l() }
    }

    @Synchronized
    fun text(): String = if (lines.isEmpty()) "(vide pour l'instant)" else lines.joinToString("\n")
}
