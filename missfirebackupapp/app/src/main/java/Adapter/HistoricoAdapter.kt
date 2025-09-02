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
    private val onItemClick: (HistoricoItem) -> Unit,
    private val onSyncClick: (HistoricoItem) -> Unit
) : RecyclerView.Adapter<HistoricoAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val titulo: TextView = view.findViewById(R.id.tvTituloRegistro)
    val status: TextView = view.findViewById(R.id.tvStatus)
    val iconStatus: ImageView = view.findViewById(R.id.iconStatus)
    val btnSync: View? = view.findViewById(R.id.btnSync)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_historico, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = lista[position]
        holder.titulo.text = item.titulo
        // Interpretar status textual se já embutido no titulo ou se HistoricoItem for estendido futuramente.
        // Aqui mantemos logica binária porém adaptamos para mostrar possibilidade de sincronização.
        holder.status.text = when (item.status) {
            "SINCRONIZADO" -> "Sincronizado"
            "PRONTO" -> if (item.syncError) "A sincronizar" else "Pronto"
            else -> "Incompleto"
        }
        when (item.status) {
            "SINCRONIZADO" -> {
                holder.status.setTextColor(holder.itemView.context.getColor(R.color.gray))
                holder.iconStatus.visibility = View.GONE
                holder.btnSync?.visibility = View.GONE
            }
            "PRONTO" -> {
                holder.status.setTextColor(holder.itemView.context.getColor(
                    if (item.syncError) R.color.redAccent else R.color.gray
                ))
                holder.iconStatus.visibility = if (item.syncError) View.VISIBLE else View.GONE
                holder.btnSync?.visibility = if (item.syncError) View.VISIBLE else View.GONE
                if (item.syncError) holder.btnSync?.setOnClickListener { onSyncClick(item) }
            }
            else -> { // INCOMPLETO
                holder.status.setTextColor(holder.itemView.context.getColor(R.color.redAccent))
                holder.iconStatus.visibility = View.VISIBLE
                holder.btnSync?.visibility = View.GONE
            }
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
