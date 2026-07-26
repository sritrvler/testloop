package com.testloop.app.data

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
import java.time.LocalDate
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DemoAuthRepository : AuthRepository {
    private val _account = MutableStateFlow<Account?>(null)
    override val account: StateFlow<Account?> = _account.asStateFlow()
    private val _authError = MutableStateFlow<String?>(null)
    override val authError: StateFlow<String?> = _authError.asStateFlow()

    override suspend fun beginGoogleSignIn(): Result<Unit> {
        _authError.value = null
        val account = Account("priya", "", "priya@example.com")
        _account.value = account
        return Result.success(Unit)
    }

    override fun clearAuthError() { _authError.value = null }

    override suspend fun setUsername(username: String): Result<Unit> {
        val current = _account.value ?: return Result.failure(IllegalStateException("No account"))
        _account.value = current.copy(username = username.trim())
        return Result.success(Unit)
    }

    override suspend fun completeTrackingSetup(): Result<Unit> {
        val current = _account.value ?: return Result.failure(IllegalStateException("No account"))
        _account.value = current.copy(trackingSetupComplete = true)
        return Result.success(Unit)
    }

    override suspend fun deleteAccount(): Result<Unit> { _account.value = null; return Result.success(Unit) }
}

class DemoTestLoopRepository : TestLoopRepository {
    private val demoUserId = "priya"
    private val today = LocalDate.now()
    private val focus = OwnedApp("own-focus", "Focus Flow", "com.focusflow.timer", "https://play.google.com/store/apps/details?id=com.focusflow.timer", "https://play.google.com/apps/testing/com.focusflow.timer", "focus", "campaign-focus")
    private val pantry = OwnedApp("own-pantry", "Pantry Pal", "io.pal.pantry", "https://play.google.com/store/apps/details?id=io.pal.pantry", "https://play.google.com/apps/testing/io.pal.pantry", "pantry", "campaign-pantry")
    private val _ownedApps = MutableStateFlow(listOf(focus, pantry))
    override val ownedApps: StateFlow<List<OwnedApp>> = _ownedApps.asStateFlow()
    private val _campaigns = MutableStateFlow(listOf(
        Campaign("campaign-focus", focus.id, 12, 8, today.minusDays(9)),
        Campaign("campaign-pantry", pantry.id, 12, 12, today.minusDays(5))
    ))
    override val campaigns: StateFlow<List<Campaign>> = _campaigns.asStateFlow()

    private val budgetBee = PublicApp("public-budget", "Budget Bee", "app.budgetbee.mobile", "alex", "alex_dev", "https://play.google.com/store/apps/details?id=app.budgetbee.mobile", "https://play.google.com/apps/testing/app.budgetbee.mobile", 12, 8, "Create a monthly budget, then edit one category.", "Android 12 or newer is helpful.", "Finance", "Posted today", "budget")
    private val trailNotes = PublicApp("public-trail", "Trail Notes", "app.trailnotes.mobile", "sana", "sana_codes", "https://play.google.com/store/apps/details?id=app.trailnotes.mobile", "https://play.google.com/apps/testing/app.trailnotes.mobile", 8, 3, "Save a short note and test search while offline.", "Offline behaviour is the focus.", "Travel", "Posted yesterday", "trail")
    private val recipeRoute = PublicApp("public-recipe", "Recipe Route", "dev.reciperoute.app", "matt", "mattbuilds", "https://play.google.com/store/apps/details?id=dev.reciperoute.app", "https://play.google.com/apps/testing/dev.reciperoute.app", 10, 7, "Save a recipe and use the shopping list.", "Please try the first-use flow.", "Lifestyle", "Posted 2 hours ago", "recipe")
    private fun ownPreview(app: OwnedApp, requested: Int, joined: Int, instructions: String) = PublicApp(
        id = app.id,
        title = app.title,
        packageName = app.packageName,
        ownerId = demoUserId,
        ownerUsername = "priya",
        installUrl = app.installUrl,
        optInUrl = app.optInUrl,
        requestedTesters = requested,
        joinedTesterCount = joined,
        instructions = instructions,
        postedLabel = "Your listing",
        iconSeed = app.iconSeed,
        campaignId = app.campaignId,
        targetDays = 14,
        activeTesterCount = joined,
        reservedTesterCount = joined
    )
    private val _discoverApps = MutableStateFlow(listOf(ownPreview(focus, 12, 8, "Preview how Focus Flow appears to testers."), ownPreview(pantry, 12, 12, "Preview how Pantry Pal appears to testers."), budgetBee, trailNotes, recipeRoute))
    override val discoverApps: StateFlow<List<PublicApp>> = _discoverApps.asStateFlow()

    private fun syncOwnedListing(appId: String) {
        val campaign = _campaigns.value.firstOrNull { it.ownedAppId == appId } ?: return
        _discoverApps.value = _discoverApps.value.map { listing ->
            if (listing.id != appId) listing else listing.copy(
                requestedTesters = campaign.requestedTesters,
                joinedTesterCount = campaign.joinedTesterCount,
                activeTesterCount = campaign.activeTesterCount,
                reservedTesterCount = campaign.reservedTesterCount,
                testingStartedAt = campaign.testingStartedAt,
                testingCompletedAt = campaign.testingCompletedAt,
                closedTestConfirmedAt = campaign.closedTestConfirmedAt,
                riskDecision = campaign.riskDecision,
                replacementOpen = campaign.replacementOpen,
                replacementJoinedAt = campaign.replacementJoinedAt,
                restartConfirmedAt = campaign.restartConfirmedAt
            )
        }
    }

    private fun adjustPublicListing(appId: String, reservedDelta: Int = 0, joinedDelta: Int = 0) {
        _discoverApps.value = _discoverApps.value.map { listing ->
            if (listing.id != appId) listing else listing.copy(
                joinedTesterCount = (listing.joinedTesterCount + joinedDelta).coerceAtLeast(0),
                activeTesterCount = (listing.activeTesterCount + joinedDelta).coerceAtLeast(0),
                reservedTesterCount = (listing.reservedTesterCount + reservedDelta).coerceAtLeast(0)
            )
        }
    }

    private val installedOpened = DirectionSignal(InstallSignal.INSTALLED, ActivitySignal.OPENED_TODAY, 12)
    private val installedPending = DirectionSignal(InstallSignal.INSTALLED, ActivitySignal.NOT_DETECTED)
    private val notInstalled = DirectionSignal(InstallSignal.NOT_INSTALLED, ActivitySignal.UNAVAILABLE)
    private val _commitments = MutableStateFlow(listOf(
        ReciprocalCommitment("pair-alex", budgetBee.id, focus.id, "alex", "alex_dev", today.minusDays(5), youOweSignal = installedPending, theyOweSignal = installedPending),
        ReciprocalCommitment("pair-sana", trailNotes.id, pantry.id, "sana", "sana_codes", today.minusDays(8), youOweSignal = installedOpened, theyOweSignal = installedOpened),
        ReciprocalCommitment("pair-matt", recipeRoute.id, focus.id, "matt", "mattbuilds", today.minusDays(2), youOweSignal = installedOpened, theyOweSignal = notInstalled)
    ))
    override val commitments: StateFlow<List<ReciprocalCommitment>> = _commitments.asStateFlow()
    private val _notifications = MutableStateFlow(listOf(
        AppNotification("n-install", NotificationKind.RECIPROCAL, "Install Budget Bee to start", "You joined alex_dev's commitment, but Budget Bee is not installed yet.", "12 min ago", true, "pair-alex"),
        AppNotification("n-reminder", NotificationKind.REMINDER, "alex_dev sent a test reminder", "Open Budget Bee when you can to keep today's commitment on track.", "1 h ago", true, "pair-alex"),
        AppNotification("n-focus", NotificationKind.INSTALL, "Install no longer detected", "TestLoop could not detect Focus Flow on mattbuilds's device. Open the commitment to review.", "Yesterday", false, "pair-matt", focus.id),
        AppNotification("n-account", NotificationKind.ACCOUNT, "Keep your TestLoop account active", "Your inactive account is scheduled for deletion in 24 hours.", "Earlier", false)
    ))
    override val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()
    private val _bookmarks = MutableStateFlow<List<AppBookmark>>(emptyList())
    override val bookmarks: StateFlow<List<AppBookmark>> = _bookmarks.asStateFlow()
    private val _usageAccess = MutableStateFlow(UsageAccessStatus())
    override val usageAccess: StateFlow<UsageAccessStatus> = _usageAccess.asStateFlow()

    override suspend fun addOwnedApp(app: OwnedApp, campaign: Campaign): Result<Unit> {
        _ownedApps.value += app
        _campaigns.value += campaign
        return Result.success(Unit)
    }

    override suspend fun updateCampaignTesterCount(campaignId: String, requestedTesters: Int): Result<Unit> {
        val campaign = _campaigns.value.firstOrNull { it.id == campaignId }
            ?: return Result.failure(IllegalArgumentException("Campaign not found"))
        val enrolledTesterCount = campaign.reservedTesterCount
        val minimumTesterTarget = enrolledTesterCount.coerceAtLeast(1)
        if (requestedTesters !in minimumTesterTarget..100) {
            return Result.failure(
                IllegalArgumentException(
                    if (enrolledTesterCount == 0) "Choose between 1 and 100 testers"
                    else "The target cannot be lower than the $enrolledTesterCount testers already enrolled"
                )
            )
        }
        _campaigns.value = _campaigns.value.map {
            if (it.id == campaignId) it.copy(requestedTesters = requestedTesters) else it
        }
        syncOwnedListing(campaign.ownedAppId)
        return Result.success(Unit)
    }

    override suspend fun delistOwnedApp(appId: String): Result<Unit> {
        _ownedApps.value = _ownedApps.value.filterNot { it.id == appId }
        _campaigns.value = _campaigns.value.filterNot { it.ownedAppId == appId }
        return Result.success(Unit)
    }

    override suspend fun markOwnedAppPublished(appId: String): Result<Unit> {
        _ownedApps.value = _ownedApps.value.map { app ->
            if (app.id == appId && app.playStorePublishedAt == null) app.copy(playStorePublishedAt = Instant.now()) else app
        }
        return Result.success(Unit)
    }

    override suspend fun confirmClosedTesting(campaignId: String): Result<Unit> {
        val campaign = _campaigns.value.firstOrNull { it.id == campaignId }
            ?: return Result.failure(IllegalArgumentException("Campaign not found"))
        if (campaign.activeTesterCount < campaign.requestedTesters) {
            return Result.failure(IllegalStateException("All required tester installs must be detected first"))
        }
        val now = Instant.now()
        _campaigns.value = _campaigns.value.map {
            if (it.id == campaignId) it.copy(closedTestConfirmedAt = it.closedTestConfirmedAt ?: now, testingStartedAt = it.testingStartedAt ?: now) else it
        }
        return Result.success(Unit)
    }

    override suspend fun chooseCampaignRiskDecision(campaignId: String, decision: CampaignRiskDecision, affectedCommitmentId: String?): Result<Unit> {
        val campaign = _campaigns.value.firstOrNull { it.id == campaignId }
            ?: return Result.failure(IllegalArgumentException("Campaign not found"))
        if (decision == CampaignRiskDecision.REOPEN) {
            _commitments.value.firstOrNull {
                it.currentUserOwnAppId == campaign.ownedAppId && it.lifecycle == CommitmentLifecycle.LEFT
            } ?: return Result.failure(IllegalStateException("No released commitment is available for replacement"))
            _campaigns.value = _campaigns.value.map {
                if (it.id == campaignId) it.copy(
                    riskDecision = CampaignRiskDecision.REOPEN,
                    replacementOpen = true,
                    replacementJoinedAt = null,
                    restartConfirmedAt = null
                ) else it
            }
            syncOwnedListing(campaign.ownedAppId)
            return Result.success(Unit)
        }
        if (decision == CampaignRiskDecision.RESTART) {
            val affected = affectedCommitmentId?.let { id -> _commitments.value.firstOrNull { it.commitmentId == id } }
                ?: return Result.failure(IllegalArgumentException("Choose the tester to replace"))
            if (affected.lifecycle != CommitmentLifecycle.ACTIVE) {
                return Result.failure(IllegalArgumentException("Choose an active commitment to replace"))
            }
            _commitments.value = _commitments.value.map { if (it.commitmentId == affected.commitmentId) it.copy(lifecycle = CommitmentLifecycle.LEFT) else it }
        }
        _campaigns.value = _campaigns.value.map {
            if (it.id == campaignId) it.copy(riskDecision = decision, replacementOpen = decision == CampaignRiskDecision.RESTART) else it
        }
        syncOwnedListing(campaign.ownedAppId)
        if (decision == CampaignRiskDecision.RESTART) {
            _notifications.value += AppNotification("n-replacement-$campaignId", NotificationKind.RECIPROCAL, "Replacement tester needed", "The owner released your commitment for a replacement tester. Your previous commitment is no longer active.", "Just now", true, affectedCommitmentId)
        }
        return Result.success(Unit)
    }

    override suspend fun finalizeCampaignRestart(campaignId: String): Result<Unit> {
        val campaign = _campaigns.value.firstOrNull { it.id == campaignId }
            ?: return Result.failure(IllegalArgumentException("Campaign not found"))
        if (campaign.riskDecision !in setOf(CampaignRiskDecision.RESTART, CampaignRiskDecision.REOPEN) || campaign.activeTesterCount < campaign.requestedTesters) {
            return Result.failure(IllegalStateException("The replacement count is not restored yet"))
        }
        val now = Instant.now()
        _campaigns.value = _campaigns.value.map {
            if (it.id == campaignId) it.copy(replacementOpen = false, replacementJoinedAt = now, restartConfirmedAt = now, testingStartedAt = now, testingCompletedAt = null, progressResetAt = now) else it
        }
        syncOwnedListing(campaign.ownedAppId)
        _notifications.value += AppNotification("n-restarted-$campaignId", NotificationKind.RECIPROCAL, "Testing restarted at Day 1", "The owner confirmed a restart. Your new Day 1 begins today; the new end date is ${today.plusDays(campaign.targetDays.toLong() - 1)}.", "Just now", true, ownedAppId = campaign.ownedAppId)
        return Result.success(Unit)
    }

    override suspend fun syncUsageAccess(hasAccess: Boolean): Result<Unit> {
        _usageAccess.value = if (hasAccess) UsageAccessStatus() else UsageAccessStatus(revokedAt = Instant.now(), deadlineAt = Instant.now().plusSeconds(60 * 60))
        return Result.success(Unit)
    }

    override suspend fun joinCampaign(publicAppId: String, ownAppId: String?): Result<ReciprocalCommitment> {
        val publicApp = _discoverApps.value.find { it.id == publicAppId } ?: return Result.failure(IllegalArgumentException("Campaign not found"))
        if (publicApp.ownerId == demoUserId) return Result.failure(IllegalArgumentException("You cannot join your own app."))
        if (_commitments.value.any { it.appBeingTestedByCurrentUserId == publicAppId }) return Result.failure(IllegalStateException("Already joined"))
        if (ownAppId != null && _commitments.value.any {
                it.currentUserOwnAppId == ownAppId &&
                    it.lifecycle in setOf(CommitmentLifecycle.PENDING_SETUP, CommitmentLifecycle.ACTIVE)
            }) return Result.failure(IllegalStateException("That app is already being offered in another active commitment"))
        val pair = ReciprocalCommitment("pair-$publicAppId", publicAppId, ownAppId, publicApp.ownerId, publicApp.ownerUsername, today, youOweSignal = DirectionSignal(InstallSignal.UNAVAILABLE, ActivitySignal.UNAVAILABLE), theyOweSignal = ownAppId?.let { DirectionSignal(InstallSignal.UNAVAILABLE, ActivitySignal.UNAVAILABLE) }, lifecycle = CommitmentLifecycle.PENDING_SETUP)
        _commitments.value += pair
        adjustPublicListing(publicAppId, reservedDelta = 1)
        return Result.success(pair)
    }

    override suspend fun bookmarkApp(publicAppId: String): Result<Unit> {
        if (_discoverApps.value.none { it.id == publicAppId }) return Result.failure(IllegalArgumentException("App not found"))
        if (_bookmarks.value.none { it.appId == publicAppId }) _bookmarks.value += AppBookmark(publicAppId, Instant.now())
        return Result.success(Unit)
    }

    override suspend fun removeBookmark(publicAppId: String): Result<Unit> {
        _bookmarks.value = _bookmarks.value.filterNot { it.appId == publicAppId }
        return Result.success(Unit)
    }

    override suspend fun refresh(): Result<Unit> = Result.success(Unit)

    override suspend fun reportInstallStatus(commitmentId: String): Result<Unit> {
        val existing = _commitments.value.firstOrNull { it.commitmentId == commitmentId }
            ?: return Result.failure(IllegalArgumentException("Commitment not found"))
        if (existing.lifecycle == CommitmentLifecycle.LEFT) {
            return Result.failure(IllegalStateException("A left commitment is terminal"))
        }
        val wasInstalled = existing.youOweSignal.install == InstallSignal.INSTALLED
        _commitments.value = _commitments.value.map { commitment ->
            if (commitment.commitmentId == commitmentId) commitment.copy(youOweSignal = commitment.youOweSignal.copy(install = InstallSignal.INSTALLED), lifecycle = CommitmentLifecycle.ACTIVE) else commitment
        }
        val installedAppId = _commitments.value.firstOrNull { it.commitmentId == commitmentId }?.appBeingTestedByCurrentUserId
        if (installedAppId != null && !wasInstalled) {
            _bookmarks.value = _bookmarks.value.filterNot { it.appId == installedAppId }
            adjustPublicListing(installedAppId, joinedDelta = 1)
        }
        val ownAppId = _commitments.value.firstOrNull { it.commitmentId == commitmentId }?.currentUserOwnAppId
        if (ownAppId != null) {
            _campaigns.value = _campaigns.value.map { campaign ->
                if (campaign.ownedAppId == ownAppId && _commitments.value.count { it.currentUserOwnAppId == ownAppId && it.lifecycle == CommitmentLifecycle.ACTIVE } > campaign.joinedTesterCount) {
                    campaign.copy(joinedTesterCount = campaign.joinedTesterCount + 1)
                } else campaign
            }
            _campaigns.value.firstOrNull { it.ownedAppId == ownAppId }?.let { syncOwnedListing(ownAppId) }
        }
        return Result.success(Unit)
    }

    override suspend fun leaveCommitment(commitmentId: String): Result<Unit> {
        val pair = _commitments.value.firstOrNull { it.commitmentId == commitmentId }
            ?: return Result.failure(IllegalArgumentException("Commitment not found"))
        if (pair.lifecycle == CommitmentLifecycle.COMPLETED) {
            return Result.failure(IllegalStateException("A completed commitment cannot be left"))
        }
        if (pair.lifecycle == CommitmentLifecycle.LEFT) return Result.success(Unit)
        _commitments.value = _commitments.value.map {
            if (it.commitmentId == commitmentId) it.copy(lifecycle = CommitmentLifecycle.LEFT) else it
        }
        val appId = pair.currentUserOwnAppId
        if (appId != null) {
            _campaigns.value = _campaigns.value.map { campaign ->
                if (campaign.ownedAppId != appId) campaign else campaign.copy(
                    joinedTesterCount = if (pair.lifecycle == CommitmentLifecycle.ACTIVE) (campaign.joinedTesterCount - 1).coerceAtLeast(0) else campaign.joinedTesterCount,
                    activeTesterCount = if (pair.lifecycle == CommitmentLifecycle.ACTIVE) (campaign.activeTesterCount - 1).coerceAtLeast(0) else campaign.activeTesterCount,
                    reservedTesterCount = (campaign.reservedTesterCount - 1).coerceAtLeast(0),
                    riskDecision = CampaignRiskDecision.CONTINUE,
                    replacementOpen = false
                )
            }
            syncOwnedListing(appId)
        }
        adjustPublicListing(pair.appBeingTestedByCurrentUserId, reservedDelta = -1, joinedDelta = if (pair.lifecycle == CommitmentLifecycle.ACTIVE) -1 else 0)
        return Result.success(Unit)
    }

    override suspend fun sendReminder(commitmentId: String): Result<Unit> {
        _commitments.value = _commitments.value.map { if (it.commitmentId == commitmentId) it.copy(youCanSendReminder = false) else it }
        return Result.success(Unit)
    }

    override suspend fun markNotificationsRead() { _notifications.value = _notifications.value.map { it.copy(isUnread = false) } }
}
