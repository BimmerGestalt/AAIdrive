package me.hufman.androidautoidrive.notifications

import android.content.Context
import android.content.Intent
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.argThat
import com.nhaarman.mockito_kotlin.argumentCaptor
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyNoMoreInteractions
import org.junit.Test

class CarNotificationControllerTest {
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
        verify(context).registerReceiver(any(), argThat { hasAction(CarNotificationControllerIntent.INTENT_INTERACTION) })
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
