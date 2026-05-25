package com.arlabs.raksha.features.auth

import android.app.Activity
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arlabs.raksha.domain.repository.UserPreferencesRepository
import com.arlabs.raksha.domain.usecase.CheckUserPhoneUseCase
import com.arlabs.raksha.domain.usecase.GoogleSignInUseCase
import com.arlabs.raksha.domain.usecase.SendOtpUseCase
import com.arlabs.raksha.domain.usecase.SetUserPreferencesUseCase
import com.arlabs.raksha.domain.usecase.VerifyOtpUseCase
import com.arlabs.raksha.domain.util.Result
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.firebase.auth.PhoneAuthProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val googleSignInUseCase: GoogleSignInUseCase,
    private val setUserPreferencesUseCase: SetUserPreferencesUseCase,
    private val checkUserPhoneUseCase: CheckUserPhoneUseCase,
    private val sendOtpUseCase: SendOtpUseCase,
    private val verifyOtpUseCase: VerifyOtpUseCase,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _authState = MutableStateFlow<Result<String>>(Result.Idle)
    val authState: StateFlow<Result<String>> = _authState.asStateFlow()

    private val _verificationId = MutableStateFlow<String?>(null)
    val verificationId: StateFlow<String?> = _verificationId.asStateFlow()


    fun signInWithGoogle(account: GoogleSignInAccount){
        Log.d("AuthViewModel", "Starting Google sign in for: ${account.email}")
        _authState.value = Result.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = googleSignInUseCase(account)
                Log.d("AuthViewModel", "Google sign in result: $result")
                
                if (result is Result.Success) {
                     // Google Sign-in Success, now check phone
                    checkUserPhone(com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "")
                } else {
                    _authState.value = result
                }
            } catch (e: Exception) {
                Log.e("AuthViewModel","Exception in Google sign in: ${e.message}",e)
                _authState.value = Result.Failure(e.message ?: "Google sign in failed")
            }
        }
    }

    private suspend fun checkUserPhone(uid: String) {
        try {
            val result = checkUserPhoneUseCase(uid)
            Log.d("AuthViewModel", "checkUserPhone result: $result")
        } catch (e: Exception) {
            // Phone check is optional — don't block login if Firestore is unavailable
            Log.w("AuthViewModel", "checkUserPhone failed (non-blocking): ${e.message}", e)
        }
        // We no longer block login based on phone verification. 
        // It is now an optional step accessed via the Profile Screen.
        setUserPreferencesUseCase.setLoggedIn(true)
        setUserPreferencesUseCase.setFirstTimeLogin(false)
        _authState.value = Result.Success("Login Complete")
    }

    fun sendOtp(phoneNumber: String, activity: Activity) {
        _authState.value = Result.Loading
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: com.google.firebase.auth.PhoneAuthCredential) {
                // Auto-retrieval or instant verification
                // We can verify directly here if needed, but usually we wait for code input if flows are separate.
                // For simplicity, let's treat it as code sent success or auto-verify logic if we want.
                // But often 'onCodeSent' is what we handle for manual entry.
                 Log.d("AuthViewModel", "onVerificationCompleted: ${credential.smsCode}")
            }

            override fun onVerificationFailed(e: com.google.firebase.FirebaseException) {
                Log.e("AuthViewModel", "onVerificationFailed", e)
                _authState.value = Result.Failure(e.message ?: "Verification failed")
            }

            override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                Log.d("AuthViewModel", "onCodeSent: $verificationId")
                _verificationId.value = verificationId
                _authState.value = Result.Success("Code Sent")
            }
        }
        sendOtpUseCase(phoneNumber, activity, callbacks)
    }

    fun verifyOtp(code: String) {
        val vid = _verificationId.value
        if (vid == null) {
            _authState.value = Result.Failure("Verification ID missing")
            return
        }
        _authState.value = Result.Loading
        viewModelScope.launch(Dispatchers.IO) {
             val result = verifyOtpUseCase(vid, code)
             if (result is Result.Success) {
                 // Update local DataStore: mark phone as verified
                 try {
                     val currentData = userPreferencesRepository.userData.first()
                     userPreferencesRepository.saveUserData(currentData.copy(isVerified = true))
                 } catch (e: Exception) {
                     Log.e("AuthViewModel", "Failed to update isVerified in DataStore", e)
                 }
                 setUserPreferencesUseCase.setLoggedIn(true)
                 setUserPreferencesUseCase.setFirstTimeLogin(false)
                 _authState.value = Result.Success("Login Complete")
             } else {
                 _authState.value = result
             }
        }
    }

    fun resetAuthState(){
        _authState.value = Result.Idle
    }
}