package studio.sundown.weather

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Explicit opt-in: genuine public GitHub check/download via the installed app's
 * Settings. Does not inject a transport or copy/stage a candidate. */
class NativePublicUpdateTest {
    private val i=InstrumentationRegistry.getInstrumentation()
    private val ctx get()=i.targetContext
    private val args get()=InstrumentationRegistry.getArguments()
    private fun find(v:View,label:String):View? {
        if(v is TextView&&v.text.toString()==label)return v
        if(v is ViewGroup)for(n in 0 until v.childCount)find(v.getChildAt(n),label)?.let{return it}
        return null
    }
    private fun tap(s:ActivityScenario<MainActivity>,label:String){
        val r=Rect()
        s.onActivity{a->val v=find(a.settings,label)?:error("Missing $label");v.requestRectangleOnScreen(Rect(0,0,v.width,v.height),true)}
        SystemClock.sleep(250)
        s.onActivity{a->assertTrue(find(a.settings,label)!!.getGlobalVisibleRect(r))}
        val now=SystemClock.uptimeMillis()
        for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)){
            val e=MotionEvent.obtain(now,SystemClock.uptimeMillis(),action,r.exactCenterX(),r.exactCenterY(),0);i.sendPointerSync(e);e.recycle();SystemClock.sleep(70)
        }
        SystemClock.sleep(300)
    }
    private fun capture(name:String){val b=i.uiAutomation.takeScreenshot()!!;File(ctx.getExternalFilesDir(null),name).outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
    @Test fun settingsDiscoversAndDownloadsTheActualPublicApk(){
        assertEquals("yes",args.getString("publicUpdate"))
        val expected=args.getString("expectedSha")!!;val code=args.getString("expectedCode")!!.toLong()
        assertTrue(ctx.packageManager.getPackageInfo(ctx.packageName,0).longVersionCode<code)
        ctx.getSharedPreferences("baro-native",Context.MODE_PRIVATE).edit().putString("language","en").putBoolean("motion",false).putBoolean("block",true).putBoolean("fahrenheit",true).putString("wind","mph").putInt("purpose",11).putInt("page",1).putString("feedback_draft","Upgrade retention 草稿").putString("upgrade_marker","BARO-native-install-20261024").commit()
        File(ctx.filesDir,"upgrade-canary.txt").writeText("BARO private data remains intact\n")
        ActivityScenario.launch(MainActivity::class.java).use{s->
            s.onActivity{a->a.updates.apk.delete();File(ctx.cacheDir,"updates/pending.json").delete();a.settings.open()};SystemClock.sleep(350)
            tap(s,"About");tap(s,"Check for updates")
            val deadline=SystemClock.uptimeMillis()+45000;var available=false
            while(!available&&SystemClock.uptimeMillis()<deadline){s.onActivity{available=find(it.settings,"Download update")!=null};SystemClock.sleep(150)}
            assertTrue("Actual public release was not discovered",available);capture("public-update-available.png")
            tap(s,"Download update")
            val end=SystemClock.uptimeMillis()+180000;var verified:Release?=null
            while(verified==null&&SystemClock.uptimeMillis()<end){
                s.onActivity{if(find(it.settings,"Open system installer")!=null)verified=it.updates.pending()};SystemClock.sleep(200)
            }
            assertNotNull("Actual public download did not verify",verified);assertEquals(code,verified!!.code);assertEquals(expected,verified!!.sha)
            assertTrue(verified!!.url.startsWith("https://github.com/dpan538/baro/releases/download/"));capture("public-update-verified.png")
            File(ctx.getExternalFilesDir(null),"public-update-download.json").writeText(verified!!.json().put("nativeSettingsDiscovery",true).put("nativeSettingsDownload",true).put("publicDownloadVerified",true).toString(2))
        }
    }
    @Test fun newProcessRestoresVerifiedPublicCandidateAndOpensSystemInstaller(){
        assertEquals("yes",args.getString("publicUpdate"))
        ActivityScenario.launch(MainActivity::class.java).use{s->
            s.onActivity{a->assertEquals(args.getString("expectedSha"),a.updates.pending()!!.sha);a.settings.open()};SystemClock.sleep(350)
            tap(s,"About");tap(s,"Check for updates")
            s.onActivity{a->assertNotNull(find(a.settings,"Open system installer"));assertNotNull(a.updates.pending())}
            capture("public-update-restored.png")
            // Source permission has already been exercised in the controlled
            // real-installer test; the host sets it only on this disposable AVD.
            assertTrue(ctx.packageManager.canRequestPackageInstalls())
            tap(s,"Open system installer");SystemClock.sleep(600);capture("public-system-installer.png")
        }
    }
}
