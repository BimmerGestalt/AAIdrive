package android.os

object SystemClock {
	@JvmStatic
	fun uptimeMillis(): Long {
		return System.currentTimeMillis()
	}
	@JvmStatic
	fun elapsedRealtime(): Long {
		return System.currentTimeMillis()
	}
}