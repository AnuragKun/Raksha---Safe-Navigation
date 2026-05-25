package com.arlabs.raksha.features.fakecall

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arlabs.raksha.data.local.UserPreferencesDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Caller entries are stored as "Name|PhoneNumber" in fakeCallerIds.
 * Legacy entries without "|" are treated as name-only.
 */
data class CallerEntry(val name: String, val phone: String) {
    fun encode(): String = "$name|$phone"

    companion object {
        fun decode(encoded: String): CallerEntry {
            val parts = encoded.split("|", limit = 2)
            return if (parts.size == 2) {
                CallerEntry(parts[0], parts[1])
            } else {
                CallerEntry(encoded, "")
            }
        }
    }
}

@HiltViewModel
class FakeCallViewModel @Inject constructor(
    private val userPreferencesDataStore: UserPreferencesDataStore,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _callerEntriesFlow = MutableStateFlow<List<CallerEntry>>(emptyList())
    val callerEntries: StateFlow<List<CallerEntry>> = _callerEntriesFlow

    // Keep raw encoded list for internal use
    private val _callerIdsFlow = MutableStateFlow<List<String>>(emptyList())
    val currentCallerIds: StateFlow<List<String>> = _callerIdsFlow

    // Default fake call settings for panic overlay
    private val _defaultFakeCallCaller = MutableStateFlow(CallerEntry("Mom", "+91 99876 54321"))
    val defaultFakeCallCaller: StateFlow<CallerEntry> = _defaultFakeCallCaller

    private val _defaultFakeCallDelay = MutableStateFlow(5)
    val defaultFakeCallDelay: StateFlow<Int> = _defaultFakeCallDelay

    init {
        viewModelScope.launch {
            userPreferencesDataStore.userData.collect { userData ->
                _callerIdsFlow.value = userData.fakeCallerIds
                _callerEntriesFlow.value = userData.fakeCallerIds.map { CallerEntry.decode(it) }
                _defaultFakeCallCaller.value = CallerEntry.decode(userData.defaultFakeCallCallerEncoded)
                _defaultFakeCallDelay.value = userData.defaultFakeCallDelaySeconds
            }
        }
    }

    fun addCallerId(name: String, phone: String = "") {
        if (name.isBlank()) return
        viewModelScope.launch {
            val entry = CallerEntry(name.trim(), phone.trim())
            val userData = userPreferencesDataStore.userData.first()
            val updatedList = (userData.fakeCallerIds + entry.encode()).distinct()
            userPreferencesDataStore.saveUserData(userData.copy(fakeCallerIds = updatedList))
        }
    }

    fun removeCallerEntry(entry: CallerEntry) {
        viewModelScope.launch {
            val userData = userPreferencesDataStore.userData.first()
            val updatedList = userData.fakeCallerIds.filter { it != entry.encode() }
            userPreferencesDataStore.saveUserData(userData.copy(fakeCallerIds = updatedList))
        }
    }

    /** Save the currently selected caller + delay as the panic overlay default */
    fun setAsDefault(entry: CallerEntry, delaySeconds: Int) {
        viewModelScope.launch {
            val userData = userPreferencesDataStore.userData.first()
            userPreferencesDataStore.saveUserData(
                userData.copy(
                    defaultFakeCallCallerEncoded = entry.encode(),
                    defaultFakeCallDelaySeconds = delaySeconds
                )
            )
        }
    }

    fun scheduleFakeCall(callerEntry: CallerEntry, delaySeconds: Int) {
        scheduleFakeCallFromContext(context, callerEntry, delaySeconds)
    }

    companion object {
        private const val TAG = "FakeCallVM"
        private const val ALARM_REQUEST_CODE = 999

        /**
         * Static method that can be called from PanicOverlayService (no ViewModel needed).
         * Schedules a fake call via AlarmManager, or falls back to a direct delayed launch.
         */
        fun scheduleFakeCallFromContext(context: Context, callerEntry: CallerEntry, delaySeconds: Int) {
            try {
                val intent = Intent(context, FakeCallReceiver::class.java).apply {
                    putExtra("CALLER_NAME", callerEntry.name)
                    putExtra("CALLER_PHONE", callerEntry.phone)
                }

                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    ALARM_REQUEST_CODE,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val triggerTime = System.currentTimeMillis() + (delaySeconds * 1000L)

                // Try exact alarm first — requires SCHEDULE_EXACT_ALARM permission
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                        Log.d(TAG, "Scheduled exact alarm for fake call in ${delaySeconds}s")
                    } else {
                        // Fallback: use Handler-based delay since inexact alarms are unreliable
                        Log.w(TAG, "Cannot schedule exact alarms, using Handler fallback")
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            try {
                                val directIntent = Intent(context, FakeCallReceiver::class.java).apply {
                                    putExtra("CALLER_NAME", callerEntry.name)
                                    putExtra("CALLER_PHONE", callerEntry.phone)
                                }
                                context.sendBroadcast(directIntent)
                            } catch (e: Exception) {
                                Log.e(TAG, "Handler fallback failed", e)
                            }
                        }, delaySeconds * 1000L)
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                    Log.d(TAG, "Scheduled exact alarm (pre-S) for fake call in ${delaySeconds}s")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to schedule fake call", e)
            }
        }
    }
}
