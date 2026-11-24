package com.example.ui.developer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.DeviceStatusRepository
import com.example.data.scanner.DeveloperModeStatus
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DeveloperModeViewModel(
    private val deviceStatusRepository: DeviceStatusRepository
) : ViewModel() {

    val status: StateFlow<DeveloperModeStatus> = deviceStatusRepository.developerModeStatus

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            deviceStatusRepository.refreshSystemStatus()
        }
    }

    class Factory(
        private val deviceStatusRepository: DeviceStatusRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DeveloperModeViewModel(deviceStatusRepository) as T
        }
    }
}
