package com.testloop.app

import android.os.Bundle
import android.content.Intent
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
import com.testloop.app.tracking.AndroidUsageAccessReporter
import com.testloop.app.ui.TestLoopApp
import com.testloop.app.ui.theme.TestLoopTheme

class MainActivity : ComponentActivity() {
    private val auth by lazy { SupabaseAuthRepository() }
    private val repository by lazy { SupabaseTestLoopRepository(applicationContext, auth) }
    private val externalAppLauncher by lazy { AndroidExternalAppLauncher(applicationContext) }
    private val usageAccessReporter by lazy { AndroidUsageAccessReporter(applicationContext) }
    private var usageAccessGranted by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            auth.awaitInitialization()
            auth.handleDeepLink(intent)
            setContent {
                TestLoopTheme {
                    TestLoopApp(
                        auth,
                        repository,
                        externalAppLauncher,
                        usageAccessGranted,
                        onOpenUsageAccess = { usageAccessReporter.openSettings() },
                        isUsageAccessGrantedNow = { usageAccessReporter.isGranted() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        usageAccessGranted = usageAccessReporter.isGranted()
        if (auth.account.value != null) lifecycleScope.launch { repository.refresh() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        lifecycleScope.launch { auth.handleDeepLink(intent) }
    }

}
