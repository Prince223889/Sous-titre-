package ml.sidibe.voixsoustitres

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.drawable.Icon
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Display
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/** Mode 2 : capture le bas de l'écran et lit les sous-titres par reconnaissance de texte. */
class ScreenCaptureService : Service() {

    companion object {
        @Volatile var running = false
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        const val ACTION_STOP = "ml.sidibe.voixsoustitres.STOP"
        private const val CHANNEL = "capture"
        private const val NOTIF_ID = 7
    }

    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var bg: Handler? = null
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    @Volatile private var busy = false
    private var w = 0
    private var h = 0
    private var blackFrames = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        Prefs.init(this)
        Speaker.init(this)
        startForegroundNow()

        if (projection != null) return START_NOT_STICKY
        val code = intent?.getIntExtra(EXTRA_CODE, 0) ?: 0
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_DATA)
        }
        if (data == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val p = try { mpm.getMediaProjection(code, data) } catch (e: Exception) { null }
        if (p == null) {
            AppLog.add("Impossible de démarrer la capture d'écran")
            stopSelf()
            return START_NOT_STICKY
        }
        projection = p
        val t = HandlerThread("capture").also { it.start() }
        thread = t
        val handler = Handler(t.looper)
        bg = handler
        p.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                AppLog.add("Capture arrêtée par Android")
                stopSelf()
            }
        }, handler)
        handler.post {
            try {
                setupDisplay()
                running = true
                AppLog.add("Mode capture d'écran démarré")
                handler.post(tick)
            } catch (e: Exception) {
                AppLog.add("Erreur au démarrage de la capture : ${e.message}")
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundNow() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Lecture des sous-titres", NotificationManager.IMPORTANCE_LOW)
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, ScreenCaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Voix Sous-titres")
            .setContentText("Lecture des sous-titres à l'écran en cours")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null as Icon?, "Arrêter", stop).build())
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    /** Taille de l'écran (divisée par 2 pour aller plus vite). Change quand on tourne le téléphone. */
    private fun screenSize(): Triple<Int, Int, Int> {
        val dm = getSystemService(DISPLAY_SERVICE) as DisplayManager
        val d = dm.getDisplay(Display.DEFAULT_DISPLAY)
        val m = DisplayMetrics()
        @Suppress("DEPRECATION")
        d.getRealMetrics(m)
        return Triple(m.widthPixels / 2, m.heightPixels / 2, maxOf(m.densityDpi / 2, 1))
    }

    private fun setupDisplay() {
        val (sw, sh, dpi) = screenSize()
        w = sw
        h = sh
        val old = reader
        val r = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        reader = r
        val vd = display
        if (vd == null) {
            display = projection!!.createVirtualDisplay(
                "VoixSousTitres", w, h, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                r.surface, null, bg
            )
        } else {
            vd.resize(w, h, dpi)
            vd.surface = r.surface
        }
        old?.close()
    }

    private val tick = object : Runnable {
        override fun run() {
            try { capture() } catch (e: Exception) { AppLog.add("Erreur capture : ${e.message}") }
            if (running) bg?.postDelayed(this, 450)
        }
    }

    private fun capture() {
        if (!Prefs.enabled || busy) return
        val (sw, sh, _) = screenSize()
        if (sw != w || sh != h) {
            setupDisplay()
            return
        }
        val img = reader?.acquireLatestImage() ?: return
        val (zone, iw) = try {
            val iw0 = img.width
            val ih = img.height
            val plane = img.planes[0]
            val pixelStride = plane.pixelStride
            val rowPadding = plane.rowStride - pixelStride * iw0
            val full = Bitmap.createBitmap(iw0 + rowPadding / pixelStride, ih, Bitmap.Config.ARGB_8888)
            full.copyPixelsFromBuffer(plane.buffer)
            checkBlack(full, iw0, ih)
            val top = (ih * Prefs.zoneTop / 100).coerceIn(0, ih - 20)
            val bottom = ih * 97 / 100
            Pair(Bitmap.createBitmap(full, 0, top, iw0, (bottom - top).coerceAtLeast(20)), iw0)
        } finally {
            img.close()
        }

        busy = true
        recognizer.process(InputImage.fromBitmap(zone, 0))
            .addOnSuccessListener { result ->
                val lines = result.textBlocks
                    .flatMap { it.lines }
                    .filter { line ->
                        val box = line.boundingBox
                        // Les sous-titres sont centrés : on ignore les textes collés aux bords (boutons, heure…)
                        val centered = box == null || (box.centerX() > iw * 0.15 && box.centerX() < iw * 0.85)
                        centered && line.text.count { it.isLetter() } >= 2
                    }
                    .sortedBy { it.boundingBox?.top ?: 0 }
                    .map { it.text }
                if (lines.isEmpty()) SubtitleProcessor.onEmpty()
                else SubtitleProcessor.onText(lines.joinToString("\n"), "Écran", true)
            }
            .addOnCompleteListener { busy = false }
    }

    /** Netflix & co. rendent l'écran tout noir à la capture : on prévient l'utilisateur. */
    private fun checkBlack(b: Bitmap, iw: Int, ih: Int) {
        var bright = false
        var y = ih / 10
        while (y < ih && !bright) {
            var x = iw / 10
            while (x < iw) {
                val c = b.getPixel(x, y)
                if (((c shr 16) and 0xFF) > 20 || ((c shr 8) and 0xFF) > 20 || (c and 0xFF) > 20) {
                    bright = true
                    break
                }
                x += iw / 10
            }
            y += ih / 10
        }
        blackFrames = if (bright) 0 else blackFrames + 1
        if (blackFrames == 20) AppLog.add("Écran noir à la capture : cette application est sûrement protégée (ex. Netflix)")
    }

    override fun onDestroy() {
        running = false
        try { display?.release() } catch (_: Exception) {}
        try { reader?.close() } catch (_: Exception) {}
        val p = projection
        projection = null
        try { p?.stop() } catch (_: Exception) {}
        thread?.quitSafely()
        AppLog.add("Mode capture d'écran arrêté")
        super.onDestroy()
    }
}
