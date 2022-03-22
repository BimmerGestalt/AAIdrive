package me.hufman.androidautoidrive.phoneui

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.google.gson.JsonObject
import com.nhaarman.mockito_kotlin.*
import me.hufman.androidautoidrive.CarInformation
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.carapp.CDSDataProvider
import me.hufman.androidautoidrive.phoneui.viewmodels.NavigationStatusModel
import io.bimmergestalt.idriveconnectkit.CDS
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NavigationStatusModelTest {
	@Rule
	@JvmField
	val instantTaskExecutorRule = InstantTaskExecutorRule()

	val context = mock<Context>()
	var isConnected = false
	val capabilities = HashMap<String, String>()
	val cdsData = CDSDataProvider()
	val carInformation = mock<CarInformation> {
		on { isConnected } doAnswer { isConnected }
		on { capabilities } doReturn capabilities
		on { cdsData } doReturn cdsData
	}

	val liveDataObserver = Observer<Boolean> {}

	@Test
	fun testIsConnected() {
		val model = NavigationStatusModel(carInformation, MutableLiveData(false), MutableLiveData(false), MutableLiveData(null)).apply { update() }
		assertEquals(false, model.isConnected.value)

		isConnected = true
		model.update()
		assertEquals(true, model.isConnected.value)
	}

	@Test
	fun testNaviSupported() {
		val model = NavigationStatusModel(carInformation, MutableLiveData(false), MutableLiveData(false), MutableLiveData(null)).apply { update() }
		assertEquals(false, model.isCarNaviSupported.value)
		assertEquals(false, model.isCarNaviNotSupported.value)

		capabilities["navi"] = "false"
		model.update()
		assertEquals(false, model.isCarNaviSupported.value)
		assertEquals(true, model.isCarNaviNotSupported.value)

		capabilities["navi"] = "true"
		model.update()
		assertEquals(true, model.isCarNaviSupported.value)
		assertEquals(false, model.isCarNaviNotSupported.value)
	}

	@Test
	fun testCustomNavNotSupported() {
		val model = NavigationStatusModel(carInformation, MutableLiveData(false), MutableLiveData(true), MutableLiveData(null))
		assertEquals(false, model.isCustomNaviSupported.value)

		model.isCustomNaviSupportedAndPreferred.observeForever(liveDataObserver)
		for (prefer in listOf(false, true)) {
			model.isCustomNaviPreferred.value = prefer
			assertEquals(false, model.isCustomNaviSupportedAndPreferred.value)
		}
	}

	@Test
	fun testCustomNavSupported() {
		val model = NavigationStatusModel(carInformation, MutableLiveData(true), MutableLiveData(true), MutableLiveData(null))
		assertEquals(true, model.isCustomNaviSupported.value)

		model.isCustomNaviSupportedAndPreferred.observeForever(liveDataObserver)
		for (prefer in listOf(false, true)) {
			model.isCustomNaviPreferred.value = prefer
			assertEquals(prefer, model.isCustomNaviSupportedAndPreferred.value)
		}
	}

	@Test
	fun testNavigating() {
		val model = NavigationStatusModel(carInformation, MutableLiveData(false), MutableLiveData(false), MutableLiveData(null)).apply { update() }
		model.carNavigationStatus.observeForever {  }
		assertEquals(false, model.isCarNavigating.value)
		context.run(model.carNavigationStatus.value!!)
		verify(context).getString(R.string.lbl_navigationstatus_inactive)
		verifyNoMoreInteractions(context)
		reset(context)

		cdsData.onPropertyChangedEvent(CDS.NAVIGATION.GUIDANCESTATUS, JsonObject()
				.apply { addProperty("guidanceStatus", 0) })
		assertEquals(false, model.isCarNavigating.value)
		context.run(model.carNavigationStatus.value!!)
		verify(context).getString(R.string.lbl_navigationstatus_inactive)
		verifyNoMoreInteractions(context)
		reset(context)

		cdsData.onPropertyChangedEvent(CDS.NAVIGATION.GUIDANCESTATUS, JsonObject()
				.apply { addProperty("guidanceStatus", 1) })
		assertEquals(true, model.isCarNavigating.value)
		context.run(model.carNavigationStatus.value!!)
		verify(context).getString(R.string.lbl_navigationstatus_active)
		verifyNoMoreInteractions(context)
		reset(context)
	}

	@Test
	fun testDestination() {
		val model = NavigationStatusModel(carInformation, MutableLiveData(false), MutableLiveData(false), MutableLiveData(null)).apply { update() }
		model.carDestinationLabel.observeForever {  }
		assertEquals("", model.carDestinationLabel.value)
		cdsData.onPropertyChangedEvent(CDS.NAVIGATION.NEXTDESTINATION, JsonObject()
				.apply { add("nextDestination", JsonObject()
						.apply { addProperty("name", "test") }
				) }
		)
		assertEquals("test", model.carDestinationLabel.value)
	}
}