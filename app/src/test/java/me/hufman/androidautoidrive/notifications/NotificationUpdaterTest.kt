package me.hufman.androidautoidrive.notifications

import android.content.Context
import android.content.Intent
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.argThat
import com.nhaarman.mockito_kotlin.argumentCaptor
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyNoMoreInteractions
import org.junit.Test

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
        verify(context).registerReceiver(any(), argThat { hasAction(NotificationUpdaterControllerIntent.INTENT_UPDATE_NOTIFICATIONS) }, eq(null), eq(null))
        verify(context).registerReceiver(any(), argThat { hasAction(NotificationUpdaterControllerIntent.INTENT_NEW_NOTIFICATION) }, eq(null), eq(null))
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
