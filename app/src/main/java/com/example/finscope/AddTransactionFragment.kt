package com.example.finscope

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.RadioButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer // Додано для Observer
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs // Для Safe Args
import com.example.finscope.databinding.FragmentAddTransactionBinding
import com.example.finscope.model.Category
import com.example.finscope.model.Transaction
import com.example.finscope.viewmodel.FinanceViewModel
import com.example.finscope.viewmodel.FinanceViewModelFactory
import com.example.finscope.viewmodel.TransactionTypes
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.*

class AddTransactionFragment : Fragment() {

    private var _binding: FragmentAddTransactionBinding? = null
    private val binding get() = _binding!!

    private val DATE_FORMAT = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    private var selectedDate: Date = Date()

    private val financeViewModel: FinanceViewModel by viewModels {
        FinanceViewModelFactory(requireActivity().application)
    }

    private val args: AddTransactionFragmentArgs by navArgs()

    private var categoriesForSpinner: List<Category> = emptyList()
    private var currentTransactionToEdit: Transaction? = null
    private var isEditMode = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddTransactionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        isEditMode = args.transactionIdToEdit != -1

        if (isEditMode) {
            binding.title.text = "Редагування транзакції"
            binding.saveButton.text = "Зберегти зміни"
            loadTransactionData(args.transactionIdToEdit)
        } else {
            binding.title.text = "Додати транзакцію"
            binding.saveButton.text = "Зберегти транзакцію"
            binding.dateInput.setText(DATE_FORMAT.format(selectedDate))
        }

        setupDatePicker()
        setupRadioGroupListener()
        if (!isEditMode) {
            updateCategoriesBasedOnType()
        }


        binding.saveButton.setOnClickListener {
            saveOrUpdateTransaction()
        }

        binding.backIcon.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun loadTransactionData(transactionId: Int) {
        financeViewModel.getTransactionById(transactionId).observe(viewLifecycleOwner, Observer { transaction ->
            transaction?.let {
                currentTransactionToEdit = it
                binding.amountInput.setText(it.amount.toPlainString())
                selectedDate = it.date
                binding.dateInput.setText(DATE_FORMAT.format(it.date))
                binding.descriptionInput.setText(it.description)

                if (it.type == TransactionTypes.INCOME) {
                    binding.incomeRadioButton.isChecked = true
                } else {
                    binding.expenseRadioButton.isChecked = true
                }

                triggerCategoryPopulationAndSelection(it.type, it.category_id)
            }
        })
    }

    private fun triggerCategoryPopulationAndSelection(transactionType: String, categoryIdToSelect: Int?) {
        val liveDataCategories = financeViewModel.getCategoriesByType(transactionType)
        liveDataCategories.observe(viewLifecycleOwner, object : Observer<List<Category>> {
            override fun onChanged(categories: List<Category>) {

                categoriesForSpinner = categories
                val categoryNames = categories.map { it.name }
                val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, categoryNames)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                binding.categorySpinner.adapter = adapter

                categoryIdToSelect?.let { catId ->
                    val categoryToSelect = categoriesForSpinner.find { it.category_id == catId }
                    categoryToSelect?.let {
                        val position = categoriesForSpinner.indexOf(it)
                        if (position >= 0) {
                            binding.categorySpinner.setSelection(position)
                        }
                    }
                }
            }
        })
    }


    private fun setupDatePicker() {
        binding.dateInput.setOnClickListener {
            val calendar = Calendar.getInstance()
            calendar.time = selectedDate

            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH)
            val day = calendar.get(Calendar.DAY_OF_MONTH)

            DatePickerDialog(
                requireContext(),
                { _, selectedYear, selectedMonth, selectedDay ->
                    val newSelectedDate = Calendar.getInstance()
                    newSelectedDate.set(selectedYear, selectedMonth, selectedDay)
                    selectedDate = newSelectedDate.time
                    binding.dateInput.setText(DATE_FORMAT.format(selectedDate))
                },
                year, month, day
            ).show()
        }
    }

    private fun setupRadioGroupListener() {
        binding.typeRadioGroup.setOnCheckedChangeListener { _, _ ->
            updateCategoriesBasedOnType(null)
        }
    }

    private fun updateCategoriesBasedOnType(categoryIdToSelectAfterLoad: Int? = null) {
        val selectedRadioButtonId = binding.typeRadioGroup.checkedRadioButtonId
        val transactionType = when (selectedRadioButtonId) {
            binding.incomeRadioButton.id -> TransactionTypes.INCOME
            binding.expenseRadioButton.id -> TransactionTypes.EXPENSE
            else -> {
                if (isEditMode && currentTransactionToEdit != null) {
                    currentTransactionToEdit!!.type
                } else {
                    binding.categorySpinner.adapter = null
                    categoriesForSpinner = emptyList()
                    return
                }
            }
        }

        financeViewModel.getCategoriesByType(transactionType).observe(viewLifecycleOwner, Observer { categories ->
            categoriesForSpinner = categories
            val categoryNames = categories.map { it.name }
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, categoryNames)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.categorySpinner.adapter = adapter

            categoryIdToSelectAfterLoad?.let { catId ->
                val categoryToSelect = categoriesForSpinner.find { it.category_id == catId }
                categoryToSelect?.let {
                    val position = categoriesForSpinner.indexOf(it)
                    if (position >= 0) {
                        binding.categorySpinner.setSelection(position)
                    }
                }
            }
        })
    }


    private fun saveOrUpdateTransaction() {
        val amountText = binding.amountInput.text.toString()
        val description = binding.descriptionInput.text.toString().takeIf { it.isNotBlank() }

        if (amountText.isBlank()) {
            Toast.makeText(context, "Будь ласка, введіть суму", Toast.LENGTH_SHORT).show()
            return
        }
        val amount = try {
            BigDecimal(amountText)
        } catch (e: NumberFormatException) {
            Toast.makeText(context, "Некоректна сума", Toast.LENGTH_SHORT).show()
            return
        }

        if (amount <= BigDecimal.ZERO) {
            Toast.makeText(context, "Сума повинна бути більше нуля", Toast.LENGTH_SHORT).show()
            return
        }

        val transactionType = when (binding.typeRadioGroup.checkedRadioButtonId) {
            binding.incomeRadioButton.id -> TransactionTypes.INCOME
            binding.expenseRadioButton.id -> TransactionTypes.EXPENSE
            else -> {
                Toast.makeText(context, "Будь ласка, оберіть тип транзакції", Toast.LENGTH_SHORT).show()
                return
            }
        }

        val selectedCategoryPosition = binding.categorySpinner.selectedItemPosition
        // Перевірка, чи адаптер не порожній і позиція валідна
        val selectedCategory: Category? = if (binding.categorySpinner.adapter != null &&
            binding.categorySpinner.adapter.count > 0 &&
            selectedCategoryPosition >= 0 &&
            selectedCategoryPosition < categoriesForSpinner.size) {
            categoriesForSpinner.getOrNull(selectedCategoryPosition)
        } else {
            null
        }


        if (selectedCategory == null) {
            Toast.makeText(context, "Будь ласка, оберіть категорію", Toast.LENGTH_SHORT).show()
            return
        }

        if (isEditMode && currentTransactionToEdit != null) {
            val updatedTransaction = currentTransactionToEdit!!.copy(
                amount = amount,
                type = transactionType,
                category_id = selectedCategory.category_id,
                date = selectedDate,
                description = description
            )
            financeViewModel.updateTransactionAndAdjustBalance(currentTransactionToEdit!!, updatedTransaction)
            Toast.makeText(context, "Транзакцію оновлено", Toast.LENGTH_SHORT).show()
        } else {
            val newTransaction = Transaction(
                user_id = 1,
                category_id = selectedCategory.category_id,
                amount = amount,
                type = transactionType,
                description = description,
                date = selectedDate
            )
            financeViewModel.addTransactionAndUpdateBalance(newTransaction)
            Toast.makeText(context, "Транзакцію збережено", Toast.LENGTH_SHORT).show()
        }
        findNavController().popBackStack()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        currentTransactionToEdit = null
    }
}