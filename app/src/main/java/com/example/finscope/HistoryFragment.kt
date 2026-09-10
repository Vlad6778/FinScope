package com.example.finscope

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.util.Pair
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
import com.google.android.material.datepicker.MaterialDatePicker
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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

    // Инициализируем текущим моментом, чтобы избежать null
    private var selectedPeriodStart: Date = Date()
    private var selectedPeriodEnd: Date = Date()

    private var currentFullTransactionListFromDb: List<Transaction> = emptyList()
    private var hasAttemptedToLoadData = false

    private val dateFormatter = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

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
            // При старте устанавливаем "Месяц"
            setPeriodMonth()
        } else {
            // Восстанавливаем лейбл при повороте экрана
            updateDateRangeLabel()
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
        // 1. Логика кнопок быстрого выбора (День/Неделя/...)
        binding.toggleButtonGroupPeriod.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.button_filter_day -> setPeriodDay()
                    R.id.button_filter_week -> setPeriodWeek()
                    R.id.button_filter_month -> setPeriodMonth()
                    R.id.button_filter_year -> setPeriodYear()
                }
            }
        }

        // 2. Логика ручного выбора даты (Календарь)
        binding.btnDateRangePicker.setOnClickListener {
            showDateRangePicker()
        }

        // 3. Спиннеры Типа и Категории
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

    // --- ЛОГИКА КАЛЕНДАРЯ ---
    private fun showDateRangePicker() {
        val dateRangePicker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("Оберіть період історії")
            .setSelection(Pair(selectedPeriodStart.time, selectedPeriodEnd.time))
            .build()

        dateRangePicker.addOnPositiveButtonClickListener { selection ->
            // Устанавливаем новые даты
            selectedPeriodStart = Date(selection.first)
            selectedPeriodEnd = Date(selection.second)

            // Снимаем выделение с кнопок "День/Месяц...", так как выбран свой период
            binding.toggleButtonGroupPeriod.clearChecked()

            // Обновляем UI
            updateDateRangeLabel()
            applyFiltersToListing()
        }
        dateRangePicker.show(parentFragmentManager, "history_date_picker")
    }

    private fun updateDateRangeLabel() {
        val startStr = dateFormatter.format(selectedPeriodStart)
        val endStr = dateFormatter.format(selectedPeriodEnd)
        // Если даты совпадают (один день), показываем одну дату
        if (startStr == endStr) {
            binding.tvCurrentDateRange.text = startStr
        } else {
            binding.tvCurrentDateRange.text = "$startStr - $endStr"
        }
    }

    // --- ФУНКЦИИ БЫСТРЫХ ПЕРИОДОВ ---
    private fun setPeriodDay() {
        val calendar = Calendar.getInstance()
        clearTime(calendar)
        selectedPeriodStart = calendar.time

        // Конец дня
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        calendar.add(Calendar.MILLISECOND, -1)
        selectedPeriodEnd = calendar.time

        updateDateRangeLabel()
        applyFiltersToListing()
    }

    private fun setPeriodWeek() {
        val calendar = Calendar.getInstance()
        clearTime(calendar)
        calendar.firstDayOfWeek = Calendar.MONDAY
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        selectedPeriodStart = calendar.time

        calendar.add(Calendar.DAY_OF_WEEK, 6)
        // Конец дня воскресенья
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        selectedPeriodEnd = calendar.time

        updateDateRangeLabel()
        applyFiltersToListing()
    }

    private fun setPeriodMonth() {
        val calendar = Calendar.getInstance()
        clearTime(calendar)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        selectedPeriodStart = calendar.time

        calendar.add(Calendar.MONTH, 1)
        calendar.add(Calendar.MILLISECOND, -1)
        selectedPeriodEnd = calendar.time

        updateDateRangeLabel()
        applyFiltersToListing()
    }

    private fun setPeriodYear() {
        val calendar = Calendar.getInstance()
        clearTime(calendar)
        calendar.set(Calendar.DAY_OF_YEAR, 1)
        selectedPeriodStart = calendar.time

        calendar.add(Calendar.YEAR, 1)
        calendar.add(Calendar.MILLISECOND, -1)
        selectedPeriodEnd = calendar.time

        updateDateRangeLabel()
        applyFiltersToListing()
    }

    private fun clearTime(calendar: Calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
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

        // Фильтрация по дате
        filteredList = filteredList.filter {
            val txDate = it.date.time
            txDate >= selectedPeriodStart.time && txDate <= selectedPeriodEnd.time
        }

        selectedTypeFilter?.let { type ->
            filteredList = filteredList.filter { it.type == type }
        }
        selectedCategoryFilter?.let { category ->
            filteredList = filteredList.filter { it.category_id == category.category_id }
        }

        transactionAdapter.submitList(filteredList.sortedByDescending { it.date })

        // Логика пустых состояний
        val isEmptyResult = filteredList.isEmpty()
        binding.textViewEmptyHistory.visibility = if (isEmptyResult) View.VISIBLE else View.GONE

        if (hasAttemptedToLoadData && isEmptyResult && currentFullTransactionListFromDb.isNotEmpty()) {
            // Можно показывать Toast, но лучше просто textViewEmptyHistory
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}