package com.arlabs.raksha.features.safetytimer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arlabs.raksha.domain.repository.TimerRepository
import com.arlabs.raksha.domain.repository.UserPreferencesRepository
import com.arlabs.raksha.domain.util.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TimerUiState(
    val isActive: Boolean = false,
    val selectedMinutes: Int = 20,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val remainingTimeMs: Long = 0L, // milliseconds remaining
    val expiryTimeMs: Long = 0L     // absolute expiry time in ms
)

@HiltViewModel
class SafetyTimerViewModel @Inject constructor(
    private val timerRepository: TimerRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimerUiState())
    val uiState: StateFlow<TimerUiState> = _uiState.asStateFlow()

    private var tickerJob: kotlinx.coroutines.Job? = null

    init {
        loadDefaultTimer()
        observeTimerStatus()
        observeExpiryTime()
    }

    private fun loadDefaultTimer() {
        viewModelScope.launch {
            val userData = userPreferencesRepository.userData.first()
            if (!_uiState.value.isActive) {
                _uiState.update { it.copy(selectedMinutes = userData.defaultTimerMinutes.takeIf { m -> m > 0 } ?: 20) }
            }
        }
    }

    private fun observeTimerStatus() {
        viewModelScope.launch {
            timerRepository.isTimerActive().collect { isActive ->
                _uiState.update { state -> 
                    // If state flips, we reset loading. Firestore snapshot means the local change succeeded.
                    val newLoading = if (state.isActive != isActive) false else state.isLoading
                    state.copy(isActive = isActive, isLoading = newLoading) 
                }
            }
        }
    }

    private fun observeExpiryTime() {
        viewModelScope.launch {
            timerRepository.getExpiryTime().collect { expiryMs ->
                _uiState.update { it.copy(expiryTimeMs = expiryMs) }
                if (expiryMs > 0L) {
                    startCountdownTicker(expiryMs)
                } else {
                    tickerJob?.cancel()
                    _uiState.update { it.copy(remainingTimeMs = 0L) }
                }
            }
        }
    }

    /**
     * Ticks every second to update the remaining time display.
     */
    private fun startCountdownTicker(expiryMs: Long) {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (true) {
                val remaining = expiryMs - System.currentTimeMillis()
                if (remaining <= 0) {
                    _uiState.update { it.copy(remainingTimeMs = 0L) }
                    break
                }
                _uiState.update { it.copy(remainingTimeMs = remaining) }
                delay(1000L)
            }
        }
    }

    fun setDuration(minutes: Int) {
        _uiState.update { it.copy(selectedMinutes = minutes) }
    }

    fun startTimer() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val result = timerRepository.startTimer(_uiState.value.selectedMinutes)
            _uiState.update { 
                it.copy(
                    isLoading = false,
                    errorMessage = if (result is Result.Failure) result.message else it.errorMessage
                ) 
            }
        }
    }

    fun cancelTimer() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val result = timerRepository.cancelTimer()
            _uiState.update { 
                it.copy(
                    isLoading = false,
                    errorMessage = if (result is Result.Failure) result.message else it.errorMessage
                ) 
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
