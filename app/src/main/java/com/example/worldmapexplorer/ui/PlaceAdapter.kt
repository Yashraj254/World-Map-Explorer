package com.example.worldmapexplorer.ui

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.worldmapexplorer.data.network.dto.Place
import com.example.worldmapexplorer.databinding.ItemLoadingBinding
import com.example.worldmapexplorer.databinding.ItemSearchResultBinding
import com.example.worldmapexplorer.databinding.ItemShowMoreBinding

class PlaceAdapter(
    private val onItemClick: (Place) -> Unit,
    private val onShowMoreClick: () -> Unit // Callback for "Show More" button
) : ListAdapter<Place, RecyclerView.ViewHolder>(diffCallback) {

    companion object {
        private const val VIEW_TYPE_ITEM = 0
        private const val VIEW_TYPE_LOADING = 1
        private const val VIEW_TYPE_SHOW_MORE = 2 // New view type for "Show More"

        private val diffCallback = object : DiffUtil.ItemCallback<Place>() {
            override fun areItemsTheSame(oldItem: Place, newItem: Place): Boolean {
                return oldItem.displayName == newItem.displayName
            }

            override fun areContentsTheSame(oldItem: Place, newItem: Place): Boolean {
                return oldItem == newItem
            }
        }
    }

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

    inner class ShowMoreViewHolder(private val binding: ItemShowMoreBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind() {
            binding.tvShowMore.paintFlags = binding.tvShowMore.paintFlags or Paint.UNDERLINE_TEXT_FLAG

            binding.tvShowMore.setOnClickListener { onShowMoreClick() }
        }
    }

    private var showLoading = false
    private var showShowMore = false

    override fun getItemViewType(position: Int): Int {
        return when {
            position == currentList.size && showLoading -> VIEW_TYPE_LOADING
            position == currentList.size && showShowMore -> VIEW_TYPE_SHOW_MORE
            else -> VIEW_TYPE_ITEM
        }
    }

    override fun getItemCount(): Int {
        return currentList.size + if (showLoading || showShowMore) 1 else 0
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_ITEM -> {
                val binding = ItemSearchResultBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                SearchResultsViewHolder(binding)
            }
            VIEW_TYPE_LOADING -> {
                val binding = ItemLoadingBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                LoadingViewHolder(binding)
            }
            VIEW_TYPE_SHOW_MORE -> {
                val binding = ItemShowMoreBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                ShowMoreViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Unknown view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is SearchResultsViewHolder -> holder.bind(getItem(position))
            is ShowMoreViewHolder -> holder.bind()
        }
    }

    fun showLoadingIndicator(show: Boolean) {
        if (showLoading == show) return
        showLoading = show
        showShowMore = false
        notifyDataSetChanged()
    }

    fun showShowMoreButton(show: Boolean) {
        if (showShowMore == show) return
        showShowMore = show
        showLoading = false
        notifyDataSetChanged()
    }
}
