package com.arlabs.raksha.features.livelocation.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arlabs.raksha.features.livelocation.data.LiveLocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LiveLocationViewModel @Inject constructor(
    private val repository: LiveLocationRepository
) : ViewModel() {

    fun startLiveLocationSession(durationMinutes: Int, onSessionCreated: (String, Long) -> Unit) {
        viewModelScope.launch {
            try {
                val sessionId = repository.createSession(durationMinutes)
                val expiresAt = System.currentTimeMillis() + (durationMinutes * 60 * 1000L)
                onSessionCreated(sessionId, expiresAt)
            } catch (e: Exception) {
                // Ignore for MVP, could use error event stream
            }
        }
    }
}
