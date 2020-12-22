package me.hufman.androidautoidrive.carapp

import de.bmw.idrive.BMWRemoting
import de.bmw.idrive.BMWRemotingServer
import me.hufman.idriveconnectionkit.rhmi.RHMIComponent
import me.hufman.idriveconnectionkit.rhmi.RHMIProperty
import java.io.InputStream
import java.security.MessageDigest
import kotlin.math.abs

object RHMIUtils {
	fun rhmi_setResourceCached(connection: BMWRemotingServer, handle: Int, type: BMWRemoting.RHMIResourceType, data: InputStream?): Boolean {
		data ?: return false
		val bytes = data.readBytes()
		return rhmi_setResourceCached(connection, handle, type, bytes)
	}

	fun rhmi_setResourceCached(connection: BMWRemotingServer, handle: Int, type: BMWRemoting.RHMIResourceType, data: ByteArray?): Boolean {
		data ?: return false
		val hash = md5sum(data)
		val cached = connection.rhmi_checkResource(hash, handle, data.size, "", type)
		if (!cached) {
			connection.rhmi_setResource(handle, data, type)
		}
		return cached
	}

	fun md5sum(data: ByteArray): ByteArray {
		val hasher = MessageDigest.getInstance("MD5")
		hasher.update(data)
		return hasher.digest()
	}

	/**
	 * Iterate through the given components to find the component that is
	 * horizontally aligned, to the right, to the component matched by the predicate
	 */
	fun findAdjacentComponent(components: Iterable<RHMIComponent>, nearTo: RHMIComponent?): RHMIComponent? {
		nearTo ?: return null
		val layout = getComponentLayout(nearTo)
		val nearToLocation = getComponentLocation(nearTo, layout)
		val neighbors = components.filter {
			val location = getComponentLocation(it, layout)
			abs(nearToLocation.second - location.second) < 10 && // same height
			nearToLocation.first < location.first    // first component left of second
		}
		return neighbors.minByOrNull {
			getComponentLocation(it, layout).first
		}
	}

	fun getComponentLayout(component: RHMIComponent): Int {
		val xProperty = component.properties[RHMIProperty.PropertyId.POSITION_X.id]
		return if (xProperty is RHMIProperty.LayoutBag) {
			xProperty.values.keys.firstOrNull { (xProperty.values[it] as Int) < 1600 } ?: 0
		} else {
			0
		}
	}
	fun getComponentLocation(component: RHMIComponent, layout: Int = 0): Pair<Int, Int> {
		val xProperty = component.properties[RHMIProperty.PropertyId.POSITION_X.id]
		val yProperty = component.properties[RHMIProperty.PropertyId.POSITION_Y.id]
		val x = when (xProperty) {
			is RHMIProperty.SimpleProperty -> xProperty.value as Int
			is RHMIProperty.LayoutBag -> xProperty.get(layout) as Int
			else -> -1
		}
		val y = when (yProperty) {
			is RHMIProperty.SimpleProperty -> yProperty.value as Int
			is RHMIProperty.LayoutBag -> yProperty.get(layout) as Int
			else -> -1
		}
		return Pair(x,y)
	}

}