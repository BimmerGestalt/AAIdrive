package me.hufman.androidautoidrive.phoneui

import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import kotlinx.android.synthetic.main.activity_musicplayer.*
import me.hufman.androidautoidrive.CarAppAssetManager
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.utils.Utils
import me.hufman.androidautoidrive.music.MusicAppInfo
import me.hufman.androidautoidrive.music.MusicController
import me.hufman.androidautoidrive.music.MusicMetadata
import me.hufman.androidautoidrive.phoneui.fragments.MusicBrowseFragment
import me.hufman.androidautoidrive.phoneui.fragments.MusicBrowsePageFragment
import me.hufman.androidautoidrive.phoneui.fragments.MusicNowPlayingFragment
import me.hufman.androidautoidrive.phoneui.fragments.MusicQueueFragment

class MusicPlayerActivity: AppCompatActivity() {

	companion object {
		const val TAG = "MusicPlayerActivity"
	}

	var musicApp: MusicAppInfo? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		val musicApp = UIState.selectedMusicApp ?: return
		this.musicApp = musicApp

		// load the viewmodel
		val viewModel = ViewModelProvider(this).get(MusicActivityModel::class.java)
		viewModel.musicController = viewModel.musicController ?: MusicController(applicationContext, Handler(this.mainLooper))
		viewModel.musicController?.connectAppManually(musicApp)

		// load the icons
		val appAssets = CarAppAssetManager(this, "multimedia")
		val images = Utils.loadZipfile(appAssets.getImagesDB("common"))
		for (id in listOf("150.png", "148.png", "152.png", "147.png", "155.png")) {
			viewModel.icons[id] = BitmapFactory.decodeByteArray(images[id], 0, images[id]?.size ?: 0)
		}

		setContentView(R.layout.activity_musicplayer)

		txtAppName.text = musicApp.name
		imgAppIcon.setImageDrawable(musicApp.icon)

		val adapter = MusicPlayerPagerAdapter(supportFragmentManager)

		// set up the paging
		pgrMusicPlayer.adapter = adapter
		pgrMusicPlayer.offscreenPageLimit = 2

		tabMusicPlayer.setupWithViewPager(pgrMusicPlayer)
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
		if (pgrMusicPlayer.currentItem == 2) {
			// go back to the main playback page
			pgrMusicPlayer.currentItem = 0
		}
	}
}

class MusicPlayerPagerAdapter(fm: FragmentManager): FragmentStatePagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
	val tabs = LinkedHashMap<String, Fragment>(3).apply {
		this["Now Playing"] = MusicNowPlayingFragment()
		this["Browse"] = MusicBrowseFragment.newInstance(MusicBrowsePageFragment.newInstance(null))
		this["Queue"] = MusicQueueFragment()
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
