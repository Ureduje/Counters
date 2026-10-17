package si.vzpenjaca.kontrola

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

data class StationState(
    val isOnline: Boolean = false,
    val currentPersons: Int = 0,
    val maxPersons: Int = 28,
    val remainingTimeMs: Long = 0L,
    val setTimeMs: Long = 0L
)

class MobileConfig(private val context: Context) {
    private val prefs = context.getSharedPreferences("vzpenjaca_prefs", Context.MODE_PRIVATE)

    var useCloudRelay: Boolean = true
    var cloudServer: String = "https://vzpenjaca-kontrola-default-rtdb.europe-west1.firebasedatabase.app"
    var cloudChannelId: String = "vzpenjaca-3436"
    var cloudPin: String = ""
    var ipZgoraj: String = "10.12.220.34"
    var ipSpodaj: String = "10.12.220.36"
    var soundEnabled: Boolean = true
    var confirmReset: Boolean = true
    var muteZgoraj: Boolean = false
    var muteSpodaj: Boolean = false
    var dimZgoraj: Boolean = false
    var dimSpodaj: Boolean = false
    var showIntervalBgZgoraj: Boolean = true
    var showIntervalBgSpodaj: Boolean = true

    init {
        load()
    }

    fun load() {
        if (prefs.contains("useCloudRelay")) useCloudRelay = prefs.getBoolean("useCloudRelay", true)
        prefs.getString("cloudServer", null)?.takeIf { it.isNotBlank() }?.let {
            val srv = it.trimEnd('/')
            cloudServer = if (srv.equals("https://ntfy.sh", ignoreCase = true) || srv.equals("http://ntfy.sh", ignoreCase = true)) {
                "https://ntfy.adminforge.de"
            } else {
                srv
            }
        }
        prefs.getString("cloudChannelId", null)?.takeIf { it.isNotBlank() }?.let { cloudChannelId = sanitizeChannel(it) }
        prefs.getString("cloudPin", null)?.let { cloudPin = it }
        prefs.getString("ipZgoraj", null)?.takeIf { it.isNotBlank() }?.let { ipZgoraj = it.trim() }
        prefs.getString("ipSpodaj", null)?.takeIf { it.isNotBlank() }?.let { ipSpodaj = it.trim() }
        if (prefs.contains("soundEnabled")) soundEnabled = prefs.getBoolean("soundEnabled", true)
        if (prefs.contains("confirmReset")) confirmReset = prefs.getBoolean("confirmReset", true)
        if (prefs.contains("muteZgoraj")) muteZgoraj = prefs.getBoolean("muteZgoraj", false)
        if (prefs.contains("muteSpodaj")) muteSpodaj = prefs.getBoolean("muteSpodaj", false)
        if (prefs.contains("dimZgoraj")) dimZgoraj = prefs.getBoolean("dimZgoraj", false)
        if (prefs.contains("dimSpodaj")) dimSpodaj = prefs.getBoolean("dimSpodaj", false)
        if (prefs.contains("showIntervalBgZgoraj")) showIntervalBgZgoraj = prefs.getBoolean("showIntervalBgZgoraj", true)
        if (prefs.contains("showIntervalBgSpodaj")) showIntervalBgSpodaj = prefs.getBoolean("showIntervalBgSpodaj", true)
    }

    fun save() {
        prefs.edit()
            .putBoolean("useCloudRelay", useCloudRelay)
            .putString("cloudServer", cloudServer.trimEnd('/'))
            .putString("cloudChannelId", sanitizeChannel(cloudChannelId))
            .putString("cloudPin", cloudPin.trim())
            .putString("ipZgoraj", ipZgoraj.trim())
            .putString("ipSpodaj", ipSpodaj.trim())
            .putBoolean("soundEnabled", soundEnabled)
            .putBoolean("confirmReset", confirmReset)
            .putBoolean("muteZgoraj", muteZgoraj)
            .putBoolean("muteSpodaj", muteSpodaj)
            .putBoolean("dimZgoraj", dimZgoraj)
            .putBoolean("dimSpodaj", dimSpodaj)
            .putBoolean("showIntervalBgZgoraj", showIntervalBgZgoraj)
            .putBoolean("showIntervalBgSpodaj", showIntervalBgSpodaj)
            .apply()
    }

    val isFirebase: Boolean
        get() = isFirebaseServer(cloudServer)

    val stateTopic: String
        get() = "${sanitizeChannel(cloudChannelId)}-state"

    val commandTopic: String
        get() = "${sanitizeChannel(cloudChannelId)}-cmd"

    fun firebaseUrl(node: String): String {
        val base = normalizeServerUrl(cloudServer)
        return "$base/vzpenjaca/${sanitizeChannel(cloudChannelId)}/$node.json"
    }

    companion object {
        fun isFirebaseServer(server: String): Boolean {
            val s = server.trim().lowercase(Locale.ROOT)
            return s.contains("firebaseio.com") || s.contains("firebasedatabase.app")
        }

        fun normalizeServerUrl(raw: String): String {
            val trimmed = raw.trim().trimEnd('/')
            if (trimmed.isEmpty()) return "https://ntfy.adminforge.de"
            var s = if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
                "https://$trimmed"
            } else {
                trimmed
            }
            if (s.endsWith(".json", ignoreCase = true)) {
                s = s.substring(0, s.length - 5).trimEnd('/')
            }
            return s
        }

        fun sanitizeChannel(raw: String): String {
            val cleaned = raw.trim().lowercase(Locale.ROOT).filter {
                (it in 'a'..'z') || (it in '0'..'9') || it == '-' || it == '_'
            }
            return if (cleaned.length >= 4) cleaned else "vzpenjaca-3436"
        }
    }
}

object ThemeColors {
    val WindowBg = Color.parseColor("#0B0F17")
    val CardBg = Color.parseColor("#131A26")
    val CardBorder = Color.parseColor("#222E42")
    val SubCardBg = Color.parseColor("#0E141E")
    val Divider = Color.parseColor("#1F2A3C")

    val TextPrimary = Color.parseColor("#F8FAFC")
    val TextSecondary = Color.parseColor("#94A3B8")
    val TextMuted = Color.parseColor("#64748B")

    val AccentBlue = Color.parseColor("#38BDF8")
    val AccentBlueActiveBg = Color.parseColor("#162C46")
    val AccentBtnMuted = Color.parseColor("#3E5274")
    val AccentEmerald = Color.parseColor("#10B981")
    val AccentAmber = Color.parseColor("#F59E0B")
    val AccentCoral = Color.parseColor("#EF4444")
    val AccentRed = Color.parseColor("#EF4444")

    val BtnBg = Color.parseColor("#1C2638")
    val BtnPressedBg = Color.parseColor("#151D2B")
    val BtnBorder = Color.parseColor("#2E3D56")
    val TrackBg = Color.parseColor("#1C2638")
}

object MobileChimePlayer {
    var enabled: Boolean = true
    private val executor = Executors.newSingleThreadExecutor()

    private val pcmTimeSingle: ShortArray by lazy {
        buildChimePcm(doubleArrayOf(932.33), 0.42, 0.0, true)
    }
    private val pcmTimeTriple: ShortArray by lazy {
        buildChimePcm(doubleArrayOf(1046.50, 1046.50, 1318.51), 0.17, 0.045, true)
    }
    private val pcmPersonsSingle: ShortArray by lazy {
        buildChimePcm(doubleArrayOf(523.25), 0.42, 0.0, false)
    }
    private val pcmPersonsTriple: ShortArray by lazy {
        buildChimePcm(doubleArrayOf(587.33, 587.33, 783.99), 0.18, 0.045, false)
    }

    fun playTimeSingle() = playPcm(pcmTimeSingle)
    fun playTimeTriple() = playPcm(pcmTimeTriple)
    fun playPersonsSingle() = playPcm(pcmPersonsSingle)
    fun playPersonsTriple() = playPcm(pcmPersonsTriple)

    private fun playPcm(samples: ShortArray) {
        if (!enabled || samples.isEmpty()) return
        executor.execute {
            try {
                val sampleRate = 44100
                val bufferBytes = samples.size * 2
                val track = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(sampleRate)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build()
                        )
                        .setBufferSizeInBytes(bufferBytes)
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferBytes,
                        AudioTrack.MODE_STATIC
                    )
                }
                track.write(samples, 0, samples.size)
                track.play()
                val durationMs = (samples.size * 1000L) / sampleRate + 80L
                Thread.sleep(durationMs)
                track.stop()
                track.release()
            } catch (_: Throwable) {
            }
        }
    }

    private fun buildChimePcm(
        frequencies: DoubleArray,
        noteDurationSec: Double,
        gapSec: Double,
        brightBellTimbre: Boolean
    ): ShortArray {
        val sampleRate = 44100
        val noteSamples = (sampleRate * noteDurationSec).toInt()
        val gapSamples = (sampleRate * gapSec).toInt()
        val tailSamples = (sampleRate * 0.06).toInt()
        val totalSamples =
            frequencies.size * noteSamples + max(0, frequencies.size - 1) * gapSamples + tailSamples

        val pcm = ShortArray(totalSamples)
        for (n in frequencies.indices) {
            val freq = frequencies[n]
            val startIdx = n * (noteSamples + gapSamples)
            val renderLen = min(noteSamples + tailSamples, totalSamples - startIdx)

            for (i in 0 until renderLen) {
                val t = i.toDouble() / sampleRate
                val attack = if (t < 0.006) (t / 0.006) else 1.0
                val sample = if (brightBellTimbre) {
                    val env1 = exp(-t * 7.5)
                    val env2 = exp(-t * 14.0)
                    val env3 = exp(-t * 22.0)
                    0.65 * env1 * sin(2.0 * Math.PI * freq * t) +
                        0.25 * env2 * sin(2.0 * Math.PI * (freq * 2.0) * t) +
                        0.10 * env3 * sin(2.0 * Math.PI * (freq * 3.0) * t)
                } else {
                    val env1 = exp(-t * 6.8)
                    val env2 = exp(-t * 11.0)
                    0.62 * env1 * sin(2.0 * Math.PI * freq * t) +
                        0.30 * env2 * sin(2.0 * Math.PI * (freq * 1.4983) * t) +
                        0.08 * env2 * sin(2.0 * Math.PI * (freq * 0.5) * t)
                }
                val value = (attack * sample * 21000.0).toInt()
                val mixed = (pcm[startIdx + i].toInt() + value).coerceIn(-32000, 32000)
                pcm[startIdx + i] = mixed.toShort()
            }
        }
        return pcm
    }
}

object DirectLanFunicularClient {
    fun sendCommand(host: String, port: Int, commandId: Int, timeoutMs: Int = 1800): StationState {
        Socket().use { socket ->
            socket.soTimeout = timeoutMs
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            val path = "/Funicular/Operater?$commandId"
            val req = "GET $path HTTP/1.1\r\nHost: $host\r\nConnection: close\r\n\r\n"
            val out = socket.getOutputStream()
            out.write(req.toByteArray(StandardCharsets.US_ASCII))
            out.flush()

            val inp = socket.getInputStream()
            val baos = ByteArrayOutputStream()
            val buf = ByteArray(512)
            while (true) {
                val read = try {
                    inp.read(buf)
                } catch (_: Throwable) {
                    if (baos.size() > 0) break else throw RuntimeException("Timeout")
                }
                if (read <= 0) break
                baos.write(buf, 0, read)
                val partial = String(baos.toByteArray(), StandardCharsets.US_ASCII)
                val line = extractPayloadLine(partial)
                if (line != null) {
                    val parts = line.split('#')
                    if (parts.size >= 5 && parts[4].trim().isNotEmpty()) break
                }
            }
            val raw = String(baos.toByteArray(), StandardCharsets.US_ASCII)
            return parseTelemetry(raw)
        }
    }

    fun executeComplexCommand(host: String, commandId: Int): StationState {
        if (commandId == 9) {
            val targetMax = 28
            var t = sendCommand(host, 80, 0, 1800)
            var attempts = 0
            while (t.maxPersons != targetMax && attempts < 35) {
                attempts++
                val adjCmd = if (t.maxPersons < targetMax) 7 else 8
                Thread.sleep(45)
                try {
                    t = sendCommand(host, 80, adjCmd, 1500)
                } catch (_: Throwable) {
                    Thread.sleep(120)
                }
            }
            return t
        } else if (commandId == 10 || commandId == 11) {
            val targetSetMs = if (commandId == 10) 360000L else 600000L
            var t = sendCommand(host, 80, 0, 1800)
            var attempts = 0
            while (abs(t.setTimeMs - targetSetMs) >= 7500L && attempts < 30) {
                attempts++
                val diff = targetSetMs - t.setTimeMs
                val adjCmd = when {
                    diff >= 210000L -> 3
                    diff <= -210000L -> 6
                    diff >= 37500L -> 2
                    diff <= -37500L -> 5
                    diff > 0L -> 1
                    else -> 4
                }
                Thread.sleep(45)
                try {
                    t = sendCommand(host, 80, adjCmd, 1500)
                } catch (_: Throwable) {
                    Thread.sleep(120)
                }
            }
            return t
        } else {
            return sendCommand(host, 80, commandId, 1800)
        }
    }

    private fun extractPayloadLine(raw: String): String? {
        val idx = raw.indexOf("\r\n\r\n")
        val body = if (idx >= 0) raw.substring(idx + 4) else raw
        for (line in body.split('\r', '\n')) {
            val trimmed = line.trim()
            if (trimmed.contains('#')) return trimmed
        }
        return null
    }

    private fun parseTelemetry(raw: String): StationState {
        val payload = extractPayloadLine(raw) ?: throw IllegalArgumentException("Invalid response")
        val parts = payload.split('#')
        if (parts.size < 4) throw IllegalArgumentException("Incomplete response")
        val cur = parts[0].trim().toIntOrNull() ?: 0
        val maxP = parts[1].trim().toIntOrNull() ?: 28
        val remMs = max(0L, parts[2].trim().toLongOrNull() ?: 0L)
        val setMs = max(0L, parts[3].trim().toLongOrNull() ?: 0L)
        return StationState(
            isOnline = true,
            currentPersons = cur,
            maxPersons = maxP,
            remainingTimeMs = remMs,
            setTimeMs = setMs
        )
    }
}

class MainActivity : Activity() {

    private lateinit var config: MobileConfig
    private val uiHandler = Handler(Looper.getMainLooper())
    private val ioExecutor = Executors.newCachedThreadPool()

    private lateinit var lblModeStatus: TextView
    private lateinit var statusBadgeDot: View
    private lateinit var cardZgoraj: StationCardView
    private lateinit var cardSpodaj: StationCardView

    private val streamActive = AtomicBoolean(false)
    @Volatile
    private var activeStreamConnection: HttpURLConnection? = null
    @Volatile
    private var lastCloudSyncSendMs: Long = 0L
    @Volatile
    private var lastAppliedStateTs: Long = 0L

    private val tickRunnable = object : Runnable {
        override fun run() {
            cardZgoraj.onLocalTick(200L)
            cardSpodaj.onLocalTick(200L)

            if (config.useCloudRelay) {
                val now = SystemClock.elapsedRealtime()
                if (now - lastCloudSyncSendMs > 90_000L) {
                    requestCloudSync()
                }
            }
            uiHandler.postDelayed(this, 200L)
        }
    }

    private val lanPollRunnable = object : Runnable {
        override fun run() {
            if (!config.useCloudRelay) {
                pollDirectLanBothStations(false)
                uiHandler.postDelayed(this, 2000L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = ThemeColors.WindowBg
        window.navigationBarColor = ThemeColors.WindowBg

        config = MobileConfig(this)
        MobileChimePlayer.enabled = config.soundEnabled

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ThemeColors.WindowBg)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Top Bar
        val topBar = buildTopBar()
        root.addView(topBar)

        // Scrollable Station Cards Container
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val cardsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(6), dp(12), dp(14))
        }

        cardZgoraj = StationCardView(
            context = this,
            stationId = "zgoraj",
            stationTitle = "ZGORNJA POSTAJA",
            initialMuted = config.muteZgoraj,
            initialDimmed = config.dimZgoraj,
            initialShowIntervalBg = config.showIntervalBgZgoraj,
            onQuickSettingsChanged = { muted, dimmed, showIntervalBg ->
                config.muteZgoraj = muted
                config.dimZgoraj = dimmed
                config.showIntervalBgZgoraj = showIntervalBg
                config.save()
            },
            onOperatorCommand = { cmdId ->
                handleOperatorCommand("zgoraj", cmdId)
            }
        )

        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(10))
        }

        cardSpodaj = StationCardView(
            context = this,
            stationId = "spodaj",
            stationTitle = "SPODNJA POSTAJA",
            initialMuted = config.muteSpodaj,
            initialDimmed = config.dimSpodaj,
            initialShowIntervalBg = config.showIntervalBgSpodaj,
            onQuickSettingsChanged = { muted, dimmed, showIntervalBg ->
                config.muteSpodaj = muted
                config.dimSpodaj = dimmed
                config.showIntervalBgSpodaj = showIntervalBg
                config.save()
            },
            onOperatorCommand = { cmdId ->
                handleOperatorCommand("spodaj", cmdId)
            }
        )

        cardsContainer.addView(
            cardZgoraj,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        cardsContainer.addView(spacer)
        cardsContainer.addView(
            cardSpodaj,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        scroll.addView(cardsContainer)
        root.addView(scroll)

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        uiHandler.removeCallbacks(tickRunnable)
        uiHandler.post(tickRunnable)
        restartNetworking()
    }

    override fun onPause() {
        super.onPause()
        uiHandler.removeCallbacks(tickRunnable)
        uiHandler.removeCallbacks(lanPollRunnable)
        stopCloudStream()
    }

    private fun buildTopBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(8))
        }

        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val lblTitle = TextView(this).apply {
            text = "Vzpenjača"
            setTextColor(ThemeColors.TextPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }

        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        statusBadgeDot = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ThemeColors.AccentAmber)
            }
            layoutParams = LinearLayout.LayoutParams(dp(7), dp(7)).apply {
                rightMargin = dp(6)
            }
        }

        lblModeStatus = TextView(this).apply {
            text = "Povezovanje..."
            setTextColor(ThemeColors.TextSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }

        statusRow.addView(statusBadgeDot)
        statusRow.addView(lblModeStatus)

        titleCol.addView(lblTitle)
        titleCol.addView(statusRow)

        val btnSync = createHeaderButton("Osveži") {
            if (config.useCloudRelay) {
                updateTopStatus("Osveževanje iz oblaka...", ThemeColors.AccentBlue)
                requestCloudSync()
                uiHandler.postDelayed({ fetchLatestCachedCloudState() }, 900L)
            } else {
                updateTopStatus("Osveževanje prek LAN...", ThemeColors.AccentBlue)
                pollDirectLanBothStations(true)
            }
        }

        val btnSettings = createHeaderButton("Nastavitve") {
            showSettingsDialog()
        }.apply {
            (layoutParams as LinearLayout.LayoutParams).leftMargin = dp(8)
        }

        bar.addView(titleCol)
        bar.addView(btnSync)
        bar.addView(btnSettings)
        return bar
    }

    private fun createHeaderButton(label: String, onClick: () -> Unit): TextView {
        val normalBg = GradientDrawable().apply {
            cornerRadius = dp(7).toFloat()
            setColor(ThemeColors.BtnBg)
            setStroke(dp(1), ThemeColors.BtnBorder)
        }
        val pressedBg = GradientDrawable().apply {
            cornerRadius = dp(7).toFloat()
            setColor(ThemeColors.BtnPressedBg)
            setStroke(dp(1), ThemeColors.AccentBlue)
        }

        return TextView(this).apply {
            text = label
            setTextColor(ThemeColors.TextPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = normalBg
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.background = pressedBg
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        v.background = normalBg
                        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onClick()
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        v.background = normalBg
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun updateTopStatus(text: String, dotColor: Int) {
        uiHandler.post {
            lblModeStatus.text = text
            (statusBadgeDot.background as? GradientDrawable)?.setColor(dotColor)
        }
    }

    private fun restartNetworking() {
        uiHandler.removeCallbacks(lanPollRunnable)
        stopCloudStream()
        lastAppliedStateTs = 0L

        if (config.useCloudRelay) {
            updateTopStatus("Oblak (${config.cloudChannelId}): povezovanje...", ThemeColors.AccentAmber)
            startCloudStream()
            requestCloudSync()
            uiHandler.postDelayed({ fetchLatestCachedCloudState() }, 700L)
        } else {
            updateTopStatus("Lokalni Wi-Fi / VPN (${config.ipZgoraj}, ${config.ipSpodaj})", ThemeColors.AccentEmerald)
            uiHandler.post(lanPollRunnable)
        }
    }

    private fun handleOperatorCommand(station: String, cmdId: Int) {
        if (cmdId == 9 && config.confirmReset) {
            val stationLabel = if (station == "zgoraj") "ZGORNJA POSTAJA" else "SPODNJA POSTAJA"
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Ponastavitev števca")
                .setMessage("Ali res želite ponastaviti števec oseb na 28 ($stationLabel)?")
                .setPositiveButton("Ponastavi") { _, _ ->
                    executeStationCommandNow(station, cmdId)
                }
                .setNegativeButton("Prekliči", null)
                .show()
            return
        }
        executeStationCommandNow(station, cmdId)
    }

    private fun executeStationCommandNow(station: String, cmdId: Int) {
        val targetCard = if (station == "zgoraj") cardZgoraj else cardSpodaj
        targetCard.showSendingIndicator()

        if (config.useCloudRelay) {
            ioExecutor.execute {
                val ok = sendCloudCommand(station, cmdId)
                if (!ok) {
                    updateTopStatus("Napaka pri pošiljanju ukaza v oblak", ThemeColors.AccentCoral)
                } else {
                    updateTopStatus("Ukaz poslan (${station.uppercase(Locale.ROOT)})", ThemeColors.AccentBlue)
                    uiHandler.postDelayed({ fetchLatestCachedCloudState() }, 1200L)
                }
            }
        } else {
            val ip = if (station == "zgoraj") config.ipZgoraj else config.ipSpodaj
            ioExecutor.execute {
                try {
                    val state = DirectLanFunicularClient.executeComplexCommand(ip, cmdId)
                    uiHandler.post {
                        targetCard.applyState(state, forceSyncTime = true)
                    }
                } catch (_: Throwable) {
                    uiHandler.post {
                        targetCard.setOfflineState()
                    }
                }
            }
        }
    }

    private fun pollDirectLanBothStations(forceSyncTime: Boolean) {
        ioExecutor.execute {
            try {
                val stZ = DirectLanFunicularClient.sendCommand(config.ipZgoraj, 80, 0, 1800)
                uiHandler.post { cardZgoraj.applyState(stZ, forceSyncTime) }
            } catch (_: Throwable) {
                uiHandler.post { cardZgoraj.setOfflineState() }
            }
        }
        ioExecutor.execute {
            try {
                val stS = DirectLanFunicularClient.sendCommand(config.ipSpodaj, 80, 0, 1800)
                uiHandler.post { cardSpodaj.applyState(stS, forceSyncTime) }
            } catch (_: Throwable) {
                uiHandler.post { cardSpodaj.setOfflineState() }
            }
        }
    }

    private fun fetchLatestCachedCloudState() {
        ioExecutor.execute {
            var conn: HttpURLConnection? = null
            try {
                if (config.isFirebase) {
                    val url = URL(config.firebaseUrl("state"))
                    conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 6000
                        readTimeout = 6000
                        setRequestProperty("Accept", "application/json")
                    }
                    val body = BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8)).use { it.readText().trim() }
                    if (body.isNotEmpty() && body != "null" && body.startsWith("{")) {
                        applyCloudStatePayload(body, fromCache = true)
                    }
                } else {
                    val url = URL("${config.cloudServer}/${config.stateTopic}/json?poll=1&since=12h")
                    conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 6000
                        readTimeout = 6000
                    }
                    BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8)).use { reader ->
                        var latestMessageJson: String? = null
                        while (true) {
                            val line = reader.readLine() ?: break
                            val trimmed = line.trim()
                            if (trimmed.isEmpty()) continue
                            val obj = JSONObject(trimmed)
                            if (obj.optString("event") == "message") {
                                val msg = obj.optString("message", "")
                                if (msg.isNotEmpty()) latestMessageJson = msg
                            }
                        }
                        if (latestMessageJson != null) {
                            applyCloudStatePayload(latestMessageJson, fromCache = true)
                        }
                    }
                }
            } catch (_: Throwable) {
            } finally {
                try { conn?.disconnect() } catch (_: Throwable) {}
            }
        }
    }

    private fun startCloudStream() {
        streamActive.set(true)
        ioExecutor.execute {
            while (streamActive.get()) {
                var conn: HttpURLConnection? = null
                try {
                    val isFirebase = config.isFirebase
                    val url = if (isFirebase) {
                        URL(config.firebaseUrl("state"))
                    } else {
                        URL("${config.cloudServer}/${config.stateTopic}/json")
                    }
                    conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 10000
                        readTimeout = 75000
                        setRequestProperty(
                            "Accept",
                            if (isFirebase) "text/event-stream" else "application/x-ndjson, application/json"
                        )
                    }
                    activeStreamConnection = conn

                    BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8)).use { reader ->
                        val modeLabel = if (isFirebase) "Firebase" else "Oblak"
                        updateTopStatus(
                            "$modeLabel povezano (${config.cloudChannelId})",
                            ThemeColors.AccentEmerald
                        )
                        var currentSseEvent = ""
                        var isFirstFirebasePut = true
                        while (streamActive.get()) {
                            val line = reader.readLine() ?: break
                            val trimmed = line.trim()
                            if (trimmed.isEmpty()) continue

                            if (isFirebase) {
                                if (trimmed.startsWith("event:", ignoreCase = true)) {
                                    currentSseEvent = trimmed.substring(6).trim()
                                } else if (trimmed.startsWith("data:", ignoreCase = true)) {
                                    val dataStr = trimmed.substring(5).trim()
                                    if ((currentSseEvent.equals("put", ignoreCase = true) ||
                                            currentSseEvent.equals("patch", ignoreCase = true)) &&
                                        dataStr.isNotEmpty() && dataStr != "null"
                                    ) {
                                        val wrapper = JSONObject(dataStr)
                                        val dataObj = wrapper.optJSONObject("data")
                                        if (dataObj != null) {
                                            applyCloudStatePayload(dataObj.toString(), fromCache = isFirstFirebasePut)
                                            isFirstFirebasePut = false
                                        }
                                    }
                                }
                            } else {
                                val obj = JSONObject(trimmed)
                                if (obj.optString("event") == "message") {
                                    val msg = obj.optString("message", "")
                                    if (msg.isNotEmpty()) {
                                        applyCloudStatePayload(msg, fromCache = false)
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Throwable) {
                    if (streamActive.get()) {
                        updateTopStatus("Ponovno povezovanje v oblak...", ThemeColors.AccentAmber)
                        try { Thread.sleep(2500) } catch (_: Throwable) {}
                    }
                } finally {
                    try { conn?.disconnect() } catch (_: Throwable) {}
                }
            }
        }
    }

    private fun stopCloudStream() {
        streamActive.set(false)
        ioExecutor.execute {
            try {
                activeStreamConnection?.disconnect()
            } catch (_: Throwable) {
            } finally {
                activeStreamConnection = null
            }
        }
    }

    private fun requestCloudSync() {
        lastCloudSyncSendMs = SystemClock.elapsedRealtime()
        ioExecutor.execute {
            sendCloudCommand("sync", 0)
        }
    }

    private fun sendCloudCommand(station: String, cmdId: Int): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            val nowMs = System.currentTimeMillis()
            val payload = JSONObject().apply {
                put("id", "$nowMs-${(1000..9999).random()}")
                put("station", station)
                put("cmd", cmdId)
                put("pin", config.cloudPin)
                put("ts", nowMs)
            }.toString()

            val isFirebase = config.isFirebase
            val url = if (isFirebase) {
                URL(config.firebaseUrl("cmd"))
            } else {
                URL("${config.cloudServer}/${config.commandTopic}")
            }
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = if (isFirebase) "PUT" else "POST"
                doOutput = true
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty(
                    "Content-Type",
                    if (isFirebase) "application/json; charset=utf-8" else "text/plain; charset=utf-8"
                )
            }
            val bytes = payload.toByteArray(StandardCharsets.UTF_8)
            conn.outputStream.use { out ->
                out.write(bytes)
                out.flush()
            }
            val code = conn.responseCode
            code in 200..299
        } catch (_: Throwable) {
            false
        } finally {
            try { conn?.disconnect() } catch (_: Throwable) {}
        }
    }

    private fun applyCloudStatePayload(jsonStr: String, fromCache: Boolean = false) {
        try {
            val obj = JSONObject(jsonStr)
            val msgTs = obj.optLong("ts", 0L)
            if (fromCache && msgTs > 0L && msgTs <= lastAppliedStateTs) {
                return
            }
            val ageMs = if (fromCache && msgTs > 0L) {
                (System.currentTimeMillis() - msgTs).coerceAtLeast(0L)
            } else {
                0L
            }
            // Ignore cached messages older than 15 minutes so stale history never overwrites the UI
            if (fromCache && ageMs > 15 * 60 * 1000L) {
                return
            }
            if (msgTs > lastAppliedStateTs) {
                lastAppliedStateTs = msgTs
            }

            val zState = StationState(
                isOnline = obj.optInt("zOn", 0) == 1,
                currentPersons = obj.optInt("zCur", 0),
                maxPersons = obj.optInt("zMax", 28),
                remainingTimeMs = max(0L, obj.optLong("zRem", 0L) - ageMs),
                setTimeMs = max(0L, obj.optLong("zSet", 0L))
            )
            val sState = StationState(
                isOnline = obj.optInt("sOn", 0) == 1,
                currentPersons = obj.optInt("sCur", 0),
                maxPersons = obj.optInt("sMax", 28),
                remainingTimeMs = max(0L, obj.optLong("sRem", 0L) - ageMs),
                setTimeMs = max(0L, obj.optLong("sSet", 0L))
            )

            uiHandler.post {
                val modeLabel = if (config.isFirebase) "Firebase" else "Oblak"
                updateTopStatus(
                    "$modeLabel aktiven (${config.cloudChannelId})",
                    ThemeColors.AccentEmerald
                )
                if (zState.isOnline) {
                    cardZgoraj.applyState(zState, forceSyncTime = true)
                } else {
                    cardZgoraj.setOfflineState()
                }

                if (sState.isOnline) {
                    cardSpodaj.applyState(sState, forceSyncTime = true)
                } else {
                    cardSpodaj.setOfflineState()
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun showSettingsDialog() {
        val scroll = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(14), dp(20), dp(8))
        }

        val modeGroup = RadioGroup(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val rbCloud = RadioButton(this).apply {
            text = "Oblačni most prek PC-ja (deluje od koderkoli - 4G/5G/Wi-Fi)"
            setTextColor(ThemeColors.TextPrimary)
            isChecked = config.useCloudRelay
        }
        val rbLan = RadioButton(this).apply {
            text = "Lokalno omrežje Wi-Fi / VPN (neposredno na 10.12.220.34 / .36)"
            setTextColor(ThemeColors.TextPrimary)
            isChecked = !config.useCloudRelay
        }
        modeGroup.addView(rbCloud)
        modeGroup.addView(rbLan)
        container.addView(modeGroup)

        container.addView(createSettingsLabel("Firebase Realtime Database URL (ali ntfy strežnik):"))
        val edtServer = createSettingsInput(config.cloudServer).apply {
            hint = "https://ime-projekta-default-rtdb.europe-west1.firebasedatabase.app"
        }
        container.addView(edtServer)

        container.addView(createSettingsLabel("Koda kanala (ista kot v PC aplikaciji Vzpenjača):"))
        val edtChannel = createSettingsInput(config.cloudChannelId)
        container.addView(edtChannel)

        container.addView(createSettingsLabel("Varnostni PIN (če je nastavljen na PC-ju):"))
        val edtPin = createSettingsInput(config.cloudPin).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        container.addView(edtPin)

        container.addView(createSettingsLabel("IP Zgornja postaja (za neposredni LAN način):"))
        val edtIpZgoraj = createSettingsInput(config.ipZgoraj)
        container.addView(edtIpZgoraj)

        container.addView(createSettingsLabel("IP Spodnja postaja (za neposredni LAN način):"))
        val edtIpSpodaj = createSettingsInput(config.ipSpodaj)
        container.addView(edtIpSpodaj)

        val chkSound = CheckBox(this).apply {
            text = "Zvočna opozorila (zvončki) na telefonu"
            setTextColor(ThemeColors.TextPrimary)
            isChecked = config.soundEnabled
        }
        container.addView(chkSound)

        val chkConfirmReset = CheckBox(this).apply {
            text = "Potrdi ponastavitev števca oseb"
            setTextColor(ThemeColors.TextPrimary)
            isChecked = config.confirmReset
        }
        container.addView(chkConfirmReset)

        scroll.addView(container)

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Nastavitve povezave")
            .setView(scroll)
            .setPositiveButton("Shrani") { _, _ ->
                config.useCloudRelay = rbCloud.isChecked
                config.cloudChannelId = MobileConfig.sanitizeChannel(edtChannel.text.toString())
                config.cloudPin = edtPin.text.toString().trim()
                config.cloudServer = MobileConfig.normalizeServerUrl(edtServer.text.toString())
                config.ipZgoraj = edtIpZgoraj.text.toString().trim().ifEmpty { "10.12.220.34" }
                config.ipSpodaj = edtIpSpodaj.text.toString().trim().ifEmpty { "10.12.220.36" }
                config.soundEnabled = chkSound.isChecked
                config.confirmReset = chkConfirmReset.isChecked
                MobileChimePlayer.enabled = config.soundEnabled
                config.save()
                restartNetworking()
            }
            .setNegativeButton("Prekliči", null)
            .show()
    }

    private fun createSettingsLabel(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(ThemeColors.TextSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(10), 0, dp(2))
        }
    }

    private fun createSettingsInput(initialValue: String): EditText {
        return EditText(this).apply {
            setText(initialValue)
            setTextColor(ThemeColors.TextPrimary)
            setHintTextColor(ThemeColors.TextMuted)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setSingleLine(true)
        }
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).roundToInt()
    }
}

/**
 * Compact icon toggle pill button for station header quick settings (Mute chimes / Dim view),
 * matching HeaderQuickToggleButton in the PC version.
 */
class HeaderQuickToggleButton(
    context: Context,
    private val isMuteButton: Boolean,
    initialActive: Boolean,
    private val onToggled: (Boolean) -> Unit
) : View(context) {

    var isActive: Boolean = initialActive
        set(value) {
            field = value
            updateVisuals()
            invalidate()
        }

    private var isPressedState = false
    private val bgDrawable = GradientDrawable()
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val iconPath = Path()
    private val arcRect1 = RectF()
    private val arcRect2 = RectF()

    init {
        background = bgDrawable
        updateVisuals()

        setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isPressedState = true
                    updateVisuals()
                    invalidate()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    isPressedState = false
                    isActive = !isActive
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onToggled(isActive)
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    isPressedState = false
                    updateVisuals()
                    invalidate()
                    true
                }
                else -> false
            }
        }
    }

    private fun updateVisuals() {
        bgDrawable.cornerRadius = dpF(5f)
        val strokePx = max(1, dpF(1f).roundToInt())
        if (isActive) {
            bgDrawable.setColor(if (isPressedState) ThemeColors.BtnBg else ThemeColors.BtnPressedBg)
            bgDrawable.setStroke(strokePx, if (isPressedState) ThemeColors.TextPrimary else ThemeColors.TextSecondary)
            iconPaint.color = ThemeColors.TextPrimary
        } else {
            bgDrawable.setColor(if (isPressedState) ThemeColors.BtnPressedBg else ThemeColors.BtnBg)
            bgDrawable.setStroke(strokePx, if (isPressedState) ThemeColors.AccentBlue else ThemeColors.BtnBorder)
            iconPaint.color = if (isPressedState) ThemeColors.TextPrimary else ThemeColors.TextSecondary
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val iconSize = min(w, h) * 0.72f
        val scale = iconSize / 16f
        val ox = (w - 16f * scale) * 0.5f
        val oy = (h - 16f * scale) * 0.5f

        iconPaint.strokeWidth = max(1.5f, 1.5f * scale)
        iconPath.reset()

        if (isMuteButton) {
            // Speaker polygon: M 2,6 L 5,6 L 8.8,2.6 L 8.8,13.4 L 5,10 L 2,10 Z
            iconPath.moveTo(ox + 2f * scale, oy + 6f * scale)
            iconPath.lineTo(ox + 5f * scale, oy + 6f * scale)
            iconPath.lineTo(ox + 8.8f * scale, oy + 2.6f * scale)
            iconPath.lineTo(ox + 8.8f * scale, oy + 13.4f * scale)
            iconPath.lineTo(ox + 5f * scale, oy + 10f * scale)
            iconPath.lineTo(ox + 2f * scale, oy + 10f * scale)
            iconPath.close()
            canvas.drawPath(iconPath, iconPaint)

            if (isActive) {
                // Muted X: M 11.2,5.5 L 15.0,10.5 M 15.0,5.5 L 11.2,10.5
                canvas.drawLine(
                    ox + 11.2f * scale, oy + 5.5f * scale,
                    ox + 15.0f * scale, oy + 10.5f * scale,
                    iconPaint
                )
                canvas.drawLine(
                    ox + 15.0f * scale, oy + 5.5f * scale,
                    ox + 11.2f * scale, oy + 10.5f * scale,
                    iconPaint
                )
            } else {
                // Sound waves
                arcRect1.set(
                    ox + 7.6f * scale, oy + 4.8f * scale,
                    ox + 12.2f * scale, oy + 11.2f * scale
                )
                canvas.drawArc(arcRect1, -48f, 96f, false, iconPaint)

                arcRect2.set(
                    ox + 6.8f * scale, oy + 2.8f * scale,
                    ox + 14.5f * scale, oy + 13.2f * scale
                )
                canvas.drawArc(arcRect2, -52f, 104f, false, iconPaint)
            }
        } else {
            // Crescent Moon matching GeomDimMoon in PC version
            val cx = ox + 8.2f * scale
            val cy = oy + 8.0f * scale
            val rOuter = 4.9f * scale
            iconPath.addCircle(cx, cy, rOuter, Path.Direction.CW)

            val cutPath = Path()
            cutPath.addCircle(cx + 2.8f * scale, cy - 2.0f * scale, 4.1f * scale, Path.Direction.CW)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                iconPath.op(cutPath, Path.Op.DIFFERENCE)
            }
            canvas.drawPath(iconPath, iconPaint)
        }
    }

    private fun dpF(value: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            resources.displayMetrics
        )
    }
}

class StationCardView(
    context: Context,
    val stationId: String,
    stationTitle: String,
    initialMuted: Boolean,
    initialDimmed: Boolean,
    initialShowIntervalBg: Boolean,
    private val onQuickSettingsChanged: (muted: Boolean, dimmed: Boolean, showIntervalBg: Boolean) -> Unit,
    private val onOperatorCommand: (Int) -> Unit
) : LinearLayout(context) {

    private val cardBgDrawable: GradientDrawable
    private val lblStation: TextView
    private val statusPanel: LinearLayout
    private val statusDot: View
    private val statusText: TextView
    private val btnMute: HeaderQuickToggleButton
    private val btnDim: HeaderQuickToggleButton
    private val contentContainer: LinearLayout

    private val txtTimerValue: TextView
    private val txtSetTimeValue: TextView
    private val txtBgIntervalValue: TextView
    private val timerProgressTrack: FrameLayout
    private val timerProgressFill: View

    private val txtPersonsCurrent: TextView
    private val txtPersonsMax: TextView
    private val personsProgressTrack: FrameLayout
    private val personsProgressFill: View

    private var isOnline = false
    private var remainingMs = 0L
    private var setMs = 0L
    private var currentPersons = 0
    private var maxPersons = 28
    private var isMuted = initialMuted
    private var isDimmed = initialDimmed
    private var showIntervalBg = initialShowIntervalBg
    private var lastTimeZone = -1
    private var lastPersonsZone = -1
    private var hasInitialTelemetry = false

    init {
        orientation = VERTICAL
        val pad = dp(12)
        setPadding(pad, dp(10), pad, dp(12))
        cardBgDrawable = GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            setColor(ThemeColors.CardBg)
            setStroke(dp(1), ThemeColors.CardBorder)
        }
        background = cardBgDrawable

        // 1. Station Header (with Mute speaker icon, Dim crescent moon icon, and connection status)
        val headerRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), 0, dp(2), dp(8))
        }

        lblStation = TextView(context).apply {
            text = stationTitle
            setTextColor(ThemeColors.TextPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        btnMute = HeaderQuickToggleButton(
            context = context,
            isMuteButton = true,
            initialActive = isMuted
        ) { muted ->
            isMuted = muted
            if (!isMuted && MobileChimePlayer.enabled) {
                MobileChimePlayer.playTimeSingle()
            }
            onQuickSettingsChanged(isMuted, isDimmed, showIntervalBg)
        }.apply {
            layoutParams = LayoutParams(dp(30), dp(24)).apply {
                rightMargin = dp(6)
            }
        }

        btnDim = HeaderQuickToggleButton(
            context = context,
            isMuteButton = false,
            initialActive = isDimmed
        ) { dimmed ->
            isDimmed = dimmed
            applyDimVisuals()
            onQuickSettingsChanged(isMuted, isDimmed, showIntervalBg)
        }.apply {
            layoutParams = LayoutParams(dp(30), dp(24)).apply {
                rightMargin = dp(12)
            }
        }

        statusPanel = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        statusDot = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ThemeColors.AccentAmber)
            }
            layoutParams = LayoutParams(dp(7), dp(7)).apply {
                rightMargin = dp(6)
            }
        }

        statusText = TextView(context).apply {
            text = "Povezovanje"
            setTextColor(ThemeColors.TextSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
        }

        statusPanel.addView(statusDot)
        statusPanel.addView(statusText)

        headerRow.addView(lblStation)
        headerRow.addView(btnMute)
        headerRow.addView(btnDim)
        headerRow.addView(statusPanel)
        addView(headerRow)

        // 2. Content Container (dims to 0.18 opacity when Dim is active, just like PC version)
        contentContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        // --- TIME SECTION ---
        val timeDisplayBox = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(ThemeColors.SubCardBg)
                setStroke(dp(1), ThemeColors.Divider)
            }
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(6)
            }
        }

        // Background Interval Watermark (80% width, Opacity 0.20, matching _bgIntervalGrid in PC version)
        txtBgIntervalValue = TextView(context).apply {
            text = "--:--"
            setTextColor(ThemeColors.TextSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 52f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.CENTER
            includeFontPadding = false
            alpha = 0.20f
            visibility = if (showIntervalBg) View.VISIBLE else View.GONE
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        }
        timeDisplayBox.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            val boxW = v.width
            if (boxW > 0) {
                val targetW = boxW * 0.80f
                val sample = "00:00"
                txtBgIntervalValue.paint.textSize = 100f
                val measured = txtBgIntervalValue.paint.measureText(sample)
                if (measured > 0f) {
                    txtBgIntervalValue.setTextSize(TypedValue.COMPLEX_UNIT_PX, 100f * (targetW / measured))
                }
            }
        }
        timeDisplayBox.addView(txtBgIntervalValue)

        val timeInnerCol = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(10))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val timeTopRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val lblTimeHeader = TextView(context).apply {
            text = "ČAS DO ODHODA"
            setTextColor(ThemeColors.TextSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        txtSetTimeValue = TextView(context).apply {
            text = "Interval: --:--"
            setTextColor(ThemeColors.TextSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setOnClickListener {
                showIntervalBg = !showIntervalBg
                txtBgIntervalValue.visibility = if (showIntervalBg) View.VISIBLE else View.GONE
                onQuickSettingsChanged(isMuted, isDimmed, showIntervalBg)
            }
        }
        timeTopRow.addView(lblTimeHeader)
        timeTopRow.addView(txtSetTimeValue)

        txtTimerValue = TextView(context).apply {
            text = "--:--"
            setTextColor(ThemeColors.TextPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 42f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(4))
        }

        timerProgressTrack = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(2).toFloat()
                setColor(ThemeColors.TrackBg)
            }
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(5))
        }
        timerProgressFill = View(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(2).toFloat()
                setColor(ThemeColors.AccentBtnMuted)
            }
            layoutParams = FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        timerProgressTrack.addView(timerProgressFill)
        timerProgressTrack.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateProgressBars() }

        timeInnerCol.addView(timeTopRow)
        timeInnerCol.addView(txtTimerValue)
        timeInnerCol.addView(timerProgressTrack)
        timeDisplayBox.addView(timeInnerCol)
        contentContainer.addView(timeDisplayBox)

        // Time Operator Buttons (2 rows x 3 columns)
        val timeBtnRow1 = LinearLayout(context).apply { orientation = HORIZONTAL }
        timeBtnRow1.addView(createActionButton(2, "+ 1 min", ThemeColors.AccentEmerald))
        timeBtnRow1.addView(createActionButton(3, "+ 5 min", ThemeColors.AccentEmerald))
        timeBtnRow1.addView(createActionButton(11, "10 min", ThemeColors.AccentBtnMuted))
        contentContainer.addView(timeBtnRow1)

        val timeBtnRow2 = LinearLayout(context).apply { orientation = HORIZONTAL }
        timeBtnRow2.addView(createActionButton(5, "− 1 min", ThemeColors.AccentRed))
        timeBtnRow2.addView(createActionButton(6, "− 5 min", ThemeColors.AccentRed))
        timeBtnRow2.addView(createActionButton(10, "6 min", ThemeColors.AccentBtnMuted))
        contentContainer.addView(timeBtnRow2)

        // Divider between Time and Persons
        val divider = View(context).apply {
            setBackgroundColor(ThemeColors.Divider)
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                topMargin = dp(8)
                bottomMargin = dp(8)
            }
        }
        contentContainer.addView(divider)

        // --- PERSONS SECTION ---
        val personsDisplayBox = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(ThemeColors.SubCardBg)
                setStroke(dp(1), ThemeColors.Divider)
            }
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(6)
            }
        }

        val lblPersonsHeader = TextView(context).apply {
            text = "ŠTEVILO OSEB"
            setTextColor(ThemeColors.TextSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }

        val personsNumberRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(4))
        }
        txtPersonsCurrent = TextView(context).apply {
            text = "--"
            setTextColor(ThemeColors.TextPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 42f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        txtPersonsMax = TextView(context).apply {
            text = " / --"
            setTextColor(ThemeColors.TextMuted)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }
        personsNumberRow.addView(txtPersonsCurrent)
        personsNumberRow.addView(txtPersonsMax)

        personsProgressTrack = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(2).toFloat()
                setColor(ThemeColors.TrackBg)
            }
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(5))
        }
        personsProgressFill = View(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(2).toFloat()
                setColor(ThemeColors.AccentBtnMuted)
            }
            layoutParams = FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        personsProgressTrack.addView(personsProgressFill)
        personsProgressTrack.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateProgressBars() }

        personsDisplayBox.addView(lblPersonsHeader)
        personsDisplayBox.addView(personsNumberRow)
        personsDisplayBox.addView(personsProgressTrack)
        contentContainer.addView(personsDisplayBox)

        // Persons Operator Buttons: Left column (+1 oseba / -1 oseba), Right column spanning rows 1..2 (Ponastavi števec)
        val personsButtonsRow = LinearLayout(context).apply { orientation = HORIZONTAL }
        val personsLeftCol = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnPlusPerson = createActionButton(7, "+ 1 oseba", ThemeColors.AccentEmerald).apply {
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply {
                setMargins(dp(3), dp(3), dp(3), dp(3))
            }
        }
        val btnMinusPerson = createActionButton(8, "− 1 oseba", ThemeColors.AccentRed).apply {
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply {
                setMargins(dp(3), dp(3), dp(3), dp(3))
            }
        }
        personsLeftCol.addView(btnPlusPerson)
        personsLeftCol.addView(btnMinusPerson)

        val btnResetCounter = createActionButton(9, "Ponastavi\nštevec", ThemeColors.AccentBtnMuted).apply {
            layoutParams = LayoutParams(0, dp(94), 1f).apply {
                setMargins(dp(3), dp(3), dp(3), dp(3))
            }
        }
        personsButtonsRow.addView(personsLeftCol)
        personsButtonsRow.addView(btnResetCounter)
        contentContainer.addView(personsButtonsRow)

        addView(contentContainer)
        applyDimVisuals()
    }

    private fun applyDimVisuals() {
        contentContainer.alpha = if (isDimmed) 0.18f else 1.0f
        lblStation.alpha = if (isDimmed) 0.38f else 1.0f
        statusPanel.alpha = if (isDimmed) 0.38f else 1.0f
        cardBgDrawable.setColor(if (isDimmed) ThemeColors.SubCardBg else ThemeColors.CardBg)
    }

    private fun createActionButton(commandId: Int, label: String, accentColor: Int): View {
        val container = FrameLayout(context).apply {
            layoutParams = LayoutParams(0, dp(44), 1f).apply {
                setMargins(dp(3), dp(3), dp(3), dp(3))
            }
        }

        val normalBg = GradientDrawable().apply {
            cornerRadius = dp(7).toFloat()
            setColor(ThemeColors.BtnBg)
            setStroke(dp(1), ThemeColors.BtnBorder)
        }
        val pressedBg = GradientDrawable().apply {
            cornerRadius = dp(7).toFloat()
            setColor(ThemeColors.BtnPressedBg)
            setStroke(dp(1), ThemeColors.AccentBlue)
        }
        container.background = normalBg

        val accentBar = View(context).apply {
            background = GradientDrawable().apply {
                cornerRadii = floatArrayOf(
                    dp(4).toFloat(), dp(4).toFloat(),
                    0f, 0f,
                    0f, 0f,
                    dp(4).toFloat(), dp(4).toFloat()
                )
                setColor(accentColor)
            }
            layoutParams = FrameLayout.LayoutParams(dp(4), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START)
        }

        val txt = TextView(context).apply {
            text = label
            setTextColor(ThemeColors.TextPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        container.addView(accentBar)
        container.addView(txt)

        container.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.background = pressedBg
                    true
                }
                MotionEvent.ACTION_UP -> {
                    v.background = normalBg
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onOperatorCommand(commandId)
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.background = normalBg
                    true
                }
                else -> false
            }
        }
        return container
    }

    fun showSendingIndicator() {
        statusText.text = "Ukaz..."
        statusText.setTextColor(ThemeColors.AccentBlue)
        (statusDot.background as? GradientDrawable)?.setColor(ThemeColors.AccentBlue)
    }

    fun onLocalTick(deltaMs: Long) {
        if (isOnline && remainingMs > 0L) {
            remainingMs = max(0L, remainingMs - deltaMs)
            renderTime(suppressChime = false)
        }
    }

    fun applyState(state: StationState, forceSyncTime: Boolean) {
        val isFirstLoad = !hasInitialTelemetry
        val wasOffline = !isOnline
        isOnline = true

        (statusDot.background as? GradientDrawable)?.setColor(ThemeColors.AccentEmerald)
        statusText.text = "Povezano"
        statusText.setTextColor(ThemeColors.TextSecondary)

        setMs = state.setTimeMs
        if (forceSyncTime || wasOffline || abs(state.remainingTimeMs - remainingMs) > 2000L) {
            remainingMs = state.remainingTimeMs
        }
        currentPersons = state.currentPersons
        maxPersons = state.maxPersons

        renderTime(suppressChime = isFirstLoad)
        renderPersons(suppressChime = isFirstLoad)
        hasInitialTelemetry = true
    }

    fun setOfflineState() {
        isOnline = false
        (statusDot.background as? GradientDrawable)?.setColor(ThemeColors.AccentCoral)
        statusText.text = "Ni povezave"
        statusText.setTextColor(ThemeColors.AccentCoral)

        txtTimerValue.text = "--:--"
        txtTimerValue.setTextColor(ThemeColors.TextMuted)
        txtBgIntervalValue.text = "--:--"
        txtPersonsCurrent.text = "--"
        txtPersonsCurrent.setTextColor(ThemeColors.TextMuted)
        txtPersonsMax.text = " / --"
        setFillWidth(timerProgressFill, 0)
        setFillWidth(personsProgressFill, 0)
    }

    private fun renderTime(suppressChime: Boolean) {
        if (!isOnline) return
        val formattedTime = formatMinSec(remainingMs)
        val formattedInterval = formatMinSec(setMs)
        txtTimerValue.text = formattedTime
        txtSetTimeValue.text = "Interval: $formattedInterval"
        txtBgIntervalValue.text = formattedInterval

        val timeZone: Int
        when {
            remainingMs <= 0L || formattedTime == "00:00" -> {
                timeZone = 2
                txtTimerValue.setTextColor(ThemeColors.AccentRed)
                (timerProgressFill.background as? GradientDrawable)?.setColor(ThemeColors.AccentRed)
            }
            remainingMs <= 60_000L -> {
                timeZone = 1
                txtTimerValue.setTextColor(ThemeColors.AccentAmber)
                (timerProgressFill.background as? GradientDrawable)?.setColor(ThemeColors.AccentAmber)
            }
            else -> {
                timeZone = 0
                txtTimerValue.setTextColor(ThemeColors.TextPrimary)
                (timerProgressFill.background as? GradientDrawable)?.setColor(ThemeColors.AccentBtnMuted)
            }
        }

        if (!suppressChime && !isMuted && lastTimeZone >= 0 && timeZone > lastTimeZone) {
            if (timeZone == 1) MobileChimePlayer.playTimeSingle()
            else if (timeZone == 2) MobileChimePlayer.playTimeTriple()
        }
        lastTimeZone = timeZone
        updateProgressBars()
    }

    private fun renderPersons(suppressChime: Boolean) {
        if (!isOnline) return
        txtPersonsCurrent.text = String.format(Locale.US, "%02d", currentPersons)
        txtPersonsMax.text = String.format(Locale.US, " / %02d", maxPersons)

        val personsZone: Int
        when {
            maxPersons > 0 && currentPersons >= maxPersons -> {
                personsZone = 2
                txtPersonsCurrent.setTextColor(ThemeColors.AccentCoral)
                (personsProgressFill.background as? GradientDrawable)?.setColor(ThemeColors.AccentCoral)
            }
            maxPersons > 0 && currentPersons.toDouble() / maxPersons >= 0.85 -> {
                personsZone = 1
                txtPersonsCurrent.setTextColor(ThemeColors.AccentAmber)
                (personsProgressFill.background as? GradientDrawable)?.setColor(ThemeColors.AccentAmber)
            }
            else -> {
                personsZone = 0
                txtPersonsCurrent.setTextColor(ThemeColors.TextPrimary)
                (personsProgressFill.background as? GradientDrawable)?.setColor(ThemeColors.AccentBtnMuted)
            }
        }

        if (!suppressChime && !isMuted && lastPersonsZone >= 0 && personsZone > lastPersonsZone) {
            if (personsZone == 1) MobileChimePlayer.playPersonsSingle()
            else if (personsZone == 2) MobileChimePlayer.playPersonsTriple()
        }
        lastPersonsZone = personsZone
        updateProgressBars()
    }

    private fun updateProgressBars() {
        if (!isOnline) return

        val timeTrackW = timerProgressTrack.width
        if (timeTrackW > 0) {
            val ratio = if (setMs > 0L) (remainingMs.toDouble() / setMs).coerceIn(0.0, 1.0) else 0.0
            setFillWidth(timerProgressFill, (timeTrackW * ratio).roundToInt())
        }

        val personsTrackW = personsProgressTrack.width
        if (personsTrackW > 0) {
            val ratio = if (maxPersons > 0) (currentPersons.toDouble() / maxPersons).coerceIn(0.0, 1.0) else 0.0
            setFillWidth(personsProgressFill, (personsTrackW * ratio).roundToInt())
        }
    }

    private fun setFillWidth(view: View, widthPx: Int) {
        val lp = view.layoutParams
        if (lp.width != widthPx) {
            lp.width = widthPx
            view.layoutParams = lp
        }
    }

    private fun formatMinSec(ms: Long): String {
        val clamped = max(0L, ms)
        val totalSeconds = (clamped + 499L) / 1000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).roundToInt()
    }
}
