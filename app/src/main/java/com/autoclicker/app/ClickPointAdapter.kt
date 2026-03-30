package com.autoclicker.app

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ClickPointAdapter(
    private val points: MutableList<ClickPoint>,
    private val onEdit: (Int) -> Unit,
    private val onDelete: (Int) -> Unit
) : RecyclerView.Adapter<ClickPointAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvIndex: TextView = view.findViewById(R.id.tvPointLabel)
        val tvType: TextView = view.findViewById(R.id.tvPointType)
        val tvCoords: TextView = view.findViewById(R.id.tvPointCoords)
        val tvLabel: TextView = view.findViewById(R.id.tvPointLabel)
        val btnEdit: View = view.findViewById(R.id.btnEditPoint)
        val btnDelete: View = view.findViewById(R.id.btnDeletePoint)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_click_point, parent, false))

    override fun getItemCount() = points.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = points[position]
        holder.tvIndex.text = "${position + 1}"
        if (p.isSwipe) {
            holder.tvType.text = "⟹ SWIPE"
            holder.tvType.setTextColor(Color.parseColor("#FF6F00"))
            holder.tvCoords.text = "(${p.x},${p.y}) → (${p.swipeToX},${p.swipeToY})"
        } else {
            holder.tvType.text = "● CLIC"
            holder.tvType.setTextColor(Color.parseColor("#1565C0"))
            holder.tvCoords.text = "(${p.x}, ${p.y})"
        }
        holder.tvLabel.text = p.label.ifEmpty { "" }
        holder.tvLabel.visibility = if (p.label.isEmpty()) View.GONE else View.VISIBLE
        holder.btnEdit.setOnClickListener { onEdit(holder.adapterPosition) }
        holder.btnDelete.setOnClickListener { onDelete(holder.adapterPosition) }
    }
}
