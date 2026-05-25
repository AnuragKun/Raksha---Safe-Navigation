package com.arlabs.raksha.domain.repository

import com.arlabs.raksha.domain.model.EmergencyContact
import com.arlabs.raksha.domain.util.Result
import kotlinx.coroutines.flow.Flow

interface EmergencyContactRepository {
    fun getContacts(): Flow<List<EmergencyContact>>
    suspend fun addContact(contact: EmergencyContact): Result<Unit>
    suspend fun updateContact(contact: EmergencyContact): Result<Unit>
    suspend fun deleteContact(contactId: String): Result<Unit>
}
