package com.testloop.app.domain

import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CampaignStateTest {
    private val started = Instant.parse("2026-07-01T09:00:00Z")
    private val campaign = Campaign(
        id = "campaign-1",
        ownedAppId = "app-1",
        requestedTesters = 1,
        joinedTesterCount = 1,
        startedOn = LocalDate.of(2026, 7, 1),
        activeTesterCount = 1,
        testingStartedAt = started,
        closedTestConfirmedAt = started
    )

    @Test
    fun `1 of 1 installed but unconfirmed is ready for owner confirmation`() {
        val unconfirmed = campaign.copy(closedTestConfirmedAt = null, testingStartedAt = null)

        assertEquals(CampaignTestingStatus.READY_FOR_OWNER_CONFIRMATION, unconfirmed.testingStatus)
        assertEquals("Ready to start · 1/1 installed", unconfirmed.progress.statusLabel())
        assertFalse(unconfirmed.progress.dailyTaskUnlocked())
    }

    @Test
    fun `pre-start tester has no task and confirmation unlocks it`() {
        val waiting = campaign.copy(closedTestConfirmedAt = null, testingStartedAt = null)
        val confirmed = waiting.copy(closedTestConfirmedAt = started, testingStartedAt = started)

        assertFalse(waiting.progress.dailyTaskUnlocked())
        assertEquals(CampaignTestingStatus.IN_PROGRESS, confirmed.testingStatus)
        assertTrue(confirmed.progress.dailyTaskUnlocked())
    }

    @Test
    fun `drop is at risk and preserves the timer`() {
        val dropped = campaign.copy(activeTesterCount = 0)

        assertEquals(CampaignTestingStatus.AT_RISK, dropped.testingStatus)
        assertEquals(started, dropped.testingStartedAt)
        assertFalse(dropped.progress.dailyTaskUnlocked())
    }

    @Test
    fun `continue preserves start and unlocks honest ongoing tasks`() {
        val continued = campaign.copy(activeTesterCount = 0, riskDecision = CampaignRiskDecision.CONTINUE)

        assertEquals(CampaignTestingStatus.AT_RISK, continued.testingStatus)
        assertEquals(started, continued.testingStartedAt)
        assertTrue(continued.progress.dailyTaskUnlocked())
        assertTrue(continued.progress.statusLabel().startsWith("Continuing current run"))
    }

    @Test
    fun `decide later and restart pending do not unlock tasks`() {
        val later = campaign.copy(activeTesterCount = 0, riskDecision = CampaignRiskDecision.DECIDE_LATER)
        val pending = campaign.copy(activeTesterCount = 0, riskDecision = CampaignRiskDecision.RESTART, replacementOpen = true)

        assertFalse(later.progress.dailyTaskUnlocked())
        assertEquals(CampaignTestingStatus.AT_RISK, later.testingStatus)
        assertFalse(pending.progress.dailyTaskUnlocked())
        assertEquals(CampaignTestingStatus.RESTART_PENDING, pending.testingStatus)
        assertTrue(pending.progress.statusLabel().contains("Replacement tester needed"))
    }

    @Test
    fun `replacement restored still needs final explicit restart`() {
        val ready = campaign.copy(
            activeTesterCount = 1,
            riskDecision = CampaignRiskDecision.RESTART,
            replacementJoinedAt = started,
            restartConfirmedAt = null
        )
        val restarted = ready.copy(restartConfirmedAt = started.plusSeconds(1), testingStartedAt = started.plusSeconds(1))

        assertEquals(CampaignTestingStatus.READY_TO_RESTART, ready.testingStatus)
        assertFalse(ready.progress.dailyTaskUnlocked())
        assertEquals(CampaignTestingStatus.IN_PROGRESS, restarted.testingStatus)
        assertTrue(restarted.progress.dailyTaskUnlocked())
    }

    @Test
    fun `restart opens a replacement listing but continue stays hidden`() {
        val replacement = PublicApp(
            id = "public-1", title = "Test app", packageName = "com.example.test", ownerId = "owner", ownerUsername = "owner",
            installUrl = "https://play.google.com/store/apps/details?id=com.example.test", optInUrl = null,
            requestedTesters = 2, joinedTesterCount = 1, instructions = "Try it", campaignId = "campaign-1",
            activeTesterCount = 1, testingStartedAt = started, closedTestConfirmedAt = started,
            riskDecision = CampaignRiskDecision.RESTART, replacementOpen = true, reservedTesterCount = 1
        )
        val continued = replacement.copy(riskDecision = CampaignRiskDecision.CONTINUE, replacementOpen = false)

        assertTrue(replacement.isDiscoverableForJoin())
        assertTrue(replacement.discoverStateLabel().contains("1 replacement slot open"))
        assertFalse(continued.isDiscoverableForJoin())
    }

    @Test
    fun `zero reserved testers never reports all active today`() {
        val waiting = campaign.copy(reservedTesterCount = 0, activeTesterCount = 0, joinedTesterCount = 0, closedTestConfirmedAt = null, testingStartedAt = null)

        assertEquals("No testers enrolled yet", waiting.portfolioActivityLabel(attention = 0))
    }

    @Test
    fun `at risk label wins over zero tester label`() {
        val atRisk = campaign.copy(reservedTesterCount = 0, activeTesterCount = 0)

        assertEquals("At risk · choose what to do", atRisk.portfolioActivityLabel(attention = 0))
    }
}
