package me.hufman.androidautoidrive

import me.hufman.androidautoidrive.utils.CachedData
import org.junit.Assert.assertEquals
import org.junit.Test

class CachedDataTest {
	var time = 500000L
	val timeProvider: () -> Long = {time}
	var served = 0
	val testProvider: () -> Int = {
		served += 1
		served
	}

	@Test
	fun testDisabled() {
		val cachedData = CachedData(5000, timeProvider, testProvider)
		assertEquals(1, cachedData.value)
		assertEquals(2, cachedData.value)
		time += 70000
		assertEquals(3, cachedData.value)
	}

	@Test
	fun testEnabled() {
		val cachedData = CachedData(5000, timeProvider, testProvider)
		cachedData.enabled = true
		assertEquals(1, cachedData.value)
		assertEquals(1, cachedData.value)
		time += 70000
		assertEquals(2, cachedData.value)

		cachedData.enabled = false
		assertEquals(3, cachedData.value)
	}
}