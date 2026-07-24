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
    val optInUrl: String,
    val iconSeed: String,
    val campaignId: String,
    val details: String = "",
    val playStorePublishedAt: Instant? = null
)

data class Campaign(
    val id: String,
    val ownedAppId: String,
    val requestedTesters: Int,
    val joinedTesterCount: Int,
    val startedOn: LocalDate,
    val isActive: Boolean = true,
    val instructions: String = ""
) {
    val slotsRemaining: Int get() = (requestedTesters - joinedTesterCount).coerceAtLeast(0)
}

data class PublicApp(
    val id: String,
    val title: String,
    val packageName: String,
    val ownerId: String,
    val ownerUsername: String,
    val installUrl: String,
    val optInUrl: String,
    val requestedTesters: Int,
    val joinedTesterCount: Int,
    val instructions: String,
    val details: String = "",
    val category: String = "Productivity",
    val postedLabel: String = "Posted today",
    val iconSeed: String = title
) {
    val slotsRemaining: Int get() = (requestedTesters - joinedTesterCount).coerceAtLeast(0)
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
    val lifecycle: CommitmentLifecycle = CommitmentLifecycle.ACTIVE
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
