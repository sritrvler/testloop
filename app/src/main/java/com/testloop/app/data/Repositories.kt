package com.testloop.app.data

import com.testloop.app.domain.Account
import com.testloop.app.domain.AppNotification
import com.testloop.app.domain.AppBookmark
import com.testloop.app.domain.Campaign
import com.testloop.app.domain.OwnedApp
import com.testloop.app.domain.PublicApp
import com.testloop.app.domain.ReciprocalCommitment
import kotlinx.coroutines.flow.StateFlow

/** Google identity is never Play enrollment proof. */
interface AuthRepository {
    val account: StateFlow<Account?>
    val authError: StateFlow<String?>
    /** Starts browser OAuth; the auth deep link delivers the signed-in session. */
    suspend fun beginGoogleSignIn(): Result<Unit>
    fun clearAuthError()
    suspend fun setUsername(username: String): Result<Unit>
    suspend fun completeTrackingSetup(): Result<Unit>
    suspend fun deleteAccount(): Result<Unit>
}

/** Supabase boundary. Production writes require row-level authorization and atomic slot allocation. */
interface TestLoopRepository {
    val ownedApps: StateFlow<List<OwnedApp>>
    val campaigns: StateFlow<List<Campaign>>
    val discoverApps: StateFlow<List<PublicApp>>
    val commitments: StateFlow<List<ReciprocalCommitment>>
    val notifications: StateFlow<List<AppNotification>>
    val bookmarks: StateFlow<List<AppBookmark>>
    suspend fun addOwnedApp(app: OwnedApp, campaign: Campaign): Result<Unit>
    suspend fun joinCampaign(publicAppId: String, ownAppId: String?): Result<ReciprocalCommitment>
    suspend fun bookmarkApp(publicAppId: String): Result<Unit>
    suspend fun removeBookmark(publicAppId: String): Result<Unit>
    suspend fun refresh(): Result<Unit>
    suspend fun reportInstallStatus(commitmentId: String): Result<Unit>
    suspend fun sendReminder(commitmentId: String): Result<Unit>
    suspend fun markNotificationsRead()
}
