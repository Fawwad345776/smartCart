package com.aura.smartcart.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aura.smartcart.AppContainer
import com.aura.smartcart.R
import com.aura.smartcart.data.remote.AdminStats
import com.aura.smartcart.data.remote.AdminUser
import com.aura.smartcart.ui.common.appViewModel
import com.aura.smartcart.util.Dates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.widget.Toast

data class AdminState(
    val loading: Boolean = true,
    val stats: AdminStats? = null,
    val users: List<AdminUser> = emptyList(),
    val query: String = "",
    val error: String? = null
) {
    val shown: List<AdminUser>
        get() {
            val q = query.trim().lowercase()
            if (q.isEmpty()) return users
            return users.filter {
                it.email.lowercase().contains(q) || it.name.lowercase().contains(q) || it.userId.lowercase().contains(q)
            }
        }
}

class AdminViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(AdminState())
    val state: StateFlow<AdminState> = _state.asStateFlow()
    val allowed: Boolean get() = container.admin.isAdmin()

    init {
        refresh()
    }

    fun refresh() {
        if (!allowed) {
            _state.update { it.copy(loading = false) }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val stats = container.admin.stats()
                val users = container.admin.users()
                _state.update { it.copy(loading = false, stats = stats, users = users) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Could not load data") }
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
}

/** Admin panel (only for the admin email): app numbers and each user's Pro switch. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(onBack: () -> Unit) {
    val vm = appViewModel { AdminViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                title = { Text("Admin panel") },
                actions = {
                    IconButton(onClick = vm::refresh) { Icon(Icons.Rounded.Refresh, contentDescription = "Refresh") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            if (!vm.allowed) {
                Text("Not allowed.", color = colors.error, modifier = Modifier.padding(24.dp))
                return@Box
            }
            LazyColumn(
                modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                state.stats?.let { s ->
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            StatTile("Users", s.users, Modifier.weight(1f))
                            StatTile("Active 7d", s.activeThisWeek, Modifier.weight(1f))
                            StatTile("Pro (admin)", s.proUsers, Modifier.weight(1f))
                        }
                        Spacer(Modifier.padding(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            StatTile("Play subs", s.playProUsers, Modifier.weight(1f))
                            StatTile("Shared prices", s.sharedPrices, Modifier.weight(1f))
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
                    UserRow(user) { on ->
                        vm.setPro(user, on) { msg -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: Long, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(16.dp), color = colors.primaryContainer, modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(value.toString(), style = MaterialTheme.typography.titleLarge, color = colors.onPrimaryContainer)
            Text(label, style = MaterialTheme.typography.labelSmall, color = colors.onPrimaryContainer)
        }
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
