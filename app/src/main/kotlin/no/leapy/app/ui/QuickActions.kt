package no.leapy.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** Everything that can sit in the quick-action row on the home screen. */
enum class QuickItem(val title: String, val short: String = title) {
    LOCK("Lås"), CLIMATE("Klima"), TAILGATE("Bakluke"), CHARGE_PORT("Ladekabel"), FIND("Finn bilen", "Finn"),
    SENTRY("Vaktmodus"), PREHEAT("Forvarm batteri", "Forvarm"), CHARGE("Lading"), WINDOWS("Vinduer"), SUNSHADE("Solskjerm");

    companion object {
        const val SLOTS = 5
        val DEFAULT = listOf(LOCK, CLIMATE, TAILGATE, CHARGE_PORT, FIND)

        fun from(ids: List<String>): List<QuickItem> {
            val l = ids.mapNotNull { id -> entries.firstOrNull { it.name == id } }.distinct()
            return if (l.size == SLOTS) l else DEFAULT
        }
    }
}

/** How one quick item looks and what it does right now. */
class QuickSpec(
    val icon: ImageVector, val description: String, val active: Boolean = false, val busy: Boolean = false,
    val caption: String? = null, val onClick: () -> Unit,
)

/**
 * The row of five quick actions. Long-press any of them to start editing: the icons wiggle,
 * more actions appear below, and icons can be dragged to swap places or into the row.
 */
@Composable
fun QuickBar(
    items: List<QuickItem>,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    onChange: (List<QuickItem>) -> Unit,
    spec: @Composable (QuickItem) -> QuickSpec,
) {
    val current by rememberUpdatedState(items)
    val change by rememberUpdatedState(onChange)
    val haptic = LocalHapticFeedback.current
    val slots = remember { mutableStateMapOf<Int, Rect>() }
    var dragItem by remember { mutableStateOf<QuickItem?>(null) }
    var dragFrom by remember { mutableIntStateOf(-1) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }

    val wiggle = rememberInfiniteTransition(label = "wiggle")
    val angle by wiggle.animateFloat(-2.5f, 2.5f, infiniteRepeatable(tween(120, easing = LinearEasing), RepeatMode.Reverse), label = "angle")

    fun target(): Int? = slots.entries.firstOrNull { it.value.inflate(12f).contains(dragPos) }?.key

    fun drop() {
        val item = dragItem
        val to = target()
        if (item != null && to != null && to != dragFrom) {
            val l = current.toMutableList()
            if (dragFrom >= 0) { l[dragFrom] = l[to]; l[to] = item } else l[to] = item
            change(l)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        dragItem = null; dragFrom = -1
    }

    fun Modifier.dragSource(item: QuickItem, from: Int, bounds: () -> Rect?): Modifier =
        if (!editing) this else pointerInput(item, from) {
            detectDragGestures(
                onDragStart = { off ->
                    val b = bounds() ?: return@detectDragGestures
                    dragItem = item; dragFrom = from; dragPos = b.topLeft + off
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                },
                onDrag = { ch, d -> ch.consume(); dragPos += d },
                onDragEnd = { drop() },
                onDragCancel = { dragItem = null; dragFrom = -1 },
            )
        }

    Box(Modifier.fillMaxWidth().onGloballyPositioned { origin = it.positionInRoot() }) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                items.forEachIndexed { i, item ->
                    key(i) {
                        val sp = spec(item)
                        val hovered = dragItem != null && target() == i && dragFrom != i
                        Box(
                            Modifier
                                .onGloballyPositioned { slots[i] = it.boundsInRoot() }
                                .graphicsLayer {
                                    rotationZ = if (editing && dragFrom != i) (if (i % 2 == 0) angle else -angle) else 0f
                                    alpha = if (dragFrom == i) 0.25f else 1f
                                }
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (hovered) C.CardHigh else if (editing) C.Card else androidx.compose.ui.graphics.Color.Transparent)
                                .dragSource(item, i) { slots[i] },
                        ) {
                            QuickAction(
                                sp.icon, sp.description, active = sp.active, busy = sp.busy, caption = sp.caption ?: item.short,
                                enabled = !editing, onLongClick = { onEditingChange(true) }, onClick = sp.onClick,
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(editing, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Column(
                    Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(18.dp)).background(C.Card).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Dra ikonene dit du vil ha dem", fontSize = 13.sp, color = C.Sub, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    val extra = QuickItem.entries.filter { it !in items }
                    extra.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { item ->
                                key(item) {
                                    var bounds by remember { mutableStateOf<Rect?>(null) }
                                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            Modifier.size(56.dp).clip(CircleShape).background(C.CardHigh)
                                                .onGloballyPositioned { bounds = it.boundsInRoot() }
                                                .graphicsLayer { alpha = if (dragItem == item) 0.25f else 1f }
                                                .dragSource(item, -1) { bounds },
                                            contentAlignment = Alignment.Center,
                                        ) { Icon(spec(item).icon, item.title, tint = C.On, modifier = Modifier.size(26.dp)) }
                                        Text(item.title, fontSize = 11.sp, color = C.Sub, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                                    }
                                }
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    BigButton("Ferdig") { onEditingChange(false) }
                }
            }
        }

        // The icon under the finger while dragging.
        dragItem?.let { item ->
            val icon = spec(item).icon
            Box(
                Modifier
                    .offset { IntOffset((dragPos.x - origin.x).roundToInt() - 32.dp.roundToPx(), (dragPos.y - origin.y).roundToInt() - 32.dp.roundToPx()) }
                    .size(64.dp).clip(CircleShape).background(C.CardHigh),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = C.Accent, modifier = Modifier.size(30.dp)) }
        }
    }
}
