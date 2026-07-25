package com.testloop.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.testloop.app.domain.AppNotification
import com.testloop.app.domain.ActivitySignal
import com.testloop.app.domain.Campaign
import com.testloop.app.domain.CampaignProgress
import com.testloop.app.domain.CampaignTestingStatus
import com.testloop.app.domain.CommitmentLifecycle
import com.testloop.app.domain.InstallSignal
import com.testloop.app.domain.OwnedApp
import com.testloop.app.domain.PublicApp
import com.testloop.app.domain.ReciprocalCommitment
import com.testloop.app.ui.theme.Brand100
import com.testloop.app.ui.theme.Brand600
import com.testloop.app.ui.theme.Ink500
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.LocalTime

@Composable
fun UsernameRedesign(error: String?, submitting: Boolean, onComplete: (String) -> Unit, onUsernameChanged: () -> Unit = {}) {
    var username by remember { mutableStateOf("") }
    val valid = username.matches(Regex("[a-zA-Z0-9_]{3,20}"))
    Column(Modifier.fillMaxSize().padding(Gutter), verticalArrangement = Arrangement.Center) {
        Text("Step 1 of 2", style = MaterialTheme.typography.labelLarge, color = Brand600)
        Spacer(Modifier.height(8.dp)); Text("Choose your public username", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp)); Text("Other members see this name. Your Google email stays private.", color = Ink500)
        Spacer(Modifier.height(24.dp)); Row(verticalAlignment = Alignment.CenterVertically) { MemberAvatar(if (username.isBlank()) "?" else username, Modifier.size(56.dp)); Spacer(Modifier.width(14.dp)); Text(if (username.isBlank()) "Your public profile" else "@$username", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.height(20.dp)); OutlinedTextField(username, { username = it.lowercase().filter { char -> char.isLetterOrDigit() || char == '_' }; onUsernameChanged() }, modifier = Modifier.fillMaxWidth(), label = { Text("Username") }, supportingText = { Text(error ?: if (username.isNotEmpty() && !valid) "Use 3 to 20 letters, numbers, or underscores." else "Choose a name other members can find you by.") }, isError = error != null || (username.isNotEmpty() && !valid), singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done))
        Spacer(Modifier.height(20.dp)); Button(onClick = { onComplete(username) }, enabled = valid && !submitting, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(if (submitting) "Checking username…" else "Use this username") }
    }
}

@Composable
fun TrackingSetup(usageAccessGranted: Boolean, onOpenUsageAccess: () -> Unit, onContinue: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(Gutter), verticalArrangement = Arrangement.Center) {
        Text("Step 2 of 2", style = MaterialTheme.typography.labelLarge, color = Brand600)
        Spacer(Modifier.height(8.dp)); Text("Allow daily open checks", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(12.dp)); Text("Enable Android Usage Access so the publisher can see whether you opened the specific app you committed to test today.", color = Ink500, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(18.dp)); Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Brand100).padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { AppMonogram("App"); Icon(Icons.Default.ArrowForward, null, tint = Brand600); Icon(Icons.Default.PrivacyTip, null, tint = Brand600, modifier = Modifier.size(38.dp)) }
        Spacer(Modifier.height(20.dp)); listOf("TestLoop records only a daily opened or not opened signal for active commitments.", "It does not upload minutes, raw usage history, or your full app list.", "Android will show its own Usage Access settings. Turn on TestLoop, then return here.").forEach { Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) { Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp), tint = Brand600); Spacer(Modifier.width(8.dp)); Text(it) } }
        Spacer(Modifier.height(20.dp)); if (!usageAccessGranted) {
            Button(onClick = onOpenUsageAccess, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Open Usage Access settings") }
        } else {
            FilledTonalButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().height(52.dp)) { Icon(Icons.Default.CheckCircle, null); Spacer(Modifier.width(8.dp)); Text("Usage Access enabled") }
        }
        Spacer(Modifier.height(12.dp)); Button(onClick = onContinue, enabled = usageAccessGranted, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Finish setup") }
    }
}

@Composable
fun TodayRedesign(padding: PaddingValues, username: String, dataLoaded: Boolean, commitments: List<ReciprocalCommitment>, ownedApps: List<OwnedApp>, campaigns: List<Campaign>, publicApps: List<PublicApp>, onPair: (String) -> Unit, onPrimaryAction: (String) -> Unit, onOwnApp: (String, Boolean) -> Unit, onNotifications: () -> Unit, unread: Int, onObligations: () -> Unit, onAddApp: () -> Unit, onDiscover: () -> Unit) {
    val today = LocalDate.now()
    // Delisted targets remain historical data, but are not current testing work.
    val active = commitments.filter {
        it.lifecycle == CommitmentLifecycle.ACTIVE &&
            (publicApps.any { app -> app.id == it.appBeingTestedByCurrentUserId } ||
                ownedApps.any { app -> app.id == it.appBeingTestedByCurrentUserId }) &&
            (it.currentUserOwnAppId == null || ownedApps.any { app -> app.id == it.currentUserOwnAppId })
    }
    val tasks = active
        .distinctBy { it.appBeingTestedByCurrentUserId to it.otherUserId }
        .sortedWith(compareBy<ReciprocalCommitment> { taskPriority(it) }.thenBy { it.joinedOn })
    val pendingTasks = tasks.filter(::needsYourAction)
    val tasksRemaining = tasks.count(::needsYourAction)
    val incoming = active.filter { pair ->
        pair.currentUserOwnAppId != null && ownedApps.any { it.id == pair.currentUserOwnAppId }
    }.groupBy { it.otherUserId to it.currentUserOwnAppId }.values.map { group ->
        group.firstOrNull { needsAttention(it.theyOweSignal) } ?: group.first()
    }
    val incomingNeedsAction = incoming.count { needsAttention(it.theyOweSignal) }
    val hasNoActivity = active.isEmpty()
    val firstRun = hasNoActivity && ownedApps.isEmpty()
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(horizontal = Gutter, vertical = 0.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { TestLoopTopBar("Today", "${timeGreeting()}, ${username.ifBlank { "there" }} · ${today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM"))}", unreadCount = unread, onNotifications = onNotifications) }
        if (!dataLoaded) {
            item { LoadingTodayState() }
        } else if (firstRun) {
            item { FirstRunToday(onAddApp = onAddApp, onDiscover = onDiscover) }
        } else if (hasNoActivity) {
            item { ReadyToPairToday(onDiscover = onDiscover, onAddApp = onAddApp) }
        } else {
            item { TodaySummaryCard(tasksRemaining, tasks.size) }
            item { SectionHeader("Your tasks today", if (tasksRemaining == 0) "Nothing else for you to do" else "$tasksRemaining app${if (tasksRemaining == 1) "" else "s"} still need you", action = "All obligations", onAction = onObligations) }
            item { if (pendingTasks.isEmpty()) EmptyTodayState() else GroupSurface { pendingTasks.forEachIndexed { index, pair -> DailyTaskRow(pair, targetAppTitle(pair, publicApps, ownedApps), targetProgress(pair, publicApps, ownedApps, campaigns), today, onPair, onPrimaryAction); if (index != pendingTasks.lastIndex) RowDivider() } } }
            item { SectionHeader("Waiting on other testers", if (incoming.isEmpty()) "No reciprocal apps yet" else if (incomingNeedsAction == 0) "All your testers are done today" else "$incomingNeedsAction person${if (incomingNeedsAction == 1) "" else "s"} ${if (incomingNeedsAction == 1) "needs" else "need"} attention") }
            item { if (incoming.isEmpty()) EmptyIncomingHint(onDiscover) else GroupSurface { incoming.forEachIndexed { index, pair -> PartnerStatusRow(pair, ownedApps, targetProgress(pair, publicApps, ownedApps, campaigns), today, onOwnApp, onPair); if (index != incoming.lastIndex) RowDivider() } } }
            if (incoming.isNotEmpty() && incomingNeedsAction == 0) item { AllTestersCaughtUp() }
            item { InlineNotice("The 14-day countdown starts only when every required tester is active at the same time. If active testers drop below the requirement, progress resets.") }
        }
    }
}

@Composable
private fun LoadingTodayState() {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = Brand600)
        Spacer(Modifier.height(12.dp))
        Text("Loading your testing plan…", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("We’re checking your apps and today’s commitments.", color = Ink500)
    }
}

@Composable
private fun AllTestersCaughtUp() {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Brand100).padding(16.dp)) {
        Text("All your testers have completed today’s commitments", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text("Everyone testing your apps is on track for today. No reminders are needed.", color = Ink500)
    }
}

private fun timeGreeting(time: LocalTime = LocalTime.now()): String = when (time.hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

@Composable
private fun FirstRunToday(onAddApp: () -> Unit, onDiscover: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Brand100).padding(20.dp)) {
            Text("Welcome to TestLoop", style = MaterialTheme.typography.labelLarge, color = Brand600, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Let’s get your first test moving.", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(8.dp))
            Text("Add your app to find testers, or discover an app you can test for someone else. You can do either first.", color = Ink500, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAddApp, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Add my app")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onDiscover, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Icon(Icons.Default.Explore, null); Spacer(Modifier.width(8.dp)); Text("Find an app to test")
            }
        }
        GettingStartedGuide()
    }
}

@Composable
private fun ReadyToPairToday(onDiscover: () -> Unit, onAddApp: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Brand100).padding(20.dp)) {
        Text("You’re ready for your first commitment", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("Explore apps available for testing and join one. In return, that publisher can test your app too.", color = Ink500)
        Spacer(Modifier.height(14.dp))
        Button(onClick = onDiscover, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Find an app to test") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onAddApp, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Add another app") }
    }
}

@Composable
private fun GettingStartedGuide() {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
        Text("How TestLoop works", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        GuideRow(Icons.Default.Apps, "My apps", "Add and monitor the apps you want testers for.")
        Spacer(Modifier.height(10.dp))
        GuideRow(Icons.Default.Explore, "Discover", "Explore apps available for testing, join a group, and get your app tested in return.")
    }
}

@Composable
private fun GuideRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = Brand600, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column { Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold); Text(body, style = MaterialTheme.typography.bodySmall, color = Ink500) }
    }
}

@Composable
private fun EmptyObligationHint(onDiscover: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
        Text("No test apps yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Your daily test list will appear here after you join an app in Discover.", color = Ink500)
        Spacer(Modifier.height(12.dp))
        FilledTonalButton(onClick = onDiscover) { Text("Explore apps") }
    }
}

@Composable
private fun TodaySummaryCard(remaining: Int, total: Int) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Brand100).padding(20.dp)) {
        Text(if (remaining == 0) "You're all caught up" else "$remaining app${if (remaining == 1) "" else "s"} need you today", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(if (remaining == 0) "You have completed today's testing for your ${if (total == 1) "committed app" else "$total committed apps"}." else "Use the action on each row below. TestLoop will show the app name, the person you're helping, and your testing day.", color = Ink500, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun DailyTaskRow(pair: ReciprocalCommitment, appName: String, progress: CampaignProgress, today: LocalDate, onPair: (String) -> Unit, onPrimaryAction: (String) -> Unit) {
    val action = when {
        pair.youOweSignal.install != InstallSignal.INSTALLED -> "Install"
        pair.youOweSignal.activity != ActivitySignal.OPENED_TODAY -> "Open"
        else -> "Open again"
    }
    Row(Modifier.fillMaxWidth().clickable { onPair(pair.commitmentId) }.padding(16.dp).semantics { contentDescription = "You owe them, $appName for ${pair.otherUsername}, ${pair.youOweSignal.install.label}" }, verticalAlignment = Alignment.CenterVertically) {
        AppMonogram(appName, small = true); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(appName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Text("Test for ${pair.otherUsername}", style = MaterialTheme.typography.bodySmall, color = Ink500); Spacer(Modifier.height(7.dp)); SignalLabel(pair.youOweSignal, compact = true); Spacer(Modifier.height(7.dp)); Text(progress.dayLabel(today), style = MaterialTheme.typography.labelMedium, color = Ink500) }
        FilledTonalButton(onClick = { onPrimaryAction(pair.commitmentId) }) { Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(action) }
    }
}

private fun targetAppTitle(pair: ReciprocalCommitment, publicApps: List<PublicApp>, ownedApps: List<OwnedApp>): String =
    publicApps.firstOrNull { it.id == pair.appBeingTestedByCurrentUserId }?.title
        ?: ownedApps.firstOrNull { it.id == pair.appBeingTestedByCurrentUserId }?.title
        ?: "Test app"

private fun targetProgress(pair: ReciprocalCommitment, publicApps: List<PublicApp>, ownedApps: List<OwnedApp>, campaigns: List<Campaign>): CampaignProgress {
    publicApps.firstOrNull { it.id == pair.appBeingTestedByCurrentUserId }?.let { return it.progress }
    ownedApps.firstOrNull { it.id == pair.currentUserOwnAppId }?.let { app ->
        campaigns.firstOrNull { it.ownedAppId == app.id }?.let { return it.progress }
    }
    return CampaignProgress(12, 0, pair.targetDays, CampaignTestingStatus.WAITING_FOR_TESTERS)
}

private fun CampaignProgress.dayLabel(today: LocalDate): String = when (status) {
    CampaignTestingStatus.WAITING_FOR_RELEASE -> "Waiting for approved release · $activeTesterCount/$requiredTesters installed"
    CampaignTestingStatus.WAITING_FOR_TESTERS -> "Waiting for $requiredTesters active testers · $activeTesterCount/$requiredTesters installed"
    CampaignTestingStatus.RESET_WAITING -> "Progress reset · $activeTesterCount/$requiredTesters active testers"
    CampaignTestingStatus.COMPLETE -> "$targetDays-day test complete · $activeTesterCount/$requiredTesters active testers"
    CampaignTestingStatus.IN_PROGRESS -> {
        val start = testingStartedAt?.atZone(java.time.ZoneId.systemDefault())?.toLocalDate() ?: today
        val day = (java.time.temporal.ChronoUnit.DAYS.between(start, today).toInt() + 1).coerceIn(1, targetDays)
        "Day $day of $targetDays · ${targetDays - day} days remaining · $activeTesterCount/$requiredTesters active"
    }
}

@Composable
private fun EmptyTodayState() {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Icon(Icons.Default.CheckCircle, null, tint = Brand600, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(8.dp))
        Text("Nothing else for you to do today", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text("Your committed apps have been opened today. You can open them again from All obligations if needed.", color = Ink500)
    }
}

@Composable
private fun PartnerStatusRow(pair: ReciprocalCommitment, ownedApps: List<OwnedApp>, progress: CampaignProgress, today: LocalDate, onOwnApp: (String, Boolean) -> Unit, onPair: (String) -> Unit) {
    val app = ownedApps.firstOrNull { it.id == pair.currentUserOwnAppId }
    val appTitle = app?.title ?: return
    val signal = pair.theyOweSignal
    Row(Modifier.fillMaxWidth().clickable { if (app != null) onOwnApp(app.id, true) else onPair(pair.commitmentId) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        AppMonogram(appTitle, small = true); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) {
            Text("${pair.otherUsername} · $appTitle", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("They test your app · ${progress.dayLabel(today)}", style = MaterialTheme.typography.bodySmall, color = Ink500)
            Spacer(Modifier.height(7.dp))
            if (signal == null) {
                Text("Waiting for ${pair.otherUsername} to open TestLoop", style = MaterialTheme.typography.labelMedium, color = Ink500)
            } else {
                SignalLabel(signal, compact = true, waitingForTester = true)
                if (signal.activity == ActivitySignal.UNAVAILABLE) {
                    Text("We’ll update today’s status when ${pair.otherUsername} opens TestLoop.", style = MaterialTheme.typography.bodySmall, color = Ink500, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        Icon(Icons.Default.ArrowForward, "See ${pair.otherUsername}'s testing status", tint = Brand600)
    }
}

@Composable
private fun EmptyIncomingHint(onDiscover: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text("No one is testing your apps yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text("When someone joins one of your app's tests, their name and today's status will appear here.", color = Ink500)
        Spacer(Modifier.height(12.dp))
        FilledTonalButton(onClick = onDiscover) { Text("Find an app to test") }
    }
}

private fun needsYourAction(pair: ReciprocalCommitment): Boolean = pair.youOweSignal.install != InstallSignal.INSTALLED || pair.youOweSignal.activity != ActivitySignal.OPENED_TODAY

private fun taskPriority(pair: ReciprocalCommitment): Int = when {
    pair.youOweSignal.install != InstallSignal.INSTALLED -> 0
    pair.youOweSignal.activity != ActivitySignal.OPENED_TODAY -> 1
    else -> 2
}

fun needsAttention(signal: com.testloop.app.domain.DirectionSignal?): Boolean = signal == null || signal.install != InstallSignal.INSTALLED || signal.activity != ActivitySignal.OPENED_TODAY
