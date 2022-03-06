package me.hufman.androidautoidrive.maps

import org.junit.Assert.assertEquals
import org.junit.Test

class LatLongTest {
	@Test
	fun distanceFrom() {
		val start = LatLong(37.373022, -121.994893)
		val end = LatLong(37.353052, -121.976596)
		assertEquals(2.7, start.distanceFrom(end), .1)
	}

	@Test
	fun bearingTowards() {
		// bearing a little south below east
		val start = LatLong(37.373022, -121.994893)
		val end = LatLong(37.371735, -121.976789)
		assertEquals(95.1f, start.bearingTowards(end), 0.01f)
	}
}