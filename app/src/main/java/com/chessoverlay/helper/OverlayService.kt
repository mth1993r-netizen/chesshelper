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
import kotlin.math.abs

class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())

    private var bubble: TextView? = null
    private var panel: LinearLayout? = null
    private val testViews = mutableListOf<View>()

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

    private fun lp(w: Int, h: Int, touchable: Boolean): WindowManager.LayoutParams {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        if (!touchable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
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
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.y = dp(100)
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

        val test = Button(this)
        test.text = "تجربة المربعات"
        test.setOnClickListener { showTest() }
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

    // ---------- test squares (red = from, blue = to) ----------

    private fun square(stroke: Int, fill: Int): View {
        val v = View(this)
        val g = GradientDrawable()
        g.setColor(fill)
        g.setStroke(dp(4), stroke)
        v.background = g
        return v
    }

    private fun clearTest() {
        for (v in testViews) safeRemove(v)
        testViews.clear()
    }

    private fun showTest() {
        clearTest()
        val red = square(Color.RED, Color.parseColor("#55FF0000"))
        val blue = square(Color.BLUE, Color.parseColor("#550000FF"))

        val pr = lp(dp(90), dp(90), false)
        pr.x = dp(40)
        pr.y = dp(350)
        val pb = lp(dp(90), dp(90), false)
        pb.x = dp(180)
        pb.y = dp(350)

        wm.addView(red, pr)
        wm.addView(blue, pb)
        testViews.add(red)
        testViews.add(blue)

        handler.postDelayed({ clearTest() }, 6000)
    }
}
