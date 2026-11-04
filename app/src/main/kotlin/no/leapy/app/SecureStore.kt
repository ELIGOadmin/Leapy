package no.leapy.app

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import no.leapy.core.Session

data class Credentials(val email: String, val password: String, val pin: String)

/** Login details and the Leapmotor session, encrypted with a key in the Android Keystore. */
class SecureStore(context: Context) {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "leapy_secure",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var credentials: Credentials?
        get() {
            val e = prefs.getString("email", null) ?: return null
            val p = prefs.getString("password", null) ?: return null
            return Credentials(e, p, prefs.getString("pin", "") ?: "")
        }
        set(value) {
            prefs.edit().apply {
                if (value == null) { remove("email"); remove("password"); remove("pin") }
                else { putString("email", value.email); putString("password", value.password); putString("pin", value.pin) }
            }.apply()
        }

    var session: Session?
        get() = prefs.getString("session", null)?.let(Session::fromJson)
        set(value) { prefs.edit().apply { if (value == null) remove("session") else putString("session", value.toJson()) }.apply() }

    var selectedVin: String?
        get() = prefs.getString("vin", null)
        set(value) { prefs.edit().putString("vin", value).apply() }

    var lastClimateTemp: Int
        get() = prefs.getInt("climate_temp", 21)
        set(value) { prefs.edit().putInt("climate_temp", value).apply() }

    var lastStatusJson: String?
        get() = prefs.getString("last_status", null)
        set(value) { prefs.edit().putString("last_status", value).apply() }

    fun clear() = prefs.edit().clear().apply()
}
