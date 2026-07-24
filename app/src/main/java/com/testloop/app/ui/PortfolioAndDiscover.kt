package com.testloop.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.testloop.app.domain.Campaign
import com.testloop.app.domain.AppBookmark
import com.testloop.app.domain.OwnedApp
import com.testloop.app.domain.PublicApp
import com.testloop.app.domain.ReciprocalCommitment
import com.testloop.app.ui.theme.Brand600
import com.testloop.app.ui.theme.Ink500
import java.time.Duration
import java.time.Instant

@Composable
fun MyAppsScreen(padding: PaddingValues, ownedApps: List<OwnedApp>, campaigns: List<Campaign>, commitments: List<ReciprocalCommitment>, onOwnApp: (String, Boolean) -> Unit, onAdd: () -> Unit, onDiscover: () -> Unit, onNotifications: () -> Unit, unread: Int) {
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(horizontal = Gutter, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { TestLoopTopBar("My apps", "${ownedApps.size} active apps", action = "Add app", onAction = onAdd, unreadCount = unread, onNotifications = onNotifications) }
        if (ownedApps.isEmpty()) item { PortfolioEmpty(onAdd, onDiscover) }
        else item {
            GroupSurface {
                ownedApps.forEachIndexed { index, app ->
                    val campaign = campaigns.firstOrNull { it.ownedAppId == app.id }
                    val pairs = commitments.filter { it.currentUserOwnAppId == app.id && it.lifecycle != com.testloop.app.domain.CommitmentLifecycle.PENDING_SETUP }
                    OwnAppRow(app, campaign, pairs, onOwnApp)
                    if (index != ownedApps.lastIndex) RowDivider()
                }
            }
        }
        if (ownedApps.isNotEmpty()) item { Text("Each app has its own campaign and reciprocal pairs.", style = MaterialTheme.typography.labelMedium, color = Ink500) }
    }
}

@Composable
private fun OwnAppRow(app: OwnedApp, campaign: Campaign?, pairs: List<ReciprocalCommitment>, onOwnApp: (String, Boolean) -> Unit) {
    val count = campaign?.joinedTesterCount ?: 0
    val requested = campaign?.requestedTesters ?: 0
    val attention = pairs.count { needsAttention(it.theyOweSignal) }
    Row(Modifier.fillMaxWidth().clickable { onOwnApp(app.id, false) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        AppMonogram(app.title); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) {
            Text(app.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("$count of $requested testers · ${(campaign?.slotsRemaining ?: 0)} slots left", style = MaterialTheme.typography.bodySmall, color = Ink500)
            Spacer(Modifier.height(9.dp)); androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(4.dp).background(com.testloop.app.ui.theme.Brand100)) { androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth(if (requested == 0) 0f else count.toFloat() / requested).height(4.dp).background(Brand600)) }
            Spacer(Modifier.height(8.dp)); Text(if (attention == 0) "All active today" else "$attention need attention", style = MaterialTheme.typography.labelMedium, color = if (attention == 0) com.testloop.app.ui.theme.Success700 else com.testloop.app.ui.theme.Attention700)
        }
        Icon(Icons.Default.ArrowForward, "Manage ${app.title}", tint = Brand600)
    }
}

@Composable
private fun PortfolioEmpty(onAdd: () -> Unit, onDiscover: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AppMonogram("My apps")
        Spacer(Modifier.height(14.dp))
        Text("Add the app you need tested", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Create a listing, choose how many testers you need, and share the Play links in one place.", color = Ink500)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Add your first app") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onDiscover, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Find an app to test") }
    }
}

@Composable
fun DiscoverRedesign(padding: PaddingValues, apps: List<PublicApp>, commitments: List<ReciprocalCommitment>, bookmarks: List<AppBookmark>, onOpen: (String) -> Unit, onBookmark: (String) -> Unit, onRemoveBookmark: (String) -> Unit, onAddApp: () -> Unit, onNotifications: () -> Unit, unread: Int) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("Open slots") }
    var showBookmarksOnly by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    val joinedAppIds = commitments.filter { it.lifecycle != com.testloop.app.domain.CommitmentLifecycle.PENDING_SETUP }.mapTo(mutableSetOf()) { it.appBeingTestedByCurrentUserId }
    val bookmarkedById = bookmarks.associateBy { it.appId }
    val filtered = apps
        .filter { it.id !in joinedAppIds }
        .filter { !showBookmarksOnly || it.id in bookmarkedById }
        .filter { it.title.contains(query, true) || it.ownerUsername.contains(query, true) || it.packageName.contains(query, true) }
        .filter { showBookmarksOnly || if (filter == "Full") it.slotsRemaining == 0 else it.slotsRemaining > 0 }
        .let { if (filter == "Fewest slots") it.sortedBy { app -> app.slotsRemaining } else it }
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(horizontal = Gutter, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { TestLoopTopBar("Discover", "Find an app to test", unreadCount = unread, onNotifications = onNotifications) }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                Box {
                    IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, "Discover menu") }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(text = { Text("All apps") }, onClick = { showBookmarksOnly = false; menuExpanded = false })
                        DropdownMenuItem(text = { Text("Bookmarks (${bookmarks.size})") }, onClick = { showBookmarksOnly = true; menuExpanded = false })
                    }
                }
            }
        }
        item { OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Search apps or usernames") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Open slots", "New", "Fewest slots", "Full").forEach { label -> FilterChip(selected = filter == label, onClick = { filter = label }, label = { Text(label) }) } } }
        if (filtered.isEmpty()) item { DiscoverEmptyState(apps.isNotEmpty(), onAddApp) }
        else item { GroupSurface { filtered.forEachIndexed { index, app -> DiscoverRow(app, bookmarkedById[app.id], showBookmarksOnly, onOpen, onBookmark, onRemoveBookmark); if (index != filtered.lastIndex) RowDivider() } } }
    }
}

@Composable
private fun DiscoverEmptyState(hasAnyApps: Boolean, onAddApp: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AppMonogram("Discover")
        Spacer(Modifier.height(14.dp))
        Text(if (hasAnyApps) "No matching apps" else "No apps to discover yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(if (hasAnyApps) "Try the app name, package ID, or member username." else "New test groups will appear here. You can start by adding your own app in My apps.", color = Ink500)
        if (!hasAnyApps) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAddApp, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Add my app") }
        }
    }
}

@Composable
private fun DiscoverRow(app: PublicApp, bookmark: AppBookmark?, showBookmarksOnly: Boolean, onOpen: (String) -> Unit, onBookmark: (String) -> Unit, onRemoveBookmark: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onOpen(app.id) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        AppMonogram(app.title); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(app.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Text("@${app.ownerUsername} · ${app.postedLabel}", style = MaterialTheme.typography.labelMedium, color = Ink500); if (bookmark != null) Text("Bookmarked ${bookmarkAgeLabel(bookmark.bookmarkedAt)}", style = MaterialTheme.typography.labelSmall, color = Brand600); Spacer(Modifier.height(5.dp)); Text(app.instructions, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = Ink500); Spacer(Modifier.height(7.dp)); Text(if (app.slotsRemaining == 0) "Full" else "${app.slotsRemaining} slots left · 14-day target", style = MaterialTheme.typography.labelMedium, color = if (app.slotsRemaining == 0) Ink500 else Brand600) }
        if (bookmark == null) IconButton(onClick = { onBookmark(app.id) }) { Icon(Icons.Default.BookmarkBorder, "Bookmark ${app.title}") }
        else if (showBookmarksOnly) IconButton(onClick = { onRemoveBookmark(app.id) }) { Icon(Icons.Default.Close, "Remove ${app.title} bookmark") }
        Icon(Icons.Default.ArrowForward, "Open ${app.title}", tint = Brand600)
    }
}

private fun bookmarkAgeLabel(bookmarkedAt: Instant, now: Instant = Instant.now()): String {
    val minutes = Duration.between(bookmarkedAt, now).toMinutes().coerceAtLeast(0)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 1440 -> "${minutes / 60}h ago"
        else -> "${minutes / 1440}d ago"
    }
}
