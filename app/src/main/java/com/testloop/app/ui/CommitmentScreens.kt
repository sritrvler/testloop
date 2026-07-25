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
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.testloop.app.domain.Campaign
import com.testloop.app.domain.ActivitySignal
import com.testloop.app.domain.CampaignProgress
import com.testloop.app.domain.CampaignTestingStatus
import com.testloop.app.domain.statusLabel
import com.testloop.app.domain.CommitmentLifecycle
import com.testloop.app.domain.DirectionSignal
import com.testloop.app.domain.InstallSignal
import com.testloop.app.domain.OwnedApp
import com.testloop.app.domain.PublicApp
import com.testloop.app.domain.ReciprocalCommitment
import com.testloop.app.ui.theme.Brand100
import com.testloop.app.ui.theme.Brand600
import com.testloop.app.ui.theme.Ink500
import java.time.LocalDate
import kotlinx.coroutines.launch

@Composable
fun PairDetailScreen(pair: ReciprocalCommitment?, ownedApps: List<OwnedApp>, campaigns: List<Campaign>, publicApps: List<PublicApp>, onBack: () -> Unit, onOpen: (String) -> Unit, onOpenLink: (String) -> Unit, onReminder: (String, String) -> Unit) {
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
    Column(Modifier.fillMaxSize()) {
        TestLoopTopBar("Reciprocal pair", if (volunteer) "Volunteer tester for ${ownApp.title}" else "${otherApp?.title ?: "Test app"} for ${ownApp?.title ?: "one-way commitment"}", onBack = onBack)
        LazyColumn(contentPadding = PaddingValues(Gutter), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { PairHeader(otherApp?.title ?: "Volunteer", ownApp?.title, pair.otherUsername) }
            item { CampaignProgressBlock(progress, today) }
            if (otherApp != null) item { DirectionBlock("You owe them", "You test ${otherApp.title} for ${pair.otherUsername}", pair.youOweSignal, pair, true) }
            if (ownApp != null && pair.theyOweSignal != null) item { DirectionBlock("They owe you", "${pair.otherUsername} tests ${ownApp.title} for you", pair.theyOweSignal, pair, false) }
            item { InlineNotice("Install status is a device observation, not proof of Google Play testing or test quality.") }
            if (otherApp != null) item {
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
            if (pair.theyOweSignal != null && needsAttention(pair.theyOweSignal)) item {
                if (pair.youCanSendReminder) OutlinedButton(onClick = { confirmReminder = true }, modifier = Modifier.fillMaxWidth()) { Text("Send a quick check-in") } else Text("Check-in sent today", modifier = Modifier.padding(12.dp), color = Ink500, style = MaterialTheme.typography.labelLarge)
            }
            item { Text("Report issue", color = Ink500, style = MaterialTheme.typography.labelLarge); Text("Leave commitment", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp)) }
        }
    }
    if (confirmReminder) AlertDialog(onDismissRequest = { confirmReminder = false }, title = { Text("Send @${pair.otherUsername} a helpful check-in?") }, text = { Text("We’ll let them know that opening TestLoop helps update today’s testing status for ${ownApp?.title ?: "your app"}. They can do it when convenient.") }, confirmButton = { Button(onClick = { confirmReminder = false; onReminder(pair.commitmentId, pair.otherUsername) }) { Text("Send check-in") } }, dismissButton = { OutlinedButton(onClick = { confirmReminder = false }) { Text("Not now") } })
}

@Composable
fun CommitmentReadyScreen(commitment: ReciprocalCommitment?, publicApps: List<PublicApp>, ownedApps: List<OwnedApp>, isBookmarked: Boolean, onBack: () -> Unit, onOpenLink: (String) -> Unit, onBookmark: suspend (String) -> Result<Unit>, onRemoveBookmark: suspend (String) -> Result<Unit>) {
    if (commitment == null) { MissingScreen(onBack); return }
    val app = publicApps.firstOrNull { it.id == commitment.appBeingTestedByCurrentUserId }
    val ownApp = ownedApps.firstOrNull { it.id == commitment.currentUserOwnAppId }
    var shownBookmarked by remember(isBookmarked) { mutableStateOf(isBookmarked) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        TestLoopTopBar("You're almost ready", app?.title ?: "Test app", onBack = onBack)
        LazyColumn(contentPadding = PaddingValues(Gutter), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                GroupSurface {
                    Column(Modifier.padding(18.dp)) {
                        Text("Install ${app?.title ?: "the app"} and join its test group.", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("You will be counted as a tester after TestLoop detects the committed app on this phone.", color = Ink500, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            item {
                Button(onClick = { app?.groupUrl?.takeIf(String::isNotBlank)?.let(onOpenLink) }, modifier = Modifier.fillMaxWidth().height(52.dp), enabled = app?.groupUrl?.isNotBlank() == true) { Text("Join testing group") }
                Spacer(Modifier.height(8.dp))
                app?.optInUrl?.let { optInUrl ->
                    OutlinedButton(onClick = { onOpenLink(optInUrl) }, modifier = Modifier.fillMaxWidth()) { Text("Opt in to Play test") }
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedButton(onClick = { app?.let { onOpenLink(it.installUrl) } }, modifier = Modifier.fillMaxWidth(), enabled = app != null) { Text("Install app") }
                Spacer(Modifier.height(8.dp))
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
                        Icon(if (shownBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, if (shownBookmarked) "Remove bookmark" else "Bookmark ${app?.title ?: "app"}", tint = Brand600)
                    }
                }
            }
            item {
                Text("Your reciprocal app", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(if (ownApp == null) "This is a volunteer commitment. You are helping ${commitment.otherUsername} without offering an app in return." else "@${commitment.otherUsername} will test ${ownApp.title} after this commitment becomes active.", color = Ink500)
            }
            item { InlineNotice("Installing is the only step needed for TestLoop to count you. App quality and Google Play approval are not verified here.") }
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
    CampaignTestingStatus.WAITING_FOR_RELEASE -> progress.statusLabel()
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
    GroupSurface {
        Column(Modifier.padding(16.dp)) {
            Text(progressLabel(progress, today), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            when (progress.status) {
                CampaignTestingStatus.WAITING_FOR_RELEASE -> Text("Mark the Google Play release as approved before the 14-day clock can begin.", color = Ink500)
                CampaignTestingStatus.WAITING_FOR_TESTERS -> Text("The 14-day countdown has not started. All ${progress.requiredTesters} testers must be active at the same time.", color = Ink500)
                CampaignTestingStatus.RESET_WAITING -> Text("A tester dropped out or became inactive, so the 14-day countdown reset. It will restart when all required testers are active again.", color = Ink500)
                CampaignTestingStatus.IN_PROGRESS -> DayProgress(progressDay(progress, today), progress.targetDays, (progress.targetDays - progressDay(progress, today)).coerceAtLeast(0))
                CampaignTestingStatus.COMPLETE -> Text("The required overlap was maintained for the full testing window.", color = Ink500)
            }
        }
    }
}

@Composable
fun ObligationsScreen(commitments: List<ReciprocalCommitment>, ownedApps: List<OwnedApp>, campaigns: List<Campaign>, publicApps: List<PublicApp>, onBack: () -> Unit, onPair: (String) -> Unit) {
    val today = LocalDate.now()
    val visible = commitments.filter {
        it.lifecycle != CommitmentLifecycle.PENDING_SETUP &&
            (publicApps.any { app -> app.id == it.appBeingTestedByCurrentUserId } ||
                ownedApps.any { app -> app.id == it.appBeingTestedByCurrentUserId }) &&
            (it.currentUserOwnAppId == null || ownedApps.any { app -> app.id == it.currentUserOwnAppId })
    }
    val youOwe = visible.distinctBy { it.appBeingTestedByCurrentUserId to it.otherUserId }
    val theyOwe = visible.filter { pair ->
        pair.currentUserOwnAppId != null && ownedApps.any { it.id == pair.currentUserOwnAppId }
    }.distinctBy { it.currentUserOwnAppId to it.otherUserId }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(Gutter), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
fun OwnAppDetailScreen(appId: String, attentionOnly: Boolean, ownedApps: List<OwnedApp>, campaigns: List<Campaign>, commitments: List<ReciprocalCommitment>, publicApps: List<PublicApp>, onBack: () -> Unit, onPair: (String) -> Unit, onReminder: (String, String) -> Unit, onSwitch: (String) -> Unit, onDelist: suspend (String) -> Result<Unit>, onMarkPublished: suspend (String) -> Result<Unit>, onUpdateTesterCount: suspend (String, Int) -> Result<Unit>) {
    val app = ownedApps.firstOrNull { it.id == appId } ?: run { MissingScreen(onBack); return }
    val campaign = campaigns.firstOrNull { it.ownedAppId == appId }
    val pairs = commitments.filter { it.currentUserOwnAppId == appId && it.lifecycle != CommitmentLifecycle.PENDING_SETUP }
        .distinctBy { it.otherUserId to it.currentUserOwnAppId }
    var filter by remember { mutableIntStateOf(if (attentionOnly) 1 else 0) }
    var confirmDelist by remember { mutableStateOf(false) }
    var delisting by remember { mutableStateOf(false) }
    var markingPublished by remember { mutableStateOf(false) }
    var showEditTesterTarget by remember { mutableStateOf(false) }
    var testerTargetText by remember(campaign?.requestedTesters) { mutableStateOf(campaign?.requestedTesters?.toString().orEmpty()) }
    var updatingTesterTarget by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val enrolledTesterCount = campaign?.reservedTesterCount ?: 0
    val minimumTesterTarget = enrolledTesterCount.coerceAtLeast(1)
    val displayed = if (filter == 1) pairs.filter { needsAttention(it.theyOweSignal) } else if (filter == 2) pairs.filter { !needsAttention(it.theyOweSignal) } else pairs
    Column(Modifier.fillMaxSize()) {
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
            item { Text("${campaign?.slotsRemaining ?: 0} slots left", style = MaterialTheme.typography.labelLarge, color = Ink500) }
            item {
                if (app.playStorePublishedAt == null) Text("Google Play status: not marked as published", style = MaterialTheme.typography.labelMedium, color = Ink500)
                else Text("Published on Google Play", style = MaterialTheme.typography.labelLarge, color = Brand600)
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
                                "Minimum $enrolledTesterCount — ${if (enrolledTesterCount == 1) "1 tester is" else "$enrolledTesterCount testers are"} already enrolled. Maximum 100."
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
                FilledTonalButton(onClick = { onPair(pair.commitmentId) }) { Text("Open their app") }
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
fun UnavailableCommitmentScreen(onBack: () -> Unit) { Column(Modifier.fillMaxSize().padding(Gutter), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { Text("This test is no longer available", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); Text("The app was unpublished or the reciprocal app was removed, so there is no action to take here.", color = Ink500); Spacer(Modifier.height(16.dp)); Button(onClick = onBack) { Text("Go back") } } }

@Composable
fun MissingScreen(onBack: () -> Unit) { Column(Modifier.fillMaxSize().padding(Gutter), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { Text("This item is no longer available", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Spacer(Modifier.height(12.dp)); Button(onClick = onBack) { Text("Go back") } } }
