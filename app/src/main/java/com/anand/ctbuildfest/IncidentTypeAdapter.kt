package com.anand.ctbuildfest


import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class IncidentTypeAdapter(
    private val types: List<String>,
    private val onTypeSelected: (String) -> Unit
) : RecyclerView.Adapter<IncidentTypeAdapter.TypeViewHolder>() {

    private var selectedPosition = -1

    inner class TypeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: CardView = itemView.findViewById(R.id.typeCard)
        val typeText: TextView = itemView.findViewById(R.id.typeText)

        fun bind(type: String, position: Int) {
            typeText.text = type

            val isSelected = position == selectedPosition
            if (isSelected) {
                card.setCardBackgroundColor(
                    ContextCompat.getColor(itemView.context, R.color.primary)
                )
                typeText.setTextColor(
                    ContextCompat.getColor(itemView.context, R.color.white)
                )
            } else {
                card.setCardBackgroundColor(
                    ContextCompat.getColor(itemView.context, R.color.CardColor)
                )
                typeText.setTextColor(
                    ContextCompat.getColor(itemView.context, R.color.black)
                )
            }

            card.setOnClickListener {
                val previousPosition = selectedPosition
                selectedPosition = position
                notifyItemChanged(previousPosition)
                notifyItemChanged(selectedPosition)
                onTypeSelected(type)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TypeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_incident_type, parent, false)
        return TypeViewHolder(view)
    }

    override fun onBindViewHolder(holder: TypeViewHolder, position: Int) {
        holder.bind(types[position], position)
    }

    override fun getItemCount() = types.size
}
