package me.hufman.androidautoidrive.carapp.music

import com.google.gson.JsonObject
import me.hufman.androidautoidrive.utils.GsonNullable.tryAsInt
import me.hufman.androidautoidrive.utils.GsonNullable.tryAsJsonObject
import me.hufman.androidautoidrive.utils.GsonNullable.tryAsJsonPrimitive
import me.hufman.androidautoidrive.utils.GsonNullable.tryAsString

/**
 * When the Spotify Entrybutton is clicked,
 * use our knowledge of the context tracking to guess if it was intentional
 *
 * The car automatically clicks the Spotify Entrybutton when the Media shortcut button is pressed
 * If the user was somewhere else in the car, this shows up as the context's Menu Title changing
 * right before the button press.
 * If the user was idling in the list and then pushes the button, this shows up as
 * no changes to the hmiContext and then suddenly one HmiContext event with a lineIndex change
 * Additionally, if the Media button is clicked but the Action Handler times out and
 * the media menu remains open, further Media button clicks should be assumed to be unintentional
 * until the Hmi Context updates with the next user input
 *
 * This is all separate from the AM icons for the music apps, because the Media button doesn't click those
 */
class ContextTracker(val timeProvider: () -> Long = {System.currentTimeMillis()}) {
	companion object {
		const val CONTEXT_CHANGED_THRESHOLD = 2500  // how long should we consider the user to be idle and then ignore Spotify entrybutton
		const val MENU_CHANGED_THRESHOLD = 1200   // how long after entering the menu do we ignore Spotify entrybutton
	}
	var contextChangedTime = 0L     // when we received a context update
	var menuChangedTime = 0L        // when we changed to a different screen
	var menuTitle = ""
	var currentLine = 0
	var linesScrolled = 0
	var wasIdle = false     // whether the user was idle before the latest hmiContext update
	var wasUnintentional = false    // whether the previous spotify click was unintentional

	fun onHmiContextUpdate(hmiContext: JsonObject) = synchronized(this) {
		val time = timeProvider()
		wasIdle = false
		wasUnintentional = false
		if (contextChangedTime + CONTEXT_CHANGED_THRESHOLD < time) {
			wasIdle = true
		}
		contextChangedTime = timeProvider()

		val line = hmiContext.tryAsJsonObject("graphicalContext")?.tryAsJsonPrimitive("listIndex")?.tryAsInt ?: -1
		if (line != currentLine) {
			linesScrolled += 1
			currentLine = line
		}

		val title = hmiContext.tryAsJsonObject("graphicalContext")?.tryAsJsonPrimitive("menuTitle")?.tryAsString ?: ""
		if (title != menuTitle) {
			menuChangedTime = timeProvider()
			menuTitle = title
			linesScrolled = 0
		}
	}

	fun isIntentionalSpotifyClick(): Boolean = synchronized(this) {
		if (wasUnintentional) {
			return false
		}
		if (contextChangedTime == 0L) {
			// car hasn't sent us a context update yet
			wasUnintentional = true
			return false
		}
		val time = timeProvider()
		if (menuChangedTime + MENU_CHANGED_THRESHOLD > time && linesScrolled <= 1) {
			// user entered the menu too recently and didn't scroll enough
			wasUnintentional = true
			return false
		}
		if (wasIdle) {
			// user hadn't changed anything for a while before suddenly clicking Spotify
			// so it was probably the car clicking Spotify for the shortcut button
			wasUnintentional = true
			return false
		}
		return true
	}
}