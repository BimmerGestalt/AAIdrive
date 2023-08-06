package me.hufman.androidautoidrive.phoneui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.viewpager.widget.ViewPager
import com.google.android.material.tabs.TabLayout
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.phoneui.fragments.welcome.*

class WelcomeActivity: AppCompatActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		setContentView(R.layout.activity_welcome)

		val pgrWelcomeTabs = findViewById<ViewPager>(R.id.pgrWelcomeTabs)
		val adapter = FirstStartPagerAdapter(supportFragmentManager)
		pgrWelcomeTabs.adapter = adapter

		findViewById<TabLayout>(R.id.tabWelcomeTabs).setupWithViewPager(pgrWelcomeTabs)

		onBackPressedDispatcher.addCallback {
			if (pgrWelcomeTabs.currentItem == 0) {
				// pass through default behavior, to close the Activity
				this.isEnabled = false
				onBackPressedDispatcher.onBackPressed()
			} else {
				pgrWelcomeTabs.currentItem = pgrWelcomeTabs.currentItem - 1
			}
		}
	}

	override fun onResume() {
		super.onResume()

		findViewById<Button>(R.id.btnNext).setOnClickListener {
			val pgrWelcomeTabs = findViewById<ViewPager>(R.id.pgrWelcomeTabs)
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
}

class FirstStartPagerAdapter(fm: FragmentManager): FragmentStatePagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
	val tabs = arrayOf(
		WelcomeFragment(),
		WelcomeDependenciesFragment(),
		WelcomeNotificationFragment(),
		WelcomeMusicFragment(),
		if (WelcomeAnalyticsFragment.isSupported()) WelcomeAnalyticsFragment() else null,
		WelcomeCompleteFragment()
	).filterNotNull()

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
