package me.hufman.androidautoidrive.carapp

interface RHMIDimensions {
	// the size of the physical screen
	val rhmiWidth: Int
	val rhmiHeight: Int
	// how much of the screen is taken away by various bars
	val marginLeft: Int     // a fixed toolbar on the left that we can't draw over
	val paddingLeft: Int    // the amount of space we can shift over with negative POSITION_X
	val paddingTop: Int     // the amount of space we can shift up with negative POSITION_Y
	val marginRight: Int

	// the remaining display area for the app content
	val appWidth: Int
		get() = rhmiWidth - marginLeft - paddingLeft - marginRight
	val appHeight: Int
		get() = rhmiHeight - paddingTop
	// the usable display area if the padding is removed
	val visibleWidth: Int
		get() = rhmiWidth - marginLeft - marginRight
	val visibleHeight: Int
		get() = rhmiHeight

	companion object {
		fun create(capabilities: Map<String, String?>): RHMIDimensions {
			val brand = if (capabilities["hmi.type"]?.startsWith("MINI") == true) "MINI" else "BMW"
			val id4 = capabilities["hmi.type"]?.contains("ID4") == true
			val a4axl = capabilities["a4axl"] == "true"
			val rhmiWidth = capabilities["hmi.display-width"]?.toIntOrNull() ?: 800
			val rhmiHeight = capabilities["hmi.display-height"]?.toIntOrNull() ?: 480

			if (brand == "BMW" && !id4 && rhmiWidth == 1440) return BMW5XLRHMIDimensions(rhmiWidth, rhmiHeight)
			if (brand == "MINI" && !id4 && a4axl) return Mini5XLDimensions()
			if (brand == "MINI") return MiniDimensions(rhmiWidth, rhmiHeight)
			return GenericRHMIDimensions(rhmiWidth, rhmiHeight)
		}
	}
}

/**
 * This RHMIDimensions object doesn't apply any margin
 * and can be used to represent a subset window for SidebarRHMIDimensions
 */
class SubsetRHMIDimensions(override val rhmiWidth: Int, override val rhmiHeight: Int): RHMIDimensions {
	override val marginLeft: Int = 0
	override val paddingLeft: Int = 0
	override val paddingTop: Int = 0
	override val marginRight: Int = 0
}

class GenericRHMIDimensions(override val rhmiWidth: Int, override val rhmiHeight: Int): RHMIDimensions {
	/*
	@jezikk82 calculated the following sizes
		Side panel on the left: 70px
		Side panel on the right open is 40% wide according to what I found.
		IDrive 5/6 has a wider main screen, with 35% side panel size
		Side panel on the right closed is 5px (omitted)
		800 x 480
		1280 x 480 * (1210/730 x 480)
		1440 x 540 * (1370/825 x 540)
		1920 x 720
	*/
	override val marginLeft: Int = 64
	override val paddingLeft: Int = 70
	override val paddingTop: Int = 80
	override val marginRight: Int = 5
}

/**
 * BMW5 has a flat toolbar, and on a 1440w screen it measures 100px
 */
class BMW5XLRHMIDimensions(override val rhmiWidth: Int, override val rhmiHeight: Int): RHMIDimensions {
	override val marginLeft: Int = 100
	override val paddingLeft: Int = 70
	override val paddingTop: Int = 80
	override val marginRight: Int = 5
}

/**
 * Mini has an extra curvy toolbar
 */
open class MiniDimensions(override val rhmiWidth: Int, override val rhmiHeight: Int): RHMIDimensions {
	override val marginLeft: Int = 200
	override val paddingLeft: Int = 16
	override val paddingTop: Int = 60
	override val marginRight: Int = 5
}

/**
 * Mini ID5 XL seems to have a bigger screen than its 1280x480 claimed rhmi dimensions
 * As a bonus, the back button floats above the content
 */
class Mini5XLDimensions: MiniDimensions(1440, 540) {
	override val marginLeft: Int = 0
	override val paddingLeft: Int = 168     // so we can remove all of the padding
}

/**
 * Wraps an existing RHMIDimensions to toggle the marginRight based on an open sidebar
 */
class SidebarRHMIDimensions(val fullscreen: RHMIDimensions, val isWidescreen: () -> Boolean): RHMIDimensions {
	override val rhmiWidth: Int = fullscreen.rhmiWidth
	override val rhmiHeight: Int = fullscreen.rhmiHeight
	override val marginLeft: Int = fullscreen.marginLeft
	override val paddingLeft: Int = fullscreen.paddingLeft
	override val paddingTop: Int = fullscreen.paddingTop
	override val marginRight: Int
		get() = if (isWidescreen() || fullscreen.rhmiWidth < 900) { fullscreen.marginRight } else {
			(fullscreen.appWidth * 0.45).toInt()
		}
}