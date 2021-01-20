package me.hufman.androidautoidrive.phoneui

import android.content.Intent
import android.os.Bundle
import android.view.MenuInflater
import android.view.View
import android.widget.PopupMenu
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Observer
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import kotlinx.android.synthetic.main.activity_navhost.*
import me.hufman.androidautoidrive.*
import me.hufman.androidautoidrive.databinding.NavHeaderBinding
import me.hufman.androidautoidrive.phoneui.viewmodels.ConnectionStatusModel
import java.lang.IllegalStateException

class NavHostActivity: AppCompatActivity() {

	private val connectionViewModel by viewModels<ConnectionStatusModel> { ConnectionStatusModel.Factory(this.applicationContext) }

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		Analytics.init(this)
		AppSettings.loadSettings(this)
		L.loadResources(this)
		CarInformation.loadCache(MutableAppSettingsReceiver(applicationContext))

		setContentView(R.layout.activity_navhost)
		val navToolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.nav_toolbar)!!
		setSupportActionBar(navToolbar)

		// Set each of the menu entries as a top level destination
		val navMenu = PopupMenu(this, null).menu.also {
			MenuInflater(this).inflate(R.menu.menu_main, it)
		}
		val drawerLayout = findViewById<View>(R.id.drawer_layout) as? DrawerLayout
		val appBarConfig = AppBarConfiguration(navMenu, drawerLayout)
		val navController = findNavController(R.id.nav_host_fragment)
		setupActionBarWithNavController(navController, appBarConfig)        // title updater
		navToolbar.setupWithNavController(navController, appBarConfig)     // hamburger click handler
		nav_view.setupWithNavController(navController)                      // nav menu click handler

		setupNavHeader()

		setupNavMenu()

		startService()

		drawerLayout?.post { drawerLayout.openDrawer(GravityCompat.START) }
	}

	fun setupNavHeader() {
		val viewModel  by viewModels<ConnectionStatusModel> { ConnectionStatusModel.Factory(this.applicationContext) }
		val binding = NavHeaderBinding.inflate(layoutInflater, nav_view, false)
		binding.lifecycleOwner = this
		binding.viewModel = viewModel
		nav_view.removeHeaderView(nav_view.getHeaderView(0))
		nav_view.addHeaderView(binding.root)

		val paneConnectionStatus = nav_view.getHeaderView(0).findViewById<View>(R.id.paneConnectionStatus)
		paneConnectionStatus.setOnClickListener {
			findNavController(R.id.nav_host_fragment).navigate(R.id.nav_connection)
			val drawerLayout = findViewById<DrawerLayout>(R.id.drawer_layout)
			drawerLayout?.closeDrawer(GravityCompat.START)
		}
	}

	fun setupNavMenu() {
		val themeAttrs = obtainStyledAttributes(R.style.optionGmapVisible, arrayOf(android.R.attr.visibility).toIntArray())
		val mapVisibility = themeAttrs.getInt(0, 0)
		themeAttrs.recycle()
		nav_view.menu.findItem(R.id.nav_maps).isVisible = mapVisibility == View.VISIBLE

		val advancedSetting = BooleanLiveSetting(this, AppSettings.KEYS.SHOW_ADVANCED_SETTINGS)
		advancedSetting.observe(this, Observer {
			nav_view.menu.findItem(R.id.nav_connection).isVisible = it
		})
	}

	fun startService() {
		try {
			this.startService(Intent(this, MainService::class.java).setAction(MainService.ACTION_START))
		} catch (e: IllegalStateException) {
			// Android Oreo strenuously objects to starting the service if the activity isn't visible
			// for example, when Android Studio tries to start the Activity with the screen off
		}
	}
}