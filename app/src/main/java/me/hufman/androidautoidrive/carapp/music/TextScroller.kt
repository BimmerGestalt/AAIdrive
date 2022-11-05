package me.hufman.androidautoidrive.carapp.music

/**
 * Wrapper around a string that handles scrolling when it is too long to fit on a line.
 */
class TextScroller(private val originalText: String, private val maxLineLength: Int) {
	companion object {
		const val SCROLL_COOLDOWN_SECONDS = 2
		const val INDEX_JUMP_VALUE = 3
		const val INDEX_PAST_END = 5        // keep scrolling this many extra letters
	}

	private val shouldScroll: Boolean
	private var scrollText: Boolean = false
	private var startIndex: Int = 0
	private var previousTimestamp: Long

	init {
		previousTimestamp = System.currentTimeMillis()
		shouldScroll = originalText.length > maxLineLength
	}

	/**
	 * Retrieves the current slice of the text to display. Every [SCROLL_COOLDOWN_SECONDS] the text
	 * will be ready to start scrolling and making a call to this method will get the current state
	 * of the text and shift the displayed slice at rate of [INDEX_JUMP_VALUE]. Once the slice reaches
	 * the end of the original text, it will reset the slice back to the beginning and begin the
	 * cooldown interval.
	 *
	 * If the original text is not long enough to need scrolling the original will be returned.
	 */
	fun getText(): String {
		if (!shouldScroll) {
			return originalText
		}
		val displayText = originalText.substring(startIndex, originalText.length)
		if (scrollText) {
			val endIndex = maxLineLength + startIndex
			if (endIndex <= originalText.length + INDEX_PAST_END) {
				startIndex += INDEX_JUMP_VALUE
			} else {
				scrollText = false
				startIndex = 0
				previousTimestamp = System.currentTimeMillis()
			}
		} else if ((System.currentTimeMillis() - previousTimestamp) / 1000 >= SCROLL_COOLDOWN_SECONDS) {
			scrollText = true
		}
		return displayText
	}
}