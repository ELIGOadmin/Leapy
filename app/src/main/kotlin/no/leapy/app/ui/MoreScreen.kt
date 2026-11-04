package no.leapy.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WbShade
import androidx.compose.material.icons.filled.Window
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import no.leapy.app.CarViewModel
import no.leapy.app.UiState
import no.leapy.core.Command

@Composable
fun MoreScreen(state: UiState, vm: CarViewModel, confirmer: Confirmer) {
    val s = state.status
    val context = LocalContext.current

    RefreshableColumn(state, vm) {
        Text("Mer", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        SectionCard("Posisjon") {
            if (s?.latitude != null && s.longitude != null) {
                InfoRow("Koordinater", "%.5f, %.5f".format(s.latitude, s.longitude))
                Button(onClick = {
                    val uri = Uri.parse("geo:${s.latitude},${s.longitude}?q=${s.latitude},${s.longitude}(Bilen)")
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                }, modifier = Modifier.fillMaxWidth()) { Text("Vis i kart") }
            } else Text("Posisjon er ikke tilgjengelig.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionTile("Åpne vinduer", Icons.Filled.Window, Modifier.weight(1f), busy = state.busy == Command.WINDOWS_OPEN.label) {
                confirmer.confirm(Command.WINDOWS_OPEN.label) { vm.send(Command.WINDOWS_OPEN) }
            }
            ActionTile("Lukk vinduer", Icons.Filled.Window, Modifier.weight(1f), busy = state.busy == Command.WINDOWS_CLOSE.label) { vm.send(Command.WINDOWS_CLOSE) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionTile("Åpne solskjerm", Icons.Filled.WbShade, Modifier.weight(1f), busy = state.busy == Command.SUNSHADE_OPEN.label) { vm.send(Command.SUNSHADE_OPEN) }
            ActionTile("Lukk solskjerm", Icons.Filled.WbShade, Modifier.weight(1f), busy = state.busy == Command.SUNSHADE_CLOSE.label) { vm.send(Command.SUNSHADE_CLOSE) }
        }
        SectionCard("Vaktmodus") {
            ToggleRow("Vaktmodus (kamera)", s?.sentryMode, { vm.send(Command.SENTRY_ON) }, { vm.send(Command.SENTRY_OFF) })
        }

        if (s != null) SectionCard("Bil") {
            s.odometerKm?.let { InfoRow("Kilometerstand", "%,d km".format(it).replace(',', ' ')) }
            val t = s.tires
            fun bar(k: Int?) = k?.let { "%.1f".format(it / 100.0) } ?: "–"
            InfoRow("Dekktrykk foran (bar)", "${bar(t.flKpa)}  /  ${bar(t.frKpa)}")
            InfoRow("Dekktrykk bak (bar)", "${bar(t.rlKpa)}  /  ${bar(t.rrKpa)}")
            if (t.allOk == false) Text("Sjekk dekktrykket", color = Warn)
            state.vehicle?.let { InfoRow("VIN", it.vin) }
        }

        if (state.vehicles.size > 1) SectionCard("Velg bil") {
            state.vehicles.forEach { v ->
                OutlinedButton(onClick = { vm.selectVehicle(v) }, modifier = Modifier.fillMaxWidth()) {
                    Text(v.displayName + if (v.vin == state.vehicle?.vin) " ✓" else "")
                }
            }
        }

        var pin by remember { mutableStateOf("") }
        SectionCard("PIN-kode") {
            Text(if (state.hasPin) "PIN-koden er lagret. Skriv en ny for å bytte." else "Legg inn bilens PIN-kode for å kunne styre bilen.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(8) }, label = { Text("PIN-kode") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
            Button(onClick = { vm.updatePin(pin); pin = "" }, enabled = pin.length >= 4, modifier = Modifier.fillMaxWidth()) { Text("Lagre PIN") }
        }

        SectionCard("Om Leapy") {
            Text("Leapy er en uoffisiell app og ikke laget av Leapmotor. Den bruker den samme skytjenesten som Leapmotor-appen. Leapmotor kan endre tjenesten når som helst.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = vm::logout, modifier = Modifier.fillMaxWidth()) {
                Text("Logg ut", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

