package com.arlabs.raksha.data.repositoryImpl

import com.arlabs.raksha.domain.model.EmergencyContact
import com.arlabs.raksha.domain.repository.EmergencyContactRepository
import com.arlabs.raksha.domain.util.Result
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class EmergencyContactRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : EmergencyContactRepository {

    private fun getContactsCollection() = auth.currentUser?.uid?.let { uid ->
        firestore.collection("users").document(uid).collection("emergency_contacts")
    }

    override fun getContacts(): Flow<List<EmergencyContact>> = callbackFlow {
        val collection = getContactsCollection()
        if (collection == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = collection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }

            val contacts = snapshot?.documents?.map { doc ->
                EmergencyContact(
                    id = doc.id,
                    name = doc.getString("name") ?: "",
                    phoneNumber = doc.getString("phoneNumber") ?: "",
                    relation = doc.getString("relation") ?: ""
                )
            } ?: emptyList()

            trySend(contacts)
        }

        awaitClose { listener.remove() }
    }

    override suspend fun addContact(contact: EmergencyContact): Result<Unit> {
        val collection = getContactsCollection()
            ?: return Result.Failure("User not authenticated")

        return try {
            val data = hashMapOf(
                "name" to contact.name,
                "phoneNumber" to contact.phoneNumber,
                "relation" to contact.relation
            )
            collection.add(data).await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to add contact")
        }
    }

    override suspend fun updateContact(contact: EmergencyContact): Result<Unit> {
        val collection = getContactsCollection()
            ?: return Result.Failure("User not authenticated")

        return try {
            val data = hashMapOf(
                "name" to contact.name,
                "phoneNumber" to contact.phoneNumber,
                "relation" to contact.relation
            )
            collection.document(contact.id).set(data).await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to update contact")
        }
    }

    override suspend fun deleteContact(contactId: String): Result<Unit> {
        val collection = getContactsCollection()
            ?: return Result.Failure("User not authenticated")

        return try {
            collection.document(contactId).delete().await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Failed to delete contact")
        }
    }
}
