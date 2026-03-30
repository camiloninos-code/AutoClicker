package com.autoclicker.app

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ScriptsActivity : AppCompatActivity() {

    private lateinit var rvScripts: RecyclerView
    private lateinit var btnNewScript: Button
    private lateinit var tvEmpty: TextView
    private lateinit var adapter: ScriptsAdapter
    private val scripts = mutableListOf<ScriptConfig>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scripts)
        supportActionBar?.title = "Mis Scripts"

        rvScripts    = findViewById(R.id.rvScripts)
        btnNewScript = findViewById(R.id.btnNewScript)
        tvEmpty      = findViewById(R.id.tvEmpty)

        adapter = ScriptsAdapter(
            scripts     = scripts,
            onOpen      = { openScript(it) },
            onRename    = { showRenameDialog(it) },
            onDuplicate = { duplicateScript(it) },
            onDelete    = { confirmDelete(it) }
        )
        rvScripts.layoutManager = LinearLayoutManager(this)
        rvScripts.adapter = adapter

        btnNewScript.setOnClickListener { createNewScript() }
    }

    override fun onResume() {
        super.onResume()
        reloadScripts()
    }

    private fun reloadScripts() {
        scripts.clear()
        scripts.addAll(ScriptRepository.loadScripts(this))
        adapter.notifyDataSetChanged()
        tvEmpty.visibility = if (scripts.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun createNewScript() {
        val script = ScriptConfig(name = "Script ${scripts.size + 1}")
        val all = ScriptRepository.loadScripts(this).toMutableList()
        all.add(script)
        ScriptRepository.saveScripts(this, all)
        openScript(script)
    }

    private fun openScript(script: ScriptConfig) {
        startActivity(Intent(this, MainActivity::class.java).apply {
            putExtra("load_script_json", ScriptRepository.scriptToJsonString(script))
        })
    }

    private fun showRenameDialog(script: ScriptConfig) {
        val et = EditText(this).apply { setText(script.name); selectAll() }
        AlertDialog.Builder(this)
            .setTitle("Renombrar script").setView(et)
            .setPositiveButton("Guardar") { _, _ ->
                val newName = et.text.toString().trim().ifBlank { script.name }
                val all = ScriptRepository.loadScripts(this).toMutableList()
                val idx = all.indexOfFirst { it.name == script.name }
                if (idx >= 0) all[idx] = script.copy(name = newName)
                ScriptRepository.saveScripts(this, all)
                reloadScripts()
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun duplicateScript(script: ScriptConfig) {
        val copy = script.copy(name = "${script.name} (copia)")
        val all = ScriptRepository.loadScripts(this).toMutableList()
        all.add(copy)
        ScriptRepository.saveScripts(this, all)
        reloadScripts()
        Toast.makeText(this, "Script duplicado", Toast.LENGTH_SHORT).show()
    }

    private fun confirmDelete(script: ScriptConfig) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar script")
            .setMessage("¿Eliminar \"${script.name}\"? Esta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ ->
                val all = ScriptRepository.loadScripts(this).toMutableList()
                all.removeAll { it.name == script.name }
                ScriptRepository.saveScripts(this, all)
                reloadScripts()
            }
            .setNegativeButton("Cancelar", null).show()
    }
}

// ─── Adapter ──────────────────────────────────────────────────────────────────

class ScriptsAdapter(
    private val scripts: List<ScriptConfig>,
    private val onOpen: (ScriptConfig) -> Unit,
    private val onRename: (ScriptConfig) -> Unit,
    private val onDuplicate: (ScriptConfig) -> Unit,
    private val onDelete: (ScriptConfig) -> Unit
) : RecyclerView.Adapter<ScriptsAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView     = view.findViewById(R.id.tvScriptItemName)
        val tvMeta: TextView     = view.findViewById(R.id.tvScriptItemMeta)
        val btnMenu: ImageButton = view.findViewById(R.id.btnScriptMenu)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_script, parent, false))

    override fun getItemCount() = scripts.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val s = scripts[position]
        holder.tvName.text = s.name

        val repeatText = if (s.repeatCount == 0) "∞ ciclos" else "${s.repeatCount} ciclos"
        val clicks = s.points.count { !it.isSwipe }
        val swipes = s.points.count { it.isSwipe }
        val stopText = if (s.stopCondition.enabled) "  ·  🛑 condición ON" else ""
        holder.tvMeta.text = "${s.points.size} puntos ($clicks clics, $swipes swipes)  ·  " +
                "${s.intervalMs}ms  ·  $repeatText$stopText"

        holder.itemView.setOnClickListener { onOpen(s) }
        holder.btnMenu.setOnClickListener { view ->
            PopupMenu(view.context, view).apply {
                menu.add(0, 1, 0, "Abrir")
                menu.add(0, 2, 0, "Renombrar")
                menu.add(0, 3, 0, "Duplicar")
                menu.add(0, 4, 0, "Eliminar")
                setOnMenuItemClickListener {
                    when (it.itemId) {
                        1 -> onOpen(s); 2 -> onRename(s); 3 -> onDuplicate(s); 4 -> onDelete(s)
                    }
                    true
                }
                show()
            }
        }
    }
}
