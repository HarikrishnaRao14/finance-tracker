package com.harikrishnarao.financetracker.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.harikrishnarao.financetracker.FinanceApp

/** Builds every ViewModel from the app's single dependency container. */
val AppViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
    fun app(extras: androidx.lifecycle.viewmodel.CreationExtras) = (extras[APPLICATION_KEY] as FinanceApp).container
    initializer { RootViewModel(app(this).settings) }
    initializer { OnboardingViewModel(app(this).repository, app(this).settings) }
    initializer { HomeViewModel(app(this).repository, app(this).settings) }
    initializer { SpendsViewModel(app(this).repository, app(this).settings) }
    initializer { BudgetViewModel(app(this).repository, app(this).settings) }
    initializer { BillsViewModel(app(this).repository) }
    initializer { SettingsViewModel(app(this).repository, app(this).settings) }
}
