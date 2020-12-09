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
		assertEquals(setOf("a", "b"), storedSet)
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

	@Test
	fun testStoredList() {
		val settings = MockAppSettings(AppSettings.KEYS.HIDDEN_MUSIC_APPS to "[\"a\",\"b\"]")
		val storedList = StoredList(settings, AppSettings.KEYS.HIDDEN_MUSIC_APPS)

		// tests the mass set functionality
		val expected = listOf("a", "b")
		assertEquals(expected, storedList.getAll())

		storedList.setAll(listOf("1", "2"))
		assertEquals("[\"1\",\"2\"]", settings[AppSettings.KEYS.HIDDEN_MUSIC_APPS])

		storedList.setAll(listOf("a", "b"))
		val foundItems = mutableListOf<String>()
		storedList.withList {
			foundItems.addAll(this)
		}
		assertEquals(expected, foundItems)

		// readonly list functionality
		assertTrue(storedList.contains("b"))
		assertFalse(storedList.contains("c"))
		assertTrue(storedList.containsAll(setOf("a", "b")))
		assertFalse(storedList.containsAll(setOf("b", "c")))
		assertEquals("b", storedList[1])
		assertEquals(1, storedList.indexOf("b"))
		assertEquals(1, storedList.lastIndexOf("b"))
		assertFalse(storedList.isEmpty())
		assertEquals(2, storedList.size)
		assertEquals("a,b", storedList.iterator().asSequence().joinToString(","))
		assertEquals("a,b", storedList.listIterator().asSequence().joinToString(","))
		assertEquals("a,b", storedList.subList(0, 2).joinToString(","))
		assertEquals("b", storedList.subList(1, 2).joinToString(","))

		// mutable list functionality
		storedList.clear()
		assertEquals(emptyList<String>(), storedList)
		storedList.add("b")
		storedList.add(0, "a")
		assertEquals(expected, storedList)
		storedList.addAll(1, listOf("1"))
		storedList.addAll(listOf("2"))
		assertEquals(listOf("a", "1", "b", "2"), storedList)

		assertTrue(storedList.removeAll(setOf("2", "3")))
		assertEquals(listOf("a", "1", "b"), storedList)
		assertTrue(storedList.retainAll(setOf("a", "b")))
		assertEquals(expected, storedList)
		assertEquals("b", storedList.removeAt(1))
		assertFalse(storedList.remove("b"))
		assertTrue(storedList.remove("a"))
		assertTrue(storedList.isEmpty())
		storedList.add("b")
		storedList[0] = "a"
		assertEquals(listOf("a"), storedList)
	}
}