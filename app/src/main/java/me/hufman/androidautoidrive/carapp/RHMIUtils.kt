package me.hufman.androidautoidrive.carapp

import de.bmw.idrive.BMWRemoting
import de.bmw.idrive.BMWRemotingServer
import java.io.InputStream
import java.security.MessageDigest

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
}