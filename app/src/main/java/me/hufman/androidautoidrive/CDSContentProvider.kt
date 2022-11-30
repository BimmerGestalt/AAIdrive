package me.hufman.androidautoidrive

import android.content.ContentProvider
import android.content.ContentValues
import android.database.AbstractCursor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.BaseColumns
import com.google.gson.JsonObject
import io.bimmergestalt.idriveconnectkit.CDSProperty
import me.hufman.androidautoidrive.carapp.CDSData
import me.hufman.androidautoidrive.carapp.CDSDataProvider
import me.hufman.androidautoidrive.carapp.CDSEventHandler
import java.util.*

class CDSContentProvider: ContentProvider(), CDSEventHandler {
	companion object {
		private const val TAG = "CDSContentProvider"
		private val QUERY_CAPABILITIES_ID = Regex("/capabilities/([a-z.]+)")
		private val QUERY_CDS_ID = Regex("/cds/(\\d+)")

		// how long to wait on first subscription before returning NULL
		private const val INITIAL_FETCH_TIMEOUT = 1500
		// how long to wait before unsubscribing an idle CDS property
		private const val IDLE_UNSUBSCRIBE_TIMEOUT = 10000

		const val CONTENT_PROVIDER_AUTHORITY = "io.bimmergestalt.cardata.provider"
		const val CONTENT_PROVIDER_CAPABILITIES = "content://$CONTENT_PROVIDER_AUTHORITY/capabilities"
		const val CONTENT_PROVIDER_CDS = "content://$CONTENT_PROVIDER_AUTHORITY/cds"

		val publicCapabilties = setOf(
				"a4axl",
				"alignment_right",
				"hmi.display-height",
				"hmi.display-width",
				"hmi.role",
				"hmi.type",
				"map",
				"navi",
				"speech2text",
				"speedlock",
				"tts",
				"voice",
		)
		val privateCdsProperties = setOf(
				CDSProperty.COMMUNICATION_CURRENTCALLINFO,
				CDSProperty.COMMUNICATION_LASTCALLINFO,
				CDSProperty.NAVIGATION_CURRENTPOSITIONDETAILEDINFO,
				CDSProperty.NAVIGATION_FINALDESTINATION,
				CDSProperty.NAVIGATION_FINALDESTINATIONDETAILEDINFO,
				CDSProperty.NAVIGATION_GPSEXTENDEDINFO,
				CDSProperty.NAVIGATION_GPSPOSITION,
				CDSProperty.NAVIGATION_GUIDANCESTATUS,
				CDSProperty.NAVIGATION_NEXTDESTINATION,
				CDSProperty.NAVIGATION_NEXTDESTINATIONDETAILEDINFO,
				CDSProperty.VEHICLE_VIN,
		)
	}
	private var carInformation = CarInformation()
	private val cdsData = CDSDataProvider()

	private val latestQuery: MutableMap<CDSProperty, Long> = EnumMap(CDSProperty::class.java)
	private val latestUpdate: MutableMap<CDSProperty, Long> = EnumMap(CDSProperty::class.java)

	override fun onCreate(): Boolean {
		cdsData.setConnection(CarInformation.cdsData.asConnection(cdsData))
		return true
	}

	override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
		val path = uri.path ?: ""
		if (path.startsWith("/capabilities")) {
			val nameMatch = QUERY_CAPABILITIES_ID.matchEntire(path)
			return queryCapabilities(nameMatch?.groups?.get(1)?.value)
		} else if (path.startsWith("/cds")) {
			val idMatch = QUERY_CDS_ID.matchEntire(path)
			val property = CDSProperty.fromIdent(idMatch?.groups?.get(1)?.value ?: "")
			if (property != null) {
				return queryCds(property)
			}
			throw IllegalArgumentException()
		}
		return null
	}

	private fun queryCapabilities(name: String?): Cursor {
		if (name != null && !publicCapabilties.contains(name)) {
			throw IllegalArgumentException()
		}
		val results = MatrixCursor(arrayOf("name", "value"))
		carInformation.capabilities.filter {
			publicCapabilties.contains(it.key) &&
				(name == null || name == it.key)
		}.forEach {
			results.addRow(arrayOf(it.key, it.value))
		}
		results.moveToFirst()
		return results
	}

	private fun queryCds(property: CDSProperty): Cursor {
		latestQuery[property] = System.currentTimeMillis()
		cdsData.addEventHandler(property, 100, this)
		val cursor = CDSCursor(cdsData, property)
		// wait for initial data
		val timeout = System.currentTimeMillis() + INITIAL_FETCH_TIMEOUT
		while (cdsData[property] == null && System.currentTimeMillis() < timeout) {
			Thread.sleep(100)
		}
		return cursor
	}

	override fun getType(uri: Uri): String? {
		val path = (uri.path ?: "").trimEnd('/')
		return when {
			path == "/capabilities" -> { "vnd.android.cursor.dir/vnd.io.bimmergestalt.cardata.provider.capabilities" }
			path.startsWith("/capabilities/") ->  { "vnd.android.cursor.item/vnd.io.bimmergestalt.cardata.provider.capabilities" }
			path == "/cds" -> { "vnd.android.cursor.dir/vnd.io.bimmergestalt.cardata.provider.cds" }
			path.startsWith("/cds/") -> { "vnd.android.cursor.item/vnd.io.bimmergestalt.cardata.provider.cds" }
			else -> { null }
		}
	}

	override fun insert(uri: Uri, values: ContentValues?): Uri? {
		return null     // no new row
	}

	override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
		return 0    // no rows altered
	}

	override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
		return 0    // no rows altered
	}

	override fun onPropertyChangedEvent(property: CDSProperty, propertyValue: JsonObject) {
		if ((latestUpdate[property] ?: 0) > (latestQuery[property] ?: 0) + IDLE_UNSUBSCRIBE_TIMEOUT) {
			// we previously announced the change but no query happened, unsubscribe
			cdsData.removeEventHandler(property, this)
		}
		val uri = Uri.parse("$CONTENT_PROVIDER_CDS/${property.ident}")
		context?.contentResolver?.notifyChange(uri, null)
		latestUpdate[property] = System.currentTimeMillis()
	}
}

class CDSCursor(val cdsData: CDSData, val property: CDSProperty): AbstractCursor() {

	override fun getCount(): Int = 1

	override fun getColumnNames(): Array<String> {
		return arrayOf(BaseColumns._ID, "name", "value")
	}

	override fun getType(column: Int): Int {
		return when (column) {
			0 -> Cursor.FIELD_TYPE_INTEGER
			1 -> Cursor.FIELD_TYPE_STRING
			2 -> Cursor.FIELD_TYPE_STRING
			else -> throw IllegalArgumentException()
		}
	}

	override fun getString(column: Int): String? {
		return when (column) {
			1 -> property.propertyName
			2 -> cdsData[property]?.toString()
			else -> throw IllegalArgumentException()
		}
	}

	override fun getShort(column: Int): Short = getInt(column).toShort()

	override fun getInt(column: Int): Int {
		return when (column) {
			0 -> property.ident
			else -> throw IllegalArgumentException()
		}
	}

	override fun getLong(column: Int): Long = getInt(column).toLong()

	override fun getFloat(column: Int): Float = throw IllegalArgumentException()

	override fun getDouble(column: Int): Double = throw IllegalArgumentException()

	override fun isNull(column: Int): Boolean {
		return when (column) {
			0 -> false
			1 -> false
			2 -> cdsData[property] == null
			else -> throw IllegalArgumentException()
		}
	}
}