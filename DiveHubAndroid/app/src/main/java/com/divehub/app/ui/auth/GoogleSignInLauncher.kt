package com.divehub.app.ui.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.launch

data class GoogleSignInAccountPayload(
    val idToken: String,
    val email: String?,
    val givenName: String?,
    val familyName: String?,
)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Starts **Sign in with Google** via [Credential Manager] + [GoogleIdTokenCredential] (modern API).
 * [webClientId] is the OAuth 2.0 **Web application** client ID (Firebase / Google Cloud), same as backend verification.
 */
@Composable
fun rememberGoogleSignInStarter(
    webClientId: String,
    onResult: (Result<GoogleSignInAccountPayload>) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    return remember(webClientId, activity, credentialManager, scope) {
        {
            val act = activity
            val wid = webClientId.trim()
            when {
                act == null -> onResult(Result.failure(IllegalStateException("no_activity")))
                wid.isEmpty() -> onResult(Result.failure(IllegalStateException("web_client_id_missing")))
                else -> {
                    scope.launch {
                        runCatching {
                            val googleIdOption = GetGoogleIdOption.Builder()
                                .setServerClientId(wid)
                                .setFilterByAuthorizedAccounts(false)
                                .build()
                            val request = GetCredentialRequest.Builder()
                                .addCredentialOption(googleIdOption)
                                .build()
                            val result = credentialManager.getCredential(
                                context = act,
                                request = request,
                            )
                            val raw = result.credential
                            val google = when (raw) {
                                is CustomCredential ->
                                    if (raw.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                                        GoogleIdTokenCredential.createFrom(raw.data)
                                    } else {
                                        null
                                    }
                                else -> null
                            } ?: error("unexpected_credential")
                            val token = google.idToken
                            if (token.isBlank()) error("id_token_missing")
                            GoogleSignInAccountPayload(
                                idToken = token,
                                email = google.id.takeIf { it.contains("@") },
                                givenName = google.givenName,
                                familyName = google.familyName,
                            )
                        }.fold(
                            onSuccess = { onResult(Result.success(it)) },
                            onFailure = { e ->
                                when (e) {
                                    is GetCredentialCancellationException -> onResult(Result.failure(e))
                                    is GoogleIdTokenParsingException -> onResult(Result.failure(e))
                                    is GetCredentialException -> onResult(Result.failure(e))
                                    else -> onResult(Result.failure(e))
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
