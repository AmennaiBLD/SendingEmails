package com.example.checkinoutapp.data.repository

import com.example.checkinoutapp.core.RequestState
import com.example.checkinoutapp.core.toUserMessage
import com.example.checkinoutapp.domain.repository.AuthAccount
import com.example.checkinoutapp.domain.repository.AuthRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest



/*

class CustomerRepositoryImpl: CustomerRepository {
    override fun getCurrentUserId(): String? {
        return try {
            Firebase.auth.currentUser?.uid
        } catch (e: Exception) {
            null
        }
    }

 */
class AuthRepositoryImpl : AuthRepository {
    override fun getCurrentUserId(): String? {
        return try {
            Firebase.auth.currentUser?.uid
        } catch (e: Exception) {
            null
        }
    }

    override fun readAccountFlow(): Flow<AuthAccount?> = channelFlow {
        try {
            Firebase.auth.authStateChanged.collectLatest { user ->
                if (user != null) {
                    send(AuthAccount(uid = user.uid, email = user.email ?: ""))
                } else {
                    send(null)
                }
            }
        } catch (e: Exception) {
            send(null)
        }
    }

    override suspend fun signIn(email: String, password: String): RequestState<Unit> {
        return try {
            Firebase.auth.signInWithEmailAndPassword(email = email, password = password)
            RequestState.Success(data = Unit)
        } catch (e: Exception) {
            RequestState.Error(e.toUserMessage())
        }
    }

    override suspend fun signOut(): RequestState<Unit> {
        return try {
            Firebase.auth.signOut()
            RequestState.Success(data = Unit)
        } catch (e: Exception) {
            RequestState.Error("Error while signing out: ${e.toUserMessage()}")
        }
    }
}
