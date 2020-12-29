package me.hufman.androidautoidrive.phoneui

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.LiveData
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import me.hufman.androidautoidrive.phoneui.viewmodels.PermissionsModel
import me.hufman.androidautoidrive.phoneui.viewmodels.PermissionsState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PermissionsModelTest {
	@Rule
	@JvmField
	val instantTaskExecutorRule = InstantTaskExecutorRule()

	val notificationListenerState = mock<LiveData<Boolean>>()
	val state = mock<PermissionsState>()
	val viewModel = PermissionsModel(notificationListenerState, state)

	@Test
	fun testModelNotification() {
		whenever(notificationListenerState.value) doReturn true
		listOf(true, false).forEach {
			whenever(state.hasNotificationPermission) doReturn it
			viewModel.update()
			assertEquals(it, viewModel.hasNotificationPermission.value)
		}

		whenever(notificationListenerState.value) doReturn false
		whenever(state.hasNotificationPermission) doReturn true
		viewModel.update()
		assertEquals(false, viewModel.hasNotificationPermission.value)
	}

	@Test
	fun testModelSms() {
		listOf(true, false).forEach {
			whenever(state.hasSmsPermission) doReturn it
			viewModel.update()
			assertEquals(it, viewModel.hasSmsPermission.value)
		}
	}

	@Test
	fun testModelLocation() {
		listOf(true, false).forEach {
			whenever(state.hasLocationPermission) doReturn it
			viewModel.update()
			assertEquals(it, viewModel.hasLocationPermission.value)
		}
	}
}