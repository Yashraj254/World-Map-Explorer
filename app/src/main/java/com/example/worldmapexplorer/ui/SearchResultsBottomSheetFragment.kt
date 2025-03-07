package com.example.worldmapexplorer.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.worldmapexplorer.data.network.dto.PlaceInfo
import com.example.worldmapexplorer.databinding.FragmentSearchResultsBottomSheetBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SearchResultsBottomSheetFragment(private var searchRoute: Boolean) :
    BottomSheetDialogFragment() {

    private var _binding: FragmentSearchResultsBottomSheetBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: SearchResultsAdapter
    private val viewModel: MainViewModel by activityViewModels()
    private var isLoading = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentSearchResultsBottomSheetBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()

        viewLifecycleOwner.lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.places.collect {
                        Log.d("SearchResults", "onViewCreated: $it")
                        adapter.submitList(it)
                    }
                }
                launch {
                    viewModel.isLoading.collect {
                        isLoading = it
                        adapter.showLoadingIndicator(it)
                    }
                }
            }
        }

        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                val bottomSheet =
                    dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                bottomSheet?.let { sheet ->
                    val behavior = BottomSheetBehavior.from(sheet)

                    if (dy > 0) { // dy < 0 means scrolling up
                        behavior.state = BottomSheetBehavior.STATE_EXPANDED
                    }
                }

                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val lastVisibleItem = layoutManager.findLastCompletelyVisibleItemPosition()
                val totalItemCount = layoutManager.itemCount

                if (!isLoading && lastVisibleItem == totalItemCount - 1) {
                    viewModel.fetchMorePlaces() // Fetch more data only when the last item is fully visible
                }
            }
        })


    }

    private fun setupRecyclerView() {
        adapter = SearchResultsAdapter() { selectedItem ->
            val place = PlaceInfo.Builder()
            place.setName(selectedItem.name)
            place.setType(selectedItem.type)
            place.setAddress(selectedItem.displayName)

            if (searchRoute) {
//                viewModel.getRoute(selectedItem.osmId, place)
            } else {
                viewModel.getWayGeometry(selectedItem.osmId, place)
            }
            dismiss() // Close the bottom sheet on item click
            // Handle item click (e.g., move map to selected location)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        binding.recyclerView.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                binding.recyclerView.parent.requestDisallowInterceptTouchEvent(true)
                return false
            }

            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
                TODO("Not yet implemented")
            }

            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
                TODO("Not yet implemented")
            }
        })
    }

}