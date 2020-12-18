package me.hufman.androidautoidrive.phoneui

import android.graphics.Bitmap
import com.nhaarman.mockito_kotlin.*
import me.hufman.androidautoidrive.phoneui.viewmodels.MusicActivityIconsModel
import org.junit.Assert.*
import org.junit.Test

class MusicActivityIconsModelTest {
	@Test
	fun testGetter() {
		val ARTIST_ID = "150.png"
		val ALBUM_ID = "148.png"
		val SONG_ID = "152.png"
		val PLACEHOLDER_ID = "147.png"
		val FOLDER_ID = "155.png"
		val realIcons = mapOf<String, Bitmap>(
				ARTIST_ID to mock(),
				ALBUM_ID to mock(),
				SONG_ID to mock(),
				PLACEHOLDER_ID to mock(),
				FOLDER_ID to mock()
		)
		val icons = spy(realIcons)
		val viewModel = MusicActivityIconsModel(icons)

		assertEquals(realIcons[ARTIST_ID], viewModel.artistIcon)
		verify(icons).getValue(ARTIST_ID)
		assertEquals(realIcons[ALBUM_ID], viewModel.albumIcon)
		verify(icons).getValue(ALBUM_ID)
		assertEquals(realIcons[SONG_ID], viewModel.songIcon)
		verify(icons).getValue(SONG_ID)
		assertEquals(realIcons[FOLDER_ID], viewModel.folderIcon)
		verify(icons).getValue(FOLDER_ID)
		assertEquals(realIcons[PLACEHOLDER_ID], viewModel.placeholderCoverArt)
		verify(icons).getValue(PLACEHOLDER_ID)
	}
}