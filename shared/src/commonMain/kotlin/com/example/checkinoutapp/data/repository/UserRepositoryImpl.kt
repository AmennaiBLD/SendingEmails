package com.example.checkinoutapp.data.repository

import com.example.checkinoutapp.core.RequestState
import com.example.checkinoutapp.core.toUserMessage
import com.example.checkinoutapp.data.firebase.auditLog
import com.example.checkinoutapp.data.firebase.currentActor
import com.example.checkinoutapp.data.firebase.millisTimestamp
import com.example.checkinoutapp.data.firebase.nowMillis
import com.example.checkinoutapp.domain.registration.isValidEmail
import com.example.checkinoutapp.domain.registration.normalizeEmail
import com.example.checkinoutapp.domain.registration.normalizePhone
import com.example.checkinoutapp.firebase.staffAccountsApp
import com.example.checkinoutapp.domain.model.AppUser
import com.example.checkinoutapp.domain.model.UserRole
import com.example.checkinoutapp.domain.repository.StaffOutcome
import com.example.checkinoutapp.domain.repository.UserRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.FirebaseAuthUserCollisionException
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest

class UserRepositoryImpl : UserRepository {
    override fun readUserFlow(uid: String): Flow<RequestState<AppUser?>> = channelFlow {
        try {
            send(RequestState.Loading)
            val database = Firebase.firestore
            database.collection(collectionPath = "users")
                .document(uid)
                .snapshots
                .collectLatest { document ->
                    if (document.exists) {
                        send(RequestState.Success(data = userFrom(document)))
                    } else {
                        send(RequestState.Success(data = null))
                    }
                }
        } catch (e: Exception) {
            send(RequestState.Error("Error while reading the user: ${e.toUserMessage()}"))
        }
    }

    override fun readStaffFlow(limit: Int): Flow<RequestState<List<AppUser>>> = channelFlow {
        try {
            send(RequestState.Loading)
            val database = Firebase.firestore
            database.collection(collectionPath = "users")
                .where { "role" inArray listOf(UserRole.ORGANIZER.name, UserRole.ADMIN.name) }
                .orderBy("fullName", Direction.ASCENDING)
                .limit(limit)
                .snapshots
                .collectLatest { query ->
                    val staff = query.documents.mapNotNull { document ->
                        try {
                            userFrom(document)
                        } catch (e: Exception) {
                            println("[club] Skipped an unreadable user '${document.id}': ${e.message}")
                            null
                        }
                    }
                    send(RequestState.Success(data = staff))
                }
        } catch (e: Exception) {
            send(RequestState.Error("Error while reading the staff: ${e.toUserMessage()}"))
        }
    }

    /**
     * Creates the sign-in through a second Firebase app (the administrator stays signed in), then
     * writes the role document. firestore.rules lets only an active administrator write it.
     */
    override suspend fun createStaff(
        fullName: String,
        email: String,
        phone: String,
        password: String,
        role: UserRole
    ): RequestState<StaffOutcome> {
        return try {
            val name = fullName.trim()
            val normalizedEmail = normalizeEmail(email)
            if (name.length < 2 || !isValidEmail(normalizedEmail) || password.length < 8) {
                return RequestState.Success(data = StaffOutcome.INVALID_INPUT)
            }
            val actor = currentActor()
            val staffAuth = Firebase.auth(staffAccountsApp())
            val createdUid = try {
                staffAuth.createUserWithEmailAndPassword(normalizedEmail, password).user?.uid
            } catch (e: FirebaseAuthUserCollisionException) {
                return RequestState.Success(data = StaffOutcome.EMAIL_IN_USE)
            } finally {
                runCatching { staffAuth.signOut() }
            }
            val uid = createdUid ?: return RequestState.Success(data = StaffOutcome.UNKNOWN)

            val database = Firebase.firestore
            val at = millisTimestamp(nowMillis())
            database.batch().apply {
                set(
                    database.collection(collectionPath = "users").document(uid),
                    mapOf(
                        "id" to uid,
                        "fullName" to name,
                        "email" to normalizedEmail,
                        "phone" to normalizePhone(phone),
                        "profilePhotoUrl" to null,
                        "role" to role.name,
                        "isActive" to true,
                        "createdAt" to at,
                        "updatedAt" to at,
                        "createdBy" to actor.uid,
                    ),
                )
                set(
                    database.collection(collectionPath = "auditLogs").document,
                    auditLog(actor, "STAFF_ACCOUNT_CREATED", "user", uid, null, at, newValue = mapOf("email" to normalizedEmail, "role" to role.name)),
                )
            }.commit()
            RequestState.Success(data = StaffOutcome.SAVED)
        } catch (e: Exception) {
            RequestState.Error("Error while creating the account: ${e.toUserMessage()}")
        }
    }

    /**
     * Changes a staff member's role or switches their access off. An inactive account can still
     * sign in, but firestore.rules then refuses it every read and write.
     */
    override suspend fun updateAccess(uid: String, role: UserRole, isActive: Boolean): RequestState<StaffOutcome> {
        return try {
            val actor = currentActor()
            if (uid == actor.uid) return RequestState.Success(data = StaffOutcome.CANNOT_CHANGE_SELF)
            val database = Firebase.firestore
            val userRef = database.collection(collectionPath = "users").document(uid)
            val outcome = database.runTransaction {
                val user = get(userRef)
                if (!user.exists) return@runTransaction StaffOutcome.USER_NOT_FOUND
                val at = millisTimestamp(nowMillis())
                update(userRef, "role" to role.name, "isActive" to isActive, "updatedAt" to at)
                set(
                    database.collection(collectionPath = "auditLogs").document,
                    auditLog(
                        actor, "STAFF_ACCESS_CHANGED", "user", uid, null, at,
                        previousValue = mapOf("role" to user.get<String?>(field = "role"), "isActive" to user.get<Boolean?>(field = "isActive")),
                        newValue = mapOf("role" to role.name, "isActive" to isActive),
                    ),
                )
                StaffOutcome.SAVED
            }
            RequestState.Success(data = outcome)
        } catch (e: Exception) {
            RequestState.Error("Error while updating the access: ${e.toUserMessage()}")
        }
    }

    private fun userFrom(document: DocumentSnapshot): AppUser {
        return AppUser(
            uid = document.id,
            fullName = document.get(field = "fullName"),
            email = document.get(field = "email"),
            phone = document.get<String?>(field = "phone") ?: "",
            role = UserRole.fromRaw(document.get<String?>(field = "role")),
            isActive = document.get(field = "isActive"),
        )
    }
}
