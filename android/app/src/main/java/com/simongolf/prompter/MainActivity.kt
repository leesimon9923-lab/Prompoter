package com.simongolf.prompter

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipboardManager
import android.graphics.Rect
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
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
        val help = TextView(this).apply {
            text = "대본을 붙여넣고 촬영 시작을 누르세요. 녹화 버튼을 누르면 3초 뒤 녹화와 대본이 같이 시작돼요. 영상은 갤러리에 저장돼요."
            setTextColor(0xFF8A8F99.toInt()); textSize = 14f; setPadding(0, dp(6f), 0, dp(12f))
        }
        root.addView(help)

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
        // 대본 위 버튼줄: [완료] ... [붙여넣기] [대본 전체 지우기]
        val toolRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, dp(8f)) }
        fun tool(label: String, bg: Int, fg: Int, onClick: () -> Unit) = Button(this).apply {
            text = label; textSize = 14f; isAllCaps = false; setTextColor(fg)
            minHeight = 0; minimumHeight = 0; minWidth = 0; minimumWidth = 0
            background = roundBg(bg, dp(10f).toFloat(), 0xFF3A3E46.toInt()); setPadding(dp(14f), 0, dp(14f), 0)
            setOnClickListener { onClick() }
        }
        // 키보드가 떠 있을 때만 보이는 "완료": 누르면 키보드를 내린다
        val doneBtn = tool("완료", ACCENT, 0xFF111111.toInt()) {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(script.windowToken, 0)
            script.clearFocus()
        }.apply { setTypeface(typeface, Typeface.BOLD); visibility = View.GONE }
        toolRow.addView(doneBtn, LinearLayout.LayoutParams(WRAP_CONTENT, dp(40f)))
        toolRow.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        // 붙여넣기: 복사해 둔 글을 커서 자리에 넣는다 (대본이 비어 있으면 그대로 채워짐)
        toolRow.addView(tool("붙여넣기", PANEL, Color.WHITE) {
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cm.primaryClip
            val t = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(this@MainActivity).toString() else ""
            if (t.isBlank()) { Toast.makeText(this@MainActivity, "복사된 글이 없어요", Toast.LENGTH_SHORT).show(); return@tool }
            val ed = script.text
            val a = if (script.hasFocus()) minOf(script.selectionStart, script.selectionEnd).coerceAtLeast(0) else ed.length
            val b = if (script.hasFocus()) maxOf(script.selectionStart, script.selectionEnd).coerceAtLeast(0) else ed.length
            ed.replace(a, b, t)
            Toast.makeText(this@MainActivity, "붙여넣었어요", Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(WRAP_CONTENT, dp(40f)).apply { rightMargin = dp(8f) })
        // 대본 전체 지우기 (실수로 누를 수 있으니 한 번 물어본다)
        toolRow.addView(tool("대본 전체 지우기", PANEL, Color.WHITE) {
            if (script.text.isNullOrEmpty()) return@tool
            AlertDialog.Builder(this@MainActivity)
                .setMessage("대본을 전부 지울까요?")
                .setPositiveButton("지우기") { _, _ -> script.setText("") }
                .setNegativeButton("취소", null)
                .show()
        }, LinearLayout.LayoutParams(WRAP_CONTENT, dp(40f)))
        root.addView(toolRow)

        root.addView(script, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        // 대본 아래 설정들: 키보드가 뜨면 통째로 숨겨서 대본 칸을 크게 쓴다
        val settings = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

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
            settings.addView(row)
        }
        // 촬영 방향: 세로(쇼츠·릴스) / 가로(유튜브 롱폼). 글자 크기·창 높이는 방향마다 따로 기억
        val land = prefs.getBoolean("land", false)
        val modeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(12f), 0, 0) }
        modeRow.addView(TextView(this).apply { text = "촬영 방향"; setTextColor(0xFFCFD2D8.toInt()); textSize = 15f; gravity = Gravity.CENTER_VERTICAL },
            LinearLayout.LayoutParams(dp(92f), dp(44f)))
        fun modeBtn(label: String, on: Boolean, value: Boolean) = Button(this).apply {
            text = label; textSize = 15f; isAllCaps = false
            setTextColor(if (on) 0xFF111111.toInt() else 0xFF8A8F99.toInt())
            if (on) setTypeface(typeface, Typeface.BOLD)
            background = roundBg(if (on) ACCENT else PANEL, dp(12f).toFloat())
            setOnClickListener { if (!on) { prefs.edit().putBoolean("land", value).apply(); recreate() } }
        }
        modeRow.addView(modeBtn("세로 (쇼츠)", !land, false), LinearLayout.LayoutParams(0, dp(44f), 1f).apply { rightMargin = dp(6f) })
        modeRow.addView(modeBtn("가로 (유튜브)", land, true), LinearLayout.LayoutParams(0, dp(44f), 1f))
        settings.addView(modeRow)

        slider("흐르는 속도", "speed", 1, 20, 6)
        if (land) {
            slider("글자 크기", "size_land", 20, 48, 26)
            slider("대본 창 높이", "ph_land", 20, 80, 45, "%")
        } else {
            slider("글자 크기", "size", 20, 48, 30)
            slider("대본 창 높이", "ph", 20, 60, 34, "%")
        }

        val start = Button(this).apply {
            text = if (land) "가로로 촬영 시작" else "촬영 시작"
            textSize = 18f; setTextColor(0xFF111111.toInt()); setTypeface(typeface, Typeface.BOLD)
            background = roundBg(ACCENT, dp(14f).toFloat()); isAllCaps = false
            setOnClickListener {
                if (script.text.toString().isBlank()) { script.hint = "대본을 먼저 붙여넣어 주세요"; script.requestFocus(); return@setOnClickListener }
                val target = if (prefs.getBoolean("land", false)) CameraLandActivity::class.java else CameraActivity::class.java
                startActivity(Intent(this@MainActivity, target))
            }
        }
        settings.addView(start, LinearLayout.LayoutParams(MATCH_PARENT, dp(58f)).apply { topMargin = dp(16f) })
        root.addView(settings)

        setContentView(root)

        // 키보드가 뜨면 설명·설정을 숨기고 "완료" 버튼을 보여 준다
        root.viewTreeObserver.addOnGlobalLayoutListener {
            val r = Rect(); root.getWindowVisibleDisplayFrame(r)
            val screenH = root.rootView.height
            val kb = screenH - r.bottom > screenH * 0.2f
            val vis = if (kb) View.GONE else View.VISIBLE
            if (settings.visibility != vis) {
                settings.visibility = vis; help.visibility = vis
                doneBtn.visibility = if (kb) View.VISIBLE else View.GONE
            }
        }
    }
}
