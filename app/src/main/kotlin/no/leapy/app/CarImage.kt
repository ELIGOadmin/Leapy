package no.leapy.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import no.leapy.core.CarStatus
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * Leapmotor's own car picture: a ZIP of transparent PNG layers (body, each door open/closed,
 * tailgate, windows, charge flap and 14 charging-animation frames) in the car's colour.
 */
class CarImagePackage private constructor(private val layers: Map<String, Bitmap>) {

    val chargeFrameCount: Int = (2..15).count { layers.containsKey("carpic_charge$it.png") }

    private fun names(s: CarStatus?, chargeFrame: Int?): List<String> {
        if (s == null) return listOf(
            "carpic_rightbehind_close.png", "carpic_rightfront_close.png", "carpic_body.png", "carpic_hood_close.png",
            "carpic_leftbehind_close.png", "carpic_leftfront_close.png",
            "carpic_leftbehind_window_close.png", "carpic_leftfront_window_close.png",
        )
        return buildList {
            if (s.tailgateOpen) add("carpic_tailgate_open.png")
            add(if (s.doorRearRightOpen) "carpic_rightbehind_open.png" else "carpic_rightbehind_close.png")
            add(if (s.doorFrontRightOpen) "carpic_rightfront_open.png" else "carpic_rightfront_close.png")
            add("carpic_body.png")
            add("carpic_hood_close.png")
            add(if (s.doorRearLeftOpen) "carpic_leftbehind_open.png" else "carpic_leftbehind_close.png")
            add(if (s.doorFrontLeftOpen) "carpic_leftfront_open.png" else "carpic_leftfront_close.png")
            if (s.windowFrontLeftPct == 0) add("carpic_leftfront_window_close.png")
            if (s.windowRearLeftPct == 0) add("carpic_leftbehind_window_close.png")
            if (s.isCharging) { add("carpic_charge_open.png"); add("carpic_charge${chargeFrame ?: 2}.png") }
            else if (s.pluggedIn == true) { add("carpic_charge_open.png"); add("carpic_charge1.png") }
        }
    }

    /** Composites the layers for [status]. [chargeFrame] (2-15) picks the charging animation frame. */
    fun compose(status: CarStatus?, chargeFrame: Int? = null): Bitmap? {
        val body = layers["carpic_body.png"] ?: return null
        val out = Bitmap.createBitmap(body.width, body.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        for (n in names(status, chargeFrame)) layers[n]?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        return out
    }

    companion object {
        fun fromZip(bytes: ByteArray): CarImagePackage? {
            val map = HashMap<String, Bitmap>()
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    val name = e.name.substringAfterLast('/')
                    if (!e.isDirectory && name.endsWith(".png", ignoreCase = true)) {
                        val data = zip.readBytes()
                        BitmapFactory.decodeByteArray(data, 0, data.size)?.let { map[name] = it }
                    }
                }
            }
            return if (map.containsKey("carpic_body.png")) CarImagePackage(map) else null
        }
    }
}
