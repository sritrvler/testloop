package com.testloop.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.testloop.app.domain.UsageAccessStatus
import com.testloop.app.ui.theme.Brand600
import com.testloop.app.ui.theme.Ink500
import java.time.Instant

@Composable
fun UsageAccessRequiredScreen(status: UsageAccessStatus, onOpenUsageAccess: () -> Unit) {
    val deadlinePassed = status.deadlineAt?.isBefore(Instant.now()) == true
    Column(
        Modifier.fillMaxSize().padding(Gutter),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.PrivacyTip, contentDescription = null, tint = Brand600, modifier = Modifier.height(48.dp))
        Spacer(Modifier.height(20.dp))
        Text("Usage Access is required", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            if (deadlinePassed) "Usage Access was not restored in time, so your test listing has been paused. Restore access to continue using TestLoop."
            else "TestLoop cannot verify your testing commitments without Android Usage Access. Restore it within 6 hours or your test listing will be paused.",
            color = Ink500,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onOpenUsageAccess, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("Give Usage Access")
        }
    }
}
