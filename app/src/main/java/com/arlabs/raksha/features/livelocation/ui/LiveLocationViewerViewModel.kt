package com.arlabs.raksha.features.livelocation.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arlabs.raksha.features.livelocation.data.LiveLocationRepository
import com.arlabs.raksha.features.livelocation.data.LiveLocationSession
import com.arlabs.raksha.features.livelocation.data.LocationPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LiveLocationViewerViewModel @Inject constructor(
    private val repository: LiveLocationRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val sessionId: String = checkNotNull(savedStateHandle["sessionId"])

    private val _sessionState = MutableStateFlow<LiveLocationSession?>(null)
    val sessionState: StateFlow<LiveLocationSession?> = _sessionState.asStateFlow()

    private val _locationPoints = MutableStateFlow<List<LocationPoint>>(emptyList())
    val locationPoints: StateFlow<List<LocationPoint>> = _locationPoints.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        observeSession()
        observePoints()
    }

    private fun observeSession() {
        viewModelScope.launch {
            repository.observeSession(sessionId)
                .catch { /* Handle error */ }
                .collect {
                    _sessionState.value = it
                    _isLoading.value = false
                }
        }
    }

    private fun observePoints() {
        viewModelScope.launch {
            repository.observeLocationPoints(sessionId)
                .catch { /* Handle error */ }
                .collect { points ->
                    _locationPoints.value = points
                }
        }
    }
}
