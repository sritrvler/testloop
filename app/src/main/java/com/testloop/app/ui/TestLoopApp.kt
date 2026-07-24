package com.testloop.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.testloop.app.data.AuthRepository
import com.testloop.app.data.TestLoopRepository
import com.testloop.app.domain.AppNotification
import com.testloop.app.domain.AppBookmark
import com.testloop.app.domain.InstallSignal
import com.testloop.app.domain.OwnedApp
import com.testloop.app.domain.PublicApp
import com.testloop.app.domain.ReciprocalCommitment
import com.testloop.app.domain.UsageAccessStatus
import com.testloop.app.ui.theme.Brand600
import com.testloop.app.ui.theme.Ink500
import com.testloop.app.platform.ExternalAppLauncher
import com.testloop.app.platform.LaunchResult
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private enum class MainDestination(val label: String) { TODAY("Today"), APPS("My apps"), DISCOVER("Discover"), PROFILE("Profile") }
private sealed interface AppScreen {
    data object Welcome : AppScreen
    data object Username : AppScreen
    data object Tracking : AppScreen
    data class Main(val destination: MainDestination = MainDestination.TODAY) : AppScreen
    data class PublicDetail(val appId: String) : AppScreen
    data class CommitmentReady(val commitmentId: String) : AppScreen
    data class PairDetail(val commitmentId: String) : AppScreen
    data class OwnDetail(val appId: String, val attentionOnly: Boolean = false) : AppScreen
    data object Obligations : AppScreen
    data object Notifications : AppScreen
    data object AddApp : AppScreen
}

@Composable
fun TestLoopApp(auth: AuthRepository, repo: TestLoopRepository, externalAppLauncher: ExternalAppLauncher, usageAccessGranted: Boolean, onOpenUsageAccess: () -> Unit, isUsageAccessGrantedNow: () -> Boolean) {
    val account by auth.account.collectAsState()
    val providerAuthError by auth.authError.collectAsState()
    val ownedApps by repo.ownedApps.collectAsState()
    val campaigns by repo.campaigns.collectAsState()
    val discoverApps by repo.discoverApps.collectAsState()
    val commitments by repo.commitments.collectAsState()
    val notifications by repo.notifications.collectAsState()
    val bookmarks by repo.bookmarks.collectAsState()
    val usageAccessStatus by repo.usageAccess.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbars = remember { SnackbarHostState() }
    var screenHistory by remember { mutableStateOf(listOf<AppScreen>(AppScreen.Welcome)) }
    var authError by remember { mutableStateOf<String?>(null) }
    var usernameError by remember { mutableStateOf<String?>(null) }
    var savingUsername by remember { mutableStateOf(false) }
    var liveUsageAccessGranted by remember { mutableStateOf(usageAccessGranted) }
    fun snack(text: String) = scope.launch { snackbars.showSnackbar(text) }
    fun navigate(next: AppScreen) {
        if (screenHistory.lastOrNull() != next) screenHistory = screenHistory + next
    }
    fun replace(next: AppScreen) {
        screenHistory = (screenHistory.dropLast(1) + next).ifEmpty { listOf(next) }
    }
    fun back() {
        screenHistory = if (screenHistory.size > 1) screenHistory.dropLast(1) else listOf(AppScreen.Main())
    }
    val screen = screenHistory.last()

    LaunchedEffect(usageAccessGranted) {
        liveUsageAccessGranted = usageAccessGranted
    }

    BackHandler(enabled = screen !is AppScreen.Welcome && screen !is AppScreen.Main && liveUsageAccessGranted) {
        back()
    }

    LaunchedEffect(account) {
        if (account != null && screen == AppScreen.Welcome) {
            replace(if (account?.username.isNullOrBlank()) AppScreen.Username else AppScreen.Main())
        }
        if (account != null && !account!!.id.equals("pending", ignoreCase = true)) {
            repo.refresh().onFailure { authError = userFriendlyError(it, "We couldn't load your apps right now. Check your connection and try again.") }
        }
    }

    LaunchedEffect(account?.id) {
        if (account != null && !account!!.id.equals("pending", ignoreCase = true)) {
            while (isActive) {
                delay(30_000)
                repo.refresh()
            }
        }
    }

    LaunchedEffect(account?.id, usageAccessGranted) {
        if (account != null && !account!!.id.equals("pending", ignoreCase = true)) {
            repo.syncUsageAccess(liveUsageAccessGranted)
        }
    }

    LaunchedEffect(account?.id) {
        if (account != null && !account!!.id.equals("pending", ignoreCase = true)) {
            liveUsageAccessGranted = isUsageAccessGrantedNow()
            repo.syncUsageAccess(liveUsageAccessGranted)
        }
    }

    LaunchedEffect(providerAuthError) {
        providerAuthError?.let { authError = it }
    }

    fun performPrimaryAction(id: String) {
        scope.launch {
            val pair = commitments.firstOrNull { it.commitmentId == id }
            val publicApp = pair?.let { selected -> discoverApps.firstOrNull { it.id == selected.appBeingTestedByCurrentUserId } }
            if (pair == null || publicApp == null) {
                snack("This test app is no longer available. Open the commitment to review it.")
                return@launch
            }

            if (pair.youOweSignal.install != InstallSignal.INSTALLED) {
                when (externalAppLauncher.openUrl(publicApp.installUrl)) {
                    LaunchResult.Opened -> snack("Opening Google Play for ${publicApp.title}. Install it, then come back to TestLoop.")
                    else -> snack("We couldn't open Google Play. Open the commitment to see the install link.")
                }
                return@launch
            }

            val result = externalAppLauncher.launch(publicApp.packageName)
            if (result != LaunchResult.Unavailable) repo.reportInstallStatus(id)
            snack(when (result) {
                LaunchResult.Opened -> "Opening ${publicApp.title}. Use it for today's test, then come back here."
                LaunchResult.NotInstalled -> "${publicApp.title} isn't installed on this phone. Tap Install to get it."
                LaunchResult.NotLaunchable -> "${publicApp.title} is installed but Android can't launch it. Open the commitment to review the app link."
                LaunchResult.Unavailable -> "Android couldn't open ${publicApp.title} right now. Try again."
            })
        }
    }

    if (account != null && !liveUsageAccessGranted) {
        UsageAccessRequiredScreen(usageAccessStatus, onOpenUsageAccess)
    } else when (val current = screen) {
        AppScreen.Welcome -> WelcomeRedesign(
            error = authError,
            onSignIn = {
                scope.launch {
                    auth.clearAuthError()
                    auth.beginGoogleSignIn()
                        .onSuccess {
                            authError = null
                        }
                        .onFailure { authError = userFriendlyError(it, "We couldn't sign you in. Check your connection and try again.") }
                }
            }
        )
        AppScreen.Username -> UsernameRedesign(error = usernameError, submitting = savingUsername, onUsernameChanged = { usernameError = null }, onComplete = { username ->
            usernameError = null
            savingUsername = true
            scope.launch {
                auth.setUsername(username)
                    .onSuccess { replace(AppScreen.Main()) }
                    .onFailure { usernameError = userFriendlyError(it, "We couldn't save your username. Check it and try again.") }
                savingUsername = false
            }
        })
        AppScreen.Tracking -> TrackingSetup(usageAccessGranted = liveUsageAccessGranted, onOpenUsageAccess = onOpenUsageAccess, onContinue = { replace(AppScreen.Main()) })
        is AppScreen.Main -> MainShell(current.destination, account?.username.orEmpty(), liveUsageAccessGranted, ownedApps, campaigns, discoverApps, commitments, bookmarks, notifications, snackbars,
            onDestination = { replace(AppScreen.Main(it)) }, onNotification = { navigate(AppScreen.Notifications) }, onPair = { navigate(AppScreen.PairDetail(it)) }, onPrimaryAction = ::performPrimaryAction, onOwnApp = { id, attention -> navigate(AppScreen.OwnDetail(id, attention)) }, onDiscover = { navigate(AppScreen.PublicDetail(it)) }, onBookmark = { id -> repo.bookmarkApp(id).onSuccess { snack("Saved to bookmarks — you're ready to find it again") }.onFailure { snack(userFriendlyError(it, "We couldn't save this bookmark. Check your connection and try again.")) } }, onRemoveBookmark = { id -> repo.removeBookmark(id).onSuccess { snack("Bookmark removed") }.onFailure { snack(userFriendlyError(it, "We couldn't remove this bookmark. Check your connection and try again.")) } }, onAddApp = { navigate(AppScreen.AddApp) }, onObligations = { navigate(AppScreen.Obligations) })
        is AppScreen.PublicDetail -> PublicAppDetail(
            discoverApps.firstOrNull { it.id == current.appId }, ownedApps,
            pendingCommitment = commitments.firstOrNull { it.appBeingTestedByCurrentUserId == current.appId && it.lifecycle == com.testloop.app.domain.CommitmentLifecycle.PENDING_SETUP },
            onBack = ::back,
            onJoin = { publicId, ownId -> scope.launch { val result = repo.joinCampaign(publicId, ownId); result.onSuccess { navigate(AppScreen.CommitmentReady(it.commitmentId)); snack("You're in! Your test commitment with @${it.otherUsername} is ready 🎉") }.onFailure { snack(userFriendlyError(it, "We couldn't create this commitment. The app may no longer be available—go back and try another one.")) } } },
            onContinue = { id -> navigate(AppScreen.CommitmentReady(id)) },
            onAdd = { navigate(AppScreen.AddApp) }
        )
        is AppScreen.CommitmentReady -> CommitmentReadyScreen(commitments.firstOrNull { it.commitmentId == current.commitmentId }, discoverApps, ownedApps, isBookmarked = commitments.firstOrNull { it.commitmentId == current.commitmentId }?.let { pair -> bookmarks.any { it.appId == pair.appBeingTestedByCurrentUserId } } == true, onBack = ::back, onOpenLink = { url ->
            when (externalAppLauncher.openUrl(url)) {
                LaunchResult.Opened -> Unit
                else -> snack("This link couldn't be opened. Make sure Google Play is installed, then try again.")
            }
        }, onBookmark = { id -> repo.bookmarkApp(id).onSuccess { snack("Saved to bookmarks — you're ready to find it again") }.onFailure { snack(userFriendlyError(it, "We couldn't save this bookmark. Check your connection and try again.")) } }, onRemoveBookmark = { id -> repo.removeBookmark(id).onSuccess { snack("Bookmark removed") }.onFailure { snack(userFriendlyError(it, "We couldn't remove this bookmark. Check your connection and try again.")) } })
        is AppScreen.PairDetail -> PairDetailScreen(commitments.firstOrNull { it.commitmentId == current.commitmentId }, ownedApps, discoverApps, onBack = ::back, onOpen = ::performPrimaryAction, onOpenLink = { url ->
            when (externalAppLauncher.openUrl(url)) {
                LaunchResult.Opened -> Unit
                else -> snack("This link couldn't be opened. Make sure Google Play is installed, then try again.")
            }
        }, onReminder = { id, person -> scope.launch { repo.sendReminder(id); snack("Reminder sent to @$person — they'll see it today") } })
        is AppScreen.OwnDetail -> OwnAppDetailScreen(current.appId, current.attentionOnly, ownedApps, campaigns, commitments, discoverApps, onBack = ::back, onPair = { navigate(AppScreen.PairDetail(it)) }, onReminder = { id, person -> scope.launch { repo.sendReminder(id).onSuccess { snack("Reminder sent to @$person — they'll see it today") }.onFailure { snack(userFriendlyError(it, "We couldn't send the reminder. Check your connection and try again.")) } } }, onSwitch = { replace(AppScreen.OwnDetail(it)) }, onDelist = { id -> repo.delistOwnedApp(id).onSuccess { replace(AppScreen.Main(MainDestination.APPS)); snack("Your app has been removed from Discover") }.onFailure { snack(userFriendlyError(it, "We couldn't remove this app from Discover. Check your connection and try again.")) } }, onMarkPublished = { id -> repo.markOwnedAppPublished(id).onSuccess { snack("Your app has been successfully published on Google Play! 🎉") }.onFailure { snack(userFriendlyError(it, "We couldn't update your Google Play status. Check your connection and try again.")) } })
        AppScreen.Obligations -> ObligationsScreen(commitments, ownedApps, discoverApps, onBack = ::back, onPair = { navigate(AppScreen.PairDetail(it)) })
        AppScreen.Notifications -> NotificationsRedesign(notifications, onBack = ::back, onOpenPair = { navigate(AppScreen.PairDetail(it)) })
        AppScreen.AddApp -> AddAppFlow(onBack = ::back, onPublished = { app, campaign ->
            repo.addOwnedApp(app, campaign).onSuccess {
                replace(AppScreen.Main(MainDestination.APPS))
                snack("Your app has been successfully published! Testers can now discover ${app.title} 🎉")
            }
        })
    }
}

@Composable
private fun MainShell(destination: MainDestination, username: String, usageAccessGranted: Boolean, ownedApps: List<OwnedApp>, campaigns: List<com.testloop.app.domain.Campaign>, discoverApps: List<PublicApp>, commitments: List<ReciprocalCommitment>, bookmarks: List<AppBookmark>, notifications: List<AppNotification>, snackbars: SnackbarHostState, onDestination: (MainDestination) -> Unit, onNotification: () -> Unit, onPair: (String) -> Unit, onPrimaryAction: (String) -> Unit, onOwnApp: (String, Boolean) -> Unit, onDiscover: (String) -> Unit, onBookmark: suspend (String) -> Result<Unit>, onRemoveBookmark: suspend (String) -> Result<Unit>, onAddApp: () -> Unit, onObligations: () -> Unit) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background, snackbarHost = { SnackbarHost(snackbars) }, bottomBar = { BottomNavigation(destination, onDestination) }) { padding ->
        // The top app bars apply their own status-bar inset. Keep only room for
        // the bottom navigation so the main content does not get a duplicate top gap.
        val contentPadding = PaddingValues(bottom = 96.dp)
        when (destination) {
            MainDestination.TODAY -> TodayRedesign(contentPadding, username, commitments, ownedApps, discoverApps, onPair, onPrimaryAction, onOwnApp, onNotification, notifications.count { it.isUnread }, onObligations = onObligations, onAddApp = onAddApp, onDiscover = { onDestination(MainDestination.DISCOVER) })
            MainDestination.APPS -> MyAppsScreen(contentPadding, ownedApps, campaigns, commitments, onOwnApp, onAddApp, onDiscover = { onDestination(MainDestination.DISCOVER) }, onNotifications = onNotification, unread = notifications.count { it.isUnread })
            MainDestination.DISCOVER -> DiscoverRedesign(contentPadding, discoverApps, commitments, bookmarks, onDiscover, onBookmark = onBookmark, onRemoveBookmark = onRemoveBookmark, onAddApp = onAddApp, onNotifications = onNotification, unread = notifications.count { it.isUnread })
            MainDestination.PROFILE -> ProfileRedesign(padding, username, usageAccessGranted, onNotification, notifications.count { it.isUnread })
        }
    }
}

@Composable
private fun BottomNavigation(selected: MainDestination, onSelect: (MainDestination) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        listOf(MainDestination.TODAY to Icons.Default.Dashboard, MainDestination.APPS to Icons.Default.Apps, MainDestination.DISCOVER to Icons.Default.Explore, MainDestination.PROFILE to Icons.Default.Person).forEach { (destination, icon) ->
            NavigationBarItem(selected = selected == destination, onClick = { onSelect(destination) }, icon = { Icon(icon, null) }, label = { Text(destination.label) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestLoopTopBar(title: String, subtitle: String? = null, action: String? = null, onAction: (() -> Unit)? = null, unreadCount: Int = 0, onNotifications: (() -> Unit)? = null, onBack: (() -> Unit)? = null, actionContent: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Column { Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelMedium, color = Ink500) } },
        navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
        actions = {
            if (action != null && onAction != null) Text(action, modifier = Modifier.padding(horizontal = 8.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onAction).padding(8.dp), color = Brand600, style = MaterialTheme.typography.labelLarge)
            actionContent()
            if (onNotifications != null) IconButton(onClick = onNotifications) { BadgedBox(badge = { if (unreadCount > 0) Badge() }) { Icon(Icons.Default.Notifications, "Notifications") } }
        }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
    )
}

@Composable
private fun WelcomeRedesign(error: String?, onSignIn: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().padding(Gutter),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("TESTLOOP", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Brand600)
                Spacer(Modifier.width(12.dp))
                androidx.compose.material3.HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
            }
            Spacer(Modifier.height(72.dp))
            Text("A clearer way to keep your testing promises.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(16.dp))
            Text("TestLoop keeps the small agreements behind a release visible, without turning them into a chore.", color = Ink500, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(36.dp))
            androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(20.dp))
            Text("For every app you join, you will see", style = MaterialTheme.typography.labelLarge, color = Brand600)
            Spacer(Modifier.height(12.dp))
            listOf("the app to open today", "whether it is installed", "the person waiting on your app").forEach { line ->
                Row(Modifier.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("•", color = Brand600, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(12.dp))
                    Text(line, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        Column {
            Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(10.dp)) {
                Icon(Icons.Default.AccountCircle, null)
                Spacer(Modifier.width(8.dp))
                Text("Continue with Google")
            }
            if (error != null) {
                Text(error, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            Text("Your email stays private. You choose what to share.", modifier = Modifier.fillMaxWidth().padding(top = 14.dp), style = MaterialTheme.typography.labelMedium, color = Ink500, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}
