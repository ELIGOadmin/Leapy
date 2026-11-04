package no.leapy.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SectionCard(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

/** Large square-ish button used for the main actions. */
@Composable
fun ActionTile(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    busy: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    OutlinedCard(
        onClick = onClick,
        enabled = enabled && !busy,
        modifier = modifier.height(96.dp),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, if (active) accent else MaterialTheme.colorScheme.surfaceVariant),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (active) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
                else Icon(icon, contentDescription = null, tint = if (active) accent else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

/** Battery ring with percentage and range in the middle. */
@Composable
fun BatteryRing(soc: Int?, rangeKm: Int?, charging: Boolean, limit: Int?) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val color = when {
        soc == null -> track
        soc <= 15 -> Danger
        soc <= 30 -> Warn
        else -> MaterialTheme.colorScheme.primary
    }
    Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(200.dp)) {
            val stroke = 16.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 135f, 270f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            if (soc != null) drawArc(color, 135f, 270f * soc.coerceIn(0, 100) / 100f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            if (limit != null && limit in 1..100) {
                val angle = Math.toRadians((135.0 + 270.0 * limit / 100.0))
                val r = (size.width - stroke) / 2
                val c = Offset(size.width / 2, size.height / 2)
                val p1 = Offset(c.x + (r - stroke) * kotlin.math.cos(angle).toFloat(), c.y + (r - stroke) * kotlin.math.sin(angle).toFloat())
                val p2 = Offset(c.x + (r + stroke) * kotlin.math.cos(angle).toFloat(), c.y + (r + stroke) * kotlin.math.sin(angle).toFloat())
                drawLine(Color.White.copy(alpha = 0.8f), p1, p2, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(soc?.let { "$it %" } ?: "–", fontSize = 44.sp, fontWeight = FontWeight.Bold)
            Text(rangeKm?.let { "$it km" } ?: "", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (charging) Text("Lader", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun InfoRow(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium, color = valueColor)
    }
}

fun ago(millis: Long?): String {
    if (millis == null) return "ukjent"
    val min = (System.currentTimeMillis() - millis) / 60_000
    return when {
        min < 1 -> "nå nettopp"
        min < 60 -> "$min min siden"
        min < 60 * 24 -> "${min / 60} t siden"
        else -> "${min / (60 * 24)} d siden"
    }
}
