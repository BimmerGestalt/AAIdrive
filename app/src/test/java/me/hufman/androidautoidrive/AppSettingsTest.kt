package me.hufman.androidautoidrive

import org.junit.Assert.*
import org.junit.Test

class AppSettingsTest {
	@Test
	fun testListSettings() {
		val settings = MockAppSettings(AppSettings.KEYS.HIDDEN_MUSIC_APPS to "a,b")
		val listSettings = ListSetting(settings, AppSettings.KEYS.HIDDEN_MUSIC_APPS)

		// tests the mass set functionality
		val expected = setOf("a", "b")
		assertEquals(expected, listSettings.getAll())

		listSettings.setAll(setOf("1", "2"))
		assertEquals("1,2", settings[AppSettings.KEYS.HIDDEN_MUSIC_APPS])

		listSettings.setAll(setOf("a", "b"))
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
		assertEquals("a,b,c", settings[AppSettings.KEYS.HIDDEN_MUSIC_APPS])

		listSettings.addAll(setOf("c", "d"))
		assertEquals("a,b,c,d", settings[AppSettings.KEYS.HIDDEN_MUSIC_APPS])

		listSettings.clear()
		assertEquals("", settings[AppSettings.KEYS.HIDDEN_MUSIC_APPS])

		settings[AppSettings.KEYS.HIDDEN_MUSIC_APPS] = "a,b"
		listSettings.remove("b")
		assertEquals("a", settings[AppSettings.KEYS.HIDDEN_MUSIC_APPS])

		listSettings.removeAll(setOf("a", "b"))
		assertEquals("", settings[AppSettings.KEYS.HIDDEN_MUSIC_APPS])

		settings[AppSettings.KEYS.HIDDEN_MUSIC_APPS] = "a,b"
		listSettings.retainAll(setOf("a", "c"))
		assertEquals("a", settings[AppSettings.KEYS.HIDDEN_MUSIC_APPS])
	}
}