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
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Base64
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
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
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
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
    var rideDurationSec: Int = 90
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
        if (prefs.contains("rideDurationSec")) rideDurationSec = prefs.getInt("rideDurationSec", 90).coerceIn(10, 600)
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
            .putInt("rideDurationSec", rideDurationSec.coerceIn(10, 600))
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

    fun playTimeSingle() = playPcm(pcmTimeSingle, 44100)
    fun playTimeTriple() = playPcm(pcmTimeTriple, 44100)
    fun playPersonsSingle() = playPcm(pcmPersonsSingle, 44100)
    fun playPersonsTriple() = playPcm(pcmPersonsTriple, 44100)

    fun playSpeechPcm(samples: ShortArray, sampleRate: Int) {
        if (samples.isEmpty()) return
        executor.execute {
            playPcmSync(samples, sampleRate, isSpeech = true)
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
                track = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    AudioTrack.Builder()
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
                } else {
                    @Suppress("DEPRECATION")
                    AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        streamBufBytes,
                        AudioTrack.MODE_STREAM
                    )
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    track.setVolume(1.0f)
                }
                track.play()
                var offset = 0
                val chunkLen = 4096
                while (offset < samples.size) {
                    val toWrite = min(chunkLen, samples.size - offset)
                    val written = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        track.write(samples, offset, toWrite, AudioTrack.WRITE_BLOCKING)
                    } else {
                        track.write(samples, offset, toWrite)
                    }
                    if (written <= 0) break
                    offset += written
                }
                Thread.sleep(110L)
                track.stop()
                offset > 0
            } else {
                val bufferBytes = samples.size * 2
                track = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
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

/**
 * Slovenian voice announcement on departure ("Obvestilo ob odhodu").
 * Uses Google TTS (Slovenian HTTPS MP3 with local caching + Google Speech Services `com.google.android.tts`)
 * and Android system TextToSpeech (`android.speech.tts.TextToSpeech`) with automatic mutual fallback
 * and distortion-free dynamic range compression.
 */
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                engine.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
            }
            val slLocale = Locale("sl", "SI")
            val res = engine.setLanguage(slLocale)
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.setLanguage(Locale("sl"))
            }
            engine.setSpeechRate(0.97f)
            engine.setPitch(1.0f)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
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
                        val inBuf = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            codec.getInputBuffer(inIdx)
                        } else {
                            @Suppress("DEPRECATION")
                            codec.inputBuffers[inIdx]
                        }
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
                        val outBuf = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            codec.getOutputBuffer(outIdx)
                        } else {
                            @Suppress("DEPRECATION")
                            codec.outputBuffers[outIdx]
                        }
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                } else {
                    @Suppress("DEPRECATION")
                    setAudioStreamType(AudioManager.STREAM_MUSIC)
                }
                setDataSource(mp3File.absolutePath)
                setVolume(1.0f, 1.0f)
                prepare()
            }
            if (gainMultiplier > 1.05 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
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
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                engine.synthesizeToFile(trimmed, params, outFile, utteranceId)
            } else {
                TextToSpeech.ERROR
            }
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
            val res = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                engine.speak(trimmed, TextToSpeech.QUEUE_FLUSH, speakParams, "direct_$utteranceId")
            } else {
                @Suppress("DEPRECATION")
                engine.speak(trimmed, TextToSpeech.QUEUE_FLUSH, null)
            }
            res == TextToSpeech.SUCCESS
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Distortion-free dynamic range compressor & speech leveler.
     * Keeps speech crisp and natural at 100% and smoothly boosts quieter syllables at >100%
     * without introducing harsh waveform clipping.
     */
    private fun amplifySpeechPcm(rawSamples: ShortArray, sampleRate: Int, gainMultiplier: Double): ShortArray {
        val frameCount = rawSamples.size
        if (frameCount == 0) return rawSamples

        var peak = 1
        for (i in 0 until frameCount) {
            val v = rawSamples[i].toInt()
            val absVal = if (v == -32768) 32767 else abs(v)
            if (absVal > peak) peak = absVal
        }

        // Normalize clean peak to 29500 (-0.9 dBFS)
        val baseNorm = 29500.0 / peak
        val effectiveGain = gainMultiplier.coerceIn(0.5, 4.0)

        // Smooth envelope follower coefficients (2.5ms attack, 90ms release)
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

            // Smooth gain reduction when envelope exceeds threshold
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

        // Parse RIFF WAVE chunks
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
                    fmtBuf.int // byteRate
                    fmtBuf.short // blockAlign
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

    private lateinit var statusRow: LinearLayout
    private lateinit var lblModeStatus: TextView
    private lateinit var statusBadgeDot: View
    private lateinit var btnLockControls: TopBarIconButton
    private lateinit var lblStationBadge: TextView
    private lateinit var stationPager: VerticalStationPager
    private lateinit var cardZgoraj: StationCardView
    private lateinit var cardSpodaj: StationCardView

    // By default controls are locked (view only) to prevent accidental touches
    private var isControlsLocked: Boolean = true

    private val streamActive = AtomicBoolean(false)
    @Volatile
    private var activeStreamConnection: HttpURLConnection? = null
    @Volatile
    private var lastCloudSyncSendMs: Long = 0L
    @Volatile
    private var lastAppliedStateTs: Long = 0L
    @Volatile
    private var lanZgorajOnline: Boolean = false
    @Volatile
    private var lanSpodajOnline: Boolean = false

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

    private val autoLockRunnable = Runnable {
        if (!isControlsLocked) {
            applyControlsLockState(true)
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
        if (::btnLockControls.isInitialized) {
            btnLockControls.isActive = locked
        }
        if (::cardZgoraj.isInitialized) {
            cardZgoraj.setControlsLocked(locked)
        }
        if (::cardSpodaj.isInitialized) {
            cardSpodaj.setControlsLocked(locked)
        }
        if (!locked) {
            scheduleAutoLockIfNeeded()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = ThemeColors.WindowBg
        window.navigationBarColor = ThemeColors.WindowBg

        config = MobileConfig(this)
        MobileChimePlayer.enabled = config.soundEnabled
        MobileVoiceAnnouncer.voiceEnabled = config.voiceAnnouncementEnabled
        MobileVoiceAnnouncer.voiceTemplate = config.voiceAnnouncementTemplate
        MobileVoiceAnnouncer.voiceGain = config.voiceAnnouncementGain
        MobileVoiceAnnouncer.neuralVoiceName = config.voiceAnnouncementVoice
        MobileVoiceAnnouncer.init(this)

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

        // Separate Zgornja postaja and Spodnja postaja views (one view fits screen, swipe up/down to switch)
        StationCardView.rideDurationSec = config.rideDurationSec
        cardZgoraj = StationCardView(
            context = this,
            stationId = "zgoraj",
            stationTitle = "ZGORNJA POSTAJA",
            initialMuted = config.muteZgoraj,
            initialDimmed = config.dimZgoraj,
            initialShowIntervalBg = config.showIntervalBgZgoraj,
            initialLocked = isControlsLocked,
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

        cardSpodaj = StationCardView(
            context = this,
            stationId = "spodaj",
            stationTitle = "SPODNJA POSTAJA",
            initialMuted = config.muteSpodaj,
            initialDimmed = config.dimSpodaj,
            initialShowIntervalBg = config.showIntervalBgSpodaj,
            initialLocked = isControlsLocked,
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

        cardZgoraj.peerCard = cardSpodaj
        cardSpodaj.peerCard = cardZgoraj
        cardZgoraj.syncSingleFunicularPosition()

        stationPager = VerticalStationPager(this, cardZgoraj, cardSpodaj).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            onStationChanged = { index ->
                updateActiveStationTitle(index)
            }
            onFooterClicked = {
                showSettingsDialog()
            }
        }
        root.addView(stationPager)
        updateActiveStationTitle(0)

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
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

    private fun buildTopBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(6))
        }

        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = dp(8)
            }
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = true
        }

        val lblTitle = TextView(this).apply {
            text = "Kontrola pristopa"
            setTextColor(ThemeColors.TextPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18.5f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        }

        lblStationBadge = TextView(this).apply {
            text = "ZGORAJ"
            setTextColor(ThemeColors.AccentBlue)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(7), dp(2), dp(7), dp(2))
            background = GradientDrawable().apply {
                setColor(0x2238BDF8.toInt())
                cornerRadius = dp(6).toFloat()
                setStroke(dp(1), 0x5538BDF8.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = dp(8)
            }
            setOnClickListener {
                if (::stationPager.isInitialized) {
                    stationPager.toggleStation()
                }
            }
        }

        titleRow.addView(lblTitle)
        titleRow.addView(lblStationBadge)

        statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
        }

        statusBadgeDot = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ThemeColors.AccentRed)
            }
            layoutParams = LinearLayout.LayoutParams(dp(7), dp(7)).apply {
                rightMargin = dp(6)
            }
        }

        lblModeStatus = TextView(this).apply {
            text = "Ni povezave"
            setTextColor(ThemeColors.AccentRed)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        }

        statusRow.addView(statusBadgeDot)
        statusRow.addView(lblModeStatus)

        titleCol.addView(titleRow)
        titleCol.addView(statusRow)

        val btnSizeW = dp(38)
        val btnSizeH = dp(36)

        btnLockControls = TopBarIconButton(
            context = this,
            iconType = TopBarIconButton.IconType.LOCK,
            initialActive = isControlsLocked
        ) {
            applyControlsLockState(!isControlsLocked)
        }.apply {
            contentDescription = "Zakleni ali odkleni kontrole"
            layoutParams = LinearLayout.LayoutParams(btnSizeW, btnSizeH)
        }

        val btnSettings = TopBarIconButton(
            context = this,
            iconType = TopBarIconButton.IconType.SETTINGS,
            initialActive = false
        ) {
            showSettingsDialog()
        }.apply {
            contentDescription = "Nastavitve"
            layoutParams = LinearLayout.LayoutParams(btnSizeW, btnSizeH).apply {
                leftMargin = dp(7)
            }
        }

        bar.addView(titleCol)
        bar.addView(btnLockControls)
        bar.addView(btnSettings)
        return bar
    }

    private fun updateActiveStationTitle(index: Int) {
        if (!::lblStationBadge.isInitialized) return
        if (index == 0) {
            lblStationBadge.text = "ZGORAJ"
            lblStationBadge.setTextColor(ThemeColors.AccentBlue)
            (lblStationBadge.background as? GradientDrawable)?.apply {
                setColor(0x2238BDF8.toInt())
                setStroke(dp(1), 0x5538BDF8.toInt())
            }
        } else {
            lblStationBadge.text = "SPODAJ"
            lblStationBadge.setTextColor(ThemeColors.AccentEmerald)
            (lblStationBadge.background as? GradientDrawable)?.apply {
                setColor(0x2210B981.toInt())
                setStroke(dp(1), 0x5510B981.toInt())
            }
        }
    }

    private fun setTopConnectionStatus(isConnected: Boolean) {
        uiHandler.post {
            if (isConnected) {
                statusRow.visibility = View.GONE
            } else {
                lblModeStatus.text = "Ni povezave"
                lblModeStatus.setTextColor(ThemeColors.AccentRed)
                (statusBadgeDot.background as? GradientDrawable)?.setColor(ThemeColors.AccentRed)
                statusRow.visibility = View.VISIBLE
            }
        }
    }

    private fun restartNetworking() {
        uiHandler.removeCallbacks(lanPollRunnable)
        stopCloudStream()
        lastAppliedStateTs = 0L
        lanZgorajOnline = false
        lanSpodajOnline = false

        if (config.useCloudRelay) {
            startCloudStream()
            requestCloudSync()
            uiHandler.postDelayed({ fetchLatestCachedCloudState() }, 700L)
        } else {
            uiHandler.post(lanPollRunnable)
        }
    }

    private fun handleOperatorCommand(station: String, cmdId: Int) {
        if (isControlsLocked) return
        executeStationCommandNow(station, cmdId)
    }

    private fun executeStationCommandNow(station: String, cmdId: Int) {
        val targetCard = if (station == "zgoraj") cardZgoraj else cardSpodaj
        targetCard.showSendingIndicator()

        if (config.useCloudRelay) {
            ioExecutor.execute {
                val ok = sendCloudCommand(station, cmdId)
                if (!ok) {
                    setTopConnectionStatus(false)
                } else {
                    setTopConnectionStatus(true)
                    uiHandler.postDelayed({ fetchLatestCachedCloudState() }, 1200L)
                }
            }
        } else {
            val ip = if (station == "zgoraj") config.ipZgoraj else config.ipSpodaj
            ioExecutor.execute {
                try {
                    val state = DirectLanFunicularClient.executeComplexCommand(ip, cmdId)
                    if (station == "zgoraj") lanZgorajOnline = true else lanSpodajOnline = true
                    setTopConnectionStatus(true)
                    uiHandler.post {
                        targetCard.applyState(state, forceSyncTime = true, isCachedState = false)
                    }
                } catch (_: Throwable) {
                    if (station == "zgoraj") lanZgorajOnline = false else lanSpodajOnline = false
                    if (!lanZgorajOnline && !lanSpodajOnline) {
                        setTopConnectionStatus(false)
                    }
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
                lanZgorajOnline = true
                setTopConnectionStatus(true)
                uiHandler.post { cardZgoraj.applyState(stZ, forceSyncTime, isCachedState = false) }
            } catch (_: Throwable) {
                lanZgorajOnline = false
                if (!lanSpodajOnline) setTopConnectionStatus(false)
                uiHandler.post { cardZgoraj.setOfflineState() }
            }
        }
        ioExecutor.execute {
            try {
                val stS = DirectLanFunicularClient.sendCommand(config.ipSpodaj, 80, 0, 1800)
                lanSpodajOnline = true
                setTopConnectionStatus(true)
                uiHandler.post { cardSpodaj.applyState(stS, forceSyncTime, isCachedState = false) }
            } catch (_: Throwable) {
                lanSpodajOnline = false
                if (!lanZgorajOnline) setTopConnectionStatus(false)
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
                        setTopConnectionStatus(true)
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
                        setTopConnectionStatus(false)
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
                setTopConnectionStatus(zState.isOnline || sState.isOnline)
                if (zState.isOnline) {
                    cardZgoraj.applyState(zState, forceSyncTime = true, isCachedState = fromCache)
                } else {
                    cardZgoraj.setOfflineState()
                }

                if (sState.isOnline) {
                    cardSpodaj.applyState(sState, forceSyncTime = true, isCachedState = fromCache)
                } else {
                    cardSpodaj.setOfflineState()
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun showSettingsDialog() {
        var useCloudMode = config.useCloudRelay
        var soundOn = config.soundEnabled
        var voiceOn = config.voiceAnnouncementEnabled
        var isAndroidVoice = config.voiceAnnouncementVoice.equals(MobileConfig.ANDROID_TTS_VOICE, ignoreCase = true)
        val initialStep = ((config.voiceAnnouncementGain.coerceIn(1.0, 4.0) - 1.0) / 0.25).roundToInt().coerceIn(0, 12)
        var currentGain = 1.0 + initialStep * 0.25
        var selectedAutoLockSec = config.autoLockTimeoutSec
        var lastTimedAutoLockSec = if (selectedAutoLockSec in listOf(5, 10, 15, 30, 60)) selectedAutoLockSec else 15

        val workingChannels = config.savedChannels.map { it.copy() }.toMutableList()
        if (workingChannels.none { it.code.equals(config.cloudChannelId, ignoreCase = true) }) {
            workingChannels.add(0, SavedChannel(config.cloudChannelId, "", config.cloudPin))
        }

        val rootDialogLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(ThemeColors.WindowBg)
                setStroke(dp(1), ThemeColors.CardBorder)
            }
        }

        // Sticky Top Header
        val headerBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(14), dp(14), dp(12))
        }
        val headerTitleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val lblDialogTitle = TextView(this).apply {
            text = "Nastavitve"
            setTextColor(ThemeColors.TextPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        val lblDialogSub = TextView(this).apply {
            text = "Povezava, odklep kontrol, vožnja in zvok"
            setTextColor(ThemeColors.TextMuted)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
        }
        headerTitleCol.addView(lblDialogTitle)
        headerTitleCol.addView(lblDialogSub)

        val btnCloseDialog = TextView(this).apply {
            text = "✕"
            gravity = Gravity.CENTER
            setTextColor(ThemeColors.TextSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(ThemeColors.BtnBg)
                setStroke(dp(1), ThemeColors.BtnBorder)
            }
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(34))
        }
        headerBar.addView(headerTitleCol)
        headerBar.addView(btnCloseDialog)
        rootDialogLayout.addView(headerBar)

        rootDialogLayout.addView(View(this).apply {
            setBackgroundColor(ThemeColors.Divider)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
        })

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }

        // =========================================================================
        // 1. POVEZAVA & SHRANJENI KANALI (PC-JI)
        // =========================================================================
        val connCard = createSettingsCard()
        connCard.addView(createSectionHeader("POVEZAVA", "Izberite način komunikacije s postajama"))

        val modeSegmentRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply {
                cornerRadius = dp(9).toFloat()
                setColor(ThemeColors.SubCardBg)
                setStroke(dp(1), ThemeColors.Divider)
            }
            setPadding(dp(3), dp(3), dp(3), dp(3))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(6)
                bottomMargin = dp(10)
            }
        }

        val btnModeCloud = TextView(this).apply {
            text = "Oblačni most (PC)"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(8), dp(9), dp(8), dp(9))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnModeLan = TextView(this).apply {
            text = "Lokalni LAN / VPN"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(8), dp(9), dp(8), dp(9))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        modeSegmentRow.addView(btnModeCloud)
        modeSegmentRow.addView(btnModeLan)
        connCard.addView(modeSegmentRow)

        val cloudSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val lanSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Saved Channels (PCs) selector
        val savedHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(4))
        }
        val lblSavedChannels = TextView(this).apply {
            text = "Shranjeni PC kanali (tapnite za izbiro):"
            setTextColor(ThemeColors.TextSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        savedHeaderRow.addView(lblSavedChannels)
        cloudSection.addView(savedHeaderRow)

        val savedChannelsListLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(8)
            }
        }
        cloudSection.addView(savedChannelsListLayout)

        val currentSaved = workingChannels.firstOrNull { it.code.equals(config.cloudChannelId, ignoreCase = true) }
        val channelInputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val colChannelCode = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.15f).apply {
                rightMargin = dp(6)
            }
        }
        colChannelCode.addView(createSettingsLabel("Koda kanala (PC):"))
        val edtChannel = createSettingsInput(config.cloudChannelId).apply {
            hint = "vzpenjaca-3436"
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        colChannelCode.addView(edtChannel)

        val colChannelLabel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        colChannelLabel.addView(createSettingsLabel("Ime PC-ja (neobvezno):"))
        val edtChannelLabel = createSettingsInput(currentSaved?.label ?: "").apply {
            hint = "npr. Glavni PC"
        }
        colChannelLabel.addView(edtChannelLabel)

        channelInputRow.addView(colChannelCode)
        channelInputRow.addView(colChannelLabel)
        cloudSection.addView(channelInputRow)

        cloudSection.addView(createSettingsLabel("Varnostni PIN (če je nastavljen na PC-ju):"))
        val edtPin = createSettingsInput(config.cloudPin).apply {
            hint = "Brez PIN-a (prazno)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        cloudSection.addView(edtPin)

        var rebuildSavedChannelsList: (() -> Unit)? = null

        val btnAddOrUpdateChannel = createDialogMiniButton("+ Shrani kanal na seznam PC-jev", accent = true) {
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
                topMargin = dp(8)
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
                    setPadding(dp(11), dp(8), dp(8), dp(8))
                    background = GradientDrawable().apply {
                        cornerRadius = dp(8).toFloat()
                        setColor(if (isSelected) ThemeColors.AccentBlueActiveBg else ThemeColors.SubCardBg)
                        setStroke(dp(1), if (isSelected) ThemeColors.AccentBlue else ThemeColors.Divider)
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        if (index > 0) topMargin = dp(5)
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
                    layoutParams = LinearLayout.LayoutParams(dp(8), dp(8)).apply {
                        rightMargin = dp(9)
                    }
                }
                row.addView(dot)

                val textCol = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                val titleStr = if (item.label.isNotBlank()) "${item.label}  •  ${item.code}" else item.code
                val lblCode = TextView(this).apply {
                    text = titleStr
                    setTextColor(if (isSelected) ThemeColors.TextPrimary else ThemeColors.TextSecondary)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    typeface = Typeface.create(
                        if (item.label.isNotBlank()) "sans-serif-medium" else "monospace",
                        Typeface.BOLD
                    )
                    setSingleLine(true)
                    ellipsize = TextUtils.TruncateAt.END
                }
                textCol.addView(lblCode)
                row.addView(textCol)

                if (isSelected) {
                    val activeBadge = TextView(this).apply {
                        text = "IZBRAN"
                        setTextColor(ThemeColors.AccentBlue)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                        setPadding(dp(6), dp(2), dp(6), dp(2))
                        background = GradientDrawable().apply {
                            cornerRadius = dp(5).toFloat()
                            setColor(0x2238BDF8.toInt())
                            setStroke(dp(1), 0x5538BDF8.toInt())
                        }
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            rightMargin = if (workingChannels.size > 1) dp(6) else 0
                        }
                    }
                    row.addView(activeBadge)
                }

                if (workingChannels.size > 1) {
                    val btnRemove = TextView(this).apply {
                        text = "✕"
                        gravity = Gravity.CENTER
                        setTextColor(ThemeColors.TextMuted)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                        background = GradientDrawable().apply {
                            cornerRadius = dp(6).toFloat()
                            setColor(ThemeColors.BtnBg)
                            setStroke(dp(1), ThemeColors.BtnBorder)
                        }
                        layoutParams = LinearLayout.LayoutParams(dp(26), dp(26))
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

        cloudSection.addView(createSettingsLabel("Strežnik oblačnega mostu (Firebase URL ali ntfy):"))
        val edtServer = createSettingsInput(config.cloudServer).apply {
            hint = "https://ime-projekta-default-rtdb.europe-west1.firebasedatabase.app"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
        }
        cloudSection.addView(edtServer)

        // LAN fields (side by side)
        val lanRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val lanColZgoraj = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = dp(6)
            }
        }
        lanColZgoraj.addView(createSettingsLabel("IP Zgornja postaja:"))
        val edtIpZgoraj = createSettingsInput(config.ipZgoraj).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        lanColZgoraj.addView(edtIpZgoraj)

        val lanColSpodaj = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        lanColSpodaj.addView(createSettingsLabel("IP Spodnja postaja:"))
        val edtIpSpodaj = createSettingsInput(config.ipSpodaj).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        lanColSpodaj.addView(edtIpSpodaj)

        lanRow.addView(lanColZgoraj)
        lanRow.addView(lanColSpodaj)
        lanSection.addView(lanRow)

        val lblLanHint = TextView(this).apply {
            text = "Neposredna povezava zahteva lokalni Wi-Fi ali VPN dostop do postaj."
            setTextColor(ThemeColors.TextMuted)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setPadding(0, dp(6), 0, 0)
        }
        lanSection.addView(lblLanHint)

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

        // =========================================================================
        // 2. ODKLEPANJE KONTROL
        // =========================================================================
        val lockCard = createSettingsCard()
        lockCard.addView(
            createSectionHeader(
                "ODKLEPANJE KONTROL",
                "Način zaklepanja tipk po odklepu s ključavnico v glavi"
            )
        )

        val lockModeSegmentRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply {
                cornerRadius = dp(9).toFloat()
                setColor(ThemeColors.SubCardBg)
                setStroke(dp(1), ThemeColors.Divider)
            }
            setPadding(dp(3), dp(3), dp(3), dp(3))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(6)
                bottomMargin = dp(8)
            }
        }

        val btnLockManual = TextView(this).apply {
            text = "Dokler ne zaklenem"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnLockTimed = TextView(this).apply {
            text = "Samodejni zaklep"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        lockModeSegmentRow.addView(btnLockManual)
        lockModeSegmentRow.addView(btnLockTimed)
        lockCard.addView(lockModeSegmentRow)

        val lockPresetsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        lockPresetsContainer.addView(createSettingsLabel("Samodejno zakleni po preteku:"))

        val lockPresetRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(2)
            }
        }
        val lockPresets = listOf(5, 10, 15, 30, 60)
        val lockPresetButtons = mutableListOf<Pair<Int, TextView>>()

        val lblLockStatusHint = TextView(this).apply {
            setTextColor(ThemeColors.TextMuted)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setPadding(0, dp(6), 0, 0)
        }

        fun updateAutoLockUi() {
            val isManual = (selectedAutoLockSec == 0)
            styleSegmentTab(btnLockManual, isManual)
            styleSegmentTab(btnLockTimed, !isManual)
            for ((sec, btn) in lockPresetButtons) {
                val active = (!isManual && selectedAutoLockSec == sec)
                btn.background = GradientDrawable().apply {
                    cornerRadius = dp(7).toFloat()
                    setColor(if (active) ThemeColors.AccentBlueActiveBg else ThemeColors.BtnBg)
                    setStroke(dp(1), if (active) ThemeColors.AccentBlue else ThemeColors.BtnBorder)
                }
                btn.setTextColor(if (active) ThemeColors.AccentBlue else ThemeColors.TextPrimary)
            }
            lblLockStatusHint.text = if (isManual) {
                "Kontrole ostanejo odklenjene, dokler jih ponovno ročno ne zaklenete."
            } else {
                "Po odklepu se kontrole čez ${selectedAutoLockSec} s samodejno zaklenejo."
            }
        }

        for ((idx, sec) in lockPresets.withIndex()) {
            val btn = createDialogMiniButton("${sec}s") {
                selectedAutoLockSec = sec
                lastTimedAutoLockSec = sec
                updateAutoLockUi()
            }.apply {
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (idx < lockPresets.size - 1) rightMargin = dp(5)
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

        lockPresetsContainer.addView(lockPresetRow)
        lockCard.addView(lockPresetsContainer)
        lockCard.addView(lblLockStatusHint)
        updateAutoLockUi()
        container.addView(lockCard)

        // =========================================================================
        // 3. TRAJANJE VOŽNJE VZPENJAČE
        // =========================================================================
        val rideCard = createSettingsCard()
        rideCard.addView(createSectionHeader("TRAJANJE VOŽNJE", "Čas vožnje za prikaz položaja kabine na tirih"))

        val rideRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
        }
        val edtRideDuration = createSettingsInput(config.rideDurationSec.toString()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            gravity = Gravity.CENTER
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(dp(74), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                rightMargin = dp(8)
            }
        }
        val lblSecUnit = TextView(this).apply {
            text = "sekund"
            setTextColor(ThemeColors.TextSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
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
                    cornerRadius = dp(7).toFloat()
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
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (idx < presets.size - 1) rightMargin = dp(5)
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
        container.addView(rideCard)

        // =========================================================================
        // 3. ZVOK IN GLASOVNO OBVESTILO OB ODHODU
        // =========================================================================
        val audioCard = createSettingsCard()
        audioCard.addView(createSectionHeader("ZVOK IN GOVOR", "Zvočna opozorila in glasovno obvestilo ob odhodu"))

        val soundToggleRow = createModernToggleRow(
            title = "Zvočna opozorila (zvončki)",
            subtitle = "Piski ob 1 min do odhoda in ob polni kabini",
            initialChecked = soundOn
        ) { checked ->
            soundOn = checked
        }
        audioCard.addView(soundToggleRow)

        val voiceDetailsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (voiceOn) View.VISIBLE else View.GONE
            setPadding(0, dp(6), 0, 0)
        }

        val voiceToggleRow = createModernToggleRow(
            title = "Glasovno obvestilo ob odhodu",
            subtitle = "Izgovori čas naslednje vožnje ob odhodu",
            initialChecked = voiceOn
        ) { checked ->
            voiceOn = checked
            voiceDetailsContainer.visibility = if (checked) View.VISIBLE else View.GONE
        }
        audioCard.addView(voiceToggleRow)

        voiceDetailsContainer.addView(createSettingsLabel("Govorni pogon (samodejna medsebojna rezerva):"))
        val engineSegmentRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply {
                cornerRadius = dp(9).toFloat()
                setColor(ThemeColors.SubCardBg)
                setStroke(dp(1), ThemeColors.Divider)
            }
            setPadding(dp(3), dp(3), dp(3), dp(3))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(4)
                bottomMargin = dp(4)
            }
        }
        val btnEngineGoogle = TextView(this).apply {
            text = "Google TTS (SL)"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnEngineAndroid = TextView(this).apply {
            text = "Android TTS"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(8), dp(8), dp(8), dp(8))
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

        // Gain header row (100 % .. 400 %, step 25 %)
        val gainRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(2))
        }
        val lblGainTitle = TextView(this).apply {
            text = "Glasnost obvestila (ojačanje):"
            setTextColor(ThemeColors.TextSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val lblGainVal = TextView(this).apply {
            text = "${(currentGain * 100.0).roundToInt()} %"
            setTextColor(ThemeColors.AccentEmerald)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        gainRow.addView(lblGainTitle)
        gainRow.addView(lblGainVal)
        voiceDetailsContainer.addView(gainRow)

        val seekGain = SeekBar(this).apply {
            max = 12 // 0 -> 100%, 12 -> 400% (25% steps)
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

        voiceDetailsContainer.addView(createSettingsLabel("Besedilo obvestila ({minute} = interval naslednje vožnje):"))
        val edtVoiceTemplate = createSettingsInput(
            config.voiceAnnouncementTemplate.ifBlank { MobileConfig.DEFAULT_VOICE_TEMPLATE },
            singleLine = false
        ).apply {
            minLines = 2
            maxLines = 3
        }
        voiceDetailsContainer.addView(edtVoiceTemplate)

        val lblVoicePreview = TextView(this).apply {
            text = "Primer: \"${MobileVoiceAnnouncer.buildAnnouncementText(edtVoiceTemplate.text.toString(), 10)}\""
            setTextColor(ThemeColors.TextMuted)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setPadding(dp(2), dp(5), dp(2), dp(8))
        }
        edtVoiceTemplate.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val sample = MobileVoiceAnnouncer.buildAnnouncementText(s?.toString(), 10)
                lblVoicePreview.text = "Primer: \"$sample\""
            }
        })
        voiceDetailsContainer.addView(lblVoicePreview)

        val voiceBtnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val btnDefaultVoice = createDialogMiniButton("Privzeto besedilo") {
            edtVoiceTemplate.setText(MobileConfig.DEFAULT_VOICE_TEMPLATE)
        }.apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = dp(6)
            }
        }
        val btnTestVoice = createDialogMiniButton("Preizkusi glas", accent = true) {
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

        scroll.addView(container)
        rootDialogLayout.addView(scroll)

        // Sticky Bottom Action Bar
        rootDialogLayout.addView(View(this).apply {
            setBackgroundColor(ThemeColors.Divider)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
        })

        val footerActionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(12))
        }
        val btnCancel = TextView(this).apply {
            text = "Prekliči"
            gravity = Gravity.CENTER
            setTextColor(ThemeColors.TextSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(9).toFloat()
                setColor(ThemeColors.BtnBg)
                setStroke(dp(1), ThemeColors.BtnBorder)
            }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = dp(8)
            }
        }
        val btnSave = TextView(this).apply {
            text = "Shrani nastavitve"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#06131F"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(9).toFloat()
                setColor(ThemeColors.AccentBlue)
            }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.35f)
        }
        footerActionRow.addView(btnCancel)
        footerActionRow.addView(btnSave)
        rootDialogLayout.addView(footerActionRow)

        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setView(rootDialogLayout)
            .create()

        btnCloseDialog.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            dialog.dismiss()
        }
        btnCancel.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            dialog.dismiss()
        }
        btnSave.setOnClickListener {
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
            config.autoLockTimeoutSec = selectedAutoLockSec
            StationCardView.rideDurationSec = config.rideDurationSec
            cardZgoraj.syncSingleFunicularPosition()
            cardSpodaj.syncSingleFunicularPosition()

            MobileChimePlayer.enabled = config.soundEnabled
            MobileVoiceAnnouncer.voiceEnabled = config.voiceAnnouncementEnabled
            MobileVoiceAnnouncer.voiceTemplate = config.voiceAnnouncementTemplate
            MobileVoiceAnnouncer.voiceGain = config.voiceAnnouncementGain
            MobileVoiceAnnouncer.neuralVoiceName = config.voiceAnnouncementVoice
            MobileVoiceAnnouncer.prewarmCommonAnnouncements()

            config.save()
            scheduleAutoLockIfNeeded()
            restartNetworking()
            dialog.dismiss()
        }

        dialog.show()
        dialog.window?.setBackgroundDrawable(GradientDrawable().apply { setColor(Color.TRANSPARENT) })
    }

    private fun createSettingsCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(ThemeColors.CardBg)
                setStroke(dp(1), ThemeColors.CardBorder)
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(12)
            }
        }
    }

    private fun createSectionHeader(title: String, subtitle: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(6))
            addView(TextView(this@MainActivity).apply {
                text = title
                setTextColor(ThemeColors.AccentBlue)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                letterSpacing = 0.06f
            })
            addView(TextView(this@MainActivity).apply {
                text = subtitle
                setTextColor(ThemeColors.TextMuted)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            })
        }
    }

    private fun styleSegmentTab(tab: TextView, selected: Boolean) {
        if (selected) {
            tab.setTextColor(ThemeColors.TextPrimary)
            tab.background = GradientDrawable().apply {
                cornerRadius = dp(7).toFloat()
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
        subtitle: String,
        initialChecked: Boolean,
        onChanged: (Boolean) -> Unit
    ): LinearLayout {
        var isChecked = initialChecked
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(11), dp(9), dp(11), dp(9))
            background = GradientDrawable().apply {
                cornerRadius = dp(9).toFloat()
                setColor(ThemeColors.SubCardBg)
                setStroke(dp(1), ThemeColors.Divider)
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(6)
            }
        }
        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = dp(8)
            }
        }
        val lblTitle = TextView(this).apply {
            text = title
            setTextColor(ThemeColors.TextPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        val lblSub = TextView(this).apply {
            text = subtitle
            setTextColor(ThemeColors.TextMuted)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        }
        textCol.addView(lblTitle)
        textCol.addView(lblSub)

        val pillStatus = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(10), dp(5), dp(10), dp(5))
        }

        fun updateVisual() {
            pillStatus.text = if (isChecked) "VKLOP" else "IZKLOP"
            pillStatus.setTextColor(if (isChecked) ThemeColors.AccentEmerald else ThemeColors.TextMuted)
            pillStatus.background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(if (isChecked) 0x2210B981.toInt() else ThemeColors.BtnBg)
                setStroke(dp(1), if (isChecked) ThemeColors.AccentEmerald else ThemeColors.BtnBorder)
            }
        }
        updateVisual()

        row.addView(textCol)
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
            cornerRadius = dp(8).toFloat()
            setColor(if (accent) ThemeColors.AccentBlueActiveBg else ThemeColors.BtnBg)
            setStroke(dp(1), if (accent) ThemeColors.AccentBlue else ThemeColors.BtnBorder)
        }
        return TextView(this).apply {
            text = label
            setTextColor(if (accent) ThemeColors.AccentBlue else ThemeColors.TextPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = bg
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
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
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dp(2), dp(8), dp(2), dp(4))
        }
    }

    private fun createSettingsInput(initialValue: String, singleLine: Boolean = true): EditText {
        return EditText(this).apply {
            setText(initialValue)
            setTextColor(ThemeColors.TextPrimary)
            setHintTextColor(ThemeColors.TextMuted)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            setSingleLine(singleLine)
            setPadding(dp(11), dp(9), dp(11), dp(9))
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
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
 * Top bar vector icon button for Lock/Unlock controls and Settings (Nastavitve).
 */
class TopBarIconButton(
    context: Context,
    private val iconType: IconType,
    initialActive: Boolean,
    private val onClick: () -> Unit
) : View(context) {

    enum class IconType {
        LOCK,
        SETTINGS
    }

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
        bgDrawable.cornerRadius = dpF(8f)
        val strokePx = max(1, dpF(1f).roundToInt())

        if (iconType == IconType.LOCK && isActive) {
            // Locked state: highlighted amber border & icon to clearly indicate locked/view-only mode
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

        val iconSize = min(w, h) * 0.56f
        val scale = iconSize / 20f
        val ox = (w - 20f * scale) * 0.5f
        val oy = (h - 20f * scale) * 0.5f

        iconPaint.strokeWidth = max(1.8f, 1.85f * scale)
        iconPath.reset()

        when (iconType) {
            IconType.LOCK -> {
                // Padlock body
                rectF.set(ox + 4.2f * scale, oy + 9.2f * scale, ox + 15.8f * scale, oy + 17.6f * scale)
                canvas.drawRoundRect(rectF, 2.2f * scale, 2.2f * scale, iconPaint)

                // Keyhole dot + vertical slot
                canvas.drawCircle(ox + 10f * scale, oy + 12.8f * scale, 1.15f * scale, fillPaint)
                canvas.drawLine(
                    ox + 10f * scale, oy + 13.5f * scale,
                    ox + 10f * scale, oy + 15.3f * scale,
                    iconPaint
                )

                // Shackle
                if (isActive) {
                    // Closed shackle
                    iconPath.moveTo(ox + 6.8f * scale, oy + 9.2f * scale)
                    iconPath.lineTo(ox + 6.8f * scale, oy + 6.2f * scale)
                    rectF.set(ox + 6.8f * scale, oy + 3.0f * scale, ox + 13.2f * scale, oy + 9.4f * scale)
                    iconPath.arcTo(rectF, 180f, 180f, false)
                    iconPath.lineTo(ox + 13.2f * scale, oy + 9.2f * scale)
                    canvas.drawPath(iconPath, iconPaint)
                } else {
                    // Open shackle (lifted & open on right)
                    iconPath.moveTo(ox + 6.8f * scale, oy + 9.2f * scale)
                    iconPath.lineTo(ox + 6.8f * scale, oy + 5.2f * scale)
                    rectF.set(ox + 6.8f * scale, oy + 2.0f * scale, ox + 13.2f * scale, oy + 8.4f * scale)
                    iconPath.arcTo(rectF, 180f, 165f, false)
                    canvas.drawPath(iconPath, iconPaint)
                }
            }

            IconType.SETTINGS -> {
                // Gear / cogwheel with 8 teeth and inner circle
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
 * Container that displays one station view at a time (fitting the screen)
 * and switches without cycling:
 * - Swipe UP to go to Spodnja postaja
 * - Swipe DOWN to go to Zgornja postaja
 */
class VerticalStationPager(
    context: Context,
    private val cardZgoraj: StationCardView,
    private val cardSpodaj: StationCardView
) : LinearLayout(context) {

    private val cardStage: FrameLayout
    private var currentIndex: Int = 0 // 0 = Zgornja postaja, 1 = Spodnja postaja
    var onStationChanged: ((Int) -> Unit)? = null
    var onFooterClicked: (() -> Unit)? = null

    private val footerVersionText: TextView

    private val touchSlop: Int = ViewConfiguration.get(context).scaledTouchSlop
    private var downX: Float = 0f
    private var downY: Float = 0f
    private var isDraggingVertical: Boolean = false
    private var velocityTracker: VelocityTracker? = null

    init {
        orientation = VERTICAL
        setPadding(dp(12), dp(2), dp(12), dp(2))

        cardStage = FrameLayout(context).apply {
            clipChildren = true
            clipToPadding = true
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        cardZgoraj.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        cardSpodaj.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        cardSpodaj.visibility = View.GONE

        cardStage.addView(cardZgoraj)
        cardStage.addView(cardSpodaj)
        addView(cardStage)

        // Footer: version only (right-aligned)
        val footerBar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            setPadding(dp(4), dp(2), dp(4), dp(4))
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onFooterClicked?.invoke()
            }
        }

        footerVersionText = TextView(context).apply {
            text = "v1.2.3"
            setTextColor(ThemeColors.TextMuted)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }

        footerBar.addView(footerVersionText)
        addView(footerBar)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                isDraggingVertical = false
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain()
                velocityTracker?.addMovement(ev)
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(ev)
                val dy = ev.y - downY
                val dx = ev.x - downX
                if (abs(dy) > touchSlop && abs(dy) > abs(dx) * 1.15f) {
                    isDraggingVertical = true
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDraggingVertical = false
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

        val activeView = if (currentIndex == 0) cardZgoraj else cardSpodaj

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                isDraggingVertical = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = ev.y - downY
                val dx = ev.x - downX
                if (!isDraggingVertical && abs(dy) > touchSlop && abs(dy) > abs(dx)) {
                    isDraggingVertical = true
                }
                if (isDraggingVertical) {
                    val allowedDirection = (currentIndex == 0 && dy < 0f) || (currentIndex == 1 && dy > 0f)
                    activeView.translationY = if (allowedDirection) dy * 0.28f else 0f
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val dy = ev.y - downY
                velocityTracker?.computeCurrentVelocity(1000)
                val vy = velocityTracker?.yVelocity ?: 0f
                velocityTracker?.recycle()
                velocityTracker = null

                val minSwipeDist = dp(40)
                if (isDraggingVertical) {
                    if (currentIndex == 0 && dy < 0f && (-dy > minSwipeDist || -vy > 380f)) {
                        // Swipe UP -> go to Spodnja postaja
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        switchToStation(1, animateDown = false)
                    } else if (currentIndex == 1 && dy > 0f && (dy > minSwipeDist || vy > 380f)) {
                        // Swipe DOWN -> go to Zgornja postaja
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        switchToStation(0, animateDown = true)
                    } else {
                        activeView.animate().translationY(0f).alpha(1f).setDuration(150L).start()
                    }
                } else {
                    activeView.animate().translationY(0f).alpha(1f).setDuration(150L).start()
                }
                isDraggingVertical = false
                return true
            }
        }
        return super.onTouchEvent(ev)
    }

    fun toggleStation() {
        val next = if (currentIndex == 0) 1 else 0
        switchToStation(next, animateDown = (next == 0))
    }

    private fun switchToStation(targetIndex: Int, animateDown: Boolean) {
        if (targetIndex == currentIndex) return
        val outgoing = if (currentIndex == 0) cardZgoraj else cardSpodaj
        val incoming = if (targetIndex == 0) cardZgoraj else cardSpodaj
        currentIndex = targetIndex
        onStationChanged?.invoke(currentIndex)

        val shift = (if (cardStage.height > 0) cardStage.height else dp(320)) * 0.22f
        val outTargetY = if (animateDown) shift else -shift
        val inStartY = if (animateDown) -shift else shift

        outgoing.animate().cancel()
        incoming.animate().cancel()

        outgoing.animate()
            .translationY(outTargetY)
            .alpha(0f)
            .setDuration(180L)
            .withEndAction {
                outgoing.visibility = View.GONE
                outgoing.translationY = 0f
                outgoing.alpha = 1f
            }
            .start()

        incoming.translationY = inStartY
        incoming.alpha = 0f
        incoming.visibility = View.VISIBLE
        incoming.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(210L)
            .start()
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

    var isLocked: Boolean = false

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
            if (isLocked) return@setOnTouchListener false
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
    initialLocked: Boolean,
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
    private val funicularTrackView: FunicularTrackView

    private val txtPersonsCurrent: TextView
    private val txtPersonsMax: TextView
    private val personsProgressTrack: FrameLayout
    private val personsProgressFill: View

    private val operatorButtons = ArrayList<View>()
    private val operatorButtonLabels = ArrayList<TextView>()

    var peerCard: StationCardView? = null

    private var isOnline = false
    private var remainingMs = 0L
    private var setMs = 0L
    private var currentPersons = 0
    private var maxPersons = 28
    private var isMuted = initialMuted
    private var isDimmed = initialDimmed
    private var showIntervalBg = initialShowIntervalBg
    private var controlsLocked = initialLocked
    private var lastTimeZone = -1
    private var lastPersonsZone = -1
    private var hasInitialTelemetry = false
    private var lastCommandSentMs = 0L

    val currentOnline: Boolean
        get() = isOnline
    val currentRemainingMs: Long
        get() = remainingMs
    val currentSetMs: Long
        get() = setMs

    init {
        orientation = VERTICAL
        val pad = dp(12)
        setPadding(pad, dp(10), pad, dp(10))
        cardBgDrawable = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(ThemeColors.CardBg)
            setStroke(dp(1), ThemeColors.CardBorder)
        }
        background = cardBgDrawable

        // 1. Station Header (with Mute speaker icon, Dim crescent moon icon, and connection status)
        val headerRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), 0, dp(2), dp(8))
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        lblStation = TextView(context).apply {
            text = stationTitle
            setTextColor(ThemeColors.TextPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f)
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
            layoutParams = LayoutParams(dp(32), dp(26)).apply {
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
            layoutParams = LayoutParams(dp(32), dp(26)).apply {
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
            layoutParams = LayoutParams(dp(8), dp(8)).apply {
                rightMargin = dp(6)
            }
        }

        statusText = TextView(context).apply {
            text = "Povezovanje"
            setTextColor(ThemeColors.TextSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }

        statusPanel.addView(statusDot)
        statusPanel.addView(statusText)

        headerRow.addView(lblStation)
        headerRow.addView(btnMute)
        headerRow.addView(btnDim)
        headerRow.addView(statusPanel)
        addView(headerRow)

        // 2. Content Container (fills remaining height and scales proportionally to fit screen size)
        contentContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        // --- TIME SECTION ---
        val timeDisplayBox = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(9).toFloat()
                setColor(ThemeColors.SubCardBg)
                setStroke(dp(1), ThemeColors.Divider)
            }
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.36f).apply {
                bottomMargin = dp(5)
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
        timeDisplayBox.addView(txtBgIntervalValue)

        val timeInnerCol = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(10))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val timeTopRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val lblTimeHeader = TextView(context).apply {
            text = "ČAS DO ODHODA"
            setTextColor(ThemeColors.TextSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        txtSetTimeValue = TextView(context).apply {
            text = "Interval: --:--"
            setTextColor(ThemeColors.TextSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setOnClickListener {
                if (controlsLocked) return@setOnClickListener
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
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 52f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.CENTER
            includeFontPadding = false
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        timerProgressTrack = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(3).toFloat()
                setColor(ThemeColors.TrackBg)
            }
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6))
        }
        timerProgressFill = View(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(3).toFloat()
                setColor(ThemeColors.AccentBtnMuted)
            }
            layoutParams = FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        timerProgressTrack.addView(timerProgressFill)
        timerProgressTrack.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateProgressBars() }

        timeDisplayBox.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            val boxW = v.width
            val boxH = v.height
            if (boxW > 0 && boxH > 0) {
                val targetW = boxW * 0.80f
                val sample = "00:00"
                txtBgIntervalValue.paint.textSize = 100f
                val measured = txtBgIntervalValue.paint.measureText(sample)
                if (measured > 0f) {
                    val maxByHeight = boxH * 0.72f
                    val bgPx = min(100f * (targetW / measured), maxByHeight)
                    txtBgIntervalValue.setTextSize(TypedValue.COMPLEX_UNIT_PX, bgPx)
                }
                val timerPx = (boxH * 0.46f).coerceIn(dp(28).toFloat(), dp(76).toFloat())
                txtTimerValue.setTextSize(TypedValue.COMPLEX_UNIT_PX, timerPx)
            }
        }

        timeInnerCol.addView(timeTopRow)
        timeInnerCol.addView(txtTimerValue)
        timeInnerCol.addView(timerProgressTrack)
        timeDisplayBox.addView(timeInnerCol)
        contentContainer.addView(timeDisplayBox)

        // Time Operator Buttons (2 rows x 3 columns, proportional height)
        val timeBtnRow1 = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.48f)
        }
        timeBtnRow1.addView(createActionButton(2, "+ 1 min", ThemeColors.AccentEmerald))
        timeBtnRow1.addView(createActionButton(3, "+ 5 min", ThemeColors.AccentEmerald))
        timeBtnRow1.addView(createActionButton(11, "10 min", ThemeColors.AccentBtnMuted))
        contentContainer.addView(timeBtnRow1)

        val timeBtnRow2 = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.48f)
        }
        timeBtnRow2.addView(createActionButton(5, "− 1 min", ThemeColors.AccentRed))
        timeBtnRow2.addView(createActionButton(6, "− 5 min", ThemeColors.AccentRed))
        timeBtnRow2.addView(createActionButton(10, "6 min", ThemeColors.AccentBtnMuted))
        contentContainer.addView(timeBtnRow2)

        // Real-time Funicular Car Position Animation (90s ride, Spodnja postaja <-> Zgornja postaja)
        // Placed horizontally between time operator buttons and number of persons window
        funicularTrackView = FunicularTrackView(
            context = context,
            isZgornjaToSpodnja = (stationId == "zgoraj")
        ).apply {
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.56f).apply {
                setMargins(dp(3), dp(5), dp(3), dp(5))
            }
        }
        contentContainer.addView(funicularTrackView)

        // --- PERSONS SECTION ---
        val personsDisplayBox = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(9).toFloat()
                setColor(ThemeColors.SubCardBg)
                setStroke(dp(1), ThemeColors.Divider)
            }
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.36f).apply {
                bottomMargin = dp(5)
            }
        }

        val lblPersonsHeader = TextView(context).apply {
            text = "ŠTEVILO OSEB"
            setTextColor(ThemeColors.TextSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val personsNumberRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        txtPersonsCurrent = TextView(context).apply {
            text = "--"
            setTextColor(ThemeColors.TextPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 52f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            includeFontPadding = false
        }
        txtPersonsMax = TextView(context).apply {
            text = " / --"
            setTextColor(ThemeColors.TextMuted)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            includeFontPadding = false
        }
        personsNumberRow.addView(txtPersonsCurrent)
        personsNumberRow.addView(txtPersonsMax)

        personsDisplayBox.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            val boxH = v.height
            if (boxH > 0) {
                val mainPx = (boxH * 0.46f).coerceIn(dp(28).toFloat(), dp(76).toFloat())
                txtPersonsCurrent.setTextSize(TypedValue.COMPLEX_UNIT_PX, mainPx)
                txtPersonsMax.setTextSize(TypedValue.COMPLEX_UNIT_PX, mainPx * 0.52f)
            }
        }

        personsProgressTrack = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(3).toFloat()
                setColor(ThemeColors.TrackBg)
            }
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6))
        }
        personsProgressFill = View(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(3).toFloat()
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
        val personsButtonsRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.96f)
        }
        val personsLeftCol = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        }
        val btnPlusPerson = createActionButton(7, "+ 1 oseba", ThemeColors.AccentEmerald).apply {
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                setMargins(dp(3), dp(3), dp(3), dp(3))
            }
        }
        val btnMinusPerson = createActionButton(8, "− 1 oseba", ThemeColors.AccentRed).apply {
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                setMargins(dp(3), dp(3), dp(3), dp(3))
            }
        }
        personsLeftCol.addView(btnPlusPerson)
        personsLeftCol.addView(btnMinusPerson)

        val btnResetCounter = createActionButton(9, "Ponastavi\nštevec", ThemeColors.AccentBtnMuted).apply {
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                setMargins(dp(3), dp(3), dp(3), dp(3))
            }
        }
        personsButtonsRow.addView(personsLeftCol)
        personsButtonsRow.addView(btnResetCounter)
        contentContainer.addView(personsButtonsRow)

        addView(contentContainer)
        applyDimVisuals()
        setControlsLocked(controlsLocked)
    }

    fun setControlsLocked(locked: Boolean) {
        controlsLocked = locked
        btnMute.isLocked = locked
        btnDim.isLocked = locked
        val btnAlpha = if (locked) 0.36f else 1.0f
        for (btn in operatorButtons) {
            btn.alpha = btnAlpha
        }
        btnMute.alpha = if (locked) 0.45f else 1.0f
        btnDim.alpha = if (locked) 0.45f else 1.0f
    }

    private fun applyDimVisuals() {
        contentContainer.alpha = if (isDimmed) 0.18f else 1.0f
        lblStation.alpha = if (isDimmed) 0.38f else 1.0f
        statusPanel.alpha = if (isDimmed) 0.38f else 1.0f
        cardBgDrawable.setColor(if (isDimmed) ThemeColors.SubCardBg else ThemeColors.CardBg)
    }

    private fun createActionButton(commandId: Int, label: String, accentColor: Int): View {
        val container = FrameLayout(context).apply {
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
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
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
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
            if (controlsLocked) {
                return@setOnTouchListener false
            }
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

        operatorButtons.add(container)
        operatorButtonLabels.add(txt)
        return container
    }

    fun showSendingIndicator() {
        lastCommandSentMs = SystemClock.elapsedRealtime()
        statusText.text = "Ukaz..."
        statusText.setTextColor(ThemeColors.AccentBlue)
        (statusDot.background as? GradientDrawable)?.setColor(ThemeColors.AccentBlue)
    }

    fun onLocalTick(deltaMs: Long) {
        if (isOnline && remainingMs > 0L) {
            remainingMs = max(0L, remainingMs - deltaMs)
            renderTime(suppressChime = false)
        } else {
            syncSingleFunicularPosition()
        }
    }

    fun applyState(state: StationState, forceSyncTime: Boolean, isCachedState: Boolean = false) {
        val isFirstLoad = !hasInitialTelemetry
        val wasOffline = !isOnline
        isOnline = true

        (statusDot.background as? GradientDrawable)?.setColor(ThemeColors.AccentEmerald)
        statusText.text = "Povezano"
        statusText.setTextColor(ThemeColors.TextSecondary)

        val prevRemainingMs = remainingMs
        val prevSetMs = setMs
        val prevPersons = currentPersons

        setMs = state.setTimeMs
        if (forceSyncTime || wasOffline || abs(state.remainingTimeMs - remainingMs) > 2000L) {
            remainingMs = state.remainingTimeMs
        }
        currentPersons = state.currentPersons
        maxPersons = state.maxPersons

        // Obvestilo ob odhodu: detect automatic departure clock reset (matching Program.cs lines 1827-1839)
        val recentlySentCommand = lastCommandSentMs > 0L && (SystemClock.elapsedRealtime() - lastCommandSentMs) < 3500L
        if (!isFirstLoad && !wasOffline && !isCachedState && !recentlySentCommand) {
            val clockReset = (state.setTimeMs > 0L) &&
                (state.setTimeMs == prevSetMs) &&
                (state.remainingTimeMs - prevRemainingMs > 15_000L) &&
                (state.remainingTimeMs >= state.setTimeMs - 20_000L)
            if (clockReset && (state.currentPersons == 0 || state.currentPersons < prevPersons)) {
                lastDepartedStationId = stationId
                if (!isMuted) {
                    val nextMinutes = max(1, ((state.setTimeMs + 30_000L) / 60_000L).toInt())
                    MobileVoiceAnnouncer.speakDeparture(nextMinutes)
                }
            }
        }

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
        syncSingleFunicularPosition()
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
        syncSingleFunicularPosition()
    }

    /**
     * Computes the single funicular car's position from both stations and applies the exact
     * same state to both Zgornja and Spodnja views so the single car is never in two places at once.
     */
    fun syncSingleFunicularPosition() {
        val totalSec = rideDurationSec.coerceIn(10, 600)
        val rideDurationMs = totalSec * 1000L
        val spodajCard = if (stationId == "spodaj") this else peerCard
        val zgorajCard = if (stationId == "zgoraj") this else peerCard

        val sOnline = spodajCard?.currentOnline == true
        val sRem = spodajCard?.currentRemainingMs ?: 0L
        val sSet = spodajCard?.currentSetMs ?: 0L

        val zOnline = zgorajCard?.currentOnline == true
        val zRem = zgorajCard?.currentRemainingMs ?: 0L
        val zSet = zgorajCard?.currentSetMs ?: 0L

        if (!sOnline && !zOnline) {
            val isFromSpodnjaLast = (lastDepartedStationId == "spodaj")
            spodajCard?.funicularTrackView?.setRideState(false, false, isFromSpodnjaLast, lastKnownSingleCarPos, 0L, totalSec)
            zgorajCard?.funicularTrackView?.setRideState(false, false, isFromSpodnjaLast, lastKnownSingleCarPos, 0L, totalSec)
            return
        }

        val sElapsed = if (sOnline && sSet >= rideDurationMs && sRem in 1L until sSet) (sSet - sRem) else -1L
        val zElapsed = if (zOnline && zSet >= rideDurationMs && zRem in 1L until zSet) (zSet - zRem) else -1L

        // Determine which station departed most recently
        val activeStationId: String = when {
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
        lastDepartedStationId = activeStationId

        val activeElapsed = if (activeStationId == "spodaj") sElapsed else zElapsed
        val isFromSpodnja = (activeStationId == "spodaj")

        if (activeElapsed in 1L..rideDurationMs) {
            val progress = (activeElapsed.toFloat() / rideDurationMs.toFloat()).coerceIn(0f, 1f)
            // Departure from Spodnja: 0.0 (Spodnja) -> 1.0 (Zgornja)
            // Departure from Zgornja: 1.0 (Zgornja) -> 0.0 (Spodnja)
            val pos = if (isFromSpodnja) progress else (1f - progress)
            lastKnownSingleCarPos = pos
            spodajCard?.funicularTrackView?.setRideState(true, true, isFromSpodnja, pos, activeElapsed, totalSec)
            zgorajCard?.funicularTrackView?.setRideState(true, true, isFromSpodnja, pos, activeElapsed, totalSec)
        } else {
            // Ride completed (>rideDurationMs) or waiting at station:
            // After departing Spodnja (0->1), car rests at Zgornja (1.0f).
            // After departing Zgornja (1->0), car rests at Spodnja (0.0f).
            val dockedPos = if (activeElapsed > rideDurationMs) {
                if (isFromSpodnja) 1f else 0f
            } else {
                lastKnownSingleCarPos
            }
            lastKnownSingleCarPos = dockedPos
            spodajCard?.funicularTrackView?.setRideState(true, false, isFromSpodnja, dockedPos, 0L, totalSec)
            zgorajCard?.funicularTrackView?.setRideState(true, false, isFromSpodnja, dockedPos, 0L, totalSec)
        }
    }

    companion object {
        @Volatile
        var rideDurationSec: Int = 90
        @Volatile
        private var lastDepartedStationId: String = "zgoraj"
        @Volatile
        private var lastKnownSingleCarPos: Float = 0f
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

/**
 * Horizontal real-time visualization of the single funicular car on tracks.
 * Spodnja postaja view: Spodnja (Left) -> Zgornja (Right).
 * Zgornja postaja view: Zgornja (Left) -> Spodnja (Right).
 */
class FunicularTrackView(
    context: Context,
    val isZgornjaToSpodnja: Boolean = false
) : View(context) {

    private val bgDrawable = GradientDrawable().apply {
        cornerRadius = dpF(8f)
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

        // Smooth frame-to-frame interpolation while riding
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

        val padX = dpF(12f)
        val headerBaselineY = h * 0.31f
        val labelTextSize = (h * 0.22f).coerceIn(dpF(9.5f), dpF(12f))
        labelPaint.textSize = labelTextSize
        statusPaint.textSize = labelTextSize

        // 1. Top Row: Station labels and ride status
        // Spodnja postaja view: SPODNJA (left) — Single Car Status (center) — ZGORNJA (right)
        // Zgornja postaja view: ZGORNJA (left) — Single Car Status (center) — SPODNJA (right)
        val isHeadingToZgornja = isFromSpodnja
        val leftLabelText = if (isZgornjaToSpodnja) "ZGORNJA" else "SPODNJA"
        val rightLabelText = if (isZgornjaToSpodnja) "SPODNJA" else "ZGORNJA"

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
            val statusStr = if (leftToRight) "▶ ${sec}s / ${maxSec}s" else "◀ ${sec}s / ${maxSec}s"
            statusPaint.color = if (isHeadingToZgornja) ThemeColors.AccentEmerald else ThemeColors.AccentBlue
            canvas.drawText(statusStr, w * 0.5f, headerBaselineY, statusPaint)
        }

        // 2. Horizontal Railway Track
        val carHalfW = (h * 0.29f).coerceIn(dpF(12f), dpF(17f))
        val carH = (h * 0.32f).coerceIn(dpF(12f), dpF(17f))
        val trackStartX = padX + carHalfW + dpF(4f)
        val trackEndX = w - padX - carHalfW - dpF(4f)
        if (trackEndX <= trackStartX) return

        val railY = h * 0.84f

        // Track sleepers / crossties underneath the rail
        trackPaint.strokeWidth = max(1.2f, dpF(1.3f))
        trackPaint.color = ThemeColors.Divider
        val sleeperTop = railY - dpF(1.5f)
        val sleeperBot = railY + dpF(3.5f)
        val numSleepers = 18
        for (i in 0..numSleepers) {
            val tx = trackStartX + (trackEndX - trackStartX) * (i.toFloat() / numSleepers.toFloat())
            canvas.drawLine(tx, sleeperTop, tx, sleeperBot, trackPaint)
        }

        // Main steel rail line
        trackPaint.strokeWidth = max(2f, dpF(2.2f))
        trackPaint.color = ThemeColors.BtnBorder
        canvas.drawLine(trackStartX, railY, trackEndX, railY, trackPaint)

        val carX = trackStartX + displayPos * (trackEndX - trackStartX)
        val activeColor = when {
            !isOnline -> ThemeColors.TextMuted
            isRiding && isHeadingToZgornja -> ThemeColors.AccentEmerald
            isRiding && !isHeadingToZgornja -> ThemeColors.AccentBlue
            else -> ThemeColors.TextSecondary
        }

        // Active trail along the track behind the funicular car while riding
        if (isOnline && isRiding) {
            activeTrackPaint.strokeWidth = max(2.2f, dpF(2.4f))
            activeTrackPaint.color = activeColor
            val originX = if (leftToRight) trackStartX else trackEndX
            canvas.drawLine(originX, railY, carX, railY, activeTrackPaint)
        }

        // Left & Right Station Track Buffers / Stops
        val stationNodeR = dpF(3.5f)
        fillPaint.color = if (isOnline && isRiding && !leftToRight) activeColor else ThemeColors.BtnBorder
        canvas.drawCircle(trackStartX, railY, stationNodeR, fillPaint)

        fillPaint.color = if (isOnline && isRiding && leftToRight) activeColor else ThemeColors.BtnBorder
        canvas.drawCircle(trackEndX, railY, stationNodeR, fillPaint)

        // 3. Funicular Car sitting directly on tracks (no top cable attachment)
        val wheelR = dpF(2.1f)
        val carBottom = railY - wheelR * 0.85f
        val carTop = carBottom - carH
        carRect.set(carX - carHalfW, carTop, carX + carHalfW, carBottom)

        // Undercarriage bogie / wheels on the rail
        fillPaint.color = if (isOnline && isRiding) activeColor else ThemeColors.TextMuted
        canvas.drawCircle(carX - carHalfW * 0.54f, railY - wheelR * 0.25f, wheelR, fillPaint)
        canvas.drawCircle(carX + carHalfW * 0.54f, railY - wheelR * 0.25f, wheelR, fillPaint)

        // Cabin body fill + border
        fillPaint.color = if (isOnline && isRiding) ThemeColors.AccentBlueActiveBg else ThemeColors.BtnBg
        val cornerR = dpF(3.8f)
        canvas.drawRoundRect(carRect, cornerR, cornerR, fillPaint)

        carStrokePaint.strokeWidth = max(1.6f, dpF(1.5f))
        carStrokePaint.color = if (isOnline && isRiding) activeColor else ThemeColors.TextSecondary
        canvas.drawRoundRect(carRect, cornerR, cornerR, carStrokePaint)

        // Two panoramic cabin windows
        val winMarginX = carHalfW * 0.22f
        val winGap = carHalfW * 0.14f
        val winTop = carTop + (carBottom - carTop) * 0.22f
        val winBottom = carTop + (carBottom - carTop) * 0.64f
        winRect1.set(carX - carHalfW + winMarginX, winTop, carX - winGap * 0.5f, winBottom)
        winRect2.set(carX + winGap * 0.5f, winTop, carX + carHalfW - winMarginX, winBottom)
        fillPaint.color = if (isOnline) ThemeColors.TextPrimary else ThemeColors.TextMuted
        fillPaint.alpha = if (isOnline && isRiding) 225 else 150
        canvas.drawRoundRect(winRect1, dpF(1.5f), dpF(1.5f), fillPaint)
        canvas.drawRoundRect(winRect2, dpF(1.5f), dpF(1.5f), fillPaint)
        fillPaint.alpha = 255

        // Direction chevron in front of the cabin when moving
        if (isOnline && isRiding) {
            val dir = if (leftToRight) 1f else -1f
            val arrowBaseX = carX + dir * (carHalfW + dpF(5f))
            val arrowTipX = arrowBaseX + dir * dpF(4.2f)
            val arrowMidY = (carTop + carBottom) * 0.5f
            val arrowHalfH = dpF(3.5f)
            if (arrowTipX in (trackStartX + dpF(2f))..(trackEndX - dpF(2f))) {
                arrowPath.reset()
                arrowPath.moveTo(arrowBaseX, arrowMidY - arrowHalfH)
                arrowPath.lineTo(arrowTipX, arrowMidY)
                arrowPath.lineTo(arrowBaseX, arrowMidY + arrowHalfH)
                carStrokePaint.strokeWidth = max(1.6f, dpF(1.5f))
                carStrokePaint.color = activeColor
                canvas.drawPath(arrowPath, carStrokePaint)
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
