package ml.sidibe.voixsoustitres

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import kotlin.math.roundToInt

class MainActivity : Activity() {

    companion object {
        private const val REQ_CAPTURE = 42
        private val BG = Color.parseColor("#101418")
        private val ACCENT = Color.parseColor("#1DB954")
        private val MUTED = Color.parseColor("#9AA4AE")
    }

    private lateinit var statusAccess: TextView
    private lateinit var statusCapture: TextView
    private lateinit var captureBtn: Button
    private lateinit var journal: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        Speaker.init(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(48))
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(BG)
            fitsSystemWindows = true
            addView(root)
        }
        setContentView(scroll)

        root.addView(heading("Voix Sous-titres"))
        root.addView(body("Lit à voix haute les sous-titres de vos films, dans n'importe quelle application, avec une voix différente par personnage."))

        // ----- Mode 1
        root.addView(section("1. Mode Accessibilité"))
        root.addView(small("Lit directement le texte des sous-titres quand l'application le permet (VLC, certains lecteurs…). Rapide et précis."))
        statusAccess = body("")
        root.addView(statusAccess)
        root.addView(button("Ouvrir les réglages d'accessibilité") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        root.addView(small("Message « Paramètre restreint » ? Touchez le bouton ci-dessous, puis ⋮ en haut à droite → « Autoriser les paramètres restreints ». Revenez ensuite activer Voix Sous-titres dans l'accessibilité."))
        root.addView(button("Ouvrir les infos de l'app") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        })

        // ----- Mode 2
        root.addView(section("2. Mode Capture d'écran"))
        root.addView(small("Lit les sous-titres affichés en bas de l'écran (reconnaissance de texte). Marche avec la plupart des apps, y compris YouTube. Netflix et les apps protégées donnent un écran noir : là, ça ne peut pas marcher."))
        root.addView(small("Quand Android demande quoi partager, choisissez « Écran entier » (pas « Une seule appli »)."))
        statusCapture = body("")
        root.addView(statusCapture)
        captureBtn = button("") { toggleCapture() }
        root.addView(captureBtn)

        // ----- Réglages
        root.addView(section("Réglages"))
        root.addView(toggle("Lecture à voix haute activée", Prefs.enabled) { Prefs.enabled = it })
        root.addView(toggle("Une voix différente par personnage", Prefs.multiVoice) { Prefs.multiVoice = it })
        root.addView(toggle("Baisser le son du film quand la voix parle", Prefs.duck) { Prefs.duck = it })
        root.addView(toggle("Ignorer les bruits : [musique], (rires)…", Prefs.skipSounds) { Prefs.skipSounds = it })

        root.addView(body("Langue de la voix"))
        val labels = arrayOf("Français", "Anglais", "Langue du téléphone")
        val codes = arrayOf("fr", "en", "auto")
        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        spinner.setSelection(codes.indexOf(Prefs.language).coerceAtLeast(0))
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (Prefs.language != codes[position]) {
                    Prefs.language = codes[position]
                    Speaker.applyLanguage()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        root.addView(spinner)

        val rateLabel = body("")
        val showRate = { rateLabel.text = "Vitesse de la voix : ${"%.2f".format(Prefs.rate)}×" }
        showRate()
        root.addView(rateLabel)
        root.addView(seek(30, ((Prefs.rate - 0.5f) / 0.05f).roundToInt()) {
            Prefs.rate = 0.5f + it * 0.05f
            showRate()
        })

        val zoneLabel = body("")
        val showZone = { zoneLabel.text = "Zone des sous-titres (mode capture) : à partir de ${Prefs.zoneTop} % de la hauteur de l'écran" }
        showZone()
        root.addView(zoneLabel)
        root.addView(seek(55, Prefs.zoneTop - 30) {
            Prefs.zoneTop = 30 + it
            showZone()
        })

        root.addView(button("Tester les voix") { Speaker.test() })

        // ----- Journal
        root.addView(section("Journal"))
        root.addView(small("Les dernières phrases lues et les messages de l'application."))
        journal = small("")
        journal.typeface = Typeface.MONOSPACE
        root.addView(journal)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7)
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
        journal.text = AppLog.text()
        AppLog.listener = {
            journal.text = AppLog.text()
            refresh()
        }
    }

    override fun onPause() {
        AppLog.listener = null
        super.onPause()
    }

    private fun accessibilityOn(): Boolean {
        val s = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return s.contains("$packageName/")
    }

    private fun refresh() {
        statusAccess.text = if (accessibilityOn()) "✅ Service d'accessibilité activé" else "❌ Service d'accessibilité désactivé"
        val c = ScreenCaptureService.running
        statusCapture.text = if (c) "✅ Capture d'écran en cours" else "⏸ Capture d'écran arrêtée"
        captureBtn.text = if (c) "Arrêter la capture d'écran" else "Démarrer la capture d'écran"
    }

    private fun toggleCapture() {
        if (ScreenCaptureService.running) {
            startService(Intent(this, ScreenCaptureService::class.java).setAction(ScreenCaptureService.ACTION_STOP))
            captureBtn.postDelayed({ refresh() }, 600)
        } else {
            val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            @Suppress("DEPRECATION")
            startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CAPTURE)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_CAPTURE) return
        if (resultCode == RESULT_OK && data != null) {
            val i = Intent(this, ScreenCaptureService::class.java)
                .putExtra(ScreenCaptureService.EXTRA_CODE, resultCode)
                .putExtra(ScreenCaptureService.EXTRA_DATA, data)
            startForegroundService(i)
            captureBtn.postDelayed({ refresh() }, 900)
            Toast.makeText(this, "C'est parti ! Lancez votre film.", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Capture refusée", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- petits outils d'interface ----------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun heading(t: String) = TextView(this).apply {
        text = t
        textSize = 28f
        setTextColor(Color.WHITE)
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, 0, 0, dp(6))
    }

    private fun section(t: String) = TextView(this).apply {
        text = t
        textSize = 19f
        setTextColor(ACCENT)
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(26), 0, dp(6))
    }

    private fun body(t: String) = TextView(this).apply {
        text = t
        textSize = 15f
        setTextColor(Color.WHITE)
        setPadding(0, dp(4), 0, dp(4))
    }

    private fun small(t: String) = TextView(this).apply {
        text = t
        textSize = 13f
        setTextColor(MUTED)
        setPadding(0, dp(2), 0, dp(6))
    }

    private fun button(t: String, onClick: () -> Unit) = Button(this).apply {
        text = t
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    private fun toggle(t: String, value: Boolean, onChange: (Boolean) -> Unit) = Switch(this).apply {
        text = t
        textSize = 15f
        setTextColor(Color.WHITE)
        isChecked = value
        setPadding(0, dp(8), 0, dp(8))
        setOnCheckedChangeListener { _, checked -> onChange(checked) }
    }

    private fun seek(maxValue: Int, value: Int, onChange: (Int) -> Unit) = SeekBar(this).apply {
        max = maxValue
        progress = value.coerceIn(0, maxValue)
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) onChange(p)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }
}
