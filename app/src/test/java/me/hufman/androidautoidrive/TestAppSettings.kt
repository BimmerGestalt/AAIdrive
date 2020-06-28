package me.hufman.androidautoidrive

import com.nhaarman.mockito_kotlin.*
import org.junit.Assert.*
import org.junit.Test

class TestAppSettings {
	@Test
	fun testListSettings() {
		val settings = mock<MutableAppSettings> {
			on { get(any()) } doReturn "a,b"
		}
		val listSettings = ListSetting(settings, AppSettings.KEYS.HIDDEN_MUSIC_APPS)

		// tests the mass set functionality
		val expected = setOf("a", "b")
		assertEquals(expected, listSettings.getAll())

		listSettings.setAll(setOf("1", "2"))
		verify(settings)[any()] = eq("1,2")

		val foundItems = mutableSetOf<String>()
		listSettings.withSet {
			foundItems.addAll(this)
		}
		assertEquals(expected, foundItems)

		// set functionality
		assertEquals("a,b", listSettings.iterator().asSequence().joinToString(","))
		assertTrue(listSettings.contains("a"))
		assertFalse(listSettings.contains("c"))
		assertTrue(listSettings.containsAll(setOf("a")))
		assertTrue(listSettings.containsAll(setOf("a", "b")))
		assertFalse(listSettings.containsAll(setOf("a", "b", "c")))
		assertEquals(2, listSettings.size)
		assertFalse(listSettings.isEmpty())

		// mutable set functionality
		listSettings.add("c")
		verify(settings, atLeastOnce())[any()] = eq("a,b,c")

		listSettings.addAll(setOf("c", "d"))
		verify(settings, atLeastOnce())[any()] = eq("a,b,c,d")

		listSettings.clear()
		verify(settings, atLeastOnce())[any()] = eq("")

		listSettings.remove("b")
		verify(settings, atLeastOnce())[any()] = eq("a")

		listSettings.removeAll(setOf("a", "b"))
		verify(settings, atLeastOnce())[any()] = eq("")

		listSettings.retainAll(setOf("a", "c"))
		verify(settings, atLeastOnce())[any()] = eq("a")
	}
}