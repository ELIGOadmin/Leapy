package no.leapy.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.EvStation
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import no.leapy.app.CarViewModel
import no.leapy.app.R
import no.leapy.app.UiState
import no.leapy.core.CarStatus
import no.leapy.core.ChargeState
import no.leapy.core.Command
import no.leapy.core.ShareInvitation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(state: UiState, vm: CarViewModel, confirmer: Confirmer, go: (Page) -> Unit) {
    val s = state.status
    fun cmd(c: Command) { if (sensitive(c)) confirmer.confirm(c.label) { vm.send(c) } else vm.send(c) }

    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { vm.refresh() }, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            // Name and state
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(state.vehicle?.displayName ?: "Leapmotor", fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = C.On)
                    Text(headline(s), fontSize = 14.sp, color = C.Sub, modifier = Modifier.padding(top = 2.dp))
                }
                Box(Modifier.size(44.dp).clip(CircleShape).clickable { go(Page.SETTINGS) }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Settings, "Innstillinger", tint = C.Sub)
                }
            }

            // Battery
            Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                BatteryGlyph(s?.soc ?: 0, s?.isCharging == true)
                Spacer(Modifier.width(10.dp))
                Text(
                    listOfNotNull(s?.soc?.let { "$it %" }, s?.rangeKm?.let { "$it km" }).joinToString("  ·  ").ifEmpty { "–" },
                    fontSize = 15.sp, color = C.On,
                )
            }

            // Car picture from Leapmotor
            CarPicture(state)

            // Quick actions
            val locked = s?.locked != false
            val busy = state.busy
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                QuickAction(if (locked) Icons.Outlined.Lock else Icons.Outlined.LockOpen, if (locked) "Lås opp" else "Lås",
                    active = locked, busy = busy == Command.LOCK.label || busy == Command.UNLOCK.label) { cmd(if (locked) Command.UNLOCK else Command.LOCK) }
                QuickAction(Icons.Outlined.Air, "Klima", active = s?.acOn == true, busy = busy?.startsWith("Klima") == true) {
                    if (s?.acOn == true) vm.send(Command.AC_OFF)
                    else vm.startClimate(defaultMode(s?.interiorTemp, state.climateTemp), state.climateTemp, 4)
                }
                QuickAction(ImageVector.vectorResource(R.drawable.ic_tailgate), "Bakluke", active = s?.tailgateOpen == true,
                    busy = busy == Command.TRUNK_OPEN.label || busy == Command.TRUNK_CLOSE.label) {
                    cmd(if (s?.tailgateOpen == true) Command.TRUNK_CLOSE else Command.TRUNK_OPEN)
                }
                QuickAction(Icons.Outlined.EvStation, "Frigjør ladekabel", active = s?.pluggedIn == true, busy = busy == Command.UNLOCK_CHARGER.label) {
                    cmd(Command.UNLOCK_CHARGER)
                }
                QuickAction(Icons.Outlined.Campaign, "Finn bilen", busy = busy == Command.FIND_CAR.label) { vm.send(Command.FIND_CAR) }
            }

            // Areas
            NavRow(Icons.Outlined.Thermostat, "Klima", climateLine(s)) { go(Page.CLIMATE) }
            NavRow(Icons.Outlined.Bolt, "Lading", chargeLine(s)) { go(Page.CHARGE) }
            NavRow(Icons.Outlined.NearMe, "Posisjon", if (s?.latitude != null) "Vis i kart" else null) { go(Page.LOCATION) }
            NavRow(Icons.Outlined.Shield, "Sikkerhet", securityLine(s)) { go(Page.SECURITY) }
            NavRow(Icons.Outlined.DirectionsCar, "Bil", carLine(s)) { go(Page.CAR) }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun CarPicture(state: UiState) {
    val pkg = state.carImage ?: run { Spacer(Modifier.height(28.dp)); return }
    val s = state.status
    // Cycle the charging animation frames while charging.
    var frame by remember { mutableIntStateOf(2) }
    val animate = s?.isCharging == true && pkg.chargeFrameCount > 1
    LaunchedEffect(animate) {
        while (animate) { delay(160); frame = if (frame >= 15) 2 else frame + 1 }
    }
    val bitmap = remember(pkg, s, if (animate) frame else 0) { pkg.compose(s, if (animate) frame else null)?.asImageBitmap() }
    if (bitmap != null) {
        Image(
            bitmap, contentDescription = "Bilen din",
            modifier = Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat() / bitmap.height).padding(vertical = 8.dp),
            contentScale = ContentScale.Fit,
        )
    }
}

/** Small battery outline filled to the charge level. */
@Composable
fun BatteryGlyph(soc: Int, charging: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(32.dp).height(15.dp).border(1.5.dp, C.Sub, RoundedCornerShape(4.dp)).padding(2.dp)) {
            Box(
                Modifier.fillMaxHeight().fillMaxWidth(soc.coerceIn(0, 100) / 100f).clip(RoundedCornerShape(2.dp))
                    .background(if (charging) C.Accent else if (soc <= 15) C.Danger else C.On),
            )
        }
        Box(Modifier.padding(start = 1.dp).width(2.dp).height(6.dp).clip(RoundedCornerShape(1.dp)).background(C.Sub))
    }
}

fun defaultMode(inside: Double?, target: Int) =
    if (inside != null && inside > target + 1) Command.Companion.ClimateMode.COOL else Command.Companion.ClimateMode.HEAT

private fun headline(s: CarStatus?): String = when {
    s == null -> ""
    s.driving -> "Kjører"
    s.isCharging -> "Lader" + if (s.locked == true) " · Låst" else ""
    else -> "Parkert" + when (s.locked) { true -> " · Låst"; false -> " · Ulåst"; null -> "" }
}

private fun climateLine(s: CarStatus?): String? = s?.let {
    listOfNotNull(if (it.acOn == true) "På" + (it.acSetting?.let { t -> " · ${fmt1(t)}°" } ?: "") else "Av",
        it.interiorTemp?.let { t -> "${fmt1(t)}° inne" }).joinToString(" · ")
}

fun chargeLine(s: CarStatus?): String? = s?.let {
    when (it.chargeState) {
        ChargeState.CHARGING -> listOfNotNull("Lader", it.chargingPowerKw?.takeIf { p -> p > 0 }?.let { p -> "%.1f kW".format(p) },
            it.chargeRemainMin?.takeIf { m -> m > 0 }?.let(::hm)).joinToString(" · ")
        ChargeState.FINISHED -> "Ferdig ladet"
        ChargeState.PAUSED -> "Pause"
        ChargeState.ERROR -> "Feil ved lading"
        else -> if (it.pluggedIn == true) "Kabel tilkoblet" else listOfNotNull(it.chargeLimit?.let { l -> "Grense $l %" }).joinToString()
    }
}

private fun securityLine(s: CarStatus?): String? = s?.let {
    when {
        it.doorsOpen.isNotEmpty() -> "Åpen: " + it.doorsOpen.joinToString()
        it.windowsOpen.isNotEmpty() -> "Vinduer åpne"
        else -> "Alt lukket" + if (it.sentryMode == true) " · Vaktmodus på" else ""
    }
}

private fun carLine(s: CarStatus?): String? = s?.let {
    listOfNotNull(it.odometerKm?.let { km -> "%,d km".format(km).replace(',', ' ') },
        when (it.tires.allOk) { true -> "Dekktrykk OK"; false -> "Sjekk dekktrykk"; null -> null }).joinToString(" · ")
}

@Composable
fun InvitationScreen(invitations: List<ShareInvitation>, onAccept: (ShareInvitation) -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Bil delt med deg", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = C.On)
        invitations.forEach { inv ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Card).padding(16.dp)) {
                Text(inv.carType?.uppercase()?.let { "Leapmotor $it" } ?: "Leapmotor", fontSize = 16.sp, color = C.On)
                inv.ownerNickname?.let { Text("Fra $it", color = C.Sub) }
                Spacer(Modifier.height(12.dp))
                BigButton("Godta") { onAccept(inv) }
            }
        }
    }
}

@Composable
fun NoCarScreen(onRetry: () -> Unit, onLogout: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Ingen bil", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = C.On)
        Text("Del bilen til denne kontoen fra Leapmotor-appen.", color = C.Sub)
        BigButton("Prøv igjen", onClick = onRetry)
        TextButton(onClick = onLogout) { Text("Logg ut", color = C.Sub) }
    }
}

