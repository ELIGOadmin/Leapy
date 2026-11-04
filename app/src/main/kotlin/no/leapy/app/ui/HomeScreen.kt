package no.leapy.app.ui

import androidx.compose.foundation.Image
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Color
import java.util.Locale
import kotlin.math.roundToInt
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
    var editing by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = editing) { editing = false }

    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { vm.refresh() }, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState(), enabled = !editing).padding(horizontal = 20.dp)) {
            // Name, state and battery
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(state.vehicle?.displayName ?: "Leapmotor", fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = C.On)
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        s?.soc?.let { soc -> BatteryGlyph(soc, s.isCharging); Spacer(Modifier.width(10.dp)) }
                        Text(headline(s), fontSize = 14.sp, color = C.Sub)
                    }
                }
                Box(Modifier.size(44.dp).clip(CircleShape).clickable { go(Page.SETTINGS) }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Settings, "Innstillinger", tint = C.Sub)
                }
            }

            // Car picture from Leapmotor
            CarPicture(state)

            // Battery and charging
            ChargePanel(state, vm)

            // Quick actions (long-press to rearrange)
            Spacer(Modifier.height(14.dp))
            QuickBar(
                items = QuickItem.from(state.quickItems),
                editing = editing,
                onEditingChange = { editing = it },
                onChange = { l -> vm.setQuickItems(l.map { it.name }) },
            ) { quickSpec(it, state, vm, confirmer) }

            // Areas
            Spacer(Modifier.height(8.dp))
            NavRow(Icons.Outlined.Thermostat, "Klima", climateLine(s)) { go(Page.CLIMATE) }
            NavRow(Icons.Outlined.NearMe, "Posisjon", if (s?.latitude != null) "Vis i kart" else null) { go(Page.LOCATION) }
            NavRow(Icons.Outlined.Shield, "Sikkerhet", securityLine(s)) { go(Page.SECURITY) }
            NavRow(Icons.Outlined.DirectionsCar, "Bil", carLine(s)) { go(Page.CAR) }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun quickSpec(item: QuickItem, state: UiState, vm: CarViewModel, confirmer: Confirmer): QuickSpec {
    val s = state.status
    val busy = state.busy
    fun cmd(c: Command) { if (sensitive(c)) confirmer.confirm(c.label) { vm.send(c) } else vm.send(c) }
    fun busyWith(vararg c: Command) = c.any { it.label == busy }
    return when (item) {
        QuickItem.LOCK -> {
            val locked = s?.locked != false
            QuickSpec(
                if (locked) Icons.Outlined.Lock else Icons.Outlined.LockOpen, if (locked) "Lås opp" else "Lås",
                active = locked, busy = busyWith(Command.LOCK, Command.UNLOCK),
                caption = when (s?.locked) { true -> "Låst"; false -> "Åpen"; null -> null },
            ) { cmd(if (locked) Command.UNLOCK else Command.LOCK) }
        }
        QuickItem.CLIMATE -> QuickSpec(Icons.Outlined.Air, "Klima", active = s?.acOn == true, busy = busy?.startsWith("Klima") == true) {
            if (s?.acOn == true) vm.send(Command.AC_OFF)
            else vm.startClimate(defaultMode(s?.interiorTemp, state.climateTemp), state.climateTemp, 4)
        }
        QuickItem.TAILGATE -> QuickSpec(ImageVector.vectorResource(R.drawable.ic_tailgate), "Bakluke", active = s?.tailgateOpen == true,
            busy = busyWith(Command.TRUNK_OPEN, Command.TRUNK_CLOSE)) { cmd(if (s?.tailgateOpen == true) Command.TRUNK_CLOSE else Command.TRUNK_OPEN) }
        QuickItem.CHARGE_PORT -> QuickSpec(Icons.Outlined.EvStation, "Frigjør ladekabel", active = s?.pluggedIn == true,
            busy = busyWith(Command.UNLOCK_CHARGER)) { cmd(Command.UNLOCK_CHARGER) }
        QuickItem.FIND -> QuickSpec(Icons.Outlined.Campaign, "Finn bilen", busy = busyWith(Command.FIND_CAR)) { vm.send(Command.FIND_CAR) }
        QuickItem.SENTRY -> QuickSpec(Icons.Outlined.Videocam, "Vaktmodus", active = s?.sentryMode == true,
            busy = busyWith(Command.SENTRY_ON, Command.SENTRY_OFF)) { vm.send(if (s?.sentryMode == true) Command.SENTRY_OFF else Command.SENTRY_ON) }
        QuickItem.PREHEAT -> QuickSpec(ImageVector.vectorResource(R.drawable.ic_battery_heat), "Forvarm batteri",
            busy = busyWith(Command.BATTERY_PREHEAT_ON)) { vm.send(Command.BATTERY_PREHEAT_ON) }
        QuickItem.CHARGE -> {
            val charging = s?.isCharging == true
            QuickSpec(Icons.Outlined.BatteryChargingFull, if (charging) "Stopp lading" else "Start lading", active = charging,
                busy = busyWith(Command.CHARGE_START, Command.CHARGE_STOP)) { vm.send(if (charging) Command.CHARGE_STOP else Command.CHARGE_START) }
        }
        QuickItem.WINDOWS -> {
            val open = s?.windowsOpen?.isNotEmpty() == true
            QuickSpec(ImageVector.vectorResource(R.drawable.ic_window), if (open) "Lukk vinduer" else "Åpne vinduer", active = open,
                busy = busyWith(Command.WINDOWS_OPEN, Command.WINDOWS_CLOSE)) { cmd(if (open) Command.WINDOWS_CLOSE else Command.WINDOWS_OPEN) }
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

/** Battery outline filled to the charge level, with the percentage inside. */
@Composable
fun BatteryGlyph(soc: Int, charging: Boolean) {
    val fill = when { charging -> C.Accent; soc <= 15 -> C.Danger; else -> C.Sub }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(40.dp).height(20.dp).border(1.5.dp, C.Sub, RoundedCornerShape(5.dp)).padding(2.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(soc.coerceIn(0, 100) / 100f)
                .clip(RoundedCornerShape(3.dp)).background(fill.copy(alpha = 0.45f)))
            Text("$soc", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = C.On)
        }
        Box(Modifier.padding(start = 1.dp).width(2.dp).height(7.dp).clip(RoundedCornerShape(1.dp)).background(C.Sub))
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
        else -> if (it.pluggedIn == true) "Kabel tilkoblet" else "Ikke tilkoblet"
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


// ---------------------------------------------------------------- Charging panel

private const val MIN_LIMIT = 20

/** Battery %, range, charge bar with a draggable limit, and the charging state. */
@Composable
private fun ChargePanel(state: UiState, vm: CarViewModel) {
    val s = state.status
    var dragging by remember { mutableStateOf<Int?>(null) }
    var committed by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(s?.chargeLimit) { if (s?.chargeLimit == committed) committed = null }
    LaunchedEffect(state.error) { if (state.error != null) committed = null }
    val limit = dragging ?: committed ?: s?.chargeLimit

    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.Bottom) {
        LiveSoc(s, state.lastFetchMillis)
        s?.rangeKm?.let { Text("$it km", fontSize = 22.sp, color = C.Sub, modifier = Modifier.padding(start = 10.dp, bottom = 10.dp)) }
        Spacer(Modifier.weight(1f))
        if (s?.pluggedIn == true || s?.isCharging == true) {
            val charging = s.isCharging
            val busy = state.busy == Command.CHARGE_START.label || state.busy == Command.CHARGE_STOP.label
            Box(
                Modifier.padding(bottom = 12.dp).clip(RoundedCornerShape(10.dp)).background(C.Card)
                    .clickable(enabled = !busy) { vm.send(if (charging) Command.CHARGE_STOP else Command.CHARGE_START) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (busy) androidx.compose.material3.CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = C.Sub)
                else Text(if (charging) "Stopp" else "Start", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = C.On)
            }
        }
    }
    LimitBar(
        soc = s?.soc ?: 0, limit = limit, charging = s?.isCharging == true,
        onChange = { dragging = it },
        onCommit = { v ->
            dragging = null
            if (v != s?.chargeLimit && state.busy == null) { committed = v; vm.setChargeLimit(v) }
        },
    )
    Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
        Text(chargeLine(s) ?: "", fontSize = 13.sp, color = C.Sub, modifier = Modifier.weight(1f))
        limit?.let { Text("Grense $it %", fontSize = 13.sp, color = if (dragging != null) C.On else C.Sub) }
    }
}

/**
 * Battery percentage. While charging it shows two decimals that keep counting up between status
 * updates, estimated from the charging power (like fast-charging phones do).
 */
@Composable
private fun LiveSoc(s: CarStatus?, fetchedAt: Long?) {
    val base = s?.preciseSoc ?: s?.soc?.toDouble()
    if (s == null || base == null) { Text("–", fontSize = 56.sp, fontWeight = FontWeight.Light, color = C.On); return }
    val charging = s.isCharging && (s.chargingPowerKw ?: 0.0) > 0
    if (!charging) {
        Text("${s.soc ?: base.toInt()} %", fontSize = 56.sp, fontWeight = FontWeight.Light, color = C.On, letterSpacing = (-1.5).sp)
        return
    }
    val capacityKwh = s.remainingKwh?.takeIf { it > 0 && base > 5 }?.let { it / (base / 100.0) } ?: 69.9
    val pctPerMs = (s.chargingPowerKw ?: 0.0) / capacityKwh * 100.0 / 3_600_000.0
    val cap = (s.chargeLimit ?: 100).toDouble()
    var shown by remember { mutableDoubleStateOf(base) }
    LaunchedEffect(base, fetchedAt) {
        val start = fetchedAt ?: System.currentTimeMillis()
        // Never jump backwards by a small amount; snap only on a real correction.
        if (base > shown || shown - base > 0.5) shown = base
        while (true) {
            val est = (base + pctPerMs * (System.currentTimeMillis() - start)).coerceAtMost(cap)
            if (est > shown) shown = est
            delay(100)
        }
    }
    Text("%.2f %%".format(Locale("nb", "NO"), shown), fontSize = 56.sp, fontWeight = FontWeight.Light, color = C.On, letterSpacing = (-1.5).sp)
}

/** Charge bar. Drag sideways to set the charge limit (1 % steps); marks at 80 and 90 %. */
@Composable
private fun LimitBar(soc: Int, limit: Int?, charging: Boolean, onChange: (Int) -> Unit, onCommit: (Int) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val change by rememberUpdatedState(onChange)
    val commit by rememberUpdatedState(onCommit)
    var widthPx by remember { mutableFloatStateOf(1f) }
    var last by remember { mutableIntStateOf(-1) }
    fun valueAt(x: Float) = (x / widthPx * 100f).roundToInt().coerceIn(MIN_LIMIT, 100)
    fun update(v: Int) {
        if (v == last) return
        last = v
        haptic.performHapticFeedback(if (v == 80 || v == 90 || v == 100) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove)
        change(v)
    }
    val fillColor = if (charging) C.Accent else C.On
    Canvas(
        Modifier.fillMaxWidth().height(40.dp).onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { update(valueAt(it.x)) },
                    onDragEnd = { if (last >= 0) commit(last); last = -1 },
                    onDragCancel = { if (last >= 0) commit(last); last = -1 },
                ) { ch, _ -> ch.consume(); update(valueAt(ch.position.x)) }
            },
    ) {
        val h = 10.dp.toPx()
        val cy = size.height / 2
        val r = CornerRadius(h / 2, h / 2)
        drawRoundRect(C.Card, Offset(0f, cy - h / 2), Size(size.width, h), r)
        val fw = size.width * soc.coerceIn(0, 100) / 100f
        if (fw > 0) drawRoundRect(fillColor, Offset(0f, cy - h / 2), Size(fw, h), r)
        // Marks at 80 and 90 %
        for (m in listOf(80, 90)) {
            val x = size.width * m / 100f
            drawLine(C.Sub, Offset(x, cy - h / 2 - 5.dp.toPx()), Offset(x, cy + h / 2 + 5.dp.toPx()), strokeWidth = 1.5.dp.toPx())
        }
        // Limit handle
        if (limit != null && limit in 1..100) {
            val w = 4.dp.toPx(); val hh = 26.dp.toPx()
            val x = (size.width * limit / 100f - w / 2).coerceIn(0f, size.width - w)
            drawRoundRect(Color.White, Offset(x, cy - hh / 2), Size(w, hh), CornerRadius(w / 2, w / 2))
        }
    }
}
