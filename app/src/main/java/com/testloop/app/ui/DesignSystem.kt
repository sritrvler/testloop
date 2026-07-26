package com.testloop.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.testloop.app.domain.DirectionSignal
import com.testloop.app.domain.ActivitySignal
import com.testloop.app.domain.InstallSignal
import com.testloop.app.ui.theme.Attention100
import com.testloop.app.ui.theme.Attention700
import com.testloop.app.ui.theme.Brand100
import com.testloop.app.ui.theme.Brand600
import com.testloop.app.ui.theme.Divider
import com.testloop.app.ui.theme.Ink500
import com.testloop.app.ui.theme.Success100
import com.testloop.app.ui.theme.Success700

internal val Gutter = 20.dp
/** Extra breathing room below scroll content so the last action clears OEM nav bars. */
internal val ScreenBottomReserve = 32.dp

@Composable
fun AppMonogram(title: String, modifier: Modifier = Modifier, small: Boolean = false) {
    val size = if (small) 36.dp else 48.dp
    val colors = listOf(Color(0xFFE1EAE3), Color(0xFFF4E2D6), Color(0xFFEFEAE1))
    val color = colors[(title.sumOf { it.code } % colors.size).coerceAtLeast(0)]
    Box(modifier.size(size).clip(RoundedCornerShape(if (small) 12.dp else 16.dp)).background(color), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.Apps, contentDescription = title, modifier = Modifier.size(if (small) 18.dp else 24.dp), tint = Brand600)
    }
}

@Composable
fun MemberAvatar(name: String, modifier: Modifier = Modifier) {
    Box(modifier.size(40.dp).clip(CircleShape).background(Brand100), contentAlignment = Alignment.Center) {
        Text(name.filter { it.isLetter() }.take(1).uppercase(), fontWeight = FontWeight.Bold, color = Brand600)
    }
}

@Composable
fun SignalLabel(signal: DirectionSignal, compact: Boolean = false, waitingForTester: Boolean = false) {
    val (text, icon, tint, background) = when {
        signal.install == InstallSignal.NOT_INSTALLED -> Quad("Install needed", Icons.Default.ErrorOutline, Attention700, Attention100)
        signal.install == InstallSignal.UNAVAILABLE -> Quad("Install check pending", Icons.Default.HourglassEmpty, Ink500, Brand100)
        signal.activity == ActivitySignal.OPENED_TODAY -> Quad("Opened today", Icons.Default.CheckCircle, Success700, Success100)
        signal.activity == ActivitySignal.NOT_DETECTED -> Quad("Not opened today", Icons.Default.ErrorOutline, Attention700, Attention100)
        signal.activity == ActivitySignal.UNAVAILABLE -> Quad(if (waitingForTester) "Waiting for TestLoop check" else "Open status unavailable", Icons.Default.ErrorOutline, Ink500, Brand100)
        else -> Quad("Installed", Icons.Default.CheckCircle, Success700, Success100)
    }
    Row(
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(background).padding(horizontal = 8.dp, vertical = 5.dp)
            .semantics { contentDescription = text },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, modifier = Modifier.size(16.dp), tint = tint)
        Spacer(Modifier.width(5.dp))
        Text(if (compact && text.length > 17) text.substringBefore(" today") else text, style = MaterialTheme.typography.labelMedium, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun InstallLabel(signal: DirectionSignal) {
    val (text, tint, background) = when (signal.install) {
        InstallSignal.INSTALLED -> Triple("Installed", Success700, Success100)
        InstallSignal.NOT_INSTALLED -> Triple("Not installed", Attention700, Attention100)
        InstallSignal.UNAVAILABLE -> Triple("Install check pending", Ink500, Brand100)
    }
    Text(text, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(background).padding(horizontal = 8.dp, vertical = 5.dp).semantics { contentDescription = text }, style = MaterialTheme.typography.labelMedium, color = tint)
}

/** Shows the two device signals that matter to a testing commitment. */
@Composable
fun EvidenceLabels(signal: DirectionSignal, compact: Boolean = true, waitingForTester: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        InstallLabel(signal)
        Spacer(Modifier.width(6.dp))
        ActivityLabel(signal, waitingForTester)
    }
}

@Composable
private fun ActivityLabel(signal: DirectionSignal, waitingForTester: Boolean = false) {
    val (text, tint, background) = when (signal.activity) {
        ActivitySignal.OPENED_TODAY -> Triple("Opened today", Success700, Success100)
        ActivitySignal.NOT_DETECTED -> Triple("Not opened today", Attention700, Attention100)
        ActivitySignal.UNAVAILABLE -> Triple(if (waitingForTester) "Waiting for TestLoop check" else "Open status unavailable", Ink500, Brand100)
    }
    Text(text, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(background).padding(horizontal = 8.dp, vertical = 5.dp).semantics { contentDescription = text }, style = MaterialTheme.typography.labelMedium, color = tint)
}

private data class Quad(val text: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val tint: Color, val background: Color)

@Composable
fun DayProgress(day: Int, target: Int, remaining: Int, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Day $day of $target", style = MaterialTheme.typography.labelMedium, color = Ink500)
            Text("$remaining days remaining", style = MaterialTheme.typography.labelMedium, color = Ink500)
        }
        Spacer(Modifier.height(7.dp))
        Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Brand100)) {
            Box(Modifier.fillMaxWidth((day.toFloat() / target).coerceIn(0f, 1f)).height(4.dp).background(Brand600))
        }
    }
}

@Composable
fun SectionHeader(title: String, detail: String? = null, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (detail != null) Text(detail, style = MaterialTheme.typography.labelMedium, color = Ink500)
        }
        if (action != null && onAction != null) Text(action, modifier = Modifier.clickable(role = Role.Button, onClick = onAction).padding(8.dp), color = Brand600, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun GroupSurface(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp, shadowElevation = 0.dp) {
        Column(content = content)
    }
}

@Composable
fun InlineNotice(text: String, isError: Boolean = false) {
    val background = if (isError) MaterialTheme.colorScheme.errorContainer else Brand100
    val contentColor = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(background).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (isError) Icons.Default.ErrorOutline else Icons.Default.PrivacyTip, null, tint = if (isError) MaterialTheme.colorScheme.error else Brand600, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(text, style = MaterialTheme.typography.bodySmall, color = contentColor)
    }
}

@Composable
fun RowDivider() = Box(Modifier.fillMaxWidth().height(1.dp).background(Divider))
