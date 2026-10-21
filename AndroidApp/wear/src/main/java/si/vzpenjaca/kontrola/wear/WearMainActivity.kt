package si.vzpenjaca.kontrola.wear

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
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tanh

data class StationState(
    val isOnline: Boolean = false,
    val currentPersons: Int = 0,
    val maxPersons: Int = 28,
    val remainingTimeMs: Long = 0L,
    val setTimeMs: Long = 0L
)

data class SavedChannel(
    val code: String,
    val label: String = "",
    val pin: String = ""
)

class MobileConfig(private val context: Context) {
    private val prefs = context.getSharedPreferences("vzpenjaca_prefs", Context.MODE_PRIVATE)

    var useCloudRelay: Boolean = true
    var cloudServer: String = "https://vzpenjaca-kontrola-default-rtdb.europe-west1.firebasedatabase.app"
    var cloudChannelId: String = "vzpenjaca-3436"
    var cloudPin: String = ""
    var savedChannels: MutableList<SavedChannel> = mutableListOf()
    var ipZgoraj: String = "10.12.220.34"
    var ipSpodaj: String = "10.12.220.36"
    var soundEnabled: Boolean = true
    var voiceAnnouncementEnabled: Boolean = true
    var voiceAnnouncementTemplate: String = DEFAULT_VOICE_TEMPLATE
    var voiceAnnouncementGain: Double = 1.5
    var voiceAnnouncementVoice: String = DEFAULT_NEURAL_VOICE
    var muteZgoraj: Boolean = false
    var muteSpodaj: Boolean = false
    var dimZgoraj: Boolean = false
    var dimSpodaj: Boolean = false
    var showIntervalBgZgoraj: Boolean = true
    var showIntervalBgSpodaj: Boolean = true
    var showCapacityZgoraj: Boolean = true
    var showCapacitySpodaj: Boolean = true
    var rideDurationSec: Int = 90
    var funicularMotionMode: Int = 1 // 0 = Linear, 1 = Graph-based (acceleration/deceleration)
    var autoLockTimeoutSec: Int = 0 // 0 = unlocked until manually locked; >0 = auto-lock after N seconds

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
        if (prefs.contains("voiceAnnouncementEnabled")) voiceAnnouncementEnabled = prefs.getBoolean("voiceAnnouncementEnabled", true)
        prefs.getString("voiceAnnouncementTemplate", null)?.takeIf { it.isNotBlank() }?.let {
            voiceAnnouncementTemplate = it.trim()
        }
        if (prefs.contains("voiceAnnouncementGain")) {
            val g = prefs.getFloat("voiceAnnouncementGain", 1.5f).toDouble()
            if (g in 0.5..5.0) voiceAnnouncementGain = g
        }
        prefs.getString("voiceAnnouncementVoice", null)?.takeIf { it.isNotBlank() }?.let {
            voiceAnnouncementVoice = it.trim()
        }
        if (prefs.contains("muteZgoraj")) muteZgoraj = prefs.getBoolean("muteZgoraj", false)
        if (prefs.contains("muteSpodaj")) muteSpodaj = prefs.getBoolean("muteSpodaj", false)
        if (prefs.contains("dimZgoraj")) dimZgoraj = prefs.getBoolean("dimZgoraj", false)
        if (prefs.contains("dimSpodaj")) dimSpodaj = prefs.getBoolean("dimSpodaj", false)
        if (prefs.contains("showIntervalBgZgoraj")) showIntervalBgZgoraj = prefs.getBoolean("showIntervalBgZgoraj", true)
        if (prefs.contains("showIntervalBgSpodaj")) showIntervalBgSpodaj = prefs.getBoolean("showIntervalBgSpodaj", true)
        if (prefs.contains("showCapacityZgoraj")) showCapacityZgoraj = prefs.getBoolean("showCapacityZgoraj", true)
        if (prefs.contains("showCapacitySpodaj")) showCapacitySpodaj = prefs.getBoolean("showCapacitySpodaj", true)
        if (prefs.contains("rideDurationSec")) rideDurationSec = prefs.getInt("rideDurationSec", 90).coerceIn(10, 600)
        if (prefs.contains("funicularMotionMode")) {
            val m = prefs.getInt("funicularMotionMode", 1)
            funicularMotionMode = if (m == 0) 0 else 1
        }
        if (prefs.contains("autoLockTimeoutSec")) {
            val v = prefs.getInt("autoLockTimeoutSec", 0)
            autoLockTimeoutSec = if (v in listOf(0, 5, 10, 15, 30, 60)) v else 0
        }

        savedChannels.clear()
        prefs.getString("savedChannelsJson", null)?.takeIf { it.isNotBlank() }?.let { rawJson ->
            try {
                val arr = JSONArray(rawJson)
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val code = sanitizeChannel(obj.optString("code", ""))
                    val label = obj.optString("label", "").trim()
                    val pin = obj.optString("pin", "").trim()
                    if (code.isNotBlank() && savedChannels.none { it.code.equals(code, ignoreCase = true) }) {
                        savedChannels.add(SavedChannel(code, label, pin))
                    }
                }
            } catch (_: Throwable) {
            }
        }
        if (savedChannels.none { it.code.equals(cloudChannelId, ignoreCase = true) }) {
            savedChannels.add(0, SavedChannel(cloudChannelId, "", cloudPin))
        }
    }

    fun upsertSavedChannel(rawCode: String, rawLabel: String = "", rawPin: String = "") {
        val code = sanitizeChannel(rawCode)
        val label = rawLabel.trim()
        val pin = rawPin.trim()
        val idx = savedChannels.indexOfFirst { it.code.equals(code, ignoreCase = true) }
        if (idx >= 0) {
            val existing = savedChannels[idx]
            savedChannels[idx] = SavedChannel(
                code = code,
                label = if (label.isNotEmpty()) label else existing.label,
                pin = if (pin.isNotEmpty()) pin else existing.pin
            )
        } else {
            savedChannels.add(SavedChannel(code, label, pin))
        }
    }

    fun save() {
        val arr = JSONArray()
        for (ch in savedChannels) {
            arr.put(
                JSONObject().apply {
                    put("code", sanitizeChannel(ch.code))
                    put("label", ch.label.trim())
                    put("pin", ch.pin.trim())
                }
            )
        }
        prefs.edit()
            .putBoolean("useCloudRelay", useCloudRelay)
            .putString("cloudServer", cloudServer.trimEnd('/'))
            .putString("cloudChannelId", sanitizeChannel(cloudChannelId))
            .putString("cloudPin", cloudPin.trim())
            .putString("savedChannelsJson", arr.toString())
            .putString("ipZgoraj", ipZgoraj.trim())
            .putString("ipSpodaj", ipSpodaj.trim())
            .putBoolean("soundEnabled", soundEnabled)
            .putBoolean("voiceAnnouncementEnabled", voiceAnnouncementEnabled)
            .putString("voiceAnnouncementTemplate", voiceAnnouncementTemplate.trim().ifEmpty { DEFAULT_VOICE_TEMPLATE })
            .putFloat("voiceAnnouncementGain", voiceAnnouncementGain.toFloat())
            .putString("voiceAnnouncementVoice", voiceAnnouncementVoice)
            .putBoolean("muteZgoraj", muteZgoraj)
            .putBoolean("muteSpodaj", muteSpodaj)
            .putBoolean("dimZgoraj", dimZgoraj)
            .putBoolean("dimSpodaj", dimSpodaj)
            .putBoolean("showIntervalBgZgoraj", showIntervalBgZgoraj)
            .putBoolean("showIntervalBgSpodaj", showIntervalBgSpodaj)
            .putBoolean("showCapacityZgoraj", showCapacityZgoraj)
            .putBoolean("showCapacitySpodaj", showCapacitySpodaj)
            .putInt("rideDurationSec", rideDurationSec.coerceIn(10, 600))
            .putInt("funicularMotionMode", if (funicularMotionMode == 0) 0 else 1)
            .putInt("autoLockTimeoutSec", autoLockTimeoutSec)
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
        const val DEFAULT_VOICE_TEMPLATE = "Vzpenjača je odpeljala. Naslednja vožnja je čez {minute}."
        const val DEFAULT_NEURAL_VOICE = "google_tts"
        const val ANDROID_TTS_VOICE = "android_tts"

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
    val AccentEmeraldActiveBg = Color.parseColor("#0E2923")
    val AccentAmber = Color.parseColor("#F59E0B")
    val AccentAmberBg = Color.parseColor("#2B2111")
    val AccentCoral = Color.parseColor("#EF4444")
    val AccentRed = Color.parseColor("#EF4444")

    val BtnBg = Color.parseColor("#1C2638")
    val BtnPressedBg = Color.parseColor("#151D2B")
    val BtnBorder = Color.parseColor("#2E3D56")
    val TrackBg = Color.parseColor("#1C2638")
}

object MobileChimePlayer {
    var enabled: Boolean = true
    private var appContext: Context? = null
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

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun playTimeSingle() {
        vibrateWatch(single = true)
        playPcm(pcmTimeSingle, 44100)
    }

    fun playTimeTriple() {
        vibrateWatch(single = false)
        playPcm(pcmTimeTriple, 44100)
    }

    fun playPersonsSingle() {
        vibrateWatch(single = true)
        playPcm(pcmPersonsSingle, 44100)
    }

    fun playPersonsTriple() {
        vibrateWatch(single = false)
        playPcm(pcmPersonsTriple, 44100)
    }

    private fun vibrateWatch(single: Boolean) {
        if (!enabled) return
        val ctx = appContext ?: return
        try {
            @Suppress("DEPRECATION")
            val vib = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (!vib.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (single) {
                    vib.vibrate(VibrationEffect.createOneShot(90L, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0L, 85L, 65L, 85L, 65L, 120L), -1))
                }
            } else {
                @Suppress("DEPRECATION")
                if (single) vib.vibrate(90L) else vib.vibrate(longArrayOf(0L, 85L, 65L, 85L, 65L, 120L), -1)
            }
        } catch (_: Throwable) {
        }
    }

    fun playSpeechPcmSync(samples: ShortArray, sampleRate: Int): Boolean {
        if (samples.isEmpty()) return false
        return playPcmSync(samples, sampleRate, isSpeech = true)
    }

    private fun playPcm(samples: ShortArray, sampleRate: Int) {
        if (!enabled || samples.isEmpty()) return
        executor.execute {
            playPcmSync(samples, sampleRate, isSpeech = false)
        }
    }

    private fun playPcmSync(samples: ShortArray, sampleRate: Int, isSpeech: Boolean): Boolean {
        if (samples.isEmpty()) return false
        var track: AudioTrack? = null
        return try {
            if (isSpeech) {
                val minBuf = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(4096)
                val streamBufBytes = max(minBuf * 2, 16384)
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(streamBufBytes)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                track.setVolume(1.0f)
                track.play()
                var offset = 0
                val chunkLen = 4096
                while (offset < samples.size) {
                    val toWrite = min(chunkLen, samples.size - offset)
                    val written = track.write(samples, offset, toWrite, AudioTrack.WRITE_BLOCKING)
                    if (written <= 0) break
                    offset += written
                }
                Thread.sleep(110L)
                track.stop()
                offset > 0
            } else {
                val bufferBytes = samples.size * 2
                track = AudioTrack.Builder()
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
                track.write(samples, 0, samples.size)
                track.play()
                val durationMs = (samples.size * 1000L) / max(1, sampleRate) + 90L
                Thread.sleep(durationMs)
                track.stop()
                true
            }
        } catch (_: Throwable) {
            false
        } finally {
            try { track?.release() } catch (_: Throwable) {}
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

object MobileVoiceAnnouncer {
    var voiceEnabled: Boolean = true
    var voiceTemplate: String = MobileConfig.DEFAULT_VOICE_TEMPLATE
    var voiceGain: Double = 1.5
    var neuralVoiceName: String = MobileConfig.DEFAULT_NEURAL_VOICE

    private val synthExecutor = Executors.newSingleThreadExecutor()
    @Volatile
    private var tts: TextToSpeech? = null
    @Volatile
    private var ttsReady: Boolean = false
    @Volatile
    private var ttsInitializing: Boolean = false
    @Volatile
    private var appContext: Context? = null
    @Volatile
    private var lastDepartureSpeechMs: Long = 0L

    private class SynthWaiter {
        val latch = CountDownLatch(1)
        val success = AtomicBoolean(false)
    }

    private val pendingWaiters = HashMap<String, SynthWaiter>()

    fun init(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        initAndroidTts(ctx)
        prewarmCommonAnnouncements()
    }

    fun prewarmCommonAnnouncements() {
        val ctx = appContext ?: return
        val tpl = voiceTemplate
        synthExecutor.execute {
            for (mins in intArrayOf(6, 10)) {
                try {
                    val text = buildAnnouncementText(tpl, mins)
                    getOrFetchGoogleMp3(ctx, text)
                } catch (_: Throwable) {
                }
            }
        }
    }

    @Synchronized
    private fun initAndroidTts(ctx: Context) {
        if (ttsReady || ttsInitializing) return
        ttsInitializing = true
        try {
            var googleEngineRef: TextToSpeech? = null
            val googleListener = TextToSpeech.OnInitListener { status ->
                if (status == TextToSpeech.SUCCESS && googleEngineRef != null) {
                    onEngineReady(googleEngineRef!!)
                } else {
                    try { googleEngineRef?.shutdown() } catch (_: Throwable) {}
                    initDefaultAndroidTts(ctx)
                }
            }
            googleEngineRef = try {
                TextToSpeech(ctx, googleListener, "com.google.android.tts")
            } catch (_: Throwable) {
                null
            }
            if (googleEngineRef == null) {
                initDefaultAndroidTts(ctx)
            }
        } catch (_: Throwable) {
            ttsInitializing = false
        }
    }

    private fun initDefaultAndroidTts(ctx: Context) {
        try {
            var defaultEngineRef: TextToSpeech? = null
            val defaultListener = TextToSpeech.OnInitListener { status ->
                if (status == TextToSpeech.SUCCESS && defaultEngineRef != null) {
                    onEngineReady(defaultEngineRef!!)
                } else {
                    try { defaultEngineRef?.shutdown() } catch (_: Throwable) {}
                    ttsInitializing = false
                }
            }
            defaultEngineRef = TextToSpeech(ctx, defaultListener)
        } catch (_: Throwable) {
            ttsInitializing = false
        }
    }

    private fun onEngineReady(engine: TextToSpeech) {
        tts = engine
        configureSlovenianVoice(engine)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                if (utteranceId == null) return
                val waiter = synchronized(pendingWaiters) { pendingWaiters.remove(utteranceId) }
                if (waiter != null) {
                    waiter.success.set(true)
                    waiter.latch.countDown()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                markWaiterFailed(utteranceId)
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                markWaiterFailed(utteranceId)
            }
        })
        ttsReady = true
        ttsInitializing = false
    }

    private fun markWaiterFailed(utteranceId: String?) {
        if (utteranceId == null) return
        val waiter = synchronized(pendingWaiters) { pendingWaiters.remove(utteranceId) }
        if (waiter != null) {
            waiter.success.set(false)
            waiter.latch.countDown()
        }
    }

    private fun configureSlovenianVoice(engine: TextToSpeech) {
        try {
            engine.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            val slLocale = Locale("sl", "SI")
            val res = engine.setLanguage(slLocale)
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.setLanguage(Locale("sl"))
            }
            engine.setSpeechRate(0.97f)
            engine.setPitch(1.0f)
            val voices = engine.voices
            if (voices != null) {
                val bestSlVoice = voices
                    .filter { v ->
                        val lang = v.locale?.language?.lowercase(Locale.ROOT) ?: ""
                        val name = v.name.lowercase(Locale.ROOT)
                        lang == "sl" || lang == "slv" || name.contains("sl-si") || name.contains("sl_si")
                    }
                    .maxByOrNull { v -> scoreAndroidVoice(v) }
                if (bestSlVoice != null) {
                    engine.voice = bestSlVoice
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun scoreAndroidVoice(v: Voice): Int {
        var score = v.quality
        val name = v.name.lowercase(Locale.ROOT)
        if (!v.isNetworkConnectionRequired) score += 200
        if (name.contains("wavenet") || name.contains("neural") || name.contains("local")) score += 150
        return score
    }

    fun speakDeparture(minutes: Int) {
        if (!voiceEnabled) return
        val now = SystemClock.elapsedRealtime()
        if (lastDepartureSpeechMs > 0L && (now - lastDepartureSpeechMs) < 4000L) return
        lastDepartureSpeechMs = now

        val text = buildAnnouncementText(voiceTemplate, minutes)
        speakCustomText(text, voiceGain, neuralVoiceName)
    }

    fun speakCustomText(text: String, gainMultiplier: Double, preferredVoiceMode: String = neuralVoiceName) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val gain = gainMultiplier.coerceIn(0.5, 5.0)
        val ctx = appContext ?: return
        val preferAndroidFirst = preferredVoiceMode.equals(MobileConfig.ANDROID_TTS_VOICE, ignoreCase = true)

        synthExecutor.execute {
            if (preferAndroidFirst) {
                if (!speakWithAndroidTts(ctx, trimmed, gain)) {
                    speakWithGoogleHttpTts(ctx, trimmed, gain)
                }
            } else {
                if (!speakWithGoogleHttpTts(ctx, trimmed, gain)) {
                    speakWithAndroidTts(ctx, trimmed, gain)
                }
            }
        }
    }

    private fun speakWithGoogleHttpTts(ctx: Context, trimmed: String, gain: Double): Boolean {
        val mp3File = getOrFetchGoogleMp3(ctx, trimmed)
        if (mp3File != null && mp3File.exists() && mp3File.length() > 256L) {
            val decoded = decodeMp3ToPcm(mp3File)
            if (decoded != null && decoded.first.isNotEmpty()) {
                val amplified = amplifySpeechPcm(decoded.first, decoded.second, gain)
                if (MobileChimePlayer.playSpeechPcmSync(amplified, decoded.second)) {
                    return true
                }
            }
            if (playMp3WithMediaPlayer(mp3File, gain)) {
                return true
            }
        }
        return false
    }

    private fun getOrFetchGoogleMp3(ctx: Context, text: String): File? {
        try {
            val cacheDir = File(ctx.cacheDir, "google_tts_cache")
            if (!cacheDir.exists()) cacheDir.mkdirs()

            val hash = sha256Hex("google_sl|$text").substring(0, 28)
            val cachedFile = File(cacheDir, "tts_$hash.mp3")
            if (cachedFile.exists() && cachedFile.length() > 256L) {
                return cachedFile
            }

            val mp3Bytes = synthesizeGoogleTtsMp3(text)
            if (mp3Bytes != null && mp3Bytes.size > 256) {
                val tmpFile = File(cacheDir, "tts_${hash}_tmp.mp3")
                tmpFile.writeBytes(mp3Bytes)
                if (cachedFile.exists()) cachedFile.delete()
                tmpFile.renameTo(cachedFile)
                return if (cachedFile.exists()) cachedFile else tmpFile
            }
        } catch (_: Throwable) {
        }
        return null
    }

    private fun synthesizeGoogleTtsMp3(text: String): ByteArray? {
        val safeText = if (text.length > 195) text.substring(0, 195) else text
        val encoded = URLEncoder.encode(safeText, "UTF-8")
        val urls = arrayOf(
            "https://translate.googleapis.com/translate_tts?ie=UTF-8&client=gtx&tl=sl&q=$encoded",
            "https://translate.google.com/translate_tts?ie=UTF-8&client=tw-ob&tl=sl&q=$encoded"
        )
        for (urlStr in urls) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 6000
                    setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                    )
                    setRequestProperty("Referer", "https://translate.google.com/")
                    setRequestProperty("Accept", "audio/mpeg, audio/*;q=0.9, */*;q=0.8")
                }
                if (conn.responseCode in 200..299) {
                    val bytes = conn.inputStream.use { it.readBytes() }
                    if (bytes.size > 256) {
                        return bytes
                    }
                }
            } catch (_: Throwable) {
            } finally {
                try { conn?.disconnect() } catch (_: Throwable) {}
            }
        }
        return null
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(StandardCharsets.UTF_8))
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            sb.append(String.format(Locale.US, "%02x", b))
        }
        return sb.toString()
    }

    private fun decodeMp3ToPcm(mp3File: File): Pair<ShortArray, Int>? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(mp3File.absolutePath)
            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    format = f
                    break
                }
            }
            if (trackIndex < 0 || format == null) return null
            extractor.selectTrack(trackIndex)

            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            var sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else {
                24000
            }
            var channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            } else {
                1
            }

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val pcmOut = ByteArrayOutputStream(mp3File.length().toInt() * 6)
            val bufferInfo = MediaCodec.BufferInfo()
            var inputEos = false
            var outputEos = false

            while (!outputEos) {
                if (!inputEos) {
                    val inIdx = codec.dequeueInputBuffer(10_000L)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)
                        if (inBuf != null) {
                            inBuf.clear()
                            val sampleSize = extractor.readSampleData(inBuf, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputEos = true
                            } else {
                                val pts = extractor.sampleTime
                                codec.queueInputBuffer(inIdx, 0, sampleSize, pts, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
                when {
                    outIdx >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIdx)
                        if (outBuf != null && bufferInfo.size > 0) {
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            val chunk = ByteArray(bufferInfo.size)
                            outBuf.get(chunk)
                            pcmOut.write(chunk)
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputEos = true
                        }
                    }
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFormat = codec.outputFormat
                        if (newFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            sampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            channels = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                        }
                    }
                    else -> break
                }
            }

            val pcmBytes = pcmOut.toByteArray()
            val totalShorts = pcmBytes.size / 2
            val frameCount = totalShorts / channels
            if (frameCount <= 0) return null

            val samples = ShortArray(frameCount)
            val bb = ByteBuffer.wrap(pcmBytes, 0, frameCount * channels * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until frameCount) {
                var sum = 0
                for (c in 0 until channels) {
                    sum += bb.short.toInt()
                }
                samples[i] = (sum / channels).coerceIn(-32768, 32767).toShort()
            }
            return Pair(samples, sampleRate)
        } catch (_: Throwable) {
            return null
        } finally {
            try { codec?.stop() } catch (_: Throwable) {}
            try { codec?.release() } catch (_: Throwable) {}
            try { extractor.release() } catch (_: Throwable) {}
        }
    }

    private fun playMp3WithMediaPlayer(mp3File: File, gainMultiplier: Double): Boolean {
        var player: MediaPlayer? = null
        var enhancer: LoudnessEnhancer? = null
        return try {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(mp3File.absolutePath)
                setVolume(1.0f, 1.0f)
                prepare()
            }
            if (gainMultiplier > 1.05) {
                try {
                    val gainMb = (2000.0 * kotlin.math.log10(gainMultiplier)).roundToInt().coerceIn(0, 1200)
                    enhancer = LoudnessEnhancer(player.audioSessionId).apply {
                        setTargetGain(gainMb)
                        enabled = true
                    }
                } catch (_: Throwable) {
                }
            }
            player.start()
            val waitMs = player.duration.coerceIn(500, 15000).toLong() + 140L
            Thread.sleep(waitMs)
            true
        } catch (_: Throwable) {
            false
        } finally {
            try { enhancer?.release() } catch (_: Throwable) {}
            try { player?.stop() } catch (_: Throwable) {}
            try { player?.release() } catch (_: Throwable) {}
        }
    }

    private fun speakWithAndroidTts(ctx: Context, trimmed: String, gain: Double): Boolean {
        if (tts == null || !ttsReady) {
            initAndroidTts(ctx)
            for (i in 0 until 15) {
                if (ttsReady && tts != null) break
                try { Thread.sleep(100L) } catch (_: Throwable) {}
            }
        }
        val engine = tts
        if (engine == null || !ttsReady) return false

        val utteranceId = "utt_${System.currentTimeMillis()}_${(100..999).random()}"
        val outFile = File(ctx.cacheDir, "$utteranceId.wav")
        try {
            val waiter = SynthWaiter()
            synchronized(pendingWaiters) {
                pendingWaiters[utteranceId] = waiter
            }
            val params = Bundle().apply {
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            }
            val code = engine.synthesizeToFile(trimmed, params, outFile, utteranceId)
            if (code == TextToSpeech.SUCCESS) {
                waiter.latch.await(3800L, TimeUnit.MILLISECONDS)
                synchronized(pendingWaiters) { pendingWaiters.remove(utteranceId) }
                if (waiter.success.get() && outFile.exists() && outFile.length() > 64L) {
                    val parsed = readAndAmplifyWav(outFile, gain)
                    if (parsed != null && parsed.first.isNotEmpty()) {
                        if (MobileChimePlayer.playSpeechPcmSync(parsed.first, parsed.second)) {
                            return true
                        }
                    }
                    val decoded = decodeMp3ToPcm(outFile)
                    if (decoded != null && decoded.first.isNotEmpty()) {
                        val amplified = amplifySpeechPcm(decoded.first, decoded.second, gain)
                        if (MobileChimePlayer.playSpeechPcmSync(amplified, decoded.second)) {
                            return true
                        }
                    }
                    if (playMp3WithMediaPlayer(outFile, gain)) {
                        return true
                    }
                }
            } else {
                synchronized(pendingWaiters) { pendingWaiters.remove(utteranceId) }
            }
        } catch (_: Throwable) {
            synchronized(pendingWaiters) { pendingWaiters.remove(utteranceId) }
        } finally {
            try { if (outFile.exists()) outFile.delete() } catch (_: Throwable) {}
        }

        return try {
            val speakParams = Bundle().apply {
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            }
            val res = engine.speak(trimmed, TextToSpeech.QUEUE_FLUSH, speakParams, "direct_$utteranceId")
            res == TextToSpeech.SUCCESS
        } catch (_: Throwable) {
            false
        }
    }

    private fun amplifySpeechPcm(rawSamples: ShortArray, sampleRate: Int, gainMultiplier: Double): ShortArray {
        val frameCount = rawSamples.size
        if (frameCount == 0) return rawSamples

        var peak = 1
        for (i in 0 until frameCount) {
            val v = rawSamples[i].toInt()
            val absVal = if (v == -32768) 32767 else abs(v)
            if (absVal > peak) peak = absVal
        }

        val baseNorm = 29500.0 / peak
        val effectiveGain = gainMultiplier.coerceIn(0.5, 4.0)

        val attackCoeff = exp(-1.0 / max(1.0, sampleRate * 0.0025))
        val releaseCoeff = exp(-1.0 / max(1.0, sampleRate * 0.090))
        val compThreshold = 19000.0
        val ceiling = 31200.0

        var env = 0.0
        val outSamples = ShortArray(frameCount)
        for (i in 0 until frameCount) {
            val x = rawSamples[i].toDouble() * baseNorm * effectiveGain
            val absX = abs(x)
            env = if (absX > env) {
                attackCoeff * env + (1.0 - attackCoeff) * absX
            } else {
                releaseCoeff * env + (1.0 - releaseCoeff) * absX
            }

            val compGain = if (env > compThreshold) {
                val targetEnv = compThreshold + (ceiling - compThreshold) * tanh((env - compThreshold) / (ceiling - compThreshold))
                targetEnv / env
            } else {
                1.0
            }

            var y = x * compGain
            if (y > 29500.0) {
                y = 29500.0 + 2800.0 * tanh((y - 29500.0) / 2800.0)
            } else if (y < -29500.0) {
                y = -29500.0 - 2800.0 * tanh((-y - 29500.0) / 2800.0)
            }
            outSamples[i] = y.roundToInt().coerceIn(-32700, 32700).toShort()
        }
        return outSamples
    }

    private fun readAndAmplifyWav(file: File, gainMultiplier: Double): Pair<ShortArray, Int>? {
        val bytes = file.readBytes()
        if (bytes.size <= 44) return null

        var audioFormat = 1
        var sampleRate = 22050
        var channels = 1
        var bitsPerSample = 16
        var dataOffset = 44
        var dataLength = bytes.size - 44

        if (bytes.size >= 12 &&
            bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte()
        ) {
            var pos = 12
            while (pos + 8 <= bytes.size) {
                val chunkId = String(bytes, pos, 4, StandardCharsets.US_ASCII)
                val chunkSize = ByteBuffer.wrap(bytes, pos + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
                if (chunkId == "fmt " && pos + 8 + 16 <= bytes.size) {
                    val fmtBuf = ByteBuffer.wrap(bytes, pos + 8, 16).order(ByteOrder.LITTLE_ENDIAN)
                    audioFormat = fmtBuf.short.toInt() and 0xFFFF
                    channels = fmtBuf.short.toInt().coerceAtLeast(1)
                    sampleRate = fmtBuf.int.coerceIn(8000, 96000)
                    fmtBuf.int
                    fmtBuf.short
                    bitsPerSample = fmtBuf.short.toInt().coerceAtLeast(8)
                } else if (chunkId == "data") {
                    dataOffset = pos + 8
                    val rem = bytes.size - dataOffset
                    dataLength = if (chunkSize <= 0 || chunkSize > rem) rem else chunkSize
                    break
                }
                if (chunkSize < 0) break
                pos += 8 + chunkSize + (chunkSize and 1)
            }
        }

        if (dataLength <= 2 || dataOffset + dataLength > bytes.size) return null

        val rawSamples: ShortArray
        if (audioFormat == 3 && bitsPerSample == 32) {
            val totalFloats = dataLength / 4
            val frameCount = totalFloats / channels
            if (frameCount <= 0) return null
            rawSamples = ShortArray(frameCount)
            val bb = ByteBuffer.wrap(bytes, dataOffset, frameCount * channels * 4).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until frameCount) {
                var sum = 0f
                for (c in 0 until channels) {
                    sum += bb.float
                }
                val s = ((sum / channels) * 32767f).roundToInt().coerceIn(-32768, 32767)
                rawSamples[i] = s.toShort()
            }
        } else if (audioFormat == 1 && bitsPerSample == 16) {
            val totalShorts = dataLength / 2
            val frameCount = totalShorts / channels
            if (frameCount <= 0) return null
            rawSamples = ShortArray(frameCount)
            val bb = ByteBuffer.wrap(bytes, dataOffset, frameCount * channels * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until frameCount) {
                var sum = 0
                for (c in 0 until channels) {
                    sum += bb.short.toInt()
                }
                rawSamples[i] = (sum / channels).coerceIn(-32768, 32767).toShort()
            }
        } else {
            return null
        }

        val outSamples = amplifySpeechPcm(rawSamples, sampleRate, gainMultiplier)
        return Pair(outSamples, sampleRate)
    }

    fun buildAnnouncementText(template: String?, minutes: Int): String {
        val validMin = if (minutes <= 0) 1 else minutes
        val tpl = if (template.isNullOrBlank()) {
            MobileConfig.DEFAULT_VOICE_TEMPLATE
        } else {
            template.trim()
        }
        val phrase = formatSlovenianMinutesPhrase(validMin)
        return replaceCaseInsensitive(
            replaceCaseInsensitive(tpl, "{minute}", phrase),
            "{min}", phrase
        )
    }

    private fun replaceCaseInsensitive(input: String, token: String, replacement: String): String {
        if (input.isEmpty() || token.isEmpty()) return input
        var idx = input.indexOf(token, ignoreCase = true)
        if (idx < 0) return input
        val sb = StringBuilder()
        var lastIdx = 0
        while (idx >= 0) {
            sb.append(input, lastIdx, idx)
            sb.append(replacement)
            lastIdx = idx + token.length
            idx = input.indexOf(token, lastIdx, ignoreCase = true)
        }
        sb.append(input, lastIdx, input.length)
        return sb.toString()
    }

    private fun formatSlovenianMinutesPhrase(minutes: Int): String {
        if (minutes == 1) return "eno minuto"
        if (minutes == 2) return "dve minuti"
        if (minutes == 3) return "tri minute"
        if (minutes == 4) return "štiri minute"

        val word = numberToSlovenianWord(minutes)
        return when (minutes % 100) {
            1 -> "$word minuto"
            2 -> "$word minuti"
            3, 4 -> "$word minute"
            else -> "$word minut"
        }
    }

    private fun numberToSlovenianWord(n: Int): String {
        val ones = arrayOf(
            "nič", "ena", "dve", "tri", "štiri", "pet", "šest", "sedem", "osem", "devet",
            "deset", "enajst", "dvanajst", "trinajst", "štirinajst", "petnajst",
            "šestnajst", "sedemnajst", "osemnajst", "devetnajst"
        )
        val tens = arrayOf(
            "", "deset", "dvajset", "trideset", "štirideset", "petdeset",
            "šestdeset", "sedemdeset", "osemdeset", "devetdeset"
        )

        if (n in 0..19) return ones[n]
        if (n in 20..99) {
            val t = n / 10
            val u = n % 10
            if (u == 0) return tens[t]
            val unitPrefix = when (u) {
                1 -> "ena"
                2 -> "dva"
                else -> ones[u]
            }
            return "${unitPrefix}in${tens[t]}"
        }
        return n.toString()
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
                val prevStepSetMs = t.setTimeMs
                val diff = targetSetMs - t.setTimeMs
                val adjCmd = when {
                    diff >= 292500L -> 3
                    diff <= -292500L -> 6
                    diff >= 52500L -> 2
                    diff <= -52500L -> 5
                    diff > 0L -> 1
                    else -> 4
                }
                Thread.sleep(45)
                try {
                    t = sendCommand(host, 80, adjCmd, 1500)
                    if (abs(t.setTimeMs - prevStepSetMs) < 5000L) {
                        Thread.sleep(70)
                        t = sendCommand(host, 80, 0, 1500)
                    }
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

/**
 * Per-station telemetry & alert state tracker (Zgoraj & Spodaj).
 */
class StationTelemetryTracker(
    val stationId: String,
    var isMuted: Boolean,
    var isDimmed: Boolean
) {
    var isOnline: Boolean = false
    var remainingMs: Long = 0L
    var setMs: Long = 0L
    var currentPersons: Int = 0
    var maxPersons: Int = 28
    var hasInitialTelemetry: Boolean = false
    var isSendingCommand: Boolean = false
    var lastCommandSentMs: Long = 0L
    private var lastTimeZone: Int = -1
    private var lastPersonsZone: Int = -1

    fun onLocalTick(deltaMs: Long) {
        if (isOnline && remainingMs > 0L) {
            remainingMs = max(0L, remainingMs - deltaMs)
            evaluateTimeZone(suppressChime = false)
        }
    }

    fun applyState(state: StationState, forceSyncTime: Boolean, isCachedState: Boolean): Boolean {
        val isFirstLoad = !hasInitialTelemetry
        val wasOffline = !isOnline
        isOnline = true
        isSendingCommand = false

        val prevRemainingMs = remainingMs
        val prevSetMs = setMs
        val prevPersons = currentPersons

        setMs = state.setTimeMs
        val clampedRemMs = if (setMs > 0L) min(state.remainingTimeMs, setMs) else state.remainingTimeMs
        if (forceSyncTime || wasOffline || abs(clampedRemMs - remainingMs) > 1500L) {
            remainingMs = clampedRemMs
        }
        if (setMs > 0L && remainingMs > setMs) {
            remainingMs = setMs
        }
        currentPersons = state.currentPersons
        maxPersons = state.maxPersons

        var departedNow = false
        val recentlySentCommand = lastCommandSentMs > 0L && (SystemClock.elapsedRealtime() - lastCommandSentMs) < 3500L
        if (!isFirstLoad && !wasOffline && !isCachedState && !recentlySentCommand) {
            val clockReset = (state.setTimeMs > 0L) &&
                (state.setTimeMs == prevSetMs) &&
                (state.remainingTimeMs - prevRemainingMs > 15_000L) &&
                (state.remainingTimeMs >= state.setTimeMs - 20_000L)
            if (clockReset && (state.currentPersons == 0 || state.currentPersons < prevPersons)) {
                departedNow = true
                if (!isMuted) {
                    val nextMinutes = max(1, ((state.setTimeMs + 30_000L) / 60_000L).toInt())
                    MobileVoiceAnnouncer.speakDeparture(nextMinutes)
                }
            }
        }

        evaluateTimeZone(suppressChime = isFirstLoad)
        evaluatePersonsZone(suppressChime = isFirstLoad)
        hasInitialTelemetry = true
        return departedNow
    }

    fun setOffline() {
        isOnline = false
        isSendingCommand = false
    }

    fun evaluateTimeZone(suppressChime: Boolean): Int {
        if (!isOnline) return 0
        val totalSec = (max(0L, remainingMs) + 499L) / 1000L
        val zone = when {
            remainingMs <= 0L || totalSec <= 0L -> 2
            remainingMs <= 60_000L -> 1
            else -> 0
        }
        if (!suppressChime && !isMuted && lastTimeZone >= 0 && zone > lastTimeZone) {
            if (zone == 1) MobileChimePlayer.playTimeSingle()
            else if (zone == 2) MobileChimePlayer.playTimeTriple()
        }
        lastTimeZone = zone
        return zone
    }

    fun evaluatePersonsZone(suppressChime: Boolean): Int {
        if (!isOnline) return 0
        val zone = when {
            maxPersons > 0 && currentPersons >= maxPersons -> 2
            maxPersons > 0 && currentPersons.toDouble() / maxPersons >= 0.85 -> 1
            else -> 0
        }
        if (!suppressChime && !isMuted && lastPersonsZone >= 0 && zone > lastPersonsZone) {
            if (zone == 1) MobileChimePlayer.playPersonsSingle()
            else if (zone == 2) MobileChimePlayer.playPersonsTriple()
        }
        lastPersonsZone = zone
        return zone
    }
}

class WearMainActivity : Activity() {

    private lateinit var config: MobileConfig
    private val uiHandler = Handler(Looper.getMainLooper())
    private val ioExecutor = Executors.newCachedThreadPool()

    private lateinit var trackerZgoraj: StationTelemetryTracker
    private lateinit var trackerSpodaj: StationTelemetryTracker

    // 0 = Zgornja postaja ("zgoraj"), 1 = Spodnja postaja ("spodaj")
    private var activeStationIndex: Int = 0

    // By default controls are locked (view only) to prevent accidental wrist touches
    private var isControlsLocked: Boolean = true

    private lateinit var rootContainer: FrameLayout
    private lateinit var pager: Wear2DPager
    private lateinit var timeWidgetPage: TimeWidgetPage
    private lateinit var personsWidgetPage: PersonsWidgetPage
    private lateinit var settingsContainer: FrameLayout
    private var settingsScrollView: ScrollView? = null
    private var isSettingsOpen: Boolean = false

    private val streamActive = AtomicBoolean(false)
    @Volatile
    private var activeStreamConnection: HttpURLConnection? = null
    @Volatile
    private var lastCloudSyncSendMs: Long = 0L
    @Volatile
    private var lastAppliedStateTs: Long = 0L
    @Volatile
    private var initialCachedStateTs: Long = 0L
    @Volatile
    private var pcClockOffsetMs: Long = 0L
    @Volatile
    private var hasPcClockOffset: Boolean = false
    @Volatile
    private var lanZgorajOnline: Boolean = false
    @Volatile
    private var lanSpodajOnline: Boolean = false
    @Volatile
    private var lastDepartedStationId: String = "zgoraj"
    @Volatile
    private var lastKnownSingleCarPos: Float = 0f
    private var lastTickRealtimeMs: Long = 0L
    private var lastTileSyncSignature: String = ""

    private val tickRunnable = object : Runnable {
        override fun run() {
            val now = SystemClock.elapsedRealtime()
            val deltaMs = if (lastTickRealtimeMs > 0L) (now - lastTickRealtimeMs).coerceIn(1L, 2000L) else 200L
            lastTickRealtimeMs = now

            trackerZgoraj.onLocalTick(deltaMs)
            trackerSpodaj.onLocalTick(deltaMs)
            refreshWidgetsUi()

            if (config.useCloudRelay) {
                if (now - lastCloudSyncSendMs > 45_000L) {
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

    private val autoLockRunnable = Runnable {
        if (!isControlsLocked) {
            applyControlsLockState(true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = ThemeColors.WindowBg
        window.navigationBarColor = ThemeColors.WindowBg

        config = MobileConfig(this)
        MobileChimePlayer.init(this)
        MobileChimePlayer.enabled = config.soundEnabled
        MobileVoiceAnnouncer.voiceEnabled = config.voiceAnnouncementEnabled
        MobileVoiceAnnouncer.voiceTemplate = config.voiceAnnouncementTemplate
        MobileVoiceAnnouncer.voiceGain = config.voiceAnnouncementGain
        MobileVoiceAnnouncer.neuralVoiceName = config.voiceAnnouncementVoice
        MobileVoiceAnnouncer.init(this)

        trackerZgoraj = StationTelemetryTracker("zgoraj", config.muteZgoraj, config.dimZgoraj)
        trackerSpodaj = StationTelemetryTracker("spodaj", config.muteSpodaj, config.dimSpodaj)

        // Page 0 (Left): Time Countdown Widget (6min / 10min switch + funicular track)
        timeWidgetPage = TimeWidgetPage(
            context = this,
            onToggleStation = { toggleActiveStation() },
            onToggleLock = { applyControlsLockState(!isControlsLocked) },
            onOpenSettings = { openSettingsPage() },
            onToggleShowInterval = {
                if (!isControlsLocked) {
                    if (activeStationIndex == 0) {
                        config.showIntervalBgZgoraj = !config.showIntervalBgZgoraj
                    } else {
                        config.showIntervalBgSpodaj = !config.showIntervalBgSpodaj
                    }
                    config.save()
                    scheduleAutoLockIfNeeded()
                    refreshWidgetsUi()
                }
            },
            onSelectIntervalCommand = { cmdId ->
                handleOperatorCommand(activeStationId(), cmdId)
            }
        )

        // Page 1 (Right): Persons / Capacity Widget (+1, -1 + long-press counter to reset)
        personsWidgetPage = PersonsWidgetPage(
            context = this,
            onToggleStation = { toggleActiveStation() },
            onToggleLock = { applyControlsLockState(!isControlsLocked) },
            onOpenSettings = { openSettingsPage() },
            onToggleShowCapacity = {
                if (!isControlsLocked) {
                    if (activeStationIndex == 0) {
                        config.showCapacityZgoraj = !config.showCapacityZgoraj
                    } else {
                        config.showCapacitySpodaj = !config.showCapacitySpodaj
                    }
                    config.save()
                    scheduleAutoLockIfNeeded()
                    refreshWidgetsUi()
                }
            },
            onLongPressReset = {
                if (!isControlsLocked) {
                    scheduleAutoLockIfNeeded()
                    showResetConfirmDialog()
                }
            },
            onPersonCommand = { cmdId ->
                handleOperatorCommand(activeStationId(), cmdId)
            }
        )

        pager = Wear2DPager(
            context = this,
            page0 = timeWidgetPage,
            page1 = personsWidgetPage,
            getStationIndex = { activeStationIndex },
            onSwitchStation = { targetStationIndex ->
                setStationIndex(targetStationIndex)
            }
        )

        settingsContainer = FrameLayout(this).apply {
            setBackgroundColor(ThemeColors.WindowBg)
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        rootContainer = FrameLayout(this).apply {
            setBackgroundColor(ThemeColors.WindowBg)
            addView(
                pager,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            addView(settingsContainer)
        }

        applyControlsLockState(isControlsLocked)
        val savedStation = getSharedPreferences("vzpenjaca_prefs", Context.MODE_PRIVATE)
            .getString("active_station", "zgoraj") ?: "zgoraj"
        activeStationIndex = if (savedStation.equals("spodaj", ignoreCase = true)) 1 else 0
        refreshWidgetsUi()
        setContentView(rootContainer)
        handleTargetPageIntent(intent)

        // Support rotary crown / bezel scrolling on Wear OS
        rootContainer.isFocusable = true
        rootContainer.isFocusableInTouchMode = true
        rootContainer.requestFocus()
        rootContainer.setOnGenericMotionListener { _, event ->
            if (event.action == MotionEvent.ACTION_SCROLL &&
                event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)
            ) {
                val delta = -event.getAxisValue(MotionEvent.AXIS_SCROLL)
                if (isSettingsOpen) {
                    settingsScrollView?.smoothScrollBy(0, (delta * dp(28)).roundToInt())
                } else {
                    pager.onRotaryScroll(delta)
                }
                true
            } else {
                false
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleTargetPageIntent(intent)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (isSettingsOpen) {
            closeSettingsPage()
        } else {
            super.onBackPressed()
        }
    }

    private fun openSettingsPage() {
        if (isSettingsOpen) return
        isSettingsOpen = true
        val scroll = buildSettingsPage()
        settingsScrollView = scroll
        settingsContainer.removeAllViews()
        settingsContainer.addView(
            scroll,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        settingsContainer.alpha = 0f
        settingsContainer.visibility = View.VISIBLE
        settingsContainer.animate()
            .alpha(1f)
            .setDuration(150L)
            .start()
    }

    private fun closeSettingsPage() {
        if (!isSettingsOpen) return
        isSettingsOpen = false
        settingsContainer.animate()
            .alpha(0f)
            .setDuration(130L)
            .withEndAction {
                settingsContainer.visibility = View.GONE
                settingsContainer.removeAllViews()
                settingsScrollView = null
                rootContainer.requestFocus()
            }
            .start()
    }

    private fun handleTargetPageIntent(intent: android.content.Intent?) {
        if (intent == null) return
        val page = intent.getIntExtra("target_page", -1)
        if (page == 2) {
            openSettingsPage()
        } else if (page in 0..1) {
            if (isSettingsOpen) closeSettingsPage()
            pager.switchToPage(page, animate = false)
        }
        val station = intent.getStringExtra("target_station")
        if (station != null) {
            activeStationIndex = if (station.equals("spodaj", ignoreCase = true)) 1 else 0
            refreshWidgetsUi()
        }
    }

    override fun onResume() {
        super.onResume()
        lastTickRealtimeMs = SystemClock.elapsedRealtime()
        uiHandler.removeCallbacks(tickRunnable)
        uiHandler.post(tickRunnable)
        scheduleAutoLockIfNeeded()
        restartNetworking()
    }

    override fun onPause() {
        super.onPause()
        uiHandler.removeCallbacks(tickRunnable)
        uiHandler.removeCallbacks(lanPollRunnable)
        uiHandler.removeCallbacks(autoLockRunnable)
        stopCloudStream()
    }

    private fun activeStationId(): String = if (activeStationIndex == 0) "zgoraj" else "spodaj"

    private fun activeTracker(): StationTelemetryTracker =
        if (activeStationIndex == 0) trackerZgoraj else trackerSpodaj

    private fun setStationIndex(targetIndex: Int) {
        val clamped = if (targetIndex == 1) 1 else 0
        if (activeStationIndex == clamped) return
        activeStationIndex = clamped
        getSharedPreferences("vzpenjaca_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("active_station", if (activeStationIndex == 0) "zgoraj" else "spodaj")
            .apply()
        TimeCountdownTileService.requestUpdate(this)
        PersonsCapacityTileService.requestUpdate(this)
        refreshWidgetsUi()
    }

    private fun toggleActiveStation() {
        val nextStation = if (activeStationIndex == 0) 1 else 0
        if (::pager.isInitialized) {
            pager.animateStationSwitch(nextStation)
        } else {
            setStationIndex(nextStation)
        }
    }

    private fun scheduleAutoLockIfNeeded() {
        uiHandler.removeCallbacks(autoLockRunnable)
        if (!isControlsLocked && config.autoLockTimeoutSec > 0) {
            uiHandler.postDelayed(autoLockRunnable, config.autoLockTimeoutSec * 1000L)
        }
    }

    private fun applyControlsLockState(locked: Boolean) {
        isControlsLocked = locked
        uiHandler.removeCallbacks(autoLockRunnable)
        timeWidgetPage.setControlsLocked(locked)
        personsWidgetPage.setControlsLocked(locked)
        if (!locked) {
            scheduleAutoLockIfNeeded()
        }
    }

    private fun refreshWidgetsUi() {
        val tracker = activeTracker()
        val isZgoraj = (activeStationIndex == 0)
        val showInterval = if (isZgoraj) config.showIntervalBgZgoraj else config.showIntervalBgSpodaj
        val showCap = if (isZgoraj) config.showCapacityZgoraj else config.showCapacitySpodaj

        timeWidgetPage.render(
            isZgoraj = isZgoraj,
            tracker = tracker,
            showIntervalInline = showInterval
        )
        personsWidgetPage.render(
            isZgoraj = isZgoraj,
            tracker = tracker,
            showCapacityInline = showCap
        )
        syncSingleFunicularPosition()

        val zSec = (max(0L, trackerZgoraj.remainingMs) + 499L) / 1000L
        val sSec = (max(0L, trackerSpodaj.remainingMs) + 499L) / 1000L
        val tileSig = "$activeStationIndex:${trackerZgoraj.isOnline}:$zSec:${trackerZgoraj.setMs}:${trackerZgoraj.currentPersons}:${trackerZgoraj.maxPersons}|${trackerSpodaj.isOnline}:$sSec:${trackerSpodaj.setMs}:${trackerSpodaj.currentPersons}:${trackerSpodaj.maxPersons}"
        if (tileSig != lastTileSyncSignature) {
            lastTileSyncSignature = tileSig
            TileSyncHelper.saveTelemetry(
                this@WearMainActivity,
                zOnline = trackerZgoraj.isOnline,
                zRemMs = trackerZgoraj.remainingMs,
                zSetMs = trackerZgoraj.setMs,
                zCur = trackerZgoraj.currentPersons,
                zMax = trackerZgoraj.maxPersons,
                sOnline = trackerSpodaj.isOnline,
                sRemMs = trackerSpodaj.remainingMs,
                sSetMs = trackerSpodaj.setMs,
                sCur = trackerSpodaj.currentPersons,
                sMax = trackerSpodaj.maxPersons
            )
            TimeCountdownTileService.requestUpdate(this@WearMainActivity)
            PersonsCapacityTileService.requestUpdate(this@WearMainActivity)
        }
    }

    private fun syncSingleFunicularPosition() {
        val totalSec = config.rideDurationSec.coerceIn(10, 600)
        val rideDurationMs = totalSec * 1000L

        val sOnline = trackerSpodaj.isOnline
        val sRem = trackerSpodaj.remainingMs
        val sSet = trackerSpodaj.setMs

        val zOnline = trackerZgoraj.isOnline
        val zRem = trackerZgoraj.remainingMs
        val zSet = trackerZgoraj.setMs

        val isZgornjaView = (activeStationIndex == 0)

        if (!sOnline && !zOnline) {
            val isFromSpodnjaLast = (lastDepartedStationId == "spodaj")
            timeWidgetPage.funicularTrackView.isZgornjaToSpodnja = isZgornjaView
            timeWidgetPage.funicularTrackView.setRideState(
                online = false,
                riding = false,
                isFromSpodnja = isFromSpodnjaLast,
                canonicalFraction = lastKnownSingleCarPos,
                elapsedMs = 0L,
                totalDurationSec = totalSec
            )
            return
        }

        val sElapsed = if (sOnline && sSet >= rideDurationMs && sRem in 1L until sSet) (sSet - sRem) else -1L
        val zElapsed = if (zOnline && zSet >= rideDurationMs && zRem in 1L until zSet) (zSet - zRem) else -1L

        val activeStation: String = when {
            sElapsed > 0L && zElapsed > 0L -> {
                when {
                    sElapsed < zElapsed -> "spodaj"
                    zElapsed < sElapsed -> "zgoraj"
                    else -> lastDepartedStationId
                }
            }
            sElapsed > 0L -> "spodaj"
            zElapsed > 0L -> "zgoraj"
            else -> lastDepartedStationId
        }
        lastDepartedStationId = activeStation

        val activeElapsed = if (activeStation == "spodaj") sElapsed else zElapsed
        val isFromSpodnja = (activeStation == "spodaj")

        timeWidgetPage.funicularTrackView.isZgornjaToSpodnja = isZgornjaView

        if (activeElapsed in 1L..rideDurationMs) {
            val linearProgress = (activeElapsed.toFloat() / rideDurationMs.toFloat()).coerceIn(0f, 1f)
            val progress = computeMotionProfilePosition(linearProgress, config.funicularMotionMode)
            val pos = if (isFromSpodnja) progress else (1f - progress)
            lastKnownSingleCarPos = pos
            timeWidgetPage.funicularTrackView.setRideState(
                online = true,
                riding = true,
                isFromSpodnja = isFromSpodnja,
                canonicalFraction = pos,
                elapsedMs = activeElapsed,
                totalDurationSec = totalSec
            )
        } else {
            val dockedPos = if (activeElapsed > rideDurationMs) {
                if (isFromSpodnja) 1f else 0f
            } else {
                lastKnownSingleCarPos
            }
            lastKnownSingleCarPos = dockedPos
            timeWidgetPage.funicularTrackView.setRideState(
                online = true,
                riding = false,
                isFromSpodnja = isFromSpodnja,
                canonicalFraction = dockedPos,
                elapsedMs = 0L,
                totalDurationSec = totalSec
            )
        }
    }

    private fun computeMotionProfilePosition(linearProgress: Float, motionMode: Int): Float {
        val u = linearProgress.coerceIn(0f, 1f).toDouble()
        if (motionMode == 0) {
            return u.toFloat()
        }

        val u1 = 0.12
        val u2 = 0.87
        val u3 = 0.96
        val u4 = 0.985

        val v0 = 0.07
        val vMax = 0.65
        val vCreep = 0.08

        val area1 = 0.5 * (v0 + vMax) * u1
        val area2 = area1 + vMax * (u2 - u1)
        val area3 = area2 + 0.5 * (vMax + vCreep) * (u3 - u2)
        val area4 = area3 + vCreep * (u4 - u3)
        val totalArea = area4 + 0.5 * vCreep * (1.0 - u4)

        val dist = when {
            u <= u1 -> v0 * u + 0.5 * (vMax - v0) * (u * u) / u1
            u <= u2 -> area1 + vMax * (u - u1)
            u <= u3 -> {
                val du = u - u2
                val w3 = u3 - u2
                area2 + vMax * du - 0.5 * (vMax - vCreep) * (du * du) / w3
            }
            u <= u4 -> area3 + vCreep * (u - u3)
            else -> {
                val du = u - u4
                val w5 = 1.0 - u4
                area4 + vCreep * du - 0.5 * vCreep * (du * du) / w5
            }
        }

        return (dist / totalArea).coerceIn(0.0, 1.0).toFloat()
    }

    private fun showResetConfirmDialog() {
        val stationTitle = if (activeStationIndex == 0) "ZGORAJ" else "SPODAJ"
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(ThemeColors.CardBg)
                setStroke(dp(1), ThemeColors.CardBorder)
            }
        }

        val lblTitle = TextView(this).apply {
            text = "Ponastavi števec?"
            gravity = Gravity.CENTER
            setTextColor(ThemeColors.TextPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        val lblSub = TextView(this).apply {
            text = "Postaja: $stationTitle (28 oseb)"
            gravity = Gravity.CENTER
            setTextColor(ThemeColors.TextSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setPadding(0, dp(3), 0, dp(10))
        }

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val btnNo = TextView(this).apply {
            text = "Prekliči"
            gravity = Gravity.CENTER
            setTextColor(ThemeColors.TextSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(ThemeColors.BtnBg)
                setStroke(dp(1), ThemeColors.BtnBorder)
            }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = dp(6)
            }
        }

        val btnYes = TextView(this).apply {
            text = "Ponastavi"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#06131F"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(ThemeColors.AccentBlue)
            }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        btnRow.addView(btnNo)
        btnRow.addView(btnYes)
        box.addView(lblTitle)
        box.addView(lblSub)
        box.addView(btnRow)

        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setView(box)
            .create()

        btnNo.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            dialog.dismiss()
        }
        btnYes.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            dialog.dismiss()
            handleOperatorCommand(activeStationId(), 9)
        }

        dialog.show()
        dialog.window?.setBackgroundDrawable(GradientDrawable().apply { setColor(Color.TRANSPARENT) })
    }

    // =========================================================================
    // NETWORKING (CLOUD RELAY & DIRECT LAN)
    // =========================================================================

    private fun restartNetworking() {
        uiHandler.removeCallbacks(lanPollRunnable)
        stopCloudStream()
        lastAppliedStateTs = 0L
        initialCachedStateTs = 0L
        lanZgorajOnline = false
        lanSpodajOnline = false

        if (config.useCloudRelay) {
            startCloudStream()
            uiHandler.postDelayed({ fetchLatestCachedCloudState() }, 900L)
        } else {
            uiHandler.post(lanPollRunnable)
        }
    }

    private fun handleOperatorCommand(station: String, cmdId: Int) {
        if (isControlsLocked) return
        scheduleAutoLockIfNeeded()
        executeStationCommandNow(station, cmdId)
    }

    private fun executeStationCommandNow(station: String, cmdId: Int) {
        val targetTracker = if (station == "zgoraj") trackerZgoraj else trackerSpodaj
        targetTracker.isSendingCommand = true
        targetTracker.lastCommandSentMs = SystemClock.elapsedRealtime()
        refreshWidgetsUi()

        if (config.useCloudRelay) {
            ioExecutor.execute {
                val ok = sendCloudCommand(station, cmdId)
                if (!ok) {
                    uiHandler.post {
                        targetTracker.isSendingCommand = false
                        refreshWidgetsUi()
                    }
                } else {
                    uiHandler.postDelayed({ fetchLatestCachedCloudState() }, 1200L)
                }
            }
        } else {
            val ip = if (station == "zgoraj") config.ipZgoraj else config.ipSpodaj
            ioExecutor.execute {
                try {
                    val state = DirectLanFunicularClient.executeComplexCommand(ip, cmdId)
                    if (station == "zgoraj") lanZgorajOnline = true else lanSpodajOnline = true
                    uiHandler.post {
                        lastTickRealtimeMs = SystemClock.elapsedRealtime()
                        val departed = targetTracker.applyState(state, forceSyncTime = true, isCachedState = false)
                        if (departed) lastDepartedStationId = station
                        refreshWidgetsUi()
                    }
                } catch (_: Throwable) {
                    if (station == "zgoraj") lanZgorajOnline = false else lanSpodajOnline = false
                    uiHandler.post {
                        targetTracker.setOffline()
                        refreshWidgetsUi()
                    }
                }
            }
        }
    }

    private fun pollDirectLanBothStations(forceSyncTime: Boolean) {
        ioExecutor.execute {
            try {
                val stZ = DirectLanFunicularClient.sendCommand(config.ipZgoraj, 80, 0, 1800)
                lanZgorajOnline = true
                uiHandler.post {
                    lastTickRealtimeMs = SystemClock.elapsedRealtime()
                    if (trackerZgoraj.applyState(stZ, forceSyncTime, isCachedState = false)) {
                        lastDepartedStationId = "zgoraj"
                    }
                    refreshWidgetsUi()
                }
            } catch (_: Throwable) {
                lanZgorajOnline = false
                uiHandler.post {
                    trackerZgoraj.setOffline()
                    refreshWidgetsUi()
                }
            }
        }
        ioExecutor.execute {
            try {
                val stS = DirectLanFunicularClient.sendCommand(config.ipSpodaj, 80, 0, 1800)
                lanSpodajOnline = true
                uiHandler.post {
                    lastTickRealtimeMs = SystemClock.elapsedRealtime()
                    if (trackerSpodaj.applyState(stS, forceSyncTime, isCachedState = false)) {
                        lastDepartedStationId = "spodaj"
                    }
                    refreshWidgetsUi()
                }
            } catch (_: Throwable) {
                lanSpodajOnline = false
                uiHandler.post {
                    trackerSpodaj.setOffline()
                    refreshWidgetsUi()
                }
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

                    if (!isFirebase) {
                        requestCloudSync()
                    }

                    BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8)).use { reader ->
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
                                            val wasFirstPut = isFirstFirebasePut
                                            isFirstFirebasePut = false
                                            if (wasFirstPut) {
                                                val ts = dataObj.optLong("ts", 0L)
                                                if (initialCachedStateTs == 0L && ts > 0L) {
                                                    initialCachedStateTs = ts
                                                }
                                            }
                                            applyCloudStatePayload(dataObj.toString(), fromCache = wasFirstPut)
                                            if (wasFirstPut) {
                                                // Request live sync from PC AFTER SSE stream is connected so the
                                                // PC's fresh publish arrives as a live (fromCache = false) SSE event.
                                                requestCloudSync()
                                            }
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
            val rawNowMs = System.currentTimeMillis()
            val nowMs = if (hasPcClockOffset) (rawNowMs - pcClockOffsetMs) else rawNowMs
            val payload = JSONObject().apply {
                put("id", "$rawNowMs-${(1000..9999).random()}")
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
            conn.responseCode in 200..299
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

            // If this payload has a newer timestamp than the initial cached snapshot, it was just
            // published live by the PC (e.g. in response to requestCloudSync or an operator command).
            val isLiveUpdate = !fromCache || (initialCachedStateTs > 0L && msgTs > initialCachedStateTs)
            if (isLiveUpdate && msgTs > 0L) {
                pcClockOffsetMs = System.currentTimeMillis() - msgTs
                hasPcClockOffset = true
            }

            val ageMs = if (!isLiveUpdate && msgTs > 0L) {
                if (hasPcClockOffset) {
                    val pcNowMs = System.currentTimeMillis() - pcClockOffsetMs
                    (pcNowMs - msgTs).coerceAtLeast(0L)
                } else {
                    val rawAge = (System.currentTimeMillis() - msgTs).coerceAtLeast(0L)
                    // Avoid subtracting small wall-clock skew (~5s) on initial cached state
                    // right before the live sync response arrives from the PC.
                    if (rawAge < 8_000L) 0L else rawAge
                }
            } else {
                0L
            }
            if (!isLiveUpdate && ageMs > 15 * 60 * 1000L) {
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
                lastTickRealtimeMs = SystemClock.elapsedRealtime()
                if (zState.isOnline) {
                    if (trackerZgoraj.applyState(zState, forceSyncTime = true, isCachedState = !isLiveUpdate)) {
                        lastDepartedStationId = "zgoraj"
                    }
                } else {
                    trackerZgoraj.setOffline()
                }

                if (sState.isOnline) {
                    if (trackerSpodaj.applyState(sState, forceSyncTime = true, isCachedState = !isLiveUpdate)) {
                        lastDepartedStationId = "spodaj"
                    }
                } else {
                    trackerSpodaj.setOffline()
                }
                refreshWidgetsUi()
            }
        } catch (_: Throwable) {
        }
    }

    // =========================================================================
    // PAGE 3: SETTINGS PAGE (SAME AS MOBILE APP, OPTIMIZED FOR WEAR OS)
    // =========================================================================

    private fun buildSettingsPage(): ScrollView {
        var useCloudMode = config.useCloudRelay
        var soundOn = config.soundEnabled
        var voiceOn = config.voiceAnnouncementEnabled
        var muteZ = config.muteZgoraj
        var muteS = config.muteSpodaj
        var dimZ = config.dimZgoraj
        var dimS = config.dimSpodaj
        var isAndroidVoice = config.voiceAnnouncementVoice.equals(MobileConfig.ANDROID_TTS_VOICE, ignoreCase = true)
        val initialStep = ((config.voiceAnnouncementGain.coerceIn(1.0, 4.0) - 1.0) / 0.25).roundToInt().coerceIn(0, 12)
        var currentGain = 1.0 + initialStep * 0.25
        var selectedAutoLockSec = config.autoLockTimeoutSec
        var lastTimedAutoLockSec = if (selectedAutoLockSec in listOf(5, 10, 15, 30, 60)) selectedAutoLockSec else 15
        var selectedMotionMode = config.funicularMotionMode

        val workingChannels = config.savedChannels.map { it.copy() }.toMutableList()
        if (workingChannels.none { it.code.equals(config.cloudChannelId, ignoreCase = true) }) {
            workingChannels.add(0, SavedChannel(config.cloudChannelId, "", config.cloudPin))
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            setBackgroundColor(ThemeColors.WindowBg)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // Extra top/bottom/side padding for round watch chin & bezel
            setPadding(dp(16), dp(22), dp(16), dp(36))
        }

        // Top navigation button to return to counters
        val btnBackToWidgets = createDialogMiniButton("← Nazaj na števce", accent = true) {
            closeSettingsPage()
        }.apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(ThemeColors.AccentBlueActiveBg)
                setStroke(dp(1), ThemeColors.AccentBlue)
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = dp(10)
                rightMargin = dp(10)
                bottomMargin = dp(8)
            }
        }
        container.addView(btnBackToWidgets)

        val lblTitle = TextView(this).apply {
            text = "Nastavitve"
            gravity = Gravity.CENTER
            setTextColor(ThemeColors.TextPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(0, 0, 0, dp(8))
        }
        container.addView(lblTitle)

        // 1. POVEZAVA & SHRANJENI KANALI (PC-JI)
        val connCard = createSettingsCard()
        connCard.addView(createSectionHeader("POVEZAVA", "Komunikacija s postajama"))

        val modeSegmentRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(ThemeColors.SubCardBg)
                setStroke(dp(1), ThemeColors.Divider)
            }
            setPadding(dp(2), dp(2), dp(2), dp(2))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(4)
                bottomMargin = dp(8)
            }
        }

        val btnModeCloud = TextView(this).apply {
            text = "Oblak (PC)"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(4), dp(6), dp(4), dp(6))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnModeLan = TextView(this).apply {
            text = "LAN / VPN"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(4), dp(6), dp(4), dp(6))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        modeSegmentRow.addView(btnModeCloud)
        modeSegmentRow.addView(btnModeLan)
        connCard.addView(modeSegmentRow)

        val cloudSection = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val lanSection = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        cloudSection.addView(createSettingsLabel("Shranjeni PC kanali:"))
        val savedChannelsListLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(6)
            }
        }
        cloudSection.addView(savedChannelsListLayout)

        val currentSaved = workingChannels.firstOrNull { it.code.equals(config.cloudChannelId, ignoreCase = true) }
        cloudSection.addView(createSettingsLabel("Koda kanala (PC):"))
        val edtChannel = createSettingsInput(config.cloudChannelId).apply {
            hint = "vzpenjaca-3436"
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        cloudSection.addView(edtChannel)

        cloudSection.addView(createSettingsLabel("Ime PC-ja (neobvezno):"))
        val edtChannelLabel = createSettingsInput(currentSaved?.label ?: "").apply {
            hint = "npr. Glavni PC"
        }
        cloudSection.addView(edtChannelLabel)

        cloudSection.addView(createSettingsLabel("Varnostni PIN:"))
        val edtPin = createSettingsInput(config.cloudPin).apply {
            hint = "Brez PIN-a"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        cloudSection.addView(edtPin)

        var rebuildSavedChannelsList: (() -> Unit)? = null

        val btnAddOrUpdateChannel = createDialogMiniButton("+ Shrani kanal", accent = true) {
            val code = MobileConfig.sanitizeChannel(edtChannel.text.toString())
            val label = edtChannelLabel.text.toString().trim()
            val pin = edtPin.text.toString().trim()
            edtChannel.setText(code)
            val idx = workingChannels.indexOfFirst { it.code.equals(code, ignoreCase = true) }
            if (idx >= 0) {
                workingChannels[idx] = SavedChannel(code, label, pin)
            } else {
                workingChannels.add(SavedChannel(code, label, pin))
            }
            rebuildSavedChannelsList?.invoke()
        }.apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(6)
                bottomMargin = dp(4)
            }
        }
        cloudSection.addView(btnAddOrUpdateChannel)

        rebuildSavedChannelsList = {
            savedChannelsListLayout.removeAllViews()
            val activeCode = MobileConfig.sanitizeChannel(edtChannel.text.toString())
            for ((index, item) in workingChannels.withIndex()) {
                val isSelected = item.code.equals(activeCode, ignoreCase = true)
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(8), dp(6), dp(6), dp(6))
                    background = GradientDrawable().apply {
                        cornerRadius = dp(7).toFloat()
                        setColor(if (isSelected) ThemeColors.AccentBlueActiveBg else ThemeColors.SubCardBg)
                        setStroke(dp(1), if (isSelected) ThemeColors.AccentBlue else ThemeColors.Divider)
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        if (index > 0) topMargin = dp(4)
                    }
                    setOnClickListener {
                        it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        edtChannel.setText(item.code)
                        edtChannelLabel.setText(item.label)
                        if (item.pin.isNotEmpty() || edtPin.text.isNullOrEmpty()) {
                            edtPin.setText(item.pin)
                        }
                        rebuildSavedChannelsList?.invoke()
                    }
                }

                val dot = View(this).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(if (isSelected) ThemeColors.AccentEmerald else ThemeColors.TextMuted)
                    }
                    layoutParams = LinearLayout.LayoutParams(dp(6), dp(6)).apply {
                        rightMargin = dp(6)
                    }
                }
                row.addView(dot)

                val titleStr = if (item.label.isNotBlank()) "${item.label} • ${item.code}" else item.code
                val lblCode = TextView(this).apply {
                    text = titleStr
                    setTextColor(if (isSelected) ThemeColors.TextPrimary else ThemeColors.TextSecondary)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                    typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                    setSingleLine(true)
                    ellipsize = TextUtils.TruncateAt.END
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                row.addView(lblCode)

                if (workingChannels.size > 1) {
                    val btnRemove = TextView(this).apply {
                        text = "✕"
                        gravity = Gravity.CENTER
                        setTextColor(ThemeColors.TextMuted)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                        background = GradientDrawable().apply {
                            cornerRadius = dp(5).toFloat()
                            setColor(ThemeColors.BtnBg)
                            setStroke(dp(1), ThemeColors.BtnBorder)
                        }
                        layoutParams = LinearLayout.LayoutParams(dp(22), dp(22)).apply {
                            leftMargin = dp(4)
                        }
                        setOnClickListener {
                            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            workingChannels.removeAt(index)
                            if (isSelected && workingChannels.isNotEmpty()) {
                                val next = workingChannels.first()
                                edtChannel.setText(next.code)
                                edtChannelLabel.setText(next.label)
                                edtPin.setText(next.pin)
                            }
                            rebuildSavedChannelsList?.invoke()
                        }
                    }
                    row.addView(btnRemove)
                }

                savedChannelsListLayout.addView(row)
            }
        }
        rebuildSavedChannelsList.invoke()

        cloudSection.addView(createSettingsLabel("Strežnik oblačnega mostu:"))
        val edtServer = createSettingsInput(config.cloudServer).apply {
            hint = "https://..."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        }
        cloudSection.addView(edtServer)

        lanSection.addView(createSettingsLabel("IP Zgornja postaja:"))
        val edtIpZgoraj = createSettingsInput(config.ipZgoraj).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        lanSection.addView(edtIpZgoraj)

        lanSection.addView(createSettingsLabel("IP Spodnja postaja:"))
        val edtIpSpodaj = createSettingsInput(config.ipSpodaj).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        lanSection.addView(edtIpSpodaj)

        connCard.addView(cloudSection)
        connCard.addView(lanSection)

        fun updateConnectionModeUi() {
            styleSegmentTab(btnModeCloud, useCloudMode)
            styleSegmentTab(btnModeLan, !useCloudMode)
            cloudSection.visibility = if (useCloudMode) View.VISIBLE else View.GONE
            lanSection.visibility = if (!useCloudMode) View.VISIBLE else View.GONE
        }
        btnModeCloud.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            useCloudMode = true
            updateConnectionModeUi()
        }
        btnModeLan.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            useCloudMode = false
            updateConnectionModeUi()
        }
        updateConnectionModeUi()
        container.addView(connCard)

        // 2. ODKLEPANJE KONTROL
        val lockCard = createSettingsCard()
        lockCard.addView(createSectionHeader("ODKLEPANJE KONTROL", "Samodejni zaklep tipk"))

        val lockModeSegmentRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(ThemeColors.SubCardBg)
                setStroke(dp(1), ThemeColors.Divider)
            }
            setPadding(dp(2), dp(2), dp(2), dp(2))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(4)
                bottomMargin = dp(6)
            }
        }
        val btnLockManual = TextView(this).apply {
            text = "Ročno"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(4), dp(6), dp(4), dp(6))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnLockTimed = TextView(this).apply {
            text = "Samodejno"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(4), dp(6), dp(4), dp(6))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        lockModeSegmentRow.addView(btnLockManual)
        lockModeSegmentRow.addView(btnLockTimed)
        lockCard.addView(lockModeSegmentRow)

        val lockPresetRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val lockPresets = listOf(5, 10, 15, 30, 60)
        val lockPresetButtons = mutableListOf<Pair<Int, TextView>>()

        fun updateAutoLockUi() {
            val isManual = (selectedAutoLockSec == 0)
            styleSegmentTab(btnLockManual, isManual)
            styleSegmentTab(btnLockTimed, !isManual)
            for ((sec, btn) in lockPresetButtons) {
                val active = (!isManual && selectedAutoLockSec == sec)
                btn.background = GradientDrawable().apply {
                    cornerRadius = dp(6).toFloat()
                    setColor(if (active) ThemeColors.AccentBlueActiveBg else ThemeColors.BtnBg)
                    setStroke(dp(1), if (active) ThemeColors.AccentBlue else ThemeColors.BtnBorder)
                }
                btn.setTextColor(if (active) ThemeColors.AccentBlue else ThemeColors.TextPrimary)
            }
        }

        for ((idx, sec) in lockPresets.withIndex()) {
            val btn = createDialogMiniButton("${sec}s") {
                selectedAutoLockSec = sec
                lastTimedAutoLockSec = sec
                updateAutoLockUi()
            }.apply {
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(6), dp(4), dp(6))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (idx < lockPresets.size - 1) rightMargin = dp(3)
                }
            }
            lockPresetButtons.add(sec to btn)
            lockPresetRow.addView(btn)
        }

        btnLockManual.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            selectedAutoLockSec = 0
            updateAutoLockUi()
        }
        btnLockTimed.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            if (selectedAutoLockSec == 0) {
                selectedAutoLockSec = lastTimedAutoLockSec
            }
            updateAutoLockUi()
        }
        lockCard.addView(lockPresetRow)
        updateAutoLockUi()
        container.addView(lockCard)

        // 3. TRAJANJE VOŽNJE VZPENJAČE
        val rideCard = createSettingsCard()
        rideCard.addView(createSectionHeader("TRAJANJE VOŽNJE", "Čas vožnje kabine na tirih"))

        val rideRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }
        val edtRideDuration = createSettingsInput(config.rideDurationSec.toString()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            gravity = Gravity.CENTER
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(dp(64), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                rightMargin = dp(8)
            }
        }
        val lblSecUnit = TextView(this).apply {
            text = "sekund"
            setTextColor(ThemeColors.TextSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        rideRow.addView(edtRideDuration)
        rideRow.addView(lblSecUnit)
        rideCard.addView(rideRow)

        val presetRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val presets = listOf(60, 75, 90, 105, 120)
        val presetButtons = mutableListOf<Pair<Int, TextView>>()

        fun updatePresetHighlight() {
            val curVal = edtRideDuration.text.toString().trim().toIntOrNull() ?: -1
            for ((sec, btn) in presetButtons) {
                val active = (sec == curVal)
                btn.background = GradientDrawable().apply {
                    cornerRadius = dp(6).toFloat()
                    setColor(if (active) ThemeColors.AccentBlueActiveBg else ThemeColors.BtnBg)
                    setStroke(dp(1), if (active) ThemeColors.AccentBlue else ThemeColors.BtnBorder)
                }
                btn.setTextColor(if (active) ThemeColors.AccentBlue else ThemeColors.TextPrimary)
            }
        }

        for ((idx, p) in presets.withIndex()) {
            val btn = createDialogMiniButton("${p}s") {
                edtRideDuration.setText(p.toString())
                updatePresetHighlight()
            }.apply {
                gravity = Gravity.CENTER
                setPadding(dp(2), dp(6), dp(2), dp(6))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (idx < presets.size - 1) rightMargin = dp(3)
                }
            }
            presetButtons.add(p to btn)
            presetRow.addView(btn)
        }
        edtRideDuration.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updatePresetHighlight()
            }
        })
        updatePresetHighlight()
        rideCard.addView(presetRow)

        rideCard.addView(createSettingsLabel("Način prikaza položaja kabine:"))
        val motionSegmentRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(ThemeColors.SubCardBg)
                setStroke(dp(1), ThemeColors.Divider)
            }
            setPadding(dp(2), dp(2), dp(2), dp(2))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(3)
            }
        }
        val btnMotionLinear = TextView(this).apply {
            text = "Linearno"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(4), dp(6), dp(4), dp(6))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnMotionGraph = TextView(this).apply {
            text = "Po grafu"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(4), dp(6), dp(4), dp(6))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        fun updateMotionModeUi() {
            styleSegmentTab(btnMotionLinear, selectedMotionMode == 0)
            styleSegmentTab(btnMotionGraph, selectedMotionMode == 1)
        }
        btnMotionLinear.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            selectedMotionMode = 0
            updateMotionModeUi()
        }
        btnMotionGraph.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            selectedMotionMode = 1
            updateMotionModeUi()
        }
        updateMotionModeUi()
        motionSegmentRow.addView(btnMotionLinear)
        motionSegmentRow.addView(btnMotionGraph)
        rideCard.addView(motionSegmentRow)
        container.addView(rideCard)

        // 4. ZVOK, POSTAJI IN GOVOR
        val audioCard = createSettingsCard()
        audioCard.addView(createSectionHeader("ZVOK IN GOVOR", "Zvončki, postaji in govor ob odhodu"))

        audioCard.addView(
            createModernToggleRow("Zvončki in vibriranje", soundOn) { checked ->
                soundOn = checked
            }
        )
        audioCard.addView(
            createModernToggleRow("Utišaj Zgornjo postajo", muteZ) { checked ->
                muteZ = checked
            }
        )
        audioCard.addView(
            createModernToggleRow("Utišaj Spodnjo postajo", muteS) { checked ->
                muteS = checked
            }
        )
        audioCard.addView(
            createModernToggleRow("Zatemni Zgornjo postajo", dimZ) { checked ->
                dimZ = checked
            }
        )
        audioCard.addView(
            createModernToggleRow("Zatemni Spodnjo postajo", dimS) { checked ->
                dimS = checked
            }
        )

        val voiceDetailsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (voiceOn) View.VISIBLE else View.GONE
            setPadding(0, dp(4), 0, 0)
        }

        audioCard.addView(
            createModernToggleRow("Glasovno obvestilo", voiceOn) { checked ->
                voiceOn = checked
                voiceDetailsContainer.visibility = if (checked) View.VISIBLE else View.GONE
            }
        )

        voiceDetailsContainer.addView(createSettingsLabel("Govorni pogon:"))
        val engineSegmentRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(ThemeColors.SubCardBg)
                setStroke(dp(1), ThemeColors.Divider)
            }
            setPadding(dp(2), dp(2), dp(2), dp(2))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(3)
                bottomMargin = dp(4)
            }
        }
        val btnEngineGoogle = TextView(this).apply {
            text = "Google TTS"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(4), dp(6), dp(4), dp(6))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnEngineAndroid = TextView(this).apply {
            text = "Android TTS"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(4), dp(6), dp(4), dp(6))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        fun updateVoiceEngineUi() {
            styleSegmentTab(btnEngineGoogle, !isAndroidVoice)
            styleSegmentTab(btnEngineAndroid, isAndroidVoice)
        }
        btnEngineGoogle.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            isAndroidVoice = false
            updateVoiceEngineUi()
        }
        btnEngineAndroid.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            isAndroidVoice = true
            updateVoiceEngineUi()
        }
        updateVoiceEngineUi()
        engineSegmentRow.addView(btnEngineGoogle)
        engineSegmentRow.addView(btnEngineAndroid)
        voiceDetailsContainer.addView(engineSegmentRow)

        val gainRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(2))
        }
        val lblGainTitle = TextView(this).apply {
            text = "Ojačanje:"
            setTextColor(ThemeColors.TextSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val lblGainVal = TextView(this).apply {
            text = "${(currentGain * 100.0).roundToInt()} %"
            setTextColor(ThemeColors.AccentEmerald)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        gainRow.addView(lblGainTitle)
        gainRow.addView(lblGainVal)
        voiceDetailsContainer.addView(gainRow)

        val seekGain = SeekBar(this).apply {
            max = 12
            progress = initialStep
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    currentGain = 1.0 + progress * 0.25
                    lblGainVal.text = "${(currentGain * 100.0).roundToInt()} %"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        voiceDetailsContainer.addView(seekGain)

        voiceDetailsContainer.addView(createSettingsLabel("Besedilo ({minute}):"))
        val edtVoiceTemplate = createSettingsInput(
            config.voiceAnnouncementTemplate.ifBlank { MobileConfig.DEFAULT_VOICE_TEMPLATE },
            singleLine = false
        ).apply {
            minLines = 2
            maxLines = 3
        }
        voiceDetailsContainer.addView(edtVoiceTemplate)

        val voiceBtnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(6)
            }
        }
        val btnDefaultVoice = createDialogMiniButton("Privzeto") {
            edtVoiceTemplate.setText(MobileConfig.DEFAULT_VOICE_TEMPLATE)
        }.apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = dp(4)
            }
        }
        val btnTestVoice = createDialogMiniButton("Preizkusi", accent = true) {
            val selectedVoice = if (isAndroidVoice) MobileConfig.ANDROID_TTS_VOICE else MobileConfig.DEFAULT_NEURAL_VOICE
            val sampleText = MobileVoiceAnnouncer.buildAnnouncementText(edtVoiceTemplate.text.toString(), 10)
            MobileVoiceAnnouncer.speakCustomText(sampleText, currentGain, selectedVoice)
        }.apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        voiceBtnRow.addView(btnDefaultVoice)
        voiceBtnRow.addView(btnTestVoice)
        voiceDetailsContainer.addView(voiceBtnRow)

        audioCard.addView(voiceDetailsContainer)
        container.addView(audioCard)

        // Save button (shaped for circular watch bottom)
        val btnSave = TextView(this).apply {
            text = "Shrani nastavitve"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#06131F"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(ThemeColors.AccentBlue)
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = dp(10)
                rightMargin = dp(10)
                topMargin = dp(4)
            }
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                val chosenCode = MobileConfig.sanitizeChannel(edtChannel.text.toString())
                val chosenLabel = edtChannelLabel.text.toString().trim()
                val chosenPin = edtPin.text.toString().trim()

                config.useCloudRelay = useCloudMode
                config.cloudChannelId = chosenCode
                config.cloudPin = chosenPin
                config.cloudServer = MobileConfig.normalizeServerUrl(edtServer.text.toString())
                config.savedChannels = workingChannels
                config.upsertSavedChannel(chosenCode, chosenLabel, chosenPin)

                config.ipZgoraj = edtIpZgoraj.text.toString().trim().ifEmpty { "10.12.220.34" }
                config.ipSpodaj = edtIpSpodaj.text.toString().trim().ifEmpty { "10.12.220.36" }
                config.soundEnabled = soundOn
                config.muteZgoraj = muteZ
                config.muteSpodaj = muteS
                config.dimZgoraj = dimZ
                config.dimSpodaj = dimS
                trackerZgoraj.isMuted = muteZ
                trackerSpodaj.isMuted = muteS
                trackerZgoraj.isDimmed = dimZ
                trackerSpodaj.isDimmed = dimS

                config.voiceAnnouncementEnabled = voiceOn
                config.voiceAnnouncementVoice = if (isAndroidVoice) {
                    MobileConfig.ANDROID_TTS_VOICE
                } else {
                    MobileConfig.DEFAULT_NEURAL_VOICE
                }
                val tpl = edtVoiceTemplate.text.toString().replace("\r", " ").replace("\n", " ").trim()
                config.voiceAnnouncementTemplate = tpl.ifEmpty { MobileConfig.DEFAULT_VOICE_TEMPLATE }
                config.voiceAnnouncementGain = currentGain

                val parsedDuration = edtRideDuration.text.toString().trim().toIntOrNull() ?: 90
                config.rideDurationSec = parsedDuration.coerceIn(10, 600)
                config.funicularMotionMode = selectedMotionMode
                config.autoLockTimeoutSec = selectedAutoLockSec

                MobileChimePlayer.enabled = config.soundEnabled
                MobileVoiceAnnouncer.voiceEnabled = config.voiceAnnouncementEnabled
                MobileVoiceAnnouncer.voiceTemplate = config.voiceAnnouncementTemplate
                MobileVoiceAnnouncer.voiceGain = config.voiceAnnouncementGain
                MobileVoiceAnnouncer.neuralVoiceName = config.voiceAnnouncementVoice
                MobileVoiceAnnouncer.prewarmCommonAnnouncements()

                config.save()
                scheduleAutoLockIfNeeded()
                refreshWidgetsUi()
                restartNetworking()
                closeSettingsPage()
            }
        }
        container.addView(btnSave)

        scroll.addView(container)
        return scroll
    }

    private fun createSettingsCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(9), dp(10), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(ThemeColors.CardBg)
                setStroke(dp(1), ThemeColors.CardBorder)
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(8)
            }
        }
    }

    private fun createSectionHeader(title: String, subtitle: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(4))
            addView(TextView(this@WearMainActivity).apply {
                text = title
                setTextColor(ThemeColors.AccentBlue)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                letterSpacing = 0.05f
            })
            addView(TextView(this@WearMainActivity).apply {
                text = subtitle
                setTextColor(ThemeColors.TextMuted)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            })
        }
    }

    private fun styleSegmentTab(tab: TextView, selected: Boolean) {
        if (selected) {
            tab.setTextColor(ThemeColors.TextPrimary)
            tab.background = GradientDrawable().apply {
                cornerRadius = dp(6).toFloat()
                setColor(ThemeColors.AccentBlueActiveBg)
                setStroke(dp(1), ThemeColors.AccentBlue)
            }
        } else {
            tab.setTextColor(ThemeColors.TextSecondary)
            tab.background = null
        }
    }

    private fun createModernToggleRow(
        title: String,
        initialChecked: Boolean,
        onChanged: (Boolean) -> Unit
    ): LinearLayout {
        var isChecked = initialChecked
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(7), dp(8), dp(7))
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(ThemeColors.SubCardBg)
                setStroke(dp(1), ThemeColors.Divider)
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(4)
            }
        }
        val lblTitle = TextView(this).apply {
            text = title
            setTextColor(ThemeColors.TextPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = dp(6)
            }
        }
        val pillStatus = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(8), dp(3), dp(8), dp(3))
        }
        fun updateVisual() {
            pillStatus.text = if (isChecked) "VKLOP" else "IZKLOP"
            pillStatus.setTextColor(if (isChecked) ThemeColors.AccentEmerald else ThemeColors.TextMuted)
            pillStatus.background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(if (isChecked) 0x2210B981.toInt() else ThemeColors.BtnBg)
                setStroke(dp(1), if (isChecked) ThemeColors.AccentEmerald else ThemeColors.BtnBorder)
            }
        }
        updateVisual()
        row.addView(lblTitle)
        row.addView(pillStatus)
        row.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            isChecked = !isChecked
            updateVisual()
            onChanged(isChecked)
        }
        return row
    }

    private fun createDialogMiniButton(
        label: String,
        accent: Boolean = false,
        onClick: () -> Unit
    ): TextView {
        val bg = GradientDrawable().apply {
            cornerRadius = dp(7).toFloat()
            setColor(if (accent) ThemeColors.AccentBlueActiveBg else ThemeColors.BtnBg)
            setStroke(dp(1), if (accent) ThemeColors.AccentBlue else ThemeColors.BtnBorder)
        }
        return TextView(this).apply {
            text = label
            setTextColor(if (accent) ThemeColors.AccentBlue else ThemeColors.TextPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = bg
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }
        }
    }

    private fun createSettingsLabel(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(ThemeColors.TextSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dp(2), dp(6), dp(2), dp(2))
        }
    }

    private fun createSettingsInput(initialValue: String, singleLine: Boolean = true): EditText {
        return EditText(this).apply {
            setText(initialValue)
            setTextColor(ThemeColors.TextPrimary)
            setHintTextColor(ThemeColors.TextMuted)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setSingleLine(singleLine)
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = GradientDrawable().apply {
                cornerRadius = dp(7).toFloat()
                setColor(ThemeColors.SubCardBg)
                setStroke(dp(1), ThemeColors.BtnBorder)
            }
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
 * 2D gesture pager for Wear OS:
 * - Swipe LEFT / RIGHT: switches between Page 0 (Time Countdown) and Page 1 (Persons / Capacity)
 * - Swipe UP / DOWN: switches between Zgornja postaja (0) and Spodnja postaja (1)
 * Includes subtle 2-dot vertical station indicator on the right edge
 * and 2-dot horizontal page indicator at the bottom edge.
 */
class Wear2DPager(
    context: Context,
    private val page0: View,
    private val page1: View,
    private val getStationIndex: () -> Int,
    private val onSwitchStation: (Int) -> Unit
) : FrameLayout(context) {

    private enum class DragAxis { NONE, HORIZONTAL, VERTICAL }

    private val pages = arrayOf(page0, page1)
    private var currentPageIndex: Int = 0
    private val touchSlop: Int = ViewConfiguration.get(context).scaledTouchSlop
    private var downX: Float = 0f
    private var downY: Float = 0f
    private var dragAxis: DragAxis = DragAxis.NONE
    private var velocityTracker: VelocityTracker? = null
    private var lastRotarySwitchMs: Long = 0L

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private var indicatorAlpha: Float = 1.0f
    private val fadeRunnable = Runnable {
        indicatorAlpha = 0.35f
        invalidate()
    }

    init {
        setBackgroundColor(ThemeColors.WindowBg)
        setWillNotDraw(false)

        for ((idx, p) in pages.withIndex()) {
            p.layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            p.visibility = if (idx == 0) View.VISIBLE else View.GONE
            addView(p)
        }
        postDelayed(fadeRunnable, 2200L)
    }

    private fun wakeIndicators() {
        indicatorAlpha = 1.0f
        removeCallbacks(fadeRunnable)
        postDelayed(fadeRunnable, 2200L)
        invalidate()
    }

    fun onRotaryScroll(delta: Float) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastRotarySwitchMs < 320L) return
        val stationIdx = getStationIndex()
        if (delta > 0.35f && stationIdx == 0) {
            lastRotarySwitchMs = now
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            animateStationSwitch(1)
        } else if (delta < -0.35f && stationIdx == 1) {
            lastRotarySwitchMs = now
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            animateStationSwitch(0)
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                dragAxis = DragAxis.NONE
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain()
                velocityTracker?.addMovement(ev)
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(ev)
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (abs(dx) > touchSlop && abs(dx) > abs(dy) * 1.12f) {
                    dragAxis = DragAxis.HORIZONTAL
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                } else if (abs(dy) > touchSlop && abs(dy) > abs(dx) * 1.12f) {
                    dragAxis = DragAxis.VERTICAL
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragAxis = DragAxis.NONE
                velocityTracker?.recycle()
                velocityTracker = null
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain()
        }
        velocityTracker?.addMovement(ev)
        val activeView = pages[currentPageIndex]

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                dragAxis = DragAxis.NONE
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (dragAxis == DragAxis.NONE) {
                    if (abs(dx) > touchSlop && abs(dx) >= abs(dy)) {
                        dragAxis = DragAxis.HORIZONTAL
                    } else if (abs(dy) > touchSlop && abs(dy) > abs(dx)) {
                        dragAxis = DragAxis.VERTICAL
                    }
                }
                when (dragAxis) {
                    DragAxis.HORIZONTAL -> {
                        val canGoRightPage = (currentPageIndex == 0 && dx < 0f)
                        val canGoLeftPage = (currentPageIndex == 1 && dx > 0f)
                        activeView.translationX = if (canGoRightPage || canGoLeftPage) dx * 0.25f else dx * 0.05f
                        activeView.translationY = 0f
                    }
                    DragAxis.VERTICAL -> {
                        val stIdx = getStationIndex()
                        val canGoSpodaj = (stIdx == 0 && dy < 0f)
                        val canGoZgoraj = (stIdx == 1 && dy > 0f)
                        activeView.translationY = if (canGoSpodaj || canGoZgoraj) dy * 0.25f else dy * 0.05f
                        activeView.translationX = 0f
                    }
                    DragAxis.NONE -> {}
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val dx = ev.x - downX
                val dy = ev.y - downY
                velocityTracker?.computeCurrentVelocity(1000)
                val vx = velocityTracker?.xVelocity ?: 0f
                val vy = velocityTracker?.yVelocity ?: 0f
                velocityTracker?.recycle()
                velocityTracker = null

                val minSwipeDist = dp(24)
                val minSwipeVel = 290f

                when (dragAxis) {
                    DragAxis.HORIZONTAL -> {
                        if (dx < 0f && currentPageIndex == 0 && (-dx > minSwipeDist || -vx > minSwipeVel)) {
                            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            switchToPage(1)
                        } else if (dx > 0f && currentPageIndex == 1 && (dx > minSwipeDist || vx > minSwipeVel)) {
                            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            switchToPage(0)
                        } else {
                            activeView.animate().translationX(0f).translationY(0f).alpha(1f).setDuration(140L).start()
                        }
                    }
                    DragAxis.VERTICAL -> {
                        val stIdx = getStationIndex()
                        if (dy < 0f && stIdx == 0 && (-dy > minSwipeDist || -vy > minSwipeVel)) {
                            // Swipe UP -> Spodnja postaja (1)
                            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            animateStationSwitch(1)
                        } else if (dy > 0f && stIdx == 1 && (dy > minSwipeDist || vy > minSwipeVel)) {
                            // Swipe DOWN -> Zgornja postaja (0)
                            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            animateStationSwitch(0)
                        } else {
                            activeView.animate().translationX(0f).translationY(0f).alpha(1f).setDuration(140L).start()
                        }
                    }
                    DragAxis.NONE -> {
                        activeView.animate().translationX(0f).translationY(0f).alpha(1f).setDuration(140L).start()
                    }
                }
                dragAxis = DragAxis.NONE
                return true
            }
        }
        return super.onTouchEvent(ev)
    }

    fun switchToPage(targetIndex: Int, animate: Boolean = true) {
        if (targetIndex !in 0..1) return
        if (targetIndex == currentPageIndex) {
            pages[currentPageIndex].animate().translationX(0f).translationY(0f).alpha(1f).setDuration(120L).start()
            return
        }
        val animateRight = targetIndex < currentPageIndex
        val outgoing = pages[currentPageIndex]
        val incoming = pages[targetIndex]
        currentPageIndex = targetIndex

        wakeIndicators()

        outgoing.animate().cancel()
        incoming.animate().cancel()

        if (!animate) {
            outgoing.visibility = View.GONE
            outgoing.translationX = 0f
            outgoing.translationY = 0f
            outgoing.alpha = 1f
            incoming.translationX = 0f
            incoming.translationY = 0f
            incoming.alpha = 1f
            incoming.visibility = View.VISIBLE
            return
        }

        val shift = (if (width > 0) width else dp(200)) * 0.22f
        val outTargetX = if (animateRight) shift else -shift
        val inStartX = if (animateRight) -shift else shift

        outgoing.animate()
            .translationX(outTargetX)
            .translationY(0f)
            .alpha(0f)
            .setDuration(150L)
            .withEndAction {
                outgoing.visibility = View.GONE
                outgoing.translationX = 0f
                outgoing.translationY = 0f
                outgoing.alpha = 1f
            }
            .start()

        incoming.translationX = inStartX
        incoming.translationY = 0f
        incoming.alpha = 0f
        incoming.visibility = View.VISIBLE
        incoming.animate()
            .translationX(0f)
            .translationY(0f)
            .alpha(1f)
            .setDuration(180L)
            .start()
    }

    fun animateStationSwitch(targetStationIndex: Int) {
        val currentStation = getStationIndex()
        val activeView = pages[currentPageIndex]
        if (targetStationIndex == currentStation) {
            activeView.animate().translationX(0f).translationY(0f).alpha(1f).setDuration(120L).start()
            return
        }
        val animateDown = (targetStationIndex == 0)
        val shift = (if (height > 0) height else dp(200)) * 0.18f
        val outTargetY = if (animateDown) shift else -shift
        val inStartY = if (animateDown) -shift else shift

        wakeIndicators()
        activeView.animate().cancel()

        activeView.animate()
            .translationY(outTargetY)
            .translationX(0f)
            .alpha(0f)
            .setDuration(110L)
            .withEndAction {
                onSwitchStation(targetStationIndex)
                invalidate()
                activeView.translationY = inStartY
                activeView.translationX = 0f
                activeView.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(150L)
                    .start()
            }
            .start()
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val stationIdx = getStationIndex()
        val spacing = dpF(8.5f)

        // 1) Vertical 2-dot indicator on right edge (0 = Zgornja postaja, 1 = Spodnja postaja)
        val rightDotX = w - dpF(5f)
        val centerY = h * 0.5f
        for (i in 0..1) {
            val cy = centerY + (i - 0.5f) * spacing
            val isSelected = (i == stationIdx)
            val activeCol = if (i == 0) ThemeColors.AccentBlue else ThemeColors.AccentEmerald
            dotPaint.color = if (isSelected) activeCol else ThemeColors.TextMuted
            dotPaint.alpha = ((if (isSelected) 240 else 115) * indicatorAlpha).roundToInt().coerceIn(30, 255)
            val r = if (isSelected) dpF(2.6f) else dpF(1.8f)
            canvas.drawCircle(rightDotX, cy, r, dotPaint)
        }

        // 2) Horizontal 2-dot indicator at bottom center (0 = Countdown, 1 = Capacity)
        val bottomDotY = h - dpF(4.2f)
        val centerX = w * 0.5f
        for (i in 0..1) {
            val cx = centerX + (i - 0.5f) * spacing
            val isSelected = (i == currentPageIndex)
            dotPaint.color = if (isSelected) ThemeColors.AccentBlue else ThemeColors.TextMuted
            dotPaint.alpha = ((if (isSelected) 240 else 115) * indicatorAlpha).roundToInt().coerceIn(30, 255)
            val r = if (isSelected) dpF(2.4f) else dpF(1.7f)
            canvas.drawCircle(cx, bottomDotY, r, dotPaint)
        }
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).roundToInt()
    }

    private fun dpF(value: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            resources.displayMetrics
        )
    }
}

/**
 * Compact header bar shared by Page 0 (Time Countdown) and Page 1 (Persons Capacity):
 * Displays:
 * 1) Station badge (`ZGORAJ` / `SPODAJ`) with live connection status dot (tap or swipe up/down to switch station)
 * 2) Lock icon button (`btnLock`)
 * 3) Dedicated Settings icon button (`btnSettings`) next to the lock button
 */
class WearWidgetHeader(
    context: Context,
    onToggleStation: () -> Unit,
    onToggleLock: () -> Unit,
    onOpenSettings: () -> Unit
) : LinearLayout(context) {

    private val stationPill: LinearLayout
    private val statusDot: View
    private val lblStationBadge: TextView
    val btnLock: WearTopIconButton
    val btnSettings: WearTopIconButton

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER

        stationPill = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(9), dp(4))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(0x2238BDF8.toInt())
                setStroke(dp(1), 0x6638BDF8.toInt())
            }
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onToggleStation()
            }
        }

        statusDot = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ThemeColors.AccentAmber)
            }
            layoutParams = LayoutParams(dp(5), dp(5)).apply {
                rightMargin = dp(4)
            }
        }

        lblStationBadge = TextView(context).apply {
            text = "ZGORAJ"
            setTextColor(ThemeColors.AccentBlue)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setSingleLine(true)
        }

        stationPill.addView(statusDot)
        stationPill.addView(lblStationBadge)

        btnLock = WearTopIconButton(
            context = context,
            iconType = WearTopIconButton.IconType.LOCK,
            initialLocked = true
        ) {
            onToggleLock()
        }.apply {
            contentDescription = "Zakleni ali odkleni kontrole"
            layoutParams = LayoutParams(dp(26), dp(25)).apply {
                leftMargin = dp(5)
            }
        }

        btnSettings = WearTopIconButton(
            context = context,
            iconType = WearTopIconButton.IconType.SETTINGS,
            initialLocked = false
        ) {
            onOpenSettings()
        }.apply {
            contentDescription = "Nastavitve"
            layoutParams = LayoutParams(dp(26), dp(25)).apply {
                leftMargin = dp(4)
            }
        }

        addView(stationPill)
        addView(btnLock)
        addView(btnSettings)
    }

    fun bind(isZgoraj: Boolean, isOnline: Boolean, isSending: Boolean) {
        if (isZgoraj) {
            lblStationBadge.text = "ZGORAJ"
            lblStationBadge.setTextColor(ThemeColors.AccentBlue)
            (stationPill.background as? GradientDrawable)?.apply {
                setColor(0x2238BDF8.toInt())
                setStroke(dp(1), 0x6638BDF8.toInt())
            }
        } else {
            lblStationBadge.text = "SPODAJ"
            lblStationBadge.setTextColor(ThemeColors.AccentEmerald)
            (stationPill.background as? GradientDrawable)?.apply {
                setColor(0x2210B981.toInt())
                setStroke(dp(1), 0x6610B981.toInt())
            }
        }

        val dotColor = when {
            isSending -> ThemeColors.AccentBlue
            isOnline -> ThemeColors.AccentEmerald
            else -> ThemeColors.AccentCoral
        }
        (statusDot.background as? GradientDrawable)?.setColor(dotColor)
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
 * Top bar icon button for Wear OS widget headers (Lock padlock & Settings gear).
 */
class WearTopIconButton(
    context: Context,
    private val iconType: IconType,
    initialLocked: Boolean,
    private val onClick: () -> Unit
) : View(context) {

    enum class IconType { LOCK, SETTINGS }

    var isLockedState: Boolean = initialLocked
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
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val iconPath = Path()
    private val rectF = RectF()

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
                    updateVisuals()
                    invalidate()
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onClick()
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
        bgDrawable.cornerRadius = dpF(12.5f)
        val strokePx = max(1, dpF(1f).roundToInt())
        if (iconType == IconType.LOCK && isLockedState) {
            bgDrawable.setColor(if (isPressedState) ThemeColors.BtnPressedBg else ThemeColors.AccentAmberBg)
            bgDrawable.setStroke(strokePx, ThemeColors.AccentAmber)
            iconPaint.color = ThemeColors.AccentAmber
            fillPaint.color = ThemeColors.AccentAmber
        } else {
            bgDrawable.setColor(if (isPressedState) ThemeColors.BtnPressedBg else ThemeColors.BtnBg)
            bgDrawable.setStroke(strokePx, if (isPressedState) ThemeColors.AccentBlue else ThemeColors.BtnBorder)
            val col = if (isPressedState) ThemeColors.AccentBlue else ThemeColors.TextPrimary
            iconPaint.color = col
            fillPaint.color = col
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val iconSize = min(w, h) * 0.62f
        val scale = iconSize / 20f
        val ox = (w - 20f * scale) * 0.5f
        val oy = (h - 20f * scale) * 0.5f

        iconPaint.strokeWidth = max(1.5f, 1.8f * scale)
        iconPath.reset()

        when (iconType) {
            IconType.LOCK -> {
                rectF.set(ox + 4.2f * scale, oy + 9.2f * scale, ox + 15.8f * scale, oy + 17.6f * scale)
                canvas.drawRoundRect(rectF, 2.2f * scale, 2.2f * scale, iconPaint)

                canvas.drawCircle(ox + 10f * scale, oy + 12.8f * scale, 1.15f * scale, fillPaint)
                canvas.drawLine(
                    ox + 10f * scale, oy + 13.5f * scale,
                    ox + 10f * scale, oy + 15.3f * scale,
                    iconPaint
                )

                if (isLockedState) {
                    iconPath.moveTo(ox + 6.8f * scale, oy + 9.2f * scale)
                    iconPath.lineTo(ox + 6.8f * scale, oy + 6.2f * scale)
                    rectF.set(ox + 6.8f * scale, oy + 3.0f * scale, ox + 13.2f * scale, oy + 9.4f * scale)
                    iconPath.arcTo(rectF, 180f, 180f, false)
                    iconPath.lineTo(ox + 13.2f * scale, oy + 9.2f * scale)
                    canvas.drawPath(iconPath, iconPaint)
                } else {
                    iconPath.moveTo(ox + 6.8f * scale, oy + 9.2f * scale)
                    iconPath.lineTo(ox + 6.8f * scale, oy + 5.2f * scale)
                    rectF.set(ox + 6.8f * scale, oy + 2.0f * scale, ox + 13.2f * scale, oy + 8.4f * scale)
                    iconPath.arcTo(rectF, 180f, 165f, false)
                    canvas.drawPath(iconPath, iconPaint)
                }
            }

            IconType.SETTINGS -> {
                val cx = ox + 10f * scale
                val cy = oy + 10f * scale
                val outerR = 7.4f * scale
                val baseR = 5.6f * scale
                val innerR = 2.6f * scale
                val numTeeth = 8
                val step = (2.0 * Math.PI) / (numTeeth * 2)

                for (i in 0 until numTeeth * 2) {
                    val r = if (i % 2 == 0) outerR else baseR
                    val a1 = i * step - step * 0.38
                    val a2 = i * step + step * 0.38
                    val x1 = (cx + r * cos(a1)).toFloat()
                    val y1 = (cy + r * sin(a1)).toFloat()
                    val x2 = (cx + r * cos(a2)).toFloat()
                    val y2 = (cy + r * sin(a2)).toFloat()
                    if (i == 0) {
                        iconPath.moveTo(x1, y1)
                    } else {
                        iconPath.lineTo(x1, y1)
                    }
                    iconPath.lineTo(x2, y2)
                }
                iconPath.close()
                canvas.drawPath(iconPath, iconPaint)
                canvas.drawCircle(cx, cy, innerR, iconPaint)
            }
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

/**
 * Curved bottom action button tailored for the lower hemisphere of a circular watch screen.
 * The outer bottom corner follows a large circular curve so the left/right bottom button pair
 * hugs the round watch bezel without clipping.
 */
class WearCircularBottomButton(
    context: Context,
    private val label: String,
    private val isLeft: Boolean,
    private val textSizeSp: Float = 14f,
    private val accentStripeColor: Int? = null,
    private val isLockedProvider: () -> Boolean,
    private val onClick: () -> Unit
) : View(context) {

    var isActiveState: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private var isPressedState: Boolean = false
    private val shapePath = Path()
    private val rectF = RectF()
    private val stripeRectF = RectF()

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val stripePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    init {
        setOnTouchListener { v, event ->
            if (isLockedProvider()) return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    isPressedState = true
                    invalidate()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    isPressedState = false
                    invalidate()
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    isPressedState = false
                    invalidate()
                    true
                }
                else -> false
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val strokeW = max(1f, dpF(1.1f))
        val halfStroke = strokeW * 0.5f
        rectF.set(halfStroke, halfStroke, w - halfStroke, h - halfStroke)

        val topOuterR = dpF(10f)
        val topInnerR = dpF(7f)
        val botInnerR = dpF(10f)
        val botOuterR = min(w * 0.76f, h * 0.94f).coerceAtLeast(topOuterR)

        val radii = if (isLeft) {
            floatArrayOf(
                topOuterR, topOuterR, // top-left
                topInnerR, topInnerR, // top-right (inner gap)
                botInnerR, botInnerR, // bottom-right (inner gap)
                botOuterR, botOuterR  // bottom-left (curved along circular bezel)
            )
        } else {
            floatArrayOf(
                topInnerR, topInnerR, // top-left (inner gap)
                topOuterR, topOuterR, // top-right
                botOuterR, botOuterR, // bottom-right (curved along circular bezel)
                botInnerR, botInnerR  // bottom-left (inner gap)
            )
        }

        shapePath.reset()
        shapePath.addRoundRect(rectF, radii, Path.Direction.CW)

        fillPaint.color = when {
            isPressedState -> ThemeColors.BtnPressedBg
            isActiveState -> ThemeColors.AccentBlueActiveBg
            else -> ThemeColors.BtnBg
        }
        canvas.drawPath(shapePath, fillPaint)

        if (accentStripeColor != null) {
            val saveCount = canvas.save()
            canvas.clipPath(shapePath)
            stripePaint.color = accentStripeColor
            stripeRectF.set(0f, 0f, w, dpF(3.4f))
            canvas.drawRect(stripeRectF, stripePaint)
            canvas.restoreToCount(saveCount)
        }

        strokePaint.strokeWidth = strokeW
        strokePaint.color = when {
            isPressedState -> accentStripeColor ?: ThemeColors.AccentBlue
            isActiveState -> ThemeColors.AccentBlue
            else -> ThemeColors.BtnBorder
        }
        canvas.drawPath(shapePath, strokePaint)

        textPaint.textSize = spF(textSizeSp)
        textPaint.color = if (isActiveState) ThemeColors.AccentBlue else ThemeColors.TextPrimary
        val opticalShiftX = if (isLeft) dpF(3.5f) else -dpF(3.5f)
        val textCenterX = w * 0.5f + opticalShiftX
        val textCenterY = h * 0.46f - (textPaint.descent() + textPaint.ascent()) * 0.5f
        canvas.drawText(label, textCenterX, textCenterY, textPaint)
    }

    private fun dpF(value: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            resources.displayMetrics
        )
    }

    private fun spF(value: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            value,
            resources.displayMetrics
        )
    }
}

/**
 * WIDGET 1 (Page 0, Left): Time Countdown + Funicular Track + 6 min / 10 min Switch.
 */
class TimeWidgetPage(
    context: Context,
    onToggleStation: () -> Unit,
    onToggleLock: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleShowInterval: () -> Unit,
    private val onSelectIntervalCommand: (Int) -> Unit
) : LinearLayout(context) {

    private val header: WearWidgetHeader
    private val contentCol: LinearLayout
    private val timeDisplayBox: LinearLayout
    private val txtTimerValue: TextView
    private val txtTimerIntervalInline: TextView
    private val timerProgressTrack: FrameLayout
    private val timerProgressFill: View
    val funicularTrackView: FunicularTrackView

    private val btn6Min: WearCircularBottomButton
    private val btn10Min: WearCircularBottomButton

    private var controlsLocked: Boolean = true
    private var lastRatio: Double = 0.0
    private var activeIntervalMin: Int = 0 // 6 or 10 when matching

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setBackgroundColor(ThemeColors.WindowBg)

        // Safe round-watch padding
        val dm = resources.displayMetrics
        val minDim = min(dm.widthPixels, dm.heightPixels)
        val horizPad = (minDim * 0.095f).roundToInt().coerceAtLeast(dp(14))
        val topPad = (minDim * 0.075f).roundToInt().coerceAtLeast(dp(11))
        val botPad = (minDim * 0.06f).roundToInt().coerceAtLeast(dp(9))
        val bottomSideInset = (minDim * 0.045f).roundToInt().coerceAtLeast(dp(8))
        setPadding(horizPad, topPad, horizPad, botPad)

        header = WearWidgetHeader(context, onToggleStation, onToggleLock, onOpenSettings).apply {
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(4)
            }
        }
        addView(header)

        contentCol = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        // Countdown Display Box
        timeDisplayBox = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(6))
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(ThemeColors.SubCardBg)
                setStroke(dp(1), ThemeColors.Divider)
            }
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.22f).apply {
                bottomMargin = dp(4)
            }
            setOnClickListener {
                if (!controlsLocked) {
                    it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onToggleShowInterval()
                }
            }
        }

        val lblTimeSub = TextView(context).apply {
            text = "ČAS DO ODHODA"
            setTextColor(ThemeColors.TextSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
        }

        val numberRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 0, 1f)
        }

        txtTimerValue = TextView(context).apply {
            text = "--:--"
            setTextColor(ThemeColors.TextMuted)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            fontFeatureSettings = "tnum"
            includeFontPadding = false
        }

        txtTimerIntervalInline = TextView(context).apply {
            text = "/--"
            setTextColor(ThemeColors.TextMuted)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            fontFeatureSettings = "tnum"
            includeFontPadding = false
        }

        numberRow.addView(txtTimerValue)
        numberRow.addView(txtTimerIntervalInline)

        timerProgressTrack = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(2.5f).toFloat()
                setColor(ThemeColors.TrackBg)
            }
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(5))
        }
        timerProgressFill = View(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(2.5f).toFloat()
                setColor(ThemeColors.AccentBtnMuted)
            }
            layoutParams = FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        timerProgressTrack.addView(timerProgressFill)
        timerProgressTrack.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateProgressWidth() }

        timeDisplayBox.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> recomputeTimerFontSize() }

        timeDisplayBox.addView(lblTimeSub)
        timeDisplayBox.addView(numberRow)
        timeDisplayBox.addView(timerProgressTrack)
        contentCol.addView(timeDisplayBox)

        // Mini Funicular Car Track Animation
        funicularTrackView = FunicularTrackView(context, isZgornjaToSpodnja = true).apply {
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.56f).apply {
                bottomMargin = dp(4)
            }
        }
        contentCol.addView(funicularTrackView)

        // 6 min / 10 min Circular Watch Bottom Switch Row
        val switchRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.72f).apply {
                leftMargin = bottomSideInset
                rightMargin = bottomSideInset
            }
        }

        btn6Min = WearCircularBottomButton(
            context = context,
            label = "6 min",
            isLeft = true,
            textSizeSp = 13.5f,
            accentStripeColor = null,
            isLockedProvider = { controlsLocked },
            onClick = { onSelectIntervalCommand(10) }
        ).apply {
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                rightMargin = dp(3)
            }
        }

        btn10Min = WearCircularBottomButton(
            context = context,
            label = "10 min",
            isLeft = false,
            textSizeSp = 13.5f,
            accentStripeColor = null,
            isLockedProvider = { controlsLocked },
            onClick = { onSelectIntervalCommand(11) }
        ).apply {
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                leftMargin = dp(3)
            }
        }

        switchRow.addView(btn6Min)
        switchRow.addView(btn10Min)
        contentCol.addView(switchRow)

        addView(contentCol)
        updateSwitchVisuals()
    }

    fun setControlsLocked(locked: Boolean) {
        controlsLocked = locked
        header.btnLock.isLockedState = locked
        val alpha = if (locked) 0.40f else 1.0f
        btn6Min.alpha = alpha
        btn10Min.alpha = alpha
    }

    private fun updateSwitchVisuals() {
        btn6Min.isActiveState = (activeIntervalMin == 6)
        btn10Min.isActiveState = (activeIntervalMin == 10)
    }

    fun render(isZgoraj: Boolean, tracker: StationTelemetryTracker, showIntervalInline: Boolean) {
        header.bind(isZgoraj, tracker.isOnline, tracker.isSendingCommand)
        contentCol.alpha = if (tracker.isDimmed) 0.22f else 1.0f

        txtTimerIntervalInline.visibility = if (showIntervalInline) View.VISIBLE else View.GONE

        if (!tracker.isOnline) {
            txtTimerValue.text = "--:--"
            txtTimerValue.setTextColor(ThemeColors.TextMuted)
            txtTimerIntervalInline.text = "/--"
            lastRatio = 0.0
            activeIntervalMin = 0
            updateProgressWidth()
            updateSwitchVisuals()
            recomputeTimerFontSize()
            return
        }

        val formattedTime = formatMinSec(tracker.remainingMs)
        txtTimerValue.text = formattedTime
        txtTimerIntervalInline.text = formatIntervalInline(tracker.setMs)

        val setMinutes = ((tracker.setMs + 30_000L) / 60_000L).toInt()
        activeIntervalMin = when (setMinutes) {
            6 -> 6
            10 -> 10
            else -> 0
        }
        updateSwitchVisuals()

        when {
            tracker.remainingMs <= 0L || formattedTime == "00:00" -> {
                txtTimerValue.setTextColor(ThemeColors.AccentRed)
                (timerProgressFill.background as? GradientDrawable)?.setColor(ThemeColors.AccentRed)
            }
            tracker.remainingMs <= 60_000L -> {
                txtTimerValue.setTextColor(ThemeColors.AccentAmber)
                (timerProgressFill.background as? GradientDrawable)?.setColor(ThemeColors.AccentAmber)
            }
            else -> {
                txtTimerValue.setTextColor(ThemeColors.TextPrimary)
                (timerProgressFill.background as? GradientDrawable)?.setColor(ThemeColors.AccentBtnMuted)
            }
        }

        lastRatio = if (tracker.setMs > 0L) {
            (tracker.remainingMs.toDouble() / tracker.setMs).coerceIn(0.0, 1.0)
        } else {
            0.0
        }
        updateProgressWidth()
        recomputeTimerFontSize()
    }

    private fun updateProgressWidth() {
        val trackW = timerProgressTrack.width
        if (trackW > 0) {
            val targetW = (trackW * lastRatio).roundToInt()
            val lp = timerProgressFill.layoutParams
            if (lp.width != targetW) {
                lp.width = targetW
                timerProgressFill.layoutParams = lp
            }
        }
    }

    private fun recomputeTimerFontSize() {
        val boxW = timeDisplayBox.width
        val boxH = timeDisplayBox.height
        if (boxW <= 0 || boxH <= 0) return

        val testPaint = Paint().apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            fontFeatureSettings = "tnum"
            textSize = 100f
        }
        val showInline = (txtTimerIntervalInline.visibility == View.VISIBLE)
        val sample = if (showInline) "00:00/00" else "00:00"
        val measuredW = testPaint.measureText(sample)
        val availW = (boxW - dp(16)).toFloat().coerceAtLeast(10f)
        val availH = (boxH - dp(26)).toFloat().coerceAtLeast(10f)
        val sizeByW = if (measuredW > 0f) 100f * (availW * 0.92f / measuredW) else 100f
        val sizeByH = availH * 0.76f
        val px = min(sizeByW, sizeByH).coerceIn(dp(18).toFloat(), dp(44).toFloat())
        txtTimerValue.setTextSize(TypedValue.COMPLEX_UNIT_PX, px)
        txtTimerIntervalInline.setTextSize(TypedValue.COMPLEX_UNIT_PX, px * 0.86f)
    }

    private fun formatMinSec(ms: Long): String {
        val clamped = max(0L, ms)
        val totalSeconds = (clamped + 499L) / 1000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    private fun formatIntervalInline(ms: Long): String {
        if (ms <= 0L) return "/--"
        val totalSeconds = (ms + 499L) / 1000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return if (seconds > 0L) {
            String.format(Locale.US, "/%02d:%02d", minutes, seconds)
        } else {
            String.format(Locale.US, "/%02d", minutes)
        }
    }

    private fun dp(value: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            resources.displayMetrics
        ).roundToInt()
    }

    private fun dp(value: Int): Int = dp(value.toFloat())
}

/**
 * WIDGET 2 (Page 1, Right): Persons / Capacity (+1, -1 circular bottom buttons + long-press counter to reset).
 */
class PersonsWidgetPage(
    context: Context,
    onToggleStation: () -> Unit,
    onToggleLock: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleShowCapacity: () -> Unit,
    onLongPressReset: () -> Unit,
    private val onPersonCommand: (Int) -> Unit
) : LinearLayout(context) {

    private val header: WearWidgetHeader
    private val contentCol: LinearLayout
    private val personsDisplayBox: LinearLayout
    private val txtPersonsCurrent: TextView
    private val txtPersonsMax: TextView
    private val personsProgressTrack: FrameLayout
    private val personsProgressFill: View
    private val btnMinusOne: WearCircularBottomButton
    private val btnPlusOne: WearCircularBottomButton

    private var controlsLocked: Boolean = true
    private var lastRatio: Double = 0.0

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setBackgroundColor(ThemeColors.WindowBg)

        val dm = resources.displayMetrics
        val minDim = min(dm.widthPixels, dm.heightPixels)
        val horizPad = (minDim * 0.095f).roundToInt().coerceAtLeast(dp(14))
        val topPad = (minDim * 0.075f).roundToInt().coerceAtLeast(dp(11))
        val botPad = (minDim * 0.06f).roundToInt().coerceAtLeast(dp(9))
        val bottomSideInset = (minDim * 0.045f).roundToInt().coerceAtLeast(dp(8))
        setPadding(horizPad, topPad, horizPad, botPad)

        header = WearWidgetHeader(context, onToggleStation, onToggleLock, onOpenSettings).apply {
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(4)
            }
        }
        addView(header)

        contentCol = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        // Persons Display Box (tap toggles inline capacity, long-press opens Reset confirmation)
        personsDisplayBox = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(5), dp(8), dp(7))
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(ThemeColors.SubCardBg)
                setStroke(dp(1), ThemeColors.Divider)
            }
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.40f).apply {
                bottomMargin = dp(5)
            }
            setOnClickListener {
                if (!controlsLocked) {
                    it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onToggleShowCapacity()
                }
            }
            setOnLongClickListener {
                if (!controlsLocked) {
                    it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    onLongPressReset()
                    true
                } else {
                    false
                }
            }
        }

        val lblPersonsSub = TextView(context).apply {
            text = "ŠTEVILO OSEB"
            setTextColor(ThemeColors.TextSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
        }

        val numberRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 0, 1f)
        }

        txtPersonsCurrent = TextView(context).apply {
            text = "--"
            setTextColor(ThemeColors.TextMuted)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            fontFeatureSettings = "tnum"
            includeFontPadding = false
        }

        txtPersonsMax = TextView(context).apply {
            text = "/--"
            setTextColor(ThemeColors.TextMuted)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            fontFeatureSettings = "tnum"
            includeFontPadding = false
        }

        numberRow.addView(txtPersonsCurrent)
        numberRow.addView(txtPersonsMax)

        personsProgressTrack = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(2.5f).toFloat()
                setColor(ThemeColors.TrackBg)
            }
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(5))
        }
        personsProgressFill = View(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(2.5f).toFloat()
                setColor(ThemeColors.AccentBtnMuted)
            }
            layoutParams = FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        personsProgressTrack.addView(personsProgressFill)
        personsProgressTrack.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateProgressWidth() }

        personsDisplayBox.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> recomputePersonsFontSize() }

        personsDisplayBox.addView(lblPersonsSub)
        personsDisplayBox.addView(numberRow)
        personsDisplayBox.addView(personsProgressTrack)
        contentCol.addView(personsDisplayBox)

        // -1 and +1 Circular Watch Bottom Buttons Row
        val btnRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.88f).apply {
                leftMargin = bottomSideInset
                rightMargin = bottomSideInset
            }
        }

        btnMinusOne = WearCircularBottomButton(
            context = context,
            label = "− 1",
            isLeft = true,
            textSizeSp = 17f,
            accentStripeColor = ThemeColors.AccentRed,
            isLockedProvider = { controlsLocked },
            onClick = { onPersonCommand(8) }
        ).apply {
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                rightMargin = dp(3)
            }
        }

        btnPlusOne = WearCircularBottomButton(
            context = context,
            label = "+ 1",
            isLeft = false,
            textSizeSp = 17f,
            accentStripeColor = ThemeColors.AccentEmerald,
            isLockedProvider = { controlsLocked },
            onClick = { onPersonCommand(7) }
        ).apply {
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                leftMargin = dp(3)
            }
        }

        btnRow.addView(btnMinusOne)
        btnRow.addView(btnPlusOne)
        contentCol.addView(btnRow)

        addView(contentCol)
    }

    fun setControlsLocked(locked: Boolean) {
        controlsLocked = locked
        header.btnLock.isLockedState = locked
        val alpha = if (locked) 0.40f else 1.0f
        btnMinusOne.alpha = alpha
        btnPlusOne.alpha = alpha
    }

    fun render(isZgoraj: Boolean, tracker: StationTelemetryTracker, showCapacityInline: Boolean) {
        header.bind(isZgoraj, tracker.isOnline, tracker.isSendingCommand)
        contentCol.alpha = if (tracker.isDimmed) 0.22f else 1.0f

        txtPersonsMax.visibility = if (showCapacityInline) View.VISIBLE else View.GONE

        if (!tracker.isOnline) {
            txtPersonsCurrent.text = "--"
            txtPersonsCurrent.setTextColor(ThemeColors.TextMuted)
            txtPersonsMax.text = "/--"
            lastRatio = 0.0
            updateProgressWidth()
            recomputePersonsFontSize()
            return
        }

        txtPersonsCurrent.text = String.format(Locale.US, "%02d", tracker.currentPersons)
        txtPersonsMax.text = String.format(Locale.US, "/%02d", tracker.maxPersons)

        when {
            tracker.maxPersons > 0 && tracker.currentPersons >= tracker.maxPersons -> {
                txtPersonsCurrent.setTextColor(ThemeColors.AccentCoral)
                (personsProgressFill.background as? GradientDrawable)?.setColor(ThemeColors.AccentCoral)
            }
            tracker.maxPersons > 0 && tracker.currentPersons.toDouble() / tracker.maxPersons >= 0.85 -> {
                txtPersonsCurrent.setTextColor(ThemeColors.AccentAmber)
                (personsProgressFill.background as? GradientDrawable)?.setColor(ThemeColors.AccentAmber)
            }
            else -> {
                txtPersonsCurrent.setTextColor(ThemeColors.TextPrimary)
                (personsProgressFill.background as? GradientDrawable)?.setColor(ThemeColors.AccentBtnMuted)
            }
        }

        lastRatio = if (tracker.maxPersons > 0) {
            (tracker.currentPersons.toDouble() / tracker.maxPersons).coerceIn(0.0, 1.0)
        } else {
            0.0
        }
        updateProgressWidth()
        recomputePersonsFontSize()
    }

    private fun updateProgressWidth() {
        val trackW = personsProgressTrack.width
        if (trackW > 0) {
            val targetW = (trackW * lastRatio).roundToInt()
            val lp = personsProgressFill.layoutParams
            if (lp.width != targetW) {
                lp.width = targetW
                personsProgressFill.layoutParams = lp
            }
        }
    }

    private fun recomputePersonsFontSize() {
        val boxW = personsDisplayBox.width
        val boxH = personsDisplayBox.height
        if (boxW <= 0 || boxH <= 0) return

        val testPaint = Paint().apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            fontFeatureSettings = "tnum"
            textSize = 100f
        }
        val showInline = (txtPersonsMax.visibility == View.VISIBLE)
        val sample = if (showInline) "00/00" else "00"
        val measuredW = testPaint.measureText(sample)
        val availW = (boxW - dp(16)).toFloat().coerceAtLeast(10f)
        val availH = (boxH - dp(26)).toFloat().coerceAtLeast(10f)
        val sizeByW = if (measuredW > 0f) 100f * (availW * 0.88f / measuredW) else 100f
        val sizeByH = availH * 0.78f
        val px = min(sizeByW, sizeByH).coerceIn(dp(20).toFloat(), dp(52).toFloat())
        txtPersonsCurrent.setTextSize(TypedValue.COMPLEX_UNIT_PX, px)
        txtPersonsMax.setTextSize(TypedValue.COMPLEX_UNIT_PX, px * 0.88f)
    }

    private fun dp(value: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            resources.displayMetrics
        ).roundToInt()
    }

    private fun dp(value: Int): Int = dp(value.toFloat())
}

/**
 * Horizontal real-time visualization of the single funicular car on tracks,
 * scaled for Wear OS Widget 1.
 */
class FunicularTrackView(
    context: Context,
    var isZgornjaToSpodnja: Boolean = false
) : View(context) {

    private val bgDrawable = GradientDrawable().apply {
        cornerRadius = dpF(7f)
        setColor(ThemeColors.SubCardBg)
        setStroke(max(1, dpF(1f).roundToInt()), ThemeColors.Divider)
    }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val activeTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val carStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }
    private val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val carRect = RectF()
    private val winRect1 = RectF()
    private val winRect2 = RectF()
    private val arrowPath = Path()

    private var isOnline: Boolean = false
    private var isRiding: Boolean = false
    private var isFromSpodnja: Boolean = true
    private var leftToRight: Boolean = true
    private var targetPos: Float = 0f
    private var displayPos: Float = 0f
    private var rideElapsedMs: Long = 0L
    private var totalRideDurationSec: Int = 90
    private var hasInitializedPos: Boolean = false

    init {
        background = bgDrawable
    }

    fun setRideState(
        online: Boolean,
        riding: Boolean,
        isFromSpodnja: Boolean,
        canonicalFraction: Float,
        elapsedMs: Long,
        totalDurationSec: Int = 90
    ) {
        val viewPos = if (isZgornjaToSpodnja) (1f - canonicalFraction) else canonicalFraction
        val clamped = viewPos.coerceIn(0f, 1f)
        this.isOnline = online
        this.isRiding = riding
        this.isFromSpodnja = isFromSpodnja
        this.leftToRight = if (isZgornjaToSpodnja) !isFromSpodnja else isFromSpodnja
        this.targetPos = clamped
        this.rideElapsedMs = elapsedMs
        this.totalRideDurationSec = totalDurationSec.coerceAtLeast(10)

        if (!hasInitializedPos || !riding || abs(clamped - displayPos) > 0.15f) {
            displayPos = clamped
            hasInitializedPos = true
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        if (isRiding && abs(targetPos - displayPos) > 0.0004f) {
            displayPos += (targetPos - displayPos) * 0.25f
            if (abs(targetPos - displayPos) <= 0.0004f) {
                displayPos = targetPos
            } else {
                postInvalidateOnAnimation()
            }
        } else {
            displayPos = targetPos
        }

        val padX = dpF(8f)
        val headerBaselineY = h * 0.34f
        val labelTextSize = (h * 0.25f).coerceIn(dpF(7.5f), dpF(10f))
        labelPaint.textSize = labelTextSize
        statusPaint.textSize = labelTextSize

        val isHeadingToZgornja = isFromSpodnja
        val leftLabelText = if (isZgornjaToSpodnja) "ZGORAJ" else "SPODAJ"
        val rightLabelText = if (isZgornjaToSpodnja) "SPODAJ" else "ZGORAJ"

        val leftActive = isRiding && (if (isZgornjaToSpodnja) isHeadingToZgornja else !isHeadingToZgornja)
        val leftActiveColor = if (isZgornjaToSpodnja) ThemeColors.AccentEmerald else ThemeColors.AccentBlue

        labelPaint.textAlign = Paint.Align.LEFT
        labelPaint.color = when {
            !isOnline -> ThemeColors.TextMuted
            leftActive -> leftActiveColor
            else -> ThemeColors.TextSecondary
        }
        canvas.drawText(leftLabelText, padX, headerBaselineY, labelPaint)

        val rightActive = isRiding && (if (isZgornjaToSpodnja) !isHeadingToZgornja else isHeadingToZgornja)
        val rightActiveColor = if (isZgornjaToSpodnja) ThemeColors.AccentBlue else ThemeColors.AccentEmerald

        labelPaint.textAlign = Paint.Align.RIGHT
        labelPaint.color = when {
            !isOnline -> ThemeColors.TextMuted
            rightActive -> rightActiveColor
            else -> ThemeColors.TextSecondary
        }
        canvas.drawText(rightLabelText, w - padX, headerBaselineY, labelPaint)

        if (isRiding) {
            val maxSec = totalRideDurationSec.toLong()
            val sec = (rideElapsedMs / 1000L).coerceIn(0L, maxSec)
            val statusStr = if (leftToRight) "▶${sec}s" else "◀${sec}s"
            statusPaint.color = if (isHeadingToZgornja) ThemeColors.AccentEmerald else ThemeColors.AccentBlue
            canvas.drawText(statusStr, w * 0.5f, headerBaselineY, statusPaint)
        }

        val carHalfW = (h * 0.30f).coerceIn(dpF(9f), dpF(13f))
        val carH = (h * 0.33f).coerceIn(dpF(9f), dpF(13f))
        val trackStartX = padX + carHalfW + dpF(3f)
        val trackEndX = w - padX - carHalfW - dpF(3f)
        if (trackEndX <= trackStartX) return

        val railY = h * 0.84f

        trackPaint.strokeWidth = max(1f, dpF(1.1f))
        trackPaint.color = ThemeColors.Divider
        val sleeperTop = railY - dpF(1.2f)
        val sleeperBot = railY + dpF(2.8f)
        val numSleepers = 14
        for (i in 0..numSleepers) {
            val tx = trackStartX + (trackEndX - trackStartX) * (i.toFloat() / numSleepers.toFloat())
            canvas.drawLine(tx, sleeperTop, tx, sleeperBot, trackPaint)
        }

        trackPaint.strokeWidth = max(1.6f, dpF(1.8f))
        trackPaint.color = ThemeColors.BtnBorder
        canvas.drawLine(trackStartX, railY, trackEndX, railY, trackPaint)

        val carX = trackStartX + displayPos * (trackEndX - trackStartX)
        val activeColor = when {
            !isOnline -> ThemeColors.TextMuted
            isRiding && isHeadingToZgornja -> ThemeColors.AccentEmerald
            isRiding && !isHeadingToZgornja -> ThemeColors.AccentBlue
            else -> ThemeColors.TextSecondary
        }

        if (isOnline && isRiding) {
            activeTrackPaint.strokeWidth = max(1.8f, dpF(2.0f))
            activeTrackPaint.color = activeColor
            val originX = if (leftToRight) trackStartX else trackEndX
            canvas.drawLine(originX, railY, carX, railY, activeTrackPaint)
        }

        val stopRadius = dpF(2.4f)
        fillPaint.color = if (leftActive) leftActiveColor else ThemeColors.BtnBorder
        canvas.drawCircle(trackStartX, railY, stopRadius, fillPaint)
        fillPaint.color = if (rightActive) rightActiveColor else ThemeColors.BtnBorder
        canvas.drawCircle(trackEndX, railY, stopRadius, fillPaint)

        val wheelR = dpF(1.8f)
        val carBottom = railY - wheelR * 0.85f
        val carTop = carBottom - carH
        val carLeft = carX - carHalfW
        val carRight = carX + carHalfW

        carRect.set(carLeft, carTop, carRight, carBottom)
        fillPaint.color = if (!isOnline) {
            ThemeColors.BtnBg
        } else if (isRiding) {
            if (isHeadingToZgornja) Color.parseColor("#112E28") else Color.parseColor("#142840")
        } else {
            ThemeColors.BtnBg
        }
        canvas.drawRoundRect(carRect, dpF(3f), dpF(3f), fillPaint)

        carStrokePaint.strokeWidth = max(1.3f, dpF(1.4f))
        carStrokePaint.color = activeColor
        canvas.drawRoundRect(carRect, dpF(3f), dpF(3f), carStrokePaint)

        val winPadX = carHalfW * 0.24f
        val winGap = carHalfW * 0.14f
        val winTop = carTop + carH * 0.22f
        val winBot = carTop + carH * 0.58f
        val winW = ((carHalfW * 2f) - winPadX * 2f - winGap) * 0.5f
        if (winW > 1f && winBot > winTop) {
            fillPaint.color = if (isOnline && isRiding) activeColor else ThemeColors.TextMuted
            fillPaint.alpha = if (isOnline && isRiding) 210 else 110
            winRect1.set(carLeft + winPadX, winTop, carLeft + winPadX + winW, winBot)
            winRect2.set(carLeft + winPadX + winW + winGap, winTop, carRight - winPadX, winBot)
            canvas.drawRoundRect(winRect1, dpF(1.2f), dpF(1.2f), fillPaint)
            canvas.drawRoundRect(winRect2, dpF(1.2f), dpF(1.2f), fillPaint)
            fillPaint.alpha = 255
        }

        fillPaint.color = activeColor
        canvas.drawCircle(carLeft + carHalfW * 0.48f, railY - wheelR * 0.2f, wheelR, fillPaint)
        canvas.drawCircle(carRight - carHalfW * 0.48f, railY - wheelR * 0.2f, wheelR, fillPaint)

        if (isOnline && isRiding) {
            val arrowGap = dpF(3f)
            val arrowLen = dpF(4f)
            val arrowHalfH = dpF(2.5f)
            val midY = (carTop + carBottom) * 0.5f
            arrowPath.reset()
            if (leftToRight) {
                val ax = carRight + arrowGap
                if (ax + arrowLen < trackEndX - dpF(2f)) {
                    arrowPath.moveTo(ax, midY - arrowHalfH)
                    arrowPath.lineTo(ax + arrowLen, midY)
                    arrowPath.lineTo(ax, midY + arrowHalfH)
                    arrowPath.close()
                    canvas.drawPath(arrowPath, fillPaint)
                }
            } else {
                val ax = carLeft - arrowGap
                if (ax - arrowLen > trackStartX + dpF(2f)) {
                    arrowPath.moveTo(ax, midY - arrowHalfH)
                    arrowPath.lineTo(ax - arrowLen, midY)
                    arrowPath.lineTo(ax, midY + arrowHalfH)
                    arrowPath.close()
                    canvas.drawPath(arrowPath, fillPaint)
                }
            }
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
