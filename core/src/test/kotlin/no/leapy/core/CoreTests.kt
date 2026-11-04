package no.leapy.core

import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Expected values are produced by the Python leapmotor-api 0.6.0 with fixed nonce and clock. */
class CryptoTest {
    private val signer = Signer(nonceSource = { "4242424" }, clock = { 1700000000123L })
    private val key = Crypto.deriveSignKey("ikm-value", "salt-value", "info-value")
    private val token = "eyJhbGciOiJIUzI1NiJ9.eyJ1c2VyX25hbWUiOiAiYSxiLGRldjEyMyxkIn0.sigxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"

    @Test fun p12Password() {
        assertEquals("ByVJ80F3WHC51IL", Crypto.deriveAccountP12Password("1234567", "abcdef0123456789xyz"))
        assertEquals("Rd4oO8XUzBUM8+X", Crypto.deriveAccountP12Password("98765", "u-77aa"))
    }

    @Test fun signKey() = assertEquals("d7b0ba2ba558fab775ac9a1b72b0abfeb2810b76519efb1b796a57aa3a851e5f", Crypto.hex(key))

    @Test fun deviceIdFromToken() = assertEquals("dev123", Crypto.deriveSessionDeviceId(token, "fb"))

    @Test fun operatePassword() {
        assertEquals("LV3joxlF7NgBhjLo4xWDmw==", Crypto.encryptOperatePassword("1234", token))
        assertEquals("Tj1VKU3DPN2l6MHy8wjrjA==", Crypto.encryptOperatePassword("1234", "short"))
    }

    @Test fun loginHeaders() {
        val h = signer.login("dev1", "a@b.no", "p@ss w")
        assertEquals("057640f311ff209fc3218a6b32c00d90532a1a40ab11a76fac0dfe53c2921431", h["sign"])
        assertEquals("1700000000123", h["timestamp"]); assertEquals("1", h["X-P12_ENC_ALG"])
    }

    @Test fun signedHeaders() {
        assertEquals("422eefa3ae3c802ce1a0ba9bea101747d4a587d08c9a719d3cead1153495c5d1", signer.signed(key, "dev1", mapOf("vin" to "LVIN123"))["sign"])
        assertEquals("db0197a9397d5f4b0b9e89014ba8cc7d14768d17046bf85fd42e9160f79f6880", signer.signed(key, "dev1", mapOf("vin" to "LVIN123", "cmdId" to "110", "cmdContent" to Command.LOCK.content, "operatePassword" to "OPX"))["sign"])
        assertEquals("72ad0c83eca2080df1d2bcf5ccbe35e03eb5a3947db0b0ac853b163f1b256f37", signer.signed(key, "dev1", mapOf("vin" to "LVIN123", "operatePassword" to "OPX"))["sign"])
        assertEquals("65c8aa29034d06bc931ac74f0d8180808fc799bc1e4ce337c85db644880f1952", signer.signed(key, "dev1", mapOf("remoteCtlId" to "RC9"))["sign"])
    }

    @Test fun carPictureHeaders() {
        assertEquals("7e351529509395deab46bc318c697c82351ef8ccb82018a48a63bd90c4de5e0d",
            signer.signed(key, "dev1", mapOf("vin" to "LVIN123", "deviceID" to "dev1"))["sign"])
        val h = signer.picturePackage(key, "dev1", "PK1")
        assertEquals("1cf30a26cd2c6010f87cba903929d4d911aaf4b20f2ecbab99d45518c26773e1", h["sign"])
        assertTrue("X-P12_ENC_ALG" !in h)
    }

    @Test fun quoteMatchesPython() = assertEquals("a%20b%2Bc%2Fd~e%2Af%40%C3%B8", Crypto.quote("a b+c/d~e*f@ø"))

    @Test fun bundledCertificatesLoad() {
        val ks = LeapmotorClient.loadP12(javaClass.getResourceAsStream(Const.APP_P12_RESOURCE)!!.readBytes(), Const.APP_P12_PASSWORD)
        assertTrue(ks.aliases().toList().isNotEmpty())
        assertNotNull(LeapmotorClient.http(ks, Const.APP_P12_PASSWORD))
    }
}

class CommandTest {
    @Test fun contentsMatchPython() {
        assertEquals("""{"value":"lock"}""", Command.LOCK.content)
        assertEquals("""{"operation":"unlock"}""", Command.UNLOCK_CHARGER.content)
        assertEquals("""{"circle":"in","mode":"hot","operate":"manual","position":"all","temperature":"32","windlevel":"7","wshld":"1"}""", Command.DEFROST.content)
        assertEquals("""{"circle":"out","mode":"wind","operate":"manual","position":"all","temperature":"26","windlevel":"3","wshld":"0"}""", Command.climate("x", Command.Companion.ClimateMode.FAN, 26).content)
        assertEquals("""{"value":"1,3"}""", Command.seatHeat(1, 3).content)
        assertEquals("""{"chargeEnable":0,"chargesoc":80,"circulation":0,"cycles":"1,2,3,4,5,6,7","endtime":"08:00","recharge":0,"starttime":"00:00"}""",
            Command.chargePlan("x", false, 80, "1,2,3,4,5,6,7", "00:00", "08:00").content)
    }
}

class StatusTest {
    @Test fun parsesC10Signals() {
        val json = parseJson("""{"signal":{"47":1,"1204":65,"1178":-20.0,"1177":400.0,"1149":1,"3260":312,"1319":0,"1318":3030,
            "1298":1,"1281":0,"3725":59.91,"3724":10.75,"2":10.75,"3":59.91,"1938":1,"2183":21.5,"1349":19,"2100":2,
            "2667":250,"2653":251,"2646":248,"2660":249,"2641":0,"2648":0,"2655":0,"2662":0,"sts":1700000000000},
            "config":{"3":{"percent":90}}}""").obj as JsonObject
        val s = CarStatus.parse(json)
        assertEquals(65, s.soc); assertEquals(312, s.rangeKm); assertEquals(ChargeState.CHARGING, s.chargeState)
        assertEquals(8.0, s.chargingPowerKw); assertEquals(true, s.pluggedIn); assertEquals(90, s.chargeLimit)
        assertEquals(true, s.locked); assertEquals(59.91, s.latitude); assertEquals(10.75, s.longitude)
        assertEquals(true, s.acOn); assertEquals(21.5, s.acSetting); assertEquals(2, s.driverSeatHeat)
        assertEquals(true, s.tires.allOk); assertEquals(1700000000000L, s.collectedAtMillis)
        assertTrue(s.doorsOpen.isEmpty())
        assertEquals(65.0, s.preciseSoc); assertEquals(false, s.tailgateOpen); assertEquals(false, s.driving)
    }
}
