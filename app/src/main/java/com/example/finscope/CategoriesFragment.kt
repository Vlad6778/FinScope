package com.example.finscope

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.finscope.databinding.FragmentCategoriesBinding
import com.example.finscope.model.Category
import com.example.finscope.viewmodel.FinanceViewModel
import com.example.finscope.viewmodel.FinanceViewModelFactory
import com.example.finscope.viewmodel.TransactionTypes

class CategoriesFragment : Fragment() {

    private var _binding: FragmentCategoriesBinding? = null
    private val binding get() = _binding!!

    private val financeViewModel: FinanceViewModel by viewModels {
        FinanceViewModelFactory(requireActivity().application)
    }
    private lateinit var categoryAdapter: CategoryAdapter

    private var currentCategoriesLiveData: LiveData<List<Category>>? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCategoriesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupFilterButtons()

        binding.buttonAddNewCategory.setOnClickListener {
            showAddEditCategoryDialog(null)
        }

        // Початкове завантаження всіх категорій
        loadAndObserveCategories(financeViewModel.allCategories)
        binding.toggleButtonGroupCategoryFilter.check(R.id.button_filter_all_cat)
    }

    private fun setupRecyclerView() {
        categoryAdapter = CategoryAdapter(
            requireContext(),
            onEditClick = { category ->
                showAddEditCategoryDialog(category)
            },
            onDeleteClick = { category ->
                showDeleteCategoryConfirmationDialog(category)
            }
        )
        binding.recyclerViewCategories.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = categoryAdapter
        }
    }

    private fun setupFilterButtons() {
        binding.toggleButtonGroupCategoryFilter.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) {
                currentCategoriesLiveData?.removeObservers(viewLifecycleOwner)
                when (checkedId) {
                    R.id.button_filter_all_cat -> {
                        loadAndObserveCategories(financeViewModel.allCategories)
                    }
                    R.id.button_filter_income_cat -> {
                        loadAndObserveCategories(financeViewModel.getCategoriesByType(TransactionTypes.INCOME))
                    }
                    R.id.button_filter_expense_cat -> {
                        loadAndObserveCategories(financeViewModel.getCategoriesByType(TransactionTypes.EXPENSE))
                    }
                }
            }
        }
    }

    private fun loadAndObserveCategories(categoriesLiveData: LiveData<List<Category>>) {
        currentCategoriesLiveData = categoriesLiveData
        currentCategoriesLiveData?.observe(viewLifecycleOwner) { categories ->
            categoryAdapter.submitList(categories)
            if (categories.isNullOrEmpty()) {
                binding.tvNoCategoriesMessage.visibility = View.VISIBLE
                binding.recyclerViewCategories.visibility = View.GONE
            } else {
                binding.tvNoCategoriesMessage.visibility = View.GONE
                binding.recyclerViewCategories.visibility = View.VISIBLE
            }
        }
    }

    private fun isCategoryNameUnique(name: String, categoryToEdit: Category?): Boolean {
        val currentCategories = currentCategoriesLiveData?.value ?: return true
        return currentCategories.none { category ->
            category.name.equals(name, ignoreCase = true) &&
                    (categoryToEdit == null || category.category_id != categoryToEdit.category_id)
        }
    }

    private fun showAddEditCategoryDialog(categoryToEdit: Category?) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_edit_category, null)
        val etCategoryName = dialogView.findViewById<EditText>(R.id.et_category_name)
        val rgCategoryType = dialogView.findViewById<RadioGroup>(R.id.rg_category_type_dialog)
        val rbIncome = dialogView.findViewById<android.widget.RadioButton>(R.id.rb_income_dialog)
        val rbExpense = dialogView.findViewById<android.widget.RadioButton>(R.id.rb_expense_dialog)

        val dialogTitle = if (categoryToEdit == null) "Додати нову категорію" else "Редагувати категорію"

        categoryToEdit?.let {
            etCategoryName.setText(it.name)
            if (it.type == TransactionTypes.INCOME) {
                rbIncome.isChecked = true
            } else {
                rbExpense.isChecked = true
            }
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(dialogTitle)
            .setView(dialogView)
            .setPositiveButton(if (categoryToEdit == null) "Додати" else "Зберегти") { _, _ ->
            }
            .setNegativeButton("Скасувати", null)
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = etCategoryName.text.toString().trim()
            val selectedTypeId = rgCategoryType.checkedRadioButtonId
            val type = if (selectedTypeId == R.id.rb_income_dialog) TransactionTypes.INCOME else TransactionTypes.EXPENSE

            when {
                name.isBlank() -> {
                    Toast.makeText(requireContext(), "Назва категорії не може бути порожньою", Toast.LENGTH_SHORT).show()
                }
                selectedTypeId == -1 -> {
                    Toast.makeText(requireContext(), "Будь ласка, оберіть тип категорії", Toast.LENGTH_SHORT).show()
                }
                !isCategoryNameUnique(name, categoryToEdit) -> {
                    Toast.makeText(requireContext(), "Категорія з назвою '$name' вже існує", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    if (categoryToEdit == null) {
                        financeViewModel.addCustomCategory(name, type)
                        Toast.makeText(requireContext(), "Категорію '$name' додано", Toast.LENGTH_SHORT).show()
                    } else {
                        val updatedCategory = categoryToEdit.copy(name = name, type = type)
                        financeViewModel.updateCategory(updatedCategory)
                        Toast.makeText(requireContext(), "Категорію '$name' оновлено", Toast.LENGTH_SHORT).show()
                    }
                    dialog.dismiss()
                }
            }
        }
    }

    private fun showDeleteCategoryConfirmationDialog(category: Category) {
        AlertDialog.Builder(requireContext())
            .setTitle("Видалити категорію")
            .setMessage("Ви впевнені, що хочете видалити категорію '${category.name}'?\nЦе може вплинути на існуючі транзакції.")
            .setPositiveButton("Так") { _, _ ->
                financeViewModel.deleteCategory(category)
                Toast.makeText(context, "Категорію '${category.name}' видалено", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Ні", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        currentCategoriesLiveData?.removeObservers(viewLifecycleOwner)
        _binding = null
    }
}