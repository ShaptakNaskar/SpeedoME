package com.sappy.speedome.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteRunsTest {
    // 1000 fixes 25 m apart, speed flickering across a colour boundary every second, GPS lost at 500.
    private val route = List(1000) { i ->
        val (lat, lon) = Geo.offset(26.0, 88.0, 0.0, i * 25.0 + if (i >= 500) 3000.0 else 0.0)
        RoutePoint(lat, lon, if (i % 2 == 0) 20f else 21f, if (i >= 500) 1 else 0)
    }

    private fun assertContinuous(runs: List<RouteRun>) {
        runs.zipWithNext().forEach { (a, b) -> assertEquals("runs must join up: $a then $b", a.to, b.from) }
        assertEquals(0, runs.first().from)
        assertEquals(route.lastIndex, runs.last().to)
        assertEquals(listOf(RouteRun(499, 500, null, gap = true)), runs.filter { it.gap })
    }

    @Test
    fun everyColourChangeIsALineWithoutAMinimum() {
        val runs = routeRuns(route, rangeMps = 36f, buckets = 24)
        assertContinuous(runs)
        assertEquals(route.size - 1, runs.size) // 998 one-step lines and the gap
    }

    @Test
    fun linesChangeColourOnlyOnceLongEnough() {
        val runs = routeRuns(route, rangeMps = 36f, buckets = 24, minRunM = 500.0)
        assertContinuous(runs)
        val lines = runs.filter { !it.gap }
        assertTrue("${lines.size} lines", lines.size in 40..52)
        // Every line but the last of each segment reaches the minimum.
        lines.filter { it.to != 499 && it.to != route.lastIndex }.forEach { r ->
            val m = (r.from until r.to).sumOf { Geo.distanceM(route[it].lat, route[it].lon, route[it + 1].lat, route[it + 1].lon) }
            assertTrue("$r is $m m", m >= 500.0 - 1e-6)
        }
        assertTrue(lines.all { it.bucket == 13 || it.bucket == 14 })
    }

    @Test
    fun oneColourRouteIsOneLinePerSegment() {
        assertEquals(
            listOf(RouteRun(0, 499, null), RouteRun(499, 500, null, gap = true), RouteRun(500, 999, null)),
            routeRuns(route, rangeMps = null, buckets = 24),
        )
    }
}
