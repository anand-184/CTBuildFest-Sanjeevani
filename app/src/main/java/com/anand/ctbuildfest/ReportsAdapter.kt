package com.anand.ctbuildfest


import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class ReportsAdapter(
    private val reports: List<ReportWithLocation>,
    private val onItemClick: (ReportWithLocation) -> Unit
) : RecyclerView.Adapter<ReportsAdapter.ReportViewHolder>() {

    inner class ReportViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val reportCard: MaterialCardView = itemView.findViewById(R.id.reportCard)
        val titleText: TextView = itemView.findViewById(R.id.reportTitle)
        val descriptionText: TextView = itemView.findViewById(R.id.reportDescription)
        val locationText: TextView = itemView.findViewById(R.id.reportLocation)
        val severityText: TextView = itemView.findViewById(R.id.reportSeverity)
        val timestampText: TextView = itemView.findViewById(R.id.reportTimestamp)

        fun bind(report: ReportWithLocation) {
            titleText.text = report.title
            descriptionText.text = report.description
            locationText.text = report.location
            severityText.text = report.severity
            timestampText.text = report.timestamp

            // Set severity color
            val severityColor = when (report.severity) {
                "High" -> itemView.context.getColor(R.color.error)
                "Medium" -> itemView.context.getColor(R.color.primary)
                else -> itemView.context.getColor(android.R.color.darker_gray)
            }
            severityText.setTextColor(severityColor)

            // Item click listener
            reportCard.setOnClickListener {
                onItemClick(report)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReportViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_report, parent, false)
        return ReportViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReportViewHolder, position: Int) {
        holder.bind(reports[position])
    }

    override fun getItemCount(): Int = reports.size
}
