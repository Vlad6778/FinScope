package com.example.finscope

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.finscope.databinding.FragmentSettingsBinding
import com.example.finscope.viewmodel.FinanceViewModel
import com.example.finscope.viewmodel.FinanceViewModelFactory

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FinanceViewModel by viewModels {
        FinanceViewModelFactory(requireActivity().application)
    }

    private val sharedPreferences by lazy {
        val masterKey = MasterKey.Builder(requireContext())
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            requireContext(),
            "secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupClickListeners()
        observeViewModel()
        updateUiState(false) // Initial setup, don't show edit mode
    }

    private fun updateUiState(isEditing: Boolean) {
        val token = sharedPreferences.getString(API_TOKEN_KEY, null)

        if (token.isNullOrEmpty() || isEditing) {
            // State 1: Token not set OR user is editing
            binding.layoutStatusConnected.visibility = View.GONE
            binding.layoutStatusDisconnected.visibility = View.VISIBLE
            binding.btnSync.visibility = View.GONE
            binding.etMonoToken.setText(token) // Show existing token if editing
        } else {
            // State 2: Token is set and user is not editing
            binding.layoutStatusConnected.visibility = View.VISIBLE
            binding.layoutStatusDisconnected.visibility = View.GONE
            binding.btnSync.visibility = View.VISIBLE
        }
    }

    private fun setupClickListeners() {
        binding.tvHowToGetToken.setOnClickListener {
            showTokenInfoDialog()
        }

        binding.btnSaveToken.setOnClickListener {
            val token = binding.etMonoToken.text.toString().trim()
            if (token.isNotEmpty()) {
                saveToken(token)
                updateUiState(false)
                Toast.makeText(requireContext(), "Токен збережено", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Токен не може бути порожнім", Toast.LENGTH_SHORT).show()
            }
        }

        // Corrected logic for "Update" button
        binding.btnUpdateToken.setOnClickListener {
            // Don't delete the token, just switch to edit mode
            updateUiState(true)
        }

        binding.btnSync.setOnClickListener {
            val token = sharedPreferences.getString(API_TOKEN_KEY, null)
            if (token != null) {
                viewModel.syncWithMonobank(token)
            }
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.btnSync.isEnabled = !isLoading
        }

        viewModel.syncStatus.observe(viewLifecycleOwner) { status ->
            status?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                viewModel.onSyncStatusShown()
            }
        }
    }

    private fun saveToken(token: String?) {
        sharedPreferences.edit().putString(API_TOKEN_KEY, token).apply()
    }

    private fun showTokenInfoDialog() {
        val message = """
        1. Перейдіть на сайт <a href="https://api.monobank.ua">api.monobank.ua</a> у вашому браузері.<br><br>
        2. Авторизуйтеся за допомогою додатку Monobank.<br><br>
        3. Скопіюйте ваш персональний токен та вставте його у це поле.
        """

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Як отримати токен Monobank")
            .setMessage(Html.fromHtml(message, Html.FROM_HTML_MODE_LEGACY))
            .setPositiveButton("Зрозуміло") { d, _ -> d.dismiss() }
            .show()
        
        // Make the link clickable
        (dialog.findViewById(android.R.id.message) as? TextView)?.movementMethod = LinkMovementMethod.getInstance()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val API_TOKEN_KEY = "mono_api_token"
    }
}