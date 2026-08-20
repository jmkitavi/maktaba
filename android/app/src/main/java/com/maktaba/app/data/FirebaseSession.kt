package com.maktaba.app.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import com.maktaba.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

object FirebaseSession {
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val functions: FirebaseFunctions by lazy { FirebaseFunctions.getInstance() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        if (BuildConfig.USE_FIREBASE_EMULATORS) {
            auth.useEmulator("10.0.2.2", 9099)
            functions.useEmulator("10.0.2.2", 5001)
            FirebaseFirestore.getInstance().useEmulator("10.0.2.2", 8080)
            FirebaseStorage.getInstance().useEmulator("10.0.2.2", 9199)
        }
    }

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    fun addAuthStateListener(listener: FirebaseAuth.AuthStateListener) {
        auth.addAuthStateListener(listener)
    }

    fun removeAuthStateListener(listener: FirebaseAuth.AuthStateListener) {
        auth.removeAuthStateListener(listener)
    }

    suspend fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
        ensureProfile()
    }

    suspend fun register(displayName: String, email: String, password: String) {
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val user = requireNotNull(result.user)
        user.updateProfile(userProfileChangeRequest { this.displayName = displayName.trim() }).await()
        ensureProfile(displayName.trim())
    }

    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    fun signOut() {
        val uid = auth.currentUser?.uid
        scope.launch {
            if (uid != null) {
                runCatching {
                    LibraryRepository.unregisterDeviceToken(uid, FirebaseMessaging.getInstance().token.await())
                }
            }
            LibraryRepository.stop()
            auth.signOut()
        }
    }

    private suspend fun ensureProfile(displayName: String? = null) {
        functions.getHttpsCallable("createUserProfileIfNeeded")
            .call(displayName?.let { mapOf("displayName" to it) } ?: emptyMap<String, Any>())
            .await()
    }
}
