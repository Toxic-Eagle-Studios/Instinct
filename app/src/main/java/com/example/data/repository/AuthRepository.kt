package com.example.data.repository

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val TAG = "AuthRepository"

class AuthRepository(private val context: Context) {
  private val auth: FirebaseAuth = Firebase.auth
  private val credentialManager: CredentialManager = CredentialManager.create(context)

  private val _currentUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
  val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

  private val _isGuestMode = MutableStateFlow<Boolean>(false)
  val isGuestMode: StateFlow<Boolean> = _isGuestMode.asStateFlow()

  init {
    auth.addAuthStateListener { firebaseAuth ->
      _currentUser.value = firebaseAuth.currentUser
    }
  }

  fun setGuestMode(enabled: Boolean) {
    _isGuestMode.value = enabled
  }

  fun getEffectiveUserId(): String {
    return _currentUser.value?.uid ?: "guest_user"
  }

  // Silent Auto-Sign-In on App Startup
  fun attemptAutoSignIn(
    onSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
  ) {
    if (auth.currentUser != null) {
      onSuccess()
      return
    }

    val clientId = try {
      context.getString(R.string.default_web_client_id)
    } catch (e: Exception) {
      Log.w(TAG, "Web client ID not found", e)
      onUnauthenticated()
      return
    }

    val googleIdOption = GetGoogleIdOption.Builder()
      .setFilterByAuthorizedAccounts(true)
      .setServerClientId(clientId)
      .setAutoSelectEnabled(true)
      .build()

    val request = GetCredentialRequest.Builder()
      .addCredentialOption(googleIdOption)
      .build()

    scope.launch {
      try {
        val result = credentialManager.getCredential(context, request)
        val credential = result.credential
        if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
          val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
          val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
          auth.signInWithCredential(authCredential).await()
          _isGuestMode.value = false
          onSuccess()
        } else {
          onUnauthenticated()
        }
      } catch (e: Exception) {
        onUnauthenticated()
      }
    }
  }

  // Interactive Sign-In Button Flow
  fun signInWithGoogle(
    activity: Activity,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
    onCancelled: () -> Unit,
    scope: CoroutineScope
  ) {
    val clientId = try {
      context.getString(R.string.default_web_client_id)
    } catch (e: Exception) {
      onError("Google Sign-In configuration missing: default_web_client_id")
      return
    }

    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder()
      .addCredentialOption(signInOption)
      .build()

    scope.launch {
      try {
        val result = credentialManager.getCredential(activity, request)
        val credential = result.credential
        if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
          val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
          val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
          auth.signInWithCredential(authCredential).await()
          _isGuestMode.value = false
          onSuccess()
        } else {
          onError("Unexpected credential type")
        }
      } catch (e: GetCredentialCancellationException) {
        Log.w(TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
        onCancelled()
      } catch (e: Exception) {
        Log.e(TAG, "Google Sign-In failed", e)
        onError(e.localizedMessage ?: "Sign in failed")
      }
    }
  }

  fun signOut(scope: CoroutineScope, onComplete: () -> Unit) {
    auth.signOut()
    _isGuestMode.value = false
    scope.launch {
      try {
        credentialManager.clearCredentialState(ClearCredentialStateRequest())
      } catch (e: Exception) {
        Log.e(TAG, "Failed to clear credential state", e)
      } finally {
        onComplete()
      }
    }
  }
}
