package com.aura.smartcart.ui.admin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aura.smartcart.AppContainer
import com.aura.smartcart.R
import com.aura.smartcart.data.remote.AdminRepository
import com.aura.smartcart.data.remote.AdminSession
import com.aura.smartcart.data.remote.AdminStats
import com.aura.smartcart.data.remote.AdminUser
import com.aura.smartcart.ui.common.appViewModel
import com.aura.smartcart.util.Dates
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** All sessions of one phone on the chosen day, newest first. */
data class SessionGroup(val deviceId: String, val sessions: List<AdminSession>) {
    val latest get() = sessions.first()
    val totalSec get() = sessions.sumOf { it.durationSec }
}

data class AdminState(
    // Users tab
    val loading: Boolean = true,
    val stats: AdminStats? = null,
    val users: List<AdminUser> = emptyList(),
    val query: String = "",
    val error: String? = null,
    // Activity tab
    val dayOffset: Int = 0,
    val hideMine: Boolean = true,
    val sessionsLoading: Boolean = true,
    val sessions: List<AdminSession> = emptyList(),
    val sessionsError: String? = null
) {
    val shown: List<AdminUser>
        get() {
            val q = query.trim().lowercase()
            if (q.isEmpty()) return users
            return users.filter {
                it.email.lowercase().contains(q) || it.name.lowercase().contains(q) || it.userId.lowercase().contains(q)
            }
        }

    val visibleSessions: List<AdminSession>
        get() = if (hideMine) sessions.filterNot { AdminRepository.isAdminEmail(it.email) } else sessions

    val groups: List<SessionGroup>
        get() = visibleSessions.groupBy { it.deviceId }
            .map { (device, list) -> SessionGroup(device, list.sortedByDescending { it.start }) }
            .sortedByDescending { it.latest.start }

    val usersByUid: Map<String, AdminUser> get() = users.associateBy { it.uid }
}

class AdminViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(AdminState())
    val state: StateFlow<AdminState> = _state.asStateFlow()
    val allowed: Boolean get() = container.admin.isAdmin()
    private var sessionsJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        if (!allowed) {
            _state.update { it.copy(loading = false, sessionsLoading = false) }
            return
        }
        loadSessions()
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val stats = container.admin.stats()
                val users = container.admin.users()
                _state.update { it.copy(loading = false, stats = stats, users = users) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.readable()) }
            }
        }
    }

    fun changeDay(by: Int) {
        val next = (_state.value.dayOffset + by).coerceAtMost(0)
        if (next == _state.value.dayOffset) return
        _state.update { it.copy(dayOffset = next) }
        loadSessions()
    }

    fun setHideMine(hide: Boolean) = _state.update { it.copy(hideMine = hide) }

    private fun loadSessions() {
        val offset = _state.value.dayOffset
        sessionsJob?.cancel()
        _state.update { it.copy(sessionsLoading = true, sessionsError = null) }
        sessionsJob = viewModelScope.launch {
            try {
                val list = container.admin.sessions(dayStart(offset).time)
                _state.update { it.copy(sessionsLoading = false, sessions = list) }
            } catch (e: Exception) {
                _state.update { it.copy(sessionsLoading = false, sessions = emptyList(), sessionsError = e.readable()) }
            }
        }
    }

    fun setQuery(q: String) = _state.update { it.copy(query = q) }

    /** Turns Pro on/off for a user; the list updates at once and rolls back if Firestore refuses. */
    fun setPro(user: AdminUser, isPro: Boolean, onError: (String) -> Unit) {
        fun apply(value: Boolean) = _state.update { s ->
            s.copy(users = s.users.map { if (it.uid == user.uid) it.copy(isPro = value) else it })
        }
        apply(isPro)
        viewModelScope.launch {
            try {
                container.admin.setPro(user.uid, isPro)
            } catch (e: Exception) {
                apply(!isPro)
                onError(e.message ?: "Update failed")
            }
        }
    }

    private fun Exception.readable(): String =
        if (this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            "Permission denied. Publish the latest firestore.rules in Firebase Console → Firestore → Rules."
        } else {
            message ?: "Could not load data"
        }
}

private fun dayStart(offsetDays: Int): Calendar = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    add(Calendar.DAY_OF_YEAR, offsetDays)
}

private fun formatDuration(sec: Long): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m ${s}s"
        else -> "${s}s"
    }
}

/** Friendly names for navigation routes in the session log. */
private val screenTitles = mapOf(
    "onboarding" to "Welcome",
    "home" to "Home",
    "editor" to "List editor",
    "shop" to "Shopping",
    "bill" to "Bill",
    "plan" to "Store plan",
    "pro" to "Pro",
    "admin" to "Admin panel"
)

/** Admin panel (only for the admin email): daily activity like What2Eat's dashboard, and each user's Pro switch. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(onBack: () -> Unit) {
    val vm = appViewModel { AdminViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val onProChange: (AdminUser, Boolean) -> Unit = { user, on ->
        vm.setPro(user, on) { msg -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    },
                    title = { Text("Admin dashboard") },
                    actions = {
                        IconButton(onClick = vm::refresh) { Icon(Icons.Rounded.Refresh, contentDescription = "Refresh") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)
                )
                if (vm.allowed) {
                    TabRow(selectedTabIndex = tab, containerColor = colors.background) {
                        Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Activity") })
                        Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Users") })
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            if (!vm.allowed) {
                Text("Not allowed.", color = colors.error, modifier = Modifier.padding(24.dp))
                return@Box
            }
            if (tab == 0) ActivityTab(state, vm, onProChange) else UsersTab(state, vm, onProChange)
        }
    }
}

@Composable
private fun ActivityTab(state: AdminState, vm: AdminViewModel, onProChange: (AdminUser, Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val groups = state.groups
    val visible = state.visibleSessions
    val totalSec = visible.sumOf { it.durationSec }
    val guestUsers = groups.count { it.latest.isGuest }
    val countries = remember(groups) {
        groups.groupingBy { it.latest.country }.eachCount().entries.sortedByDescending { it.value }
    }
    val dateLabel = remember(state.dayOffset) {
        val base = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(dayStart(state.dayOffset).time)
        when (state.dayOffset) {
            0 -> "Today • $base"
            -1 -> "Yesterday • $base"
            else -> base
        }
    }
    val usersByUid = state.usersByUid

    LazyColumn(
        modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(shape = RoundedCornerShape(14.dp), color = colors.surface, tonalElevation = 1.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.changeDay(-1) }) {
                        Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous day")
                    }
                    Text(
                        dateLabel,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center
                    )
                    IconButton(onClick = { vm.changeDay(1) }, enabled = state.dayOffset < 0) {
                        Icon(Icons.Rounded.ChevronRight, contentDescription = "Next day")
                    }
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().clickable { vm.setHideMine(!state.hideMine) }.padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Hide my own sessions", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                Switch(checked = state.hideMine, onCheckedChange = vm::setHideMine)
            }
        }

        when {
            state.sessionsLoading -> item {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            state.sessionsError != null -> item {
                Surface(shape = RoundedCornerShape(14.dp), color = colors.errorContainer) {
                    Text(state.sessionsError, Modifier.padding(14.dp), color = colors.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                }
            }
            else -> {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile("Users", groups.size.toString(), "$guestUsers guest • ${groups.size - guestUsers} Google", Modifier.weight(1f))
                        StatTile("Sessions", visible.size.toString(), "app opens", Modifier.weight(1f))
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile("Total time", formatDuration(totalSec), "all users", Modifier.weight(1f))
                        StatTile(
                            "Avg / user",
                            if (groups.isEmpty()) "–" else formatDuration(totalSec / groups.size),
                            "this day",
                            Modifier.weight(1f)
                        )
                    }
                }
                if (countries.isNotEmpty()) {
                    item {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            countries.forEach { (c, n) ->
                                Surface(shape = RoundedCornerShape(20.dp), color = colors.primaryContainer) {
                                    Text(
                                        "🌍 $c · $n",
                                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colors.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
                if (groups.isEmpty()) {
                    item {
                        Text(
                            "No one opened the app on this day.",
                            Modifier.fillMaxWidth().padding(32.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                items(groups, key = { it.deviceId }) { group ->
                    SessionCard(group, usersByUid[group.latest.authUid], context, onProChange)
                }
            }
        }
    }
}

@Composable
private fun UsersTab(state: AdminState, vm: AdminViewModel, onProChange: (AdminUser, Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    LazyColumn(
        modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        state.stats?.let { s ->
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    StatTile("Users", s.users.toString(), "signed in", Modifier.weight(1f))
                    StatTile("Active 7d", s.activeThisWeek.toString(), "last week", Modifier.weight(1f))
                    StatTile("Pro", s.proUsers.toString(), "by admin", Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    StatTile("Play subs", s.playProUsers.toString(), "paying", Modifier.weight(1f))
                    StatTile("Prices", s.sharedPrices.toString(), "shared", Modifier.weight(1f))
                    Spacer(Modifier.weight(1f))
                }
            }
        }
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                placeholder = { Text("Search email, name or SC- id") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (state.loading) {
            item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        }
        state.error?.let { e -> item { Text(e, color = colors.error, style = MaterialTheme.typography.bodySmall) } }
        items(state.shown, key = { it.uid }) { user ->
            UserRow(user) { on -> onProChange(user, on) }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, sub: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier = modifier, shape = RoundedCornerShape(14.dp), color = colors.surface, tonalElevation = 1.dp) {
        Column(Modifier.padding(14.dp)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = colors.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = colors.onSurface)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SessionCard(group: SessionGroup, profile: AdminUser?, context: Context, onProChange: (AdminUser, Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val u = group.latest
    val timeFmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    Surface(shape = RoundedCornerShape(16.dp), color = colors.surface, tonalElevation = 1.dp) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (u.isGuest) "👤" else "🟢", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (u.isGuest) "Guest" else (u.name ?: "Google user"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!u.isGuest && !u.email.isNullOrBlank()) {
                        Text(u.email, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Surface(shape = RoundedCornerShape(8.dp), color = colors.secondaryContainer) {
                    Text(
                        formatDuration(group.totalSec),
                        Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSecondaryContainer
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            profile?.userId?.ifBlank { null }?.let { InfoLine("👤", "User ID: $it") }
            val shownId = group.deviceId
            InfoLine("🆔", "Phone ID: $shownId") {
                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                cb?.setPrimaryClip(ClipData.newPlainText("phone_id", shownId))
                Toast.makeText(context, "Phone ID copied", Toast.LENGTH_SHORT).show()
            }
            InfoLine("🌍", u.country)
            InfoLine("📱", u.deviceName)
            InfoLine("🤖", u.androidVersion + if (u.appVersion.isNotBlank()) "  •  App v${u.appVersion}" else "")

            // Signed-in users with a profile: the Pro switch right here.
            if (profile != null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("⭐", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        (if (profile.isPro) "Pro" else "Free") + (if (profile.playPro) " • Play sub" else ""),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(checked = profile.isPro, onCheckedChange = { onProChange(profile, it) })
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = colors.outlineVariant)
            Text(
                "${group.sessions.size} session${if (group.sessions.size == 1) "" else "s"}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onSurfaceVariant
            )
            group.sessions.forEach { s ->
                Spacer(Modifier.height(6.dp))
                Column(
                    Modifier.fillMaxWidth()
                        .background(colors.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        "🕒 ${s.start?.let(timeFmt::format) ?: "?"} – ${s.end?.let(timeFmt::format) ?: "?"}   (${formatDuration(s.durationSec)})",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (s.screens.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            s.screens.joinToString("  →  ") { screenTitles[it] ?: it },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoLine(icon: String, text: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun UserRow(user: AdminUser, onProChange: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(16.dp), color = colors.surface, tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(user.name.ifBlank { "(no name)" }, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                Text(user.email, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                val extra = listOfNotNull(
                    user.userId.ifBlank { null },
                    if (user.playPro) "Play sub" else null,
                    user.lastSeen?.let { "seen " + Dates.date(it) }
                ).joinToString(" · ")
                Text(extra, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Switch(checked = user.isPro, onCheckedChange = onProChange)
                Text(if (user.isPro) "Pro" else "Free", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }
    }
}
