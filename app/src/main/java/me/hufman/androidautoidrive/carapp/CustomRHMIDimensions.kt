package me.hufman.androidautoidrive.carapp

import io.bimmergestalt.idriveconnectkit.RHMIDimensions
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.AppSettingsViewer

class CustomRHMIDimensions(val original: RHMIDimensions, val settingsViewer: AppSettingsViewer): RHMIDimensions {
	override val rhmiWidth: Int
		get() = settingsViewer[AppSettings.KEYS.DIMENSIONS_RHMI_WIDTH].toIntOrNull() ?: original.rhmiWidth
	override val rhmiHeight: Int
		get() = settingsViewer[AppSettings.KEYS.DIMENSIONS_RHMI_HEIGHT].toIntOrNull() ?: original.rhmiHeight
	override val marginLeft: Int
		get() = settingsViewer[AppSettings.KEYS.DIMENSIONS_MARGIN_LEFT].toIntOrNull() ?: original.marginLeft
	override val paddingLeft: Int
		get() = settingsViewer[AppSettings.KEYS.DIMENSIONS_PADDING_LEFT].toIntOrNull() ?: original.paddingLeft
	override val paddingTop: Int
		get() = settingsViewer[AppSettings.KEYS.DIMENSIONS_PADDING_TOP].toIntOrNull() ?: original.paddingTop
	override val marginRight: Int
		get() = settingsViewer[AppSettings.KEYS.DIMENSIONS_MARGIN_RIGHT].toIntOrNull() ?: original.marginRight
}
class UpdatingSidebarRHMIDimensions(val fullscreen: RHMIDimensions, val isWidescreen: () -> Boolean):
		RHMIDimensions {
	override val rhmiWidth: Int
		get() = fullscreen.rhmiWidth
	override val rhmiHeight: Int
		get() = fullscreen.rhmiHeight
	override val marginLeft: Int
		get() = fullscreen.marginLeft
	override val paddingLeft: Int
		get() = fullscreen.paddingLeft
	override val paddingTop: Int
		get() = fullscreen.paddingTop
	override val marginRight: Int
		get() = if (isWidescreen() || fullscreen.rhmiWidth < 900) { fullscreen.marginRight } else {
			(fullscreen.rhmiWidth * 0.37).toInt()
		}
}