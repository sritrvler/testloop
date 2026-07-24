package com.testloop.app.data

import android.content.Intent
import com.testloop.app.BuildConfig
import com.testloop.app.domain.Account
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.ExternalAuthAction
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val AUTH_SCHEME = "testloop"
private const val AUTH_HOST = "auth-callback"

object SupabaseClientProvider {
    val client = createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY) {
        install(Auth) {
            scheme = AUTH_SCHEME
            host = AUTH_HOST
            flowType = FlowType.PKCE
            defaultExternalAuthAction = ExternalAuthAction.CustomTabs(
                intentModifier = {
                    // Do not leave the OAuth tab underneath MainActivity after the
                    // callback returns; pressing Back should stay within the app.
                    addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                }
            )
        }
        install(Postgrest)
    }
}

class SupabaseAuthRepository : AuthRepository {
    private val supabase = SupabaseClientProvider.client
    private val _account = MutableStateFlow<Account?>(null)
    override val account: StateFlow<Account?> = _account.asStateFlow()
    private val _authError = MutableStateFlow<String?>(null)
    override val authError: StateFlow<String?> = _authError.asStateFlow()

    suspend fun awaitInitialization() {
        supabase.auth.awaitInitialization()
        _account.value = supabase.auth.currentUserOrNull()?.toAccount(supabase)
    }

    suspend fun handleDeepLink(intent: Intent) {
        supabase.handleDeeplinks(
            intent,
            onSessionSuccess = {
                _authError.value = null
                _account.value = supabase.auth.currentUserOrNull()?.toAccountWithoutProfile()
            },
            onError = { error ->
            _authError.value = if (error.message?.contains("cancel", ignoreCase = true) == true) {
                "Sign-in was cancelled. Tap Continue with Google when you're ready to try again."
            } else {
                "We couldn't complete sign-in. Check your connection and try again."
            }
            }
        )
        // Do not replace a session restored by handleDeeplinks with a stale null value.
        // The callback may finish the PKCE exchange just after the deep-link handler returns.
        supabase.auth.currentUserOrNull()?.let { user ->
            _account.value = user.toAccount(supabase)
        }
    }

    override suspend fun beginGoogleSignIn(): Result<Unit> = runCatching {
        _authError.value = null
        // Browser OAuth uses the Google client configured in Supabase rather than
        // binding sign-in to this APK's certificate. This covers Google Play signed builds.
        // handleDeepLink() receives the completed session asynchronously.
        supabase.auth.signInWith(Google)
    }

    override fun clearAuthError() { _authError.value = null }

    override suspend fun setUsername(username: String): Result<Unit> {
        val user = supabase.auth.currentUserOrNull() ?: return Result.failure(IllegalStateException("Please sign in again"))
        val clean = username.trim()
        if (!clean.matches(Regex("[A-Za-z0-9_]{3,20}"))) {
            return Result.failure(IllegalArgumentException("Use 3 to 20 letters, numbers, or underscores."))
        }

        return try {
            // Save the profile first. This prevents Auth metadata from being updated when
            // the username is rejected by the unique index.
            supabase.from("profiles").update(ProfileUpdate(username = clean)) {
                filter { eq("id", user.id) }
            }
            supabase.auth.updateUser {
                data { put("username", kotlinx.serialization.json.JsonPrimitive(clean)) }
            }
            _account.value = user.toAccount(supabase).copy(username = clean)
            Result.success(Unit)
        } catch (error: Throwable) {
            if (error.hasUsernameConflict()) {
                Result.failure(IllegalArgumentException("That username is already in use. Please choose another one."))
            } else {
                Result.failure(IllegalStateException("Could not save your username. Please try again."))
            }
        }
    }

    override suspend fun completeTrackingSetup(): Result<Unit> = runCatching {
        val user = supabase.auth.currentUserOrNull() ?: error("Please sign in again")
        supabase.from("profiles").update(ProfileUpdate(trackingSetupComplete = true)) {
            filter { eq("id", user.id) }
        }
        _account.value = user.toAccount(supabase).copy(trackingSetupComplete = true)
    }

    override suspend fun deleteAccount(): Result<Unit> = runCatching {
        supabase.auth.signOut()
        _account.value = null
    }

}

private suspend fun UserInfo.toAccount(supabase: io.github.jan.supabase.SupabaseClient): Account {
    val basic = toAccountWithoutProfile()
    val profile = runCatching {
        supabase.from("profiles").select { filter { eq("id", id) } }.decodeList<ProfileRow>().firstOrNull()
    }.getOrNull()
    return basic.copy(username = profile?.username ?: basic.username, trackingSetupComplete = profile?.trackingSetupComplete == true)
}

private fun UserInfo.toAccountWithoutProfile(): Account {
    val username = userMetadata?.get("username")?.toString()?.trim('"').orEmpty()
    return Account(id = id, username = username, email = email.orEmpty())
}

private fun Throwable.hasUsernameConflict(): Boolean =
    generateSequence(this) { it.cause }.any { current ->
        val text = "${current::class.qualifiedName} ${current.message.orEmpty()}"
        text.contains("profiles_username_lower_key", ignoreCase = true) ||
            text.contains("duplicate key", ignoreCase = true)
    }

@Serializable
private data class ProfileRow(
    val username: String? = null,
    @SerialName("tracking_setup_complete") val trackingSetupComplete: Boolean = false
)

@Serializable
private data class ProfileUpdate(
    val username: String? = null,
    @SerialName("tracking_setup_complete") val trackingSetupComplete: Boolean? = null
)
