package no.leapy.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Scrollable detail page with a back arrow and title. */
@Composable
fun DetailPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Tilbake", tint = C.On)
            }
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = C.On)
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            content = content,
        )
    }
}

/**
 * Round quick action (Tesla style). Shows a spinner while the car is working on it.
 * [caption] is a short state text under the icon; [onLongClick] starts editing the row.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuickAction(
    icon: ImageVector, description: String, active: Boolean = false, busy: Boolean = false,
    caption: String? = null, enabled: Boolean = true, onLongClick: (() -> Unit)? = null, onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val tint by animateColorAsState(if (active) C.Accent else C.On, label = "tint")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).combinedClickable(
                enabled = enabled && !busy,
                onLongClick = onLongClick?.let { l -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); l() } },
                onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
            ),
            contentAlignment = Alignment.Center,
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = C.Sub)
            else Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(27.dp))
        }
        Text(caption ?: "", fontSize = 11.sp, color = C.Sub, maxLines = 1, modifier = Modifier.height(15.dp))
    }
}

/** List row: icon, title, live value, chevron. */
@Composable
fun NavRow(icon: ImageVector, title: String, value: String?, onClick: () -> Unit) {
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.Line))
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = C.On, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = C.On)
                if (!value.isNullOrBlank()) Text(value, fontSize = 13.sp, color = C.Sub, maxLines = 1)
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = C.Faint)
        }
    }
}

/** Row with a label and a switch. [on] = null means the car does not report the state. */
@Composable
fun SwitchRow(label: String, on: Boolean?, busy: Boolean = false, onChange: (Boolean) -> Unit) {
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.Line))
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 16.sp, color = C.On, modifier = Modifier.weight(1f))
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = C.Sub)
            else Switch(
                checked = on == true, onCheckedChange = onChange,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = C.Accent, checkedThumbColor = Color.White,
                    uncheckedTrackColor = C.CardHigh, uncheckedThumbColor = Color(0xFF9AA1AD), uncheckedBorderColor = Color.Transparent,
                ),
            )
        }
    }
}

/** Row with a label and a small action button, for commands whose state the car does not report. */
@Composable
fun ActionRow(label: String, action: String, busy: Boolean = false, onClick: () -> Unit) {
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.Line))
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 16.sp, color = C.On, modifier = Modifier.weight(1f))
            Box(
                Modifier.clip(RoundedCornerShape(10.dp)).background(C.CardHigh).clickable(enabled = !busy, onClick = onClick)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = C.Sub)
                else Text(action, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = C.On)
            }
        }
    }
}

/** Card with values side by side, separated by thin lines. */
@Composable
fun StatStrip(vararg stats: Pair<String, String>) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Card)) {
        stats.forEachIndexed { i, (value, label) ->
            if (i > 0) Box(Modifier.width(1.dp).height(64.dp).padding(vertical = 0.dp).background(C.Line))
            Column(Modifier.weight(1f).padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(value, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = C.On, maxLines = 1)
                Text(label, fontSize = 12.sp, color = C.Sub)
            }
        }
    }
}

/** Segmented choice in a pill. */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T?, enabled: Boolean = true, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Card).padding(4.dp)) {
        options.forEach { (value, label) ->
            val sel = value == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (sel) C.CardHigh else Color.Transparent)
                    .clickable(enabled = enabled && !sel) { onSelect(value) }.padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, fontSize = 15.sp, fontWeight = if (sel) FontWeight.Medium else FontWeight.Normal, color = if (sel) C.On else C.Sub)
            }
        }
    }
}

/** Full-width button. Primary is light on dark, secondary is a dark card. */
@Composable
fun BigButton(text: String, primary: Boolean = true, busy: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp)).background(if (primary) C.On else C.Card)
            .clickable(enabled = !busy, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = if (primary) C.Bg else C.Sub)
        else Text(text, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = if (primary) C.Bg else C.On)
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text, fontSize = 13.sp, color = C.Sub, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
}

@Composable
fun RowScope.Spacer1() = Spacer(Modifier.weight(1f))

fun fmt1(d: Double) = if (d % 1.0 == 0.0) d.toInt().toString() else "%.1f".format(d)

fun ago(millis: Long?): String {
    if (millis == null) return ""
    val min = (System.currentTimeMillis() - millis) / 60_000
    return when {
        min < 1 -> "nå"
        min < 60 -> "$min min siden"
        min < 60 * 24 -> "${min / 60} t siden"
        else -> "${min / (60 * 24)} d siden"
    }
}

fun hm(minutes: Int) = if (minutes >= 60) "${minutes / 60} t ${minutes % 60} m" else "$minutes min"

/** Square-ish control tile: icon on top, label and state below. */
@Composable
fun Tile(icon: ImageVector, label: String, value: String?, active: Boolean = false, busy: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(C.Card).clickable(enabled = !busy, onClick = onClick).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.CenterStart) {
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = C.Sub)
            else Icon(icon, contentDescription = null, tint = if (active) C.Accent else C.On, modifier = Modifier.size(28.dp))
        }
        Column {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = C.On, maxLines = 1)
            Text(value ?: " ", fontSize = 13.sp, color = if (active) C.Accent else C.Sub, maxLines = 1)
        }
    }
}

/** Two tiles side by side. */
@Composable
fun TileRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

private val stampFormat = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss", java.util.Locale("nb", "NO"))

/** Full date and time with seconds, e.g. 04.11.2026 21:03:51. */
fun stamp(millis: Long?): String = millis?.let { synchronized(stampFormat) { stampFormat.format(java.util.Date(it)) } } ?: "–"
