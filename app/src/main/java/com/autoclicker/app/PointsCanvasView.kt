package com.autoclicker.app

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.*

/**
 * PointsCanvasView — Canvas compartido entre PointsOverlayService y PreviewActivity.
 * Dibuja puntos de clic (azul) y swipes (naranja con flechas).
 */
class PointsCanvasView(context: Context, private val points: List<ClickPoint>) : View(context) {

    private val clickFill   = mkPaint(Color.argb(200, 33, 150, 243), Paint.Style.FILL)
    private val clickStroke = mkPaint(Color.WHITE, Paint.Style.STROKE, 3f)
    private val swipeFill   = mkPaint(Color.argb(200, 255, 120, 0), Paint.Style.FILL)
    private val swipeStroke = mkPaint(Color.WHITE, Paint.Style.STROKE, 3f)
    private val swipeLine   = mkPaint(Color.argb(220, 255, 140, 0), Paint.Style.STROKE, 4f)
    private val shadowPaint = mkPaint(Color.argb(80, 0, 0, 0), Paint.Style.FILL)
    private val seqLine     = mkPaint(Color.argb(100, 33, 150, 243), Paint.Style.STROKE, 2f).apply {
        pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 28f
        textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 20f; textAlign = Paint.Align.CENTER
        setShadowLayer(4f, 0f, 2f, Color.argb(180, 0, 0, 0))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.isEmpty()) return
        drawSequenceLines(canvas)
        points.forEachIndexed { i, p ->
            if (p.isSwipe) drawSwipe(canvas, p, i + 1)
            else drawClick(canvas, p, i + 1)
        }
    }

    private fun drawSequenceLines(canvas: Canvas) {
        val cp = points.filter { !it.isSwipe }
        if (cp.size < 2) return
        val path = Path().apply {
            moveTo(cp[0].x.toFloat(), cp[0].y.toFloat())
            for (i in 1 until cp.size) lineTo(cp[i].x.toFloat(), cp[i].y.toFloat())
        }
        canvas.drawPath(path, seqLine)
    }

    private fun drawClick(canvas: Canvas, p: ClickPoint, num: Int) {
        val x = p.x.toFloat(); val y = p.y.toFloat(); val r = 28f
        canvas.drawCircle(x + 2f, y + 2f, r + 2f, shadowPaint)
        canvas.drawCircle(x, y, r, clickFill)
        canvas.drawCircle(x, y, r, clickStroke)
        canvas.drawText("$num", x, y + textPaint.textSize / 3f, textPaint)
        if (p.label.isNotBlank()) canvas.drawText(p.label, x, y + r + 24f, labelPaint)
    }

    private fun drawSwipe(canvas: Canvas, p: ClickPoint, num: Int) {
        val x1 = p.x.toFloat(); val y1 = p.y.toFloat()
        val x2 = p.swipeToX.toFloat(); val y2 = p.swipeToY.toFloat()
        val r = 28f; val r2 = 20f

        canvas.drawLine(x1, y1, x2, y2, swipeLine)
        drawArrowHead(canvas, x1, y1, x2, y2)

        // Start circle
        canvas.drawCircle(x1 + 2f, y1 + 2f, r + 2f, shadowPaint)
        canvas.drawCircle(x1, y1, r, swipeFill)
        canvas.drawCircle(x1, y1, r, swipeStroke)
        canvas.drawText("$num", x1, y1 + textPaint.textSize / 3f, textPaint)

        // End circle
        canvas.drawCircle(x2 + 2f, y2 + 2f, r2 + 2f, shadowPaint)
        canvas.drawCircle(x2, y2, r2, swipeFill)
        canvas.drawCircle(x2, y2, r2, swipeStroke)

        val label = if (p.label.isNotBlank()) p.label else "Swipe $num"
        canvas.drawText(label, x1, y1 + r + 24f, labelPaint)
    }

    private fun drawArrowHead(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float) {
        val angle = atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())
        val len = 28f; val spread = Math.toRadians(30.0)
        val ax = (x2 - len * cos(angle - spread)).toFloat()
        val ay = (y2 - len * sin(angle - spread)).toFloat()
        val bx = (x2 - len * cos(angle + spread)).toFloat()
        val by = (y2 - len * sin(angle + spread)).toFloat()
        val path = Path().apply {
            moveTo(x2, y2); lineTo(ax, ay); moveTo(x2, y2); lineTo(bx, by)
        }
        canvas.drawPath(path, swipeLine)
    }

    private fun mkPaint(color: Int, style: Paint.Style, strokeWidth: Float = 0f) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; this.style = style; this.strokeWidth = strokeWidth
        }
}
