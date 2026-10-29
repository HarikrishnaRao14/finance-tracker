package com.harikrishnarao.financetracker.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.harikrishnarao.financetracker.FinanceApp
import com.harikrishnarao.financetracker.update.UpdateViewModel

/** Builds every ViewModel from the app's single dependency container. */
val AppViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
    fun app(extras: CreationExtras) = extras[APPLICATION_KEY] as FinanceApp
    initializer { RootViewModel(app(this).container.settings, app(this).container.repository) }
    initializer { QuickAddViewModel(app(this).container.repository) }
    initializer { OnboardingViewModel(app(this).container.repository, app(this).container.settings) }
    initializer { HomeViewModel(app(this).container.repository, app(this).container.settings, app(this)) }
    initializer { SpendsViewModel(app(this).container.repository, app(this).container.settings) }
    initializer { AnalysisViewModel(app(this).container.repository, app(this).container.settings) }
    initializer { BillsViewModel(app(this).container.repository) }
    initializer { SettingsViewModel(app(this).container.repository, app(this).container.settings) }
    initializer { SmsActivityViewModel(app(this).container.repository) }
    initializer { UpdateViewModel(app(this).container.updater) }
}
