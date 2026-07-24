package com.testloop.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.testloop.app.domain.AppNotification
import com.testloop.app.domain.Campaign
import com.testloop.app.domain.NotificationKind
import com.testloop.app.domain.OwnedApp
import com.testloop.app.domain.PublicApp
import com.testloop.app.ui.theme.Brand600
import com.testloop.app.ui.theme.Ink500
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.launch

private const val MIN_INSTRUCTIONS_LENGTH = 8
private const val MAX_INSTRUCTIONS_LENGTH = 256
private const val MAX_DETAILS_LENGTH = 256

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicAppDetail(app: PublicApp?, ownedApps: List<OwnedApp>, pendingCommitment: com.testloop.app.domain.ReciprocalCommitment?, onBack: () -> Unit, onJoin: (String, String?) -> Unit, onContinue: (String) -> Unit, onAdd: () -> Unit) {
    if (app == null) { MissingScreen(onBack); return }
    var showJoin by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        TestLoopTopBar("Test ${app.title}", "@${app.ownerUsername}", onBack = onBack)
        Column(Modifier.weight(1f).padding(Gutter), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { AppMonogram(app.title); Spacer(Modifier.width(12.dp)); Column { Text(app.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("${app.slotsRemaining} slots left · ${app.postedLabel}", color = Ink500) } }
            SectionHeader("What you will test")
            GroupSurface { Column(Modifier.padding(16.dp)) { Text("1", color = Brand600, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Spacer(Modifier.height(6.dp)); Text(app.instructions, style = MaterialTheme.typography.bodyLarge) } }
            Spacer(Modifier.weight(1f))
            Button(onClick = { if (pendingCommitment != null) onContinue(pendingCommitment.commitmentId) else showJoin = true }, modifier = Modifier.fillMaxWidth().height(52.dp), enabled = pendingCommitment != null || app.slotsRemaining > 0) { Text(if (pendingCommitment != null) "Continue setup" else if (app.slotsRemaining > 0) "Join and choose my app" else "Full") }
        }
    }
    if (showJoin) JoinPairSheet(app, ownedApps, onDismiss = { showJoin = false }, onJoin = { ownId -> showJoin = false; onJoin(app.id, ownId) }, onAdd = { showJoin = false; onAdd() })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JoinPairSheet(app: PublicApp, ownedApps: List<OwnedApp>, onDismiss: () -> Unit, onJoin: (String?) -> Unit, onAdd: () -> Unit) {
    // The listing detail already showed the instructions. Start at the only
    // decision this sheet owns: which of the member's apps is offered back.
    var step by remember { mutableIntStateOf(2) }
    var selectedAppId by remember { mutableStateOf<String?>(ownedApps.firstOrNull()?.id) }
    var consent by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (step) {
                1 -> { Text("Test ${app.title}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(app.instructions, color = Ink500); Text("Target: 14 days with @${app.ownerUsername}", style = MaterialTheme.typography.labelLarge); Button(onClick = { step = 2 }, modifier = Modifier.fillMaxWidth()) { Text("Continue") } }
                2 -> { Text("Which of your apps should @${app.ownerUsername} test?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); if (ownedApps.isEmpty()) { Text("Reciprocal commitments need an app from each member.", color = Ink500); Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Add my app") } } else { ownedApps.forEach { own -> Row(Modifier.fillMaxWidth().clickable { selectedAppId = own.id }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { androidx.compose.material3.RadioButton(selected = selectedAppId == own.id, onClick = { selectedAppId = own.id }); Spacer(Modifier.width(10.dp)); AppMonogram(own.title, small = true); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(own.title, fontWeight = FontWeight.SemiBold); Text("Active campaign", color = Ink500, style = MaterialTheme.typography.bodySmall) } } }; Button(onClick = { step = 3 }, modifier = Modifier.fillMaxWidth(), enabled = selectedAppId != null) { Text("Pair with ${ownedApps.firstOrNull { it.id == selectedAppId }?.title}") }; OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("Add another app") } } }
                else -> { val own = ownedApps.firstOrNull { it.id == selectedAppId }; Text("Confirm your pair", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("You test: ${app.title}"); Text("@${app.ownerUsername} tests: ${own?.title}"); Text("Target: 14 days", color = Ink500); Row(verticalAlignment = Alignment.Top) { Checkbox(consent, { consent = it }); Text("I understand that activity signals do not verify test quality or Google Play completion.", modifier = Modifier.padding(top = 12.dp)) }; Button(onClick = { onJoin(selectedAppId) }, modifier = Modifier.fillMaxWidth(), enabled = consent) { Text("Join commitment") } }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun AddAppFlow(onBack: () -> Unit, onPublished: suspend (OwnedApp, Campaign) -> Result<Unit>) {
    var step by remember { mutableIntStateOf(1) }
    var title by remember { mutableStateOf("") }
    var installUrl by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("") }
    var optInUrl by remember { mutableStateOf("") }
    var testerCount by remember { mutableStateOf("12") }
    var instructions by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var publishing by remember { mutableStateOf(false) }
    var publishError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val id = remember { UUID.randomUUID().toString() }
    Column(Modifier.fillMaxSize()) {
        TestLoopTopBar("Add app", "Step $step of 3", onBack = onBack)
        Column(Modifier.weight(1f).padding(Gutter), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            when (step) {
                1 -> { Text("App identity", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Field(title, { title = it }, "App display name"); Field(installUrl, { installUrl = it; PlayLinkValidator.packageFromInstallLink(it)?.let { parsed -> packageName = parsed } }, "Play install link"); Field(packageName, { packageName = it }, "Package ID", "Extracted from your install link. You can correct it.") }
                2 -> { Text("Closed-test access", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Field(optInUrl, { optInUrl = it }, "Play testing opt-in link"); Field(testerCount, { testerCount = it.filter(Char::isDigit) }, "Testers needed", keyboard = KeyboardType.Number); InlineNotice("Use direct Google Play links that match ${if (packageName.isBlank()) "your package ID" else packageName}.") }
                else -> {
                    Text("Test brief", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Field(instructions, { instructions = it.take(MAX_INSTRUCTIONS_LENGTH) }, "What should testers try?", "${instructions.length}/$MAX_INSTRUCTIONS_LENGTH characters · Minimum $MIN_INSTRUCTIONS_LENGTH", lines = 3)
                    Field(details, { details = it.take(MAX_DETAILS_LENGTH) }, "Extra details", "${details.length}/$MAX_DETAILS_LENGTH characters · Optional", lines = 3)
                    GroupSurface { Column(Modifier.padding(16.dp)) { Text("Preview", style = MaterialTheme.typography.labelLarge, color = Brand600); Text(title.ifBlank { "Your app" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("${testerCount.ifBlank { "0" }} tester target", color = Ink500) } }
                }
            }
            Spacer(Modifier.weight(1f)); val canContinue = when (step) { 1 -> title.length > 1 && PlayLinkValidator.isInstallLink(installUrl, packageName) && packageName.contains('.'); 2 -> PlayLinkValidator.isTestingLink(optInUrl, packageName) && testerCount.toIntOrNull() != null; else -> instructions.length in MIN_INSTRUCTIONS_LENGTH..MAX_INSTRUCTIONS_LENGTH && details.length <= MAX_DETAILS_LENGTH }
            publishError?.let { InlineNotice(it) }
            Button(onClick = {
                if (step < 3) step++ else {
                    publishing = true
                    publishError = null
                    val app = OwnedApp("own-$id", title, packageName, installUrl, optInUrl, title, "campaign-$id", details)
                    val campaign = Campaign("campaign-$id", "own-$id", testerCount.toInt(), 0, LocalDate.now(), instructions = instructions)
                    scope.launch {
                        onPublished(app, campaign).onFailure { publishError = friendlyPublishError(it) }
                        publishing = false
                    }
                }
            }, modifier = Modifier.fillMaxWidth().height(52.dp), enabled = canContinue && !publishing) { Text(if (publishing) "Publishing…" else if (step == 3) "Publish app" else "Continue") }
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, support: String? = null, lines: Int = 1, keyboard: KeyboardType = KeyboardType.Text) { OutlinedTextField(value, onChange, modifier = Modifier.fillMaxWidth(), label = { Text(label) }, supportingText = support?.let { { Text(it) } }, minLines = lines, singleLine = lines == 1, keyboardOptions = KeyboardOptions(keyboardType = keyboard)) }

@Composable
fun NotificationsRedesign(notifications: List<AppNotification>, onBack: () -> Unit, onOpenPair: (String) -> Unit) { Column(Modifier.fillMaxSize()) { TestLoopTopBar("Notifications", onBack = onBack); LazyColumn(contentPadding = PaddingValues(Gutter), verticalArrangement = Arrangement.spacedBy(16.dp)) { listOf("Needs action" to notifications.filter { it.isUnread }, "Updates" to notifications.filter { !it.isUnread }).forEach { (label, entries) -> if (entries.isNotEmpty()) { item { SectionHeader(label) }; item { GroupSurface { entries.forEachIndexed { index, notification -> NotificationRow(notification, onOpenPair); if (index != entries.lastIndex) RowDivider() } } } } } } } }

@Composable
private fun NotificationRow(notification: AppNotification, onOpenPair: (String) -> Unit) { Row(Modifier.fillMaxWidth().clickable { notification.commitmentId?.let(onOpenPair) }.padding(16.dp), verticalAlignment = Alignment.Top) { Icon(Icons.Default.Notifications, null, tint = Brand600); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(notification.title, fontWeight = FontWeight.SemiBold); Text(notification.message, style = MaterialTheme.typography.bodySmall, color = Ink500); Text(notification.relativeTime, style = MaterialTheme.typography.labelSmall, color = Ink500, modifier = Modifier.padding(top = 5.dp)) }; if (notification.isUnread) androidx.compose.material3.Badge() } }

@Composable
fun ProfileRedesign(padding: PaddingValues, username: String, usageAccessGranted: Boolean, onNotifications: () -> Unit, unread: Int) { LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(Gutter), verticalArrangement = Arrangement.spacedBy(16.dp)) { item { TestLoopTopBar("Profile", unreadCount = unread, onNotifications = onNotifications) }; item { Row(verticalAlignment = Alignment.CenterVertically) { MemberAvatar(username); Spacer(Modifier.width(12.dp)); Column { Text("@${username.ifBlank { "you" }}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("Your Google email stays private", color = Ink500) } } }; item { GroupSurface { listOf("Install checks" to "Checked when you return to TestLoop", "Notifications" to "Manage reminders and quiet hours", "Privacy and data" to "TestLoop checks only committed apps", "Blocked members" to "No blocked members", "Help and report a problem" to "Get support").forEachIndexed { index, (title, body) -> Column(Modifier.padding(16.dp)) { Text(title, fontWeight = FontWeight.SemiBold); Text(body, style = MaterialTheme.typography.bodySmall, color = Ink500) }; if (index != 4) RowDivider() } } }; item { Text("Delete account", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(12.dp)) } } }
