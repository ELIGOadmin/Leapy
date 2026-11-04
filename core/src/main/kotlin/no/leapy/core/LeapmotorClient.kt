package no.leapy.core

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

open class LeapmotorException(message: String, val code: Int? = null) : Exception(message)
class LeapmotorAuthException(message: String, code: Int? = null) : LeapmotorException(message, code)

/** Everything needed to resume without logging in again. Stored encrypted on the phone. */
data class Session(
    val userId: String,
    val token: String,
    val refreshToken: String,
    val deviceId: String,
    val signIkm: String,
    val signSalt: String,
    val signInfo: String,
    val accountP12Base64: String,
    val accountP12Password: String,
) {
    fun toJson(): String = buildJsonObject {
        put("userId", userId); put("token", token); put("refreshToken", refreshToken); put("deviceId", deviceId)
        put("signIkm", signIkm); put("signSalt", signSalt); put("signInfo", signInfo)
        put("accountP12Base64", accountP12Base64); put("accountP12Password", accountP12Password)
    }.toString()

    companion object {
        fun fromJson(s: String): Session? = runCatching {
            val o = parseJson(s).obj!!
            fun f(k: String) = o[k].str ?: error("missing $k")
            Session(f("userId"), f("token"), f("refreshToken"), f("deviceId"), f("signIkm"), f("signSalt"), f("signInfo"), f("accountP12Base64"), f("accountP12Password"))
        }.getOrNull()
    }
}

/**
 * Leapmotor cloud client (EU gateway). Blocking calls; run them off the main thread.
 *
 * @param onSessionChanged called whenever login or token refresh produces a new session.
 */
class LeapmotorClient(
    private val email: String,
    private val password: String,
    private val pin: String?,
    session: Session? = null,
    private val baseUrl: String = Const.BASE_URL,
    private val signer: Signer = Signer(),
    private val onSessionChanged: (Session) -> Unit = {},
) {
    var session: Session? = session
        private set

    private var accountHttp: OkHttpClient? = null
    private var remoteCertSynced = false
    private var vehicles: List<Vehicle>? = null

    private val appHttp: OkHttpClient by lazy {
        val ks = loadP12(LeapmotorClient::class.java.getResourceAsStream(Const.APP_P12_RESOURCE)!!.readBytes(), Const.APP_P12_PASSWORD)
        http(ks, Const.APP_P12_PASSWORD)
    }

    // ------------------------------------------------------------------ auth

    @Synchronized
    fun login(): Session {
        val deviceId = session?.deviceId ?: UUID.randomUUID().toString().replace("-", "")
        val headers = signer.login(deviceId, email, password)
        val body = "isRecoverAcct=0&password=${Crypto.quote(password)}&policyId=${Const.POLICY_ID}&loginMethod=1&email=${Crypto.quote(email)}"
        val data = parse(post(appHttp, "/carownerservice/oversea/acct/v1/login", headers, body), "Innlogging", auth = true)["data"].obj
            ?: throw LeapmotorAuthException("Innlogging ga ikke data")

        val id = data["id"].str ?: throw LeapmotorAuthException("Innlogging ga ingen bruker-id")
        val token = data["token"].str ?: throw LeapmotorAuthException("Innlogging ga ingen token")
        val p12b64 = data["base64Cert"].str ?: throw LeapmotorAuthException("Innlogging ga ikke kontosertifikat")
        val p12pwd = Crypto.deriveAccountP12Password(id, data["uid"].str ?: "")
        try {
            loadP12(Base64.getDecoder().decode(p12b64), p12pwd)
        } catch (e: Exception) {
            throw LeapmotorAuthException("Kunne ikke åpne kontosertifikatet: ${e.message}")
        }

        val s = Session(
            userId = id, token = token, refreshToken = data["refreshToken"].str ?: "",
            deviceId = Crypto.deriveSessionDeviceId(token, deviceId),
            signIkm = data["signIkm"].str ?: "", signSalt = data["signSalt"].str ?: "", signInfo = data["signInfo"].str ?: "",
            accountP12Base64 = p12b64, accountP12Password = p12pwd,
        )
        setSession(s)
        return s
    }

    private fun setSession(s: Session) {
        val certChanged = session?.accountP12Base64 != s.accountP12Base64
        session = s
        if (certChanged) { accountHttp = null; remoteCertSynced = false }
        onSessionChanged(s)
    }

    private fun requireSession(): Session = session ?: login()

    private fun signKey(s: Session) = Crypto.deriveSignKey(s.signIkm, s.signSalt, s.signInfo)

    private fun accountClient(s: Session): OkHttpClient =
        accountHttp ?: http(loadP12(Base64.getDecoder().decode(s.accountP12Base64), s.accountP12Password), s.accountP12Password)
            .also { accountHttp = it }

    private fun refreshToken() {
        val s = session ?: throw LeapmotorAuthException("Ikke innlogget")
        if (s.refreshToken.isEmpty()) throw LeapmotorAuthException("Ingen refresh-token")
        val h = signer.signed(signKey(s), s.deviceId, mapOf("refreshToken" to s.refreshToken)).withAuth(s)
        val d = parse(post(accountClient(s), "/carownerservice/oversea/acct/v1/token/refresh", h, "refreshToken=${Crypto.quote(s.refreshToken)}"), "Token-fornying")["data"].obj
            ?: throw LeapmotorAuthException("Token-fornying ga ikke data")
        setSession(s.copy(token = d["token"].str ?: s.token, refreshToken = d["refreshToken"].str ?: ""))
    }

    /** Runs [block]; on a token error it refreshes (or logs in again) and retries once. */
    private fun <T> withAuthRetry(block: (Session) -> T): T {
        val s = requireSession()
        return try {
            block(s)
        } catch (e: LeapmotorException) {
            if (e is LeapmotorAuthException || !(e.message ?: "").contains("token", ignoreCase = true)) throw e
            try { refreshToken() } catch (_: LeapmotorException) { session = null; login() }
            block(session!!)
        }
    }

    // ------------------------------------------------------------------ reads

    @Synchronized
    fun vehicles(forceRefresh: Boolean = false): List<Vehicle> {
        if (!forceRefresh) vehicles?.let { return it }
        return withAuthRetry { s ->
            val h = signer.signed(signKey(s), s.deviceId).withAuth(s)
            val d = parse(post(accountClient(s), "/carownerservice/oversea/vehicle/v1/list", h, ""), "Billiste")["data"].obj
            buildList {
                d?.get("bindcars").arr?.forEach { it.obj?.let { o -> add(Vehicle.from(o, false)) } }
                d?.get("sharedcars").arr?.forEach { it.obj?.let { o -> add(Vehicle.from(o, true)) } }
            }.filter { it.vin.isNotEmpty() }
        }.also { vehicles = it }
    }

    @Synchronized
    fun rawStatus(vehicle: Vehicle): JsonObject = withAuthRetry { s ->
        fun call(path: String): HttpResult {
            val h = signer.signed(signKey(s), s.deviceId, mapOf("vin" to vehicle.vin)).withAuth(s)
            return post(accountClient(s), "/carownerservice/oversea/vehicle/v1/status/get/$path", h, "vin=${Crypto.quote(vehicle.vin)}")
        }
        var r = call(vehicle.statusPath)
        if (r.code == 404 && vehicle.statusPath != "c10") r = call("c10")
        parse(r, "Bilstatus")["data"].obj ?: JsonObject(emptyMap())
    }

    fun status(vehicle: Vehicle): CarStatus = CarStatus.parse(rawStatus(vehicle))

    /** Key of the car picture package (a ZIP of PNG layers in the car's own colour), or null. */
    @Synchronized
    fun carPictureKey(vehicle: Vehicle): String? = withAuthRetry { s ->
        val h = signer.signed(signKey(s), s.deviceId, mapOf("vin" to vehicle.vin, "deviceID" to s.deviceId)).withAuth(s)
        val body = "deviceID=${Crypto.quote(s.deviceId)}&vin=${Crypto.quote(vehicle.vin)}"
        parse(post(accountClient(s), "/carownerservice/oversea/vehicle/v1/carpicture/key", h, body), "Bilbilde")["data"].obj?.get("key").str
    }

    @Synchronized
    fun carPicturePackage(key: String): ByteArray = withAuthRetry { s ->
        val h = signer.picturePackage(signKey(s), s.deviceId, key).withAuth(s)
        val req = Request.Builder().url(baseUrl.trimEnd('/') + "/carownerservice/oversea/vehicle/v1/carpicture/package")
            .apply { h.forEach { (k, v) -> header(k, v) } }
            .post("key=${Crypto.quote(key)}".toByteArray(Charsets.UTF_8).toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .build()
        try {
            accountClient(s).newCall(req).execute().use { r ->
                if (r.code != 200) throw LeapmotorException("Bilbilde feilet: HTTP ${r.code}")
                r.body?.bytes() ?: throw LeapmotorException("Bilbilde var tomt")
            }
        } catch (e: java.io.IOException) {
            throw LeapmotorException("Fikk ikke hentet bilbilde: ${e.message}")
        }
    }

    @Synchronized
    fun shareInvitations(): List<ShareInvitation> = withAuthRetry { s ->
        val h = signer.signed(signKey(s), s.deviceId).withAuth(s)
        parse(post(accountClient(s), "/carownerservice/oversea/sharecar/getsharemsg", h, ""), "Delingsinvitasjoner")["data"]
            .arr?.mapNotNull { it.obj?.let(ShareInvitation::from) } ?: emptyList()
    }

    @Synchronized
    fun acceptShare(inv: ShareInvitation) {
        withAuthRetry { s ->
            val params = linkedMapOf("shareUserId" to inv.shareUserId, "userId" to s.userId, "carCode" to inv.vin, "state" to "yes")
            val h = signer.signed(signKey(s), s.deviceId, params).withAuth(s)
            val body = params.entries.joinToString("&") { "${Crypto.quote(it.key)}=${Crypto.quote(it.value)}" }
            parse(post(accountClient(s), "/carownerservice/oversea/sharecar/setokmsg", h, body), "Godta deling")
        }
        vehicles = null
    }

    /** Charge schedule (cmdId 190) as stored in the car, or null. */
    @Synchronized
    fun chargeSchedule(vin: String): JsonObject? = withAuthRetry { s ->
        val h = signer.signed(signKey(s), s.deviceId, mapOf("vin" to vin, "cmdId" to "190")).withAuth(s)
        val r = post(accountClient(s), "/carownerservice/oversea/vehicle/v1/app/remote/ctl/getAppointment", h, "vin=${Crypto.quote(vin)}&cmdId=190")
        val o = runCatching { parseJson(r.body).obj }.getOrNull() ?: throw LeapmotorException("Ugyldig svar fra ladeplan")
        val code = (o["result"] ?: o["code"]).int
        if (r.code != 200 || code != 0) {
            val msg = o["message"].str ?: ""
            if (msg.contains("permission", true)) return@withAuthRetry null
            throw LeapmotorException("Ladeplan feilet: $msg")
        }
        val raw = o["data"] ?: return@withAuthRetry null
        (raw.str?.let { runCatching { parseJson(it).obj }.getOrNull() }) ?: raw.obj
    }

    /** Sets charge limit, keeping the rest of the stored charge plan. */
    fun setChargeLimit(vin: String, percent: Int) {
        val p = chargeSchedule(vin)
        send(vin, Command.chargePlan(
            "Ladegrense $percent %",
            enabled = (p?.get("chargeEnable").int ?: 0) == 1,
            socLimit = percent,
            cycles = p?.get("cycles").str?.takeIf { it.isNotEmpty() } ?: "1,2,3,4,5,6,7",
            start = p?.get("starttime").str?.takeIf { it.isNotEmpty() } ?: "00:00",
            end = p?.get("endtime").str?.takeIf { it.isNotEmpty() } ?: "08:00",
            circulation = p?.get("circulation").int ?: 0,
            recharge = p?.get("recharge").int ?: 0,
        ))
    }

    // ------------------------------------------------------------------ commands

    /** Sends a command and waits until the car confirms it (or throws). */
    @Synchronized
    fun send(vin: String, cmd: Command) {
        withAuthRetry { s ->
            val key = signKey(s)
            if (!cmd.requiresPin) {
                val h = signer.signed(key, s.deviceId, mapOf("vin" to vin, "cmdId" to cmd.cmdId, "cmdContent" to cmd.content)).withAuth(s)
                val body = "cmdContent=${Crypto.quote(cmd.content)}&vin=${Crypto.quote(vin)}&cmdId=${Crypto.quote(cmd.cmdId)}"
                parse(post(accountClient(s), "/carownerservice/oversea/vehicle/v1/app/remote/ctl", h, body), cmd.label)
                return@withAuthRetry
            }
            val pin = pin?.takeIf { it.isNotBlank() } ?: throw LeapmotorAuthException("PIN-kode mangler. Legg den inn under Innstillinger.")
            val op = Crypto.encryptOperatePassword(pin, s.token)

            if (!remoteCertSynced) {
                val h = signer.signed(key, s.deviceId).withAuth(s)
                parse(post(appHttp, "/carownerservice/oversea/vehicle/v1/cert/sync", h, ""), "Sertifikat-synk")
                remoteCertSynced = true
            }

            val vh = signer.signed(key, s.deviceId, mapOf("vin" to vin, "operatePassword" to op)).withAuth(s)
            parse(post(accountClient(s), "/carownerservice/oversea/vehicle/v1/operPwd/verify", vh,
                "operatePassword=${Crypto.quote(op)}&vin=${Crypto.quote(vin)}"), "PIN-sjekk", auth = true)

            val h = signer.signed(key, s.deviceId, mapOf("vin" to vin, "cmdId" to cmd.cmdId, "cmdContent" to cmd.content, "operatePassword" to op)).withAuth(s)
            val body = "cmdContent=${Crypto.quote(cmd.content)}&vin=${Crypto.quote(vin)}&cmdId=${Crypto.quote(cmd.cmdId)}&operatePassword=${Crypto.quote(op)}"
            val res = parse(post(accountClient(s), "/carownerservice/oversea/vehicle/v1/app/remote/ctl", h, body), cmd.label)

            val d = res["data"].obj ?: return@withAuthRetry
            val ctlId = d["remoteCtlId"].str ?: return@withAuthRetry
            val timeout = (d["queryRemoteCtlResultTimeout"].long ?: 30000L).coerceAtLeast(1000)
            val interval = (d["queryInterval"].long ?: 2000L).coerceAtLeast(250)
            val deadline = System.currentTimeMillis() + timeout
            while (true) {
                val ph = signer.signed(key, s.deviceId, mapOf("remoteCtlId" to ctlId)).withAuth(s)
                val r = parse(post(accountClient(s), "/carownerservice/oversea/vehicle/v1/app/remote/ctl/result/query", ph, "remoteCtlId=${Crypto.quote(ctlId)}"), cmd.label)
                if (r["data"].int == 1) return@withAuthRetry
                if (System.currentTimeMillis() + interval >= deadline) throw LeapmotorException("Bilen svarte ikke i tide på «${cmd.label}». Den kan være uten dekning.")
                Thread.sleep(interval)
            }
        }
    }

    // ------------------------------------------------------------------ http

    data class HttpResult(val code: Int, val body: String)

    private fun MutableMap<String, String>.withAuth(s: Session) = apply { put("userId", s.userId); put("token", s.token) }

    private fun post(client: OkHttpClient, path: String, headers: Map<String, String>, body: String): HttpResult {
        val req = Request.Builder().url(baseUrl.trimEnd('/') + path)
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .post(body.toByteArray(Charsets.UTF_8).toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .build()
        return try {
            client.newCall(req).execute().use { HttpResult(it.code, it.body?.string() ?: "") }
        } catch (e: java.io.IOException) {
            throw LeapmotorException("Fikk ikke kontakt med Leapmotor: ${e.message}")
        }
    }

    private fun parse(r: HttpResult, label: String, auth: Boolean = false): JsonObject {
        val o = runCatching { parseJson(r.body).obj }.getOrNull()
            ?: throw LeapmotorException("$label: ugyldig svar (HTTP ${r.code})")
        val code = o["code"].int
        if (r.code != 200 || code != 0) {
            val msg = o["message"].str ?: "HTTP ${r.code}"
            if (auth) throw LeapmotorAuthException("$label feilet: $msg", code)
            throw LeapmotorException("$label feilet: $msg", code)
        }
        return o
    }

    companion object {
        private val bc by lazy { BouncyCastleProvider() }

        fun loadP12(bytes: ByteArray, password: String): KeyStore =
            KeyStore.getInstance("PKCS12", bc).apply { load(ByteArrayInputStream(bytes), password.toCharArray()) }

        /**
         * Server verification pinned to Leapmotor's own "AppSubCA" (their gateway is not signed by a
         * public CA). Only chains issued by that CA are accepted, and hostnames are checked as usual.
         */
        private val serverTrust: X509TrustManager by lazy {
            val ca = CertificateFactory.getInstance("X.509")
                .generateCertificate(LeapmotorClient::class.java.getResourceAsStream(Const.SERVER_CA_RESOURCE)!!)
            val ks = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null); setCertificateEntry("leapmotor-appsubca", ca) }
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(ks) }
                .trustManagers.filterIsInstance<X509TrustManager>().first()
        }

        fun http(ks: KeyStore, password: String): OkHttpClient {
            // Re-pack into a default-provider keystore so the platform KeyManager can read it.
            val plain = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null) }
            for (alias in ks.aliases()) {
                if (ks.isKeyEntry(alias)) plain.setKeyEntry(alias, ks.getKey(alias, password.toCharArray()), password.toCharArray(), ks.getCertificateChain(alias))
            }
            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(plain, password.toCharArray()) }
            val ctx = SSLContext.getInstance("TLS").apply { init(kmf.keyManagers, arrayOf(serverTrust), null) }
            return OkHttpClient.Builder()
                .sslSocketFactory(ctx.socketFactory, serverTrust)
                .connectTimeout(30, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
                .build()
        }
    }
}
