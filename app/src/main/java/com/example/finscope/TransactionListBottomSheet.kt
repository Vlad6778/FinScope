package com.example.finscope

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import com.example.finscope.databinding.BottomSheetTransactionListBinding
import com.example.finscope.viewmodel.FinanceViewModel
import com.example.finscope.viewmodel.FinanceViewModelFactory
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.util.Date

class TransactionListBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetTransactionListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FinanceViewModel by viewModels {
        FinanceViewModelFactory(requireActivity().application)
    }

    private lateinit var transactionAdapter: TransactionAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetTransactionListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val categoryId = requireArguments().getInt(ARG_CATEGORY_ID)
        val categoryName = requireArguments().getString(ARG_CATEGORY_NAME)
        val totalAmount = requireArguments().getFloat(ARG_TOTAL_AMOUNT)
        val startDate = Date(requireArguments().getLong(ARG_START_DATE))
        val endDate = Date(requireArguments().getLong(ARG_END_DATE))

        binding.tvSheetTitle.text = "$categoryName: ${totalAmount} ₴"

        setupRecyclerView()

        viewModel.getTransactionsByCategoryAndPeriod(categoryId, startDate, endDate)
            .observe(viewLifecycleOwner) { transactions ->
                transactionAdapter.submitList(transactions)
            }
    }

    private fun setupRecyclerView() {
        transactionAdapter = TransactionAdapter(viewModel, viewLifecycleOwner, false, null, null)
        binding.rvTransactions.adapter = transactionAdapter
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_CATEGORY_ID = "category_id"
        private const val ARG_CATEGORY_NAME = "category_name"
        private const val ARG_TOTAL_AMOUNT = "total_amount"
        private const val ARG_START_DATE = "start_date"
        private const val ARG_END_DATE = "end_date"

        fun newInstance(
            categoryId: Int,
            categoryName: String,
            totalAmount: Float,
            startDate: Date,
            endDate: Date
        ): TransactionListBottomSheet {
            return TransactionListBottomSheet().apply {
                arguments = Bundle().apply {
                    putInt(ARG_CATEGORY_ID, categoryId)
                    putString(ARG_CATEGORY_NAME, categoryName)
                    putFloat(ARG_TOTAL_AMOUNT, totalAmount)
                    putLong(ARG_START_DATE, startDate.time)
                    putLong(ARG_END_DATE, endDate.time)
                }
            }
        }
    }
}