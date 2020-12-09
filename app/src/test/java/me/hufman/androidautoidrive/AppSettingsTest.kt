package me.hufman.androidautoidrive

import org.junit.Assert.*
import org.junit.Test

class AppSettingsTest {
	@Test
	fun testStoredSet() {
		val settings = MockAppSettings(AppSettings.KEYS.HIDDEN_MUSIC_APPS to "a,b")
		val storedSet = StoredSet(settings, AppSettings.KEYS.HIDDEN_MUSIC_APPS)

		// tests the mass set functionality
		val expected = setOf("a", "b")
		assertEquals(expected, storedSet.getAll())

		storedSet.setAll(setOf("1", "2"))
		assertEquals("[\"1\",\"2\"]", settings[AppSettings.KEYS.HIDDEN_MUSIC_APPS])

		storedSet.setAll(setOf("a", "b"))
		val foundItems = mutableSetOf<String>()
		storedSet.withSet {
			foundItems.addAll(this)
		}
		assertEquals(expected, foundItems)

		// set functionality
		assertEquals("a,b", storedSet.iterator().asSequence().joinToString(","))
		assertTrue(storedSet.contains("a"))
		assertFalse(storedSet.contains("c"))
		assertTrue(storedSet.containsAll(setOf("a")))
		assertTrue(storedSet.containsAll(setOf("a", "b")))
		assertFalse(storedSet.containsAll(setOf("a", "b", "c")))
		assertEquals(2, storedSet.size)
		assertFalse(storedSet.isEmpty())

		// mutable set functionality
		storedSet.add("c")
		assertEquals("[\"a\",\"b\",\"c\"]", settings[AppSettings.KEYS.HIDDEN_MUSIC_APPS])


		storedSet.addAll(setOf("c", "d"))
		assertEquals("[\"a\",\"b\",\"c\",\"d\"]", settings[AppSettings.KEYS.HIDDEN_MUSIC_APPS])

		storedSet.clear()
		assertEquals("[]", settings[AppSettings.KEYS.HIDDEN_MUSIC_APPS])

		settings[AppSettings.KEYS.HIDDEN_MUSIC_APPS] = "a,b"
		storedSet.remove("b")
		assertEquals("[\"a\"]", settings[AppSettings.KEYS.HIDDEN_MUSIC_APPS])

		storedSet.removeAll(setOf("a", "b"))
		assertEquals("[]", settings[AppSettings.KEYS.HIDDEN_MUSIC_APPS])

		settings[AppSettings.KEYS.HIDDEN_MUSIC_APPS] = "a,b"
		storedSet.retainAll(setOf("a", "c"))
		assertEquals("[\"a\"]", settings[AppSettings.KEYS.HIDDEN_MUSIC_APPS])

		storedSet.add("complex,th\"ings[yay]")
		assertEquals("[\"a\",\"complex,th\\\"ings[yay]\"]", settings[AppSettings.KEYS.HIDDEN_MUSIC_APPS])
	}
}