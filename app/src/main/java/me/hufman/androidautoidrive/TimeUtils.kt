package me.hufman.androidautoidrive

import com.luckycatlabs.sunrisesunset.SunriseSunsetCalculator
import com.luckycatlabs.sunrisesunset.dto.Location
import me.hufman.androidautoidrive.carapp.maps.LatLong
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

		val afterSunrise = sunrise.get(Calendar.HOUR_OF_DAY) < calendar.get(Calendar.HOUR_OF_DAY) ||
				(sunrise.get(Calendar.HOUR_OF_DAY) == calendar.get(Calendar.HOUR_OF_DAY) &&
				sunrise.get(Calendar.MINUTE) < calendar.get(Calendar.MINUTE))
		val beforeSunset = calendar.get(Calendar.HOUR_OF_DAY) < sunset.get(Calendar.HOUR_OF_DAY) ||
				(calendar.get(Calendar.HOUR_OF_DAY) == sunset.get(Calendar.HOUR_OF_DAY) &&
				calendar.get(Calendar.MINUTE) < sunset.get(Calendar.MINUTE))
		return afterSunrise && beforeSunset
	}
}