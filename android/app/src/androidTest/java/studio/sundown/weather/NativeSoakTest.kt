package studio.sundown.weather

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Debug
import android.os.Process
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.math.max

/** Real GLES + real cached provider data. One JSONL row is one launch/interact/close cycle. */
class NativeSoakTest {
    private val i=InstrumentationRegistry.getInstrumentation()
    private val args=InstrumentationRegistry.getArguments()
    private fun pause(ms:Long=220){i.waitForIdleSync();SystemClock.sleep(ms)}
    private fun await(message:String,limit:Long=15000,predicate:()->Boolean){val end=SystemClock.uptimeMillis()+limit;while(!predicate()&&SystemClock.uptimeMillis()<end)SystemClock.sleep(60);assertTrue(message,predicate())}
    private fun gesture(x:Float,y:Float,dx:Float,dy:Float,cancel:Boolean=false){
        val time=SystemClock.uptimeMillis()
        fun send(action:Int,f:Float){val e=MotionEvent.obtain(time,SystemClock.uptimeMillis(),action,x+dx*f,y+dy*f,0);i.sendPointerSync(e);e.recycle()}
        send(MotionEvent.ACTION_DOWN,0f);for(n in 1..8){send(MotionEvent.ACTION_MOVE,n/8f);SystemClock.sleep(12)};send(if(cancel)MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP,1f);pause(260)
    }
    private fun tap(view:View){val r=Rect();assertTrue(view.getGlobalVisibleRect(r));val time=SystemClock.uptimeMillis();for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)){val e=MotionEvent.obtain(time,SystemClock.uptimeMillis(),action,r.exactCenterX(),r.exactCenterY(),0);i.sendPointerSync(e);e.recycle()};pause(300)}
    private fun find(v:View,label:String):View?{if(v is TextView&&v.text.toString()==label)return v;if(v is ViewGroup)for(n in 0 until v.childCount)find(v.getChildAt(n),label)?.let{return it};return null}
    private fun artThreads()=Thread.getAllStackTraces().keys.count{it.isAlive&&it.name=="BARO-art"}
    private fun drain(art:PixelArt){
        val latch=java.util.concurrent.CountDownLatch(1)
        val worker=PixelArt::class.java.getDeclaredField("worker").apply{isAccessible=true}.get(art) as android.os.Handler
        assertTrue(worker.post{latch.countDown()});assertTrue("In-flight GLES did not finish",latch.await(20,java.util.concurrent.TimeUnit.SECONDS))
    }
    private fun memory():JSONObject{val m=Debug.MemoryInfo();Debug.getMemoryInfo(m);return JSONObject().put("pssKiB",m.totalPss).put("privateDirtyKiB",m.totalPrivateDirty).put("javaHeapKiB",m.getMemoryStat("summary.java-heap").toInt()).put("nativeHeapKiB",m.getMemoryStat("summary.native-heap").toInt()).put("graphicsKiB",m.getMemoryStat("summary.graphics").toInt()).put("rssKiB",File("/proc/self/status").readLines().first{it.startsWith("VmRSS:")}.trim().split(Regex("\\s+"))[1].toLong()).put("fdCount",File("/proc/self/fd").list()?.size?:-1).put("artThreads",artThreads())}
    private fun screenshot(name:String){val b=i.uiAutomation.takeScreenshot()!!;File(i.targetContext.getExternalFilesDir(null),name).outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
    @Test fun repeatedNativeLaunchInteractionLifecycleAndResourceMeasurements(){
        assertFalse("Soak must use actual GLES, never hosted UI substitutes",NativeTestEnvironment.uiOnly)
        val start=args.getString("startRound")?.toInt()?:1;val count=args.getString("roundCount")?.toInt()?:15
        val output=File(i.targetContext.getExternalFilesDir(null),"soak-$start.jsonl");output.writeText("")
        for(n in start until start+count){
            if(n==start||(n-1)%75==0){i.uiAutomation.executeShellCommand("wm user-rotation lock ${if((n-1)/75%2==0)0 else 1}").close();SystemClock.sleep(1400)}
            val began=SystemClock.elapsedRealtime();val row=JSONObject().put("round",n).put("epochMs",System.currentTimeMillis()).put("source",BuildConfig.SOURCE_COMMIT).put("pid",Process.myPid()).put("freshInstrumentationProcess",n==start)
            val checks=org.json.JSONArray();row.put("checks",checks)
            var scenario:ActivityScenario<MainActivity>?=null
            try{
                val lang=if(((n-1)/12+(n-1)%12)%2==0)"zh"else "en";val purpose=(n-1)%12;val block=n%5!=0;val motion=(n-1)/5%2==0
                row.put("language",lang).put("purpose",purpose).put("pixels",if(block)"block"else "fine").put("motion",motion)
                i.targetContext.getSharedPreferences("baro-native",Context.MODE_PRIVATE).edit().putString("place_name","Brisbane").putLong("lat",(-27.47).toBits()).putLong("lon",153.03.toBits()).putString("zone","Australia/Brisbane").putString("language",lang).putInt("page",0).putInt("purpose",purpose).putBoolean("block",block).putBoolean("motion",motion).putBoolean("fahrenheit",n/7%2==0).putString("wind",listOf("kmh","ms","mph","kts")[n/7%4]).commit()
                scenario=ActivityScenario.launch(MainActivity::class.java);val s=scenario
                var ready=false;await("Weather/first actual GLES frame did not become ready",30000){s.onActivity{a->ready=a.model.weather!=null&&a.model.status=="ready"&&a.pager.weatherPage.art.renderedFrames>0};ready}
                row.put("launchToFirstArtMs",SystemClock.elapsedRealtime()-began)
                checks.put("weather-ready-and-real-gles-first-frame")
                var a:MainActivity?=null;s.onActivity{a=it;assertEquals(lang,it.model.lang);assertEquals(purpose,it.model.purpose);assertEquals(0,it.model.page);assertNull(it.pager.weatherPage.art.error);row.put("orientation",if(it.resources.configuration.orientation==2)"landscape"else "portrait").put("providerFetchedAt",it.model.weather!!.fetchedAt).put("cacheBytes",it.repository.bytes())
                    val raw=it.repository.rawFor(it.model.place)!!;val hash=java.security.MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString(""){b->"%02x".format(b.toInt()and 255)}
                    val name="soak-provider-${it.model.weather!!.fetchedAt}-${hash.take(12)}.json";val file=File(i.targetContext.getExternalFilesDir(null),name);if(!file.exists())file.writeText(raw);row.put("providerResponseSha256",hash).put("providerResponseFile",name)
                }
                s.onActivity{assertEquals(block,it.pager.weatherPage.art.block);assertEquals(motion,it.model.motion)};checks.put("seeded-preferences-and-render-mode")
                val activity=a!!;val p=activity.pager;val location=IntArray(2);p.getLocationOnScreen(location);val x=location[0]+p.width*.5f;val y=location[1]+p.height*.82f;val dx=p.width*.34f*(if(n%2==0)1 else -1)
                if(n%3==0){gesture(x,y,dx*.45f,0f,true);s.onActivity{assertEquals(0,it.model.page)};checks.put("cancelled-page-stays")}
                gesture(x,y,dx,0f);s.onActivity{assertEquals(1,it.model.page)}
                var dialX=0f;var dialY=0f;var tooth=0f;var ticks=0
                s.onActivity{val b=it.pager.observationPage.dashboard;val r=Rect();assertTrue(b.getGlobalVisibleRect(r));val d=it.resources.displayMetrics.density;dialX=r.left+b.dialRect.centerX()*d;dialY=r.top+b.dialRect.centerY()*d;tooth=max(38f,(b.dialRect.height()*.70f*Math.toRadians(8.5)).toFloat())*d;ticks=b.detentFeedbackCount}
                gesture(dialX,dialY,0f,-tooth)
                s.onActivity{assertEquals((purpose+1)%12,it.model.purpose);assertEquals(1,it.model.page);assertEquals(ticks+1,it.pager.observationPage.dashboard.detentFeedbackCount);assertFalse(it.pager.observationPage.dashboard.dialSettling)}
                checks.put("outward-page-and-dial-detent-ownership")
                if(n%25==0){await("Observation GLES frame missing",20000){activity.pager.observationPage.art.renderedFrames>0||activity.pager.observationPage.art.weather?.scene(activity.pager.observationPage.art.sampleTime?:0)=="unknown"};screenshot("soak-$n-observation.png")}
                gesture(x,y,-dx,0f);s.onActivity{assertEquals(0,it.model.page);assertEquals(View.LAYER_TYPE_NONE,it.pager.weatherPage.dashboard.layerType)}
                checks.put("return-page-and-layer-reset")
                // Actual canvas gear press opens the same overlay used by people.
                var gx=0f;var gy=0f;s.onActivity{val b=it.pager.weatherPage.dashboard;val r=Rect();b.getGlobalVisibleRect(r);gx=r.left+b.gearRect.centerX()*it.resources.displayMetrics.density;gy=r.top+b.gearRect.centerY()*it.resources.displayMetrics.density}
                gesture(gx,gy,0f,0f);s.onActivity{assertTrue(it.pager.overlayOpen);assertFalse(it.pager.weatherPage.art.active);assertFalse(it.pager.observationPage.art.active)}
                lateinit var language:View;s.onActivity{language=find(it.settings,if(lang=="zh")"English"else "中文")!!};tap(language)
                s.onActivity{assertEquals(if(lang=="zh")"en"else "zh",it.model.lang);it.settings.back();assertFalse(it.pager.overlayOpen)}
                checks.put("gear-overlay-pauses-art-and-language-button-closes")
                if(n%5==0){s.moveToState(Lifecycle.State.CREATED);pause(100);assertFalse(activity.pager.weatherPage.art.active);drain(activity.pager.weatherPage.art);drain(activity.pager.observationPage.art);val before=activity.pager.weatherPage.art.renderedFrames;SystemClock.sleep(250);assertEquals(before,activity.pager.weatherPage.art.renderedFrames);s.moveToState(Lifecycle.State.RESUMED);pause();row.put("backgroundResume",true)}
                if(n%10==0||n in listOf(65,85,105,195)){s.recreate();pause();s.onActivity{assertEquals((purpose+1)%12,it.model.purpose);assertEquals(0,it.model.page);assertFalse(it.pager.overlayOpen)};row.put("recreated",true)}
                val cpu=Process.getElapsedCpuTime();val wall=SystemClock.elapsedRealtime();SystemClock.sleep(1000);row.put("idleWindowMs",SystemClock.elapsedRealtime()-wall).put("idleCpuMs",Process.getElapsedCpuTime()-cpu).put("foreground",memory())
                if(n%25==0)screenshot("soak-$n-weather.png")
                s.close();scenario=null
                await("Rendering threads leaked after closing Activity",15000){artThreads()==0};checks.put("closed-activity-releases-render-workers");row.put("afterClose",memory()).put("status","pass")
            }catch(e:Throwable){row.put("status","fail").put("error",e.stackTraceToString());runCatching{screenshot("soak-$n-failure.png")};throw e}
            finally{scenario?.close();row.put("durationMs",SystemClock.elapsedRealtime()-began);output.appendText(row.toString()+"\n");i.sendStatus(0,android.os.Bundle().apply{putString("soakRound","$n/${start+count-1}: ${row.optString("status")}")})}
        }
    }
}
