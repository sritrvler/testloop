package com.testloop.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
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
import androidx.compose.runtime.LaunchedEffect
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
import com.testloop.app.domain.ReciprocalCommitment
import com.testloop.app.domain.statusLabel
import com.testloop.app.ui.theme.Brand600
import com.testloop.app.ui.theme.Ink500
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.launch

private const val MIN_INSTRUCTIONS_LENGTH = 8
private const val MAX_INSTRUCTIONS_LENGTH = 500
private const val MIN_TESTERS = 1
private const val MAX_TESTERS = 100

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicAppDetail(app: PublicApp?, ownedApps: List<OwnedApp>, ownedAppIdsInUse: Set<String>, pendingCommitment: ReciprocalCommitment?, onBack: () -> Unit, onJoin: suspend (String, String?) -> Result<ReciprocalCommitment>, onJoined: (ReciprocalCommitment) -> Unit, onContinue: (String) -> Unit, onAdd: () -> Unit) {
    if (app == null) { MissingScreen(onBack); return }
    val isOwnerPreview = ownedApps.any { it.id == app.id }
    var showJoin by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        TestLoopTopBar(if (isOwnerPreview) "Preview ${app.title}" else "Test ${app.title}", "@${app.ownerUsername}", onBack = onBack)
        Column(Modifier.weight(1f).padding(Gutter), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { AppMonogram(app.title); Spacer(Modifier.width(12.dp)); Column { Text(app.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(app.progress.statusLabel(), color = Ink500); Text("${app.slotsRemaining} slots left · ${app.postedLabel}", color = Ink500, style = MaterialTheme.typography.bodySmall) } }
            SectionHeader("What you will test")
            GroupSurface { Column(Modifier.padding(16.dp)) { Text("1", color = Brand600, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Spacer(Modifier.height(6.dp)); Text(app.instructions, style = MaterialTheme.typography.bodyLarge) } }
            app.messagingId.takeIf(String::isNotBlank)?.let { messagingId ->
                GroupSurface {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("How to report a problem", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Report issues directly on the publisher's messaging platform using:", color = Ink500, style = MaterialTheme.typography.bodySmall)
                        Text(messagingId, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (isOwnerPreview) {
                GroupSurface {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("This is your app", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Preview only — you can’t join your own test. This is how your public listing appears to other testers.", color = Ink500, style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = { showJoin = true }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Preview tester actions") }
                        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Back to Discover") }
                    }
                }
            } else {
                Button(onClick = { if (pendingCommitment != null) onContinue(pendingCommitment.commitmentId) else showJoin = true }, modifier = Modifier.fillMaxWidth().height(52.dp), enabled = pendingCommitment != null || app.slotsRemaining > 0) { Text(if (pendingCommitment != null) "Continue setup" else if (app.slotsRemaining > 0) "Join and choose my app" else "Full") }
            }
        }
    }
    if (showJoin) {
        if (isOwnerPreview) {
            OwnerJoinPairPreviewSheet(app, onDismiss = { showJoin = false })
        } else {
            JoinPairSheet(app, ownedApps, ownedAppIdsInUse, onDismiss = { showJoin = false }, onJoin = { ownId -> onJoin(app.id, ownId) }, onJoined = { showJoin = false; onJoined(it) }, onAdd = { showJoin = false; onAdd() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OwnerJoinPairPreviewSheet(app: PublicApp, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f).navigationBarsPadding()) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Gutter, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Choose how to help", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("A tester sees these actions; you can’t join your own test.", color = Ink500, style = MaterialTheme.typography.bodyMedium)
                GroupSurface {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Test ${app.title}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("The tester opens the app, follows your instructions, and keeps the test active for the requested period.", color = Ink500, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text("A tester can either offer an app in return or volunteer without one.", color = Ink500)
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.RadioButton(selected = true, onClick = null)
                    Spacer(Modifier.width(10.dp))
                    Column { Text("No app, volunteer", fontWeight = FontWeight.SemiBold); Text("Help you without offering an app back", color = Ink500, style = MaterialTheme.typography.bodySmall) }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.RadioButton(selected = false, onClick = null)
                    Spacer(Modifier.width(10.dp)); AppMonogram("Their app", small = true); Spacer(Modifier.width(10.dp))
                    Column { Text("Offer an app in return", fontWeight = FontWeight.SemiBold); Text("The tester chooses one eligible app", color = Ink500, style = MaterialTheme.typography.bodySmall) }
                }
                GroupSurface {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Preview only", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("These controls are disabled. No commitment will be created and no app will be offered.", color = Ink500, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Preview only") }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Close preview") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JoinPairSheet(app: PublicApp, ownedApps: List<OwnedApp>, ownedAppIdsInUse: Set<String>, onDismiss: () -> Unit, onJoin: suspend (String?) -> Result<ReciprocalCommitment>, onJoined: (ReciprocalCommitment) -> Unit, onAdd: () -> Unit) {
    // The listing detail already showed the instructions. Start at the only
    // decision this sheet owns: which of the member's apps is offered back.
    var step by remember { mutableIntStateOf(2) }
    val availableApps = ownedApps.filterNot { it.id in ownedAppIdsInUse }
    val alreadyUsedApps = ownedApps.filter { it.id in ownedAppIdsInUse }
    var selectedAppId by remember { mutableStateOf<String?>(availableApps.firstOrNull()?.id) }
    var consent by remember { mutableStateOf(false) }
    var joining by remember { mutableStateOf(false) }
    var joinError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f).navigationBarsPadding()) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Gutter, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            when (step) {
                1 -> { Text("Test ${app.title}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(app.instructions, color = Ink500); Text(app.progress.statusLabel(), style = MaterialTheme.typography.labelLarge); Text("The 14-day countdown starts only after all ${app.requestedTesters} testers are active together. If participation drops, the owner decides whether to continue or restart.", color = Ink500) }
                2 -> {
                    Text("Choose how to help", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("You’ll test ${app.title}. You can also offer one of your apps back, or volunteer without a reciprocal app.", color = Ink500)
                    if (alreadyUsedApps.isNotEmpty()) {
                        GroupSurface {
                            Column(Modifier.padding(14.dp)) {
                                Text("Already being tested", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("${alreadyUsedApps.joinToString { it.title }} is already offered in another active commitment. Choose a different app or volunteer so you don’t create duplicate work.", color = Ink500, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (ownedApps.isEmpty()) {
                        Text("You have no app to offer, so this will be a volunteer commitment.", color = Ink500)
                    } else {
                        Row(Modifier.fillMaxWidth().clickable { selectedAppId = null }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.RadioButton(selected = selectedAppId == null, onClick = { selectedAppId = null })
                            Spacer(Modifier.width(10.dp))
                            Column { Text("No app, volunteer", fontWeight = FontWeight.SemiBold); Text("Help ${app.ownerUsername} without offering an app back", color = Ink500, style = MaterialTheme.typography.bodySmall) }
                        }
                        availableApps.forEach { own ->
                            Row(Modifier.fillMaxWidth().clickable { selectedAppId = own.id }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.material3.RadioButton(selected = selectedAppId == own.id, onClick = { selectedAppId = own.id })
                                Spacer(Modifier.width(10.dp)); AppMonogram(own.title, small = true); Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) { Text(own.title, fontWeight = FontWeight.SemiBold); Text("Offer this app in return", color = Ink500, style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                }
                else -> { val own = ownedApps.firstOrNull { it.id == selectedAppId }; Text("Confirm your commitment", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("You test: ${app.title}"); Text(if (own == null) "Volunteer: no app offered in return" else "@${app.ownerUsername} tests: ${own.title}"); Row(verticalAlignment = Alignment.Top) { Checkbox(consent, { consent = it }); Text("I understand that activity signals do not verify test quality or Google Play completion.", modifier = Modifier.padding(top = 12.dp)) } }
            }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (step) {
                    1 -> Button(onClick = { step = 2 }, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
                    2 -> if (ownedApps.isEmpty()) {
                        Button(onClick = { step = 3; selectedAppId = null }, modifier = Modifier.fillMaxWidth()) { Text("Continue as volunteer") }
                        OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Add my app first") }
                    } else {
                        Button(onClick = { step = 3 }, modifier = Modifier.fillMaxWidth()) { Text(if (selectedAppId == null) "Continue as volunteer" else "Continue with ${availableApps.firstOrNull { it.id == selectedAppId }?.title}") }
                        OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("Add another app") }
                    }
                    else -> {
                        joinError?.let { InlineNotice(it, isError = true) }
                        if (joining) InlineNotice("Joining this app and checking whether it is installed on your phone…")
                        Button(
                            onClick = {
                                joining = true
                                joinError = null
                                scope.launch {
                                    onJoin(selectedAppId)
                                        .onSuccess(onJoined)
                                        .onFailure {
                                            joining = false
                                            joinError = userFriendlyError(it, "We couldn't join this app right now. Check your connection and try again.")
                                        }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = consent && !joining
                        ) { Text(if (joining) "Joining…" else "Join commitment") }
                    }
                }
            }
        }
    }
}

@Composable
fun AddAppFlow(onBack: () -> Unit, onPublished: suspend (OwnedApp, Campaign) -> Result<Unit>) {
    var step by remember { mutableIntStateOf(1) }
    var title by remember { mutableStateOf("") }
    var installUrl by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("") }
    var groupUrl by remember { mutableStateOf("") }
    var optInUrl by remember { mutableStateOf("") }
    var testerCount by remember { mutableStateOf("12") }
    var instructions by remember { mutableStateOf("") }
    var messagingId by remember { mutableStateOf("") }
    var publishing by remember { mutableStateOf(false) }
    var publishError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val id = remember { UUID.randomUUID().toString() }
    val handleBack: () -> Unit = {
        if (step > 1) step-- else onBack()
    }
    BackHandler(enabled = !publishing) { handleBack() }
    LaunchedEffect(step) { publishError = null }
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        TestLoopTopBar("Add app", "Step $step of 3", onBack = handleBack)
        Column(Modifier.weight(1f).padding(Gutter), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            when (step) {
                1 -> {
                    val installLinkError = if (installUrl.isNotBlank() && PlayLinkValidator.packageFromInstallLink(installUrl) == null) {
                        "Invalid format. Example: https://play.google.com/store/apps/details?id=com.example.app"
                    } else null
                    Text("App identity", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Field(title, { title = it }, "App display name")
                    Field(installUrl, { installUrl = it; packageName = PlayLinkValidator.packageFromInstallLink(it).orEmpty() }, "Play install link", error = installLinkError)
                    Field(packageName, {}, "Package ID", "Extracted automatically from the Play install link", readOnly = true)
                    InlineNotice(if (packageName.isBlank()) "Paste a valid Google Play install link to detect the package ID." else "Package ID detected: $packageName")
                }
                2 -> {
                    val groupLinkValid = PlayLinkValidator.isGroupLink(groupUrl)
                    val optInLinkValid = optInUrl.isBlank() || PlayLinkValidator.isTestingLink(optInUrl, packageName)
                    val testerCountValid = testerCount.toIntOrNull()?.let { it in MIN_TESTERS..MAX_TESTERS } == true
                    val groupLinkError = if (groupUrl.isNotBlank() && !groupLinkValid) {
                        "Invalid format. Example: https://groups.google.com/g/your-group-name"
                    } else null
                    val optInLinkError = if (optInUrl.isNotBlank() && !optInLinkValid) {
                        "Invalid format. Example: https://play.google.com/apps/testing/com.example.app"
                    } else null
                    val testerCountError = if (testerCount.isNotBlank() && !testerCountValid) {
                        "Enter a whole number between $MIN_TESTERS and $MAX_TESTERS."
                    } else null
                    Text("Closed-test access", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    GroupSurface { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Plan for a smooth test", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Use Closed testing, not Internal or Open testing. Aim for 14-15 opted-in testers so you have a buffer if someone drops out. Check Google Play Console, then confirm in TestLoop that closed testing is live; the 14-day timer starts only after that confirmation and all required installs are detected.", color = Ink500, style = MaterialTheme.typography.bodySmall)
                    } }
                    Field(groupUrl, { groupUrl = it }, "Testing group link", "Google Groups link · required", error = groupLinkError)
                    Field(optInUrl, { optInUrl = it }, "Play testing opt-in link", "Optional · use the link from Play Console", error = optInLinkError)
                    Field(testerCount, { testerCount = it.filter(Char::isDigit) }, "Testers needed", keyboard = KeyboardType.Number, error = testerCountError)
                    InlineNotice(
                        when {
                            !groupLinkValid -> "Enter a valid Google Groups link to continue."
                            !optInLinkValid -> "The Play opt-in link must match $packageName."
                            !testerCountValid -> "Enter between $MIN_TESTERS and $MAX_TESTERS testers."
                            else -> "The Play opt-in link is optional and must match $packageName."
                        }
                    )
                }
                else -> {
                    Text("App description and tester contact", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Field(instructions, { instructions = it.take(MAX_INSTRUCTIONS_LENGTH) }, "Tell testers about your app", "${instructions.length}/$MAX_INSTRUCTIONS_LENGTH characters · Briefly describe your app and what testers should try", lines = 5)
                    Field(messagingId, { messagingId = it.take(160) }, "Messaging ID (recommended)", "Optional · e.g. Telegram @handle, Discord username, or WhatsApp number. This is visible to testers for direct reports.")
                    GroupSurface { Column(Modifier.padding(16.dp)) { Text("Preview", style = MaterialTheme.typography.labelLarge, color = Brand600); Text(title.ifBlank { "Your app" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("${testerCount.ifBlank { "0" }} tester target", color = Ink500) } }
                }
            }
            Spacer(Modifier.weight(1f)); val canContinue = when (step) { 1 -> title.length > 1 && PlayLinkValidator.isInstallLink(installUrl, packageName) && packageName.contains('.'); 2 -> PlayLinkValidator.isGroupLink(groupUrl) && (optInUrl.isBlank() || PlayLinkValidator.isTestingLink(optInUrl, packageName)) && testerCount.toIntOrNull()?.let { it in MIN_TESTERS..MAX_TESTERS } == true; else -> instructions.length in MIN_INSTRUCTIONS_LENGTH..MAX_INSTRUCTIONS_LENGTH }
            if (step == 3) publishError?.let { InlineNotice(it, isError = true) }
            Button(onClick = {
                if (step < 3) step++ else {
                    publishing = true
                    publishError = null
                    val app = OwnedApp("own-$id", title, packageName, installUrl.trim(), optInUrl.trim().ifBlank { null }, title, "campaign-$id", instructions.trim(), groupUrl = groupUrl.trim(), messagingId = messagingId.trim())
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
private fun Field(value: String, onChange: (String) -> Unit, label: String, support: String? = null, lines: Int = 1, keyboard: KeyboardType = KeyboardType.Text, readOnly: Boolean = false, error: String? = null) {
    OutlinedTextField(
        value,
        onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        supportingText = (error ?: support)?.let { message ->
            { Text(message, color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        minLines = lines,
        singleLine = lines == 1,
        isError = error != null,
        readOnly = readOnly,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard)
    )
}

@Composable
fun NotificationsRedesign(notifications: List<AppNotification>, onBack: () -> Unit, onOpenPair: (String) -> Unit) { Column(Modifier.fillMaxSize().navigationBarsPadding()) { TestLoopTopBar("Notifications", onBack = onBack); LazyColumn(contentPadding = PaddingValues(Gutter), verticalArrangement = Arrangement.spacedBy(16.dp)) { listOf("Needs action" to notifications.filter { it.isUnread }, "Updates" to notifications.filter { !it.isUnread }).forEach { (label, entries) -> if (entries.isNotEmpty()) { item { SectionHeader(label) }; item { GroupSurface { entries.forEachIndexed { index, notification -> NotificationRow(notification, onOpenPair); if (index != entries.lastIndex) RowDivider() } } } } } } } }

@Composable
private fun NotificationRow(notification: AppNotification, onOpenPair: (String) -> Unit) { Row(Modifier.fillMaxWidth().clickable { notification.commitmentId?.let(onOpenPair) }.padding(16.dp), verticalAlignment = Alignment.Top) { Icon(Icons.Default.Notifications, null, tint = Brand600); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(notification.title, fontWeight = FontWeight.SemiBold); Text(notification.message, style = MaterialTheme.typography.bodySmall, color = Ink500); Text(notification.relativeTime, style = MaterialTheme.typography.labelSmall, color = Ink500, modifier = Modifier.padding(top = 5.dp)) }; if (notification.isUnread) androidx.compose.material3.Badge() } }

@Composable
fun ProfileRedesign(padding: PaddingValues, username: String, usageAccessGranted: Boolean, onNotifications: () -> Unit, onFeedback: () -> Unit, onTestingGuide: () -> Unit, unread: Int) {
    val helpTitle = "Help and report a problem"
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(Gutter), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { TestLoopTopBar("Profile", unreadCount = unread, onNotifications = onNotifications) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MemberAvatar(username)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("@${username.ifBlank { "you" }}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Your Google email stays private", color = Ink500)
                }
            }
        }
        item {
            GroupSurface {
                listOf(
                    "Install checks" to "First check is manual; later checks refresh automatically",
                    "Notifications" to "Manage reminders and quiet hours",
                    "Privacy and data" to "TestLoop checks only committed apps",
                    "Blocked members" to "No blocked members",
                    "Google Play testing guide" to "Understand the 12 testers · 14 days requirement",
                    helpTitle to "Tell us what happened or what you’d like to see",
                ).forEachIndexed { index, (title, body) ->
                    val isHelp = title == helpTitle
                    val isTestingGuide = title == "Google Play testing guide"
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = isHelp || isTestingGuide, onClick = if (isTestingGuide) onTestingGuide else onFeedback)
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(title, fontWeight = FontWeight.SemiBold)
                                Text(body, style = MaterialTheme.typography.bodySmall, color = Ink500)
                            }
                            if (isHelp) Icon(Icons.Default.ChatBubbleOutline, contentDescription = "Send feedback", tint = Brand600)
                            if (isTestingGuide) Icon(Icons.Default.ArrowForward, contentDescription = "Open Google Play testing guide", tint = Brand600)
                        }
                    }
                    if (index != 5) RowDivider()
                }
            }
        }
        item { Text("Delete account", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(12.dp)) }
    }
}

@Composable
fun GooglePlayTestingGuide(padding: PaddingValues, onBack: () -> Unit, onOpenOfficialGuide: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().navigationBarsPadding().padding(padding), contentPadding = PaddingValues(Gutter), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { TestLoopTopBar("Google Play testing guide", "A simple path to production access", onBack = onBack) }
        item {
            GroupSurface { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Before you start", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                GuideBullet("Use the Closed testing track. Internal and Open testing do not count for this requirement.")
                GuideBullet("Plan for 14–15 testers, not exactly 12. The extra buffer helps if someone opts out or stops participating.")
                GuideBullet("Ask each tester to accept the invite and opt in using your testing link.")
            } }
        }
        item {
            GroupSurface { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("While you wait", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                GuideBullet("Keep at least 12 testers opted in for 14 consecutive days. If you fall below 12, your progress may reset.")
                GuideBullet("Check Google Play Console directly under your app to see whether the release has been reviewed and approved. TestLoop cannot confirm Google’s approval.")
                GuideBullet("Have testers use the app like real users. Collect feedback, fix important issues, and keep notes for the production-access form.")
            } }
        }
        item { Button(onClick = onOpenOfficialGuide, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.OpenInNew, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Read Google’s official requirements")
        } }
    }
}

@Composable
private fun GuideBullet(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text("•", color = Brand600, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Ink500)
    }
}
