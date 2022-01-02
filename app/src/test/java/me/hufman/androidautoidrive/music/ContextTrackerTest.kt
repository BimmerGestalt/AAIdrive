package me.hufman.androidautoidrive.music

import com.google.gson.JsonObject
import me.hufman.androidautoidrive.carapp.music.ContextTracker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeTime {
	var time = System.currentTimeMillis()
	val timeProvider: () -> Long = {time}

	fun passTime(timeMs: Int) {
		time += timeMs
	}
}

fun _hmiContext(title: String, listIndex: Int): JsonObject {
	return JsonObject().apply {
		add("graphicalContext", JsonObject().apply {
			addProperty("menuTitle", title)
			addProperty("listIndex", listIndex)
			addProperty("appType", 1)
			addProperty("shiftDirections", "L")
			addProperty("scrollDirection", "vertical")
		})
	}
}

fun ContextTracker.onHmiContextUpdate(title: String, listIndex: Int) {
	onHmiContextUpdate(_hmiContext(title, listIndex))
}

class ContextTrackerTest {
	val time = FakeTime()
	val contextTracker = ContextTracker(time.timeProvider)

	@Test
	/** User intentional clicks Spotify by scrolling to it, but too fast */
	fun testSpotifyLines() {
		contextTracker.onHmiContextUpdate("Home", -1)
		time.passTime(10000)
		contextTracker.onHmiContextUpdate("Media", 3)
		time.passTime(500)
		contextTracker.onHmiContextUpdate("Media", 4)
		time.passTime(100)
		contextTracker.onHmiContextUpdate("Media", 5)
		assertTrue(contextTracker.isIntentionalSpotifyClick())
	}

	@Test
	/** User intentional clicks Spotify by scrolling to it, slower than the threshold */
	fun testSpotifyTime() {
		contextTracker.onHmiContextUpdate("Home", -1)
		time.passTime(10000)
		contextTracker.onHmiContextUpdate("Media", 3)
		time.passTime(500)
		contextTracker.onHmiContextUpdate("Media", 4)
		time.passTime(100)
		contextTracker.onHmiContextUpdate("Media", 5)
		time.passTime(1000)
		assertTrue(contextTracker.isIntentionalSpotifyClick())
	}

	@Test
	/** Media button unintentionally clicks Spotify, faster than threshold */
	fun testMediaShortcut() {
		contextTracker.onHmiContextUpdate("Home", -1)
		time.passTime(10000)
		contextTracker.onHmiContextUpdate("Media", 3)
		time.passTime(100)
		assertFalse(contextTracker.isIntentionalSpotifyClick())
	}

	@Test
	/** Media button unintentionally clicks Spotify, but slower than the threshold */
	fun testMediaShortcutDelayed() {
		contextTracker.onHmiContextUpdate("Home", -1)
		time.passTime(10000)
		contextTracker.onHmiContextUpdate("Media", 3)
		time.passTime(1500)
		assertFalse(contextTracker.isIntentionalSpotifyClick())
	}

	@Test
	/** Media button unintentionally clicks Spotify, after the user has idled in the Media menu */
	fun testMediaDwell() {
		contextTracker.onHmiContextUpdate("Home", -1)
		time.passTime(10000)
		contextTracker.onHmiContextUpdate("Media", 3)
		time.passTime(500)
		contextTracker.onHmiContextUpdate("Media", 4)
		time.passTime(100)
		contextTracker.onHmiContextUpdate("Media", 5)
		time.passTime(10000)
		contextTracker.onHmiContextUpdate("Media", 6)
		time.passTime(100)
		assertFalse(contextTracker.isIntentionalSpotifyClick())
	}

	@Test
	/** Media button unintentionally clicks Spotify, after previously timing out the previous Media button press */
	fun testMediaDoubleEntry() {
		contextTracker.onHmiContextUpdate("Home", -1)
		time.passTime(10000)
		contextTracker.onHmiContextUpdate("Media", 3)
		time.passTime(100)
		assertFalse(contextTracker.isIntentionalSpotifyClick())
		time.passTime(5000)
		assertFalse(contextTracker.isIntentionalSpotifyClick())
		time.passTime(10000)
		assertFalse(contextTracker.isIntentionalSpotifyClick())
	}

	@Test
	/** The car scrolls a single line after pushing the Media button, make sure that doesn't count as user input */
	fun testSingleScrollInput() {
		contextTracker.onHmiContextUpdate("Media", 0)
		time.passTime(15)
		contextTracker.onHmiContextUpdate("Media", 1)   // the car changed list index
		assertFalse(contextTracker.isIntentionalSpotifyClick())
	}
}