package me.hufman.androidautoidrive.phoneui

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import org.mockito.kotlin.*
import me.hufman.androidautoidrive.CarInformation
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.carapp.music.MusicAppMode
import me.hufman.androidautoidrive.phoneui.viewmodels.CarCapabilitiesViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CarCapabilitiesModelTest {
	@Rule
	@JvmField
	val instantTaskExecutorRule = InstantTaskExecutorRule()

	val context: Context = mock {
		on { getString(any()) } doReturn ""
		on { getString(any(), any()) } doReturn ""
	}

	@Test
	fun testNoCapabilities() {
		val carInfo = mock<CarInformation> {
			on { connectionBrand } doReturn "mini"
			on { capabilities } doReturn mapOf()
		}
		val musicAppMode = mock<MusicAppMode> {
			on { heuristicAudioContext() } doReturn false
			on { isId4() } doReturn false
			on { supportsId5Playback() } doReturn false
			on { shouldId5Playback() } doReturn false
		}
		val viewModel = CarCapabilitiesViewModel(carInfo, musicAppMode).apply { update() }
		assertEquals(false, viewModel.isCarConnected.value)
		assertEquals(false, viewModel.isAudioContextSupported.value)
		assertEquals(false, viewModel.isAudioStateSupported.value)
		assertEquals(false, viewModel.isTtsSupported.value)
		assertEquals(false, viewModel.isTtsNotSupported.value)
		assertEquals(false, viewModel.isNaviSupported.value)
		assertEquals(false, viewModel.isNaviNotSupported.value)
	}

	@Test
	fun testId4Usb() {
		val carInfo = mock<CarInformation> {
			on { connectionBrand } doReturn "mini"
			on { capabilities } doReturn mapOf("hmi.type" to "MINI ID4", "tts" to "true", "navi" to "false")
		}
		val musicAppMode = mock<MusicAppMode> {
			on { heuristicAudioContext() } doReturn false
			on { isId4() } doReturn true
			on { supportsId5Playback() } doReturn false
			on { shouldId5Playback() } doReturn false
		}
		val viewModel = CarCapabilitiesViewModel(carInfo, musicAppMode).apply { update() }
		assertEquals(true, viewModel.isCarConnected.value)
		assertEquals(false, viewModel.isAudioContextSupported.value)
		assertEquals(false, viewModel.isAudioStateSupported.value)
		assertEquals(true, viewModel.isTtsSupported.value)
		assertEquals(false, viewModel.isTtsNotSupported.value)
		assertEquals(false, viewModel.isNaviSupported.value)
		assertEquals(true, viewModel.isNaviNotSupported.value)

		context.run(viewModel.audioContextStatus.value!!)
		verify(context).getString(R.string.txt_capabilities_audiocontext_no_usb)
		context.run(viewModel.audioContextHint.value!!)
		verify(context).getString(R.string.txt_capabilities_audiocontext_hint)

		context.run(viewModel.audioStateStatus.value!!)
		verify(context).getString(R.string.txt_capabilities_audiostate_no)
		context.run(viewModel.audioStateHint.value!!)
		verify(context).getString(R.string.txt_capabilities_audiostate_id4)

		context.run(viewModel.ttsStatus.value!!)
		verify(context).getString(R.string.txt_capabilities_tts_yes)

		context.run(viewModel.naviStatus.value!!)
		verify(context).getString(R.string.txt_capabilities_navi_no)
	}

	@Test
	fun testId4UsbContext() {
		val carInfo = mock<CarInformation> {
			on { connectionBrand } doReturn "mini"
			on { capabilities } doReturn mapOf("hmi.type" to "MINI ID4", "tts" to "true", "navi" to "false")
		}
		val musicAppMode = mock<MusicAppMode> {
			on { heuristicAudioContext() } doReturn true
			on { isId4() } doReturn true
			on { shouldId5Playback() } doReturn false
		}
		val viewModel = CarCapabilitiesViewModel(carInfo, musicAppMode).apply { update() }
		assertEquals(true, viewModel.isCarConnected.value)
		assertEquals(true, viewModel.isAudioContextSupported.value)
		assertEquals(false, viewModel.isAudioStateSupported.value)
		assertEquals(true, viewModel.isTtsSupported.value)
		assertEquals(false, viewModel.isTtsNotSupported.value)
		assertEquals(false, viewModel.isNaviSupported.value)
		assertEquals(true, viewModel.isNaviNotSupported.value)

		context.run(viewModel.audioContextStatus.value!!)
		verify(context).getString(R.string.txt_capabilities_audiocontext_yes)
		context.run(viewModel.audioContextHint.value!!)
		verifyNoMoreInteractions(context)

		context.run(viewModel.audioStateStatus.value!!)
		verify(context).getString(R.string.txt_capabilities_audiostate_no)
		context.run(viewModel.audioStateHint.value!!)
		verify(context).getString(R.string.txt_capabilities_audiostate_id4)
	}

	@Test
	fun testId5() {
		val carInfo = mock<CarInformation> {
			on { connectionBrand } doReturn "mini"
			on { capabilities } doReturn mapOf("hmi.type" to "MINI ID5", "tts" to "false", "navi" to "true")
		}
		val musicAppMode = mock<MusicAppMode> {
			on { heuristicAudioContext() } doReturn true
			on { isId4() } doReturn false
			on { supportsId5Playback() } doReturn true
			on { shouldId5Playback() } doReturn true
		}
		val viewModel = CarCapabilitiesViewModel(carInfo, musicAppMode).apply { update() }
		assertEquals(true, viewModel.isCarConnected.value)
		assertEquals(true, viewModel.isAudioContextSupported.value)
		assertEquals(true, viewModel.isAudioStateSupported.value)
		assertEquals(false, viewModel.isTtsSupported.value)
		assertEquals(true, viewModel.isTtsNotSupported.value)
		assertEquals(true, viewModel.isNaviSupported.value)
		assertEquals(false, viewModel.isNaviNotSupported.value)

		context.run(viewModel.audioContextStatus.value!!)
		verify(context).getString(R.string.txt_capabilities_audiocontext_yes)
		context.run(viewModel.audioContextHint.value!!)
		verifyNoMoreInteractions(context)

		context.run(viewModel.audioStateStatus.value!!)
		verify(context).getString(R.string.txt_capabilities_audiostate_yes)
		context.run(viewModel.audioStateHint.value!!)
		verifyNoMoreInteractions(context)

		context.run(viewModel.ttsStatus.value!!)
		verify(context).getString(R.string.txt_capabilities_tts_no)

		context.run(viewModel.naviStatus.value!!)
		verify(context).getString(R.string.txt_capabilities_navi_yes)
	}

	@Test
	fun testId5NoContext() {
		val carInfo = mock<CarInformation> {
			on { connectionBrand } doReturn "mini"
			on { capabilities } doReturn mapOf("hmi.type" to "MINI ID5", "tts" to "false", "navi" to "true")
		}
		val musicAppMode = mock<MusicAppMode> {
			on { heuristicAudioContext() } doReturn false
			on { shouldRequestAudioContext() } doReturn false
			on { isBTConnection() } doReturn false
			on { supportsUsbAudio() } doReturn false
			on { isId4() } doReturn false
			on { supportsId5Playback() } doReturn false
			on { shouldId5Playback() } doReturn false
		}
		val viewModel = CarCapabilitiesViewModel(carInfo, musicAppMode).apply { update() }
		assertEquals(true, viewModel.isCarConnected.value)
		assertEquals(false, viewModel.isAudioContextSupported.value)
		assertEquals(false, viewModel.isAudioStateSupported.value)
		assertEquals(false, viewModel.isTtsSupported.value)
		assertEquals(true, viewModel.isTtsNotSupported.value)
		assertEquals(true, viewModel.isNaviSupported.value)
		assertEquals(false, viewModel.isNaviNotSupported.value)

		context.run(viewModel.audioContextStatus.value!!)
		verify(context).getString(R.string.txt_capabilities_audiocontext_no_usb)
		context.run(viewModel.audioContextHint.value!!)
		verify(context).getString(R.string.txt_capabilities_audiocontext_hint)

		context.run(viewModel.audioStateStatus.value!!)
		verify(context).getString(R.string.txt_capabilities_audiostate_no)
		context.run(viewModel.audioStateHint.value!!)
		verify(context).getString(R.string.txt_capabilities_audiostate_audiocontext)
	}

	@Test
	fun testId5Spotify() {
		val carInfo = mock<CarInformation> {
			on { connectionBrand } doReturn "mini"
			on { capabilities } doReturn mapOf("hmi.type" to "MINI ID5", "tts" to "false", "navi" to "true")
		}
		val musicAppMode = mock<MusicAppMode> {
			on { heuristicAudioContext() } doReturn true
			on { shouldRequestAudioContext() } doReturn true
			on { isId4() } doReturn false
			on { supportsId5Playback() } doReturn false
			on { shouldId5Playback() } doReturn false
			on { isNewSpotifyInstalled() } doReturn false
		}
		val viewModel = CarCapabilitiesViewModel(carInfo, musicAppMode).apply { update() }
		assertEquals(true, viewModel.isCarConnected.value)
		assertEquals(true, viewModel.isAudioContextSupported.value)
		assertEquals(false, viewModel.isAudioStateSupported.value)
		assertEquals(false, viewModel.isTtsSupported.value)
		assertEquals(true, viewModel.isTtsNotSupported.value)
		assertEquals(true, viewModel.isNaviSupported.value)
		assertEquals(false, viewModel.isNaviNotSupported.value)

		context.run(viewModel.audioContextStatus.value!!)
		verify(context).getString(R.string.txt_capabilities_audiocontext_yes)
		context.run(viewModel.audioContextHint.value!!)
		verifyNoMoreInteractions(context)

		context.run(viewModel.audioStateStatus.value!!)
		verify(context).getString(R.string.txt_capabilities_audiostate_no)
		context.run(viewModel.audioStateHint.value!!)
		verify(context).getString(R.string.txt_capabilities_audiostate_spotify)
	}


	@Test
	fun testId5Classic() {
		val carInfo = mock<CarInformation> {
			on { connectionBrand } doReturn "mini"
			on { capabilities } doReturn mapOf("hmi.type" to "MINI ID5", "tts" to "false", "navi" to "true")
		}
		val musicAppMode = mock<MusicAppMode> {
			on { heuristicAudioContext() } doReturn true
			on { shouldRequestAudioContext() } doReturn true
			on { isId4() } doReturn false
			on { supportsId5Playback() } doReturn true
			on { shouldId5Playback() } doReturn false
			on { isNewSpotifyInstalled() } doReturn true
		}
		val viewModel = CarCapabilitiesViewModel(carInfo, musicAppMode).apply { update() }
		assertEquals(true, viewModel.isCarConnected.value)
		assertEquals(true, viewModel.isAudioContextSupported.value)
		assertEquals(true, viewModel.isAudioStateSupported.value)
		assertEquals(false, viewModel.isTtsSupported.value)
		assertEquals(true, viewModel.isTtsNotSupported.value)
		assertEquals(true, viewModel.isNaviSupported.value)
		assertEquals(false, viewModel.isNaviNotSupported.value)

	}


	@Test
	fun testJ29() {
		val carInfo = mock<CarInformation> {
			on { connectionBrand } doReturn "j29"
			on { capabilities } doReturn mapOf("hmi.type" to "J29 ID6L", "tts" to "true", "navi" to "false")
		}
		val musicAppMode = mock<MusicAppMode> {
			on { heuristicAudioContext() } doReturn true
			on { shouldRequestAudioContext() } doReturn true
			on { isId4() } doReturn false
			on { supportsId5Playback() } doReturn true
			on { shouldId5Playback() } doReturn false
			on { isNewSpotifyInstalled() } doReturn true
		}
		val viewModel = CarCapabilitiesViewModel(carInfo, musicAppMode).apply { update() }
		assertEquals(false, viewModel.isCarConnected.value)
		assertEquals(true, viewModel.isJ29Connected.value)
		assertEquals(false, viewModel.isAudioContextSupported.value)
		assertEquals(false, viewModel.isAudioStateSupported.value)
		assertEquals(false, viewModel.isTtsSupported.value)
		assertEquals(true, viewModel.isTtsNotSupported.value)
		assertEquals(false, viewModel.isNaviSupported.value)
		assertEquals(true, viewModel.isNaviNotSupported.value)

	}

	@Test
	fun testJ29_entwickler() {
		val carInfo = mock<CarInformation> {
			on { connectionBrand } doReturn "bmw"
			on { capabilities } doReturn mapOf("hmi.type" to "J29 ID6L", "tts" to "true", "navi" to "false")
		}
		val musicAppMode = mock<MusicAppMode> {
			on { heuristicAudioContext() } doReturn true
			on { shouldRequestAudioContext() } doReturn true
			on { isId4() } doReturn false
			on { supportsId5Playback() } doReturn true
			on { shouldId5Playback() } doReturn false
			on { isNewSpotifyInstalled() } doReturn true
		}
		val viewModel = CarCapabilitiesViewModel(carInfo, musicAppMode).apply { update() }
		assertEquals(true, viewModel.isCarConnected.value)
		assertEquals(true, viewModel.isJ29Connected.value)
		assertEquals(true, viewModel.isAudioContextSupported.value)
		assertEquals(true, viewModel.isAudioStateSupported.value)
		assertEquals(true, viewModel.isTtsSupported.value)
		assertEquals(false, viewModel.isTtsNotSupported.value)
		assertEquals(false, viewModel.isNaviSupported.value)
		assertEquals(true, viewModel.isNaviNotSupported.value)

	}
}