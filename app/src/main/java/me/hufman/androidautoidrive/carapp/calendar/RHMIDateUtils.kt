package me.hufman.androidautoidrive.carapp.calendar

import java.util.*

object RHMIDateUtils {
	fun convertFromRhmiDate(date: Int): Calendar {
		val year = (date shr 16) and 0xffff
		val month = (date shr 8) and 0xff
		val day = (date shr 0) and 0xff
		return Calendar.getInstance().apply {
			set(year, month - 1, day)
			set(Calendar.HOUR_OF_DAY, 0)
			set(Calendar.MINUTE, 0)
			set(Calendar.SECOND, 0)
			set(Calendar.MILLISECOND, 0)
		}
	}
	fun convertToRhmiDate(date: Calendar): Int {
		return (date.get(Calendar.YEAR) shl 16) +
				((date.get(Calendar.MONTH) + 1) shl 8) +
				date.get(Calendar.DAY_OF_MONTH)
	}

	fun convertToRhmiTime(time: Calendar): Int {
		return (time.get(Calendar.SECOND) shl 16) +
				(time.get(Calendar.MINUTE) shl 8) +
				time.get(Calendar.HOUR_OF_DAY)
	}
}