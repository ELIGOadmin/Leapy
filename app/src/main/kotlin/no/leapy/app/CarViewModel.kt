package no.leapy.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import no.leapy.core.CarStatus
import no.leapy.core.Command
import no.leapy.core.LeapmotorClient
import no.leapy.core.ShareInvitation
import no.leapy.core.Vehicle
import no.leapy.core.obj
import no.leapy.core.parseJson

data class UiState(
    val loggedIn: Boolean = false,
    val loggingIn: Boolean = false,
    val refreshing: Boolean = false,
    val vehicles: List<Vehicle> = emptyList(),
    val vehicle: Vehicle? = null,
    val status: CarStatus? = null,
    val lastFetchMillis: Long? = null,
    val invitations: List<ShareInvitation> = emptyList(),
    /** Label of the command being sent right now. */
    val busy: String? = null,
    val message: String? = null,
    val error: String? = null,
    val climateTemp: Int = 21,
    val hasPin: Boolean = false,
)

class CarViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SecureStore(app)
    private var client: LeapmotorClient? = null

    private val _state = MutableStateFlow(UiState(climateTemp = store.lastClimateTemp))
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        store.credentials?.let { c ->
            client = newClient(c)
            val cached = store.lastStatusJson?.let { runCatching { CarStatus.parse(parseJson(it).obj!!) }.getOrNull() }
            _state.update { it.copy(loggedIn = true, status = cached, hasPin = c.pin.isNotBlank()) }
            refresh()
        }
    }

    private fun newClient(c: Credentials) =
        LeapmotorClient(c.email, c.password, c.pin, store.session, onSessionChanged = { store.session = it })

    fun login(email: String, password: String, pin: String) {
        val c = Credentials(email.trim(), password, pin.trim())
        _state.update { it.copy(loggingIn = true, error = null) }
        viewModelScope.launch {
            try {
                val cl = LeapmotorClient(c.email, c.password, c.pin, null, onSessionChanged = { store.session = it })
                withContext(Dispatchers.IO) { cl.login() }
                store.credentials = c
                client = cl
                _state.update { it.copy(loggedIn = true, loggingIn = false, hasPin = c.pin.isNotBlank()) }
                refresh()
            } catch (e: Exception) {
                _state.update { it.copy(loggingIn = false, error = friendly(e)) }
            }
        }
    }

    fun logout() {
        store.clear()
        client = null
        _state.value = UiState()
    }

    fun updatePin(pin: String) {
        val c = store.credentials ?: return
        val n = c.copy(pin = pin.trim())
        store.credentials = n
        client = newClient(n)
        _state.update { it.copy(hasPin = n.pin.isNotBlank(), message = "PIN-koden er lagret") }
    }

    fun selectVehicle(v: Vehicle) {
        store.selectedVin = v.vin
        _state.update { it.copy(vehicle = v, status = null) }
        refresh()
    }

    fun refresh(silent: Boolean = false) {
        val cl = client ?: return
        if (_state.value.refreshing) return
        _state.update { it.copy(refreshing = !silent, error = if (silent) it.error else null) }
        viewModelScope.launch {
            try {
                val vehicles = withContext(Dispatchers.IO) { cl.vehicles(forceRefresh = _state.value.vehicles.isEmpty()) }
                if (vehicles.isEmpty()) {
                    val inv = withContext(Dispatchers.IO) { cl.shareInvitations() }
                    _state.update { it.copy(refreshing = false, vehicles = emptyList(), vehicle = null, invitations = inv) }
                    return@launch
                }
                val v = vehicles.firstOrNull { it.vin == store.selectedVin } ?: vehicles.first()
                val raw: JsonObject = withContext(Dispatchers.IO) { cl.rawStatus(v) }
                store.lastStatusJson = raw.toString()
                _state.update {
                    it.copy(
                        refreshing = false, vehicles = vehicles, vehicle = v, invitations = emptyList(),
                        status = CarStatus.parse(raw), lastFetchMillis = System.currentTimeMillis(),
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(refreshing = false, error = if (silent) it.error else friendly(e)) }
            }
        }
    }

    fun acceptInvitation(inv: ShareInvitation) {
        val cl = client ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { cl.acceptShare(inv) }
                _state.update { it.copy(message = "Bilen er lagt til", vehicles = emptyList()) }
                delay(1500)
                refresh()
            } catch (e: Exception) {
                _state.update { it.copy(error = friendly(e)) }
            }
        }
    }

    fun send(cmd: Command) = run(cmd.label) { cl, vin -> cl.send(vin, cmd) }

    fun startClimate(mode: Command.Companion.ClimateMode, temp: Int, fan: Int) {
        store.lastClimateTemp = temp
        _state.update { it.copy(climateTemp = temp) }
        send(Command.climate("Klima ${temp}°", mode, temp, fan))
    }

    fun setChargeLimit(percent: Int) = run("Ladegrense $percent %") { cl, vin -> cl.setChargeLimit(vin, percent) }

    private fun run(label: String, action: (LeapmotorClient, String) -> Unit) {
        val cl = client ?: return
        val vin = _state.value.vehicle?.vin ?: return
        if (_state.value.busy != null) return
        _state.update { it.copy(busy = label, error = null, message = null) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { action(cl, vin) }
                _state.update { it.copy(busy = null, message = "$label: utført") }
                delay(4000)
                refresh(silent = true)
            } catch (e: Exception) {
                _state.update { it.copy(busy = null, error = friendly(e)) }
            }
        }
    }

    fun clearMessages() = _state.update { it.copy(message = null, error = null) }

    private fun friendly(e: Exception): String = when {
        e.message.isNullOrBlank() -> "Noe gikk galt (${e.javaClass.simpleName})"
        else -> e.message!!
    }
}
