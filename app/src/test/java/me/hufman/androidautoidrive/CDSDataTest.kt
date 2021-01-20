package me.hufman.androidautoidrive

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.google.gson.JsonObject
import com.nhaarman.mockito_kotlin.*
import de.bmw.idrive.BMWRemotingServer
import me.hufman.androidautoidrive.carapp.*
import me.hufman.idriveconnectionkit.CDS
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CDSDataTest {
	@Rule
	@JvmField
	val instantTaskExecutorRule = InstantTaskExecutorRule()

	@Test
	fun testEtchConnection() {
		val etch = mock<BMWRemotingServer> {
			on {cds_create()} doReturn 1
		}
		val connection = CDSConnectionEtch(etch)
		verify(etch).cds_create()
		verifyNoMoreInteractions(etch)
		connection.subscribeProperty(CDS.VEHICLE.VIN, 5000)
		verify(etch).cds_addPropertyChangedEventHandler(1, CDS.VEHICLE.VIN.propertyName, CDS.VEHICLE.VIN.ident.toString(), 5000)
		verify(etch).cds_getPropertyAsync(1, CDS.VEHICLE.VIN.ident.toString(), CDS.VEHICLE.VIN.propertyName)
		verifyNoMoreInteractions(etch)
		connection.unsubscribeProperty(CDS.VEHICLE.VIN)
		verify(etch).cds_removePropertyChangedEventHandler(1, CDS.VEHICLE.VIN.propertyName, CDS.VEHICLE.VIN.ident.toString())
	}

	@Test
	fun testCdsParse() {
		val cdsEventHandler = CDSDataProvider()
		cdsEventHandler.onPropertyChangedEvent("87", "{\"VIN\": \"1234\"}")
		assertEquals("1234", cdsEventHandler[CDS.VEHICLE.VIN]!!["VIN"]?.asString)
	}

	@Test
	fun testCdsParseInvalid() {
		val cdsEventHandler = mock<CDSEventHandler>()
		// null ident
		cdsEventHandler.onPropertyChangedEvent(null, "{\"VIN\": \"1234\"}")
		verifyNoMoreInteractions(cdsEventHandler)
		// invalid ident
		cdsEventHandler.onPropertyChangedEvent("99987", "{\"VIN\": \"1234\"}")
		verifyNoMoreInteractions(cdsEventHandler)
		// null value
		cdsEventHandler.onPropertyChangedEvent("87", null)
		verifyNoMoreInteractions(cdsEventHandler)
		// invalid json
		cdsEventHandler.onPropertyChangedEvent("87", "{\"VIN\": \"1234\"")
		verifyNoMoreInteractions(cdsEventHandler)
		// not a json object
		cdsEventHandler.onPropertyChangedEvent("87", "[\"1234\"]")
		verifyNoMoreInteractions(cdsEventHandler)
	}

	@Test
	fun testSubscriptions() {
		var vin = ""
		val data = mock<CDSData>()
		val subscriptionManager = CDSSubscriptionsManager(data)
		val subscriptions: CDSSubscriptions = subscriptionManager
		subscriptions[CDS.VEHICLE.VIN] = {vin = it["VIN"].asString}
		verify(data).addEventHandler(CDS.VEHICLE.VIN, subscriptions.defaultIntervalLimit, subscriptionManager)
		verifyNoMoreInteractions(data)

		subscriptionManager.onPropertyChangedEvent(CDS.VEHICLE.VIN, JsonObject().apply { addProperty("VIN", "1234") })
		assertEquals("1234", vin)

		subscriptions[CDS.VEHICLE.VIN] = null
		verify(data).removeEventHandler(CDS.VEHICLE.VIN, subscriptionManager)
		verifyNoMoreInteractions(data)
	}

	@Test
	fun testCdsData() {
		val cdsEventHandler = CDSDataProvider()
		val cdsData: CDSData = cdsEventHandler
		cdsEventHandler.onPropertyChangedEvent(CDS.VEHICLE.VIN, JsonObject().apply { addProperty("VIN", "1234") })
		assertEquals("1234", cdsData[CDS.VEHICLE.VIN]!!["VIN"]?.asString)
	}

	@Test
	fun testCdsDataConnection() {
		val connection = mock<CDSConnection>()
		val cdsData = CDSDataProvider()
		cdsData.setConnection(connection)
		val eventHandler = CDSEventHandler { _, _ -> }
		cdsData.addEventHandler(CDS.VEHICLE.VIN, 1000, eventHandler)
		verify(connection).subscribeProperty(CDS.VEHICLE.VIN, 1000)

		// don't change subscription for bigger limit
		cdsData.addEventHandler(CDS.VEHICLE.VIN, 2000, eventHandler)
		verifyNoMoreInteractions(connection)

		// change for smaller limit
		cdsData.addEventHandler(CDS.VEHICLE.VIN, 500, eventHandler)
		verify(connection).subscribeProperty(CDS.VEHICLE.VIN, 500)

		// unsubscribe
		cdsData.removeEventHandler(CDS.VEHICLE.VIN, eventHandler)
		verify(connection).unsubscribeProperty(CDS.VEHICLE.VIN)
	}

	@Test
	fun testCdsDataEvents() {
		var vin = ""
		val cdsEventHandler = CDSDataProvider()
		cdsEventHandler.addEventHandler(CDS.VEHICLE.VIN, 500, CDSEventHandler { property, value ->
			assertEquals(CDS.VEHICLE.VIN, property)
			vin = value["VIN"].asString
		})
		val cdsData: CDSData = cdsEventHandler
		cdsEventHandler.onPropertyChangedEvent(CDS.VEHICLE.VIN, JsonObject().apply { addProperty("VIN", "1234") })
		assertEquals("1234", cdsData[CDS.VEHICLE.VIN]!!["VIN"]?.asString)
		assertEquals("1234", vin)
	}

	@Test
	fun testCdsDataSubscriptions() {
		var vin = ""
		val cdsEventHandler = CDSDataProvider()
		val cdsData: CDSData = cdsEventHandler
		cdsData.subscriptions[CDS.VEHICLE.VIN] = {
			vin = it["VIN"].asString
		}
		cdsEventHandler.onPropertyChangedEvent(CDS.VEHICLE.VIN, JsonObject().apply { addProperty("VIN", "1234") })
		assertEquals("1234", cdsData[CDS.VEHICLE.VIN]!!["VIN"]?.asString)
		assertEquals("1234", vin)
	}

	@Test
	fun testCdsLiveData() {
		var vin = ""
		val connection = mock<CDSConnection>()
		val cdsData = CDSDataProvider()
		cdsData.setConnection(connection)
		val observer = object: Observer<JsonObject> {
			override fun onChanged(t: JsonObject?) {
				t ?: return
				vin = t["VIN"].asString
			}
		}
		val liveData = cdsData.liveData[CDS.VEHICLE.VIN]
		liveData.observeForever(observer)
		assertTrue(liveData.hasObservers())
		verify(connection).subscribeProperty(CDS.VEHICLE.VIN, cdsData.liveData.defaultIntervalLimit)
		cdsData.onPropertyChangedEvent(CDS.VEHICLE.VIN, JsonObject().apply { addProperty("VIN", "1234") })
		assertEquals("1234", cdsData[CDS.VEHICLE.VIN]!!["VIN"]?.asString)
		assertEquals("1234", vin)

		liveData.removeObserver(observer)
		assertFalse(liveData.hasObservers())
		verify(connection).unsubscribeProperty(CDS.VEHICLE.VIN)

		// test pre-existing data
		val mockObserver = mock<Observer<JsonObject>>()
		val languageJson = JsonObject().apply { addProperty("language", 3) }
		cdsData.onPropertyChangedEvent(CDS.VEHICLE.LANGUAGE, languageJson)
		val languageLiveData = cdsData.liveData[CDS.VEHICLE.LANGUAGE]
		assertEquals(languageJson, languageLiveData.value)      // pre-existing data is set at liveData creation
		languageLiveData.observeForever(mockObserver)
		assertEquals(languageJson, languageLiveData.value)
		languageLiveData.removeObserver(mockObserver)
	}
}