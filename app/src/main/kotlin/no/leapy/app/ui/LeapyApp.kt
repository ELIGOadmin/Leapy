package no.leapy.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import no.leapy.app.CarViewModel
import no.leapy.core.Command

/** Asks the user to confirm a sensitive action, then runs it. */
class Confirmer(
    private val biometric: (String, () -> Unit) -> Boolean,
    private val fallback: (String, () -> Unit) -> Unit,
) {
    fun confirm(title: String, action: () -> Unit) {
        if (!biometric(title, action)) fallback(title, action)
    }
}

enum class Page { HOME, CLIMATE, LOCATION, SECURITY, CAR, SETTINGS }

@Composable
fun LeapyApp(vm: CarViewModel, biometric: (String, () -> Unit) -> Boolean) {
    val state by vm.state.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().background(C.Bg)) {
        if (!state.loggedIn) {
            LoginScreen(state.loggingIn, state.error, onLogin = vm::login)
            return@Box
        }

        var page by rememberSaveable { mutableStateOf(Page.HOME) }
        var pending by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
        val confirmer = remember(biometric) { Confirmer(biometric) { title, action -> pending = title to action } }
        val snackbar = remember { SnackbarHostState() }
        val charging by rememberUpdatedState(state.status?.isCharging == true)

        BackHandler(enabled = page != Page.HOME) { page = Page.HOME }

        // Fresh status while the app is open; more often while charging so the numbers move.
        LaunchedEffect(Unit) {
            while (true) { delay(if (charging) 20_000 else 60_000); vm.refresh(silent = true) }
        }
        LaunchedEffect(state.error) {
            state.error?.let { snackbar.showSnackbar(it); vm.clearMessages() }
        }
        LaunchedEffect(state.message) {
            state.message?.let { snackbar.showSnackbar(it); vm.clearMessages() }
        }

        pending?.let { (title, action) ->
            AlertDialog(
                onDismissRequest = { pending = null },
                title = { Text(title) },
                confirmButton = { TextButton(onClick = { pending = null; action() }) { Text("Ja") } },
                dismissButton = { TextButton(onClick = { pending = null }) { Text("Avbryt") } },
                containerColor = C.CardHigh,
            )
        }

        val go: (Page) -> Unit = { page = it }
        val back = { page = Page.HOME }

        when {
            state.vehicle == null && state.invitations.isNotEmpty() -> InvitationScreen(state.invitations, vm::acceptInvitation)
            state.vehicle == null && !state.refreshing && state.status == null -> NoCarScreen(onRetry = { vm.refresh() }, onLogout = vm::logout)
            else -> AnimatedContent(
                targetState = page,
                transitionSpec = {
                    val forward = targetState != Page.HOME
                    (slideInHorizontally(tween(260)) { if (forward) it / 4 else -it / 4 } + fadeIn(tween(260)))
                        .togetherWith(slideOutHorizontally(tween(260)) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(180)))
                },
                label = "page",
            ) { p ->
                when (p) {
                    Page.HOME -> HomeScreen(state, vm, confirmer, go)
                    Page.CLIMATE -> ClimateScreen(state, vm, back)
                    Page.LOCATION -> LocationScreen(state, back)
                    Page.SECURITY -> SecurityScreen(state, vm, confirmer, back)
                    Page.CAR -> CarScreen(state, vm, back)
                    Page.SETTINGS -> SettingsScreen(state, vm, back)
                }
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp)) {
            Snackbar(it, containerColor = C.CardHigh, contentColor = C.On)
        }
    }
}

/** Commands that open the car get an extra confirmation. */
fun sensitive(cmd: Command) = cmd in setOf(Command.UNLOCK, Command.TRUNK_OPEN, Command.WINDOWS_OPEN, Command.UNLOCK_CHARGER)
