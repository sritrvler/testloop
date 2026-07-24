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
fun TodayRedesign(padding: PaddingValues, username: String, commitments: List<ReciprocalCommitment>, ownedApps: List<OwnedApp>, publicApps: List<PublicApp>, onPair: (String) -> Unit, onOwnApp: (String, Boolean) -> Unit, onNotifications: () -> Unit, unread: Int, onObligations: () -> Unit, onAddApp: () -> Unit, onDiscover: () -> Unit) {
    val today = LocalDate.now()
    val active = commitments.filter { it.lifecycle != com.testloop.app.domain.CommitmentLifecycle.LEFT && it.lifecycle != com.testloop.app.domain.CommitmentLifecycle.PENDING_SETUP }
    val tasks = active.sortedBy { it.youOweSignal.install == InstallSignal.INSTALLED }
    val opened = tasks.count { it.youOweSignal.install == InstallSignal.INSTALLED }
    val attentionByApp = active.filter { it.currentUserOwnAppId != null && needsAttention(it.theyOweSignal) }.groupBy { it.currentUserOwnAppId!! }
    val hasNoActivity = active.isEmpty()
    val firstRun = hasNoActivity && ownedApps.isEmpty()
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(horizontal = Gutter, vertical = 0.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { TestLoopTopBar("${timeGreeting()}, ${username.ifBlank { "there" }}", today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), unreadCount = unread, onNotifications = onNotifications) }
        if (firstRun) {
            item { FirstRunToday(onAddApp = onAddApp, onDiscover = onDiscover) }
        } else if (hasNoActivity) {
            item { ReadyToPairToday(onDiscover = onDiscover, onAddApp = onAddApp) }
        } else {
            item { MomentumPanel(opened, tasks.size) }
        }
        if (!firstRun) {
            item { SectionHeader("You owe them", if (tasks.isEmpty()) "No test apps yet" else "${tasks.size - opened} left", action = "All obligations", onAction = onObligations) }
            if (tasks.isNotEmpty()) item { GroupSurface { tasks.forEachIndexed { index, pair -> DailyTaskRow(pair, publicApps.firstOrNull { it.id == pair.appBeingTestedByCurrentUserId }, today, onPair); if (index != tasks.lastIndex) RowDivider() } } }
            else item { EmptyObligationHint(onDiscover) }
            item { SectionHeader("They owe you", if (attentionByApp.isEmpty()) "All on track" else "${attentionByApp.values.sumOf { it.size }} need attention") }
            item { GroupSurface { val entries = attentionByApp.entries.toList(); if (entries.isEmpty()) Text("All testers with active pairs have a positive signal today.", Modifier.padding(16.dp), color = Ink500) else entries.forEachIndexed { index, entry -> AttentionSummary(entry.key, entry.value, ownedApps, onOwnApp); if (index != entries.lastIndex) RowDivider() } } }
            item { InlineNotice("Install status is a device observation, not proof of Google Play testing or test quality.") }
        }
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
        Text("Find an app to test in Discover. If you also want testers for your own app, add it in My apps.", color = Ink500)
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
        GuideRow(Icons.Default.Explore, "Discover", "Join a test group and see the app you need to open.")
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
private fun MomentumPanel(opened: Int, total: Int) {
    val fraction = if (total == 0) 0f else opened.toFloat() / total
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Brand100).padding(18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(if (opened == total && total > 0) "All committed apps are installed" else "$opened of $total committed apps installed", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(5.dp))
                Text(if (total == 0) "Find an app to test when you are ready." else "${(total - opened).coerceAtLeast(0)} apps still need attention", color = Ink500)
            }
            Text("${(fraction * 100).toInt()}%", color = Brand600, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surface)) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Brand600))
        }
    }
}

@Composable
private fun DailyTaskRow(pair: ReciprocalCommitment, app: PublicApp?, today: LocalDate, onPair: (String) -> Unit) {
    val appName = app?.title ?: "Test app"
    val action = if (pair.youOweSignal.install == InstallSignal.NOT_INSTALLED) "Install" else "Open"
    Row(Modifier.fillMaxWidth().clickable { onPair(pair.commitmentId) }.padding(16.dp).semantics { contentDescription = "You owe them, $appName for ${pair.otherUsername}, ${pair.youOweSignal.install.label}" }, verticalAlignment = Alignment.CenterVertically) {
        AppMonogram(appName, small = true); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(appName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Text("You test $appName for ${pair.otherUsername}", style = MaterialTheme.typography.bodySmall, color = Ink500); Spacer(Modifier.height(7.dp)); SignalLabel(pair.youOweSignal, compact = true); Spacer(Modifier.height(7.dp)); Text("Day ${pair.elapsedDays(today)} of ${pair.targetDays} · ${pair.daysRemaining(today)} days remaining", style = MaterialTheme.typography.labelMedium, color = Ink500) }
        FilledTonalButton(onClick = { onPair(pair.commitmentId) }) { Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(action) }
    }
}

@Composable
private fun AttentionSummary(ownAppId: String, pairs: List<ReciprocalCommitment>, ownedApps: List<OwnedApp>, onOwnApp: (String, Boolean) -> Unit) {
    val app = ownedApps.firstOrNull { it.id == ownAppId } ?: return
    val notInstalled = pairs.count { it.theyOweSignal?.install == InstallSignal.NOT_INSTALLED }
    Row(Modifier.fillMaxWidth().clickable { onOwnApp(ownAppId, true) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { AppMonogram(app.title, small = true); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("${app.title}: ${pairs.size} people need attention", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Text("$notInstalled not installed", style = MaterialTheme.typography.bodySmall, color = Ink500) }; Icon(Icons.Default.ArrowForward, "Open ${app.title}", tint = Brand600) }
}

fun needsAttention(signal: com.testloop.app.domain.DirectionSignal?): Boolean = signal?.install != InstallSignal.INSTALLED
