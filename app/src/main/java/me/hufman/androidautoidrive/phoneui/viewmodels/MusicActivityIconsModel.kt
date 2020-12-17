package me.hufman.androidautoidrive.phoneui.viewmodels

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import me.hufman.androidautoidrive.CarAppAssetManager
import me.hufman.androidautoidrive.utils.Utils

class MusicActivityIconsModel(private val _icons: Map<String, Bitmap>): ViewModel() {
	class Factory(val context: Context): ViewModelProvider.Factory {
		@Suppress("UNCHECKED_CAST")
		override fun <T : ViewModel> create(modelClass: Class<T>): T {
			val icons = loadIcons(context)
			return MusicActivityIconsModel(icons) as T
		}

		private fun loadIcons(context: Context): Map<String, Bitmap> {
			val appAssets = CarAppAssetManager(context, "multimedia")
			val images = Utils.loadZipfile(appAssets.getImagesDB("common"))
			val icons = HashMap<String, Bitmap>()
			for (id in listOf(ARTIST_ID, ALBUM_ID, SONG_ID, PLACEHOLDER_ID, FOLDER_ID)) {
				icons[id] = BitmapFactory.decodeByteArray(images[id], 0, images[id]?.size ?: 0)
			}
			return icons
		}
	}

	companion object {
		private const val ARTIST_ID = "150.png"
		private const val ALBUM_ID = "148.png"
		private const val SONG_ID = "152.png"
		private const val PLACEHOLDER_ID = "147.png"
		private const val FOLDER_ID = "155.png"
	}

	val artistIcon: Bitmap
		get() = _icons.getValue(ARTIST_ID)
	val albumIcon: Bitmap
		get() = _icons.getValue(ALBUM_ID)
	val songIcon: Bitmap
		get() = _icons.getValue(SONG_ID)
	val placeholderCoverArt: Bitmap
		get() = _icons.getValue(PLACEHOLDER_ID)
	val folderIcon: Bitmap
		get() = _icons.getValue(FOLDER_ID)
}