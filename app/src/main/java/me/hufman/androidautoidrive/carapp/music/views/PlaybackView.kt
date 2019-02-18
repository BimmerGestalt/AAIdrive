package me.hufman.androidautoidrive.carapp.music.views

import me.hufman.androidautoidrive.PhoneAppResources
import me.hufman.androidautoidrive.TimeUtils.formatTime
import me.hufman.androidautoidrive.carapp.RHMIModelMultiSetterData
import me.hufman.androidautoidrive.carapp.RHMIModelMultiSetterInt
import me.hufman.androidautoidrive.findAdjacentComponent
import me.hufman.androidautoidrive.music.MusicAppInfo
import me.hufman.androidautoidrive.music.MusicController
import me.hufman.androidautoidrive.music.MusicMetadata
import me.hufman.idriveconnectionkit.rhmi.*

private const val IMAGEID_ARTIST = 150
private const val IMAGEID_ALBUM = 148
private const val IMAGEID_SONG = 152

class PlaybackView(val state: RHMIState,val controller: MusicController, val phoneAppResources: PhoneAppResources) {
	companion object {
		fun fits(state: RHMIState): Boolean {
			return state is RHMIState.ToolbarState &&
					state.componentsList.filterIsInstance<RHMIComponent.Gauge>().isNotEmpty() &&
					state.componentsList.filterIsInstance<RHMIComponent.Image>().filter {
						it.getModel() is RHMIModel.RaImageModel
					}.isNotEmpty()
		}
	}

	val appTitleModel: RHMIModel.RaDataModel
	val appLogoModel: RHMIModel.RaImageModel
	val albumArtBigModel: RHMIModel.RaImageModel
	val albumArtSmallModel: RHMIModel.RaImageModel
	val artistModel: RHMIModelMultiSetterData
	val albumModel: RHMIModelMultiSetterData
	val trackModel: RHMIModelMultiSetterData
	val gaugeModel: RHMIModelMultiSetterInt
	val currentTimeModel: RHMIModelMultiSetterData
	val maximumTimeModel: RHMIModelMultiSetterData

	var displayedApp: MusicAppInfo? = null
	var displayedSong: MusicMetadata? = null

	init {
		// discover widgets
		state as RHMIState.ToolbarState

		appTitleModel = state.getTextModel()?.asRaDataModel()!!
		appLogoModel = state.componentsList.filterIsInstance<RHMIComponent.Image>().filter {
			var property = it.properties[20]
			val smallPosition = (property as? RHMIProperty.LayoutBag)?.get(1)
			val widePosition = (property as? RHMIProperty.LayoutBag)?.get(0)
			(smallPosition is Int && smallPosition < 1900) &&
					(widePosition is Int && widePosition < 1900)
		}.first().getModel()?.asRaImageModel()!!

		val smallComponents = state.componentsList.filter {
			val property = it.properties[20]
			val smallPosition = (property as? RHMIProperty.LayoutBag)?.get(1)
			smallPosition is Int && smallPosition < 1900
		}
		val wideComponents = state.componentsList.filter {
			val property = it.properties[20]
			val widePosition = (property as? RHMIProperty.LayoutBag)?.get(0)
			widePosition is Int && widePosition < 1900
		}
		albumArtBigModel = wideComponents.filterIsInstance<RHMIComponent.Image>().first {
			(it.properties[10]?.value as? Int ?: 0) == 320
		}.getModel()?.asRaImageModel()!!
		albumArtSmallModel = smallComponents.filterIsInstance<RHMIComponent.Image>().first {
			(it.properties[10]?.value as? Int ?: 0) == 200
		}.getModel()?.asRaImageModel()!!

		val artists = arrayOf(smallComponents, wideComponents).map { components ->
			findAdjacentComponent(components) { it.asImage()?.getModel()?.asImageIdModel()?.imageId == IMAGEID_ARTIST}
		}
		artistModel = RHMIModelMultiSetterData(artists.map { it?.asLabel()?.getModel()?.asRaDataModel() })

		val albums = arrayOf(smallComponents, wideComponents).map { components ->
			findAdjacentComponent(components) { it.asImage()?.getModel()?.asImageIdModel()?.imageId == IMAGEID_ALBUM}
		}
		albumModel = RHMIModelMultiSetterData(albums.map { it?.asLabel()?.getModel()?.asRaDataModel() })

		val titles = arrayOf(smallComponents, wideComponents).map { components ->
			findAdjacentComponent(components) { it.asImage()?.getModel()?.asImageIdModel()?.imageId == IMAGEID_SONG}
		}
		trackModel = RHMIModelMultiSetterData(titles.map { it?.asLabel()?.getModel()?.asRaDataModel() })

		val currentTimes = arrayOf(smallComponents, wideComponents).map { components ->
			components.filterIsInstance<RHMIComponent.Label>().dropLast(1).last()
		}
		currentTimeModel = RHMIModelMultiSetterData(currentTimes.map { it.asLabel()?.getModel()?.asRaDataModel() })
		val maxTimes = arrayOf(smallComponents, wideComponents).map { components ->
			components.filterIsInstance<RHMIComponent.Label>().last()
		}
		maximumTimeModel = RHMIModelMultiSetterData(maxTimes.map { it.asLabel()?.getModel()?.asRaDataModel() })
		val gauges = arrayOf(smallComponents, wideComponents).map { components ->
			components.filterIsInstance<RHMIComponent.Gauge>().first()
		}
		gaugeModel = RHMIModelMultiSetterInt(gauges.map { it.getModel()?.asRaIntModel() })
	}

	fun initWidgets(appSwitcherView: AppSwitcherView) {
		state as RHMIState.ToolbarState

		val buttons = state.toolbarComponentsList
		buttons[0].getTooltipModel()?.asRaDataModel()?.value = "Apps"
		buttons[0].getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = appSwitcherView.state.id

		buttons[1].getTooltipModel()?.asRaDataModel()?.value = "Browse"
		buttons[1].setEnabled(false)
		buttons[1].setSelectable(false)

		buttons[2].getTooltipModel()?.asRaDataModel()?.value = "Currently Playing"
		buttons[2].setEnabled(false)
		buttons[2].setSelectable(false)

		// the book icon
		buttons[3].getImageModel()?.asImageIdModel()?.imageId = 0
		buttons[3].setSelectable(false)

		buttons[4].getTooltipModel()?.asRaDataModel()?.value = "Actions"
		buttons[4].setEnabled(false)
		buttons[4].setSelectable(false)

		buttons[5].getTooltipModel()?.asRaDataModel()?.value = "Shuffle"
		buttons[5].setEnabled(false)
		buttons[5].setSelectable(false)

		buttons[6].getTooltipModel()?.asRaDataModel()?.value = "Back"
		buttons[6].getAction()?.asRAAction()?.rhmiActionCallback = object : RHMIAction.RHMIActionCallback {
			override fun onActionEvent(args: Map<*, *>?) {
				controller.skipToPrevious()
			}
		}
		buttons[7].getTooltipModel()?.asRaDataModel()?.value = "Next"
		buttons[7].getAction()?.asRAAction()?.rhmiActionCallback = object : RHMIAction.RHMIActionCallback {
			override fun onActionEvent(args: Map<*, *>?) {
				controller.skipToNext()
			}
		}

	}

	fun show() {
		redraw()
	}

	fun redraw() {
		if (displayedApp != controller.currentApp?.musicAppInfo) {
			redrawApp()
		}
		if (displayedSong != controller.getMetadata()) {
			redrawSong()
		}
		redrawPosition()
	}

	private fun redrawApp() {
		val app = controller.currentApp?.musicAppInfo ?: return
		appTitleModel.value = app.name
		val image = phoneAppResources.getBitmap(app.icon, 48, 48)
		appLogoModel.value = image
		displayedApp = app
	}

	private fun redrawSong() {
		val song = controller.getMetadata()
		artistModel.value = song?.artist ?: ""
		albumModel.value = song?.album ?: ""
		trackModel.value = song?.title ?: ""
		if (song?.coverArt != null) {
			albumArtBigModel.value = phoneAppResources.getBitmap(song.coverArt, 320, 320)
			albumArtSmallModel.value = phoneAppResources.getBitmap(song.coverArt, 200, 200)
		} else {
			albumArtBigModel.value = ByteArray(0)
			albumArtSmallModel.value = ByteArray(0)
		}
		displayedSong = song
	}

	private fun redrawPosition() {
		val progress = controller.getPlaybackPosition()
		if (progress.maximumPosition > 0) {
			gaugeModel.value = (100 * progress.getPosition() / progress.maximumPosition).toInt()
		} else {
			gaugeModel.value = 50
		}
		maximumTimeModel.value = formatTime(progress.maximumPosition)
		currentTimeModel.value = if (progress.playbackPaused && System.currentTimeMillis() % 1000 >= 500) {
			"   :  "
		} else {
			formatTime(progress.getPosition())
		}
	}

}