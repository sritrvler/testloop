package com.testloop.app.data

import android.content.Context
import com.testloop.app.domain.Account
import com.testloop.app.domain.AppBookmark
import com.testloop.app.domain.ActivitySignal
import com.testloop.app.domain.AppNotification
import com.testloop.app.domain.Campaign
import com.testloop.app.domain.CampaignRiskDecision
import com.testloop.app.domain.CommitmentLifecycle
import com.testloop.app.domain.DirectionSignal
import com.testloop.app.domain.InstallSignal
import com.testloop.app.domain.NotificationKind
import com.testloop.app.domain.OwnedApp
import com.testloop.app.domain.PublicApp
import com.testloop.app.domain.ReciprocalCommitment
import com.testloop.app.domain.UsageAccessStatus
import com.testloop.app.tracking.AndroidInstallStatusReporter
import com.testloop.app.tracking.AndroidUsageAccessReporter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc

/**
 * Production repository for the bounded MVP. Every write is authorized by Supabase RLS.
 * Device reporting is install-only and is performed only for package names in commitments.
 */
class SupabaseTestLoopRepository(
    context: Context,
    private val auth: AuthRepository
) : TestLoopRepository {
    private val supabase = SupabaseClientProvider.client
    private val installReporter = AndroidInstallStatusReporter(context)
    private val usageReporter = AndroidUsageAccessReporter(context)
    private val deviceCheckEnabledCommitmentIds = mutableSetOf<String>()

    private val _ownedApps = MutableStateFlow<List<OwnedApp>>(emptyList())
    override val ownedApps: StateFlow<List<OwnedApp>> = _ownedApps.asStateFlow()
    private val _campaigns = MutableStateFlow<List<Campaign>>(emptyList())
    override val campaigns: StateFlow<List<Campaign>> = _campaigns.asStateFlow()
    private val _discoverApps = MutableStateFlow<List<PublicApp>>(emptyList())
    override val discoverApps: StateFlow<List<PublicApp>> = _discoverApps.asStateFlow()
    private val _commitments = MutableStateFlow<List<ReciprocalCommitment>>(emptyList())
    override val commitments: StateFlow<List<ReciprocalCommitment>> = _commitments.asStateFlow()
    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    override val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()
    private val _bookmarks = MutableStateFlow<List<AppBookmark>>(emptyList())
    override val bookmarks: StateFlow<List<AppBookmark>> = _bookmarks.asStateFlow()
    private val _usageAccess = MutableStateFlow(UsageAccessStatus())
    override val usageAccess: StateFlow<UsageAccessStatus> = _usageAccess.asStateFlow()

    override suspend fun refresh(): Result<Unit> = refreshInternal()

    private suspend fun refreshInternal(): Result<Unit> = runCatching {
        val userId = requireUserId()
        val ownRows = supabase.from("owned_apps").select {
            filter { eq("owner_id", userId) }
        }.decodeList<OwnedAppRow>()
        // Archived rows remain in the database for history, but are no longer
        // active apps in the owner's portfolio.
        val activeOwnRows = ownRows.filter { it.lifecycle == "published" }
        val ownIds = activeOwnRows.map { it.id }
        val campaignRows = ownIds.mapNotNull { appId ->
            supabase.from("campaigns").select {
                filter { eq("owned_app_id", appId) }
            }.decodeList<CampaignRow>().firstOrNull()
        }
        // A listing permission problem must not hide the publisher's own apps.
        // The database migration grants the helper permission; this fallback
        // keeps My apps usable while Discover is temporarily unavailable.
        val listings = supabase.from("public_app_listings").select().decodeList<PublicListingRow>()
        var commitmentRows = supabase.from("reciprocal_commitments").select().decodeList<CommitmentRow>()
        val profileRows = supabase.from("profiles").select().decodeList<TesterProfileRow>()

        // Map only commitments that belong to this signed-in user, either as
        // tester or publisher. Install detection is the only device signal.
        commitmentRows = supabase.from("reciprocal_commitments").select().decodeList()
        val ownedCampaignIds = campaignRows.mapTo(mutableSetOf()) { it.id }
        val visibleCommitmentRows = commitmentRows.filter { row ->
            row.testerId == userId || row.campaignId in ownedCampaignIds
        }
        visibleCommitmentRows
            .filter { it.lifecycle == "active" || it.id in deviceCheckEnabledCommitmentIds }
            .forEach { commitment ->
            runCatching { reportInstallStatusInternal(commitment, listings, userId) }
        }
        // Install activation happens in the database trigger, so read the
        // commitments and observations again before publishing UI state.
        commitmentRows = supabase.from("reciprocal_commitments").select().decodeList()
        val updatedVisibleCommitmentRows = commitmentRows.filter { row ->
            row.testerId == userId || row.campaignId in ownedCampaignIds
        }

        _ownedApps.value = activeOwnRows.map { it.toOwnedApp(campaignRows.firstOrNull { campaign -> campaign.ownedAppId == it.id }?.id) }
        val listingsByCampaign = listings.associateBy { it.campaignId }
        // Keep joined listings in this cache so commitment details can still
        // resolve the app name and both Play links. Discover filters joined
        // listings at the UI boundary, while own listings are excluded here.
        // Keep the owner's published rows in the same cache as public rows so
        // Discover can render an exact preview. The UI gates them as preview
        // only; other users still receive the backend view's normal rows.
        _discoverApps.value = listings.map { it.toPublicApp() }

        val installRows = updatedVisibleCommitmentRows.flatMap { commitment ->
            supabase.from("commitment_install_states").select {
                filter {
                    eq("commitment_id", commitment.id)
                }
            }.decodeList<InstallStateRow>()
        }
        _campaigns.value = campaignRows.map { campaign ->
            val campaignCommitments = updatedVisibleCommitmentRows.filter { it.campaignId == campaign.id }
            val reserved = campaignCommitments.count { it.lifecycle == "pending_setup" || it.lifecycle == "active" }
            val active = campaignCommitments
                .filter { it.lifecycle == "active" }
                .filter { commitment ->
                    installRows.any {
                        it.commitmentId == commitment.id &&
                            it.reporterId == commitment.testerId &&
                            it.targetOwnedAppId == campaign.ownedAppId &&
                            it.status == "installed"
                    }
                }
                .distinctBy { it.testerId }
                .size
            val listing = listingsByCampaign[campaign.id]
            val isOwner = campaign.id in ownedCampaignIds
            campaign.toCampaign(if (isOwner) active else (listing?.joinedTesterCount ?: active)).copy(
                releaseApprovedAt = activeOwnRows.firstOrNull { it.id == campaign.ownedAppId }?.playStorePublishedAt?.let(Instant::parse),
                reservedTesterCount = if (isOwner) reserved else listing?.let { (it.requestedTesters - it.slotsRemaining).coerceAtLeast(0) } ?: reserved,
                replacementOpen = campaign.replacementOpen && (if (isOwner) campaign.requestedTesters - reserved else listing?.slotsRemaining ?: 0) > 0
            )
        }
        val openRows = updatedVisibleCommitmentRows.flatMap { commitment ->
            runCatching {
                supabase.from("commitment_daily_open_states").select { filter { eq("commitment_id", commitment.id) } }
                    .decodeList<DailyOpenRow>()
            }.getOrDefault(emptyList())
        }
        val reminderRows = updatedVisibleCommitmentRows.flatMap { commitment ->
            runCatching {
                supabase.from("reminders").select { filter { eq("commitment_id", commitment.id) } }
                    .decodeList<ReminderRow>()
            }.getOrDefault(emptyList())
        }
        val mappedCommitments = updatedVisibleCommitmentRows.map { row ->
            row.toCommitment(listings, _ownedApps.value, profileRows, installRows, openRows, userId, reminderRows)
        }
        _commitments.value = mappedCommitments
            .groupBy { Pair(it.otherUserId, it.currentUserOwnAppId) }
            .values
            .map { duplicates ->
                duplicates.maxWithOrNull(
                    compareBy<ReciprocalCommitment> { if (it.lifecycle == CommitmentLifecycle.ACTIVE) 1 else 0 }
                        .thenBy { installRank(it.youOweSignal.install) }
                        .thenBy { activityRank(it.youOweSignal.activity) }
                ) ?: duplicates.first()
            }
        _notifications.value = supabase.from("notifications").select {
            filter { eq("recipient_id", userId) }
        }.decodeList<NotificationRow>().map { it.toNotification() }
        _bookmarks.value = supabase.from("app_bookmarks").select {
            filter { eq("member_id", userId) }
        }.decodeList<BookmarkRow>().map { it.toBookmark() }

    }

    override suspend fun addOwnedApp(app: OwnedApp, campaign: Campaign): Result<Unit> = runCatching {
        val userId = requireUserId()
        val inserted = supabase.from("owned_apps").insert(
            OwnedAppInsert(
                ownerId = userId,
                title = app.title.trim(),
                packageName = app.packageName.trim(),
                installUrl = app.installUrl.trim(),
                optInUrl = app.optInUrl?.trim()?.ifBlank { null },
                groupUrl = app.groupUrl.trim(),
                messagingId = app.messagingId.trim().ifBlank { null },
                iconSeed = app.iconSeed,
                details = app.details.trim(),
                category = "Productivity",
                lifecycle = "published"
            )
        ) { select() }.decodeSingle<OwnedAppRow>()
        supabase.from("campaigns").insert(
            CampaignInsert(
                ownedAppId = inserted.id,
                requestedTesters = campaign.requestedTesters,
                instructions = campaign.instructions.trim()
            )
        )
        refresh().getOrThrow()
    }

    override suspend fun updateCampaignTesterCount(campaignId: String, requestedTesters: Int): Result<Unit> = runCatching {
        val campaign = _campaigns.value.firstOrNull { it.id == campaignId }
        val enrolledTesterCount = campaign?.reservedTesterCount ?: 0
        val minimumTesterTarget = enrolledTesterCount.coerceAtLeast(1)
        require(requestedTesters in minimumTesterTarget..100) {
            if (enrolledTesterCount == 0) "Choose between 1 and 100 testers"
            else "The target cannot be lower than the $enrolledTesterCount testers already enrolled"
        }
        supabase.from("campaigns").update(CampaignTesterCountUpdate(requestedTesters)) {
            filter { eq("id", campaignId) }
        }
        refresh().getOrThrow()
    }

    override suspend fun delistOwnedApp(appId: String): Result<Unit> = runCatching {
        val userId = requireUserId()
        supabase.from("campaigns").update(CampaignLifecycleUpdate(lifecycle = "closed", closedAt = Instant.now().toString())) {
            filter { eq("owned_app_id", appId) }
        }
        supabase.from("owned_apps").update(OwnedAppLifecycleUpdate(lifecycle = "archived")) {
            filter { eq("id", appId); eq("owner_id", userId) }
        }
        refresh().getOrThrow()
    }

    override suspend fun markOwnedAppPublished(appId: String): Result<Unit> = runCatching {
        val userId = requireUserId()
        supabase.from("owned_apps").update(OwnedAppPublicationUpdate(playStorePublishedAt = Instant.now().toString())) {
            filter { eq("id", appId); eq("owner_id", userId) }
        }
        refresh().getOrThrow()
    }

    override suspend fun confirmClosedTesting(campaignId: String): Result<Unit> = runCatching {
        requireUserId()
        supabase.postgrest.rpc("confirm_closed_testing", CampaignActionParams(campaignId))
            .decodeList<CampaignActionRow>().firstOrNull() ?: error("Closed testing could not be confirmed")
        refresh().getOrThrow()
    }

    override suspend fun chooseCampaignRiskDecision(campaignId: String, decision: CampaignRiskDecision, affectedCommitmentId: String?): Result<Unit> = runCatching {
        requireUserId()
        if (decision == CampaignRiskDecision.REOPEN) {
            supabase.postgrest.rpc("reopen_campaign_replacement", CampaignActionParams(campaignId))
                .decodeList<CampaignActionRow>().firstOrNull() ?: error("Replacement slot could not be reopened")
            refresh().getOrThrow()
            return@runCatching
        }
        require(decision != CampaignRiskDecision.RESTART || !affectedCommitmentId.isNullOrBlank()) {
            "Choose the tester commitment to release before requesting a replacement"
        }
        supabase.postgrest.rpc(
            "choose_campaign_risk_decision",
            CampaignRiskDecisionParams(campaignId, decision.rpcValue, affectedCommitmentId)
        ).decodeList<CampaignActionRow>().firstOrNull() ?: error("Campaign decision could not be saved")
        refresh().getOrThrow()
    }

    override suspend fun finalizeCampaignRestart(campaignId: String): Result<Unit> = runCatching {
        requireUserId()
        supabase.postgrest.rpc("finalize_campaign_restart", CampaignActionParams(campaignId))
            .decodeList<CampaignActionRow>().firstOrNull() ?: error("Campaign restart could not be confirmed")
        refresh().getOrThrow()
    }

    override suspend fun syncUsageAccess(hasAccess: Boolean): Result<Unit> = runCatching {
        requireUserId()
        val status = supabase.postgrest.rpc("record_usage_access_status", UsageAccessStatusParams(hasAccess))
            .decodeList<UsageAccessStatusRow>()
            .firstOrNull()
            ?: error("Could not update Usage Access status")
        _usageAccess.value = status.toDomain()
    }

    override suspend fun joinCampaign(publicAppId: String, ownAppId: String?): Result<ReciprocalCommitment> = runCatching {
        val userId = requireUserId()
        val offeredAppId = ownAppId?.takeIf { id -> _ownedApps.value.any { it.id == id } }
        val listing = _discoverApps.value.firstOrNull { it.id == publicAppId }
            ?: error("That listing is no longer available")
        if (listing.ownerId == userId) {
            error("You cannot join your own app.")
        }
        val listingRow = supabase.from("public_app_listings").select {
            filter { eq("id", listing.id) }
        }.decodeSingle<PublicListingRow>()
        val inserted = supabase.from("reciprocal_commitments").insert(
            CommitmentInsert(
                campaignId = listingRow.campaignId,
                testerId = userId,
                currentUserOwnedAppId = offeredAppId,
                targetDays = 14,
                lifecycle = "pending_setup"
            )
        ) { select() }.decodeSingle<CommitmentRow>()
        // The ready screen performs the first device check explicitly. Once
        // that check runs, normal refreshes keep checking this commitment.
        refresh().getOrThrow()
        _commitments.value.firstOrNull { it.commitmentId == inserted.id }
            ?: inserted.toCommitment(listOf(listingRow), _ownedApps.value, emptyList(), emptyList(), emptyList(), userId)
    }

    override suspend fun bookmarkApp(publicAppId: String): Result<Unit> = runCatching {
        val userId = requireUserId()
        supabase.from("app_bookmarks").upsert(
            BookmarkInsert(memberId = userId, ownedAppId = publicAppId)
        ) { onConflict = "member_id,owned_app_id" }
        // Publish the successful save immediately. A full refresh can be slow
        // and should not make a bookmark appear to have failed when the user
        // opens the Bookmarks filter right away.
        if (_bookmarks.value.none { it.appId == publicAppId }) {
            _bookmarks.value = _bookmarks.value + AppBookmark(publicAppId, Instant.now())
        }
        refresh()
        Unit
    }

    override suspend fun removeBookmark(publicAppId: String): Result<Unit> = runCatching {
        val userId = requireUserId()
        supabase.from("app_bookmarks").delete {
            filter { eq("member_id", userId); eq("owned_app_id", publicAppId) }
        }
        _bookmarks.value = _bookmarks.value.filterNot { it.appId == publicAppId }
        refresh()
        Unit
    }

    override suspend fun reportInstallStatus(commitmentId: String): Result<Unit> = runCatching {
        val userId = requireUserId()
        val row = supabase.from("reciprocal_commitments").select {
            filter { eq("id", commitmentId); eq("tester_id", userId) }
        }.decodeSingle<CommitmentRow>()
        require(row.lifecycle in setOf("pending_setup", "active")) { "A left commitment is terminal" }
        val listingRows = supabase.from("public_app_listings").select {
            filter { eq("campaign_id", row.campaignId) }
        }.decodeList<PublicListingRow>()
        reportInstallStatusInternal(row, listingRows, userId)
        // Only enable the existing automatic refresh loop after the initial
        // device check has completed successfully.
        deviceCheckEnabledCommitmentIds += commitmentId
        refresh().getOrThrow()
    }

    override suspend fun leaveCommitment(commitmentId: String): Result<Unit> = runCatching {
        requireUserId()
        supabase.postgrest.rpc("leave_commitment", LeaveCommitmentParams(commitmentId))
            .decodeList<LeaveCommitmentRow>()
            .firstOrNull()
            ?: error("Commitment could not be left")
        deviceCheckEnabledCommitmentIds -= commitmentId
        // The public listing view derives reserved slots from lifecycle, so a
        // refresh immediately exposes the freed slot to this member.
        refresh().getOrThrow()
    }

    override suspend fun sendReminder(commitmentId: String): Result<Unit> = runCatching {
        val userId = requireUserId()
        val pair = _commitments.value.firstOrNull { it.commitmentId == commitmentId }
            ?: error("Commitment not found")
        val targetAppId = pair.currentUserOwnAppId ?: error("One-way commitments do not support reciprocal reminders")
        val appTitle = _ownedApps.value.firstOrNull { it.id == targetAppId }?.title ?: "your test app"
        supabase.from("reminders").insert(
            ReminderInsert(
                commitmentId = commitmentId,
                senderId = userId,
                recipientId = pair.otherUserId,
                targetOwnedAppId = targetAppId,
                message = "Quick check-in: open TestLoop when you have a moment so we can update your testing status for $appTitle. It helps keep the shared test on track."
            )
        )
        refresh().getOrThrow()
    }

    override suspend fun markNotificationsRead() {
        val userId = auth.account.value?.id ?: return
        _notifications.value.filter { it.isUnread }.forEach { notification ->
            runCatching {
                supabase.from("notifications").update(NotificationReadUpdate(readAt = Instant.now().toString())) {
                    filter { eq("id", notification.id); eq("recipient_id", userId) }
                }
            }
        }
        _notifications.value = _notifications.value.map { it.copy(isUnread = false) }
    }

    private suspend fun reportInstallStatusInternal(commitment: CommitmentRow, listings: List<PublicListingRow>, userId: String) {
        val sourceListing = listings.firstOrNull { it.campaignId == commitment.campaignId } ?: run {
            return
        }
        val listing = if (sourceListing.ownerId == userId) {
            listings.firstOrNull { it.id == commitment.currentUserOwnedAppId }
        } else {
            sourceListing
        } ?: run {
            return
        }
        val installed = installReporter.isInstalled(listing.packageName)
        supabase.from("commitment_install_states").upsert(
            InstallStateUpsert(
                commitmentId = commitment.id,
                reporterId = userId,
                targetOwnedAppId = listing.id,
                status = if (installed) "installed" else "not_installed"
            )
        ) {
            onConflict = "commitment_id,reporter_id,target_owned_app_id"
        }
        if (installed) {
            supabase.from("app_bookmarks").delete {
                filter { eq("member_id", userId); eq("owned_app_id", listing.id) }
            }
        }
        usageReporter.openedOn(listing.packageName)?.let { opened ->
            supabase.from("commitment_daily_open_states").upsert(
                DailyOpenUpsert(commitment.id, userId, listing.id, LocalDate.now().toString(), opened)
            ) { onConflict = "commitment_id,reporter_id,target_owned_app_id,activity_date" }
        }
    }

    private fun requireUserId(): String = auth.account.value?.id?.takeIf { it.isNotBlank() }
        ?: error("Please sign in again")

}

@Serializable
private data class OwnedAppRow(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    val title: String,
    @SerialName("package_name") val packageName: String,
    @SerialName("install_url") val installUrl: String,
    @SerialName("opt_in_url") val optInUrl: String? = null,
    @SerialName("group_url") val groupUrl: String? = null,
    @SerialName("messaging_id") val messagingId: String? = null,
    @SerialName("icon_seed") val iconSeed: String = "",
    val details: String = "",
    val category: String = "Productivity",
    @SerialName("play_store_published_at") val playStorePublishedAt: String? = null,
    val lifecycle: String = "published"
) {
    fun toOwnedApp(campaignId: String?, details: String = this.details) = OwnedApp(id, title, packageName, installUrl, optInUrl, iconSeed, campaignId.orEmpty(), details, playStorePublishedAt?.let(Instant::parse), groupUrl.orEmpty(), messagingId.orEmpty())
}

@Serializable
private data class OwnedAppInsert(
    @SerialName("owner_id") val ownerId: String,
    val title: String,
    @SerialName("package_name") val packageName: String,
    @SerialName("install_url") val installUrl: String,
    @SerialName("opt_in_url") val optInUrl: String?,
    @SerialName("group_url") val groupUrl: String,
    @SerialName("messaging_id") val messagingId: String?,
    @SerialName("icon_seed") val iconSeed: String,
    val details: String,
    val category: String,
    val lifecycle: String
)

@Serializable
private data class OwnedAppLifecycleUpdate(val lifecycle: String)

@Serializable
private data class OwnedAppPublicationUpdate(
    @SerialName("play_store_published_at") val playStorePublishedAt: String
)

@Serializable
private data class UsageAccessStatusParams(
    @SerialName("has_access") val hasAccess: Boolean
)

@Serializable
private data class UsageAccessStatusRow(
    @SerialName("usage_access_revoked_at") val revokedAt: String? = null,
    @SerialName("usage_access_deadline") val deadlineAt: String? = null,
    @SerialName("listing_deactivated") val listingDeactivated: Boolean = false
) {
    fun toDomain() = UsageAccessStatus(revokedAt?.let(Instant::parse), deadlineAt?.let(Instant::parse), listingDeactivated)
}

@Serializable
private data class CampaignRow(
    val id: String,
    @SerialName("owned_app_id") val ownedAppId: String,
    @SerialName("requested_testers") val requestedTesters: Int,
    val instructions: String = "",
    @SerialName("started_on") val startedOn: String,
    val lifecycle: String = "active",
    @SerialName("target_days") val targetDays: Int = 14,
    @SerialName("testing_started_at") val testingStartedAt: String? = null,
    @SerialName("testing_completed_at") val testingCompletedAt: String? = null,
    @SerialName("progress_reset_at") val progressResetAt: String? = null,
    @SerialName("closed_test_confirmed_at") val closedTestConfirmedAt: String? = null,
    @SerialName("risk_decision") val riskDecision: String? = null,
    @SerialName("replacement_open") val replacementOpen: Boolean = false,
    @SerialName("replacement_joined_at") val replacementJoinedAt: String? = null,
    @SerialName("restart_confirmed_at") val restartConfirmedAt: String? = null
) {
    fun toCampaign(activeTesterCount: Int = 0) = Campaign(id, ownedAppId, requestedTesters, activeTesterCount, parseDate(startedOn), isActive = lifecycle == "active", instructions = instructions, targetDays = targetDays, activeTesterCount = activeTesterCount, testingStartedAt = testingStartedAt?.let(Instant::parse), testingCompletedAt = testingCompletedAt?.let(Instant::parse), progressResetAt = progressResetAt?.let(Instant::parse), closedTestConfirmedAt = closedTestConfirmedAt?.let(Instant::parse), riskDecision = riskDecision.toRiskDecision(), replacementOpen = replacementOpen, replacementJoinedAt = replacementJoinedAt?.let(Instant::parse), restartConfirmedAt = restartConfirmedAt?.let(Instant::parse))
}

@Serializable
private data class CampaignInsert(
    @SerialName("owned_app_id") val ownedAppId: String,
    @SerialName("requested_testers") val requestedTesters: Int,
    val instructions: String,
    val lifecycle: String = "active"
)

@Serializable
private data class CampaignTesterCountUpdate(
    @SerialName("requested_testers") val requestedTesters: Int
)

@Serializable
private data class CampaignActionParams(
    @SerialName("target_campaign_id") val targetCampaignId: String
)

@Serializable
private data class CampaignRiskDecisionParams(
    @SerialName("target_campaign_id") val targetCampaignId: String,
    val decision: String,
    @SerialName("affected_commitment_id") val affectedCommitmentId: String? = null
)

@Serializable
private data class CampaignActionRow(
    val id: String
)

@Serializable
private data class CampaignLifecycleUpdate(
    val lifecycle: String,
    @SerialName("closed_at") val closedAt: String
)

@Serializable
private data class PublicListingRow(
    val id: String,
    val title: String,
    @SerialName("package_name") val packageName: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("owner_username") val ownerUsername: String,
    @SerialName("install_url") val installUrl: String,
    @SerialName("opt_in_url") val optInUrl: String? = null,
    @SerialName("group_url") val groupUrl: String? = null,
    @SerialName("campaign_id") val campaignId: String,
    @SerialName("requested_testers") val requestedTesters: Int,
    @SerialName("joined_tester_count") val joinedTesterCount: Int,
    @SerialName("slots_remaining") val slotsRemaining: Int = requestedTesters,
    val instructions: String,
    val details: String = "",
    val category: String = "Productivity",
    @SerialName("started_on") val startedOn: String = "",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("target_days") val targetDays: Int = 14,
    @SerialName("testing_started_at") val testingStartedAt: String? = null,
    @SerialName("testing_completed_at") val testingCompletedAt: String? = null,
    @SerialName("progress_reset_at") val progressResetAt: String? = null,
    @SerialName("play_store_published_at") val releaseApprovedAt: String? = null,
    @SerialName("closed_test_confirmed_at") val closedTestConfirmedAt: String? = null,
    @SerialName("risk_decision") val riskDecision: String? = null,
    @SerialName("replacement_open") val replacementOpen: Boolean = false,
    @SerialName("replacement_joined_at") val replacementJoinedAt: String? = null,
    @SerialName("restart_confirmed_at") val restartConfirmedAt: String? = null,
    @SerialName("messaging_id") val messagingId: String? = null
) {
    fun toPublicApp() = PublicApp(id, title, packageName, ownerId, ownerUsername, installUrl, optInUrl, requestedTesters, joinedTesterCount, instructions, details, category, postedLabel(createdAt), title, campaignId, targetDays, joinedTesterCount, testingStartedAt?.let(Instant::parse), testingCompletedAt?.let(Instant::parse), progressResetAt?.let(Instant::parse), releaseApprovedAt?.let(Instant::parse), groupUrl.orEmpty(), (requestedTesters - slotsRemaining).coerceAtLeast(0), messagingId.orEmpty(), closedTestConfirmedAt?.let(Instant::parse), riskDecision.toRiskDecision(), replacementOpen, replacementJoinedAt?.let(Instant::parse), restartConfirmedAt?.let(Instant::parse))
}

@Serializable
private data class CommitmentRow(
    val id: String,
    @SerialName("campaign_id") val campaignId: String,
    @SerialName("tester_id") val testerId: String,
    @SerialName("current_user_owned_app_id") val currentUserOwnedAppId: String? = null,
    @SerialName("target_days") val targetDays: Int = 14,
    val lifecycle: String = "pending_setup",
    @SerialName("joined_at") val joinedAt: String
) {
    fun toCommitment(listings: List<PublicListingRow>, ownApps: List<OwnedApp>, profiles: List<TesterProfileRow>, summaries: List<InstallStateRow>, openRows: List<DailyOpenRow>, userId: String, reminders: List<ReminderRow> = emptyList()): ReciprocalCommitment {
        val listing = listings.firstOrNull { it.campaignId == campaignId }
        val ownedCampaignApp = ownApps.firstOrNull { it.campaignId == campaignId }
        val isPublisher = listing?.ownerId == userId || ownedCampaignApp != null
        val otherUserId = if (isPublisher) testerId else listing?.ownerId.orEmpty()
        val otherUser = if (isPublisher) profiles.firstOrNull { it.id == testerId }?.username ?: "tester" else listing?.ownerUsername ?: "member"
        val ownerAppId = listing?.id ?: ownedCampaignApp?.id
        return ReciprocalCommitment(
            commitmentId = id,
            appBeingTestedByCurrentUserId = if (isPublisher) currentUserOwnedAppId.orEmpty() else listing?.id.orEmpty(),
            currentUserOwnAppId = if (isPublisher) ownerAppId else currentUserOwnedAppId,
            otherUserId = otherUserId,
            otherUsername = otherUser,
            joinedOn = parseDate(joinedAt),
            targetDays = targetDays,
            youOweSignal = summaries.signalFor(id, userId, if (isPublisher) currentUserOwnedAppId else listing?.id, openRows),
            theyOweSignal = if (isPublisher) summaries.signalFor(id, testerId, ownerAppId, openRows) else currentUserOwnedAppId?.let { summaries.signalFor(id, listing?.ownerId.orEmpty(), it, openRows) },
            youCanSendReminder = reminders.none { it.commitmentId == id && it.senderId == userId && it.reminderDate == LocalDate.now(ZoneOffset.UTC).toString() },
            lifecycle = lifecycle.toCommitmentLifecycle()
        )
    }
}

@Serializable
private data class TesterProfileRow(
    val id: String,
    val username: String = ""
)

@Serializable
private data class CommitmentInsert(
    @SerialName("campaign_id") val campaignId: String,
    @SerialName("tester_id") val testerId: String,
    @SerialName("current_user_owned_app_id") val currentUserOwnedAppId: String?,
    @SerialName("target_days") val targetDays: Int,
    val lifecycle: String
)

@Serializable
private data class LeaveCommitmentParams(
    @SerialName("target_commitment_id") val targetCommitmentId: String
)

@Serializable
private data class LeaveCommitmentRow(
    @SerialName("commitment_id") val commitmentId: String,
    val lifecycle: String,
    @SerialName("left_at") val leftAt: String
)

@Serializable
private data class InstallStateRow(
    @SerialName("commitment_id") val commitmentId: String,
    @SerialName("reporter_id") val reporterId: String,
    @SerialName("target_owned_app_id") val targetOwnedAppId: String,
    val status: String
)

@Serializable
private data class InstallStateUpsert(
    @SerialName("commitment_id") val commitmentId: String,
    @SerialName("reporter_id") val reporterId: String,
    @SerialName("target_owned_app_id") val targetOwnedAppId: String,
    val status: String
)

@Serializable
private data class BookmarkRow(
    @SerialName("owned_app_id") val ownedAppId: String,
    @SerialName("created_at") val createdAt: String
) {
    fun toBookmark() = AppBookmark(ownedAppId, runCatching { Instant.parse(createdAt) }.getOrDefault(Instant.now()))
}

@Serializable
private data class BookmarkInsert(
    @SerialName("member_id") val memberId: String,
    @SerialName("owned_app_id") val ownedAppId: String
)

@Serializable
private data class DailyOpenRow(
    @SerialName("commitment_id") val commitmentId: String,
    @SerialName("reporter_id") val reporterId: String,
    @SerialName("target_owned_app_id") val targetOwnedAppId: String,
    @SerialName("activity_date") val activityDate: String,
    val opened: Boolean
)

@Serializable
private data class DailyOpenUpsert(
    @SerialName("commitment_id") val commitmentId: String,
    @SerialName("reporter_id") val reporterId: String,
    @SerialName("target_owned_app_id") val targetOwnedAppId: String,
    @SerialName("activity_date") val activityDate: String,
    val opened: Boolean
)

@Serializable
private data class ReminderInsert(
    @SerialName("commitment_id") val commitmentId: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("recipient_id") val recipientId: String,
    @SerialName("target_owned_app_id") val targetOwnedAppId: String,
    val message: String
)

@Serializable
private data class ReminderRow(
    @SerialName("commitment_id") val commitmentId: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("reminder_date") val reminderDate: String
)

@Serializable
private data class NotificationRow(
    val id: String,
    val kind: String,
    val title: String,
    val message: String,
    @SerialName("read_at") val readAt: String? = null,
    @SerialName("commitment_id") val commitmentId: String? = null,
    @SerialName("owned_app_id") val ownedAppId: String? = null,
    @SerialName("created_at") val createdAt: String = ""
) {
    fun toNotification() = AppNotification(id, kind.toNotificationKind(), title, message, relativeTime(createdAt), readAt == null, commitmentId, ownedAppId)
}

@Serializable
private data class NotificationReadUpdate(@SerialName("read_at") val readAt: String)

private fun List<InstallStateRow>.signalFor(commitmentId: String, reporterId: String, targetAppId: String?, openRows: List<DailyOpenRow>): DirectionSignal {
    val row = firstOrNull { it.commitmentId == commitmentId && it.reporterId == reporterId && it.targetOwnedAppId == targetAppId }
    val opened = openRows.firstOrNull { it.commitmentId == commitmentId && it.reporterId == reporterId && it.targetOwnedAppId == targetAppId && it.activityDate == LocalDate.now().toString() }?.opened
    return DirectionSignal(install = row?.status.toInstallSignal(), activity = when (opened) { true -> ActivitySignal.OPENED_TODAY; false -> ActivitySignal.NOT_DETECTED; null -> ActivitySignal.UNAVAILABLE }, lastCheckedLabel = if (row == null) "Not checked yet" else "Checked today")
}

private fun String?.toInstallSignal() = when (this) {
    "installed" -> InstallSignal.INSTALLED
    "not_installed" -> InstallSignal.NOT_INSTALLED
    else -> InstallSignal.UNAVAILABLE
}

private fun installRank(signal: InstallSignal): Int = when (signal) {
    InstallSignal.INSTALLED -> 2
    InstallSignal.NOT_INSTALLED -> 1
    InstallSignal.UNAVAILABLE -> 0
}

private fun activityRank(signal: ActivitySignal): Int = when (signal) {
    ActivitySignal.OPENED_TODAY -> 2
    ActivitySignal.NOT_DETECTED -> 1
    ActivitySignal.UNAVAILABLE -> 0
}

private fun String.toCommitmentLifecycle() = when (this) {
    "active" -> CommitmentLifecycle.ACTIVE
    "completed" -> CommitmentLifecycle.COMPLETED
    "left" -> CommitmentLifecycle.LEFT
    else -> CommitmentLifecycle.PENDING_SETUP
}

private fun String?.toRiskDecision() = when (this) {
    "continue" -> CampaignRiskDecision.CONTINUE
    "restart" -> CampaignRiskDecision.RESTART
    "reopen" -> CampaignRiskDecision.REOPEN
    "defer" -> CampaignRiskDecision.DECIDE_LATER
    else -> null
}

private val CampaignRiskDecision.rpcValue: String
    get() = when (this) {
        CampaignRiskDecision.CONTINUE -> "continue"
        CampaignRiskDecision.RESTART -> "restart"
        CampaignRiskDecision.REOPEN -> "reopen"
        CampaignRiskDecision.DECIDE_LATER -> "defer"
    }

private fun String.toNotificationKind() = when (this) {
    "install" -> NotificationKind.INSTALL
    "reminder" -> NotificationKind.REMINDER
    "account" -> NotificationKind.ACCOUNT
    else -> NotificationKind.RECIPROCAL
}

private fun parseDate(value: String): LocalDate = runCatching {
    if (value.length == 10) LocalDate.parse(value) else Instant.parse(value).atZone(ZoneOffset.UTC).toLocalDate()
}.getOrDefault(LocalDate.now(ZoneOffset.UTC))

private fun postedLabel(createdAt: String): String = if (createdAt.isBlank()) "Posted recently" else "Posted recently"
private fun relativeTime(createdAt: String): String = if (createdAt.isBlank()) "Recently" else "Recently"
