package com.aura.smartcart.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Feedback
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aura.smartcart.BuildConfig
import com.aura.smartcart.R
import com.aura.smartcart.data.auth.AccountUser
import com.aura.smartcart.ui.account.AccountViewModel
import com.aura.smartcart.ui.account.GoogleSignInButton
import com.aura.smartcart.ui.components.CartLogo
import com.aura.smartcart.util.Feedback
import com.aura.smartcart.util.Links

/**
 * Side menu: account (Google sign in / log out), About, Privacy Policy, Terms, feedback and the app version.
 * [onClose] closes the drawer after an item is tapped.
 */
@Composable
fun AppDrawer(vm: AccountViewModel, onClose: () -> Unit, onOpenPro: () -> Unit) {
    val user by vm.user.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    // Default sheet is up to 360dp wide, which covers most of a phone screen.
    ModalDrawerSheet(modifier = Modifier.width(280.dp), drawerContainerColor = colors.surface) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 16.dp)
        ) {
            DrawerHeader(user)
            Spacer(Modifier.height(12.dp))
            if (user == null) {
                GoogleSignInButton(vm, Modifier.fillMaxWidth().padding(horizontal = 4.dp))
                Spacer(Modifier.height(12.dp))
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = colors.outline)

            DrawerItem(Icons.Rounded.Info, stringResource(R.string.drawer_about)) { showAbout = true }
            DrawerItem(Icons.Rounded.Policy, stringResource(R.string.settings_privacy_policy)) {
                onClose(); uriHandler.openUri(Links.PRIVACY)
            }
            DrawerItem(Icons.Rounded.Description, stringResource(R.string.settings_terms)) {
                onClose(); uriHandler.openUri(Links.TERMS)
            }
            DrawerItem(Icons.Rounded.Feedback, stringResource(R.string.drawer_feedback)) {
                onClose(); Feedback.open(context)
            }
            DrawerItem(Icons.Rounded.WorkspacePremium, stringResource(R.string.pro_title)) {
                onClose(); onOpenPro()
            }

            if (user != null) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = colors.outline)
                DrawerItem(Icons.AutoMirrored.Rounded.Logout, stringResource(R.string.auth_sign_out)) { confirmLogout = true }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
        }
    }

    if (showAbout) AboutDialog(onDismiss = { showAbout = false })
    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text(stringResource(R.string.auth_sign_out_confirm_title)) },
            text = { Text(stringResource(R.string.auth_sign_out_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { confirmLogout = false; vm.signOut(); onClose() }) {
                    Text(stringResource(R.string.auth_sign_out))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

@Composable
private fun DrawerHeader(user: AccountUser?) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(52.dp).background(if (user == null) colors.primaryContainer else colors.primary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (user == null) {
                CartLogo(Modifier.size(34.dp), cartColor = colors.primary, checkColor = colors.secondary)
            } else {
                Text(user.initial, style = MaterialTheme.typography.titleLarge, color = colors.onPrimary)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                user?.name?.ifBlank { null } ?: stringResource(if (user == null) R.string.drawer_guest else R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                user?.email ?: stringResource(R.string.drawer_guest_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DrawerItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    NavigationDrawerItem(
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(label) },
        selected = false,
        onClick = onClick,
        colors = NavigationDrawerItemDefaults.colors(unselectedIconColor = MaterialTheme.colorScheme.primary)
    )
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { CartLogo(Modifier.size(56.dp), cartColor = colors.primary, checkColor = colors.secondary) },
        title = { Text(stringResource(R.string.app_name)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.splash_tagline),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.primary,
                    textAlign = TextAlign.Center
                )
                Text(stringResource(R.string.about_body), textAlign = TextAlign.Center)
                Text(
                    stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.about_close)) } }
    )
}
