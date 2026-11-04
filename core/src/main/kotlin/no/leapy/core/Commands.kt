package no.leapy.core

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A remote-control command: Leapmotor cmdId plus its JSON cmdContent. */
data class Command(val label: String, val cmdId: String, val content: String, val requiresPin: Boolean = true) {
    companion object {
        private fun value(v: String) = buildJsonObject { put("value", v) }.toString()

        val LOCK = Command("Lås", "110", value("lock"))
        val UNLOCK = Command("Lås opp", "110", value("unlock"))
        val TRUNK_OPEN = Command("Åpne bakluke", "130", value("true"))
        val TRUNK_CLOSE = Command("Lukk bakluke", "130", value("false"))
        val FIND_CAR = Command("Finn bilen", "120", value("true"))
        val WINDOWS_OPEN = Command("Åpne vinduer", "230", value("100"))
        val WINDOWS_CLOSE = Command("Lukk vinduer", "230", value("0"))
        val SUNSHADE_OPEN = Command("Åpne solskjerm", "240", value("10"))
        val SUNSHADE_CLOSE = Command("Lukk solskjerm", "240", value("0"))
        val BATTERY_PREHEAT_ON = Command("Forvarm batteri", "160", value("ptcon"))
        val BATTERY_PREHEAT_OFF = Command("Stopp batteriforvarming", "160", value("ptcoff"))
        val CHARGE_START = Command("Start lading", "193", value("start"))
        val CHARGE_STOP = Command("Stopp lading", "193", value("stop"))
        val UNLOCK_CHARGER = Command("Frigjør ladekabel", "192", buildJsonObject { put("operation", "unlock") }.toString())
        val STEERING_HEAT_ON = Command("Rattvarme på", "320", value("on"))
        val STEERING_HEAT_OFF = Command("Rattvarme av", "320", value("off"))
        val MIRROR_HEAT_ON = Command("Speilvarme på", "440", value("on"))
        val MIRROR_HEAT_OFF = Command("Speilvarme av", "440", value("off"))
        val SENTRY_ON = Command("Vaktmodus på", "220", value("1"))
        val SENTRY_OFF = Command("Vaktmodus av", "220", value("0"))
        val AC_OFF = Command("Klima av", "170", buildJsonObject { put("operate", "off") }.toString())

        enum class ClimateMode(val api: String) { HEAT("hot"), COOL("cold"), FAN("wind") }

        fun climate(label: String, mode: ClimateMode, temperature: Int, fan: Int = 3, defrost: Boolean = false, recirculate: Boolean = false): Command {
            require(fan in 1..7) { "fan must be 1-7" }
            val c = buildJsonObject {
                put("circle", if (recirculate) "in" else "out")
                put("mode", mode.api)
                put("operate", "manual")
                put("position", "all")
                put("temperature", temperature.coerceIn(18, 32).toString())
                put("windlevel", fan.toString())
                put("wshld", if (defrost) "1" else "0")
            }
            return Command(label, "170", c.toString())
        }

        val QUICK_HEAT = Command("Hurtigvarme", "170", buildJsonObject {
            put("circle", "in"); put("mode", "hot"); put("operate", "manual"); put("position", "all")
            put("temperature", "32"); put("windlevel", "7"); put("wshld", "0")
        }.toString())
        val QUICK_COOL = Command("Hurtigkjøling", "170", buildJsonObject {
            put("circle", "in"); put("mode", "cold"); put("operate", "manual"); put("position", "all")
            put("temperature", "18"); put("windlevel", "7"); put("wshld", "0")
        }.toString())
        val DEFROST = Command("Avising frontrute", "170", buildJsonObject {
            put("circle", "in"); put("mode", "hot"); put("operate", "manual"); put("position", "all")
            put("temperature", "32"); put("windlevel", "7"); put("wshld", "1")
        }.toString())

        /** Seat position 1 = driver, 2 = front passenger; level 0-3. */
        fun seatHeat(position: Int, level: Int) =
            Command("Setevarme", "301", value("$position,${level.coerceIn(0, 3)}"))

        fun seatVentilation(position: Int, level: Int) =
            Command("Seteventilasjon", "370", value("$position,${level.coerceIn(0, 3)}"))

        fun chargePlan(
            label: String, enabled: Boolean, socLimit: Int, cycles: String, start: String, end: String,
            circulation: Int = 0, recharge: Int = 0,
        ): Command {
            val c = buildJsonObject {
                put("chargeEnable", if (enabled) 1 else 0)
                put("chargesoc", socLimit)
                put("circulation", circulation)
                put("cycles", cycles)
                put("endtime", end)
                put("recharge", recharge)
                put("starttime", start)
            }
            return Command(label, "190", c.toString())
        }

        fun sendDestination(address: String, name: String, lat: Double, lon: Double): Command {
            val c = buildJsonObject {
                put("address", address); put("addressname", name)
                put("latitude", JsonPrimitive(lat.toString())); put("linenum", "0"); put("longitude", JsonPrimitive(lon.toString()))
            }
            return Command("Send destinasjon", "180", c.toString(), requiresPin = false)
        }
    }
}
