package com.arlabs.raksha.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.arlabs.raksha.domain.model.UserData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UserPreferencesDataStore(
    private val context: Context
) {
    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("user_preferences")
        private val IS_FIRST_TIME_LOGIN = booleanPreferencesKey("is_first_time_login")
        private val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")

        // Profile Keys
        private val USER_NAME = stringPreferencesKey("user_name")
        private val USER_EMAIL = stringPreferencesKey("user_email")
        private val USER_PHONE = stringPreferencesKey("user_phone")
        private val USER_DOB = longPreferencesKey("user_dob")
        private val USER_BLOOD_GROUP = stringPreferencesKey("user_blood_group")
        private val USER_IS_VERIFIED = booleanPreferencesKey("user_is_verified")
        private val USER_PHOTO_URL = stringPreferencesKey("user_photo_url")

        // Medical ID
        private val USER_ALLERGIES = stringPreferencesKey("user_allergies")
        private val USER_MEDICAL_CONDITIONS = stringPreferencesKey("user_medical_conditions")
        private val USER_EMERGENCY_NOTE = stringPreferencesKey("user_emergency_note")

        // Safety Preferences
        private val DEFAULT_TIMER_MINUTES = intPreferencesKey("default_timer_minutes")
        // Fake Call
        private val FAKE_CALLER_IDS = stringPreferencesKey("fake_caller_ids")
        // SOS
        private val SOS_MESSAGE_TEMPLATE = stringPreferencesKey("sos_message_template")
        // Default Fake Call (Panic Overlay)
        private val DEFAULT_FAKE_CALL_CALLER = stringPreferencesKey("default_fake_call_caller")
        private val DEFAULT_FAKE_CALL_DELAY = intPreferencesKey("default_fake_call_delay")
    }

    val isFirstTimeLogin: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_FIRST_TIME_LOGIN] ?: true
    }

    val isLoggedIn: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_LOGGED_IN] ?: false
    }

    val userData: Flow<UserData> = context.dataStore.data.map { preferences ->
        UserData(
            name = preferences[USER_NAME] ?: "",
            email = preferences[USER_EMAIL] ?: "",
            phone = preferences[USER_PHONE] ?: "",
            dob = preferences[USER_DOB] ?: 0L,
            bloodGroup = preferences[USER_BLOOD_GROUP] ?: "",
            isVerified = preferences[USER_IS_VERIFIED] ?: false,
            profilePhotoUrl = preferences[USER_PHOTO_URL] ?: "",
            allergies = preferences[USER_ALLERGIES] ?: "",
            medicalConditions = preferences[USER_MEDICAL_CONDITIONS] ?: "",
            emergencyNote = preferences[USER_EMERGENCY_NOTE] ?: "",
            defaultTimerMinutes = preferences[DEFAULT_TIMER_MINUTES] ?: 20,
            sosMessageTemplate = preferences[SOS_MESSAGE_TEMPLATE]
                ?: "I'm using Raksha and my timer just expired. Please check my last location.",
            fakeCallerIds = preferences[FAKE_CALLER_IDS]?.split(",")?.filter { it.isNotBlank() } ?: listOf("Unknown|+91 98765 43210", "Mom|+91 99876 54321", "Police|100"),
            defaultFakeCallCallerEncoded = preferences[DEFAULT_FAKE_CALL_CALLER] ?: "Mom|+91 99876 54321",
            defaultFakeCallDelaySeconds = preferences[DEFAULT_FAKE_CALL_DELAY] ?: 5
        )
    }

    suspend fun setFirstTimeLogin(isFirstTime: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_FIRST_TIME_LOGIN] = isFirstTime
        }
    }

    suspend fun setLoggedIn(isLoggedIn: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_LOGGED_IN] = isLoggedIn
        }
    }

    suspend fun saveUserData(userData: UserData) {
        context.dataStore.edit { preferences ->
            preferences[USER_NAME] = userData.name
            preferences[USER_EMAIL] = userData.email
            preferences[USER_PHONE] = userData.phone
            preferences[USER_DOB] = userData.dob
            preferences[USER_BLOOD_GROUP] = userData.bloodGroup
            preferences[USER_IS_VERIFIED] = userData.isVerified
            preferences[USER_PHOTO_URL] = userData.profilePhotoUrl
            preferences[USER_ALLERGIES] = userData.allergies
            preferences[USER_MEDICAL_CONDITIONS] = userData.medicalConditions
            preferences[USER_EMERGENCY_NOTE] = userData.emergencyNote
            preferences[DEFAULT_TIMER_MINUTES] = userData.defaultTimerMinutes
            preferences[SOS_MESSAGE_TEMPLATE] = userData.sosMessageTemplate
            preferences[FAKE_CALLER_IDS] = userData.fakeCallerIds.joinToString(",")
            preferences[DEFAULT_FAKE_CALL_CALLER] = userData.defaultFakeCallCallerEncoded
            preferences[DEFAULT_FAKE_CALL_DELAY] = userData.defaultFakeCallDelaySeconds
        }
    }
}