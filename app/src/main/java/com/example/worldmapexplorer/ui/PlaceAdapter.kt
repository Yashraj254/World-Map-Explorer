package com.example.worldmapexplorer.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.worldmapexplorer.data.network.dto.Place
import com.example.worldmapexplorer.databinding.ItemLoadingBinding
import com.example.worldmapexplorer.databinding.ItemSearchResultBinding

private const val VIEW_TYPE_ITEM = 0
private const val VIEW_TYPE_LOADING = 1


class PlaceAdapter(
    private val onItemClick: (Place) -> Unit
) : ListAdapter<Place, RecyclerView.ViewHolder>(diffCallback) {

    inner class SearchResultsViewHolder(private val binding: ItemSearchResultBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Place) {
            binding.placeType.text = item.type
            binding.placeName.text = item.displayName
            binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    inner class LoadingViewHolder(binding: ItemLoadingBinding) :
        RecyclerView.ViewHolder(binding.root)


    private var showLoading = false

    override fun getItemViewType(position: Int): Int {
        return if (position == currentList.size) VIEW_TYPE_LOADING else VIEW_TYPE_ITEM
    }

    override fun getItemCount(): Int {
        return currentList.size + if (showLoading) 1 else 0
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_ITEM) {
            val binding =
                ItemSearchResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            SearchResultsViewHolder(binding)
        } else {
            val binding =
                ItemLoadingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            LoadingViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is SearchResultsViewHolder)
            holder.bind(getItem(position))
    }

    fun showLoadingIndicator(show: Boolean) {
        if (showLoading == show) return
        showLoading = show
        notifyDataSetChanged()
    }

    companion object {
        private val diffCallback = object : DiffUtil.ItemCallback<Place>() {
            override fun areItemsTheSame(oldItem: Place, newItem: Place): Boolean {
                return oldItem.displayName == newItem.displayName
            }

            override fun areContentsTheSame(oldItem: Place, newItem: Place): Boolean {
                return oldItem == newItem
            }
        }
    }
}