package android.os

object SystemClock {
    @JvmStatic
    fun elapsedRealtime(): Long {
        return System.currentTimeMillis()
    }
}
