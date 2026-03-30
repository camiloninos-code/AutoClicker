package com.autoclicker.app

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class PreviewActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preview)

        val jsonStr = intent.getStringExtra("script_json") ?: run { finish(); return }
        val config = try { ScriptRepository.scriptFromJsonString(jsonStr) }
                     catch (e: Exception) { finish(); return }

        val canvasView = PointsCanvasView(this, config.points)
        val container = findViewById<FrameLayout>(R.id.previewContainer)
        container.addView(canvasView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        val count = config.points.size
        val clicks = config.points.count { !it.isSwipe }
        val swipes = config.points.count { it.isSwipe }
        findViewById<TextView>(R.id.tvPreviewCount).text =
            "$count acciones  ·  $clicks clics  ·  $swipes swipes"

        findViewById<View>(R.id.btnPreviewClose).setOnClickListener { finish() }
    }
}
