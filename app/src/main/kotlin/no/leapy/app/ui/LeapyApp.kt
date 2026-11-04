package no.leapy.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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

private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Bil", Icons.Filled.DirectionsCar),
    CLIMATE("Klima", Icons.Filled.AcUnit),
    CHARGE("Lading", Icons.Filled.BatteryChargingFull),
    MORE("Mer", Icons.Filled.MoreHoriz),
}

@Composable
fun LeapyApp(vm: CarViewModel, biometric: (String, () -> Unit) -> Boolean) {
    val state by vm.state.collectAsStateWithLifecycle()

    if (!state.loggedIn) {
        LoginScreen(state.loggingIn, state.error, onLogin = vm::login)
        return
    }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var pending by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    val confirmer = remember(biometric) { Confirmer(biometric) { title, action -> pending = title to action } }
    val snackbar = remember { SnackbarHostState() }

    // Keep status fresh while the app is open.
    LaunchedEffect(Unit) {
        while (true) { delay(60_000); vm.refresh(silent = true) }
    }
    LaunchedEffect(state.message, state.error) {
        val text = state.error ?: state.message
        if (text != null) { snackbar.showSnackbar(text); vm.clearMessages() }
    }

    pending?.let { (title, action) ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(title) },
            text = { Text("Er du sikker?") },
            confirmButton = { TextButton(onClick = { pending = null; action() }) { Text("Ja") } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Avbryt") } },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { tab = i },
                        icon = { Icon(t.icon, contentDescription = null) }, label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when {
                state.vehicle == null && state.invitations.isNotEmpty() ->
                    InvitationScreen(state.invitations, vm::acceptInvitation)
                state.vehicle == null && !state.refreshing && state.status == null ->
                    NoCarScreen(onRetry = { vm.refresh() }, onLogout = vm::logout)
                else -> when (Tab.entries[tab]) {
                    Tab.HOME -> HomeScreen(state, vm, confirmer)
                    Tab.CLIMATE -> ClimateScreen(state, vm)
                    Tab.CHARGE -> ChargeScreen(state, vm)
                    Tab.MORE -> MoreScreen(state, vm, confirmer)
                }
            }
        }
    }
}

/** Commands that open the car get an extra confirmation. */
fun sensitive(cmd: Command) = cmd in setOf(Command.UNLOCK, Command.TRUNK_OPEN, Command.WINDOWS_OPEN, Command.UNLOCK_CHARGER)
