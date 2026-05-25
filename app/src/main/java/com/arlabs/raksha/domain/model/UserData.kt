package com.arlabs.raksha.domain.model

data class UserData(
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val dob: Long = 0L,
    val bloodGroup: String = "",
    val isVerified: Boolean = false,
    val profilePhotoUrl: String = "",
    val allergies: String = "",
    val medicalConditions: String = "",
    val emergencyNote: String = "",
    val defaultTimerMinutes: Int = 20,
    val sosMessageTemplate: String = "I'm using Raksha and my timer just expired. Please check my last location.",
    val fakeCallerIds: List<String> = emptyList(),
    val defaultFakeCallCallerEncoded: String = "Mom|+91 99876 54321",
    val defaultFakeCallDelaySeconds: Int = 5
)
