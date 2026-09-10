package com.example.finscope

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.finscope.databinding.FragmentHomeBinding
import com.example.finscope.viewmodel.FinanceViewModel
import com.example.finscope.viewmodel.FinanceViewModelFactory
import java.text.NumberFormat
import java.util.Locale

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val financeViewModel: FinanceViewModel by viewModels {
        FinanceViewModelFactory(requireActivity().application)
    }
    private lateinit var transactionAdapter: TransactionAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        setupRecyclerView()

        binding.addTransactionButton.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_addTransaction)
        }

        binding.settingsIcon.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_settings)
        }

        observeViewModel()

        return root
    }

    private fun setupRecyclerView() {
        transactionAdapter = TransactionAdapter(
            financeViewModel,
            viewLifecycleOwner,
            showActionButtons = false,
            onEditClick = null,
            onDeleteClick = null
        )
        binding.transactionRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = transactionAdapter
        }
    }

    private fun observeViewModel() {
        financeViewModel.currentUser.observe(viewLifecycleOwner) { user ->
            user?.let {
                val format: NumberFormat = NumberFormat.getCurrencyInstance(Locale("uk", "UA"))
                binding.balanceText.text = "Баланс: ${format.format(it.balance)}"
            } ?: run {
                binding.balanceText.text = "Баланс: 0.00 грн"
            }
        }

        financeViewModel.recentTransactions.observe(viewLifecycleOwner) { transactions ->
            transactionAdapter.submitList(transactions)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}