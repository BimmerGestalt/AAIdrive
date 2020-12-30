package me.hufman.androidautoidrive.phoneui

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import me.hufman.androidautoidrive.music.MusicController
import me.hufman.androidautoidrive.music.MusicMetadata
import me.hufman.androidautoidrive.music.PlaybackPosition
import me.hufman.androidautoidrive.music.QueueMetadata
import me.hufman.androidautoidrive.music.controllers.SpotifyAppController
import me.hufman.androidautoidrive.music.spotify.SpotifyWebApi
import me.hufman.androidautoidrive.phoneui.viewmodels.MusicActivityModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MusicActivityModelTest {
	@Rule @JvmField
	val instantTaskExecutorRule = InstantTaskExecutorRule()

	val metadata = MusicMetadata(artist="Artist", album="Album", title="Title")
	val playbackPosition = mock<PlaybackPosition> {
		on { isPaused } doReturn false
		on { getPosition() } doReturn 4500
		on { maximumPosition } doReturn 300000
	}
	val musicController = mock<MusicController> {
		on { isConnected() } doReturn true
		on { getMetadata() } doReturn metadata
		on { getPlaybackPosition() } doReturn playbackPosition
	}
	val webApi = mock<SpotifyWebApi>()
	val viewModel = MusicActivityModel(musicController, webApi)

	@Test
	fun update() {
		viewModel.update()

		assertEquals(true, viewModel.connected.value)
		assertEquals(metadata.artist, viewModel.artist.value)
		assertEquals(metadata.album, viewModel.album.value)
		assertEquals(metadata.title, viewModel.title.value)
		assertEquals(null, viewModel.coverArt.value)
		assertEquals(null, viewModel.queueMetadata.value)
		assertEquals(false, viewModel.isPaused.value)
		assertEquals(4, viewModel.playbackPosition.value)
		assertEquals(300, viewModel.maxPosition.value)
		assertEquals(null, viewModel.errorTitle.value)
		assertEquals(null, viewModel.errorMessage.value)

		val queue = mock<QueueMetadata>()
		whenever(musicController.getQueue()) doReturn queue
		viewModel.update()
		assertEquals(queue, viewModel.queueMetadata.value)
	}

	@Test
	fun error() {
		val error = Exception("Test")
		val spotifyConnector = mock<SpotifyAppController.Connector> {
			on { lastError } doReturn error
		}
		whenever(musicController.connectors) doAnswer { listOf (spotifyConnector) }
		viewModel.update()

		assertEquals("Exception", viewModel.errorTitle.value)
		assertEquals("Test", viewModel.errorMessage.value)
	}

	@Test
	fun apiAuthenticated() {
		whenever(webApi.isUsingSpotify) doReturn false
		whenever(webApi.isAuthorized()) doReturn true

		viewModel.update()

		assertNull(viewModel.isWebApiAuthorized.value)
	}

	@Test
	fun apiNotAuthenticated_NotUsingSpotify() {
		whenever(webApi.isUsingSpotify) doReturn false
		whenever(webApi.isAuthorized()) doReturn false

		viewModel.update()

		assertNull(viewModel.isWebApiAuthorized.value)
	}

	@Test
	fun apiNotAuthenticated_UsingSpotify() {
		whenever(webApi.isUsingSpotify) doReturn true
		whenever(webApi.isAuthorized()) doReturn false

		viewModel.update()

		assertEquals(false, viewModel.isWebApiAuthorized.value)
	}
}