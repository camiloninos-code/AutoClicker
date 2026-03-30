package com.autoclicker.app

import android.app.AlertDialog
import android.content.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var rvPoints: RecyclerView
    private lateinit var adapter: ClickPointAdapter
    private lateinit var tvStatus: TextView
    private lateinit var tvCountdown: TextView
    private lateinit var etRepeat: EditText
    private lateinit var etInterval: EditText
    private lateinit var etStartDelay: EditText
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnAddClick: Button
    private lateinit var btnAddMulti: Button
    private lateinit var btnAddSwipe: Button
    private lateinit var btnPreview: Button
    private lateinit var btnRecord: Button
    private lateinit var btnStopCondition: Button
    private lateinit var btnExport: Button
    private lateinit var btnImport: Button

    private val points = mutableListOf<ClickPoint>()
    private var stopCondition = StopCondition()
    private var isRecording = false
    private var currentScriptName = "Script"

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { importScriptFromUri(it) } }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                AppConstants.ACTION_STATE_CHANGED ->
                    updateStatusUI(intent.getStringExtra(AppConstants.EXTRA_STATE))
                AppConstants.ACTION_CLICK_CAPTURED -> {
                    val x = intent.getIntExtra(AppConstants.EXTRA_X, 0)
                    val y = intent.getIntExtra(AppConstants.EXTRA_Y, 0)
                    addPoint(ClickPoint(x, y))
                }
                AppConstants.ACTION_SWIPE_CAPTURED -> {
                    val x1 = intent.getIntExtra(AppConstants.EXTRA_X, 0)
                    val y1 = intent.getIntExtra(AppConstants.EXTRA_Y, 0)
                    val x2 = intent.getIntExtra(AppConstants.EXTRA_SWIPE_X2, 0)
                    val y2 = intent.getIntExtra(AppConstants.EXTRA_SWIPE_Y2, 0)
                    addPoint(ClickPoint(x1, y1, swipeToX = x2, swipeToY = y2))
                }
                AppConstants.ACTION_COUNTDOWN -> {
                    val n = intent.getIntExtra(AppConstants.EXTRA_COUNTDOWN, 0)
                    tvCountdown.visibility = View.VISIBLE
                    tvCountdown.text = "⏱ Empezando en $n…"
                    if (n == 0) tvCountdown.visibility = View.GONE
                }
                AppConstants.ACTION_RECORD_POINT -> {
                    val x   = intent.getIntExtra(AppConstants.EXTRA_X, 0)
                    val y   = intent.getIntExtra(AppConstants.EXTRA_Y, 0)
                    val dur = intent.getLongExtra(AppConstants.EXTRA_DURATION_MS, 50L)
                    addPoint(ClickPoint(x, y, durationMs = dur))
                }
                AppConstants.ACTION_RECORDER_STOP -> {
                    isRecording = false
                    btnRecord.text = "⏺ Grabar"
                }
            }
        }
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_v2)
        bindViews()
        setupRecyclerView()
        setupListeners()
        registerLocalReceiver()
        handleIncomingScript(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIncomingScript(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
    }

    // ── Cargar script desde ScriptsActivity ───────────────────────────────────

    private fun handleIncomingScript(intent: Intent) {
        val json = intent.getStringExtra("load_script_json") ?: return
        try {
            val config = ScriptRepository.scriptFromJsonString(json)
            currentScriptName = config.name
            points.clear()
            points.addAll(config.points)
            adapter.notifyDataSetChanged()
            etRepeat.setText(config.repeatCount.toString())
            etInterval.setText(config.intervalMs.toString())
            etStartDelay.setText((config.startDelayMs / 1000).toString())
            stopCondition = config.stopCondition
            val label = if (stopCondition.enabled) "🛑 Condición: ON" else "🛑 Condición: OFF"
            btnStopCondition.text = label
            title = config.name
        } catch (_: Exception) {}
    }

    // ── Setup ──────────────────────────────────────────────────────────────────

    private fun bindViews() {
        rvPoints          = findViewById(R.id.rvPoints)
        tvStatus          = findViewById(R.id.tvStatus)
        tvCountdown       = findViewById(R.id.tvCountdown)
        etRepeat          = findViewById(R.id.etRepeat)
        etInterval        = findViewById(R.id.etInterval)
        etStartDelay      = findViewById(R.id.etStartDelay)
        btnStart          = findViewById(R.id.btnStart)
        btnStop           = findViewById(R.id.btnStop)
        btnAddClick       = findViewById(R.id.btnAddClick)
        btnAddMulti       = findViewById(R.id.btnAddMulti)
        btnAddSwipe       = findViewById(R.id.btnAddSwipe)
        btnPreview        = findViewById(R.id.btnPreview)
        btnRecord         = findViewById(R.id.btnRecord)
        btnStopCondition  = findViewById(R.id.btnStopCondition)
        btnExport         = findViewById(R.id.btnExport)
        btnImport         = findViewById(R.id.btnImport)
    }

    private fun setupRecyclerView() {
        adapter = ClickPointAdapter(points,
            onEdit   = { pos -> showEditDialog(pos) },
            onDelete = { pos -> points.removeAt(pos); adapter.notifyItemRemoved(pos) }
        )
        rvPoints.layoutManager = LinearLayoutManager(this)
        rvPoints.adapter = adapter
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder,
                                target: RecyclerView.ViewHolder): Boolean {
                val from = vh.adapterPosition; val to = target.adapterPosition
                points.add(to, points.removeAt(from)); adapter.notifyItemMoved(from, to)
                return true
            }
            override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) {}
        }).attachToRecyclerView(rvPoints)
    }

    private fun setupListeners() {
        btnStart.setOnClickListener { startScript() }
        btnStop.setOnClickListener  { AutoClickService.instance?.stopScript() }
        btnAddClick.setOnClickListener {
            startService(Intent(this, CaptureOverlayService::class.java)
                .putExtra("mode", AppConstants.MODE_SINGLE))
            moveTaskToBack(true)
        }
        btnAddMulti.setOnClickListener {
            startService(Intent(this, CaptureOverlayService::class.java)
                .putExtra("mode", AppConstants.MODE_MULTI))
            moveTaskToBack(true)
        }
        btnAddSwipe.setOnClickListener {
            startService(Intent(this, CaptureOverlayService::class.java)
                .putExtra("mode", AppConstants.MODE_SWIPE))
            moveTaskToBack(true)
        }
        btnPreview.setOnClickListener          { openPreview() }
        btnRecord.setOnClickListener           { toggleRecorder() }
        btnStopCondition.setOnClickListener    { showStopConditionDialog() }
        btnExport.setOnClickListener           { exportCurrentScript() }
        btnImport.setOnClickListener           { importLauncher.launch("application/json") }
        findViewById<Button>(R.id.btnScripts).setOnClickListener {
            // Guardar primero si hay puntos
            if (points.isNotEmpty()) saveCurrentScript()
            startActivity(Intent(this, ScriptsActivity::class.java))
        }
    }

    private fun registerLocalReceiver() {
        val filter = IntentFilter().apply {
            addAction(AppConstants.ACTION_STATE_CHANGED)
            addAction(AppConstants.ACTION_CLICK_CAPTURED)
            addAction(AppConstants.ACTION_SWIPE_CAPTURED)
            addAction(AppConstants.ACTION_COUNTDOWN)
            addAction(AppConstants.ACTION_RECORD_POINT)
            addAction(AppConstants.ACTION_RECORDER_STOP)
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter)
    }

    // ── Script ─────────────────────────────────────────────────────────────────

    private fun startScript() {
        if (!isAccessibilityEnabled()) {
            Toast.makeText(this, "Activa el servicio de accesibilidad primero", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        if (points.isEmpty()) {
            Toast.makeText(this, "Agrega al menos un punto", Toast.LENGTH_SHORT).show()
            return
        }
        AutoClickService.instance?.startScript(buildConfiguredScript())
            ?: Toast.makeText(this, "Servicio no activo", Toast.LENGTH_SHORT).show()
    }

    private fun buildConfiguredScript() = ScriptConfig(
        name         = currentScriptName,
        points       = points.toList(),
        repeatCount  = etRepeat.text.toString().toIntOrNull() ?: 1,
        intervalMs   = etInterval.text.toString().toLongOrNull() ?: 1000L,
        startDelayMs = (etStartDelay.text.toString().toLongOrNull() ?: 0L) * 1000L,
        stopCondition = stopCondition
    )

    private fun saveCurrentScript() {
        val config = buildConfiguredScript()
        val all = ScriptRepository.loadScripts(this).toMutableList()
        val idx = all.indexOfFirst { it.name == config.name }
        if (idx >= 0) all[idx] = config else all.add(config)
        ScriptRepository.saveScripts(this, all)
    }

    private fun addPoint(p: ClickPoint) {
        points.add(p); adapter.notifyItemInserted(points.size - 1)
    }

    // ── Grabador ───────────────────────────────────────────────────────────────

    private fun toggleRecorder() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Permite 'Mostrar sobre otras apps'", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")))
            return
        }
        isRecording = !isRecording
        if (isRecording) {
            btnRecord.text = "⏹ Parar grabación"
            startService(Intent(this, RecorderOverlayService::class.java))
            moveTaskToBack(true)
        } else {
            btnRecord.text = "⏺ Grabar"
            stopService(Intent(this, RecorderOverlayService::class.java))
        }
    }

    // ── Vista previa ───────────────────────────────────────────────────────────

    private fun openPreview() {
        if (points.isEmpty()) {
            Toast.makeText(this, "No hay puntos para previsualizar", Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(Intent(this, PreviewActivity::class.java).apply {
            putExtra("script_json", ScriptRepository.scriptToJsonString(buildConfiguredScript()))
        })
    }

    // ── Condición de parada ────────────────────────────────────────────────────

    private fun showStopConditionDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_stop_condition, null)
        val switchEnabled = view.findViewById<Switch>(R.id.switchStopEnabled)
        val etX           = view.findViewById<EditText>(R.id.etStopPixelX)
        val etY           = view.findViewById<EditText>(R.id.etStopPixelY)
        val etTol         = view.findViewById<EditText>(R.id.etStopTolerance)
        val rbMatches     = view.findViewById<android.widget.RadioButton>(R.id.rbStopWhenMatches)
        val tvHex         = view.findViewById<TextView>(R.id.tvStopColorHex)
        val colorPreview  = view.findViewById<View>(R.id.vStopColorPreview)
        val tvApiWarn     = view.findViewById<TextView>(R.id.tvStopApiWarning)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R)
            tvApiWarn.visibility = View.VISIBLE

        switchEnabled.isChecked = stopCondition.enabled
        etX.setText(stopCondition.pixelX.toString())
        etY.setText(stopCondition.pixelY.toString())
        etTol.setText(stopCondition.tolerance.toString())
        rbMatches.isChecked = stopCondition.stopWhenMatches
        colorPreview.setBackgroundColor(stopCondition.targetColor)
        tvHex.text = String.format("#%06X", 0xFFFFFF and stopCondition.targetColor)

        view.findViewById<Button>(R.id.btnCaptureColor).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Toast.makeText(this,
                    "Captura de color disponible durante la ejecución del script",
                    Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this,
                    getString(R.string.stop_condition_not_supported),
                    Toast.LENGTH_LONG).show()
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Condición de parada por pixel")
            .setView(view)
            .setPositiveButton("Guardar") { _, _ ->
                stopCondition = StopCondition(
                    enabled          = switchEnabled.isChecked,
                    pixelX           = etX.text.toString().toIntOrNull() ?: 0,
                    pixelY           = etY.text.toString().toIntOrNull() ?: 0,
                    targetColor      = stopCondition.targetColor,
                    tolerance        = etTol.text.toString().toIntOrNull() ?: 15,
                    stopWhenMatches  = rbMatches.isChecked
                )
                btnStopCondition.text = if (stopCondition.enabled) "🛑 Condición: ON"
                                        else "🛑 Condición: OFF"
            }
            .setNegativeButton("Cancelar", null).show()
    }

    // ── Export / Import ────────────────────────────────────────────────────────

    private fun exportCurrentScript() {
        if (points.isEmpty()) {
            Toast.makeText(this, "No hay puntos en el script actual", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val file = File(cacheDir, "script_export.json")
            file.writeText(ScriptRepository.scriptToJsonString(buildConfiguredScript()))
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            startActivity(Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, "Exportar script"
            ))
        } catch (e: Exception) {
            Toast.makeText(this, "Error al exportar: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun importScriptFromUri(uri: Uri) {
        try {
            val json = contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: return
            // Confirmar si hay puntos cargados
            if (points.isNotEmpty()) {
                AlertDialog.Builder(this)
                    .setTitle("Importar script")
                    .setMessage("¿Reemplazar los ${points.size} puntos actuales con el script importado?")
                    .setPositiveButton("Reemplazar") { _, _ -> applyImport(json) }
                    .setNegativeButton("Cancelar", null).show()
            } else {
                applyImport(json)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error al importar: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun applyImport(json: String) {
        val config = ScriptRepository.scriptFromJsonString(json)
        currentScriptName = config.name
        points.clear(); points.addAll(config.points)
        adapter.notifyDataSetChanged()
        etRepeat.setText(config.repeatCount.toString())
        etInterval.setText(config.intervalMs.toString())
        etStartDelay.setText((config.startDelayMs / 1000).toString())
        stopCondition = config.stopCondition
        btnStopCondition.text = if (stopCondition.enabled) "🛑 Condición: ON" else "🛑 Condición: OFF"
        Toast.makeText(this, "Script importado: ${config.name} (${points.size} puntos)",
            Toast.LENGTH_SHORT).show()
    }

    // ── Diálogo editar punto ───────────────────────────────────────────────────

    private fun showEditDialog(pos: Int) {
        val p = points[pos]
        val view = layoutInflater.inflate(R.layout.dialog_edit_point, null)
        val etLabel     = view.findViewById<EditText>(R.id.etPointLabel)
        val etDelay     = view.findViewById<EditText>(R.id.etPointDelay)
        val etDuration  = view.findViewById<EditText>(R.id.etPointDuration)
        val layoutSwipe = view.findViewById<View>(R.id.layoutSwipeDuration)
        val etSwipeDur  = view.findViewById<EditText>(R.id.etSwipeDuration)

        etLabel.setText(p.label)
        etDelay.setText(p.delayMs.toString())
        etDuration.setText(p.durationMs.toString())
        if (p.isSwipe) { layoutSwipe.visibility = View.VISIBLE; etSwipeDur.setText(p.swipeDurationMs.toString()) }

        AlertDialog.Builder(this)
            .setTitle(if (p.isSwipe) "Editar swipe" else "Editar punto")
            .setView(view)
            .setPositiveButton("Guardar") { _, _ ->
                points[pos] = p.copy(
                    label        = etLabel.text.toString(),
                    delayMs      = etDelay.text.toString().toLongOrNull() ?: 0L,
                    durationMs   = etDuration.text.toString().toLongOrNull() ?: 50L,
                    swipeDurationMs = if (p.isSwipe)
                        etSwipeDur.text.toString().toLongOrNull() ?: 300L
                    else p.swipeDurationMs
                )
                adapter.notifyItemChanged(pos)
            }
            .setNegativeButton("Cancelar", null).show()
    }

    // ── Utils ──────────────────────────────────────────────────────────────────

    private fun updateStatusUI(state: String?) {
        tvCountdown.visibility = View.GONE
        tvStatus.text = when (state) {
            AppConstants.STATE_RUNNING -> "▶ EN EJECUCIÓN"
            AppConstants.STATE_PAUSED  -> "⏸ PAUSADO"
            else -> "⏹ Detenido"
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val enabled = android.provider.Settings.Secure.getString(
            contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return enabled.contains(packageName)
    }
}
