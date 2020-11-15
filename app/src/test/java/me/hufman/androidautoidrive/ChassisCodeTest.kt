package me.hufman.androidautoidrive

import org.junit.Assert.*
import org.junit.Test

class ChassisCodeTest {
	@Test
	fun testChassisCode() {
		assertEquals(null, ChassisCode.fromCode("unknown"))
		assertEquals(ChassisCode.E12, ChassisCode.fromCode("E12"))
		assertEquals(ChassisCode.E36_7, ChassisCode.fromCode("E36/7"))
	}
}