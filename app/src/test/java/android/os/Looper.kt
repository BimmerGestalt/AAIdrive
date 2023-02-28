package android.os

class Looper {
	companion object {
		@JvmStatic
		fun getMainLooper(): Looper? {
			return null
		}

		@JvmStatic
		fun myLooper(): Looper? {
			return null
		}
	}
	fun getThread(): Thread {
		return Thread.currentThread()
	}
}