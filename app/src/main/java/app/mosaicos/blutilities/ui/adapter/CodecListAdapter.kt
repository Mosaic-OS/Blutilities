package app.mosaicos.blutilities.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.mosaicos.blutilities.R
import app.mosaicos.blutilities.model.BluetoothCodecInfo

private const val VIEW_TYPE_HEADER = 0
private const val VIEW_TYPE_ITEM = 1

class CodecListAdapter(
    private val onItemClick: (BluetoothCodecInfo) -> Unit
) : ListAdapter<BluetoothCodecInfo, RecyclerView.ViewHolder>(DiffCallback()) {

    override fun getItemViewType(position: Int): Int =
        if (getItem(position).isHeader) VIEW_TYPE_HEADER else VIEW_TYPE_ITEM

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_HEADER) {
            HeaderViewHolder(inflater.inflate(R.layout.item_codec_header, parent, false))
        } else {
            ItemViewHolder(inflater.inflate(R.layout.item_codec, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is HeaderViewHolder -> holder.bind(item)
            is ItemViewHolder -> holder.bind(item, onItemClick)
        }
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvHeader: TextView = view.findViewById(R.id.tvHeader)
        fun bind(item: BluetoothCodecInfo) {
            tvHeader.text = item.codecName
        }
    }

    class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvCodecName: TextView = view.findViewById(R.id.tvCodecName)
        private val tvSubLabel: TextView = view.findViewById(R.id.tvSubLabel)
        private val ivSelected: ImageView = view.findViewById(R.id.ivSelected)

        fun bind(item: BluetoothCodecInfo, onClick: (BluetoothCodecInfo) -> Unit) {
            tvCodecName.text = item.codecName
            if (item.qualityLabel != null) {
                tvSubLabel.visibility = View.VISIBLE
                tvSubLabel.text = item.qualityLabel
            } else {
                tvSubLabel.visibility = View.GONE
            }
            ivSelected.visibility = if (item.isSelected) View.VISIBLE else View.INVISIBLE
            itemView.isSelected = item.isSelected
            itemView.stateDescription = if (item.isSelected) itemView.context.getString(R.string.selected) else null
            itemView.setOnClickListener { onClick(item) }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<BluetoothCodecInfo>() {
        // codecName is part of the identity because headers and LDAC presets share a codec type.
        override fun areItemsTheSame(a: BluetoothCodecInfo, b: BluetoothCodecInfo) =
            a.codecName == b.codecName && a.codecType == b.codecType &&
                a.ldacQuality == b.ldacQuality && a.isHeader == b.isHeader

        override fun areContentsTheSame(a: BluetoothCodecInfo, b: BluetoothCodecInfo) = a == b
    }
}
