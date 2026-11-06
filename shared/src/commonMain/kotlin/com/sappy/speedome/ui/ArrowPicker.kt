package com.sappy.speedome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sappy.speedome.ui.theme.SpeedoColors
import kotlin.math.ceil
import kotlin.math.roundToInt

/** One choice of an [ArrowPicker]; [swatch] colours draw a small chip beside the label. */
@Immutable
data class PickOption(val id: String, val label: String, val swatch: List<Color> = emptyList())

/**
 * The open choice list, drawn over the whole app by [PickMenuHost]. Positions are root coordinates,
 * so the finger that opened the list (still tracked by the picker) can slide onto an option.
 */
@Stable
class PickMenuState {
    class Request(val title: String, val options: List<PickOption>, val selected: String, val anchor: Rect, val sliding: Boolean, val onSelect: (String) -> Unit)

    var request by mutableStateOf<Request?>(null)
        private set
    var hovered by mutableStateOf<String?>(null)
        private set
    internal val bounds = HashMap<String, Rect>()
    internal var pointer by mutableStateOf<Offset?>(null)
    internal var viewport by mutableStateOf<Rect?>(null)

    fun open(r: Request) {
        bounds.clear()
        hovered = null
        pointer = null
        request = r
    }

    /** Highlights the option under [root]; true when that changed it. */
    fun hover(root: Offset): Boolean {
        pointer = root
        val id = if (viewport?.contains(root) != false) bounds.entries.firstOrNull { it.value.contains(root) }?.key else null
        if (id == hovered) return false
        hovered = id
        return id != null
    }

    /** Ends a slide: picks the highlighted option, or just closes. */
    fun release() {
        val r = request
        val id = hovered
        close()
        if (r != null && id != null) r.onSelect(id)
    }

    fun close() {
        request = null
        hovered = null
        pointer = null
        viewport = null
    }
}

val LocalPickMenu = staticCompositionLocalOf { PickMenuState() }

/**
 * ‹ TITLE ›: the arrows step through [options]. Tapping the title opens the whole list to tap a
 * choice; pressing and holding opens it too, and sliding onto a choice and lifting picks it.
 */
@Composable
fun ArrowPicker(
    title: String,
    noun: String,
    options: List<PickOption>,
    selected: String,
    onSelect: (String) -> Unit,
    ink: Color,
    modifier: Modifier = Modifier,
    titleWidth: Dp = 96.dp,
) {
    val menu = LocalPickMenu.current
    val haptics = LocalHapticFeedback.current
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val latest by rememberUpdatedState(Triple(options, selected, onSelect))
    fun step(delta: Int) {
        val (opts, sel, pick) = latest
        val i = opts.indexOfFirst { it.id == sel }
        pick(opts[(i + delta + opts.size) % opts.size].id)
    }
    fun request(sliding: Boolean) = coords?.let { c ->
        PickMenuState.Request(noun.uppercase() + "S", latest.first, latest.second, c.boundsInRoot(), sliding, latest.third)
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        PickerArrow("‹", "Previous $noun", ink) { step(-1) }
        Text(
            title, color = ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.5.sp,
            textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .widthIn(min = titleWidth)
                .onGloballyPositioned { coords = it }
                .semantics {
                    role = Role.Button
                    contentDescription = "$noun: ${title.lowercase()}. Choose $noun"
                    onClick { request(false)?.let(menu::open); true }
                }
                .pointerInput(menu) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val held = awaitLongPressOrCancellation(down.id)
                        if (held == null) {
                            // A tap (lifted in place) opens the list for tapping.
                            val up = currentEvent.changes.firstOrNull { it.id == down.id }
                            if (up != null && !up.pressed && !up.isConsumed) request(false)?.let(menu::open)
                            return@awaitEachGesture
                        }
                        val c = coords ?: return@awaitEachGesture
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        request(true)?.let(menu::open)
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                menu.release()
                                break
                            }
                            change.consume()
                            if (menu.hover(c.localToRoot(change.position))) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        if (menu.request?.sliding == true) menu.close()
                    }
                }
                .padding(vertical = 10.dp),
        )
        PickerArrow("›", "Next $noun", ink) { step(1) }
    }
}

@Composable
private fun PickerArrow(glyph: String, description: String, ink: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Text(glyph, color = ink, fontSize = 22.sp) }
}

/**
 * Draws the open [PickMenuState] list over [content]: a scrim and a grid of choices under the title
 * that opened it, as many columns as the height needs.
 */
@Composable
fun PickMenuHost(state: PickMenuState, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        content()
        val r = state.request ?: return@Box
        PlatformBackHandler { state.close() } // Back closes the list, not the app
        val density = LocalDensity.current
        BoxWithConstraints(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = .45f))
                .clickable(remember { MutableInteractionSource() }, indication = null) { state.close() },
        ) {
            val anchorBottom = with(density) { r.anchor.bottom.toDp() }
            val rowH = 48.dp
            val maxRows = ((maxHeight - anchorBottom - 72.dp) / rowH).toInt().coerceAtLeast(4)
            val maxCols = ((maxWidth - 24.dp) / 156.dp).toInt().coerceIn(1, 4)
            val cols = ceil(r.options.size / maxRows.toFloat()).toInt().coerceIn(1, maxCols)
            val rows = ceil(r.options.size / cols.toFloat()).toInt()
            val panelW = minOf(maxWidth - 24.dp, if (cols == 1) 260.dp else 164.dp * cols + 16.dp) // room for "CONNECTED CITY"
            val x = with(density) { (r.anchor.center.x - (panelW / 2).toPx()).coerceIn(12.dp.toPx(), (maxWidth - panelW - 12.dp).toPx()) }
            val safeTop = with(density) { WindowInsets.safeDrawing.getTop(this).toDp() }
            val safeBottom = with(density) { WindowInsets.safeDrawing.getBottom(this).toDp() }
            val panelMaxHeight = (maxHeight - safeTop - safeBottom - 24.dp).coerceAtLeast(rowH)
            val panelHeight = minOf(panelMaxHeight, rowH * rows + 56.dp)
            val top = minOf(anchorBottom + 4.dp, maxHeight - safeBottom - 12.dp - panelHeight).coerceAtLeast(safeTop + 12.dp)
            val y = with(density) { top.toPx() }
            val scroll = rememberScrollState()
            LaunchedEffect(r) {
                while (r.sliding && state.request === r) {
                    val pointer = state.pointer
                    val viewport = state.viewport
                    if (pointer != null && viewport != null && pointer.x in viewport.left..viewport.right) {
                        val edge = with(density) { 36.dp.toPx() }
                        val step = when {
                            pointer.y >= viewport.bottom - edge -> edge / 3
                            pointer.y <= viewport.top + edge -> -edge / 3
                            else -> 0f
                        }
                        if (step != 0f) {
                            scroll.scrollBy(step)
                            state.hover(pointer)
                        }
                    }
                    delay(32)
                }
            }
            Column(
                Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }.width(panelW)
                    .clip(RoundedCornerShape(16.dp)).background(SpeedoColors.Raised)
                    .border(1.dp, SpeedoColors.Accent.copy(alpha = .35f), RoundedCornerShape(16.dp))
                    .clickable(remember { MutableInteractionSource() }, indication = null) {} // taps inside don't close
                    .heightIn(max = panelMaxHeight)
                    .onGloballyPositioned { state.viewport = it.boundsInRoot() }
                    .verticalScroll(scroll).padding(8.dp),
            ) {
                Text(
                    if (r.sliding) "${r.title} · SLIDE AND LIFT" else r.title, color = SpeedoColors.Muted, fontSize = 10.sp, letterSpacing = 2.sp,
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 6.dp),
                )
                for (row in 0 until rows) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (col in 0 until cols) {
                            val i = col * rows + row // fill columns top to bottom, so the order reads down
                            val o = r.options.getOrNull(i)
                            if (o == null) {
                                Box(Modifier.weight(1f))
                            } else {
                                PickItem(o, o.id == r.selected, o.id == state.hovered, rowH, Modifier.weight(1f)
                                    .onGloballyPositioned { state.bounds[o.id] = it.boundsInRoot() }) {
                                    state.close()
                                    r.onSelect(o.id)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickItem(o: PickOption, selected: Boolean, hovered: Boolean, height: Dp, modifier: Modifier, onClick: () -> Unit) {
    val ink = when {
        hovered -> SpeedoColors.Background
        selected -> SpeedoColors.Accent
        else -> SpeedoColors.Text
    }
    Row(
        modifier.height(height).clip(RoundedCornerShape(10.dp))
            .background(if (hovered) SpeedoColors.Accent else if (selected) SpeedoColors.Accent.copy(alpha = .12f) else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (o.swatch.isNotEmpty()) {
            Row(Modifier.size(width = 18.dp, height = 18.dp).clip(RoundedCornerShape(5.dp))) {
                o.swatch.forEach { Box(Modifier.weight(1f).fillMaxSize().background(it)) }
            }
        }
        Text(
            o.label, color = ink, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = .5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            fontWeight = if (selected || hovered) FontWeight.Bold else FontWeight.Medium,
        )
    }
}
