package com.aura.smartcart.navigation

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aura.smartcart.AppContainer
import com.aura.smartcart.ui.account.SignInRequiredDialog
import com.aura.smartcart.ui.bill.BillEditScreen
import com.aura.smartcart.ui.editor.ListEditorScreen
import com.aura.smartcart.ui.home.HomeScreen
import com.aura.smartcart.ui.onboarding.OnboardingScreen
import com.aura.smartcart.ui.shop.ShopScreen
import com.aura.smartcart.ui.splash.SplashScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val EDITOR = "editor?listId={listId}&copyFrom={copyFrom}"
    const val SHOP = "shop/{listId}"
    const val BILL = "bill?billId={billId}&listId={listId}&image={image}"

    fun editor(listId: Long? = null, copyFrom: Long? = null) =
        "editor?listId=${listId ?: -1}&copyFrom=${copyFrom ?: -1}"

    fun shop(listId: Long) = "shop/$listId"

    fun bill(billId: Long? = null, listId: Long? = null, imagePath: String? = null) =
        "bill?billId=${billId ?: -1}&listId=${listId ?: -1}&image=${Uri.encode(imagePath.orEmpty())}"
}

private fun Long.orNull(): Long? = takeIf { it > 0 }

@Composable
fun AppNavigation(container: AppContainer, openListId: Long?, onOpenListHandled: () -> Unit) {
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    // Saved so it survives the activity being recreated (e.g. after changing the app language).
    var ready by rememberSaveable { mutableStateOf(false) }

    // Adding a bill needs a Google account (bills are backed up to it). Signed out, the route
    // waits here while the sign-in dialog is shown, and opens once sign-in succeeds.
    var pendingBillRoute by rememberSaveable { mutableStateOf<String?>(null) }
    val openNewBill: (String) -> Unit = { route ->
        if (container.auth.isSignedIn()) nav.navigate(route) else pendingBillRoute = route
    }
    pendingBillRoute?.let { route ->
        SignInRequiredDialog(
            onSignedIn = { pendingBillRoute = null; nav.navigate(route) },
            onDismiss = { pendingBillRoute = null }
        )
    }

    // A tapped shopping reminder opens that list once the app is past splash/onboarding.
    LaunchedEffect(openListId, ready) {
        if (ready && openListId != null) {
            nav.navigate(Routes.shop(openListId)) { launchSingleTop = true }
            onOpenListHandled()
        }
    }

    NavHost(
        navController = nav,
        startDestination = Routes.SPLASH,
        enterTransition = { fadeIn(tween(350)) },
        exitTransition = { fadeOut(tween(250)) },
        popEnterTransition = { fadeIn(tween(350)) },
        popExitTransition = { fadeOut(tween(250)) }
    ) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onFinished = {
                    scope.launch {
                        val next = if (container.prefs.onboardingDone.first()) Routes.HOME else Routes.ONBOARDING
                        nav.navigate(next) { popUpTo(Routes.SPLASH) { inclusive = true } }
                        if (next == Routes.HOME) ready = true
                    }
                }
            )
        }
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onFinished = {
                    scope.launch {
                        container.prefs.setOnboardingDone()
                        nav.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                        ready = true
                    }
                }
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                onNewList = { nav.navigate(Routes.editor()) },
                onOpenList = { nav.navigate(Routes.shop(it)) },
                onEditList = { nav.navigate(Routes.editor(listId = it)) },
                onReuseList = { nav.navigate(Routes.editor(copyFrom = it)) },
                onOpenBill = { nav.navigate(Routes.bill(billId = it)) },
                onNewBill = { path -> openNewBill(Routes.bill(imagePath = path)) }
            )
        }
        composable(
            Routes.EDITOR,
            arguments = listOf(
                navArgument("listId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("copyFrom") { type = NavType.LongType; defaultValue = -1L }
            )
        ) { entry ->
            val listId = entry.arguments?.getLong("listId")?.orNull()
            val copyFrom = entry.arguments?.getLong("copyFrom")?.orNull()
            ListEditorScreen(
                listId = listId,
                copyFrom = copyFrom,
                onBack = { nav.popBackStack() },
                onSaved = { savedId -> nav.openShopAfterEdit(savedId) }
            )
        }
        composable(
            Routes.SHOP,
            arguments = listOf(navArgument("listId") { type = NavType.LongType })
        ) { entry ->
            val listId = entry.arguments?.getLong("listId") ?: return@composable
            ShopScreen(
                listId = listId,
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate(Routes.editor(listId = listId)) },
                onAddBill = { openNewBill(Routes.bill(listId = listId)) }
            )
        }
        composable(
            Routes.BILL,
            arguments = listOf(
                navArgument("billId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("listId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("image") { type = NavType.StringType; defaultValue = "" }
            )
        ) { entry ->
            val billId = entry.arguments?.getLong("billId")?.orNull()
            val listId = entry.arguments?.getLong("listId")?.orNull()
            val image = entry.arguments?.getString("image")?.takeIf { it.isNotBlank() }
            BillEditScreen(
                billId = billId,
                listId = listId,
                imagePath = image,
                onBack = { nav.popBackStack() },
                onSaved = {
                    // After saving a bill for a list, go back home (the list is finished).
                    if (listId != null) nav.popBackStack(Routes.HOME, inclusive = false) else nav.popBackStack()
                }
            )
        }
    }
}

/** After saving: go to the list's shopping screen, replacing the editor (and an older copy of the same screen). */
private fun NavHostController.openShopAfterEdit(listId: Long) {
    val cameFromShop = previousBackStackEntry?.destination?.route == Routes.SHOP
    if (cameFromShop) {
        popBackStack()
    } else {
        navigate(Routes.shop(listId)) {
            popUpTo(Routes.HOME)
        }
    }
}
