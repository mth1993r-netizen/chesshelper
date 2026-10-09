package com.chessoverlay.helper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.max

class BoardSelectorView(
    context: Context,
    var bx: Float,
    var by: Float,
    var bs: Float
) : View(context) {

    private val density = resources.displayMetrics.density

    private val linePaint = Paint().apply {
        color = Color.parseColor("#CCFFFFFF")
        strokeWidth = 1.5f * density
        style = Paint.Style.STROKE
    }
    private val framePaint = Paint().apply {
        color = Color.parseColor("#FF00E676")
        strokeWidth = 3f * density
        style = Paint.Style.STROKE
    }
    private val fillPaint = Paint().apply {
        color = Color.parseColor("#2200E676")
    }
    private val handlePaint = Paint().apply {
        color = Color.parseColor("#FF00E676")
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val handleR = 22f * density

    // 0 = ما في شي، 1 = تحريك، 2 = تكبير
    private var mode = 0
    private var lastX = 0f
    private var lastY = 0f

    override fun onDraw(c: Canvas) {
        c.drawRect(bx, by, bx + bs, by + bs, fillPaint)
        val cell = bs / 8f
        for (i in 1..7) {
            c.drawLine(bx + i * cell, by, bx + i * cell, by + bs, linePaint)
            c.drawLine(bx, by + i * cell, bx + bs, by + i * cell, linePaint)
        }
        c.drawRect(bx, by, bx + bs, by + bs, framePaint)
        c.drawCircle(bx + bs, by + bs, handleR, handlePaint)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                lastX = e.x
                lastY = e.y
                val dx = e.x - (bx + bs)
                val dy = e.y - (by + bs)
                val r = handleR * 2
                mode = if (dx * dx + dy * dy <= r * r) {
                    2
                } else if (e.x >= bx && e.x <= bx + bs && e.y >= by && e.y <= by + bs) {
                    1
                } else {
                    0
                }
                return mode != 0
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.x - lastX
                val dy = e.y - lastY
                lastX = e.x
                lastY = e.y
                if (mode == 1) {
                    bx += dx
                    by += dy
                } else if (mode == 2) {
                    bs = max(80f * density, bs + (dx + dy) / 2f)
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                mode = 0
                return true
            }
        }
        return false
    }
}
