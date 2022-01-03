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
import me.hufman.androidautoidrive.BuildConfig
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
		val model = NavigationStatusModel(carInformation, MutableLiveData(false), MutableLiveData(false)).apply { update() }
		assertEquals(false, model.isConnected.value)

		isConnected = true
		model.update()
		assertEquals(true, model.isConnected.value)
	}

	@Test
	fun testNaviSupported() {
		val model = NavigationStatusModel(carInformation, MutableLiveData(false), MutableLiveData(false)).apply { update() }
		assertEquals(false, model.isNaviSupported.value)
		assertEquals(false, model.isNaviNotSupported.value)

		capabilities["navi"] = "false"
		model.update()
		assertEquals(false, model.isNaviSupported.value)
		assertEquals(true, model.isNaviNotSupported.value)

		capabilities["navi"] = "true"
		model.update()
		assertEquals(true, model.isNaviSupported.value)
		assertEquals(false, model.isNaviNotSupported.value)
	}

	@Test
	fun testCustomNavNotSupported() {
		val model = NavigationStatusModel(carInformation, MutableLiveData(false), MutableLiveData(true))
		assertEquals(false, model.isCustomNaviSupported.value)

		model.isCustomNaviSupportedAndPreferred.observeForever(liveDataObserver)
		for (prefer in listOf(false, true)) {
			model.isCustomNaviPreferred.value = prefer
			assertEquals(false, model.isCustomNaviSupportedAndPreferred.value)
		}
	}

	@Test
	fun testCustomNavSupported() {
		val model = NavigationStatusModel(carInformation, MutableLiveData(true), MutableLiveData(true))
		assertEquals(true, model.isCustomNaviSupported.value)

		model.isCustomNaviSupportedAndPreferred.observeForever(liveDataObserver)
		for (prefer in listOf(false, true)) {
			model.isCustomNaviPreferred.value = prefer
			assertEquals(prefer, model.isCustomNaviSupportedAndPreferred.value)
		}
	}

	@Test
	fun testNavigating() {
		val model = NavigationStatusModel(carInformation, MutableLiveData(false), MutableLiveData(false)).apply { update() }
		model.navigationStatus.observeForever {  }
		assertEquals(false, model.isNavigating.value)
		context.run(model.navigationStatus.value!!)
		verify(context).getString(R.string.lbl_navigationstatus_inactive)
		verifyNoMoreInteractions(context)
		reset(context)

		cdsData.onPropertyChangedEvent(CDS.NAVIGATION.GUIDANCESTATUS, JsonObject()
				.apply { addProperty("guidanceStatus", 0) })
		assertEquals(false, model.isNavigating.value)
		context.run(model.navigationStatus.value!!)
		verify(context).getString(R.string.lbl_navigationstatus_inactive)
		verifyNoMoreInteractions(context)
		reset(context)

		cdsData.onPropertyChangedEvent(CDS.NAVIGATION.GUIDANCESTATUS, JsonObject()
				.apply { addProperty("guidanceStatus", 1) })
		assertEquals(true, model.isNavigating.value)
		context.run(model.navigationStatus.value!!)
		verify(context).getString(R.string.lbl_navigationstatus_active)
		verifyNoMoreInteractions(context)
		reset(context)
	}

	@Test
	fun testDestination() {
		val model = NavigationStatusModel(carInformation, MutableLiveData(false), MutableLiveData(false)).apply { update() }
		model.destination.observeForever {  }
		assertEquals("", model.destination.value)
		cdsData.onPropertyChangedEvent(CDS.NAVIGATION.NEXTDESTINATION, JsonObject()
				.apply { add("nextDestination", JsonObject()
						.apply { addProperty("name", "test") }
				) }
		)
		assertEquals("test", model.destination.value)
	}
}