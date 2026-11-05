package ml.sidibe.voixsoustitres

import android.content.Context
import android.content.SharedPreferences

/** Réglages de l'application, gardés sur le téléphone. */
object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(c: Context) {
        if (!::sp.isInitialized) {
            sp = c.applicationContext.getSharedPreferences("reglages", Context.MODE_PRIVATE)
        }
    }

    var enabled: Boolean
        get() = sp.getBoolean("enabled", true)
        set(v) = sp.edit().putBoolean("enabled", v).apply()

    var multiVoice: Boolean
        get() = sp.getBoolean("multiVoice", true)
        set(v) = sp.edit().putBoolean("multiVoice", v).apply()

    var duck: Boolean
        get() = sp.getBoolean("duck", true)
        set(v) = sp.edit().putBoolean("duck", v).apply()

    var skipSounds: Boolean
        get() = sp.getBoolean("skipSounds", true)
        set(v) = sp.edit().putBoolean("skipSounds", v).apply()

    var rate: Float
        get() = sp.getFloat("rate", 1.1f)
        set(v) = sp.edit().putFloat("rate", v).apply()

    /** "fr", "en" ou "auto" (langue du téléphone) */
    var language: String
        get() = sp.getString("language", "fr") ?: "fr"
        set(v) = sp.edit().putString("language", v).apply()

    /** Début de la zone des sous-titres, en % de la hauteur de l'écran (mode capture). */
    var zoneTop: Int
        get() = sp.getInt("zoneTop", 60)
        set(v) = sp.edit().putInt("zoneTop", v).apply()
}
