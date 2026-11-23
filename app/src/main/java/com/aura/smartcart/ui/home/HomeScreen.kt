package com.aura.smartcart.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import com.aura.smartcart.ui.account.accountViewModel
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aura.smartcart.R
import com.aura.smartcart.service.scan.ImageLoader
import com.aura.smartcart.ui.common.rememberDocumentScanner
import com.aura.smartcart.ui.components.CartLogo
import com.aura.smartcart.ui.components.SystemBarIcons
import com.aura.smartcart.ui.theme.Brand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class HomeTab(val title: Int, val icon: ImageVector) {
    LISTS(R.string.tab_lists, Icons.Rounded.ShoppingCart),
    BILLS(R.string.tab_bills, Icons.AutoMirrored.Rounded.ReceiptLong),
    BUDGET(R.string.tab_budget, Icons.Rounded.Savings),
    SETTINGS(R.string.tab_settings, Icons.Rounded.Settings)
}

/** Main screen with four tabs: bottom bar on phones, side rail on tablets/landscape. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNewList: () -> Unit,
    onOpenList: (Long) -> Unit,
    onEditList: (Long) -> Unit,
    onReuseList: (Long) -> Unit,
    onOpenBill: (Long) -> Unit,
    onNewBill: (imagePath: String?) -> Unit,
    onOpenPro: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    SystemBarIcons(darkStatusIcons = !Brand.colors.isDark)

    var tab by rememberSaveable { mutableStateOf(HomeTab.LISTS) }
    val wide = LocalConfiguration.current.screenWidthDp >= 600

    val content: @Composable (PaddingValues) -> Unit = { padding ->
        when (tab) {
            HomeTab.LISTS -> ListsTab(padding, onOpenList, onEditList, onReuseList, onNewList)
            HomeTab.BILLS -> BillsTab(padding, onOpenBill)
            HomeTab.BUDGET -> BudgetTab(padding)
            HomeTab.SETTINGS -> SettingsTab(padding)
        }
    }

    val accountVm = accountViewModel()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = { AppDrawer(accountVm, onClose = { scope.launch { drawerState.close() } }, onOpenPro = onOpenPro) }
    ) {
    Row(Modifier.fillMaxSize()) {
        if (wide) {
            NavigationRail(containerColor = colors.surfaceVariant) {
                Spacer(Modifier.padding(top = 16.dp))
                CartLogo(Modifier.padding(8.dp).width(40.dp), cartColor = colors.primary, checkColor = colors.secondary)
                Spacer(Modifier.padding(top = 16.dp))
                HomeTab.entries.forEach { t ->
                    NavigationRailItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(stringResource(t.title)) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = colors.onPrimary,
                            indicatorColor = colors.primary
                        )
                    )
                }
            }
        }
        Scaffold(
            modifier = Modifier.weight(1f),
            containerColor = colors.background,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(tab.title), style = MaterialTheme.typography.headlineMedium) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Rounded.Menu, contentDescription = stringResource(R.string.drawer_open))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)
                )
            },
            bottomBar = {
                if (!wide) {
                    NavigationBar(containerColor = colors.surfaceVariant) {
                        HomeTab.entries.forEach { t ->
                            NavigationBarItem(
                                selected = tab == t,
                                onClick = { tab = t },
                                icon = { Icon(t.icon, contentDescription = null) },
                                label = { Text(stringResource(t.title)) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = colors.onPrimary,
                                    indicatorColor = colors.primary
                                )
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                when (tab) {
                    HomeTab.LISTS -> ExtendedFloatingActionButton(
                        onClick = onNewList,
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary,
                        icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                        text = { Text(stringResource(R.string.home_new_list)) }
                    )
                    HomeTab.BILLS -> ExtendedFloatingActionButton(
                        onClick = { onNewBill(null) },
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary,
                        icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                        text = { Text(stringResource(R.string.shop_add_bill)) }
                    )
                    else -> Unit
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize()) { content(padding) }
        }
    }
    }

}
