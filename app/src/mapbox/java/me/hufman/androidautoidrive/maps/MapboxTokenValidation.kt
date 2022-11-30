package me.hufman.androidautoidrive.maps

import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.hufman.androidautoidrive.utils.GsonNullable.tryAsJsonPrimitive
import me.hufman.androidautoidrive.utils.GsonNullable.tryAsString
import java.io.IOException
import java.net.URL

object MapboxTokenValidation {
	suspend fun validateToken(token: String): Boolean? {
		return withContext(Dispatchers.IO) {
			// https://stackoverflow.com/a/62025764/169035
			val url = "https://api.mapbox.com/tokens/v2?access_token=$token"

			try {
				val json = JsonParser.parseReader(URL(url).openStream().reader()) as? JsonObject
				val validity = json?.tryAsJsonPrimitive("code")?.tryAsString
				if (validity != null) {
					validity == "TokenValid"
				} else {
					null
				}
			} catch (e: IOException) {
				// no connection
				null
			} catch (e: JsonParseException) {
				// invalid json
				null
			}
		}
	}
}