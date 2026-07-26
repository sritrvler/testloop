package com.testloop.app.data

import com.testloop.app.domain.CampaignRiskDecision
import com.testloop.app.domain.isDiscoverableForJoin
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DemoRepositoryStateTest {
    @Test
    fun `released slot stays hidden until owner explicitly reopens replacement`() = runBlocking {
        val repository = DemoTestLoopRepository()

        repository.leaveCommitment("pair-matt").getOrThrow()

        val hidden = repository.discoverApps.value.first { it.id == "own-focus" }
        assertFalse(hidden.isDiscoverableForJoin())
        assertEquals(7, repository.campaigns.value.first { it.id == "campaign-focus" }.reservedTesterCount)

        repository.chooseCampaignRiskDecision("campaign-focus", CampaignRiskDecision.REOPEN, null).getOrThrow()

        val reopened = repository.discoverApps.value.first { it.id == "own-focus" }
        assertTrue(reopened.isDiscoverableForJoin())
        assertTrue(reopened.replacementOpen)
        assertEquals(5, reopened.slotsRemaining)
    }
}
