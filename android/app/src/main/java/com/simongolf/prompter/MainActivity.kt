package com.simongolf.prompter

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

const val PREFS = "prompter"
const val ACCENT = 0xFFFFD43B.toInt()
const val PANEL = 0xFF1A1C21.toInt()

fun Context.dp(v: Float): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()

fun roundBg(color: Int, radius: Float, stroke: Int = 0): GradientDrawable = GradientDrawable().apply {
    setColor(color); cornerRadius = radius
    if (stroke != 0) setStroke(2, stroke)
}

class MainActivity : Activity() {
    private lateinit var script: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(18f), dp(16f), dp(16f))
            setBackgroundColor(0xFF0E0F12.toInt())
        }
        root.addView(TextView(this).apply {
            text = "시몬골프 프롬프터"; setTextColor(Color.WHITE); textSize = 22f; setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "대본을 붙여넣고 촬영 시작을 누르세요. 녹화 버튼을 누르면 3초 뒤 녹화와 대본이 같이 시작돼요. 영상은 갤러리에 저장돼요."
            setTextColor(0xFF8A8F99.toInt()); textSize = 14f; setPadding(0, dp(6f), 0, dp(12f))
        })

        script = EditText(this).apply {
            setText(prefs.getString("script", ""))
            hint = "여기에 대본을 붙여넣으세요"
            setHintTextColor(0xFF6B7079.toInt()); setTextColor(Color.WHITE); textSize = 17f
            gravity = Gravity.TOP or Gravity.START
            background = roundBg(PANEL, dp(12f).toFloat())
            setPadding(dp(14f), dp(14f), dp(14f), dp(14f))
            minLines = 8
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { prefs.edit().putString("script", s.toString()).apply() }
            })
        }
        root.addView(script, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        fun slider(label: String, key: String, min: Int, max: Int, def: Int, suffix: String = "") {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(12f), 0, 0) }
            row.addView(TextView(this).apply { text = label; setTextColor(0xFFCFD2D8.toInt()); textSize = 15f }, LinearLayout.LayoutParams(dp(92f), WRAP_CONTENT))
            val value = TextView(this).apply { setTextColor(ACCENT); textSize = 16f; setTypeface(typeface, Typeface.BOLD); gravity = Gravity.END }
            val sb = SeekBar(this).apply {
                this.max = max - min; progress = prefs.getInt(key, def) - min
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) { value.text = "${p + min}$suffix"; prefs.edit().putInt(key, p + min).apply() }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })
            }
            value.text = "${prefs.getInt(key, def)}$suffix"
            row.addView(sb, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            row.addView(value, LinearLayout.LayoutParams(dp(48f), WRAP_CONTENT))
            root.addView(row)
        }
        slider("흐르는 속도", "speed", 1, 20, 6)
        slider("글자 크기", "size", 20, 48, 30)
        slider("대본 창 높이", "ph", 20, 60, 34, "%")

        val start = Button(this).apply {
            text = "촬영 시작"; textSize = 18f; setTextColor(0xFF111111.toInt()); setTypeface(typeface, Typeface.BOLD)
            background = roundBg(ACCENT, dp(14f).toFloat()); isAllCaps = false
            setOnClickListener {
                if (script.text.toString().isBlank()) { script.hint = "대본을 먼저 붙여넣어 주세요"; script.requestFocus(); return@setOnClickListener }
                startActivity(Intent(this@MainActivity, CameraActivity::class.java))
            }
        }
        root.addView(start, LinearLayout.LayoutParams(MATCH_PARENT, dp(58f)).apply { topMargin = dp(16f) })

        setContentView(root)
    }
}
