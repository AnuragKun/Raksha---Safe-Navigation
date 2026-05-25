package com.arlabs.raksha.features.emergencyhub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arlabs.raksha.domain.repository.SosRepository
import com.arlabs.raksha.domain.util.Result
import com.google.android.gms.location.FusedLocationProviderClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EmergencyHubState(
    val isConfirmDialogVisible: Boolean = false,
    val isTriggering: Boolean = false,
    val isSosActive: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class EmergencyHubViewModel @Inject constructor(
    private val sosRepository: SosRepository,
    private val fusedLocationClient: FusedLocationProviderClient
) : ViewModel() {

    private val _state = MutableStateFlow(EmergencyHubState())
    val state: StateFlow<EmergencyHubState> = _state.asStateFlow()

    init {
        fetchLocation()
    }

    private fun fetchLocation() {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    _state.update {
                        it.copy(
                            latitude = location.latitude,
                            longitude = location.longitude
                        )
                    }
                }
            }
        } catch (_: SecurityException) {
            // Location permission not granted, SOS will still work but without location
        }
    }

    fun showConfirmDialog() {
        _state.update { it.copy(isConfirmDialogVisible = true) }
    }

    fun dismissConfirmDialog() {
        _state.update { it.copy(isConfirmDialogVisible = false) }
    }

    fun triggerSos() {
        _state.update { it.copy(isConfirmDialogVisible = false, isTriggering = true, errorMessage = null) }

        viewModelScope.launch {
            // Refresh location before triggering
            fetchLocation()

            val currentState = _state.value
            val result = sosRepository.triggerSos(
                latitude = currentState.latitude,
                longitude = currentState.longitude
            )

            when (result) {
                is Result.Success -> {
                    _state.update {
                        it.copy(isTriggering = false, isSosActive = true)
                    }
                }
                is Result.Failure -> {
                    _state.update {
                        it.copy(isTriggering = false, errorMessage = result.message)
                    }
                }
                else -> {
                    _state.update { it.copy(isTriggering = false) }
                }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun resetSos() {
        _state.update { it.copy(isSosActive = false) }
    }
}
