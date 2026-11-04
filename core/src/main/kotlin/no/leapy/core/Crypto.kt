package no.leapy.core

import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Leapmotor protocol cryptography, ported from the community `leapmotor-api` Python client.
 * MD5, SM4 and the fixed keys are imposed by the Leapmotor protocol.
 */
object Crypto {

    // ---- SM4 (only the encrypt direction with fixed round keys is needed) ----

    private val SM4_SBOX = intArrayOf(
        0xD6, 0x90, 0xE9, 0xFE, 0xCC, 0xE1, 0x3D, 0xB7, 0x16, 0xB6, 0x14, 0xC2, 0x28, 0xFB, 0x2C, 0x05,
        0x2B, 0x67, 0x9A, 0x76, 0x2A, 0xBE, 0x04, 0xC3, 0xAA, 0x44, 0x13, 0x26, 0x49, 0x86, 0x06, 0x99,
        0x9C, 0x42, 0x50, 0xF4, 0x91, 0xEF, 0x98, 0x7A, 0x33, 0x54, 0x0B, 0x43, 0xED, 0xCF, 0xAC, 0x62,
        0xE4, 0xB3, 0x1C, 0xA9, 0xC9, 0x08, 0xE8, 0x95, 0x80, 0xDF, 0x94, 0xFA, 0x75, 0x8F, 0x3F, 0xA6,
        0x47, 0x07, 0xA7, 0xFC, 0xF3, 0x73, 0x17, 0xBA, 0x83, 0x59, 0x3C, 0x19, 0xE6, 0x85, 0x4F, 0xA8,
        0x68, 0x6B, 0x81, 0xB2, 0x71, 0x64, 0xDA, 0x8B, 0xF8, 0xEB, 0x0F, 0x4B, 0x70, 0x56, 0x9D, 0x35,
        0x1E, 0x24, 0x0E, 0x5E, 0x63, 0x58, 0xD1, 0xA2, 0x25, 0x22, 0x7C, 0x3B, 0x01, 0x21, 0x78, 0x87,
        0xD4, 0x00, 0x46, 0x57, 0x9F, 0xD3, 0x27, 0x52, 0x4C, 0x36, 0x02, 0xE7, 0xA0, 0xC4, 0xC8, 0x9E,
        0xEA, 0xBF, 0x8A, 0xD2, 0x40, 0xC7, 0x38, 0xB5, 0xA3, 0xF7, 0xF2, 0xCE, 0xF9, 0x61, 0x15, 0xA1,
        0xE0, 0xAE, 0x5D, 0xA4, 0x9B, 0x34, 0x1A, 0x55, 0xAD, 0x93, 0x32, 0x30, 0xF5, 0x8C, 0xB1, 0xE3,
        0x1D, 0xF6, 0xE2, 0x2E, 0x82, 0x66, 0xCA, 0x60, 0xC0, 0x29, 0x23, 0xAB, 0x0D, 0x53, 0x4E, 0x6F,
        0xD5, 0xDB, 0x37, 0x45, 0xDE, 0xFD, 0x8E, 0x2F, 0x03, 0xFF, 0x6A, 0x72, 0x6D, 0x6C, 0x5B, 0x51,
        0x8D, 0x1B, 0xAF, 0x92, 0xBB, 0xDD, 0xBC, 0x7F, 0x11, 0xD9, 0x5C, 0x41, 0x1F, 0x10, 0x5A, 0xD8,
        0x0A, 0xC1, 0x31, 0x88, 0xA5, 0xCD, 0x7B, 0xBD, 0x2D, 0x74, 0xD0, 0x12, 0xB8, 0xE5, 0xB4, 0xB0,
        0x89, 0x69, 0x97, 0x4A, 0x0C, 0x96, 0x77, 0x7E, 0x65, 0xB9, 0xF1, 0x09, 0xC5, 0x6E, 0xC6, 0x84,
        0x18, 0xF0, 0x7D, 0xEC, 0x3A, 0xDC, 0x4D, 0x20, 0x79, 0xEE, 0x5F, 0x3E, 0xD7, 0xCB, 0x39, 0x48,
    )

    private val P12_SM4_ROUND_KEYS = longArrayOf(
        0x818FA553, 0xEBA3318D, 0x5FC3C93A, 0xBD1DADD9,
        0xBB61CAB9, 0x000FD7EA, 0xDC6E0166, 0xDA937279,
        0x607EE786, 0xB548754C, 0x107330E4, 0xEA17C186,
        0x0F56F74B, 0xB21E443C, 0xE1210FE2, 0x009995C8,
        0xE7529A48, 0x6EF474F6, 0x2AB06DF6, 0x43B11BE8,
        0x359D4A14, 0xC29E2CDE, 0x30CF6A3E, 0x79D1C806,
        0x7C502387, 0xAAAB9BC6, 0xF0FE744B, 0x1CAFC872,
        0x95A9D075, 0x88070D58, 0x22800475, 0x8391938B,
    ).map { it.toInt() }.toIntArray()

    private fun sm4EncryptBlock(block: ByteArray, off: Int): ByteArray {
        fun word(i: Int): Int =
            ((block[off + i].toInt() and 0xFF) shl 24) or
                ((block[off + i + 1].toInt() and 0xFF) shl 16) or
                ((block[off + i + 2].toInt() and 0xFF) shl 8) or
                (block[off + i + 3].toInt() and 0xFF)

        var x0 = word(0); var x1 = word(4); var x2 = word(8); var x3 = word(12)
        for (rk in P12_SM4_ROUND_KEYS) {
            val t = x1 xor x2 xor x3 xor rk
            val b = (SM4_SBOX[(t ushr 24) and 0xFF] shl 24) or
                (SM4_SBOX[(t ushr 16) and 0xFF] shl 16) or
                (SM4_SBOX[(t ushr 8) and 0xFF] shl 8) or
                SM4_SBOX[t and 0xFF]
            val nx = x0 xor b xor b.rotateLeft(2) xor b.rotateLeft(10) xor b.rotateLeft(18) xor b.rotateLeft(24)
            x0 = x1; x1 = x2; x2 = x3; x3 = nx
        }
        val out = ByteArray(16)
        for ((i, v) in intArrayOf(x3, x2, x1, x0).withIndex()) {
            out[i * 4] = (v ushr 24).toByte(); out[i * 4 + 1] = (v ushr 16).toByte()
            out[i * 4 + 2] = (v ushr 8).toByte(); out[i * 4 + 3] = v.toByte()
        }
        return out
    }

    internal fun p12MemoryEncode(data: ByteArray): ByteArray {
        val pad = 16 - (data.size % 16)
        val padded = data + ByteArray(pad) { pad.toByte() }
        val out = java.io.ByteArrayOutputStream()
        for (o in padded.indices step 16) out.write(sm4EncryptBlock(padded, o))
        return out.toByteArray()
    }

    // ---- Hashes ----

    fun md5Hex(s: String): String = hex(MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.UTF_8)))
    fun sha256Hex(s: String): String = hex(MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8)))
    fun hmacSha256Hex(key: ByteArray, s: String): String {
        val mac = Mac.getInstance("HmacSHA256"); mac.init(SecretKeySpec(key, "HmacSHA256"))
        return hex(mac.doFinal(s.toByteArray(Charsets.UTF_8)))
    }

    fun hex(b: ByteArray): String = b.joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    // ---- Account PKCS#12 password ----

    fun deriveAccountP12Password(accountId: String, uid: String): String {
        val cn = md5Hex(accountId)
        val input = cn + cn.filterIndexed { i, _ -> i % 2 == 0 } + uid.filterIndexed { i, _ -> i % 2 == 1 }
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.US_ASCII))
        val encoded = p12MemoryEncode(digest)
        return Base64.getEncoder().encodeToString(encoded.copyOfRange(0, 12)).take(15)
    }

    // ---- Vehicle PIN (operatePassword) ----

    fun deriveOperPwdKeyIv(token: String?): Pair<String, String> {
        if (token == null || token.length < 64) return Const.OPERPWD_AES_KEY to Const.OPERPWD_AES_IV
        val key = md5Hex(token.substring(0, 32)).substring(8, 24)
        val iv = md5Hex(token.substring(32, 64)).substring(8, 24)
        return key to iv
    }

    fun encryptOperatePassword(pin: String, token: String?): String {
        val (k, iv) = deriveOperPwdKeyIv(token)
        val c = Cipher.getInstance("AES/CBC/PKCS5Padding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(k.toByteArray(Charsets.UTF_8), "AES"), IvParameterSpec(iv.toByteArray(Charsets.UTF_8)))
        return Base64.getEncoder().encodeToString(c.doFinal(pin.toByteArray(Charsets.UTF_8)))
    }

    // ---- HKDF-SHA256 signing key ----

    fun deriveSignKey(ikm: String, salt: String, info: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt.toByteArray(Charsets.UTF_8).let { if (it.isEmpty()) ByteArray(32) else it }, "HmacSHA256"))
        val prk = mac.doFinal(ikm.toByteArray(Charsets.UTF_8))
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        // 32 bytes = exactly one HKDF block
        mac.update(info.toByteArray(Charsets.UTF_8)); mac.update(1)
        return mac.doFinal()
    }

    // ---- Session device id from JWT ----

    fun deriveSessionDeviceId(token: String?, fallback: String): String {
        if (token == null) return fallback
        return try {
            val payload = String(Base64.getUrlDecoder().decode(token.split(".")[1].let { it + "=".repeat((4 - it.length % 4) % 4) }))
            val userName = parseJson(payload).obj?.get("user_name").str ?: ""
            val parts = userName.split(",")
            if (parts.size >= 4 && parts[2].isNotEmpty()) parts[2] else fallback
        } catch (_: Exception) {
            fallback
        }
    }

    /** Percent-encoding identical to Python's `urllib.parse.quote(s, safe='')`. */
    fun quote(s: String): String {
        val sb = StringBuilder()
        for (b in s.toByteArray(Charsets.UTF_8)) {
            val c = b.toInt() and 0xFF
            val ch = c.toChar()
            if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch == '_' || ch == '.' || ch == '-' || ch == '~') sb.append(ch)
            else sb.append('%').append("%02X".format(c))
        }
        return sb.toString()
    }
}
