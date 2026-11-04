package no.leapy.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import no.leapy.app.CarViewModel
import no.leapy.app.UiState
import no.leapy.core.ChargeState
import no.leapy.core.Command
import no.leapy.core.ShareInvitation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshableColumn(state: UiState, vm: CarViewModel, content: @Composable () -> Unit) {
    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { vm.refresh() }, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) { content() }
    }
}

@Composable
fun HomeScreen(state: UiState, vm: CarViewModel, confirmer: Confirmer) {
    val s = state.status
    fun cmd(c: Command) {
        if (sensitive(c)) confirmer.confirm(c.label) { vm.send(c) } else vm.send(c)
    }

    RefreshableColumn(state, vm) {
        Column(Modifier.fillMaxWidth()) {
            Text(state.vehicle?.displayName ?: "Bilen din", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Oppdatert " + ago(s?.collectedAtMillis ?: state.lastFetchMillis) + (state.vehicle?.plateNumber?.let { " · $it" } ?: ""),
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
            )
        }

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            BatteryRing(s?.soc, s?.rangeKm, s?.isCharging == true, s?.chargeLimit)
            Text(chargeLine(state), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Status summary
        SectionCard {
            InfoRow("Låser", when (s?.locked) { true -> "Låst"; false -> "Ulåst"; null -> "–" },
                if (s?.locked == false) Warn else MaterialTheme.colorScheme.onSurface)
            InfoRow("Dører", if (s == null) "–" else if (s.doorsOpen.isEmpty()) "Lukket" else s.doorsOpen.joinToString(), if (s?.doorsOpen?.isNotEmpty() == true) Warn else MaterialTheme.colorScheme.onSurface)
            InfoRow("Vinduer", if (s == null) "–" else if (s.windowsOpen.isEmpty()) "Lukket" else "Åpne: " + s.windowsOpen.joinToString(), if (s?.windowsOpen?.isNotEmpty() == true) Warn else MaterialTheme.colorScheme.onSurface)
            InfoRow("Klima", when (s?.acOn) { true -> "På" + (s.acSetting?.let { " · ${fmt(it)}°" } ?: ""); false -> "Av"; null -> "–" })
            s?.interiorTemp?.let { InfoRow("Temperatur inne", "${fmt(it)}°") }
        }

        // Main actions
        val locked = s?.locked != false
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionTile(if (locked) "Lås opp" else "Lås", if (locked) Icons.Filled.LockOpen else Icons.Filled.Lock, Modifier.weight(1f),
                busy = state.busy == Command.LOCK.label || state.busy == Command.UNLOCK.label) { cmd(if (locked) Command.UNLOCK else Command.LOCK) }
            ActionTile(if (s?.acOn == true) "Klima av" else "Klima ${state.climateTemp}°", if (s?.acOn == true) Icons.Filled.PowerSettingsNew else Icons.Filled.Thermostat,
                Modifier.weight(1f), active = s?.acOn == true, busy = state.busy?.startsWith("Klima") == true) {
                if (s?.acOn == true) vm.send(Command.AC_OFF)
                else vm.startClimate(defaultMode(s?.interiorTemp, state.climateTemp), state.climateTemp, 4)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionTile("Hurtigvarme", Icons.Filled.Whatshot, Modifier.weight(1f), busy = state.busy == Command.QUICK_HEAT.label) { vm.send(Command.QUICK_HEAT) }
            ActionTile("Avising", Icons.Filled.Air, Modifier.weight(1f), busy = state.busy == Command.DEFROST.label) { vm.send(Command.DEFROST) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionTile("Bagasjerom", Icons.Filled.Luggage, Modifier.weight(1f), busy = state.busy == Command.TRUNK_OPEN.label) { cmd(Command.TRUNK_OPEN) }
            ActionTile("Finn bilen", Icons.Filled.VolumeUp, Modifier.weight(1f), busy = state.busy == Command.FIND_CAR.label) { vm.send(Command.FIND_CAR) }
        }
        if (!state.hasPin) {
            Text("Legg inn PIN-koden under Mer for å kunne styre bilen.", color = Warn)
        }
    }
}

fun defaultMode(inside: Double?, target: Int) =
    if (inside != null && inside > target + 1) Command.Companion.ClimateMode.COOL else Command.Companion.ClimateMode.HEAT

fun fmt(d: Double) = if (d % 1.0 == 0.0) d.toInt().toString() else "%.1f".format(d)

fun chargeLine(state: UiState): String {
    val s = state.status ?: return ""
    return when (s.chargeState) {
        ChargeState.CHARGING -> buildString {
            append("Lader")
            s.chargingPowerKw?.takeIf { it > 0 }?.let { append(" med ${"%.1f".format(it)} kW") }
            s.chargeRemainMin?.takeIf { it > 0 }?.let { append(" · ferdig om ${it / 60} t ${it % 60} min") }
        }
        ChargeState.FINISHED -> "Ferdig ladet"
        ChargeState.PAUSED -> "Lading på pause"
        ChargeState.ERROR -> "Feil ved lading"
        else -> if (s.pluggedIn == true) "Ladekabel tilkoblet" else "Ikke tilkoblet lader"
    }
}

@Composable
fun InvitationScreen(invitations: List<ShareInvitation>, onAccept: (ShareInvitation) -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Bil delt med deg", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Godta delingen, så dukker bilen opp her.")
        invitations.forEach { inv ->
            SectionCard(inv.carType?.uppercase()?.let { "Leapmotor $it" } ?: "Leapmotor") {
                inv.ownerNickname?.let { Text("Fra $it") }
                Button(onClick = { onAccept(inv) }, modifier = Modifier.fillMaxWidth()) { Text("Godta deling") }
            }
        }
    }
}

@Composable
fun NoCarScreen(onRetry: () -> Unit, onLogout: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Fant ingen bil", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Kontoen har ingen bil ennå. Del bilen til denne kontoen fra Leapmotor-appen, med alle rettigheter, og trykk Prøv igjen.")
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Prøv igjen") }
        androidx.compose.material3.TextButton(onClick = onLogout) { Text("Logg ut") }
    }
}
