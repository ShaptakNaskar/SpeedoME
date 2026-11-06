package com.sappy.speedome.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.*
import org.junit.Test

class PickMenuStateTest {
    @Test
    fun `release selects only an option visible inside the scrolling menu`() {
        val menu = PickMenuState()
        var picked: String? = null
        val request = PickMenuState.Request("WORLDS", listOf(PickOption("a", "A")), "a", Rect.Zero, true) { picked = it }
        menu.open(request)
        menu.bounds["a"] = Rect(0f, 0f, 100f, 100f)
        menu.viewport = Rect(0f, 20f, 100f, 80f)
        assertFalse(menu.hover(Offset(50f, 10f)))
        menu.release()
        assertNull(picked)
        menu.open(request)
        menu.bounds["a"] = Rect(0f, 0f, 100f, 100f)
        menu.viewport = Rect(0f, 20f, 100f, 80f)
        assertTrue(menu.hover(Offset(50f, 50f)))
        menu.release()
        assertEquals("a", picked)
        assertNull(menu.request)
        assertNull(menu.pointer)
        assertNull(menu.viewport)
    }

    @Test
    fun `opening a new menu clears old item locations`() {
        val menu = PickMenuState()
        val request = PickMenuState.Request("WORLDS", emptyList(), "", Rect.Zero, true) {}
        menu.open(request)
        menu.bounds["old"] = Rect(0f, 0f, 100f, 100f)
        menu.open(request)
        assertFalse(menu.hover(Offset(50f, 50f)))
        assertTrue(menu.bounds.isEmpty())
    }
}
