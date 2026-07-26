package com.testloop.app.domain

import java.time.LocalDate
import java.time.Instant

data class Account(
    val id: String,
    val username: String,
    val email: String,
    val trackingSetupComplete: Boolean = false
)

data class UsageAccessStatus(
    val revokedAt: Instant? = null,
    val deadlineAt: Instant? = null,
    val listingDeactivated: Boolean = false
)

data class OwnedApp(
    val id: String,
    val title: String,
    val packageName: String,
    val installUrl: String,
    val optInUrl: String?,
    val iconSeed: String,
    val campaignId: String,
    val details: String = "",
    val playStorePublishedAt: Instant? = null,
    val groupUrl: String = "",
    val messagingId: String = ""
)

data class Campaign(
    val id: String,
    val ownedAppId: String,
    val requestedTesters: Int,
    val joinedTesterCount: Int,
    val startedOn: LocalDate,
    val isActive: Boolean = true,
    val instructions: String = "",
    val targetDays: Int = 14,
    val activeTesterCount: Int = joinedTesterCount,
    val testingStartedAt: Instant? = null,
    val testingCompletedAt: Instant? = null,
    val progressResetAt: Instant? = null,
    val releaseApprovedAt: Instant? = null,
    val reservedTesterCount: Int = joinedTesterCount,
    val closedTestConfirmedAt: Instant? = null,
    val riskDecision: CampaignRiskDecision? = null,
    val replacementOpen: Boolean = false,
    val replacementJoinedAt: Instant? = null,
    val restartConfirmedAt: Instant? = null
) {
    val slotsRemaining: Int get() = (requestedTesters - reservedTesterCount).coerceAtLeast(0)
    val testingStatus: CampaignTestingStatus
        get() = when {
            testingCompletedAt != null -> CampaignTestingStatus.COMPLETE
            closedTestConfirmedAt == null && activeTesterCount < requestedTesters -> CampaignTestingStatus.WAITING_FOR_TESTERS
            closedTestConfirmedAt == null -> CampaignTestingStatus.READY_FOR_OWNER_CONFIRMATION
            riskDecision in setOf(CampaignRiskDecision.RESTART, CampaignRiskDecision.REOPEN) && restartConfirmedAt == null && activeTesterCount < requestedTesters -> CampaignTestingStatus.RESTART_PENDING
            riskDecision in setOf(CampaignRiskDecision.RESTART, CampaignRiskDecision.REOPEN) && restartConfirmedAt == null -> CampaignTestingStatus.READY_TO_RESTART
            testingStartedAt != null && activeTesterCount < requestedTesters -> CampaignTestingStatus.AT_RISK
            testingStartedAt != null -> CampaignTestingStatus.IN_PROGRESS
            else -> CampaignTestingStatus.WAITING_FOR_TESTERS
        }
    val progress: CampaignProgress get() = CampaignProgress(requestedTesters, activeTesterCount, targetDays, testingStatus, testingStartedAt, releaseApprovedAt, closedTestConfirmedAt, riskDecision, replacementOpen, replacementJoinedAt, restartConfirmedAt)
}

enum class CampaignRiskDecision { CONTINUE, RESTART, REOPEN, DECIDE_LATER }

enum class CampaignTestingStatus { WAITING_FOR_TESTERS, READY_FOR_OWNER_CONFIRMATION, IN_PROGRESS, AT_RISK, RESTART_PENDING, READY_TO_RESTART, COMPLETE }

data class CampaignProgress(
    val requiredTesters: Int,
    val activeTesterCount: Int,
    val targetDays: Int,
    val status: CampaignTestingStatus,
    val testingStartedAt: Instant? = null,
    val releaseApprovedAt: Instant? = null,
    val closedTestConfirmedAt: Instant? = null,
    val riskDecision: CampaignRiskDecision? = null,
    val replacementOpen: Boolean = false,
    val replacementJoinedAt: Instant? = null,
    val restartConfirmedAt: Instant? = null
)

fun CampaignProgress.statusLabel(): String = when (status) {
    CampaignTestingStatus.WAITING_FOR_TESTERS -> "Waiting for ${testerWord(requiredTesters)} to install · $activeTesterCount/$requiredTesters installed"
    CampaignTestingStatus.READY_FOR_OWNER_CONFIRMATION -> "Ready to start · $activeTesterCount/$requiredTesters installed"
    CampaignTestingStatus.AT_RISK -> if (riskDecision == CampaignRiskDecision.CONTINUE) {
        "Continuing current run · $activeTesterCount/$requiredTesters active testers"
    } else {
        "At risk · $activeTesterCount/$requiredTesters active testers"
    }
    CampaignTestingStatus.RESTART_PENDING -> "Replacement tester needed · $activeTesterCount/$requiredTesters active testers"
    CampaignTestingStatus.READY_TO_RESTART -> "Ready to restart · $activeTesterCount/$requiredTesters active testers"
    CampaignTestingStatus.IN_PROGRESS -> "14-day countdown active · $activeTesterCount/$requiredTesters active testers"
    CampaignTestingStatus.COMPLETE -> "$targetDays-day test complete · $activeTesterCount/$requiredTesters active testers"
}

fun testerWord(count: Int): String = if (count == 1) "1 tester" else "$count testers"

fun CampaignProgress.dailyTaskUnlocked(): Boolean = status == CampaignTestingStatus.IN_PROGRESS ||
    status == CampaignTestingStatus.COMPLETE ||
    (status == CampaignTestingStatus.AT_RISK && riskDecision == CampaignRiskDecision.CONTINUE)

fun CampaignProgress.ownerActionRequired(): Boolean = status in setOf(
    CampaignTestingStatus.READY_FOR_OWNER_CONFIRMATION,
    CampaignTestingStatus.AT_RISK,
    CampaignTestingStatus.RESTART_PENDING,
    CampaignTestingStatus.READY_TO_RESTART
) || (status == CampaignTestingStatus.WAITING_FOR_TESTERS && riskDecision == CampaignRiskDecision.CONTINUE)

fun Campaign.portfolioActivityLabel(attention: Int): String = when {
    testingStatus == CampaignTestingStatus.READY_FOR_OWNER_CONFIRMATION -> "Waiting for your confirmation"
    testingStatus == CampaignTestingStatus.AT_RISK -> "At risk · choose what to do"
    testingStatus == CampaignTestingStatus.RESTART_PENDING -> "Replacement tester needed"
    testingStatus == CampaignTestingStatus.READY_TO_RESTART -> "Ready to restart"
    reservedTesterCount == 0 -> "No testers enrolled yet"
    attention == 0 -> "All active today"
    else -> "$attention need attention"
}

data class PublicApp(
    val id: String,
    val title: String,
    val packageName: String,
    val ownerId: String,
    val ownerUsername: String,
    val installUrl: String,
    val optInUrl: String?,
    val requestedTesters: Int,
    val joinedTesterCount: Int,
    val instructions: String,
    val details: String = "",
    val category: String = "Productivity",
    val postedLabel: String = "Posted today",
    val iconSeed: String = title,
    val campaignId: String = "",
    val targetDays: Int = 14,
    val activeTesterCount: Int = joinedTesterCount,
    val testingStartedAt: Instant? = null,
    val testingCompletedAt: Instant? = null,
    val progressResetAt: Instant? = null,
    val releaseApprovedAt: Instant? = null,
    val groupUrl: String = "",
    val reservedTesterCount: Int = joinedTesterCount,
    val messagingId: String = "",
    val closedTestConfirmedAt: Instant? = null,
    val riskDecision: CampaignRiskDecision? = null,
    val replacementOpen: Boolean = false,
    val replacementJoinedAt: Instant? = null,
    val restartConfirmedAt: Instant? = null
) {
    val slotsRemaining: Int get() = (requestedTesters - reservedTesterCount).coerceAtLeast(0)
    val testingStatus: CampaignTestingStatus
        get() = when {
            testingCompletedAt != null -> CampaignTestingStatus.COMPLETE
            closedTestConfirmedAt == null && activeTesterCount < requestedTesters -> CampaignTestingStatus.WAITING_FOR_TESTERS
            closedTestConfirmedAt == null -> CampaignTestingStatus.READY_FOR_OWNER_CONFIRMATION
            riskDecision in setOf(CampaignRiskDecision.RESTART, CampaignRiskDecision.REOPEN) && restartConfirmedAt == null && activeTesterCount < requestedTesters -> CampaignTestingStatus.RESTART_PENDING
            riskDecision in setOf(CampaignRiskDecision.RESTART, CampaignRiskDecision.REOPEN) && restartConfirmedAt == null -> CampaignTestingStatus.READY_TO_RESTART
            testingStartedAt != null && activeTesterCount < requestedTesters -> CampaignTestingStatus.AT_RISK
            testingStartedAt != null -> CampaignTestingStatus.IN_PROGRESS
            else -> CampaignTestingStatus.WAITING_FOR_TESTERS
        }
    val progress: CampaignProgress get() = CampaignProgress(requestedTesters, activeTesterCount, targetDays, testingStatus, testingStartedAt, releaseApprovedAt, closedTestConfirmedAt, riskDecision, replacementOpen, replacementJoinedAt, restartConfirmedAt)
}

fun PublicApp.isDiscoverableForJoin(): Boolean =
    !(riskDecision == CampaignRiskDecision.CONTINUE && activeTesterCount < requestedTesters) &&
        !(riskDecision in setOf(CampaignRiskDecision.RESTART, CampaignRiskDecision.REOPEN) && restartConfirmedAt == null && (!replacementOpen || slotsRemaining == 0))

fun PublicApp.discoverStateLabel(): String = if (replacementOpen && slotsRemaining > 0) {
    if (slotsRemaining == 1) "Replacement tester needed · 1 replacement slot open" else "Replacement testers needed · $slotsRemaining replacement slots open"
} else {
    progress.statusLabel()
}

enum class InstallSignal(val label: String) {
    INSTALLED("Installed"),
    NOT_INSTALLED("Not installed"),
    UNAVAILABLE("Install status unavailable")
}

enum class ActivitySignal(val label: String) {
    OPENED_TODAY("Opened today"),
    NOT_DETECTED("No activity detected today"),
    UNAVAILABLE("Activity unavailable")
}

data class DirectionSignal(
    val install: InstallSignal,
    val activity: ActivitySignal,
    val minutesToday: Int? = null,
    val lastCheckedLabel: String = "Last checked 2 min ago"
)

enum class CommitmentLifecycle { PENDING_SETUP, ACTIVE, COMPLETED, LEFT }

enum class CommitmentEndReason { USER_LEFT, OWNER_RELEASE, INSTALL_TIMEOUT }

/** LEFT is terminal history, never a current tester or obligation. */
fun CommitmentLifecycle.isCurrentTester(): Boolean = this == CommitmentLifecycle.ACTIVE

/** A named pair between one external app and one selected owned app. */
data class ReciprocalCommitment(
    val commitmentId: String,
    val appBeingTestedByCurrentUserId: String,
    val currentUserOwnAppId: String?,
    val otherUserId: String,
    val otherUsername: String,
    val joinedOn: LocalDate,
    val targetDays: Int = 14,
    val youOweSignal: DirectionSignal,
    val theyOweSignal: DirectionSignal? = null,
    val youCanSendReminder: Boolean = true,
    val lifecycle: CommitmentLifecycle = CommitmentLifecycle.ACTIVE,
    val setupDeadlineAt: Instant? = null,
    val endReason: CommitmentEndReason? = null
) {
    val isOneWay: Boolean get() = currentUserOwnAppId == null
    fun elapsedDays(today: LocalDate): Int = (java.time.temporal.ChronoUnit.DAYS.between(joinedOn, today).toInt() + 1).coerceIn(1, targetDays)
    fun daysRemaining(today: LocalDate): Int = (targetDays - elapsedDays(today)).coerceAtLeast(0)
}

enum class NotificationKind { INSTALL, RECIPROCAL, REMINDER, ACCOUNT }

data class AppNotification(
    val id: String,
    val kind: NotificationKind,
    val title: String,
    val message: String,
    val relativeTime: String,
    val isUnread: Boolean,
    val commitmentId: String? = null,
    val ownedAppId: String? = null
)

data class AppBookmark(
    val appId: String,
    val bookmarkedAt: Instant
)
