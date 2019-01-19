package me.hufman.androidautoidrive

import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import android.util.Log
import me.hufman.idriveconnectionkit.android.CertMangling
import me.hufman.idriveconnectionkit.android.SecurityService
import org.awaitility.Awaitility.await

import org.junit.Test
import org.junit.runner.RunWith


/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getTargetContext()

        val assets = CarAppAssetManager(appContext, "basecoreOnlineServices")
        val appCert = assets.getAppCertificateRaw("Mini")?.readBytes() as ByteArray
        Log.i("Test", String(appCert))
        SecurityService.connect(appContext)
        SecurityService.subscribe(Runnable {
            val signedCert = CertMangling.mergeBMWCert(appCert, SecurityService.fetchBMWCerts(appContext.packageName))
            val certs = CertMangling.loadCerts(signedCert)
            certs?.forEach {
                Log.i("Test", CertMangling.getCN(it))
            }
        })
        await().until { SecurityService.isConnected() }
    }
}
