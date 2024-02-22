package me.hufman.androidautoidrive

import java.lang.reflect.Field
import java.lang.reflect.Method

fun getModifiersField(): Field? {
	// https://stackoverflow.com/a/74727966/169035
	// https://github.com/prestodb/presto/pull/15240/files
	return try {
		Field::class.java.getDeclaredField("modifiers")
	} catch (e: NoSuchFieldException) {
		try {
			val getDeclaredFields0: Method = Class::class.java.getDeclaredMethod(
				"getDeclaredFields0",
				Boolean::class.javaPrimitiveType
			)
			getDeclaredFields0.isAccessible = true
			val fields = getDeclaredFields0.invoke(Field::class.java, false) as Array<Field>
			for (field in fields) {
				if ("modifiers" == field.name) {
					return field
				}
			}
		} catch (ex: ReflectiveOperationException) {
			e.addSuppressed(ex)
		}
		throw e
	}
}
