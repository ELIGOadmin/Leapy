package no.leapy.core

import kotlin.random.Random

/** Builds the signed request headers the Leapmotor gateway expects. */
class Signer(
    private val language: String = Const.LANGUAGE,
    private val nonceSource: () -> String = { (100000 + Random.nextInt(9999999 - 100000 + 1)).toString() },
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    private fun baseHeaders(nonce: String, deviceId: String, timestamp: String, sign: String, p12: Boolean = true): MutableMap<String, String> {
        val h = linkedMapOf(
            "acceptLanguage" to language,
            "channel" to Const.CHANNEL,
            "deviceType" to Const.DEVICE_TYPE,
            "source" to Const.SOURCE,
            "version" to Const.APP_VERSION,
            "nonce" to nonce,
            "deviceId" to deviceId,
            "timestamp" to timestamp,
            "sign" to sign,
        )
        if (p12) h["X-P12_ENC_ALG"] = Const.P12_ENC_ALG
        h["Content-Type"] = "application/x-www-form-urlencoded"
        return h
    }

    /** SHA-256 signed headers for the login call. */
    fun login(deviceId: String, username: String, password: String): MutableMap<String, String> {
        val nonce = nonceSource(); val ts = clock().toString()
        val input = listOf(
            language, Const.DEVICE_TYPE, deviceId, "1", username, "0", "1", nonce, password,
            Const.POLICY_ID, Const.SOURCE, ts, Const.APP_VERSION,
        ).joinToString("")
        return baseHeaders(nonce, deviceId, ts, Crypto.sha256Hex(input))
    }

    /**
     * HMAC-SHA256 signed headers. The signature input is the values of the standard header
     * fields plus [extra] (vin, body params, ...), concatenated in key order. Every
     * explicit-order variant in the Python client is this same rule.
     */
    fun signed(signKey: ByteArray, deviceId: String, extra: Map<String, String> = emptyMap(), p12: Boolean = true): MutableMap<String, String> {
        val nonce = nonceSource(); val ts = clock().toString()
        val fields = sortedMapOf(
            "acceptLanguage" to language,
            "channel" to Const.CHANNEL,
            "deviceId" to deviceId,
            "deviceType" to Const.DEVICE_TYPE,
            "nonce" to nonce,
            "source" to Const.SOURCE,
            "timestamp" to ts,
            "version" to Const.APP_VERSION,
        )
        fields.putAll(extra)
        val input = fields.values.joinToString("")
        return baseHeaders(nonce, deviceId, ts, Crypto.hmacSha256Hex(signKey, input), p12)
    }
}
