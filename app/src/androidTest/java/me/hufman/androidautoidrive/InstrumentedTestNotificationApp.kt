package me.hufman.androidautoidrive


import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.hufman.androidautoidrive.notifications.NotificationListenerServiceImpl

import org.junit.Test
import org.junit.runner.RunWith

import com.nhaarman.mockito_kotlin.*
import me.hufman.androidautoidrive.notifications.CarNotification
import me.hufman.androidautoidrive.notifications.CarNotificationControllerIntent
import me.hufman.androidautoidrive.carapp.notifications.PhoneNotifications
import me.hufman.androidautoidrive.notifications.NotificationUpdaterControllerIntent
import org.awaitility.Awaitility.await

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
		val appContext = InstrumentationRegistry.getInstrumentation().targetContext

		// prepare to listen to updates from the phone
		val mockListener = mock<PhoneNotifications.PhoneNotificationListener> {}
		val updateListener = NotificationUpdaterControllerIntent.Receiver(mockListener)
		val updateReceiver = object: BroadcastReceiver() {
			override fun onReceive(p0: Context?, p1: Intent?) {
				updateListener.onReceive(p1!!)
			}
		}
		updateListener.register(appContext, updateReceiver, null)

		// send an update from the phone
		val controller = NotificationUpdaterControllerIntent(appContext)
		controller.onUpdatedList()

		// verify that it made it across
		await().untilAsserted { verify(mockListener, times(1)).onUpdatedList() }
		Log.i("Testing", "Finished the tests")
	}

	@Test
	fun testNotificationControl() {
		/** Test that a car button press pokes the phone */
		// Context of the app under test.
		val appContext = InstrumentationRegistry.getInstrumentation().targetContext

		// prepare to listen to the interaction from the car
		val mockListener = mock<NotificationListenerServiceImpl.CarNotificationControllerListener> { }
		val controllerListener = CarNotificationControllerIntent.Receiver(mockListener)
		val interactionListener = NotificationListenerServiceImpl.NotificationInteractionListener(controllerListener, mock())
		val interactionReceiver = object: BroadcastReceiver() {
			override fun onReceive(p0: Context?, p1: Intent?) {
				interactionListener.onReceive(p1!!)
			}
		}
		controllerListener.register(appContext, interactionReceiver)
		appContext.registerReceiver(interactionReceiver, IntentFilter(NotificationListenerServiceImpl.INTENT_REQUEST_DATA))

		// prepare a notification
		val icon = Icon.createWithResource(appContext, R.mipmap.ic_launcher).loadDrawable(appContext)
		val notification = CarNotification(appContext.packageName, "test", icon, true, listOf(),
				"Test", "Test Text", icon, null, null, null, null)

		val carController = CarNotificationControllerIntent(appContext)
		// send an interaction from the car
		carController.clear(notification.key)
		await().untilAsserted { verify(mockListener, times(1)).clear(notification.key) }
		// send a custom action from the car
		carController.action(notification.key, "custom")
		await().untilAsserted { verify(mockListener, times(1)).action(notification.key, "custom") }
		// send a reply from the car
		carController.reply(notification.key, "reply", "text")
		await().untilAsserted { verify(mockListener, times(1)).reply(notification.key, "reply", "text") }
	}
}
