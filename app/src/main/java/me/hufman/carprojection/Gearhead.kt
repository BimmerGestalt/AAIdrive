package me.hufman.carprojection

import android.content.Context
import android.content.pm.PackageInfo
import android.os.IBinder
import android.os.IInterface
import android.os.Parcelable

object Gearhead {
	val gearheadPackageName = "com.google.android.projection.gearhead"
	val defaultClassPackage = "com.google.android.gms.car"

	/** Get a context for Gearhead classloader */
	fun getContext(context: Context): Context {
		return context.createPackageContext(gearheadPackageName, Context.CONTEXT_IGNORE_SECURITY or Context.CONTEXT_INCLUDE_CODE)
	}
	fun getClassLoader(context: Context): ClassLoader {
		return getContext(context).classLoader
	}

	fun getPackageInfo(context: Context): PackageInfo {
		return context.packageManager.getPackageInfo(Gearhead.gearheadPackageName, 0)

	}

	/** Expands a class name to have a full package prefix */
	fun expandClassName(name: String): String {
		return if (name.startsWith("com")) {
			name
		} else {
			return "$defaultClassPackage.$name"
		}
	}

	/** Fetch the Class of a Gearhead parcelable object */
	fun getParcelableClass(context: Context, name: String): Class<Parcelable> {
		return getContext(context).classLoader.loadClass(expandClassName(name)) as Class<Parcelable>
	}
	/** Create an instance of a Gearhead parcelable object */
	fun createParcelable(context: Context, name: String, vararg args: Any): Parcelable {
		val target = getParcelableClass(context, name)
		val types = args.map { it::class.javaPrimitiveType ?: it::class.java}.toTypedArray()
		val constructor = target.getConstructor(*types)
		return constructor.newInstance(*args)
	}

	/** Fetch the Class of a Gearhead interface */
	fun getInterface(context: Context, name: String): Class<IInterface> {
		return getContext(context).classLoader.loadClass(expandClassName(name)) as Class<IInterface>
	}
	/** Create an instance of a Gearhead interface */
	fun createInterface(context: Context, name: String, transport: IBinder): IInterface {
		val target = getInterface(context, name)
		val constructor = target.getConstructor(IBinder::class.java)
		return constructor.newInstance(transport)
	}
}