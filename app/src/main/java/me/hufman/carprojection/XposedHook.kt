package me.hufman.carprojection

import android.content.pm.PackageManager
import de.robv.android.xposed.*
import de.robv.android.xposed.callbacks.XC_LoadPackage

class XposedHook: IXposedHookLoadPackage {
	companion object {
		val GOOGLE_SIGNATURE_VERIFIER = "com.google.android.gms.common.GoogleSignatureVerifier"
	}

	override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam?) {
		lpparam ?: return

		// grant the CAPTURE_VIDEO_OUTPUT permission
		hookContextPermission(lpparam)

		// if this package includes the GoogleSignatureVerifier, disable it
		try {
			XposedHelpers.findClass(GOOGLE_SIGNATURE_VERIFIER, lpparam.classLoader)
			hookContextPermission(lpparam)
			hookGoogleSignatureVerifier(lpparam)
		} catch (e: XposedHelpers.ClassNotFoundError) {
//			XposedBridge.log("Could not find GoogleSignatureVerifier in ${lpparam.packageName}")
			return
		}
	}

	val isGrantedPermission = object: XC_MethodHook() {
		override fun afterHookedMethod(param: MethodHookParam?) {
			val perm = param?.args?.getOrNull(0)
			if (perm == "android.permission.CAPTURE_VIDEO_OUTPUT" || perm == "android.permission.CAPTURE_SECURE_VIDEO_OUTPUT") {
				XposedBridge.log("Returning PERMISSION_GRANTED for ${param.args?.getOrNull(0)}")
				param.result = PackageManager.PERMISSION_GRANTED
			}
		}
	}
	val isGoogleSigned = object: XC_MethodReplacement() {
		override fun replaceHookedMethod(param: MethodHookParam?): Any {
			XposedBridge.log("Returning googledSigned:true for ${param?.method} (${param?.args?.getOrNull(0)})")
			return true
		}
	}

	fun hookContextPermission(lpparam: XC_LoadPackage.LoadPackageParam) {
		val contextClass = try {
			XposedHelpers.findClass("android.app.ContextImpl", lpparam.classLoader)
		} catch (e: XposedHelpers.ClassNotFoundError) {
			return
		}

		contextClass.methods.forEach {
//			XposedBridge.log("Context method - ${it.name}(${it.parameterTypes.map { it.componentType }}) -> ${it.returnType}")
			if (it.name == "checkCallingPermission") {
				XposedBridge.log("Attaching hook to ${lpparam.packageName} $it")
				XposedBridge.hookMethod(it, isGrantedPermission)
			}
		}
	}

	fun hookGoogleSignatureVerifier(lpparam: XC_LoadPackage.LoadPackageParam) {
		val verifierClass = try {
			XposedHelpers.findClass(GOOGLE_SIGNATURE_VERIFIER, lpparam.classLoader)
		} catch (e: XposedHelpers.ClassNotFoundError) {
//			XposedBridge.log("Could not find GoogleSignatureVerifier in ${lpparam.packageName}")
			return
		}
		verifierClass.declaredMethods.filter {
			it.returnType == Boolean::class.javaPrimitiveType
		}.forEach {
			XposedBridge.log("Attaching hook googleSigned to ${lpparam.packageName} $it")
			XposedBridge.hookMethod(it, isGoogleSigned)
		}
	}
}