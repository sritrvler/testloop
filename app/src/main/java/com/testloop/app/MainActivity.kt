package com.testloop.app

import android.os.Bundle
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import com.testloop.app.data.SupabaseAuthRepository
import com.testloop.app.data.SupabaseTestLoopRepository
import com.testloop.app.platform.AndroidExternalAppLauncher
import com.testloop.app.platform.FeedbackForm
import com.testloop.app.tracking.AndroidUsageAccessReporter
import com.testloop.app.ui.TestLoopApp
import com.testloop.app.ui.theme.TestLoopTheme
import com.testloop.app.notifications.PendingSetupReminder
import com.testloop.app.notifications.PendingSetupReminderScheduler

class MainActivity : ComponentActivity() {
    private val auth by lazy { SupabaseAuthRepository() }
    private val repository by lazy { SupabaseTestLoopRepository(applicationContext, auth) }
    private val chatRepository by lazy { com.testloop.app.data.SupabaseChatRepository(applicationContext, auth) }
    private val externalAppLauncher by lazy { AndroidExternalAppLauncher(applicationContext) }
    private val usageAccessReporter by lazy { AndroidUsageAccessReporter(applicationContext) }
    private val eveningReminderScheduler by lazy { com.testloop.app.notifications.EveningReminderScheduler(applicationContext) }
    private val pendingSetupReminderScheduler by lazy { PendingSetupReminderScheduler(applicationContext) }
    private var notificationPermissionAsked = false
    private var usageAccessGranted by mutableStateOf(false)
    private val requestNotificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Read AppOps before Compose's first frame so an already-authorized
        // signed-in user is not routed through the usage gate on cold launch.
        usageAccessGranted = usageAccessReporter.isGranted()
        lifecycleScope.launch {
            auth.awaitInitialization()
            auth.handleDeepLink(intent)
            setContent {
                TestLoopTheme {
                    TestLoopApp(
                        auth,
                        repository,
                        chatRepository,
                        externalAppLauncher,
                        FeedbackForm.url(),
                        usageAccessGranted,
                        onOpenUsageAccess = { usageAccessReporter.openSettings() },
                        isUsageAccessGrantedNow = { usageAccessReporter.isGranted() },
                        onScheduleEveningReminder = { pendingCount, singleAppName -> eveningReminderScheduler.schedule(pendingCount, singleAppName) },
                        onSyncPendingSetupReminders = { reminders -> pendingSetupReminderScheduler.sync(reminders) },
                        onRequestNotificationPermission = ::requestNotificationPermissionIfNeeded
                    )
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !notificationPermissionAsked &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionAsked = true
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        val hasUsageAccess = usageAccessReporter.isGranted()
        usageAccessGranted = hasUsageAccess
        // Settings returns to this callback. Persist the fresh permission state
        // before refreshing commitments so the active-commitment gate and the
        // server's enforcement window see the same round-trip result.
        if (!auth.account.value?.id.isNullOrBlank() && auth.account.value?.id != "pending") {
            lifecycleScope.launch {
                repository.refresh()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        lifecycleScope.launch { auth.handleDeepLink(intent) }
    }

}
