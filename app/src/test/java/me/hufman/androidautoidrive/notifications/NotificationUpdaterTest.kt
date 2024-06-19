package me.hufman.androidautoidrive.notifications

import android.content.Context
import android.content.Intent
import org.mockito.kotlin.*
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString

class NotificationUpdaterTest {
	val sentBroadcast = argumentCaptor<Intent>()
	val context: Context = mock {
		on { packageName } doReturn "me.hufman.androidautoidrive"
		on { sendBroadcast(sentBroadcast.capture()) } doAnswer {}
	}

	val sender = NotificationUpdaterControllerIntent(context)
	val receiver = mock<NotificationUpdaterController>()
	val listener = NotificationUpdaterControllerIntent.Receiver(receiver)

	@Test
	fun testRegister() {
		listener.register(context, mock(), null)
		verify(context).registerReceiver(any(), argThat {hasAction(NotificationUpdaterControllerIntent.INTENT_UPDATE_NOTIFICATIONS)}, anyString(), eq(null))
		verify(context).registerReceiver(any(), argThat {hasAction(NotificationUpdaterControllerIntent.INTENT_NEW_NOTIFICATION)}, anyString(), eq(null))
	}

	@Test
	fun testOnUpdatedList() {
		sender.onUpdatedList()
		listener.onReceive(sentBroadcast.lastValue)
		verify(receiver).onUpdatedList()
		verifyNoMoreInteractions(receiver)
	}

	@Test
	fun testOnNewNotification() {
		sender.onNewNotification("test")
		listener.onReceive(sentBroadcast.lastValue)
		verify(receiver).onNewNotification("test")
		verifyNoMoreInteractions(receiver)
	}
}