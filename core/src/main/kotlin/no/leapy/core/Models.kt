package no.leapy.core

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

data class Vehicle(
    val vin: String,
    val carType: String,
    val nickname: String?,
    val plateNumber: String?,
    val carId: String?,
    val isShared: Boolean,
    val rights: List<Int>,
) {
    val displayName: String get() = nickname?.takeIf { it.isNotBlank() } ?: "Leapmotor ${carType.uppercase()}"

    /** Backend status path segment; B05/B10/B11 share the C10 endpoint. */
    val statusPath: String
        get() = when (carType.trim().lowercase()) {
            "b10", "b11", "b05" -> "c10"
            "" -> "c10"
            else -> carType.trim().lowercase()
        }

    companion object {
        fun from(o: JsonObject, shared: Boolean) = Vehicle(
            vin = o["vin"].str ?: "",
            carType = o["carType"].str ?: "",
            nickname = o["vinNickname"].str,
            plateNumber = o["plateNumber"].str,
            carId = o["carId"].str,
            isShared = shared,
            rights = (o["rightList"].str ?: "").split(",").mapNotNull { it.trim().toIntOrNull() },
        )
    }
}

data class ShareInvitation(
    val shareUserId: String,
    val vin: String,
    val carType: String?,
    val ownerNickname: String?,
) {
    companion object {
        fun from(o: JsonObject) = ShareInvitation(
            shareUserId = o["shareUserid"].str ?: "",
            vin = o["carCode"].str ?: "",
            carType = o["carType"].str,
            ownerNickname = o["nickName"].str,
        )
    }
}

enum class ChargeState { NOT_CHARGING, CHARGING, FINISHED, ERROR, SETTING, REGEN, PAUSED, UNKNOWN;
    companion object { fun of(v: Int?) = when (v) { 0 -> NOT_CHARGING; 1 -> CHARGING; 2 -> FINISHED; 3 -> ERROR; 4 -> SETTING; 5 -> REGEN; 6 -> PAUSED; else -> UNKNOWN } }
}

data class Tires(val flKpa: Int?, val frKpa: Int?, val rlKpa: Int?, val rrKpa: Int?, val allOk: Boolean?)

/** The subset of vehicle status the app shows, parsed from T03-style named fields or C10-style signals. */
data class CarStatus(
    val soc: Int?,
    val preciseSoc: Double?,
    val remainingKwh: Double?,
    val batteryTempC: Int?,
    val rangeKm: Int?,
    val chargeState: ChargeState,
    val chargeRemainMin: Int?,
    val chargingPowerKw: Double?,
    val pluggedIn: Boolean?,
    val chargeLimit: Int?,
    val odometerKm: Int?,
    val speedKmh: Int?,
    val latitude: Double?,
    val longitude: Double?,
    val locked: Boolean?,
    val doorsOpen: List<String>,
    val windowsOpen: List<String>,
    val acOn: Boolean?,
    val acSetting: Double?,
    val interiorTemp: Double?,
    val outdoorTemp: Double?,
    val driverSeatHeat: Int?,
    val passengerSeatHeat: Int?,
    val steeringWheelHeat: Boolean?,
    val sentryMode: Boolean?,
    val defrostOn: Boolean?,
    val rapidHeatOn: Boolean?,
    val mirrorHeatOn: Boolean?,
    val driving: Boolean,
    val doorFrontLeftOpen: Boolean,
    val doorFrontRightOpen: Boolean,
    val doorRearLeftOpen: Boolean,
    val doorRearRightOpen: Boolean,
    val tailgateOpen: Boolean,
    val windowFrontLeftPct: Int,
    val windowRearLeftPct: Int,
    val tires: Tires,
    val collectedAtMillis: Long?,
) {
    val isCharging: Boolean get() = chargeState == ChargeState.CHARGING

    companion object {
        private val SIGNAL_TO_NAMED = mapOf(
            "47" to "acInputSlowCharge", "1204" to "soc", "100003" to "preciseSoc", "1200" to "chargeRemainTime",
            "1178" to "batteryCurrent", "1177" to "batteryVoltage", "1197" to "dcInputFastCharge", "1149" to "chargeState",
            "3260" to "expectedMileage", "2188" to "liveRemainingRange",
            "1319" to "speed", "1318" to "totalMileage", "1010" to "gearStatus",
            "1938" to "acSwitch", "2183" to "acSetting", "1349" to "interiorTemp",
            "3727" to "leftFrontWindowPercent", "3728" to "rightFrontWindowPercent",
            "1879" to "leftRearWindowPercent", "1880" to "rightRearWindowPercent",
            "1298" to "driverDoorLockStatus", "1277" to "lbcmDriverDoorStatus", "1278" to "rbcmDriverDoorStatus",
            "1279" to "lbcmLeftRearDoorStatus", "1280" to "rbcmRightRearDoorStatus", "1281" to "bbcmBackDoorStatus",
            "2667" to "leftFrontTirePressure", "2653" to "rightFrontTirePressure",
            "2646" to "leftRearTirePressure", "2660" to "rightRearTirePressure",
            "2641" to "leftFrontTirePressureState", "2648" to "rightFrontTirePressureState",
            "2655" to "leftRearTirePressureState", "2662" to "rightRearTirePressureState",
            "1182" to "minBatteryTemp", "1945" to "windshieldDefrost", "2681" to "rapidHeating", "49" to "leftMirrorHeating", "1258" to "bcmKeyPositionOn3",
            "2100" to "driverSeatHeating", "2118" to "passengerSeatHeating", "1816" to "steeringWheelHeating",
            "3636" to "sentryMode",
        )

        fun parse(data: JsonObject): CarStatus {
            val m = HashMap<String, JsonElement>(data)
            val signal = data["signal"].obj
            if (signal != null) {
                for ((id, name) in SIGNAL_TO_NAMED) if (name !in m) signal[id]?.let { m[name] = it }
                // Signed GPS signals first, absolute-value fallbacks after.
                if ("longitude" !in m) listOf("2", "3724", "2191").firstNotNullOfOrNull { signal[it] }?.let { m["longitude"] = it }
                if ("latitude" !in m) listOf("3", "3725", "2190").firstNotNullOfOrNull { signal[it] }?.let { m["latitude"] = it }
                if ("collectTime" !in m) signal["sts"].long?.let { m["_sts"] = kotlinx.serialization.json.JsonPrimitive(if (it > 9_999_999_999) it else it * 1000) }
            }
            val plan = data["config"].obj?.get("3").obj
            val chargeLimit = m["chargesocSetting"].int ?: plan?.get("percent").int

            val voltage = m["batteryVoltage"].dbl
            val current = m["batteryCurrent"].dbl
            val powerKw = if (voltage != null && current != null) (voltage * current / 1000.0).let { if (it < 0) -it else 0.0 } else null
            val state = ChargeState.of(m["chargeState"].int)
            val speed = m["speed"].int
            val parked = speed == null || speed == 0
            val plugged = when {
                m["dcInputFastCharge"].int == 1 && parked -> true
                m["acInputSlowCharge"] != null -> m["acInputSlowCharge"].int == 1 && parked
                else -> state in setOf(ChargeState.CHARGING, ChargeState.FINISHED, ChargeState.ERROR, ChargeState.SETTING, ChargeState.PAUSED) && parked
            }

            fun open(key: String) = m[key].int?.let { it != 0 } == true
            val doors = buildList {
                if (open("lbcmDriverDoorStatus")) add("Førerdør")
                if (open("rbcmDriverDoorStatus")) add("Passasjerdør")
                if (open("lbcmLeftRearDoorStatus")) add("Bakdør venstre")
                if (open("rbcmRightRearDoorStatus")) add("Bakdør høyre")
                if (open("bbcmBackDoorStatus")) add("Bagasjerom")
            }
            fun pct(key: String) = (m[key].int ?: 0) in 1..100
            val windows = buildList {
                if (pct("leftFrontWindowPercent")) add("Venstre foran")
                if (pct("rightFrontWindowPercent")) add("Høyre foran")
                if (pct("leftRearWindowPercent")) add("Venstre bak")
                if (pct("rightRearWindowPercent")) add("Høyre bak")
            }
            val tireStates = listOf("leftFrontTirePressureState", "rightFrontTirePressureState", "leftRearTirePressureState", "rightRearTirePressureState").map { m[it].int }
            val collected = m["_sts"].long ?: m["collectTime"].str?.let {
                runCatching { java.time.LocalDateTime.parse(it.replace(' ', 'T')).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()
            }
            val lat = m["latitude"].dbl?.takeIf { it != 0.0 && it in -90.0..90.0 }
            val lon = m["longitude"].dbl?.takeIf { it != 0.0 && it in -180.0..180.0 }

            return CarStatus(
                soc = m["soc"].int,
                preciseSoc = m["preciseSoc"].dbl ?: m["soc"].dbl,
                remainingKwh = m["dumpEnergy"].dbl?.let { it / 1000.0 },
                batteryTempC = m["minBatteryTemp"].int,
                rangeKm = m["expectedMileage"].int ?: m["liveRemainingRange"].int,
                chargeState = state,
                chargeRemainMin = m["chargeRemainTime"].int,
                chargingPowerKw = powerKw,
                pluggedIn = plugged,
                chargeLimit = chargeLimit,
                odometerKm = m["totalMileage"].int,
                speedKmh = speed,
                latitude = lat,
                longitude = lon,
                locked = m["driverDoorLockStatus"].int?.let { it != 0 },
                doorsOpen = doors,
                windowsOpen = windows,
                acOn = m["acSwitch"].int?.let { it != 0 },
                acSetting = m["acSetting"].dbl,
                interiorTemp = m["interiorTemp"].dbl,
                outdoorTemp = m["outdoorTemp"].dbl,
                driverSeatHeat = m["driverSeatHeating"].int,
                passengerSeatHeat = m["passengerSeatHeating"].int,
                steeringWheelHeat = m["steeringWheelHeating"].int?.let { it != 0 },
                sentryMode = m["sentryMode"].int?.let { it != 0 },
                defrostOn = m["windshieldDefrost"].int?.let { it == 1 || it == 2 },
                rapidHeatOn = m["rapidHeating"].int?.let { it != 0 },
                mirrorHeatOn = m["leftMirrorHeating"].int?.let { it != 0 },
                driving = m["bcmKeyPositionOn3"].int == 1 && m["gearStatus"].int in setOf(1, 3),
                doorFrontLeftOpen = open("lbcmDriverDoorStatus"),
                doorFrontRightOpen = open("rbcmDriverDoorStatus"),
                doorRearLeftOpen = open("lbcmLeftRearDoorStatus"),
                doorRearRightOpen = open("rbcmRightRearDoorStatus"),
                tailgateOpen = open("bbcmBackDoorStatus"),
                windowFrontLeftPct = m["leftFrontWindowPercent"].int ?: 0,
                windowRearLeftPct = m["leftRearWindowPercent"].int ?: 0,
                tires = Tires(
                    m["leftFrontTirePressure"].int, m["rightFrontTirePressure"].int,
                    m["leftRearTirePressure"].int, m["rightRearTirePressure"].int,
                    if (tireStates.any { it == null }) null else tireStates.all { it == 0 },
                ),
                collectedAtMillis = collected,
            )
        }
    }
}
