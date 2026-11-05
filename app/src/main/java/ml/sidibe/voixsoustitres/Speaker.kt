package ml.sidibe.voixsoustitres

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/** Synthèse vocale : plusieurs voix (et hauteurs) pour distinguer les personnages. */
object Speaker : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    @Volatile private var ready = false
    private lateinit var app: Context
    private var voices: List<Voice> = emptyList()
    private val pending = AtomicInteger(0)
    private var focusRequest: AudioFocusRequest? = null
    private var counter = 0

    /** Hauteur de voix par personnage : 0 = normal, 1 = grave, 2 = aigu… */
    private val pitches = floatArrayOf(1.0f, 0.72f, 1.32f, 0.88f, 1.16f, 0.62f)

    private val attrs: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    fun init(c: Context) {
        if (tts != null) return
        app = c.applicationContext
        tts = TextToSpeech(app, this)
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            AppLog.add("Synthèse vocale indisponible : installez « Services de reconnaissance vocale Google » depuis le Play Store")
            return
        }
        ready = true
        tts?.setAudioAttributes(attrs)
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) = finished()
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finished()
        })
        applyLanguage()
    }

    private fun finished() {
        if (pending.decrementAndGet() <= 0) {
            pending.set(0)
            abandonFocus()
        }
    }

    fun applyLanguage() {
        val t = tts ?: return
        if (!ready) return
        val loc = when (Prefs.language) {
            "en" -> Locale.US
            "auto" -> Locale.getDefault()
            else -> Locale.FRANCE
        }
        val r = t.setLanguage(loc)
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            AppLog.add("La langue ${loc.displayLanguage} n'est pas installée dans la synthèse vocale du téléphone")
        }
        voices = try {
            (t.voices ?: emptySet())
                .filter { v ->
                    v.locale.language == loc.language &&
                        !v.isNetworkConnectionRequired &&
                        !v.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
                }
                .sortedBy { it.name }
        } catch (e: Exception) {
            emptyList()
        }
        AppLog.add("${voices.size} voix trouvée(s) en ${loc.displayLanguage}")
    }

    fun speak(text: String, voiceIndex: Int, flush: Boolean) {
        val t = tts ?: return
        if (!ready) return
        // Si la voix prend trop de retard sur le film, on saute pour rester synchronisé.
        if (flush || pending.get() >= 3) {
            t.stop()
            pending.set(0)
        }
        val idx = if (Prefs.multiVoice) voiceIndex else 0
        if (voices.isNotEmpty()) {
            try { t.voice = voices[idx % voices.size] } catch (_: Exception) {}
        }
        t.setPitch(pitches[idx % pitches.size])
        t.setSpeechRate(Prefs.rate)
        requestFocus()
        pending.incrementAndGet()
        t.speak(text, TextToSpeech.QUEUE_ADD, null, "u${counter++}")
    }

    fun test() {
        val lines = if (Prefs.language == "en") listOf(
            "Hello, I am the first character.",
            "And I am the second one.",
            "I am the third!"
        ) else listOf(
            "Bonjour, je suis le premier personnage.",
            "Et moi, je suis le deuxième.",
            "Moi, je suis le troisième !"
        )
        lines.forEachIndexed { i, s -> speak(s, i, i == 0) }
    }

    private fun audio(): AudioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private fun requestFocus() {
        if (!Prefs.duck) return
        val f = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attrs)
            .build()
            .also { focusRequest = it }
        try { audio().requestAudioFocus(f) } catch (_: Exception) {}
    }

    private fun abandonFocus() {
        val f = focusRequest ?: return
        try { audio().abandonAudioFocusRequest(f) } catch (_: Exception) {}
    }
}
