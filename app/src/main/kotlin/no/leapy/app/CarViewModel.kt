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
    val carImage: CarImagePackage? = null,
    /** The quick actions on the home screen, in order. Kept across logins. */
    val quickItems: List<String> = emptyList(),
)

class CarViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SecureStore(app)
    private var client: LeapmotorClient? = null

    private val uiPrefs = app.getSharedPreferences("leapy_ui", android.content.Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(
        UiState(climateTemp = store.lastClimateTemp, quickItems = uiPrefs.getString("quick", null)?.split(',')?.filter { it.isNotBlank() } ?: emptyList()),
    )
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
        java.io.File(getApplication<Application>().filesDir, "car").deleteRecursively()
        client = null
        _state.value = UiState(quickItems = _state.value.quickItems)
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
        _state.update { it.copy(vehicle = v, status = null, carImage = null) }
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
                if (_state.value.carImage == null) loadCarImage(cl, v)
                store.lastStatusJson = raw.toString()
                _state.update {
                    it.copy(
                        refreshing = false, vehicles = vehicles, vehicle = v, invitations = emptyList(),
                        status = withExpected(CarStatus.parse(raw)), lastFetchMillis = System.currentTimeMillis(),
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(refreshing = false, error = if (silent) it.error else friendly(e)) }
            }
        }
    }

    private var carImageLoading = false

    /** Loads Leapmotor's picture of this car, cached on the phone after the first download. */
    private fun loadCarImage(cl: LeapmotorClient, v: Vehicle) {
        if (carImageLoading) return
        carImageLoading = true
        viewModelScope.launch {
            try {
                val pkg = withContext(Dispatchers.IO) {
                    val dir = java.io.File(getApplication<Application>().filesDir, "car").apply { mkdirs() }
                    val cached = dir.listFiles()?.firstOrNull { it.name.startsWith(v.vin) }
                    val bytes = cached?.readBytes() ?: run {
                        val key = cl.carPictureKey(v) ?: return@withContext null
                        cl.carPicturePackage(key).also { java.io.File(dir, "${v.vin}.zip").writeBytes(it) }
                    }
                    CarImagePackage.fromZip(bytes)
                }
                if (pkg != null) _state.update { it.copy(carImage = pkg) }
            } catch (_: Exception) {
                // No picture: the home screen simply shows none.
            } finally {
                carImageLoading = false
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

    fun send(cmd: Command) = run(cmd.label, expectedAfter(cmd)) { cl, vin -> cl.send(vin, cmd) }

    fun startClimate(mode: Command.Companion.ClimateMode, temp: Int, fan: Int) {
        store.lastClimateTemp = temp
        _state.update { it.copy(climateTemp = temp) }
        send(Command.climate("Klima ${temp}°", mode, temp, fan))
    }

    fun setChargeLimit(percent: Int) = run("Ladegrense $percent %") { cl, vin -> cl.setChargeLimit(vin, percent) }

    /**
     * What the status should look like after a command the car accepted. Leapmotor's cloud often
     * keeps reporting the old value for a while, so the app shows the expected state until the car
     * reports the same thing (or a few minutes pass).
     */
    private class Expected(val patch: (CarStatus) -> CarStatus, val until: Long)
    private val expected = mutableListOf<Expected>()

    private fun withExpected(s: CarStatus): CarStatus = synchronized(expected) {
        val now = System.currentTimeMillis()
        expected.removeAll { it.until < now || it.patch(s) == s }
        expected.fold(s) { acc, e -> e.patch(acc) }
    }

    private fun expectedAfter(cmd: Command): ((CarStatus) -> CarStatus)? = when {
        cmd == Command.LOCK -> { s: CarStatus -> s.copy(locked = true) }
        cmd == Command.UNLOCK -> { s: CarStatus -> s.copy(locked = false) }
        cmd == Command.TRUNK_OPEN -> { s: CarStatus -> s.copy(tailgateOpen = true) }
        cmd == Command.TRUNK_CLOSE -> { s: CarStatus -> s.copy(tailgateOpen = false) }
        cmd == Command.SENTRY_ON -> { s: CarStatus -> s.copy(sentryMode = true) }
        cmd == Command.SENTRY_OFF -> { s: CarStatus -> s.copy(sentryMode = false) }
        cmd == Command.STEERING_HEAT_ON -> { s: CarStatus -> s.copy(steeringWheelHeat = true) }
        cmd == Command.STEERING_HEAT_OFF -> { s: CarStatus -> s.copy(steeringWheelHeat = false) }
        cmd == Command.MIRROR_HEAT_ON -> { s: CarStatus -> s.copy(mirrorHeatOn = true) }
        cmd == Command.MIRROR_HEAT_OFF -> { s: CarStatus -> s.copy(mirrorHeatOn = false) }
        cmd == Command.DEFROST -> { s: CarStatus -> s.copy(defrostOn = true, acOn = true) }
        cmd == Command.QUICK_HEAT -> { s: CarStatus -> s.copy(rapidHeatOn = true, acOn = true) }
        cmd == Command.QUICK_COOL -> { s: CarStatus -> s.copy(acOn = true) }
        cmd == Command.AC_OFF -> { s: CarStatus -> s.copy(acOn = false, defrostOn = false, rapidHeatOn = false) }
        cmd.cmdId == "170" -> { s: CarStatus -> s.copy(acOn = true) }
        cmd.cmdId == "301" -> {
            // Content is {"value":"<position>,<level>"}: 1 = driver, 2 = passenger.
            val v = Regex("\"(\\d+),(\\d+)\"").find(cmd.content)?.groupValues
            val pos = v?.get(1)?.toIntOrNull(); val level = v?.get(2)?.toIntOrNull()
            when {
                level == null -> null
                pos == 1 -> { s: CarStatus -> s.copy(driverSeatHeat = level) }
                pos == 2 -> { s: CarStatus -> s.copy(passengerSeatHeat = level) }
                else -> null
            }
        }
        else -> null
    }

    private fun run(label: String, expect: ((CarStatus) -> CarStatus)? = null, action: (LeapmotorClient, String) -> Unit) {
        val cl = client ?: return
        val vin = _state.value.vehicle?.vin ?: return
        if (_state.value.busy != null) return
        _state.update { it.copy(busy = label, error = null, message = null) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { action(cl, vin) }
                // The car accepted it: show the new state right away.
                if (expect != null) {
                    synchronized(expected) { expected += Expected(expect, System.currentTimeMillis() + 5 * 60_000) }
                    _state.update { st -> st.copy(status = st.status?.let(expect)) }
                }
                // Keep the buttons locked until the car reports its new state.
                delay(3000)
                runCatching { fetchStatus(cl) }
                _state.update { it.copy(busy = null) }
            } catch (e: Exception) {
                _state.update { it.copy(busy = null, error = friendly(e)) }
            }
        }
    }

    fun setQuickItems(items: List<String>) {
        uiPrefs.edit().putString("quick", items.joinToString(",")).apply()
        _state.update { it.copy(quickItems = items) }
    }

    private suspend fun fetchStatus(cl: LeapmotorClient) {
        val v = _state.value.vehicle ?: return
        val raw: JsonObject = withContext(Dispatchers.IO) { cl.rawStatus(v) }
        store.lastStatusJson = raw.toString()
        _state.update { it.copy(status = withExpected(CarStatus.parse(raw)), lastFetchMillis = System.currentTimeMillis()) }
    }

    fun clearMessages() = _state.update { it.copy(message = null, error = null) }

    private fun friendly(e: Exception): String = when {
        e.message.isNullOrBlank() -> "Noe gikk galt (${e.javaClass.simpleName})"
        else -> e.message!!
    }
}
