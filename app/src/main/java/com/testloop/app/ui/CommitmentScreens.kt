package com.testloop.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalUriHandler
import com.testloop.app.domain.Campaign
import com.testloop.app.domain.ActivitySignal
import com.testloop.app.domain.CampaignProgress
import com.testloop.app.domain.CampaignTestingStatus
import com.testloop.app.domain.CampaignRiskDecision
import com.testloop.app.domain.statusLabel
import com.testloop.app.domain.CommitmentLifecycle
import com.testloop.app.domain.DirectionSignal
import com.testloop.app.domain.InstallSignal
import com.testloop.app.domain.OwnedApp
import com.testloop.app.domain.PublicApp
import com.testloop.app.domain.ReciprocalCommitment
import com.testloop.app.domain.isCurrentTester
import com.testloop.app.ui.theme.Brand100
import com.testloop.app.ui.theme.Brand600
import com.testloop.app.ui.theme.Ink500
import com.testloop.app.ui.theme.Success100
import com.testloop.app.ui.theme.Success700
import java.time.LocalDate
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@Composable
fun PairDetailScreen(pair: ReciprocalCommitment?, ownedApps: List<OwnedApp>, campaigns: List<Campaign>, publicApps: List<PublicApp>, onBack: () -> Unit, onOpen: (String) -> Unit, onOpenLink: (String) -> Unit, onReminder: (String, String) -> Unit, onLeave: (String) -> Unit) {
    if (pair == null) { MissingScreen(onBack); return }
    val otherApp = publicApps.firstOrNull { it.id == pair.appBeingTestedByCurrentUserId }
    val ownApp = ownedApps.firstOrNull { it.id == pair.currentUserOwnAppId }
    val volunteer = pair.appBeingTestedByCurrentUserId.isBlank() && ownApp != null
    if ((otherApp == null && !volunteer) || (pair.currentUserOwnAppId != null && ownApp == null)) {
        UnavailableCommitmentScreen(onBack)
        return
    }
    val progress = progressForPair(pair, ownedApps, campaigns, publicApps)
    val today = LocalDate.now()
    var confirmReminder by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    val canActOnCommitment = pair.lifecycle in setOf(CommitmentLifecycle.PENDING_SETUP, CommitmentLifecycle.ACTIVE)
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        TestLoopTopBar("Reciprocal pair", if (volunteer) "Volunteer tester for ${ownApp.title}" else "${otherApp?.title ?: "Test app"} for ${ownApp?.title ?: "one-way commitment"}", onBack = onBack)
        LazyColumn(contentPadding = PaddingValues(Gutter), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { PairHeader(otherApp?.title ?: "Volunteer", ownApp?.title, pair.otherUsername) }
            if (!canActOnCommitment) item {
                InlineNotice(
                    when (pair.lifecycle) {
                        CommitmentLifecycle.LEFT -> "You left this commitment. It no longer reserves a tester slot."
                        CommitmentLifecycle.COMPLETED -> "This commitment is complete and no longer accepts actions."
                        else -> "This commitment is no longer active."
                    }
                )
            }
            otherApp?.messagingId?.takeIf(String::isNotBlank)?.let { messagingId ->
                item {
                    GroupSurface {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Report issues directly", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("Use the publisher's messaging platform:", color = Ink500, style = MaterialTheme.typography.bodySmall)
                            Text(messagingId, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            item { CampaignProgressBlock(progress, today) }
            if (otherApp != null) item { DirectionBlock("You owe them", "You test ${otherApp.title} for ${pair.otherUsername}", pair.youOweSignal, pair, true) }
            if (ownApp != null && pair.theyOweSignal != null) item { DirectionBlock("They owe you", "${pair.otherUsername} tests ${ownApp.title} for you", pair.theyOweSignal, pair, false) }
            item { InlineNotice("Install status is a device observation, not proof of Google Play testing or test quality.") }
            if (canActOnCommitment && otherApp != null) item {
                if (pair.youOweSignal.install == InstallSignal.INSTALLED) {
                    Button(onClick = { onOpen(pair.commitmentId) }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Icon(Icons.Default.OpenInNew, null); Spacer(Modifier.width(8.dp)); Text("Open ${otherApp.title}") }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onOpenLink(otherApp.installUrl) }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Install ${otherApp.title}") }
                        otherApp.groupUrl.takeIf(String::isNotBlank)?.let { groupUrl -> OutlinedButton(onClick = { onOpenLink(groupUrl) }, modifier = Modifier.fillMaxWidth()) { Text("Join testing group") } }
                        otherApp.optInUrl?.let { optInUrl -> OutlinedButton(onClick = { onOpenLink(optInUrl) }, modifier = Modifier.fillMaxWidth()) { Text("Opt in to Play test") } }
                        Text("You will be counted as a tester after TestLoop detects the app on this phone.", color = Ink500, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (canActOnCommitment && pair.theyOweSignal != null && needsAttention(pair.theyOweSignal)) item {
                if (pair.youCanSendReminder) OutlinedButton(onClick = { confirmReminder = true }, modifier = Modifier.fillMaxWidth()) { Text("Send a quick check-in") } else Text("Check-in sent today", modifier = Modifier.padding(12.dp), color = Ink500, style = MaterialTheme.typography.labelLarge)
            }
            if (canActOnCommitment) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Report issue", color = Ink500, style = MaterialTheme.typography.labelLarge)
                    OutlinedButton(
                        onClick = { confirmLeave = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("Leave commitment") }
                }
            }
        }
    }
    if (confirmReminder) AlertDialog(onDismissRequest = { confirmReminder = false }, title = { Text("Send @${pair.otherUsername} a helpful check-in?") }, text = { Text("We’ll let them know that opening TestLoop helps update today’s testing status for ${ownApp?.title ?: "your app"}. They can do it when convenient.") }, confirmButton = { Button(onClick = { confirmReminder = false; onReminder(pair.commitmentId, pair.otherUsername) }) { Text("Send check-in") } }, dismissButton = { OutlinedButton(onClick = { confirmReminder = false }) { Text("Not now") } })
    if (confirmLeave) AlertDialog(
        onDismissRequest = { confirmLeave = false },
        title = { Text("Leave this commitment?") },
        text = {
            Text(
                "Leaving now may interrupt the app owner’s closed test, cost them progress toward production access, and force them to find a replacement tester. Please leave only if you can no longer continue. This cannot be undone."
            )
        },
        confirmButton = {
            Button(
                onClick = { confirmLeave = false; onLeave(pair.commitmentId) },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Leave commitment") }
        },
        dismissButton = { OutlinedButton(onClick = { confirmLeave = false }) { Text("Keep commitment") } }
    )
}

@Composable
fun CommitmentLeftScreen(onDone: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(1_800)
        onDone()
    }
    Column(
        Modifier.fillMaxSize().navigationBarsPadding().padding(Gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(76.dp).clip(RoundedCornerShape(38.dp)).background(Success100),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = "Commitment left", tint = Success700, modifier = Modifier.size(46.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("Commitment ended", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "You’ve left the commitment. The app owner has been notified, and the tester slot is available again.",
            color = Ink500,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
fun CommitmentReadyScreen(commitment: ReciprocalCommitment?, publicApps: List<PublicApp>, ownedApps: List<OwnedApp>, isBookmarked: Boolean, onBack: () -> Unit, onOpenLink: (String) -> Unit, onCheckInstall: suspend (String) -> Result<Unit>, onFound: () -> Unit, onBookmark: suspend (String) -> Result<Unit>, onRemoveBookmark: suspend (String) -> Result<Unit>) {
    if (commitment == null) { MissingScreen(onBack); return }
    val app = publicApps.firstOrNull { it.id == commitment.appBeingTestedByCurrentUserId }
    val ownApp = ownedApps.firstOrNull { it.id == commitment.currentUserOwnAppId }
    val appName = app?.title ?: "Test app"
    val optInUrl = app?.optInUrl?.trim()?.takeIf { it.isNotEmpty() }
    val installStep = if (optInUrl != null) 3 else 2
    val checkStep = installStep + 1
    var shownBookmarked by remember(isBookmarked) { mutableStateOf(isBookmarked) }
    var checkingInstall by remember(commitment.commitmentId) { mutableStateOf(false) }
    var hasChecked by rememberSaveable(commitment.commitmentId) {
        mutableStateOf(commitment.youOweSignal.install != InstallSignal.UNAVAILABLE)
    }
    val scope = rememberCoroutineScope()
    val installSignal = commitment.youOweSignal.install
    val found = installSignal == InstallSignal.INSTALLED
    val currentStep = when {
        checkingInstall -> checkStep
        hasChecked && installSignal == InstallSignal.NOT_INSTALLED -> installStep
        else -> 1
    }

    LaunchedEffect(commitment.commitmentId, found) {
        if (found) {
            delay(1_800)
            onFound()
        }
    }

    if (found) {
        CommitmentFoundScreen(appName, onBack)
        return
    }

    fun runInstallCheck() {
        if (checkingInstall) return
        checkingInstall = true
        scope.launch {
            val result = onCheckInstall(commitment.commitmentId)
            if (result.isSuccess) hasChecked = true
            checkingInstall = false
        }
    }

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        TestLoopTopBar("Get ready to test", appName, onBack = onBack)
        LazyColumn(contentPadding = PaddingValues(Gutter), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Column {
                    Text("Complete these steps", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("Then we’ll confirm that $appName is installed on this phone.", color = Ink500, style = MaterialTheme.typography.bodyLarge)
                }
            }
            item {
                GroupSurface {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        ChecklistStep(
                            number = 1,
                            title = "Join the testing group",
                            description = "Join the Google Group so the publisher can add you to the test.",
                            actionLabel = "Join group",
                            highlighted = currentStep == 1,
                            enabled = app?.groupUrl?.isNotBlank() == true,
                            onClick = { app?.groupUrl?.trim()?.takeIf { it.isNotEmpty() }?.let(onOpenLink) }
                        )
                        if (optInUrl != null) {
                            ChecklistStep(
                                number = 2,
                                title = "Opt in to the Play test",
                                description = "Open Google Play and accept the testing invitation.",
                                actionLabel = "Opt in",
                                highlighted = currentStep == 2,
                                onClick = { onOpenLink(optInUrl) }
                            )
                        }
                        ChecklistStep(
                            number = installStep,
                            title = "Install $appName",
                            description = "Install the app from Google Play.",
                            actionLabel = "Install app",
                            highlighted = currentStep == installStep,
                            enabled = app != null,
                            onClick = { app?.let { onOpenLink(it.installUrl) } }
                        )
                        ChecklistStep(
                            number = checkStep,
                            title = "Check the app",
                            description = if (checkingInstall) "Checking whether $appName is installed…" else "After installing, return here and confirm it’s on this phone.",
                            actionLabel = if (hasChecked) "Check app again" else "Check app",
                            highlighted = currentStep == checkStep,
                            enabled = !checkingInstall,
                            onClick = ::runInstallCheck,
                            accessory = {
                                if (checkingInstall) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = Brand600
                                    )
                                }
                                if (hasChecked && installSignal == InstallSignal.NOT_INSTALLED) {
                                    InstallNotFoundSurface(appName)
                                }
                            }
                        )
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (shownBookmarked) "Saved to bookmarks" else "Save this app for later", modifier = Modifier.weight(1f), color = Ink500)
                    IconButton(onClick = {
                        app?.let {
                            val wasBookmarked = shownBookmarked
                            shownBookmarked = !wasBookmarked
                            scope.launch {
                                val result = if (wasBookmarked) onRemoveBookmark(it.id) else onBookmark(it.id)
                                result.onFailure { shownBookmarked = wasBookmarked }
                            }
                        }
                    }, enabled = app != null) {
                        Icon(if (shownBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, if (shownBookmarked) "Remove bookmark" else "Bookmark $appName", tint = Brand600)
                    }
                }
            }
            item {
                Text("Your reciprocal app", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(if (ownApp == null) "This is a volunteer commitment. You are helping ${commitment.otherUsername} without offering an app in return." else "@${commitment.otherUsername} will test ${ownApp.title} after this commitment becomes active.", color = Ink500)
            }
            item { InlineNotice("TestLoop checks whether the app is installed on this phone. It does not verify test quality or Google Play approval.") }
        }
    }
}

@Composable
private fun ChecklistStep(
    number: Int,
    title: String,
    description: String,
    actionLabel: String,
    highlighted: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    accessory: (@Composable () -> Unit)? = null
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(16.dp)).background(if (highlighted) Brand600 else Brand100),
            contentAlignment = Alignment.Center
        ) {
            Text(number.toString(), color = if (highlighted) Color.White else Brand600, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(description, color = Ink500, style = MaterialTheme.typography.bodyMedium)
            accessory?.let {
                Spacer(Modifier.height(10.dp))
                it()
            }
            Spacer(Modifier.height(10.dp))
            if (highlighted) {
                Button(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = enabled) { Text(actionLabel) }
            } else {
                OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = enabled) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun InstallNotFoundSurface(appName: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.errorContainer).padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("App not found", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer)
        }
        Spacer(Modifier.height(4.dp))
        Text("Install $appName, then tap Check app again.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

@Composable
private fun CommitmentFoundScreen(appName: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        TestLoopTopBar("You're ready to test", appName, onBack = onBack)
        Column(
            Modifier.fillMaxSize().padding(Gutter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier.size(72.dp).clip(RoundedCornerShape(36.dp)).background(Success100),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = "App found", tint = Success700, modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text("You’re ready to test", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("$appName was found on this phone. Your testing commitment is active.", color = Ink500, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun PairHeader(other: String, own: String?, person: String) { Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) { Column(horizontalAlignment = Alignment.CenterHorizontally) { AppMonogram(other); Text(other, style = MaterialTheme.typography.labelLarge) }; Icon(Icons.Default.ArrowForward, null, tint = Brand600); Column(horizontalAlignment = Alignment.CenterHorizontally) { AppMonogram(own ?: "One way"); Text(own ?: "One-way", style = MaterialTheme.typography.labelLarge) } } }

@Composable
private fun DirectionBlock(title: String, description: String, signal: DirectionSignal, pair: ReciprocalCommitment, currentUserDirection: Boolean) {
    GroupSurface { Column(Modifier.padding(16.dp)) { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp)); Text(description, color = Ink500); Spacer(Modifier.height(12.dp)); if (!currentUserDirection && signal.activity == ActivitySignal.UNAVAILABLE) { Text("Waiting for ${pair.otherUsername} to open TestLoop so we can update today’s status.", style = MaterialTheme.typography.bodySmall, color = Ink500); Spacer(Modifier.height(8.dp)) }; EvidenceLabels(signal, waitingForTester = !currentUserDirection); Text(signal.lastCheckedLabel, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelSmall, color = Ink500) } }
}
private fun progressForPair(pair: ReciprocalCommitment, ownedApps: List<OwnedApp>, campaigns: List<Campaign>, publicApps: List<PublicApp>): CampaignProgress {
    publicApps.firstOrNull { it.id == pair.appBeingTestedByCurrentUserId }?.let { return it.progress }
    ownedApps.firstOrNull { it.id == pair.currentUserOwnAppId }?.let { app ->
        campaigns.firstOrNull { it.ownedAppId == app.id }?.let { return it.progress }
    }
    return CampaignProgress(12, 0, pair.targetDays, CampaignTestingStatus.WAITING_FOR_TESTERS)
}

private fun progressLabel(progress: CampaignProgress, today: LocalDate): String = when (progress.status) {
    CampaignTestingStatus.IN_PROGRESS -> {
        val day = progressDay(progress, today)
        "Day $day of ${progress.targetDays} · ${progress.targetDays - day} days remaining · ${progress.activeTesterCount}/${progress.requiredTesters} active"
    }
    else -> progress.statusLabel()
}

private fun progressDay(progress: CampaignProgress, today: LocalDate): Int {
    val start = progress.testingStartedAt?.atZone(java.time.ZoneId.systemDefault())?.toLocalDate() ?: today
    return (java.time.temporal.ChronoUnit.DAYS.between(start, today).toInt() + 1).coerceIn(1, progress.targetDays)
}

@Composable
private fun CampaignProgressBlock(progress: CampaignProgress, today: LocalDate) {
    var showRequirements by remember(progress.status) { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    GroupSurface {
        Column(Modifier.padding(16.dp)) {
            Text(progressLabel(progress, today), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            when (progress.status) {
                CampaignTestingStatus.READY_FOR_OWNER_CONFIRMATION -> {
                    Row(
                        Modifier.fillMaxWidth().clickable { showRequirements = true }.padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("How to reach production access", style = MaterialTheme.typography.labelLarge, color = Brand600, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.Info, contentDescription = "Show production access requirements", tint = Brand600, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                CampaignTestingStatus.WAITING_FOR_TESTERS -> Text("Waiting for ${progress.requiredTesters - progress.activeTesterCount} more tester install${if (progress.requiredTesters - progress.activeTesterCount == 1) "" else "s"}. Daily open tasks stay locked until all installs are detected and the owner confirms closed testing is live.", color = Ink500)
                CampaignTestingStatus.AT_RISK -> Text(
                    if (progress.riskDecision == CampaignRiskDecision.CONTINUE) "The owner is continuing the current run. TestLoop is not observing the full threshold right now; the owner may be relying on buffer or testers outside TestLoop. This does not verify the threshold here."
                    else "The required installed count dropped. The owner is deciding whether to continue, recruit a replacement, or decide later. The current timer and history are preserved.",
                    color = Ink500
                )
                CampaignTestingStatus.RESTART_PENDING -> Text(if (progress.replacementJoinedAt == null) "The owner plans to restart after replacing or adding a tester. A replacement tester may be required; the current timer is preserved." else "A replacement tester has joined. Install detection must restore the required count before the owner can confirm a restart.", color = Ink500)
                CampaignTestingStatus.READY_TO_RESTART -> Text("The required count is restored. The owner must explicitly confirm the restart; only that confirmation sets Day 1 and a new end date.", color = Ink500)
                CampaignTestingStatus.IN_PROGRESS -> DayProgress(progressDay(progress, today), progress.targetDays, (progress.targetDays - progressDay(progress, today)).coerceAtLeast(0))
                CampaignTestingStatus.COMPLETE -> Text("The required overlap was maintained for the full testing window.", color = Ink500)
            }
        }
    }
    if (showRequirements && progress.status == CampaignTestingStatus.READY_FOR_OWNER_CONFIRMATION) {
        AlertDialog(
            onDismissRequest = { showRequirements = false },
            title = { Text("Closed-testing confirmation checklist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("• Use Google Play Closed testing, not Internal or Open testing.", style = MaterialTheme.typography.bodySmall)
                    Text("• Confirm in Google Play Console that closed testing is live. TestLoop cannot verify Google’s approval.", style = MaterialTheme.typography.bodySmall)
                    Text("• Confirm only after the installed count shown here is complete. This owner confirmation unlocks daily open tasks and starts Day 1.", style = MaterialTheme.typography.bodySmall)
                    Text("• Keep your own buffer and Google Play records; TestLoop only reports its observed install signals.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { uriHandler.openUri("https://support.google.com/googleplay/android-developer/answer/14151465") }) { Text("Open Google guide") } },
            dismissButton = { TextButton(onClick = { showRequirements = false }) { Text("Close") } }
        )
    }
}

@Composable
fun ObligationsScreen(commitments: List<ReciprocalCommitment>, ownedApps: List<OwnedApp>, campaigns: List<Campaign>, publicApps: List<PublicApp>, onBack: () -> Unit, onPair: (String) -> Unit) {
    val today = LocalDate.now()
    val visible = commitments.filter {
        it.lifecycle.isCurrentTester() &&
            (publicApps.any { app -> app.id == it.appBeingTestedByCurrentUserId } ||
                ownedApps.any { app -> app.id == it.appBeingTestedByCurrentUserId }) &&
            (it.currentUserOwnAppId == null || ownedApps.any { app -> app.id == it.currentUserOwnAppId })
    }
    val youOwe = visible.distinctBy { it.appBeingTestedByCurrentUserId to it.otherUserId }
    val theyOwe = visible.filter { pair ->
        pair.currentUserOwnAppId != null && ownedApps.any { it.id == pair.currentUserOwnAppId }
    }.distinctBy { it.currentUserOwnAppId to it.otherUserId }
    LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = PaddingValues(Gutter), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { TestLoopTopBar("Obligations", "Every direction, named clearly", onBack = onBack) }
        item { SectionHeader("You owe them") }
        item { if (youOwe.isEmpty()) EmptyObligationsState("You have no active app to test today.") else GroupSurface { youOwe.forEachIndexed { index, pair -> val other = publicApps.firstOrNull { it.id == pair.appBeingTestedByCurrentUserId }; val owned = ownedApps.firstOrNull { it.id == pair.appBeingTestedByCurrentUserId }; val appTitle = other?.title ?: owned?.title ?: "Test app"; ObligationRow("You test $appTitle for ${pair.otherUsername}", pair.youOweSignal, progressForPair(pair, ownedApps, campaigns, publicApps), pair, today, onPair); if (index != youOwe.lastIndex) RowDivider() } } }
        item { SectionHeader("They owe you") }
        item { if (theyOwe.isEmpty()) EmptyObligationsState("No one is currently testing one of your active apps.") else GroupSurface { theyOwe.forEachIndexed { index, pair -> val own = ownedApps.first { it.id == pair.currentUserOwnAppId }; ObligationRow("${pair.otherUsername} tests ${own.title} for you", pair.theyOweSignal ?: pair.youOweSignal, progressForPair(pair, ownedApps, campaigns, publicApps), pair, today, onPair); if (index != theyOwe.lastIndex) RowDivider() } } }
    }
}

@Composable
private fun EmptyObligationsState(message: String) {
    GroupSurface { Text(message, modifier = Modifier.padding(16.dp), color = Ink500) }
}

@Composable
private fun ObligationRow(text: String, signal: DirectionSignal, progress: CampaignProgress, pair: ReciprocalCommitment, today: LocalDate, onPair: (String) -> Unit) { Row(Modifier.fillMaxWidth().clickable { onPair(pair.commitmentId) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(7.dp)); SignalLabel(signal, compact = true); Spacer(Modifier.height(7.dp)); Text(progressLabel(progress, today), style = MaterialTheme.typography.labelMedium, color = Ink500) }; Icon(Icons.Default.ArrowForward, "Open obligation", tint = Brand600) } }

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun OwnAppDetailScreen(appId: String, attentionOnly: Boolean, ownedApps: List<OwnedApp>, campaigns: List<Campaign>, commitments: List<ReciprocalCommitment>, publicApps: List<PublicApp>, onBack: () -> Unit, onPair: (String) -> Unit, onReminder: (String, String) -> Unit, onSwitch: (String) -> Unit, onDelist: suspend (String) -> Result<Unit>, onMarkPublished: suspend (String) -> Result<Unit>, onUpdateTesterCount: suspend (String, Int) -> Result<Unit>, onConfirmClosedTesting: (String) -> Unit, onRiskDecision: (String, com.testloop.app.domain.CampaignRiskDecision, String?) -> Unit, onFinalRestart: (String) -> Unit) {
    val app = ownedApps.firstOrNull { it.id == appId } ?: run { MissingScreen(onBack); return }
    val campaign = campaigns.firstOrNull { it.ownedAppId == appId }
    val pairs = commitments.filter { it.currentUserOwnAppId == appId && it.lifecycle.isCurrentTester() }
        .distinctBy { it.otherUserId to it.currentUserOwnAppId }
    val hasReleasedCommitment = commitments.any { it.currentUserOwnAppId == appId && it.lifecycle == CommitmentLifecycle.LEFT }
    var filter by remember { mutableIntStateOf(if (attentionOnly) 1 else 0) }
    var confirmDelist by remember { mutableStateOf(false) }
    var delisting by remember { mutableStateOf(false) }
    var markingPublished by remember { mutableStateOf(false) }
    var showEditTesterTarget by remember { mutableStateOf(false) }
    var showRiskChoice by remember { mutableStateOf(false) }
    var testerTargetText by remember(campaign?.requestedTesters) { mutableStateOf(campaign?.requestedTesters?.toString().orEmpty()) }
    var updatingTesterTarget by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val enrolledTesterCount = campaign?.reservedTesterCount ?: 0
    val minimumTesterTarget = enrolledTesterCount.coerceAtLeast(1)
    val displayed = if (filter == 1) pairs.filter { needsAttention(it.theyOweSignal) } else if (filter == 2) pairs.filter { !needsAttention(it.theyOweSignal) } else pairs
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        TestLoopTopBar(app.title, campaign?.progress?.statusLabel() ?: "Waiting for active testers", onBack = onBack, actionContent = {
            if (campaign != null) {
                IconButton(onClick = { showEditTesterTarget = true }, enabled = !delisting && !markingPublished && !updatingTesterTarget) {
                    Icon(Icons.Default.Edit, "Edit tester target", tint = Brand600)
                }
            }
            IconButton(onClick = { confirmDelist = true }, enabled = !delisting && !markingPublished) {
                Icon(Icons.Default.DeleteForever, "Delist ${app.title}", tint = MaterialTheme.colorScheme.error)
            }
            if (app.playStorePublishedAt == null) {
                IconButton(onClick = {
                    markingPublished = true
                    scope.launch { onMarkPublished(app.id); markingPublished = false }
                }, enabled = !delisting && !markingPublished) {
                    Icon(Icons.Default.CheckCircle, "Mark ${app.title} published on Google Play", tint = Brand600)
                }
            } else {
                Icon(Icons.Default.CheckCircle, "Published on Google Play", tint = Brand600, modifier = Modifier.padding(horizontal = 12.dp))
            }
        })
        LazyColumn(contentPadding = PaddingValues(Gutter), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { OwnAppSwitcher(app, ownedApps, onSwitch) }
            item { campaign?.let { CampaignProgressBlock(it.progress, LocalDate.now()) } }
            item {
                campaign?.let {
                    CampaignOwnerActionSurface(
                        app = app,
                        campaign = it,
                        pairs = pairs,
                        hasReleasedCommitment = hasReleasedCommitment,
                        onConfirmClosedTesting = onConfirmClosedTesting,
                        onChooseRisk = { showRiskChoice = true },
                        onFinalRestart = onFinalRestart
                    )
                }
            }
            item { CompactFilterControl(filter, { filter = it }, pairs.size, pairs.count { needsAttention(it.theyOweSignal) }, pairs.count { !needsAttention(it.theyOweSignal) }) }
            if (displayed.isEmpty()) item { Text("Everyone is on track", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("No tester currently needs attention for this app.", color = Ink500) }
            item { GroupSurface { displayed.forEachIndexed { index, pair -> TesterRow(pair, app, publicApps.firstOrNull { it.id == pair.appBeingTestedByCurrentUserId }, campaign?.progress ?: CampaignProgress(12, 0, 14, CampaignTestingStatus.WAITING_FOR_TESTERS), onPair, onReminder); if (index != displayed.lastIndex) RowDivider() } } }
        }
    }
    if (confirmDelist) AlertDialog(
        onDismissRequest = { if (!delisting) confirmDelist = false },
        title = { Text(if (delisting) "Removing from Discover…" else "Delist ${app.title}?") },
        text = {
            if (delisting) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.width(24.dp).height(24.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Closing the test campaign and removing this app from Discover. Please wait.")
                }
            } else {
                Text("This closes the test campaign and removes ${app.title} from Discover. This action cannot be undone.")
            }
        },
        confirmButton = {
            Button(onClick = {
                delisting = true
                scope.launch { onDelist(app.id).onSuccess { confirmDelist = false }.also { delisting = false } }
            }, enabled = !delisting) { Text("Delist app") }
        },
        dismissButton = { if (!delisting) OutlinedButton(onClick = { confirmDelist = false }) { Text("Keep app") } }
    )
    if (showRiskChoice && campaign != null) AlertDialog(
        onDismissRequest = { showRiskChoice = false },
        title = { Text("Choose what happens to this run") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("A commitment was released or the installed count is below the required threshold. Your current start time and history will remain unchanged.", color = Ink500)
                Button(onClick = { showRiskChoice = false; onRiskDecision(campaign.id, com.testloop.app.domain.CampaignRiskDecision.CONTINUE, null) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Continue current run")
                }
                Text("Preserves Day 1. TestLoop will say the full threshold is not verified while the observed count is low.", style = MaterialTheme.typography.bodySmall, color = Ink500)
                OutlinedButton(onClick = { showRiskChoice = false; onRiskDecision(campaign.id, com.testloop.app.domain.CampaignRiskDecision.DECIDE_LATER, null) }, modifier = Modifier.fillMaxWidth()) { Text("Decide later") }
                Text("Leaves the campaign at risk without changing the timer or opening Discover.", style = MaterialTheme.typography.bodySmall, color = Ink500)
                Text("Restart after replacing a tester", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                pairs.filter { it.lifecycle == CommitmentLifecycle.ACTIVE }.forEach { pair ->
                    OutlinedButton(onClick = { showRiskChoice = false; onRiskDecision(campaign.id, com.testloop.app.domain.CampaignRiskDecision.RESTART, pair.commitmentId) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Replace @${pair.otherUsername}")
                    }
                }
                if (hasReleasedCommitment && campaign.slotsRemaining > 0 && campaign.riskDecision != CampaignRiskDecision.REOPEN) {
                    OutlinedButton(onClick = { showRiskChoice = false; onRiskDecision(campaign.id, CampaignRiskDecision.REOPEN, null) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Open a replacement slot in Discover")
                    }
                }
                Text("Releasing a selected commitment opens one replacement slot in Discover. The restart still needs a separate final confirmation after the required count returns.", style = MaterialTheme.typography.bodySmall, color = Ink500)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { showRiskChoice = false }) { Text("Close") } }
    )
    if (showEditTesterTarget && campaign != null) AlertDialog(
        onDismissRequest = { if (!updatingTesterTarget) showEditTesterTarget = false },
        title = { Text("Update tester target") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Change how many testers you need for this live listing. The update will appear in Discover and in every tester’s progress immediately.", color = Ink500)
                OutlinedTextField(
                    value = testerTargetText,
                    onValueChange = { value ->
                        val digits = value.filter(Char::isDigit)
                        testerTargetText = digits.toIntOrNull()?.let { it.coerceAtLeast(minimumTesterTarget).toString() } ?: digits
                    },
                    label = { Text("Testers needed") },
                    supportingText = {
                        Text(
                            if (enrolledTesterCount == 0) {
                                "No testers are enrolled yet. Choose 1–100 testers."
                            } else {
                                "Minimum $enrolledTesterCount. ${if (enrolledTesterCount == 1) "1 tester is" else "$enrolledTesterCount testers are"} already enrolled. Maximum 100."
                            }
                        )
                    },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            val requested = testerTargetText.toIntOrNull()
            Button(onClick = {
                if (requested != null) {
                    updatingTesterTarget = true
                    scope.launch {
                        onUpdateTesterCount(campaign.id, requested)
                            .onSuccess { showEditTesterTarget = false }
                        updatingTesterTarget = false
                    }
                }
            }, enabled = !updatingTesterTarget && testerTargetText.toIntOrNull()?.let { it in minimumTesterTarget..100 && it >= enrolledTesterCount && it != campaign.requestedTesters } == true) {
                Text(if (updatingTesterTarget) "Saving…" else "Save target")
            }
        },
        dismissButton = { if (!updatingTesterTarget) OutlinedButton(onClick = { showEditTesterTarget = false }) { Text("Cancel") } }
    )
}

@Composable
private fun CampaignOwnerActionSurface(
    app: OwnedApp,
    campaign: Campaign,
    pairs: List<ReciprocalCommitment>,
    hasReleasedCommitment: Boolean,
    onConfirmClosedTesting: (String) -> Unit,
    onChooseRisk: () -> Unit,
    onFinalRestart: (String) -> Unit
) {
    when (campaign.testingStatus) {
        CampaignTestingStatus.READY_FOR_OWNER_CONFIRMATION -> GroupSurface {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Confirm closed testing is live", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("TestLoop detected every required install. Check Google Play Console yourself, then confirm here. TestLoop cannot verify Google’s approval.", color = Ink500)
                Text("Checklist: Closed testing track is live · tester count is complete · you understand this starts Day 1.", style = MaterialTheme.typography.bodySmall, color = Ink500)
                Button(onClick = { onConfirmClosedTesting(campaign.id) }, modifier = Modifier.fillMaxWidth()) { Text("Confirm closed testing is live") }
            }
        }
        CampaignTestingStatus.AT_RISK -> GroupSurface {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Your campaign is at risk", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("The observed installed count is below the target. Choose how to handle the current run; TestLoop will not reset the timer automatically.", color = Ink500)
                Button(onClick = onChooseRisk, modifier = Modifier.fillMaxWidth()) { Text("Choose a campaign decision") }
            }
        }
        CampaignTestingStatus.WAITING_FOR_TESTERS -> if (campaign.riskDecision == CampaignRiskDecision.CONTINUE && hasReleasedCommitment) {
            GroupSurface {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("A tester slot is available", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("The released commitment is hidden from Discover until you choose what to do.", color = Ink500)
                    Button(onClick = onChooseRisk, modifier = Modifier.fillMaxWidth()) { Text("Choose a campaign decision") }
                }
            }
        }
        CampaignTestingStatus.RESTART_PENDING -> GroupSurface {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (campaign.replacementJoinedAt == null) "Replacement tester needed" else "Waiting for replacement install", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("The owner plans to restart after a replacement tester restores the required installed count. The current timer is preserved.", color = Ink500)
                if (campaign.replacementOpen) Text("Discover shows one replacement slot open for this campaign.", style = MaterialTheme.typography.bodySmall, color = Brand600)
            }
        }
        CampaignTestingStatus.READY_TO_RESTART -> GroupSurface {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Replacement count restored", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Review the replacement tester, then confirm only if you want a new Day 1. This is the only action that changes testing_started_at.", color = Ink500)
                Button(onClick = { onFinalRestart(campaign.id) }, modifier = Modifier.fillMaxWidth()) { Text("Confirm restart at Day 1") }
            }
        }
        CampaignTestingStatus.IN_PROGRESS, CampaignTestingStatus.COMPLETE -> Unit
    }
}

@Composable
private fun OwnAppSwitcher(selected: OwnedApp, ownedApps: List<OwnedApp>, onSwitch: (String) -> Unit) { var expanded by remember { mutableStateOf(false) }; Column { OutlinedButton(onClick = { expanded = !expanded }) { AppMonogram(selected.title, small = true); Spacer(Modifier.width(8.dp)); Text(selected.title); Icon(Icons.Default.ExpandMore, null) }; if (expanded) GroupSurface { ownedApps.filter { it.id != selected.id }.forEach { app -> Text(app.title, modifier = Modifier.fillMaxWidth().clickable { expanded = false; onSwitch(app.id) }.padding(14.dp), style = MaterialTheme.typography.labelLarge) } } } }

@Composable
private fun TesterRow(pair: ReciprocalCommitment, ownApp: OwnedApp, otherApp: PublicApp?, progress: CampaignProgress, onPair: (String) -> Unit, onReminder: (String, String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val theirs = pair.theyOweSignal ?: pair.youOweSignal
    Column(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MemberAvatar(pair.otherUsername)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("@${pair.otherUsername}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("They owe you: ${ownApp.title}", style = MaterialTheme.typography.bodySmall, color = Ink500)
            }
        }
        Text(progress.statusLabel(), style = MaterialTheme.typography.labelMedium, color = Ink500, modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.height(8.dp))
        EvidenceLabels(theirs, waitingForTester = true)
        if (theirs.activity == ActivitySignal.UNAVAILABLE) Text("Waiting for ${pair.otherUsername} to open TestLoop for the latest status.", style = MaterialTheme.typography.bodySmall, color = Ink500, modifier = Modifier.padding(top = 6.dp))
        if (expanded) {
            Spacer(Modifier.height(14.dp))
            Text("You owe them: ${otherApp?.title ?: "their app"}", style = MaterialTheme.typography.labelLarge)
            EvidenceLabels(pair.youOweSignal)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (pair.currentUserOwnAppId != null && otherApp != null) {
                    FilledTonalButton(onClick = { onPair(pair.commitmentId) }) { Text("Open their app") }
                }
                if (needsAttention(pair.theyOweSignal) && pair.youCanSendReminder) OutlinedButton(onClick = { onReminder(pair.commitmentId, pair.otherUsername) }) { Text("Send check-in") }
            }
            if (!pair.youCanSendReminder) Text("Check-in sent today", style = MaterialTheme.typography.labelMedium, color = Ink500, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun CompactFilterControl(selected: Int, onSelect: (Int) -> Unit, all: Int, needs: Int, onTrack: Int) {
    val labels = listOf("All $all", "Needs $needs", "On track $onTrack")
    Row(Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(14.dp)).background(Brand100).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        labels.forEachIndexed { index, label ->
            Box(Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(11.dp)).background(if (selected == index) MaterialTheme.colorScheme.surface else Color.Transparent).clickable(role = Role.RadioButton) { onSelect(index) }, contentAlignment = Alignment.Center) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected == index) Brand600 else Ink500, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

@Composable
fun UnavailableCommitmentScreen(onBack: () -> Unit) { Column(Modifier.fillMaxSize().navigationBarsPadding().padding(Gutter), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { Text("This test is no longer available", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); Text("The app was unpublished or the reciprocal app was removed, so there is no action to take here.", color = Ink500); Spacer(Modifier.height(16.dp)); Button(onClick = onBack) { Text("Go back") } } }

@Composable
fun MissingScreen(onBack: () -> Unit) { Column(Modifier.fillMaxSize().navigationBarsPadding().padding(Gutter), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { Text("This item is no longer available", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Spacer(Modifier.height(12.dp)); Button(onClick = onBack) { Text("Go back") } } }
