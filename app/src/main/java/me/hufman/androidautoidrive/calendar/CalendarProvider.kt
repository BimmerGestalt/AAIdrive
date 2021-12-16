package me.hufman.androidautoidrive.calendar

import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.database.Cursor
import android.provider.CalendarContract
import me.hufman.androidautoidrive.AppSettings
import java.util.*

fun Calendar.copy(): Calendar {
	return Calendar.getInstance().also { out ->
		out.timeInMillis = this.timeInMillis
		out.timeZone = this.timeZone
	}
}

data class CalendarEvent(
		val title: String,
		val start: Calendar,
		val end: Calendar,
		val location: String,
		val description: String
)

class CalendarProvider(val context: Context, val appSettings: AppSettings) {
	companion object {

		val PROJECTION = arrayOf(
				CalendarContract.Instances.TITLE,
				CalendarContract.Instances.DESCRIPTION,
				CalendarContract.Instances.EVENT_LOCATION,
				CalendarContract.Instances.ALL_DAY,
				CalendarContract.Instances.EVENT_TIMEZONE,
				CalendarContract.Instances.BEGIN,
				CalendarContract.Instances.END,)
		const val INDEX_TITLE = 0
		const val INDEX_DESCRIPTION = 1
		const val INDEX_LOCATION = 2
		const val INDEX_ALL_DAY = 3
		const val INDEX_TIMEZONE = 4
		const val INDEX_BEGIN = 5
		const val INDEX_END = 6

		fun parseEvent(cursor: Cursor): CalendarEvent {
			val title = cursor.getString(INDEX_TITLE)
			val description = cursor.getString(INDEX_DESCRIPTION)
			val location = cursor.getString(INDEX_LOCATION)

			val eventStart = Calendar.getInstance().apply {
				timeInMillis = cursor.getLong(INDEX_BEGIN)

				// All Day events are time-zone independent
				// and stored in UTC, so represent the time as such
				if (cursor.getInt(INDEX_ALL_DAY) == 1) {
					timeZone = TimeZone.getTimeZone("UTC")
				}
			}
			val eventEnd = Calendar.getInstance().apply {
				timeInMillis = cursor.getLong(INDEX_END)

				// All Day events are time-zone independent
				// and stored in UTC, so represent the time as such
				if (cursor.getInt(INDEX_ALL_DAY) == 1) {
					timeZone = TimeZone.getTimeZone("UTC")
				}
			}

			return CalendarEvent(
					title, eventStart, eventEnd, location, description
			)
		}

		fun splitEventIntoDays(event: CalendarEvent): List<CalendarEvent> {
			val results = ArrayList<CalendarEvent>()

			val dayStart = event.start.copy()
			val dayEnd = event.start.copy()
			dayEnd.add(Calendar.DATE, 1)
			dayEnd[Calendar.HOUR_OF_DAY] = 0
			dayEnd[Calendar.MINUTE] = 0
			dayEnd[Calendar.SECOND] = 0
			dayEnd[Calendar.MILLISECOND] = 0

			while (dayEnd < event.end) {
				results.add(event.copy(start=dayStart.copy(), end=dayEnd.copy()))
				// move window to next midnight
				dayStart.timeInMillis = dayEnd.timeInMillis
				dayEnd.add(Calendar.DATE, 1)
			}
			results.add(event.copy(start=dayStart, end=event.end))
			return results
		}
	}

	fun hasPermission(): Boolean {
		return context.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) == PERMISSION_GRANTED
	}

	fun getNow(): Calendar {
		return Calendar.getInstance()
	}

	fun getEvents(year: Int, month: Int, day: Int?): List<CalendarEvent> {
		val events = ArrayList<CalendarEvent>()
		val start = Calendar.getInstance()
		start.set(year, month-1, day ?: 1, 0, 0, 0)
		val end: Calendar = Calendar.getInstance().apply {
			time = start.time
			if (day == null) {
				add(Calendar.MONTH, 1)
			} else {
				add(Calendar.DATE, 1)
			}
		}

		// adjust to capture cross-day events
		val queryStart = Calendar.getInstance().apply {
			time = start.time
			add(Calendar.DATE, -1)
		}
		val queryEnd = Calendar.getInstance().apply {
			time = end.time
			add(Calendar.DATE, 1)
		}

		val cursor = CalendarContract.Instances.query(context.contentResolver, PROJECTION, queryStart.timeInMillis, queryEnd.timeInMillis)
		if (cursor != null) {
			cursor.moveToFirst()
			while (cursor.moveToNext()) {
				val allDay = cursor.getInt(INDEX_ALL_DAY)
				val timezoneName = cursor.getString(INDEX_TIMEZONE)
				splitEventIntoDays(parseEvent(cursor)).forEach { event ->
					if (event.start[Calendar.YEAR] == year &&
						event.start[Calendar.MONTH] + 1 == month &&
						(event.start[Calendar.DAY_OF_MONTH] == day || day == null)) {
							if (!appSettings[AppSettings.KEYS.CALENDAR_DETAILED_EVENTS].toBoolean() ||
									event.description.isNotBlank() || event.location.isNotBlank()) {
								events.add(event)
							}
//						println("${event.title}  ${allDay}T $timezoneName  ${event.start} - ${event.end}")
//					} else {
//						println("Skipping ${event.title} on ${event.start}")
					}
				}
			}
		}
		cursor?.close()
		return events
	}
}