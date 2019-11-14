package me.hufman.androidautoidrive.phoneui

import android.arch.lifecycle.ViewModelProviders
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentStatePagerAdapter
import android.support.v7.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_music.*
import me.hufman.androidautoidrive.CarAppAssetManager
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.Utils
import me.hufman.androidautoidrive.music.MusicAppInfo
import me.hufman.androidautoidrive.music.MusicController
import me.hufman.androidautoidrive.music.MusicMetadata

class MusicActivity: AppCompatActivity() {

	companion object {
		const val TAG = "MusicActivity"
	}

	var musicApp: MusicAppInfo? = null
	var musicController: MusicController? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		setContentView(R.layout.activity_music)

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
		pgrMusic.adapter = MusicActivityPagerAdapter(supportFragmentManager)
		pgrMusic.offscreenPageLimit = 2
		tabMusic.setupWithViewPager(pgrMusic)
	}

	override fun onDestroy() {
		super.onDestroy()
		musicController?.disconnectApp(pause=false)
	}

	fun pushBrowse(directory: MusicMetadata?) {
		val container = (pgrMusic.adapter as MusicActivityPagerAdapter).getItem(1) as MusicBrowseFragment
		container.replaceFragment(MusicBrowsePageFragment.newInstance(directory), true)
	}

	fun showNowPlaying() {
		pgrMusic.currentItem = 0
	}

	override fun onBackPressed() {
		if (pgrMusic.currentItem == 0) {
			// pass through default behavior, to close the Activity
			super.onBackPressed()
		}
		if (pgrMusic.currentItem == 1) {
			val container = (pgrMusic.adapter as MusicActivityPagerAdapter).getItem(1) as MusicBrowseFragment
			val popped = container.onBackPressed()
			if (!popped) {
				pgrMusic.currentItem = 0
			}
		}
	}
}

class MusicActivityPagerAdapter(fm: FragmentManager): FragmentStatePagerAdapter(fm) {
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
