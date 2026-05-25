package com.arlabs.raksha.features.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arlabs.raksha.domain.model.EmergencyContact
import com.arlabs.raksha.domain.model.UserData
import com.arlabs.raksha.domain.repository.EmergencyContactRepository
import com.arlabs.raksha.domain.repository.UserPreferencesRepository
import com.arlabs.raksha.domain.util.Result
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val isContactDialogVisible: Boolean = false,
    val editingContact: EmergencyContact? = null,
    val dialogName: String = "",
    val dialogPhone: String = "",
    val dialogRelation: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    // Profile edit
    val isEditing: Boolean = false
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val emergencyContactRepository: EmergencyContactRepository,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    val userData: StateFlow<UserData> = userPreferencesRepository.userData
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserData()
        )

    val emergencyContacts: StateFlow<List<EmergencyContact>> = emergencyContactRepository.getContacts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState

    init {
        syncFromFirebaseAuth()
    }

    /**
     * Auto-populate name, email, and photo from Google Sign-In if they are empty in DataStore.
     */
    private fun syncFromFirebaseAuth() {
        viewModelScope.launch {
            try {
                val currentUser = firebaseAuth.currentUser ?: return@launch
                val existing = userPreferencesRepository.userData.first()

                // Check if phone provider is linked in Firebase Auth
                val hasPhoneProvider = currentUser.providerData.any { it.providerId == "phone" }

                val needsSync = existing.name.isEmpty() || existing.email.isEmpty()
                        || (hasPhoneProvider && !existing.isVerified)
                if (needsSync) {
                    val updated = existing.copy(
                        name = if (existing.name.isEmpty()) (currentUser.displayName ?: "") else existing.name,
                        email = if (existing.email.isEmpty()) (currentUser.email ?: "") else existing.email,
                        profilePhotoUrl = if (existing.profilePhotoUrl.isEmpty())
                            (currentUser.photoUrl?.toString() ?: "") else existing.profilePhotoUrl,
                        phone = if (existing.phone.isEmpty()) (currentUser.phoneNumber ?: "") else existing.phone,
                        isVerified = hasPhoneProvider || existing.isVerified
                    )
                    userPreferencesRepository.saveUserData(updated)
                    Log.d("ProfileViewModel", "Synced from Firebase Auth: ${updated.name}, ${updated.email}, isVerified=${updated.isVerified}")
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Failed to sync from Firebase Auth", e)
            }
        }
    }

    fun toggleEditMode() {
        _uiState.update { it.copy(isEditing = !it.isEditing) }
    }

    fun saveAllProfileData(
        name: String, phone: String, bloodGroup: String,
        allergies: String, conditions: String, note: String,
        timerMinutes: Int, sosMessage: String
    ) {
        viewModelScope.launch {
            val current = userPreferencesRepository.userData.first()
            userPreferencesRepository.saveUserData(
                current.copy(
                    name = name.trim(),
                    phone = phone.trim(),
                    bloodGroup = bloodGroup.trim(),
                    allergies = allergies.trim(),
                    medicalConditions = conditions.trim(),
                    emergencyNote = note.trim(),
                    defaultTimerMinutes = timerMinutes,
                    sosMessageTemplate = sosMessage.trim()
                )
            )
            
            // Sync the name back to Firestore so Cloud Functions (Twilio) can use it!
            try {
                val uid = firebaseAuth.currentUser?.uid
                if (uid != null) {
                    val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    firestore.collection("users").document(uid).set(
                        mapOf(
                            "displayName" to name.trim(),
                            "phone" to phone.trim(),
                            "bloodGroup" to bloodGroup.trim(),
                            "allergies" to allergies.trim(),
                            "medicalConditions" to conditions.trim(),
                            "emergencyNote" to note.trim()
                        ),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Failed to sync medical data to Firestore", e)
            }
            
            _uiState.update { it.copy(isEditing = false, successMessage = "Profile updated successfully") }
        }
    }


    // --- Contact Dialog Management ---

    fun showAddContactDialog() {
        _uiState.update {
            it.copy(isContactDialogVisible = true, editingContact = null,
                dialogName = "", dialogPhone = "", dialogRelation = "")
        }
    }

    fun showEditContactDialog(contact: EmergencyContact) {
        _uiState.update {
            it.copy(isContactDialogVisible = true, editingContact = contact,
                dialogName = contact.name, dialogPhone = contact.phoneNumber,
                dialogRelation = contact.relation)
        }
    }

    fun dismissContactDialog() {
        _uiState.update {
            it.copy(isContactDialogVisible = false, editingContact = null,
                dialogName = "", dialogPhone = "", dialogRelation = "", isLoading = false)
        }
    }

    fun onDialogNameChange(name: String) { _uiState.update { it.copy(dialogName = name) } }
    fun onDialogPhoneChange(phone: String) { _uiState.update { it.copy(dialogPhone = phone) } }
    fun onDialogRelationChange(relation: String) { _uiState.update { it.copy(dialogRelation = relation) } }

    /**
     * Set contact fields from a system contact picker result.
     */
    fun setContactFromPicker(name: String, phone: String) {
        _uiState.update { it.copy(dialogName = name, dialogPhone = phone) }
    }

    fun saveContact() {
        val state = _uiState.value
        if (state.dialogName.isBlank() || state.dialogPhone.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Name and phone are required") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val contact = EmergencyContact(
                id = state.editingContact?.id ?: "",
                name = state.dialogName.trim(),
                phoneNumber = state.dialogPhone.trim(),
                relation = state.dialogRelation.trim()
            )

            val result = if (state.editingContact != null) {
                emergencyContactRepository.updateContact(contact)
            } else {
                emergencyContactRepository.addContact(contact)
            }

            // Always dismiss the dialog and reset loading
            _uiState.update {
                when (result) {
                    is Result.Success -> it.copy(
                        isLoading = false,
                        isContactDialogVisible = false,
                        editingContact = null,
                        dialogName = "", dialogPhone = "", dialogRelation = "",
                        successMessage = if (state.editingContact != null) "Contact updated" else "Contact added"
                    )
                    is Result.Failure -> it.copy(
                        isLoading = false,
                        isContactDialogVisible = false, // ALWAYS close dialog
                        errorMessage = result.message
                    )
                    else -> it.copy(isLoading = false, isContactDialogVisible = false)
                }
            }
        }
    }

    fun deleteContact(contactId: String) {
        viewModelScope.launch {
            val result = emergencyContactRepository.deleteContact(contactId)
            when (result) {
                is Result.Success -> _uiState.update { it.copy(successMessage = "Contact deleted") }
                is Result.Failure -> _uiState.update { it.copy(errorMessage = result.message) }
                else -> {}
            }
        }
    }

    fun clearError() { _uiState.update { it.copy(errorMessage = null) } }
    fun clearSuccess() { _uiState.update { it.copy(successMessage = null) } }
}
