package com.chessoverlay.helper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())

    private var bubble: TextView? = null
    private var panel: LinearLayout? = null
    private val testViews = mutableListOf<View>()

    private var selector: BoardSelectorView? = null
    private var selectorBar: LinearLayout? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences("cfg", MODE_PRIVATE)
        startAsForeground()
        addBubble()
    }

    override fun onDestroy() {
        clearTest()
        endBoardSelect()
        panel?.let { safeRemove(it) }
        bubble?.let { safeRemove(it) }
        panel = null
        bubble = null
        super.onDestroy()
    }

    // ---------- helpers ----------

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun safeRemove(v: View) {
        try {
            wm.removeView(v)
        } catch (e: Exception) {
        }
    }

    private fun lp(
        w: Int,
        h: Int,
        touchable: Boolean,
        fullCoords: Boolean = false
    ): WindowManager.LayoutParams {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        if (!touchable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        if (fullCoords) {
            flags = flags or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        }
        val p = WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START
        return p
    }

    private fun startAsForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel("ov", "Overlay", NotificationManager.IMPORTANCE_LOW)
        )
        val n = Notification.Builder(this, "ov")
            .setContentTitle("Chess Helper شغال")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    // ---------- bubble ----------

    private fun addBubble() {
        val b = TextView(this)
        b.text = "♟"
        b.textSize = 26f
        b.setTextColor(Color.WHITE)
        b.gravity = Gravity.CENTER
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.OVAL
        bg.setColor(Color.parseColor("#E6263238"))
        bg.setStroke(dp(2), Color.WHITE)
        b.background = bg

        val p = lp(dp(56), dp(56), true)
        p.x = dp(8)
        p.y = dp(200)

        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false

        b.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = p.x
                    startY = p.y
                    touchX = e.rawX
                    touchY = e.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - touchX).toInt()
                    val dy = (e.rawY - touchY).toInt()
                    if (abs(dx) > dp(6) || abs(dy) > dp(6)) moved = true
                    if (moved) {
                        p.x = startX + dx
                        p.y = startY + dy
                        wm.updateViewLayout(b, p)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) togglePanel()
                    true
                }
                else -> false
            }
        }

        wm.addView(b, p)
        bubble = b
    }

    // ---------- settings panel ----------

    private fun togglePanel() {
        val old = panel
        if (old != null) {
            safeRemove(old)
            panel = null
            return
        }
        val v = buildPanel()
        val params = lp(dp(300), WindowManager.LayoutParams.WRAP_CONTENT, true)
        params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        params.y = dp(40)
        wm.addView(v, params)
        panel = v
    }

    private fun buildPanel(): LinearLayout {
        val l = LinearLayout(this)
        l.orientation = LinearLayout.VERTICAL
        l.layoutDirection = View.LAYOUT_DIRECTION_RTL
        l.setPadding(dp(16), dp(12), dp(16), dp(12))
        val bg = GradientDrawable()
        bg.setColor(Color.parseColor("#F2202020"))
        bg.cornerRadius = dp(16).toFloat()
        l.background = bg

        val title = TextView(this)
        title.text = "إعدادات المحرك"
        title.textSize = 18f
        title.setTextColor(Color.WHITE)
        title.gravity = Gravity.CENTER
        l.addView(title)

        l.addView(slider("العمق", "depth", 1, 30, 12))
        l.addView(slider("مستوى المهارة", "skill", 0, 20, 20))
        l.addView(slider("وقت التفكير (×0.1 ثانية)", "time", 1, 50, 10))

        val side = Button(this)
        fun upd() {
            side.text = if (prefs.getBoolean("white", true)) "الدور: أبيض" else "الدور: أسود"
        }
        upd()
        side.setOnClickListener {
            prefs.edit().putBoolean("white", !prefs.getBoolean("white", true)).apply()
            upd()
        }
        l.addView(side)

        val sel = Button(this)
        sel.text = "تحديد الرقعة"
        sel.setOnClickListener { startBoardSelect() }
        l.addView(sel)

        val test = Button(this)
        test.text = "تجربة المربعات (e2 ← e4)"
        test.setOnClickListener { drawMove("e2", "e4") }
        l.addView(test)

        val close = Button(this)
        close.text = "إيقاف التطبيق"
        close.setOnClickListener { stopSelf() }
        l.addView(close)

        return l
    }

    private fun slider(name: String, key: String, lo: Int, hi: Int, def: Int): LinearLayout {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(0, dp(8), 0, 0)

        val label = TextView(this)
        label.setTextColor(Color.WHITE)
        label.textSize = 14f

        val sb = SeekBar(this)
        sb.max = hi
        sb.min = lo
        val cur = prefs.getInt(key, def)
        sb.progress = cur
        label.text = "$name: $cur"

        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, v: Int, fromUser: Boolean) {
                label.text = "$name: $v"
                prefs.edit().putInt(key, v).apply()
            }

            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        box.addView(label)
        box.addView(sb)
        return box
    }

    // ---------- board selection ----------

    private fun startBoardSelect() {
        panel?.let { safeRemove(it) }
        panel = null
        clearTest()
        endBoardSelect()

        val dm = resources.displayMetrics
        val w = dm.widthPixels.toFloat()
        val h = dm.heightPixels.toFloat()
        val saved = prefs.getInt("bs", 0)
        val offX = prefs.getInt("offx", 0).toFloat()
        val offY = prefs.getInt("offy", 0).toFloat()
        val s = if (saved > 0) saved.toFloat() else w
        val x = if (saved > 0) prefs.getInt("bx", 0) - offX else 0f
        val y = if (saved > 0) prefs.getInt("by", 0) - offY else (h - w) / 2f

        val v = BoardSelectorView(this, x, y, s)
        wm.addView(
            v,
            lp(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                true,
                true
            )
        )
        selector = v

        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.VERTICAL
        bar.layoutDirection = View.LAYOUT_DIRECTION_RTL
        bar.setPadding(dp(12), dp(8), dp(12), dp(8))
        val bg = GradientDrawable()
        bg.setColor(Color.parseColor("#F2202020"))
        bg.cornerRadius = dp(12).toFloat()
        bar.background = bg

        val hint = TextView(this)
        hint.text = "حرّك الإطار، وكبّره بالنقطة الخضراء، لين يغطي الرقعة بالضبط"
        hint.setTextColor(Color.WHITE)
        hint.textSize = 13f
        hint.gravity = Gravity.CENTER
        bar.addView(hint)

        val done = Button(this)
        done.text = "تم ✅ حفظ"
        done.setOnClickListener {
            val loc = IntArray(2)
            v.getLocationOnScreen(loc)
            prefs.edit()
                .putInt("bx", v.bx.toInt() + loc[0])
                .putInt("by", v.by.toInt() + loc[1])
                .putInt("bs", v.bs.toInt())
                .putInt("offx", loc[0])
                .putInt("offy", loc[1])
                .apply()
            endBoardSelect()
            Toast.makeText(this, "انحفظ مكان الرقعة", Toast.LENGTH_SHORT).show()
        }
        bar.addView(done)

        val bp = lp(dp(300), WindowManager.LayoutParams.WRAP_CONTENT, true)
        bp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        bp.y = dp(40)
        wm.addView(bar, bp)
        selectorBar = bar
    }

    private fun endBoardSelect() {
        selector?.let { safeRemove(it) }
        selectorBar?.let { safeRemove(it) }
        selector = null
        selectorBar = null
    }

    // ---------- squares (red = from, blue = to) ----------

    private fun square(stroke: Int, fill: Int): View {
        val v = View(this)
        val g = GradientDrawable()
        g.setColor(fill)
        g.setStroke(dp(3), stroke)
        v.background = g
        return v
    }

    private fun clearTest() {
        for (v in testViews) safeRemove(v)
        testViews.clear()
    }

    private fun drawMove(from: String, to: String) {
        clearTest()
        val bs = prefs.getInt("bs", 0)
        if (bs <= 0) {
            Toast.makeText(this, "حدد الرقعة أول", Toast.LENGTH_SHORT).show()
            return
        }
        val bx = prefs.getInt("bx", 0)
        val by = prefs.getInt("by", 0)
        val cell = bs / 8
        val white = prefs.getBoolean("white", true)

        fun pos(sq: String): Pair<Int, Int> {
            val f = sq[0] - 'a'
            val r = sq[1] - '1' + 1
            val col = if (white) f else 7 - f
            val row = if (white) 8 - r else r - 1
            return Pair(bx + col * cell, by + row * cell)
        }

        val red = square(Color.RED, Color.parseColor("#55FF0000"))
        val blue = square(Color.BLUE, Color.parseColor("#550000FF"))

        val a = pos(from)
        val b = pos(to)

        val pr = lp(cell, cell, false, true)
        pr.x = a.first
        pr.y = a.second
        val pb = lp(cell, cell, false, true)
        pb.x = b.first
        pb.y = b.second

        wm.addView(red, pr)
        wm.addView(blue, pb)
        testViews.add(red)
        testViews.add(blue)

        handler.postDelayed({ clearTest() }, 6000)
    }
}
