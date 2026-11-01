package com.simongolf.prompter

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.MirrorMode
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CameraActivity : ComponentActivity() {

    private lateinit var frame: FrameLayout
    private lateinit var previewView: PreviewView
    private lateinit var prompter: FrameLayout
    private lateinit var text: TextView
    private lateinit var marker: View
    private lateinit var recBtn: FrameLayout
    private lateinit var recDot: View
    private lateinit var timeView: TextView
    private lateinit var countView: TextView
    private lateinit var speedView: TextView
    private lateinit var pauseBtn: Button

    private var provider: ProcessCameraProvider? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var front = true

    private var speed = 6
    private var sizeSp = 30
    private var phPct = 34
    private var y = 0f
    private var playing = false
    private var lastNs = 0L
    private var recStart = 0L
    private var counting = false

    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res[Manifest.permission.CAMERA] == true) startCamera()
        else { Toast.makeText(this, "카메라 권한이 필요해요", Toast.LENGTH_LONG).show(); finish() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        speed = prefs.getInt("speed", 6); sizeSp = prefs.getInt("size", 30); phPct = prefs.getInt("ph", 34)
        front = prefs.getBoolean("front", true)
        buildUi()
        val need = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        if (need.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) startCamera()
        else permLauncher.launch(need)
    }

    // ---------------- UI ----------------
    private fun buildUi() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        frame = FrameLayout(this)
        previewView = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
        frame.addView(previewView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        prompter = FrameLayout(this).apply { setBackgroundColor(0x99000000.toInt()); clipChildren = true }
        text = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = sizeSp.toFloat(); setTypeface(typeface, Typeface.BOLD)
            setLineSpacing(0f, 1.25f); setShadowLayer(6f, 0f, 2f, 0x99000000.toInt())
            setPadding(dp(20f), 0, dp(18f), dp(400f))
            text = buildScript(prefs.getString("script", "") ?: "")
        }
        prompter.addView(text, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        marker = View(this).apply { background = GradientDrawable().apply { setColor(ACCENT); cornerRadius = dp(3f).toFloat() } }
        prompter.addView(marker, FrameLayout.LayoutParams(dp(6f), dp(26f)))
        frame.addView(prompter, FrameLayout.LayoutParams(MATCH_PARENT, 0))

        timeView = TextView(this).apply {
            text = "00:00"; setTextColor(Color.WHITE); textSize = 15f; setTypeface(typeface, Typeface.BOLD)
            background = roundBg(0x80000000.toInt(), dp(16f).toFloat()); setPadding(dp(12f), dp(5f), dp(12f), dp(5f))
        }
        frame.addView(timeView, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.END or Gravity.TOP))

        countView = TextView(this).apply {
            setTextColor(ACCENT); textSize = 110f; setTypeface(typeface, Typeface.BOLD); gravity = Gravity.CENTER
            setShadowLayer(16f, 0f, 4f, Color.BLACK); visibility = View.GONE
        }
        frame.addView(countView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        root.addView(frame, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT, Gravity.CENTER))

        // bottom controls
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12f), dp(10f), dp(12f), dp(22f))
        }
        fun small(label: String, onClick: () -> Unit) = Button(this).apply {
            text = label; textSize = 14f; setTextColor(Color.WHITE); isAllCaps = false; minWidth = 0; minimumWidth = 0
            background = roundBg(0xD91A1C21.toInt(), dp(12f).toFloat()); setPadding(dp(12f), 0, dp(12f), 0)
            setOnClickListener { onClick() }
        }
        val left = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        left.addView(small("대본") { if (recording == null) finish() }, LinearLayout.LayoutParams(WRAP_CONTENT, dp(44f)))
        pauseBtn = small("재생") { if (!counting) { if (playing) pause() else play() } }
        left.addView(pauseBtn, LinearLayout.LayoutParams(WRAP_CONTENT, dp(44f)).apply { leftMargin = dp(6f) })
        left.addView(small("전환") { if (recording == null) { front = !front; prefs.edit().putBoolean("front", front).apply(); bindCamera() } },
            LinearLayout.LayoutParams(WRAP_CONTENT, dp(44f)).apply { leftMargin = dp(6f) })
        bar.addView(left, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))

        recBtn = FrameLayout(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setStroke(dp(5f), Color.WHITE) }
            setOnClickListener { onRecClick() }
        }
        recDot = View(this).apply { background = GradientDrawable().apply { setColor(0xFFFF3B3B.toInt()); cornerRadius = dp(30f).toFloat() } }
        recBtn.addView(recDot, FrameLayout.LayoutParams(dp(56f), dp(56f), Gravity.CENTER))
        bar.addView(recBtn, LinearLayout.LayoutParams(dp(76f), dp(76f)))

        val right = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL or Gravity.END }
        right.addView(small("－") { setSpeed(speed - 1) }, LinearLayout.LayoutParams(dp(44f), dp(44f)))
        speedView = TextView(this).apply { setTextColor(ACCENT); textSize = 18f; setTypeface(typeface, Typeface.BOLD); gravity = Gravity.CENTER }
        right.addView(speedView, LinearLayout.LayoutParams(dp(36f), WRAP_CONTENT))
        right.addView(small("＋") { setSpeed(speed + 1) }, LinearLayout.LayoutParams(dp(44f), dp(44f)))
        bar.addView(right, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        root.addView(bar, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM))

        setContentView(root)
        setSpeed(speed)

        // fit a 9:16 frame inside the screen
        root.post {
            val W = root.width; val H = root.height
            var fw = W; var fh = W * 16 / 9
            if (fh > H) { fh = H; fw = H * 9 / 16 }
            frame.layoutParams = FrameLayout.LayoutParams(fw, fh, Gravity.CENTER)
            frame.post { layoutPrompter() }
        }

        // drag to reposition the script
        var dragY0 = 0f; var dragS0 = 0f; var wasPlaying = false
        prompter.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { dragY0 = e.rawY; dragS0 = y; wasPlaying = playing; playing = false }
                MotionEvent.ACTION_MOVE -> { y = (dragS0 - (e.rawY - dragY0)).coerceIn(0f, maxY()); apply() }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { if (wasPlaying) play() }
            }
            true
        }
    }

    private fun layoutPrompter() {
        val ph = frame.height * phPct / 100
        prompter.layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, ph, Gravity.TOP)
        (timeView.layoutParams as FrameLayout.LayoutParams).apply { topMargin = ph + dp(8f); rightMargin = dp(10f) }
        timeView.requestLayout()
        prompter.post {
            val mlp = marker.layoutParams as FrameLayout.LayoutParams
            mlp.topMargin = (lineTop() + text.lineHeight * 0.15f).toInt(); mlp.leftMargin = dp(4f)
            marker.layoutParams = mlp
            apply()
        }
    }

    private fun buildScript(raw: String): CharSequence {
        val sb = SpannableStringBuilder()
        val pend = mutableListOf<String>()
        val dirRe = Regex("[(\\[]([^)\\]]*)[)\\]]")
        raw.replace("\r", "").split(Regex("\n+")).map { it.trim() }.filter { it.isNotEmpty() }.forEach { p ->
            dirRe.findAll(p).forEach { m -> m.groupValues[1].trim().takeIf { it.isNotEmpty() }?.let { pend.add(it) } }
            val rest = p.replace(dirRe, " ").replace(Regex("\\s+"), " ").trim()
            if (rest.isEmpty()) return@forEach
            if (sb.isNotEmpty()) sb.append("\n\n")
            if (pend.isNotEmpty()) {
                val s = sb.length
                sb.append("(").append(pend.joinToString(" / ")).append(")\n")
                sb.setSpan(RelativeSizeSpan(0.5f), s, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(ForegroundColorSpan(0xFFE0B84A.toInt()), s, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                pend.clear()
            }
            sb.append(rest)
        }
        return sb
    }

    // ---------------- scrolling ----------------
    private fun lineTop() = maxOf(dp(12f).toFloat(), prompter.height * 0.14f)
    private fun maxY() = maxOf(0f, (text.height - dp(400f)).toFloat())
    private fun apply() { text.translationY = lineTop() - y }
    private fun pxPerSec() = speed * text.textSize * 0.09f

    private val frameCb = object : Choreographer.FrameCallback {
        override fun doFrame(now: Long) {
            if (!playing) return
            val dt = if (lastNs == 0L) 0f else (now - lastNs) / 1e9f
            lastNs = now
            y = (y + pxPerSec() * dt).coerceAtMost(maxY())
            apply()
            if (recording != null) {
                val s = (SystemClock.elapsedRealtime() - recStart) / 1000
                timeView.text = String.format(Locale.US, "%02d:%02d", s / 60, s % 60)
            }
            if (y >= maxY()) { pause(); return }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }
    private fun play() { playing = true; lastNs = 0L; pauseBtn.text = "멈춤"; Choreographer.getInstance().postFrameCallback(frameCb) }
    private fun pause() { playing = false; pauseBtn.text = "재생" }
    private fun setSpeed(v: Int) { speed = v.coerceIn(1, 20); prefs.edit().putInt("speed", speed).apply(); speedView.text = speed.toString() }

    // ---------------- camera ----------------
    private fun startCamera() {
        val f = ProcessCameraProvider.getInstance(this)
        f.addListener({ provider = f.get(); bindCamera() }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCamera() {
        val p = provider ?: return
        val rs = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY).build()
        val preview = Preview.Builder().setResolutionSelector(rs).build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.FHD, FallbackStrategy.lowerQualityOrHigherThan(Quality.FHD)))
            .setAspectRatio(AspectRatio.RATIO_16_9)
            .build()
        val vc = VideoCapture.Builder(recorder).setMirrorMode(MirrorMode.MIRROR_MODE_ON_FRONT_ONLY).build()
        videoCapture = vc
        val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        try {
            p.unbindAll()
            p.bindToLifecycle(this, selector, preview, vc)
        } catch (e: Exception) {
            Toast.makeText(this, "카메라를 열 수 없어요: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun onRecClick() {
        if (counting) return
        if (recording != null) { stopRecording(); return }
        counting = true
        y = 0f; apply()
        countView.visibility = View.VISIBLE
        var n = 3
        countView.text = n.toString()
        val tick = object : Runnable {
            override fun run() {
                n--
                if (n > 0) { countView.text = n.toString(); countView.postDelayed(this, 1000) }
                else { countView.visibility = View.GONE; counting = false; startRecording() }
            }
        }
        countView.postDelayed(tick, 1000)
    }

    @SuppressLint("MissingPermission")
    private fun startRecording() {
        val vc = videoCapture ?: return
        val name = "simongolf_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val cv = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/SimonGolf")
        }
        val opts = MediaStoreOutputOptions.Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(cv).build()
        var pending = vc.output.prepareRecording(this, opts)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            pending = pending.withAudioEnabled()
        }
        recording = pending.start(ContextCompat.getMainExecutor(this)) { ev ->
            when (ev) {
                is VideoRecordEvent.Start -> {
                    recStart = SystemClock.elapsedRealtime()
                    (recDot.background as GradientDrawable).cornerRadius = dp(8f).toFloat()
                    recDot.layoutParams = FrameLayout.LayoutParams(dp(30f), dp(30f), Gravity.CENTER)
                    y = 0f; apply(); play()
                }
                is VideoRecordEvent.Finalize -> {
                    recording = null
                    (recDot.background as GradientDrawable).cornerRadius = dp(30f).toFloat()
                    recDot.layoutParams = FrameLayout.LayoutParams(dp(56f), dp(56f), Gravity.CENTER)
                    pause()
                    if (!ev.hasError()) Toast.makeText(this, "갤러리에 저장했어요 (동영상 > SimonGolf)", Toast.LENGTH_LONG).show()
                    else Toast.makeText(this, "저장 실패: 오류 ${ev.error}", Toast.LENGTH_LONG).show()
                }
                else -> {}
            }
        }
    }

    private fun stopRecording() { recording?.stop() }

    override fun onStop() {
        super.onStop()
        recording?.stop(); recording = null
        pause()
    }
}
