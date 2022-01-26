package me.hufman.androidautoidrive.maps

import me.hufman.androidautoidrive.carapp.maps.LatLong
import org.junit.Assert.assertEquals
import org.junit.Test

class LatLongTest {
	@Test
	fun distanceFrom() {
		val start = LatLong(37.373022, -121.994893)
		val end = LatLong(37.353052, -121.976596)
		assertEquals(2.7, start.distanceFrom(end), .1)
	}
}