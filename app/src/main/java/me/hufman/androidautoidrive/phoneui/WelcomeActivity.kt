package me.hufman.androidautoidrive.phoneui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import kotlinx.android.synthetic.main.activity_welcome.*
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.phoneui.fragments.welcome.*

class WelcomeActivity: AppCompatActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		setContentView(R.layout.activity_welcome)

		val adapter = FirstStartPagerAdapter(supportFragmentManager)
		pgrWelcomeTabs.adapter = adapter
		pgrWelcomeTabs.offscreenPageLimit = 2

		tabWelcomeTabs.setupWithViewPager(pgrWelcomeTabs)
	}

	override fun onResume() {
		super.onResume()

		btnNext.setOnClickListener {
			if (pgrWelcomeTabs.currentItem != (pgrWelcomeTabs.adapter?.count ?: 0) - 1) {
				pgrWelcomeTabs.currentItem = pgrWelcomeTabs.currentItem + 1
			} else {
				AppSettings.saveSetting(this, AppSettings.KEYS.FIRST_START_DONE, "true")
				val intent = Intent(this, NavHostActivity::class.java)
				intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
				startActivity(intent)
				finish()
			}
		}
	}

	override fun onBackPressed() {
		if (pgrWelcomeTabs.currentItem == 0) {
			// pass through default behavior, to close the Activity
			super.onBackPressed()
		} else {
			pgrWelcomeTabs.currentItem = pgrWelcomeTabs.currentItem - 1
		}
	}
}

class FirstStartPagerAdapter(fm: FragmentManager): FragmentStatePagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
	val tabs = arrayOf(
		WelcomeFragment(),
		WelcomeDependenciesFragment(),
		WelcomeMusicFragment(),
		WelcomeNotificationFragment(),
		WelcomeCompleteFragment()
	)

	override fun getCount(): Int {
		return tabs.size
	}

	override fun getPageTitle(position: Int): CharSequence {
		// only show dots, not titles
		return ""
	}

	override fun getItem(index: Int): Fragment {
		return tabs.elementAt(index)
	}
}
