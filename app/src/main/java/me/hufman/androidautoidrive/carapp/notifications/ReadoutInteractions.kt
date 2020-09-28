package me.hufman.androidautoidrive.carapp.notifications

import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.MutableAppSettings
import me.hufman.androidautoidrive.carapp.ReadoutController
import me.hufman.androidautoidrive.notifications.CarNotification

/**
 * High-level interaction descriptions and how they affect readouts
 *
 * Incoming Notification Flow:
 *   - New notification arrives, start speaking it out (if enabled)
 *   - User enters Notification app, which shortcuts directly to the currently-speaking Notification
 *   - User presses Back to go to the ListView, which stops any current speaking
 *
 * Read Out History Flow:
 *   - User is viewing ListView, no current speaking
 *   - User clicks into a DetailsView, it starts speaking that notification (if enabled)
 *   - User presses Back, which stops the current speaking
 */
class ReadoutInteractions(val settings: MutableAppSettings) {
	companion object {
		/**
		 * Return a trimmed version of the first line that is duplicated in the second line
		 */
		fun trimCommonSuffix(line1: String, line2: String): String {
			var endpoint = line1.length
			(line1.length downTo 0).forEach { index ->
				val affix = line1.substring(index)
				if (line2.startsWith(affix)) {
					endpoint = index
				}
			}
			return line1.substring(0, endpoint)
		}
	}

	var readoutController: ReadoutController? = null   // gets initialized later than the main Notifications app
	var currentNotification: CarNotification? = null
		get() {
			if (readoutController?.isActive != true) { field = null }
			return field
		}
		set(value) { field = value }

	var passengerSeated: Boolean = false

	val shouldPopupReadout: Boolean
		get() {
			val main = AppSettings[AppSettings.KEYS.NOTIFICATIONS_READOUT_POPUP].toBoolean()
			val passenger = AppSettings[AppSettings.KEYS.NOTIFICATIONS_READOUT_POPUP_PASSENGER].toBoolean()
			return main && (passenger || !passengerSeated)
		}

	val shouldDisplayReadout: Boolean
		get() = AppSettings[AppSettings.KEYS.NOTIFICATIONS_READOUT].toBoolean()

	fun triggerPopupReadout(carNotification: CarNotification) {
		// read the latest line of a new notification, if we aren't currently reading
		if (shouldPopupReadout && currentNotification == null) {
			currentNotification = carNotification
			val line = carNotification.text.split("\n").lastOrNull() ?: ""
			val lines = mutableListOf(line)
			val title = trimCommonSuffix(carNotification.title, line)
			if (title.isNotEmpty()) {
				lines.add(0, title)
			}
			readoutController?.readout(lines)
		}
	}

	fun triggerDisplayReadout(carNotification: CarNotification) {
		if (shouldDisplayReadout && carNotification != currentNotification) {
			if (currentNotification != null) {
				readoutController?.cancel()
				Thread.sleep(500)
			}
			currentNotification = carNotification
			readoutController?.readout(carNotification.text.split("\n"))
		}
	}

	fun cancel() {
		currentNotification = null
		readoutController?.cancel()
	}
}