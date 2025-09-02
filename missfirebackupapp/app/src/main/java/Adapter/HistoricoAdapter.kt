package com.example.missfirebackupapp.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.missfirebackupapp.R
import com.example.missfirebackupapp.model.HistoricoItem

class HistoricoAdapter(
    private var lista: MutableList<HistoricoItem>,
    private val onItemClick: (HistoricoItem) -> Unit
) : RecyclerView.Adapter<HistoricoAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titulo: TextView = view.findViewById(R.id.tvTituloRegistro)
        val status: TextView = view.findViewById(R.id.tvStatus)
        val iconStatus: ImageView = view.findViewById(R.id.iconStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_historico, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = lista[position]
        holder.titulo.text = item.titulo
        holder.status.text = if (item.concluido) "Concluído" else "Pendente"

        // Cores e ícone baseado no status
        if (item.concluido) {
            holder.status.setTextColor(holder.itemView.context.getColor(R.color.gray))
            holder.iconStatus.visibility = View.GONE
            holder.itemView.isActivated = false
        } else {
            holder.status.setTextColor(holder.itemView.context.getColor(R.color.redAccent))
            holder.iconStatus.visibility = View.VISIBLE
            holder.itemView.isActivated = true
        }

        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = lista.size

    fun atualizarLista(novaLista: MutableList<HistoricoItem>) {
        lista = novaLista
        notifyDataSetChanged()
    }
}
