package me.hufman.androidautoidrive.phoneui.controllers

import androidx.viewpager.widget.ViewPager
import me.hufman.androidautoidrive.music.MusicMetadata
import me.hufman.androidautoidrive.phoneui.MusicPlayerPagerAdapter
import me.hufman.androidautoidrive.phoneui.fragments.MusicBrowseFragment
import me.hufman.androidautoidrive.phoneui.fragments.MusicBrowsePageFragment

class MusicPlayerController(var viewPager: ViewPager?) {

	fun showNowPlaying() {
		viewPager?.currentItem = 0
	}

	fun showBrowse() {
		viewPager?.currentItem = 1
	}

	fun pushBrowse(directory: MusicMetadata?) {
		val adapter = viewPager?.adapter as? MusicPlayerPagerAdapter ?: return
		val container = adapter.getItem(1) as MusicBrowseFragment
		container.replaceFragment(MusicBrowsePageFragment.newInstance(directory), true)
	}
}