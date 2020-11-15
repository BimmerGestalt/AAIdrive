package android.text.format

object DateUtils {
	@JvmStatic
	fun formatElapsedTime(recycle: StringBuilder, elapsedSeconds: Long): String {
		return elapsedSeconds.toString()
	}
}