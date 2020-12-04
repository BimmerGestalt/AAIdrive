package me.hufman.androidautoidrive.notifications

import android.content.Context
import android.content.Intent
import com.nhaarman.mockito_kotlin.*
import me.hufman.androidautoidrive.notifications.CarNotificationController
import me.hufman.androidautoidrive.notifications.CarNotificationControllerIntent
import me.hufman.androidautoidrive.notifications.NotificationListenerServiceImpl
import me.hufman.androidautoidrive.notifications.NotificationUpdaterController
import org.junit.Test

class CarNotificationControllerTest  {
	val sentBroadcast = argumentCaptor<Intent>()
	val context: Context = mock {
		on { packageName } doReturn "me.hufman.androidautoidrive"
		on { sendBroadcast(sentBroadcast.capture()) } doAnswer {}
	}
	val sender = CarNotificationControllerIntent(context)
	val receiver = mock<CarNotificationController>()
	val carController = mock<NotificationUpdaterController>()
	val intentReceiver = CarNotificationControllerIntent.Receiver(receiver)
	val listener = NotificationListenerServiceImpl.NotificationInteractionListener(intentReceiver, carController)

	@Test
	fun testRegister() {
		intentReceiver.register(context, mock())
		verify(context).registerReceiver(any(), argThat {hasAction(CarNotificationControllerIntent.INTENT_INTERACTION)})
	}

	@Test
	fun testClear() {
		sender.clear("test")
		listener.onReceive(sentBroadcast.lastValue)
		verify(receiver).clear("test")
		verifyNoMoreInteractions(receiver)
	}

	@Test
	fun testAction() {
		sender.action("test", "action")
		listener.onReceive(sentBroadcast.lastValue)
		verify(receiver).action("test", "action")
		verifyNoMoreInteractions(receiver)
	}

	@Test
	fun testReply() {
		sender.reply("test", "action", "hello")
		listener.onReceive(sentBroadcast.lastValue)
		verify(receiver).reply("test", "action", "hello")
		verifyNoMoreInteractions(receiver)
	}

	@Test
	fun testRequest() {
		listener.onReceive(Intent(NotificationListenerServiceImpl.INTENT_REQUEST_DATA))
		verify(carController).onUpdatedList()
		verifyNoMoreInteractions(carController)
	}
}