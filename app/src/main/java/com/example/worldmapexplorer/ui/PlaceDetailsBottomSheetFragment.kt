package com.example.worldmapexplorer.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.worldmapexplorer.databinding.FragmentPlaceDetailsBottomSheetBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PlaceDetailsBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentPlaceDetailsBottomSheetBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()
    private var isLoading = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentPlaceDetailsBottomSheetBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let {
                val behavior = BottomSheetBehavior.from(it)
                behavior.peekHeight = 200  // Set minimum height when collapsed
                behavior.isHideable = false // Prevent full dismissal
                behavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED // Start collapsed

                // Prevent dismiss when tapping outside
                dialog.setCancelable(false)
                dialog.setCanceledOnTouchOutside(false)

                bottomSheet.setBackgroundResource(android.R.color.transparent)

            }
        }
        dialog.window?.setDimAmount(0f)

        return dialog
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.placeInfo.collect {
                    it?.let {
                        binding.apply {
                            tvArea.text = it.area.toString()
                            tvName.text = it.name
                            tvType.text = it.type
                            tvAddress.text = it.address
                        }
                    }
                }
            }
        }
    }
}