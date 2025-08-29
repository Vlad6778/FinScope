package com.example.finscope

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.finscope.CurrencyAdapter
import com.example.finscope.databinding.FragmentCurrencyBinding
import com.example.finscope.viewmodel.FinanceViewModel
import com.example.finscope.viewmodel.FinanceViewModelFactory

class CurrencyFragment : Fragment() {

    private var _binding: FragmentCurrencyBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FinanceViewModel by viewModels {
        FinanceViewModelFactory(requireActivity().application)
    }
    private lateinit var currencyAdapter: CurrencyAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCurrencyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeViewModel()

        // Завантаження даних при створенні фрагменту
        binding.progressBarCurrency.visibility = View.VISIBLE
        viewModel.fetchExchangeRates()
    }

    private fun setupRecyclerView() {
        currencyAdapter = CurrencyAdapter(emptyList())
        binding.recyclerViewCurrency.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = currencyAdapter
        }
    }

    private fun observeViewModel() {
        viewModel.exchangeRates.observe(viewLifecycleOwner) { rates ->
            binding.progressBarCurrency.visibility = View.GONE
            // Фільтри, щоб показати тільки основні валюти
            val filteredRates = rates.filter {
                    it.currencyCode == "USD" ||
                    it.currencyCode == "EUR" ||
                    it.currencyCode == "TRY" ||
                    it.currencyCode == "GBP" ||
                    it.currencyCode == "PLN" ||
                    it.currencyCode == "CHF" }
            currencyAdapter.updateData(filteredRates)
        }

        viewModel.networkError.observe(viewLifecycleOwner) { errorMessage ->
            errorMessage?.let {
                binding.progressBarCurrency.visibility = View.GONE
                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                viewModel.onNetworkErrorShown() 
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}