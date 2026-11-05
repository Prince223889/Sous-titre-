package ml.sidibe.voixsoustitres

/**
 * Reçoit le texte lu à l'écran, évite les répétitions, repère les personnages
 * et envoie chaque réplique à la voix qui convient.
 */
object SubtitleProcessor {
    private class Segment(var text: String, val dashed: Boolean)

    private var lastSpoken = ""
    private var candidate = ""
    private var candidateCount = 0
    private var emptyCount = 0
    private var currentVoice = 0
    private val characters = HashMap<String, Int>()

    // « JEAN : Bonjour »  ou  « [MARIE] Salut »
    private val nameRegex = Regex("^([A-ZÀ-ÝŒ][A-ZÀ-ÝŒ'’ .-]{1,20})\\s*:\\s*(.+)$")
    private val bracketNameRegex = Regex("^[\\[(]([^\\])]{1,25})[\\])]\\s*:?\\s*(.+)$")
    private val soundRegex = Regex("[\\[(][^\\])]*[\\])]|[♪♫#]")
    private val dashStart = Regex("^[-–—]\\s*")
    private val midDash = Regex("\\s[-–—]\\s")
    private val spaces = Regex("[ \\t]+")
    private val tags = Regex("<[^>]+>|\\{[^}]*\\}")

    @Synchronized
    fun onEmpty() {
        candidate = ""
        candidateCount = 0
        emptyCount++
        if (emptyCount >= 3) lastSpoken = ""
    }

    /**
     * @param needStable true pour la capture d'écran : on attend que le même texte
     *                   soit vu 2 fois de suite (évite les erreurs de lecture).
     */
    @Synchronized
    fun onText(raw: String, source: String, needStable: Boolean) {
        if (!Prefs.enabled) return
        val text = clean(raw)
        if (text.count { it.isLetter() } < 2) {
            onEmpty()
            return
        }
        emptyCount = 0
        if (needStable) {
            if (similar(text, candidate)) candidateCount++ else {
                candidate = text
                candidateCount = 1
            }
            if (candidateCount < 2) return
        }
        if (similar(text, lastSpoken)) return
        val part = newPart(lastSpoken, text)
        lastSpoken = text
        if (part.count { it.isLetter() } < 2) return
        AppLog.add("[$source] ${part.replace('\n', ' ')}")
        speakSegments(part)
    }

    private fun clean(raw: String): String =
        raw.replace(tags, " ")
            .split('\n')
            .map { it.replace(spaces, " ").trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")

    private fun speakSegments(text: String) {
        // 1) Découper en répliques : chaque tiret « - » = un nouveau personnage qui parle
        val segs = mutableListOf<Segment>()
        for (line in text.split('\n')) {
            line.split(midDash).forEachIndexed { i, piece ->
                val dashed = i > 0 || dashStart.containsMatchIn(piece.trim())
                val p = piece.trim().replace(dashStart, "").trim()
                if (p.isEmpty()) return@forEachIndexed
                if (dashed || segs.isEmpty()) segs.add(Segment(p, dashed))
                else segs.last().text += " $p"
            }
        }
        // 2) Choisir une voix pour chaque réplique
        for (s in segs) {
            var t = s.text
            var name: String? = null
            val m1 = nameRegex.find(t)
            if (m1 != null) {
                name = m1.groupValues[1].trim()
                t = m1.groupValues[2]
            } else {
                val m2 = bracketNameRegex.find(t)
                if (m2 != null) {
                    val n = m2.groupValues[1].trim()
                    if (n.any { it.isLetter() } && n == n.uppercase()) {
                        name = n
                        t = m2.groupValues[2]
                    }
                }
            }
            if (Prefs.skipSounds) t = t.replace(soundRegex, " ").replace(spaces, " ").trim()
            if (t.count { it.isLetter() } < 1) continue

            val n = name
            currentVoice = when {
                n != null -> characters.getOrPut(n) { (characters.size % 5) + 1 }
                s.dashed -> if (currentVoice == 0) 1 else 0
                else -> currentVoice
            }
            Speaker.speak(t, currentVoice, false)
        }
    }

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    /** Pour les sous-titres qui défilent mot à mot (YouTube auto) : ne lire que la nouvelle partie. */
    private fun newPart(old: String, new: String): String {
        if (old.isEmpty()) return new
        val ow = old.split(Regex("\\s+"))
        val nw = new.split(Regex("\\s+"))
        val maxK = minOf(ow.size, nw.size)
        for (k in maxK downTo 3) {
            var ok = true
            for (i in 0 until k) {
                if (norm(ow[ow.size - k + i]) != norm(nw[i])) { ok = false; break }
            }
            if (ok) return nw.drop(k).joinToString(" ")
        }
        return new
    }

    private fun similar(a: String, b: String): Boolean {
        val x = norm(a).take(300)
        val y = norm(b).take(300)
        if (x.isEmpty() || y.isEmpty()) return false
        if (x == y) return true
        val d = levenshtein(x, y)
        return 1.0 - d.toDouble() / maxOf(x.length, y.length) >= 0.85
    }

    private fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val c = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + c)
            }
            val tmp = prev; prev = cur; cur = tmp
        }
        return prev[b.length]
    }
}
