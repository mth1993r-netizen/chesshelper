package com.chessoverlay.helper

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        }

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER
        root.layoutDirection = View.LAYOUT_DIRECTION_RTL
        root.setBackgroundColor(Color.parseColor("#121212"))
        root.setPadding(48, 48, 48, 48)

        val title = TextView(this)
        title.text = "Chess Helper ♟"
        title.textSize = 26f
        title.setTextColor(Color.WHITE)
        title.gravity = Gravity.CENTER

        status = TextView(this)
        status.textSize = 16f
        status.setTextColor(Color.LTGRAY)
        status.gravity = Gravity.CENTER
        status.setPadding(0, 32, 0, 32)

        val start = Button(this)
        start.text = "تشغيل"
        start.setOnClickListener { startOverlay() }

        val stop = Button(this)
        stop.text = "إيقاف"
        stop.setOnClickListener {
            stopService(Intent(this, OverlayService::class.java))
        }

        root.addView(title)
        root.addView(status)
        root.addView(start)
        root.addView(stop)
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        status.text = if (Settings.canDrawOverlays(this))
            "إذن الرسم فوق التطبيقات: مفعّل ✅"
        else
            "إذن الرسم فوق التطبيقات: غير مفعّل ❌"
    }

    private fun startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "فعّل الإذن ثم ارجع واضغط تشغيل", Toast.LENGTH_LONG).show()
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
            return
        }
        startForegroundService(Intent(this, OverlayService::class.java))
        finish()
    }
}
