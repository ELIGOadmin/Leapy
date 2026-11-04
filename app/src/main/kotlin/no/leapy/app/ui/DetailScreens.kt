package no.leapy.app.ui

import android.content.Intent
import android.location.Geocoder
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AirlineSeatReclineNormal
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.SensorDoor
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import no.leapy.app.CarViewModel
import no.leapy.app.R
import no.leapy.app.UiState
import no.leapy.core.CarStatus
import no.leapy.core.Command
import java.util.Locale

// ---------------------------------------------------------------- Climate

@Composable
fun ClimateScreen(state: UiState, vm: CarViewModel, onBack: () -> Unit) {
    val s = state.status
    var temp by remember { mutableIntStateOf(state.climateTemp) }
    val busy = state.busy
    DetailPage("Klima", onBack) {
        Text(s?.interiorTemp?.let { "Inne ${fmt1(it)}°" } ?: "", color = C.Sub, fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp), textAlign = TextAlign.Center)

        Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(Icons.Outlined.ChevronLeft, "Kaldere") { temp = (temp - 1).coerceAtLeast(18) }
            Text("$temp°", fontSize = 76.sp, fontWeight = FontWeight.Light, color = C.On,
                modifier = Modifier.weight(1f), textAlign = TextAlign.Center, letterSpacing = (-2).sp)
            RoundIcon(Icons.Outlined.ChevronRight, "Varmere") { temp = (temp + 1).coerceAtMost(32) }
        }

        // One big on/off button
        val on = s?.acOn == true
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(76.dp).clip(CircleShape).background(if (on) C.Accent else C.CardHigh)
                    .clickable(enabled = busy == null) {
                        if (on && temp == state.climateTemp) vm.send(Command.AC_OFF)
                        else vm.startClimate(defaultMode(s?.interiorTemp, temp), temp, 4)
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (busy?.startsWith("Klima") == true) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp, color = if (on) C.Bg else C.Sub)
                else Icon(Icons.Outlined.PowerSettingsNew, if (on) "Slå av" else "Slå på", tint = if (on) C.Bg else C.On, modifier = Modifier.size(34.dp))
            }
        }
        if (on && temp != state.climateTemp) Text("Trykk for å bytte til $temp°", color = C.Sub, fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp), textAlign = TextAlign.Center)

        // Seats and wheel: tap cycles the level
        Row(Modifier.fillMaxWidth().padding(top = 28.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HeatTile(Icons.Outlined.AirlineSeatReclineNormal, "Fører", s?.driverSeatHeat ?: 0, 3, Modifier.weight(1f)) { vm.send(Command.seatHeat(1, it)) }
            HeatTile(Icons.Outlined.AirlineSeatReclineNormal, "Passasjer", s?.passengerSeatHeat ?: 0, 3, Modifier.weight(1f)) { vm.send(Command.seatHeat(2, it)) }
            HeatTile(ImageVector.vectorResource(R.drawable.ic_steering_heat), "Ratt", if (s?.steeringWheelHeat == true) 1 else 0, 1, Modifier.weight(1f)) {
                vm.send(if (it > 0) Command.STEERING_HEAT_ON else Command.STEERING_HEAT_OFF)
            }
        }

        Spacer(Modifier.height(20.dp))
        SwitchRow("Avising", s?.defrostOn, busy = busy == Command.DEFROST.label) { vm.send(if (it) Command.DEFROST else Command.AC_OFF) }
        SwitchRow("Hurtigvarme", s?.rapidHeatOn, busy = busy == Command.QUICK_HEAT.label) { vm.send(if (it) Command.QUICK_HEAT else Command.AC_OFF) }
        SwitchRow("Speilvarme", s?.mirrorHeatOn, busy = busy == Command.MIRROR_HEAT_ON.label || busy == Command.MIRROR_HEAT_OFF.label) {
            vm.send(if (it) Command.MIRROR_HEAT_ON else Command.MIRROR_HEAT_OFF)
        }
        ActionRow("Hurtigkjøling", "Start", busy = busy == Command.QUICK_COOL.label) { vm.send(Command.QUICK_COOL) }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(Modifier.size(52.dp).clip(CircleShape).background(C.Card).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = C.On)
    }
}

@Composable
private fun HeatTile(icon: ImageVector, label: String, level: Int, max: Int, modifier: Modifier, onSet: (Int) -> Unit) {
    val on = level > 0
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(C.Card).clickable { onSet(if (level >= max) 0 else level + 1) }.padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, label, tint = if (on) C.Heat else C.Sub, modifier = Modifier.size(26.dp))
        Text(label, fontSize = 13.sp, color = if (on) C.On else C.Sub)
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(max) { i -> Box(Modifier.size(6.dp).clip(CircleShape).background(if (i < level) C.Heat else C.CardHigh)) }
        }
    }
}

// ---------------------------------------------------------------- Location

@Composable
fun LocationScreen(state: UiState, onBack: () -> Unit) {
    val s = state.status
    val context = LocalContext.current
    val lat = s?.latitude; val lon = s?.longitude
    val address by produceState<String?>(null, lat, lon) {
        value = if (lat == null || lon == null) null else withContext(Dispatchers.IO) {
            runCatching {
                @Suppress("DEPRECATION")
                Geocoder(context, Locale("nb", "NO")).getFromLocation(lat, lon, 1)?.firstOrNull()?.let { a ->
                    listOfNotNull(a.thoroughfare?.let { t -> listOfNotNull(t, a.subThoroughfare).joinToString(" ") }, a.locality).joinToString(", ")
                }
            }.getOrNull()
        }
    }
    DetailPage("Posisjon", onBack) {
        if (lat == null || lon == null) {
            Text("Posisjonen er ikke tilgjengelig.", color = C.Sub, modifier = Modifier.padding(top = 16.dp))
            return@DetailPage
        }
        Text(address ?: "%.5f, %.5f".format(Locale.US, lat, lon), fontSize = 22.sp, fontWeight = FontWeight.Medium, color = C.On, modifier = Modifier.padding(top = 16.dp))
        Text("Oppdatert ${ago(s.collectedAtMillis ?: state.lastFetchMillis)}", color = C.Sub, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(28.dp))
        BigButton("Åpne i kart") {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$lat,$lon(Bilen)")))
        }
        Spacer(Modifier.height(10.dp))
        BigButton("Veibeskrivelse", primary = false) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$lat,$lon&mode=w")))
        }
    }
}

// ---------------------------------------------------------------- Security

@Composable
fun SecurityScreen(state: UiState, vm: CarViewModel, confirmer: Confirmer, onBack: () -> Unit) {
    val s = state.status
    val busy = state.busy
    fun busyWith(vararg c: Command) = c.any { it.label == busy }
    val tailgate = ImageVector.vectorResource(R.drawable.ic_tailgate)
    val window = ImageVector.vectorResource(R.drawable.ic_window)
    DetailPage("Sikkerhet", onBack) {
        Spacer(Modifier.height(12.dp))
        val locked = s?.locked != false
        TileRow {
            Tile(if (locked) Icons.Outlined.Lock else Icons.Outlined.LockOpen, "Lås", when (s?.locked) { true -> "Låst"; false -> "Åpen"; null -> null },
                active = s?.locked == true, busy = busyWith(Command.LOCK, Command.UNLOCK), modifier = Modifier.weight(1f)) {
                if (locked) confirmer.confirm(Command.UNLOCK.label) { vm.send(Command.UNLOCK) } else vm.send(Command.LOCK)
            }
            Tile(Icons.Outlined.Videocam, "Vaktmodus", when (s?.sentryMode) { true -> "På"; false -> "Av"; null -> null },
                active = s?.sentryMode == true, busy = busyWith(Command.SENTRY_ON, Command.SENTRY_OFF), modifier = Modifier.weight(1f)) {
                vm.send(if (s?.sentryMode == true) Command.SENTRY_OFF else Command.SENTRY_ON)
            }
        }
        val windowsOpen = s?.windowsOpen?.isNotEmpty() == true
        TileRow {
            Tile(window, "Vinduer", s?.let { if (windowsOpen) "Åpne" else "Lukket" }, active = windowsOpen,
                busy = busyWith(Command.WINDOWS_OPEN, Command.WINDOWS_CLOSE), modifier = Modifier.weight(1f)) {
                if (windowsOpen) vm.send(Command.WINDOWS_CLOSE) else confirmer.confirm(Command.WINDOWS_OPEN.label) { vm.send(Command.WINDOWS_OPEN) }
            }
            Tile(tailgate, "Bakluke", s?.let { if (it.tailgateOpen) "Åpen" else "Lukket" }, active = s?.tailgateOpen == true,
                busy = busyWith(Command.TRUNK_OPEN, Command.TRUNK_CLOSE), modifier = Modifier.weight(1f)) {
                if (s?.tailgateOpen == true) vm.send(Command.TRUNK_CLOSE) else confirmer.confirm(Command.TRUNK_OPEN.label) { vm.send(Command.TRUNK_OPEN) }
            }
        }
        TileRow {
            Tile(Icons.Outlined.WbSunny, "Solskjerm", "Åpne", busy = busyWith(Command.SUNSHADE_OPEN), modifier = Modifier.weight(1f)) { vm.send(Command.SUNSHADE_OPEN) }
            Tile(Icons.Outlined.WbSunny, "Solskjerm", "Lukk", busy = busyWith(Command.SUNSHADE_CLOSE), modifier = Modifier.weight(1f)) { vm.send(Command.SUNSHADE_CLOSE) }
        }

        SectionLabel("Status")
        StatusLine(Icons.Outlined.SensorDoor, "Dører", s?.doorsOpen?.let { if (it.isEmpty()) "Lukket" else it.joinToString() })
        StatusLine(window, "Vinduer", s?.windowsOpen?.let { if (it.isEmpty()) "Lukket" else it.joinToString() })
        StatusLine(tailgate, "Bakluke", s?.let { if (it.tailgateOpen) "Åpen" else "Lukket" })
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun StatusLine(icon: ImageVector?, label: String, value: String?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, tint = C.Sub, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(14.dp)) }
        Text(label, color = C.Sub, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(value ?: "–", color = C.On, fontSize = 15.sp)
    }
}

// ---------------------------------------------------------------- Car

@Composable
fun CarScreen(state: UiState, vm: CarViewModel, onBack: () -> Unit) {
    val s = state.status
    val busy = state.busy
    DetailPage("Bil", onBack) {
        Spacer(Modifier.height(12.dp))
        StatStrip(
            (s?.odometerKm?.let { "%,d".format(it).replace(',', ' ') } ?: "–") to "km",
            (s?.batteryTempC?.let { "$it°" } ?: "–") to "Batteri",
            (s?.interiorTemp?.let { "${fmt1(it)}°" } ?: "–") to "Inne",
        )
        SectionLabel("Batteri")
        val heat = ImageVector.vectorResource(R.drawable.ic_battery_heat)
        TileRow {
            Tile(heat, "Forvarm batteri", "Start", busy = busy == Command.BATTERY_PREHEAT_ON.label, modifier = Modifier.weight(1f)) { vm.send(Command.BATTERY_PREHEAT_ON) }
            Tile(heat, "Forvarm batteri", "Stopp", busy = busy == Command.BATTERY_PREHEAT_OFF.label, modifier = Modifier.weight(1f)) { vm.send(Command.BATTERY_PREHEAT_OFF) }
        }
        SectionLabel("Dekktrykk (bar)")
        val t = s?.tires
        fun bar(k: Int?) = k?.let { "%.1f".format(Locale("nb", "NO"), it / 100.0) } ?: "–"
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Card).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row { TireValue("Venstre foran", bar(t?.flKpa), Modifier.weight(1f)); TireValue("Høyre foran", bar(t?.frKpa), Modifier.weight(1f)) }
            Row { TireValue("Venstre bak", bar(t?.rlKpa), Modifier.weight(1f)); TireValue("Høyre bak", bar(t?.rrKpa), Modifier.weight(1f)) }
        }
        if (t?.allOk == false) Text("Sjekk dekktrykket", color = C.Warn, modifier = Modifier.padding(top = 10.dp))
        SectionLabel("Bil")
        StatusLine(null, "Modell", state.vehicle?.carType?.uppercase())
        StatusLine(null, "Skilt", state.vehicle?.plateNumber)
        StatusLine(null, "VIN", state.vehicle?.vin)
        StatusLine(null, "Oppdatert", stamp(s?.collectedAtMillis ?: state.lastFetchMillis))
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun TireValue(label: String, value: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Light, color = C.On)
        Text(label, fontSize = 12.sp, color = C.Sub)
    }
}

// ---------------------------------------------------------------- Settings

@Composable
fun SettingsScreen(state: UiState, vm: CarViewModel, onBack: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    DetailPage("Innstillinger", onBack) {
        if (state.vehicles.size > 1) {
            SectionLabel("Bil")
            state.vehicles.forEach { v ->
                ActionRow(v.displayName, if (v.vin == state.vehicle?.vin) "Valgt" else "Velg") { vm.selectVehicle(v) }
            }
        }
        SectionLabel(if (state.hasPin) "PIN-kode (lagret)" else "PIN-kode")
        OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(8) }, singleLine = true, placeholder = { Text("Ny PIN-kode") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        BigButton("Lagre PIN", primary = false) { if (pin.length >= 4) { vm.updatePin(pin); pin = "" } }

        SectionLabel("Bluetooth-nøkkel")
        ActionRow("Start nøkkelen i bilen på nytt", "Start", busy = state.busy == Command.BLE_KEY_RESTART.label) { vm.send(Command.BLE_KEY_RESTART) }

        Spacer(Modifier.height(32.dp))
        BigButton("Logg ut", primary = false, onClick = vm::logout)
        Text("Leapy er uoffisiell og ikke laget av Leapmotor.", color = C.Faint, fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 32.dp), textAlign = TextAlign.Center)
    }
}

