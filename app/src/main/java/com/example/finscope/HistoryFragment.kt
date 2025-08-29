package com.example.finscope

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.finscope.databinding.FragmentHistoryBinding
import com.example.finscope.model.Category
import com.example.finscope.model.Transaction
import com.example.finscope.viewmodel.FinanceViewModel
import com.example.finscope.viewmodel.FinanceViewModelFactory
import com.example.finscope.viewmodel.TransactionTypes
import java.util.Calendar
import java.util.Date
import android.widget.ImageButton

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private val financeViewModel: FinanceViewModel by viewModels {
        FinanceViewModelFactory(requireActivity().application)
    }
    private lateinit var transactionAdapter: TransactionAdapter

    private var allRawCategoriesList: List<Category> = emptyList()
    private var selectedCategoryFilter: Category? = null
    private var selectedTypeFilter: String? = null
    private var selectedPeriodStart: Date? = null
    private var selectedPeriodEnd: Date? = null

    private var currentFullTransactionListFromDb: List<Transaction> = emptyList()
    private var hasAttemptedToLoadData = false


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupFilterControls()
        observeViewModel()

        val addButton = binding.toolbarHistory.findViewById<ImageButton>(R.id.button_add_transaction_history)
        addButton.setOnClickListener {
            findNavController().navigate(R.id.action_history_to_addTransaction)
        }

        if (savedInstanceState == null) {
            binding.toggleButtonGroupPeriod.check(R.id.button_filter_month)
            applyPeriodFilter(R.id.button_filter_month)
        } else {
            applyFiltersToListing()
        }
    }

    private fun setupRecyclerView() {
        transactionAdapter = TransactionAdapter(
            financeViewModel,
            viewLifecycleOwner,
            showActionButtons = true,
            onEditClick = { transaction ->
                val action = HistoryFragmentDirections.actionHistoryFragmentToAddTransactionFragmentForEdit(transaction.transaction_id)
                findNavController().navigate(action)
            },
            onDeleteClick = { transaction ->
                showDeleteConfirmationDialog(transaction)
            }
        )
        binding.recyclerViewTransactionsHistory.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = transactionAdapter
        }
    }

    private fun showDeleteConfirmationDialog(transaction: Transaction) {
        AlertDialog.Builder(requireContext())
            .setTitle("Видалити транзакцію")
            .setMessage("Ви впевнені, що хочете видалити цю транзакцію?")
            .setPositiveButton("Так") { _, _ ->
                financeViewModel.deleteTransactionAndUpdateBalance(transaction)
                Toast.makeText(context, "Транзакцію видалено", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Ні", null)
            .show()
    }

    private fun setupFilterControls() {
        binding.toggleButtonGroupPeriod.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                applyPeriodFilter(checkedId)
            }
        }

        val typeOptions = listOf("Всі типи", TransactionTypes.INCOME, TransactionTypes.EXPENSE)
        val typeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, typeOptions)
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerFilterType.adapter = typeAdapter
        binding.spinnerFilterType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedTypeFilter = if (position == 0) null else typeOptions[position]
                applyFiltersToListing()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        financeViewModel.allCategories.observe(viewLifecycleOwner) { categories ->
            allRawCategoriesList = categories
            val categoryNames = mutableListOf("Всі категорії")
            categoryNames.addAll(categories.map { it.name })
            val categoryAdapterSpinner = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, categoryNames)
            categoryAdapterSpinner.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerFilterCategory.adapter = categoryAdapterSpinner

        }

        binding.spinnerFilterCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedCategoryFilter = if (position == 0) null else allRawCategoriesList.getOrNull(position - 1)
                applyFiltersToListing()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun applyPeriodFilter(checkedId: Int) {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0); calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0)

        when (checkedId) {
            R.id.button_filter_day -> {
                selectedPeriodStart = calendar.time
                calendar.add(Calendar.DAY_OF_YEAR, 1); calendar.add(Calendar.MILLISECOND, -1)
                selectedPeriodEnd = calendar.time
            }
            R.id.button_filter_week -> {
                calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                selectedPeriodStart = calendar.time
                calendar.add(Calendar.WEEK_OF_YEAR, 1); calendar.add(Calendar.MILLISECOND, -1)
                selectedPeriodEnd = calendar.time
            }
            R.id.button_filter_month -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                selectedPeriodStart = calendar.time
                calendar.add(Calendar.MONTH, 1); calendar.add(Calendar.MILLISECOND, -1)
                selectedPeriodEnd = calendar.time
            }
            R.id.button_filter_year -> {
                calendar.set(Calendar.DAY_OF_YEAR, 1)
                selectedPeriodStart = calendar.time
                calendar.add(Calendar.YEAR, 1); calendar.add(Calendar.MILLISECOND, -1)
                selectedPeriodEnd = calendar.time
            }
        }
        applyFiltersToListing()
    }

    private fun observeViewModel() {
        financeViewModel.allTransactionsForHistory.observe(viewLifecycleOwner) { transactions ->
            hasAttemptedToLoadData = true
            currentFullTransactionListFromDb = transactions ?: emptyList()
            applyFiltersToListing()
        }
    }

    private fun applyFiltersToListing() {
        if (!hasAttemptedToLoadData && currentFullTransactionListFromDb.isEmpty()) {
            transactionAdapter.submitList(emptyList())
            return
        }

        var filteredList = currentFullTransactionListFromDb

        selectedPeriodStart?.let { start ->
            selectedPeriodEnd?.let { end ->
                filteredList = filteredList.filter {
                    val transactionDateCal = Calendar.getInstance().apply { time = it.date; clearTime() }
                    val startDateCal = Calendar.getInstance().apply { time = start; clearTime() }
                    val endDateCal = Calendar.getInstance().apply { time = end; clearTime() }
                    !transactionDateCal.before(startDateCal) && !transactionDateCal.after(endDateCal)
                }
            }
        }
        selectedTypeFilter?.let { type ->
            filteredList = filteredList.filter { it.type == type }
        }
        selectedCategoryFilter?.let { category ->
            filteredList = filteredList.filter { it.category_id == category.category_id }
        }

        transactionAdapter.submitList(filteredList.sortedByDescending { it.date })


        if (hasAttemptedToLoadData && filteredList.isEmpty()) {
            if (selectedCategoryFilter != null || selectedTypeFilter != null || !isDefaultPeriodMonthInitiallyChecked()) {
                Toast.makeText(context, "Немає транзакцій за обраними фільтрами", Toast.LENGTH_SHORT).show()
            }

            else if (currentFullTransactionListFromDb.isEmpty()){
                Toast.makeText(context, "У вас ще немає транзакцій", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Допоміжна функція для очищення часу в Calendar
    private fun Calendar.clearTime() {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private fun isDefaultPeriodMonthInitiallyChecked(): Boolean {
        return binding.toggleButtonGroupPeriod.checkedButtonId == R.id.button_filter_month &&
                selectedCategoryFilter == null && selectedTypeFilter == null
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}