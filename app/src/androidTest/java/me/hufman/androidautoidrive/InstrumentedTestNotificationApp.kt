package me.hufman.androidautoidrive


import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import android.util.Log
import me.hufman.androidautoidrive.carapp.notifications.NotificationListenerServiceImpl

import org.junit.Test
import org.junit.runner.RunWith

import com.nhaarman.mockito_kotlin.*
import me.hufman.androidautoidrive.carapp.notifications.CarNotification
import me.hufman.androidautoidrive.carapp.notifications.CarNotificationControllerIntent
import me.hufman.androidautoidrive.carapp.notifications.PhoneNotifications
import me.hufman.idriveconnectionkit.android.IDriveConnectionListener
import org.awaitility.Awaitility.await
import org.junit.After
import org.junit.Before

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class InstrumentedTestNotificationApp {

	@Test
	fun testNotificationUpdate() {
		/** Test that a new notification pokes the car */
		// Context of the app under test.
		val appContext = InstrumentationRegistry.getTargetContext()

		// prepare to listen to updates from the phone
		val mockListener = mock<PhoneNotifications.PhoneNotificationListener> {}
		val updateListener = PhoneNotifications.PhoneNotificationUpdate(mockListener)
		appContext.registerReceiver(updateListener, IntentFilter(PhoneNotifications.INTENT_NEW_NOTIFICATION))
		appContext.registerReceiver(updateListener, IntentFilter(PhoneNotifications.INTENT_UPDATE_NOTIFICATIONS))

		// prepare a notification
		val icon = Icon.createWithResource(appContext, R.mipmap.ic_launcher)
		val notification = CarNotification(appContext.packageName, "test", icon, true, arrayOf(),
				"Test", "Test Text", null, null)

		// send an update from the phone
		val controller = NotificationListenerServiceImpl.NotificationUpdater(appContext)
		controller.sendNotificationList()

		// verify that it made it across
		await().untilAsserted { verify(mockListener, times(1)).updateNotificationList() }
		Log.i("Testing", "Finished the tests")
	}

	@Test
	fun testNotificationControl() {
		/** Test that a car button press pokes the phone */
		// Context of the app under test.
		val appContext = InstrumentationRegistry.getTargetContext()

		// prepare to listen to the interaction from the car
		val mockListener = mock<NotificationListenerServiceImpl.InteractionListener> { }
		val controller = NotificationListenerServiceImpl.NotificationUpdater(appContext)
		val interactionListener = NotificationListenerServiceImpl.IDriveNotificationInteraction(mockListener, controller)
		appContext.registerReceiver(interactionListener, IntentFilter(NotificationListenerServiceImpl.INTENT_INTERACTION))
		appContext.registerReceiver(interactionListener, IntentFilter(NotificationListenerServiceImpl.INTENT_REQUEST_DATA))

		// prepare a notification
		val icon = Icon.createWithResource(appContext, R.mipmap.ic_launcher)
		val notification = CarNotification(appContext.packageName, "test", icon, true, arrayOf(),
				"Test", "Test Text", null, null)

		val carController = CarNotificationControllerIntent(appContext)
		// send an interaction from the car
		carController.clear(notification)
		await().untilAsserted { verify(mockListener, times(1)).cancelNotification(notification.key) }
		// send a custom action from the car
		carController.action(notification, "custom")
		await().untilAsserted { verify(mockListener, times(1)).sendNotificationAction(notification.key, "custom") }

	}
}
