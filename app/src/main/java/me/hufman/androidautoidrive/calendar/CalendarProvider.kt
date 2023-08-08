package me.hufman.androidautoidrive.calendar

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.database.Cursor
import android.provider.CalendarContract
import me.hufman.androidautoidrive.AppSettings
import java.text.DateFormat
import java.util.*

fun Calendar.copy(): Calendar {
	return Calendar.getInstance().also { out ->
		out.timeInMillis = this.timeInMillis
		out.timeZone = this.timeZone
	}
}

data class PhoneCalendar(
		val name: String,
		val visible: Boolean,
		val color: Int
)

data class CalendarEvent(
		val title: String,
		val start: Calendar,
		val end: Calendar,
		val location: String,
		val description: String,
		val color: Int
) {
	fun isAllDay(): Boolean {
		return start[Calendar.HOUR_OF_DAY] == 0 &&
				start[Calendar.MINUTE] == 0 &&
				end[Calendar.HOUR_OF_DAY] == 0 &&
				end[Calendar.MINUTE] == 0
	}
	fun formatDay(): String {
		val format = DateFormat.getDateInstance(DateFormat.SHORT)
		return format.format(start.time)
	}
	fun formatStartTime(): String {
		val format = DateFormat.getTimeInstance(DateFormat.SHORT)
		return format.format(start.time)
	}
	fun formatEndTime(): String {
		val format = DateFormat.getTimeInstance(DateFormat.SHORT)
		return format.format(end.time)
	}
}

class CalendarProvider(val context: Context, val appSettings: AppSettings) {
	companion object {

		val PROJECTION = arrayOf(
				CalendarContract.Instances.TITLE,
				CalendarContract.Instances.DESCRIPTION,
				CalendarContract.Instances.EVENT_LOCATION,
				CalendarContract.Instances.ALL_DAY,
				CalendarContract.Instances.EVENT_TIMEZONE,
				CalendarContract.Instances.BEGIN,
				CalendarContract.Instances.END,
				CalendarContract.Instances.DISPLAY_COLOR)
		const val INDEX_TITLE = 0
		const val INDEX_DESCRIPTION = 1
		const val INDEX_LOCATION = 2
		const val INDEX_ALL_DAY = 3
		const val INDEX_TIMEZONE = 4
		const val INDEX_BEGIN = 5
		const val INDEX_END = 6
		const val INDEX_COLOR = 7

		val CALENDAR_PROJECTION = arrayOf(
				CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
				CalendarContract.Calendars.VISIBLE,
				CalendarContract.Calendars.CALENDAR_COLOR,
		)
		const val INDEX_CALENDAR_NAME = 0
		const val INDEX_CALENDAR_VISIBLE = 1
		const val INDEX_CALENDAR_COLOR = 2

		fun parseEvent(cursor: Cursor): CalendarEvent {
			val title = cursor.getString(INDEX_TITLE) ?: ""
			val description = cursor.getString(INDEX_DESCRIPTION) ?: ""
			val location = cursor.getString(INDEX_LOCATION) ?: ""
			val color = cursor.getInt(INDEX_COLOR)

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
					title, eventStart, eventEnd, location, description, color
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

	@SuppressLint("Recycle")
	fun getCalendars(): List<PhoneCalendar> {
		val cursor = try {
			context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, CALENDAR_PROJECTION, null, null, null)
		} catch (e: SecurityException) { null }

		val calendars = ArrayList<PhoneCalendar>()
		if (cursor != null) {
			cursor.moveToFirst()
			while (cursor.moveToNext()) {
				val name = cursor.getString(INDEX_CALENDAR_NAME)
				val visible = cursor.getInt(INDEX_CALENDAR_VISIBLE) == 1
				val color = cursor.getInt(INDEX_CALENDAR_COLOR)
				calendars.add(PhoneCalendar(name, visible, color))
			}
		}
		cursor?.close()
		calendars.sortBy { it.name }
		return calendars
	}

	@SuppressLint("Recycle")
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

		val cursor = try {
			if (appSettings[AppSettings.KEYS.CALENDAR_IGNORE_VISIBILITY].toBoolean()) {
				// manually query Instances table, ignoring VISIBLE flag
				val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
				ContentUris.appendId(builder, queryStart.timeInMillis)
				ContentUris.appendId(builder, queryEnd.timeInMillis)
				context.contentResolver.query(builder.build(), PROJECTION,
						null, null, "begin ASC")
			} else {
				CalendarContract.Instances.query(context.contentResolver, PROJECTION, queryStart.timeInMillis, queryEnd.timeInMillis)
			}
		} catch (e: SecurityException) { null }
		if (cursor != null) {
			cursor.moveToFirst()
			while (cursor.moveToNext()) {
				splitEventIntoDays(parseEvent(cursor)).forEach { event ->
					if (event.start[Calendar.YEAR] == year &&
						event.start[Calendar.MONTH] + 1 == month &&
						(event.start[Calendar.DAY_OF_MONTH] == day || day == null)) {
							val isDetailed = event.title.isNotBlank() && (event.description.isNotBlank() || event.location.isNotBlank() )
							if (!appSettings[AppSettings.KEYS.CALENDAR_DETAILED_EVENTS].toBoolean() || isDetailed) {
								events.add(event)
							}
//						val allDay = cursor.getInt(INDEX_ALL_DAY)
//						val timezoneName = cursor.getString(INDEX_TIMEZONE)
//						println("${event.title}  ${allDay}T $timezoneName  ${event.start} - ${event.end}")
//					} else {
//						println("Skipping ${event.title} on ${event.start}")
					}
				}
			}
		}
		cursor?.close()
		events.sortBy { it.start }
		return events
	}
}