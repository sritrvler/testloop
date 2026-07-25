package com.testloop.app.data

import android.content.Context
import com.testloop.app.domain.Account
import com.testloop.app.domain.AppBookmark
import com.testloop.app.domain.ActivitySignal
import com.testloop.app.domain.AppNotification
import com.testloop.app.domain.Campaign
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

    override suspend fun refresh(): Result<Unit> = runCatching {
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
        val visibleCommitmentRows = commitmentRows.filter { row ->
            row.testerId == userId || listings.firstOrNull { it.campaignId == row.campaignId }?.ownerId == userId
        }
        visibleCommitmentRows.forEach { commitment ->
            runCatching { reportInstallStatusInternal(commitment, listings, userId) }
        }
        // Install activation happens in the database trigger, so read the
        // commitments and observations again before publishing UI state.
        commitmentRows = supabase.from("reciprocal_commitments").select().decodeList()

        _ownedApps.value = activeOwnRows.map { it.toOwnedApp(campaignRows.firstOrNull { campaign -> campaign.ownedAppId == it.id }?.id) }
        val listingsByCampaign = listings.associateBy { it.campaignId }
        _campaigns.value = campaignRows.map { campaign ->
            campaign.toCampaign(listingsByCampaign[campaign.id]?.joinedTesterCount ?: 0).copy(
                releaseApprovedAt = activeOwnRows.firstOrNull { it.id == campaign.ownedAppId }?.playStorePublishedAt?.let(Instant::parse),
                reservedTesterCount = listingsByCampaign[campaign.id]?.let { listing ->
                    (listing.requestedTesters - listing.slotsRemaining).coerceAtLeast(0)
                } ?: 0
            )
        }
        // Keep joined listings in this cache so commitment details can still
        // resolve the app name and both Play links. Discover filters joined
        // listings at the UI boundary, while own listings are excluded here.
        _discoverApps.value = listings
            .filter { it.id !in ownIds }
            .map { it.toPublicApp() }

        val installRows = visibleCommitmentRows.flatMap { commitment ->
            supabase.from("commitment_install_states").select {
                filter {
                    eq("commitment_id", commitment.id)
                }
            }.decodeList<InstallStateRow>()
        }
        val openRows = visibleCommitmentRows.flatMap { commitment ->
            runCatching {
                supabase.from("commitment_daily_open_states").select { filter { eq("commitment_id", commitment.id) } }
                    .decodeList<DailyOpenRow>()
            }.getOrDefault(emptyList())
        }
        val reminderRows = visibleCommitmentRows.flatMap { commitment ->
            runCatching {
                supabase.from("reminders").select { filter { eq("commitment_id", commitment.id) } }
                    .decodeList<ReminderRow>()
            }.getOrDefault(emptyList())
        }
        val mappedCommitments = visibleCommitmentRows.map { row ->
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
        require(requestedTesters in 12..100) { "Choose between 12 and 100 testers" }
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
        refresh().getOrThrow()
        _commitments.value.firstOrNull { it.commitmentId == inserted.id }
            ?: inserted.toCommitment(listOf(listingRow), _ownedApps.value, emptyList(), emptyList(), emptyList(), userId)
    }

    override suspend fun bookmarkApp(publicAppId: String): Result<Unit> = runCatching {
        val userId = requireUserId()
        supabase.from("app_bookmarks").upsert(
            BookmarkInsert(memberId = userId, ownedAppId = publicAppId)
        ) { onConflict = "member_id,owned_app_id" }
        refresh().getOrThrow()
    }

    override suspend fun removeBookmark(publicAppId: String): Result<Unit> = runCatching {
        val userId = requireUserId()
        supabase.from("app_bookmarks").delete {
            filter { eq("member_id", userId); eq("owned_app_id", publicAppId) }
        }
        refresh().getOrThrow()
    }

    override suspend fun reportInstallStatus(commitmentId: String): Result<Unit> = runCatching {
        val userId = requireUserId()
        val row = supabase.from("reciprocal_commitments").select {
            filter { eq("id", commitmentId); eq("tester_id", userId) }
        }.decodeSingle<CommitmentRow>()
        val listingRows = supabase.from("public_app_listings").select {
            filter { eq("campaign_id", row.campaignId) }
        }.decodeList<PublicListingRow>()
        reportInstallStatusInternal(row, listingRows, userId)
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
    @SerialName("icon_seed") val iconSeed: String = "",
    val details: String = "",
    val category: String = "Productivity",
    @SerialName("play_store_published_at") val playStorePublishedAt: String? = null,
    val lifecycle: String = "published"
) {
    fun toOwnedApp(campaignId: String?, details: String = this.details) = OwnedApp(id, title, packageName, installUrl, optInUrl, iconSeed, campaignId.orEmpty(), details, playStorePublishedAt?.let(Instant::parse), groupUrl.orEmpty())
}

@Serializable
private data class OwnedAppInsert(
    @SerialName("owner_id") val ownerId: String,
    val title: String,
    @SerialName("package_name") val packageName: String,
    @SerialName("install_url") val installUrl: String,
    @SerialName("opt_in_url") val optInUrl: String?,
    @SerialName("group_url") val groupUrl: String,
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
    @SerialName("progress_reset_at") val progressResetAt: String? = null
) {
    fun toCampaign(activeTesterCount: Int = 0) = Campaign(id, ownedAppId, requestedTesters, activeTesterCount, parseDate(startedOn), isActive = lifecycle == "active", instructions = instructions, targetDays = targetDays, activeTesterCount = activeTesterCount, testingStartedAt = testingStartedAt?.let(Instant::parse), testingCompletedAt = testingCompletedAt?.let(Instant::parse), progressResetAt = progressResetAt?.let(Instant::parse))
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
    @SerialName("play_store_published_at") val releaseApprovedAt: String? = null
) {
    fun toPublicApp() = PublicApp(id, title, packageName, ownerId, ownerUsername, installUrl, optInUrl, requestedTesters, joinedTesterCount, instructions, details, category, postedLabel(createdAt), title, campaignId, targetDays, joinedTesterCount, testingStartedAt?.let(Instant::parse), testingCompletedAt?.let(Instant::parse), progressResetAt?.let(Instant::parse), releaseApprovedAt?.let(Instant::parse), groupUrl.orEmpty(), (requestedTesters - slotsRemaining).coerceAtLeast(0))
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
        val isPublisher = listing?.ownerId == userId
        val otherUserId = if (isPublisher) testerId else listing?.ownerId.orEmpty()
        val otherUser = if (isPublisher) profiles.firstOrNull { it.id == testerId }?.username ?: "tester" else listing?.ownerUsername ?: "member"
        return ReciprocalCommitment(
            commitmentId = id,
            appBeingTestedByCurrentUserId = if (isPublisher) currentUserOwnedAppId.orEmpty() else listing?.id.orEmpty(),
            currentUserOwnAppId = if (isPublisher) listing?.id else currentUserOwnedAppId,
            otherUserId = otherUserId,
            otherUsername = otherUser,
            joinedOn = parseDate(joinedAt),
            targetDays = targetDays,
            youOweSignal = summaries.signalFor(id, userId, if (isPublisher) currentUserOwnedAppId else listing?.id, openRows),
            theyOweSignal = if (isPublisher) summaries.signalFor(id, testerId, listing?.id, openRows) else currentUserOwnedAppId?.let { summaries.signalFor(id, listing?.ownerId.orEmpty(), it, openRows) },
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
