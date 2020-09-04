package me.hufman.androidautoidrive.phoneui

import android.Manifest
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.app.NotificationManagerCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_notifications.*
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.MutableAppSettings
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.carapp.notifications.WordListPersistence

class ConfigureNotificationsActivity: AppCompatActivity() {

	val appSettings = MutableAppSettings(this)

	private lateinit var languageChoices: ArrayAdapter<String>
	private lateinit var languageLoader: DictionaryListTask
	private var languageDownloader: DictionaryDownloadTask? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		setContentView(R.layout.activity_notifications)

		swNotificationPopup.setOnCheckedChangeListener { buttonView, isChecked ->
			appSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP] = isChecked.toString()
			redraw()
		}
		swNotificationPopupPassenger.setOnCheckedChangeListener { buttonView, isChecked ->
			appSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP_PASSENGER] = isChecked.toString()
		}
		btnGrantSMS.setOnClickListener {
			ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_SMS), 20)
		}

		// spawn a Test notification
		btnTestNotification.setOnClickListener {
			createNotificationChannel()
			val actionIntent = Intent(this, CustomActionListener::class.java)
			val replyInput = RemoteInput.Builder("reply")
					.setChoices(arrayOf("Yes", "No", "\uD83D\uDC4C"))
					.build()

			val action = Notification.Action.Builder(null, "Custom Action",
					PendingIntent.getBroadcast(this, 0, actionIntent, PendingIntent.FLAG_UPDATE_CURRENT))
					.build()
			val inputAction = Notification.Action.Builder(null, "Reply",
					PendingIntent.getBroadcast(this, 1, actionIntent, PendingIntent.FLAG_UPDATE_CURRENT))
					.addRemoteInput(replyInput)
					.build()
			val notificationBuilder = Notification.Builder(this)
					.setSmallIcon(android.R.drawable.ic_menu_gallery)
					.setContentTitle("Test Notification")
					.setContentText("This is a test notification \ud83d\udc4d")
					.setSubText("SubText")
					.addAction(action)
					.addAction(inputAction)
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				notificationBuilder.setChannelId(MainActivity.NOTIFICATION_CHANNEL_ID)
			}
			val notification = notificationBuilder.build()
			val manager = NotificationManagerCompat.from(this)
			manager.notify(1, notification)
		}

		languageChoices = ArrayAdapter(this, android.R.layout.simple_spinner_item)
		languageChoices.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
		languageChoices.add("None")
		val selectedLanguage = AppSettings[AppSettings.KEYS.INPUT_COMPLETION_LANGUAGE]
		if (selectedLanguage.isNotBlank() && selectedLanguage != languageChoices.getItem(0)) {
			languageChoices.add(selectedLanguage)
		}
		spnLanguageChoice.adapter = languageChoices
		spnLanguageChoice.setSelection(spnLanguageChoice.adapter.count-1)
		spnLanguageChoice.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
			override fun onNothingSelected(p0: AdapterView<*>?) { }
			override fun onItemSelected(p0: AdapterView<*>?, p1: View?, index: Int, p3: Long) {
				p0 ?: return
				val item = p0.adapter.getItem(index) as? String ?: return
				onDictionaryChoice(item)
			}
		}
		languageLoader = DictionaryListTask(this)
		languageLoader.execute()
	}

	override fun onResume() {
		super.onResume()

		redraw()
		appSettings.callback = { redraw() }
	}

	override fun onPause() {
		super.onPause()
		appSettings.callback = null
		languageLoader.cancel(false)
		languageDownloader?.cancel(false)
	}

	fun hasSMSPermission(): Boolean {
		return (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED)
	}

	fun redraw() {
		swNotificationPopup.isChecked = appSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP].toBoolean()
		paneNotificationPopup.visible = appSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP].toBoolean()
		swNotificationPopupPassenger.isChecked = appSettings[AppSettings.KEYS.ENABLED_NOTIFICATIONS_POPUP_PASSENGER].toBoolean()
		paneSMSPermission.visible = !hasSMSPermission()
	}

	private fun createNotificationChannel() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val channel = NotificationChannel(MainActivity.NOTIFICATION_CHANNEL_ID,
					MainActivity.NOTIFICATION_CHANNEL_NAME,
					NotificationManager.IMPORTANCE_DEFAULT)

			val notificationManager = getSystemService(NotificationManager::class.java)
			notificationManager.createNotificationChannel(channel)
		}
	}

	fun setDictionaryChoices(languages: List<String>) {
		languageChoices.clear()
		languageChoices.add("None")
		languageChoices.addAll(languages.sorted())
		val position = languageChoices.getPosition(AppSettings[AppSettings.KEYS.INPUT_COMPLETION_LANGUAGE])
		if (position >= 0) {
			spnLanguageChoice.setSelection(position)
		}
		spnLanguageChoice.invalidate()
	}

	fun onDictionaryChoice(language: String) {
		AppSettings.saveSetting(this@ConfigureNotificationsActivity, AppSettings.KEYS.INPUT_COMPLETION_LANGUAGE, language)
		if (language != languageChoices.getItem(0)) {
			val loader = WordListPersistence(this@ConfigureNotificationsActivity)
			if (!loader.isLanguageDownloaded(language) && languageDownloader == null) {
				languageDownloader = DictionaryDownloadTask(this@ConfigureNotificationsActivity)
				languageDownloader?.execute(language)
			}
		}
	}

	class CustomActionListener: BroadcastReceiver() {
		override fun onReceive(context: Context?, intent: Intent?) {
			if (intent != null) {
				if (RemoteInput.getResultsFromIntent(intent) != null) {
					val reply = RemoteInput.getResultsFromIntent(intent)
					Log.i(MainActivity.TAG, "Received reply")
					Toast.makeText(context, "Reply: ${reply.getCharSequence("reply")}", Toast.LENGTH_SHORT).show()

					val manager = NotificationManagerCompat.from(context!!)
					manager.cancel(1)
				} else {
					Log.i(MainActivity.TAG, "Received custom action")
					Toast.makeText(context, "Custom Action press", Toast.LENGTH_SHORT).show()
				}
			}
		}
	}

	class DictionaryListTask(val parent: ConfigureNotificationsActivity): AsyncTask<Void, Void, List<String>>() {
		override fun doInBackground(vararg p0: Void?): List<String> {
			val loader = WordListPersistence(parent)
			return loader.discover()
		}

		override fun onPostExecute(result: List<String>?) {
			result ?: return
			parent.setDictionaryChoices(result)
		}
	}

	class DictionaryDownloadTask(val parent: ConfigureNotificationsActivity): AsyncTask<String, Void, Unit>() {
		override fun onPreExecute() {
			parent.prgDownloading.visibility = View.VISIBLE
		}

		override fun doInBackground(vararg p0: String?): Unit {
			val language = p0.getOrNull(0) ?: return
			val loader = WordListPersistence(parent)
			loader.download(language)
		}

		override fun onPostExecute(result: Unit?) {
			parent.prgDownloading.visibility = View.INVISIBLE
			parent.languageDownloader = null
		}
	}
}