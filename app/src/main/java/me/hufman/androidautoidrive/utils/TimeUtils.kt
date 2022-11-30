package me.hufman.androidautoidrive.utils

import com.luckycatlabs.sunrisesunset.SunriseSunsetCalculator
import com.luckycatlabs.sunrisesunset.dto.Location
import me.hufman.androidautoidrive.maps.LatLong
import java.util.*

object TimeUtils {
	/**
	 * Returns true if we are in day mode, false if it night mode
	 */
	fun getDayMode(location: LatLong, testCalendar:Calendar? = null): Boolean {
		val calendar = testCalendar ?: Calendar.getInstance()
		val sunsetLocation = Location(location.latitude, location.longitude)
		val sunsetCalculator = SunriseSunsetCalculator(sunsetLocation, calendar.timeZone)

		val sunrise = sunsetCalculator.getCivilSunriseCalendarForDate(calendar)
		val sunset = sunsetCalculator.getCivilSunsetCalendarForDate(calendar)

		if (sunrise == null || sunset == null) {
			return true     // default to Day if we don't know sunset
		}

		val afterSunrise = sunrise.get(Calendar.HOUR_OF_DAY) < calendar.get(Calendar.HOUR_OF_DAY) ||
				(sunrise.get(Calendar.HOUR_OF_DAY) == calendar.get(Calendar.HOUR_OF_DAY) &&
				sunrise.get(Calendar.MINUTE) < calendar.get(Calendar.MINUTE))
		val beforeSunset = calendar.get(Calendar.HOUR_OF_DAY) < sunset.get(Calendar.HOUR_OF_DAY) ||
				(calendar.get(Calendar.HOUR_OF_DAY) == sunset.get(Calendar.HOUR_OF_DAY) &&
				calendar.get(Calendar.MINUTE) < sunset.get(Calendar.MINUTE))
		return afterSunrise && beforeSunset
	}

	fun formatTime(timeMs: Long): String {
		return if (timeMs < 0) {
			" --:--"
		} else {
			String.format("%3d:%02d", timeMs/1000/60, timeMs/1000%60)
		}
	}
}