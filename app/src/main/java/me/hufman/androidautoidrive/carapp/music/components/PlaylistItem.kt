package me.hufman.androidautoidrive.carapp.music.components

/**
 * @param isAnimated - Whether the icon is replaced with a loading spinner
 * @param isEnabled - Whether the user can action this item
 * @param leftImage - the icon to show on the left side
 * @param firstText - first line of text
 * @param secondText - second line of text
 */
fun PlaylistItem(isAnimated: Boolean, isEnabled: Boolean, leftImage: Any, firstText: String, secondText: String = ""): Array<Any> {
	return arrayOf(
		isAnimated, leftImage, firstText,
		"", false,    // firstRightImage
		"", false,    // secondRightImage
		secondText, secondText.isNotBlank(),
		isEnabled
	)
}