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
    private val onSyncClick: (HistoricoItem) -> Unit,
    private val onDeleteClick: (HistoricoItem) -> Unit
) : RecyclerView.Adapter<HistoricoAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val titulo: TextView = view.findViewById(R.id.tvTituloRegistro)
    val status: TextView = view.findViewById(R.id.tvStatus)
    val iconStatus: ImageView = view.findViewById(R.id.iconStatus)
    val btnSync: View? = view.findViewById(R.id.btnSync)
    val btnDelete: View? = view.findViewById(R.id.btnDelete)
    val containerConclusao: View? = view.findViewById(R.id.containerConclusao)
    val tvCausa: TextView? = view.findViewById(R.id.tvCausa)
    val tvMedidas: TextView? = view.findViewById(R.id.tvMedidas)
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
            "CONCLUIDA" -> "Investigação concluída"
            "EM_ANDAMENTO" -> "Investigação em andamento"
            "SINCRONIZADO" -> "Sincronizado"
            "PRONTO" -> if (item.syncError) "A sincronizar" else "Pronto"
            else -> "Incompleto"
        }
    when (item.status) {
            "CONCLUIDA" -> {
                holder.status.setTextColor(holder.itemView.context.getColor(R.color.statusConcluida))
                holder.iconStatus.visibility = View.GONE
                holder.btnSync?.visibility = if (item.syncError) View.VISIBLE else View.GONE
                if (item.syncError) holder.btnSync?.setOnClickListener { onSyncClick(item) }
        holder.btnDelete?.visibility = View.VISIBLE
        holder.btnDelete?.setOnClickListener { onDeleteClick(item) }
                holder.containerConclusao?.visibility = View.GONE // ocultar para não poluir a lista
            }
            "EM_ANDAMENTO" -> {
                holder.status.setTextColor(holder.itemView.context.getColor(R.color.statusLocal)) // amber style color for in-progress
                holder.iconStatus.visibility = View.GONE
        holder.btnSync?.visibility = View.GONE
        holder.btnDelete?.visibility = View.VISIBLE
        holder.btnDelete?.setOnClickListener { onDeleteClick(item) }
                holder.containerConclusao?.visibility = View.GONE
            }
            "SINCRONIZADO" -> {
                holder.status.setTextColor(holder.itemView.context.getColor(R.color.gray))
                holder.iconStatus.visibility = View.GONE
                holder.btnSync?.visibility = View.GONE
        holder.btnDelete?.visibility = View.VISIBLE
        holder.btnDelete?.setOnClickListener { onDeleteClick(item) }
                holder.containerConclusao?.visibility = View.GONE
            }
            "PRONTO" -> {
                holder.status.setTextColor(holder.itemView.context.getColor(
                    if (item.syncError) R.color.redAccent else R.color.gray
                ))
                holder.iconStatus.visibility = if (item.syncError) View.VISIBLE else View.GONE
                if (item.syncError) {
                    holder.iconStatus.setOnClickListener { v ->
                        android.widget.Toast.makeText(v.context, item.syncErrorMessage ?: "Falha de sincronização", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
        holder.btnSync?.visibility = if (item.syncError) View.VISIBLE else View.GONE
        if (item.syncError) holder.btnSync?.setOnClickListener { onSyncClick(item) }
        holder.btnDelete?.visibility = View.VISIBLE
        holder.btnDelete?.setOnClickListener { onDeleteClick(item) }
                holder.containerConclusao?.visibility = View.GONE
            }
            else -> { // INCOMPLETO
                holder.status.setTextColor(holder.itemView.context.getColor(R.color.redAccent))
                holder.iconStatus.visibility = View.VISIBLE
                holder.iconStatus.setOnClickListener { v ->
                    if (item.syncError) {
                        android.widget.Toast.makeText(v.context, item.syncErrorMessage ?: "Erro de sync", android.widget.Toast.LENGTH_LONG).show()
                    } else {
                        android.widget.Toast.makeText(v.context, "Registro incompleto", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                holder.btnSync?.visibility = View.GONE
        holder.btnDelete?.visibility = View.VISIBLE
        holder.btnDelete?.setOnClickListener { onDeleteClick(item) }
                holder.containerConclusao?.visibility = View.GONE
            }
        }

        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = lista.size

    fun getItem(position: Int): HistoricoItem = lista[position]

    fun atualizarLista(novaLista: MutableList<HistoricoItem>) {
        lista = novaLista
        notifyDataSetChanged()
    }
}
