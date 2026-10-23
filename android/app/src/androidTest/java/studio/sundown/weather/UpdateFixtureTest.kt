package studio.sundown.weather

import android.content.Context
import android.os.SystemClock
import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import java.io.File

/** Requires explicitly staged local fixture APKs; never points at public releases. */
class UpdateFixtureTest {
    private val i=InstrumentationRegistry.getInstrumentation()
    private val ctx get()=i.targetContext
    private fun installedSigner()=sha256(ctx.packageManager.getPackageInfo(ctx.packageName,android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES).signingInfo!!.apkContentsSigners.single().toByteArray())
    private fun release(file:File,code:Long=13,name:String="0.10.1-local-install-fixture")=Release(code,name,fileHash(file),installedSigner(),file.length(),"https://github.com/dpan538/baro/releases/download/local-test-fixture/candidate.apk","Local fixture; URL is never fetched",true)
    @Test fun verifiedCandidateAndRejectedTampering(){
        val good=File(ctx.cacheDir,"updates/candidate.apk");assertTrue("Stage the explicit fixture before this opt-in test",good.isFile)
        val prefs=ctx.getSharedPreferences("baro-native",Context.MODE_PRIVATE);prefs.edit().commit()
        ActivityScenario.launch(MainActivity::class.java).use{s->s.onActivity{a->
            val r=release(good);a.updates.verify(good,r)
            assertThrows(IllegalArgumentException::class.java){a.updates.verify(good,r.copy(sha="0".repeat(64)))}
            assertThrows(IllegalArgumentException::class.java){a.updates.verify(good,r.copy(code=99))}
            assertThrows(IllegalArgumentException::class.java){a.updates.verify(good,r.copy(version="wrong"))}
            val bad=File(ctx.cacheDir,"updates/wrong-signer.apk");assertTrue(bad.exists());val wrong=release(bad).copy(signer=sha256(ctx.packageManager.getPackageArchiveInfo(bad.absolutePath,android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)!!.signingInfo!!.apkContentsSigners.single().toByteArray()))
            assertThrows(IllegalArgumentException::class.java){a.updates.verify(bad,wrong)}
            File(ctx.cacheDir,"updates/pending.json").writeText(r.json().toString());assertNotNull(a.updates.pending())
        }}
    }
    @Test fun installerPermissionDenialRetryAndActualHandoff(){
        assertEquals("yes",InstrumentationRegistry.getArguments().getString("installFixture"))
        val good=File(ctx.cacheDir,"updates/candidate.apk");assertTrue(good.isFile)
        val prefs=ctx.getSharedPreferences("baro-native",Context.MODE_PRIVATE)
        prefs.edit().putString("language","en").putBoolean("fahrenheit",true).putString("wind","mph").putInt("purpose",11).putInt("page",1).putString("feedback_draft","Upgrade retention 草稿").putString("upgrade_marker","BARO-native-install-20261024").commit()
        File(ctx.filesDir,"upgrade-canary.txt").writeText("BARO private data remains intact\n")
        File(ctx.cacheDir,"updates/pending.json").writeText(release(good).json().toString())
        ActivityScenario.launch(MainActivity::class.java).use{s->
            if(!ctx.packageManager.canRequestPackageInstalls()){
                s.onActivity{assertFalse(it.updates.install(it))};SystemClock.sleep(500);i.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);SystemClock.sleep(500)
                assertFalse(ctx.packageManager.canRequestPackageInstalls());s.onActivity{assertNotNull(it.updates.pending());assertFalse(it.updates.install(it))};SystemClock.sleep(500)
                fun clickSwitch(n:android.view.accessibility.AccessibilityNodeInfo?):Boolean{if(n==null)return false;if((n.className?.toString()?.contains("Switch")==true||n.isCheckable)&&!n.isChecked)return n.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);for(j in 0 until n.childCount)if(clickSwitch(n.getChild(j)))return true;return false}
                var switched=false;for(attempt in 0..20){if(clickSwitch(i.uiAutomation.rootInActiveWindow)){switched=true;break};SystemClock.sleep(100)};assertTrue("Allow this source control missing",switched);SystemClock.sleep(400);i.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);SystemClock.sleep(500)
            }
            assertTrue(ctx.packageManager.canRequestPackageInstalls());s.onActivity{assertTrue(it.updates.install(it))};SystemClock.sleep(500)
            val image=i.uiAutomation.takeScreenshot();File(ctx.getExternalFilesDir(null),"system-installer-handoff.png").outputStream().use{image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};image.recycle()
        }
    }
}
