package me.hufman.androidautoidrive.phoneui

import android.arch.lifecycle.ViewModelProviders
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentStatePagerAdapter
import android.support.v7.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_musicplayer.*
import me.hufman.androidautoidrive.CarAppAssetManager
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.Utils
import me.hufman.androidautoidrive.music.MusicAppInfo
import me.hufman.androidautoidrive.music.MusicController
import me.hufman.androidautoidrive.music.MusicMetadata

class MusicPlayerActivity: AppCompatActivity() {

	companion object {
		const val TAG = "MusicPlayerActivity"
	}

	var musicApp: MusicAppInfo? = null
	var musicController: MusicController? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		setContentView(R.layout.activity_musicplayer)

		val musicApp = UIState.selectedMusicApp ?: return
		this.musicApp = musicApp
		txtAppName.text = musicApp.name
		imgAppIcon.setImageDrawable(musicApp.icon)

		// load the viewmodel
		val viewModel = ViewModelProviders.of(this).get(MusicActivityModel::class.java)
		viewModel.musicController = viewModel.musicController ?: MusicController(applicationContext, Handler(this.mainLooper))
		viewModel.musicController?.connectApp(musicApp)
		musicController = viewModel.musicController

		// load the icons
		val appAssets = CarAppAssetManager(this, "multimedia")
		val images = Utils.loadZipfile(appAssets.getImagesDB("common"))
		for (id in listOf("150.png", "148.png", "152.png", "147.png", "155.png")) {
			viewModel.icons[id] = BitmapFactory.decodeByteArray(images[id], 0, images[id]?.size ?: 0)
		}

		// set up the paging
		pgrMusicPlayer.adapter = MusicPlayerPagerAdapter(supportFragmentManager)
		pgrMusicPlayer.offscreenPageLimit = 2
		tabMusicPlayer.setupWithViewPager(pgrMusicPlayer)
	}

	override fun onDestroy() {
		super.onDestroy()
		musicController?.disconnectApp(pause=false)
	}

	fun pushBrowse(directory: MusicMetadata?) {
		val container = (pgrMusicPlayer.adapter as MusicPlayerPagerAdapter).getItem(1) as MusicBrowseFragment
		container.replaceFragment(MusicBrowsePageFragment.newInstance(directory), true)
	}

	fun showNowPlaying() {
		pgrMusicPlayer.currentItem = 0
	}

	override fun onBackPressed() {
		if (pgrMusicPlayer.currentItem == 0) {
			// pass through default behavior, to close the Activity
			super.onBackPressed()
		}
		if (pgrMusicPlayer.currentItem == 1) {
			val container = (pgrMusicPlayer.adapter as MusicPlayerPagerAdapter).getItem(1) as MusicBrowseFragment
			val popped = container.onBackPressed()
			if (!popped) {
				pgrMusicPlayer.currentItem = 0
			}
		}
	}
}

class MusicPlayerPagerAdapter(fm: FragmentManager): FragmentStatePagerAdapter(fm) {
	val tabs = LinkedHashMap<String, Fragment>(2).apply {
		this["Now Playing"] = MusicNowPlayingFragment()
		this["Browse"] = MusicBrowseFragment.newInstance(MusicBrowsePageFragment.newInstance(null))
	}

	override fun getCount(): Int {
		return tabs.size
	}

	override fun getPageTitle(position: Int): CharSequence? {
		return tabs.keys.elementAt(position)
	}

	override fun getItem(index: Int): Fragment {
		return tabs.values.elementAt(index)
	}
}
