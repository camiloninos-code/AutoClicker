package com.autoclicker.app

import android.content.Context
import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

object ScriptRepository {

    private const val PREF_NAME = "scripts_prefs"
    private const val KEY_SCRIPTS = "scripts_json"

    fun saveScripts(context: Context, scripts: List<ScriptConfig>) {
        val arr = JSONArray()
        scripts.forEach { arr.put(scriptToJson(it)) }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_SCRIPTS, arr.toString()).apply()
    }

    fun loadScripts(context: Context): MutableList<ScriptConfig> {
        val raw = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SCRIPTS, null) ?: return mutableListOf()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { jsonToScript(arr.getJSONObject(it)) }.toMutableList()
        } catch (e: Exception) { mutableListOf() }
    }

    // ── JSON serialization ────────────────────────────────────────────────────

    fun scriptToJson(s: ScriptConfig): JSONObject = JSONObject().apply {
        put("name", s.name)
        put("repeatCount", s.repeatCount)
        put("intervalMs", s.intervalMs)
        put("startDelayMs", s.startDelayMs)  // v3
        put("points", JSONArray().also { a -> s.points.forEach { a.put(pointToJson(it)) } })
        // v4: stop condition
        put("stopCondition", JSONObject().apply {
            put("enabled", s.stopCondition.enabled)
            put("pixelX", s.stopCondition.pixelX)
            put("pixelY", s.stopCondition.pixelY)
            put("targetColor", s.stopCondition.targetColor)
            put("tolerance", s.stopCondition.tolerance)
            put("stopWhenMatches", s.stopCondition.stopWhenMatches)
        })
    }

    fun jsonToScript(o: JSONObject): ScriptConfig {
        val pts = o.optJSONArray("points") ?: JSONArray()
        val scObj = o.optJSONObject("stopCondition")
        val sc = if (scObj != null) StopCondition(
            enabled = scObj.optBoolean("enabled", false),
            pixelX = scObj.optInt("pixelX", 0),
            pixelY = scObj.optInt("pixelY", 0),
            targetColor = scObj.optInt("targetColor", 0),
            tolerance = scObj.optInt("tolerance", 15),
            stopWhenMatches = scObj.optBoolean("stopWhenMatches", true)
        ) else StopCondition()
        return ScriptConfig(
            name = o.optString("name", "Script"),
            repeatCount = o.optInt("repeatCount", 1),
            intervalMs = o.optLong("intervalMs", 1000L),
            startDelayMs = o.optLong("startDelayMs", 0L),
            points = (0 until pts.length()).map { jsonToPoint(pts.getJSONObject(it)) },
            stopCondition = sc
        )
    }

    private fun pointToJson(p: ClickPoint): JSONObject = JSONObject().apply {
        put("x", p.x); put("y", p.y)
        put("delayMs", p.delayMs); put("durationMs", p.durationMs); put("label", p.label)
        put("swipeToX", p.swipeToX); put("swipeToY", p.swipeToY)
        put("swipeDurationMs", p.swipeDurationMs)
    }

    private fun jsonToPoint(o: JSONObject) = ClickPoint(
        x = o.optInt("x"), y = o.optInt("y"),
        delayMs = o.optLong("delayMs", 0L),
        durationMs = o.optLong("durationMs", 50L),
        label = o.optString("label", ""),
        swipeToX = o.optInt("swipeToX", -1),
        swipeToY = o.optInt("swipeToY", -1),
        swipeDurationMs = o.optLong("swipeDurationMs", 300L)
    )

    // ── Export / Import (v4) ──────────────────────────────────────────────────

    fun scriptToJsonString(s: ScriptConfig): String = scriptToJson(s).toString(2)

    fun scriptFromJsonString(json: String): ScriptConfig = jsonToScript(JSONObject(json))
}
