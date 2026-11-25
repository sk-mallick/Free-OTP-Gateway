package com.multi.encription.sms.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.multi.encription.sms.R
import com.multi.encription.sms.database.SmsEntity
import com.multi.encription.sms.database.SmsStatus
import java.text.SimpleDateFormat
import java.util.*

class SmsHistoryAdapter(
    private val onItemClick: (SmsEntity) -> Unit
) : ListAdapter<SmsEntity, SmsHistoryAdapter.SmsViewHolder>(SmsDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SmsViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_sms_history, parent, false)
        return SmsViewHolder(view, onItemClick)
    }

    override fun onBindViewHolder(holder: SmsViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class SmsViewHolder(
        itemView: View,
        private val onItemClick: (SmsEntity) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val phoneNumberText: TextView = itemView.findViewById(R.id.phoneNumberText)
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val statusBadgeContainer: LinearLayout = itemView.findViewById(R.id.statusBadgeContainer)
        private val statusIndicatorDot: View = itemView.findViewById(R.id.statusIndicatorDot)
        private val statusText: TextView = itemView.findViewById(R.id.statusText)
        private val timestampText: TextView = itemView.findViewById(R.id.timestampText)
        private val requestIdText: TextView = itemView.findViewById(R.id.requestIdText)

        private val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())

        fun bind(sms: SmsEntity) {
            phoneNumberText.text = sms.phoneNumber
            messageText.text = sms.message

            val ctx = itemView.context
            val (label, bgColor, fgColor) = when (sms.status) {
                SmsStatus.DELIVERED -> Triple(
                    "Delivered",
                    ContextCompat.getColor(ctx, R.color.status_delivered_bg),
                    ContextCompat.getColor(ctx, R.color.status_delivered)
                )
                SmsStatus.SENT -> Triple(
                    "Sent",
                    ContextCompat.getColor(ctx, R.color.status_sent_bg),
                    ContextCompat.getColor(ctx, R.color.status_sent)
                )
                SmsStatus.PENDING -> Triple(
                    "Pending",
                    ContextCompat.getColor(ctx, R.color.status_pending_bg),
                    ContextCompat.getColor(ctx, R.color.status_pending)
                )
                SmsStatus.FAILED -> Triple(
                    "Failed",
                    ContextCompat.getColor(ctx, R.color.status_failed_bg),
                    ContextCompat.getColor(ctx, R.color.status_failed)
                )
                SmsStatus.UNKNOWN -> Triple(
                    "Unknown",
                    ContextCompat.getColor(ctx, R.color.status_unknown_bg),
                    ContextCompat.getColor(ctx, R.color.status_unknown)
                )
            }

            statusText.text = label
            statusText.setTextColor(fgColor)
            statusIndicatorDot.backgroundTintList = ColorStateList.valueOf(fgColor)
            statusBadgeContainer.backgroundTintList = ColorStateList.valueOf(bgColor)

            timestampText.text = dateFormat.format(Date(sms.timestamp))

            if (!sms.requestId.isNullOrBlank()) {
                val shortId = if (sms.requestId.length > 8) sms.requestId.take(8) else sms.requestId
                requestIdText.text = "ID: $shortId"
                requestIdText.visibility = View.VISIBLE
            } else {
                requestIdText.visibility = View.GONE
            }

            itemView.setOnClickListener {
                onItemClick(sms)
            }
        }
    }

    class SmsDiffCallback : DiffUtil.ItemCallback<SmsEntity>() {
        override fun areItemsTheSame(oldItem: SmsEntity, newItem: SmsEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: SmsEntity, newItem: SmsEntity): Boolean {
            return oldItem == newItem
        }
    }
}
