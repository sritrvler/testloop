package com.testloop.app.ui

import com.testloop.app.domain.CampaignRiskDecision
import com.testloop.app.domain.CommitmentLifecycle
import com.testloop.app.domain.PublicApp
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PortfolioAndDiscoverTest {
    private val ownedApp = PublicApp(
        id = "whatsapp",
        title = "WhatsApp",
        packageName = "com.whatsapp",
        ownerId = "owner",
        ownerUsername = "owner",
        installUrl = "https://play.google.com/store/apps/details?id=com.whatsapp",
        optInUrl = null,
        requestedTesters = 1,
        joinedTesterCount = 0,
        instructions = "Test WhatsApp",
        campaignId = "campaign-whatsapp",
        riskDecision = CampaignRiskDecision.REOPEN,
        replacementOpen = true,
        reservedTesterCount = 0
    )

    @Test
    fun `publisher still hides its replacement listing when hide my apps is on`() {
        assertFalse(shouldShowDiscoverApp(ownedApp, setOf("whatsapp"), hideMyApps = true))
    }

    @Test
    fun `tester can see replacement listing`() {
        assertTrue(shouldShowDiscoverApp(ownedApp, emptySet(), hideMyApps = true))
        assertTrue(discoverTesterCountLabel(ownedApp).contains("1 tester needed"))
        assertFalse(discoverTesterCountLabel(ownedApp).contains("replacement"))
    }

    @Test
    fun `ordinary owned listing remains hidden by default`() {
        assertFalse(shouldShowDiscoverApp(ownedApp.copy(replacementOpen = false), setOf("whatsapp"), hideMyApps = true))
    }

    @Test
    fun `left commitment does not block replacement listing`() {
        assertFalse(isDiscoverJoinBlocked(CommitmentLifecycle.LEFT))
        assertTrue(isDiscoverJoinBlocked(CommitmentLifecycle.ACTIVE))
    }

    @Test
    fun `regular listing shows remaining and filled tester counts`() {
        val app = ownedApp.copy(
            riskDecision = null,
            replacementOpen = false,
            requestedTesters = 15,
            reservedTesterCount = 0
        )

        assertTrue(discoverTesterCountLabel(app).contains("15 testers needed"))
        assertTrue(discoverTesterCountLabel(app).contains("0/15 slots filled"))
    }
}
