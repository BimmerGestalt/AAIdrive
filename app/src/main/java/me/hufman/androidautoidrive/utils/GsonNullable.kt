package me.hufman.androidautoidrive.utils

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import java.lang.ClassCastException

object GsonNullable {
	fun JsonObject.tryAsJsonObject(key: String): JsonObject? =
			try {
				this.getAsJsonObject(key)
			} catch (e: ClassCastException) {null}

	fun JsonObject.tryAsJsonArray(key: String): JsonArray? =
			try {
				this.getAsJsonArray(key)
			} catch (e: ClassCastException) {null}

	fun JsonObject.tryAsJsonPrimitive(key: String): JsonPrimitive? =
			try {
				this.getAsJsonPrimitive(key)
			} catch (e: ClassCastException) {null}

	// Primitive handling
	val JsonPrimitive.tryAsDouble: Double?
		get() =	if (this.isNumber) { this.asDouble } else {null}
	val JsonPrimitive.tryAsInt: Int?
		get() = if (this.isNumber) { this.asInt } else {null}
	val JsonPrimitive.tryAsLong: Long?
		get() = if (this.isNumber) { this.asLong } else {null}
	val JsonPrimitive.tryAsString: String?
		get() = if (this.isString) { this.asString } else {null}
}