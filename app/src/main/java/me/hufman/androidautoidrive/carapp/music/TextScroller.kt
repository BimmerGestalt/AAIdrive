package me.hufman.androidautoidrive.carapp.music

/**
 * Handles the scrolling of text that is too long to fit on a line.
 */
class TextScroller(private val originalText: String, private val maxLineLength: Int) {
	companion object {
		const val SCROLL_COOLDOWN_SECONDS = 6
		const val INDEX_JUMP_VALUE = 3
	}

	val shouldScroll: Boolean
	var scrollText: Boolean = false
	var startIndex: Int = 0
	var previousTimestamp: Long

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
		var displayText = originalText
		val endIndex = maxLineLength + startIndex
		if (scrollText) {
			if (endIndex <= originalText.length) {
				displayText = originalText.substring(startIndex, endIndex)
				startIndex += INDEX_JUMP_VALUE
			} else {
				scrollText = false
				displayText = originalText.substring(startIndex, originalText.length)
				startIndex = 0
				previousTimestamp = System.currentTimeMillis()
			}
		} else if ((System.currentTimeMillis() - previousTimestamp) / 1000 >= SCROLL_COOLDOWN_SECONDS) {
			scrollText = true
		}
		return displayText
	}
}