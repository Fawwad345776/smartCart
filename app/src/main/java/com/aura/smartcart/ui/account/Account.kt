package com.aura.smartcart.ui.account

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import com.aura.smartcart.data.auth.AccountUser
import com.aura.smartcart.data.auth.SignInResult
import com.aura.smartcart.ui.common.appViewModel
import com.aura.smartcart.ui.common.toast
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AccountViewModel(private val container: AppContainer) : ViewModel() {
    val user: StateFlow<AccountUser?> = container.auth.user
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var busy by mutableStateOf(false)
        private set

    fun isSignedIn(): Boolean = container.auth.isSignedIn()

    /** Shows Google's account picker; on success uploads bills saved while signed out. */
    fun signIn(activityContext: Context, onSuccess: () -> Unit = {}) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            val result = container.auth.signInWithGoogle(activityContext)
            busy = false
            when (result) {
                SignInResult.SUCCESS -> {
                    onSuccess()
                    container.bills.syncPending()
                }
                SignInResult.CANCELLED -> Unit
                SignInResult.NOT_CONFIGURED -> activityContext.toast(R.string.auth_not_configured)
                SignInResult.FAILED -> activityContext.toast(R.string.auth_failed)
            }
        }
    }

    fun signOut() = viewModelScope.launch { container.auth.signOut() }
}

@Composable
fun accountViewModel(): AccountViewModel = appViewModel { AccountViewModel(it) }

/** "Sign in with Google" button that shows a spinner while the account picker is busy. */
@Composable
fun GoogleSignInButton(vm: AccountViewModel, modifier: Modifier = Modifier, onSuccess: () -> Unit = {}) {
    val context = LocalContext.current
    Button(onClick = { vm.signIn(context, onSuccess) }, enabled = !vm.busy, modifier = modifier) {
        if (vm.busy) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        } else {
            Icon(Icons.AutoMirrored.Rounded.Login, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.auth_sign_in_google))
    }
}

/** Asked before adding a bill while signed out. [onSignedIn] continues to the bill screen. */
@Composable
fun SignInRequiredDialog(onSignedIn: () -> Unit, onDismiss: () -> Unit) {
    val vm = accountViewModel()
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.CloudDone, contentDescription = null) },
        title = { Text(stringResource(R.string.auth_required_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.auth_required_body))
                Row(Modifier, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    GoogleSignInButton(vm, onSuccess = onSignedIn)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
