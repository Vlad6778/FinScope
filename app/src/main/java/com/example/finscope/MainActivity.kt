package com.example.finscope

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.example.finscope.databinding.ActivityMainBinding
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navView: BottomNavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment)
        navView.setupWithNavController(navController)

        navView.setOnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.homeFragment -> {
                    val handled = navController.popBackStack(R.id.homeFragment, false)
                    if (!handled) {
                        navController.navigate(R.id.homeFragment)
                    }
                    true
                }
                R.id.historyFragment -> {
                    val handled = navController.popBackStack(R.id.historyFragment, false)
                    if (!handled) {
                        navController.navigate(R.id.historyFragment)
                    }
                    true
                }
                R.id.categoriesFragment -> {
                    val handled = navController.popBackStack(R.id.categoriesFragment, false)
                    if (!handled) {
                        navController.navigate(R.id.categoriesFragment)
                    }
                    true
                }
                R.id.currencyFragment -> {
                    val handled = navController.popBackStack(R.id.currencyFragment, false)
                    if (!handled) {
                        navController.navigate(R.id.currencyFragment)
                    }
                    true
                }
                else -> false
            }
        }
    }
}