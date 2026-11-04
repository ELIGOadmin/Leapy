package no.leapy.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import no.leapy.app.CarViewModel
import no.leapy.app.UiState
import no.leapy.core.Command
import no.leapy.core.Command.Companion.ClimateMode

@Composable
fun ClimateScreen(state: UiState, vm: CarViewModel) {
    val s = state.status
    var temp by remember { mutableIntStateOf(state.climateTemp) }
    var mode by remember { mutableStateOf(defaultMode(s?.interiorTemp, state.climateTemp)) }
    var fan by remember { mutableFloatStateOf(4f) }

    RefreshableColumn(state, vm) {
        Text("Klima", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Nå: " + (s?.interiorTemp?.let { "${fmt(it)}° inne" } ?: "ukjent temperatur") +
                (if (s?.acOn == true) " · klima er på" else ""),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionCard("Start klima") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = { temp = (temp - 1).coerceAtLeast(18) }) { Icon(Icons.Filled.Remove, "Kaldere") }
                Text("$temp°", fontSize = 52.sp, fontWeight = FontWeight.Bold)
                FilledTonalIconButton(onClick = { temp = (temp + 1).coerceAtMost(32) }) { Icon(Icons.Filled.Add, "Varmere") }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val modes = listOf(ClimateMode.HEAT to "Varme", ClimateMode.COOL to "Kjøle", ClimateMode.FAN to "Vifte")
                modes.forEachIndexed { i, (m, label) ->
                    SegmentedButton(selected = mode == m, onClick = { mode = m }, shape = SegmentedButtonDefaults.itemShape(i, modes.size)) { Text(label) }
                }
            }
            Text("Viftestyrke ${fan.toInt()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(value = fan, onValueChange = { fan = it }, valueRange = 1f..7f, steps = 5)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { vm.startClimate(mode, temp, fan.toInt()) }, enabled = state.busy == null, modifier = Modifier.weight(1f).height(52.dp)) { Text("Start") }
                Button(onClick = { vm.send(Command.AC_OFF) }, enabled = state.busy == null, modifier = Modifier.weight(1f).height(52.dp)) { Text("Slå av") }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionTile("Hurtigvarme", Icons.Filled.Whatshot, Modifier.weight(1f), busy = state.busy == Command.QUICK_HEAT.label) { vm.send(Command.QUICK_HEAT) }
            ActionTile("Hurtigkjøling", Icons.Filled.AcUnit, Modifier.weight(1f), busy = state.busy == Command.QUICK_COOL.label) { vm.send(Command.QUICK_COOL) }
            ActionTile("Avising", Icons.Filled.Air, Modifier.weight(1f), busy = state.busy == Command.DEFROST.label) { vm.send(Command.DEFROST) }
        }

        SectionCard("Setevarme") {
            SeatLevel("Fører", s?.driverSeatHeat) { vm.send(Command.seatHeat(1, it)) }
            SeatLevel("Passasjer", s?.passengerSeatHeat) { vm.send(Command.seatHeat(2, it)) }
        }

        SectionCard("Varme") {
            ToggleRow("Rattvarme", s?.steeringWheelHeat, { vm.send(Command.STEERING_HEAT_ON) }, { vm.send(Command.STEERING_HEAT_OFF) })
            ToggleRow("Speilvarme", null, { vm.send(Command.MIRROR_HEAT_ON) }, { vm.send(Command.MIRROR_HEAT_OFF) })
            ToggleRow("Forvarm batteri", null, { vm.send(Command.BATTERY_PREHEAT_ON) }, { vm.send(Command.BATTERY_PREHEAT_OFF) })
        }
    }
}

@Composable
private fun SeatLevel(label: String, current: Int?, onSet: (Int) -> Unit) {
    Text(label + (current?.let { " (nå: ${if (it == 0) "av" else "nivå $it"})" } ?: ""))
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        val labels = listOf("Av", "1", "2", "3")
        labels.forEachIndexed { i, l ->
            SegmentedButton(selected = current == i, onClick = { onSet(i) }, shape = SegmentedButtonDefaults.itemShape(i, labels.size)) { Text(l) }
        }
    }
}

@Composable
fun ToggleRow(label: String, on: Boolean?, onOn: () -> Unit, onOff: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label + when (on) { true -> " · på"; false -> " · av"; null -> "" }, modifier = Modifier.weight(1f))
        SingleChoiceSegmentedButtonRow {
            SegmentedButton(selected = on == true, onClick = onOn, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("På") }
            SegmentedButton(selected = on == false, onClick = onOff, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Av") }
        }
    }
}

@Composable
fun ChargeScreen(state: UiState, vm: CarViewModel) {
    val s = state.status
    var limit by remember { mutableFloatStateOf((s?.chargeLimit ?: 80).toFloat()) }
    LaunchedEffect(s?.chargeLimit) { s?.chargeLimit?.let { limit = it.toFloat() } }

    RefreshableColumn(state, vm) {
        Text("Lading", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            BatteryRing(s?.soc, s?.rangeKm, s?.isCharging == true, s?.chargeLimit)
            Text(chargeLine(state), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SectionCard("Ladegrense") {
            Text("${limit.toInt()} %", fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Slider(value = limit, onValueChange = { limit = it }, valueRange = 50f..100f, steps = 9)
            Text("80 % er best for batteriet i hverdagen. Bruk 100 % før lange turer.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                onClick = { vm.setChargeLimit(limit.toInt()) },
                enabled = state.busy == null && limit.toInt() != s?.chargeLimit,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Lagre ladegrense") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionTile("Start lading", Icons.Filled.BatteryChargingFull, Modifier.weight(1f), busy = state.busy == Command.CHARGE_START.label) { vm.send(Command.CHARGE_START) }
            ActionTile("Stopp lading", Icons.Filled.StopCircle, Modifier.weight(1f), busy = state.busy == Command.CHARGE_STOP.label) { vm.send(Command.CHARGE_STOP) }
            ActionTile("Frigjør kabel", Icons.Filled.ElectricalServices, Modifier.weight(1f), busy = state.busy == Command.UNLOCK_CHARGER.label) { vm.send(Command.UNLOCK_CHARGER) }
        }

        if (s != null) SectionCard("Detaljer") {
            InfoRow("Status", chargeLine(state))
            InfoRow("Kabel", if (s.pluggedIn == true) "Tilkoblet" else "Ikke tilkoblet")
            s.chargingPowerKw?.takeIf { it > 0 }?.let { InfoRow("Effekt", "%.1f kW".format(it)) }
            s.chargeRemainMin?.takeIf { it > 0 && s.isCharging }?.let { InfoRow("Tid igjen", "${it / 60} t ${it % 60} min") }
        }
    }
}

