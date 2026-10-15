package com.example.climapulse.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.climapulse.AppContainer
import com.example.climapulse.ClimaPulseApplication

/** Crea ViewModels con acceso al contenedor de dependencias y a su SavedStateHandle. */
inline fun <reified VM : ViewModel> appViewModelFactory(
    crossinline create: (AppContainer, SavedStateHandle) -> VM
): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        val app = this[APPLICATION_KEY] as ClimaPulseApplication
        create(app.container, createSavedStateHandle())
    }
}
