package com.testloop.app.ui

import com.testloop.app.domain.Campaign
import com.testloop.app.domain.CampaignRiskDecision
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class OnboardingAndTodayTest {
    private val started = Instant.parse("2026-07-01T09:00:00Z")

    @Test
    fun `saved restart decision is presented as replacement needed rather than decision needed`() {
        val campaign = Campaign(
            id = "campaign-1",
            ownedAppId = "app-1",
            requestedTesters = 1,
            joinedTesterCount = 1,
            startedOn = LocalDate.of(2026, 7, 1),
            activeTesterCount = 0,
            testingStartedAt = started,
            closedTestConfirmedAt = started,
            riskDecision = CampaignRiskDecision.RESTART,
            replacementOpen = true
        )

        assertEquals("Replacement tester needed", ownerCampaignActionTitle(listOf(campaign)))
    }
}
