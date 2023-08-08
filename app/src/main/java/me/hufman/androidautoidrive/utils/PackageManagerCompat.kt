package me.hufman.androidautoidrive.utils

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build

object PackageManagerCompat {
	fun PackageManager.getApplicationInfoCompat(packageName: String, flags: Int = 0): ApplicationInfo? {
		return try {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
				this.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(flags.toLong()))
			} else {
				@Suppress("DEPRECATION")
				this.getApplicationInfo(packageName, flags)
			}
		} catch (e: PackageManager.NameNotFoundException) {
			null
		}
	}
	fun PackageManager.getPackageInfoCompat(packageName: String, flags: Int = 0): PackageInfo? {
		return try {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
				this.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
			} else {
				@Suppress("DEPRECATION")
				this.getPackageInfo(packageName, flags)
			}
		} catch (e: PackageManager.NameNotFoundException) {
			null
		}
	}
	fun PackageManager.queryIntentActivitiesCompat(intent: Intent, flags: Int = 0): List<ResolveInfo> {
		return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			this.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
		} else {
			@Suppress("DEPRECATION")
			this.queryIntentActivities(intent, flags)
		}
	}
	fun PackageManager.queryIntentServicesCompat(intent: Intent, flags: Int = 0): List<ResolveInfo> {
		return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			this.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
		} else {
			@Suppress("DEPRECATION")
			this.queryIntentServices(intent, flags)
		}
	}
	fun PackageManager.resolveActivityCompat(intent: Intent, flags: Int = 0): ResolveInfo? {
		return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			this.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
		} else {
			@Suppress("DEPRECATION")
			this.resolveActivity(intent, flags)
		}
	}
}