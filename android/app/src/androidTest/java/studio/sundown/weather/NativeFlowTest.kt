package studio.sundown.weather

import android.content.Context
import android.graphics.Bitmap
import android.view.MotionEvent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.json.JSONObject
import java.io.File

/** Destructive only to the dedicated BARO emulator preferences. */
class NativeFlowTest {
    private val instrument=InstrumentationRegistry.getInstrumentation()
    private val ctx get()=instrument.targetContext
    private fun seed(page:Int=0){ctx.getSharedPreferences("baro-native",Context.MODE_PRIVATE).edit().clear().putBoolean("motion",false).putBoolean("block",InstrumentationRegistry.getArguments().getString("pixels")=="block").putString("language","zh").putInt("page",page).commit()}
    private fun idle(){instrument.waitForIdleSync();SystemClock.sleep(350)}
    private fun swipe(scenario:ActivityScenario<MainActivity>,direction:Int,yFraction:Float=.7f,vertical:Boolean=false,cancel:Boolean=false){
        val coords=FloatArray(4);scenario.onActivity{a->val loc=IntArray(2);a.pager.getLocationOnScreen(loc);val width=a.pager.width;val height=a.pager.height;coords[0]=loc[0]+width*.5f;coords[1]=loc[1]+height*yFraction;coords[2]=if(vertical)coords[0]+4 else coords[0]+width*.3f*direction;coords[3]=if(vertical)coords[1]+height*.15f else coords[1]+3}
        val start=SystemClock.uptimeMillis();fun event(action:Int,x:Float,y:Float,t:Long){val e=MotionEvent.obtain(start,t,action,x,y,0);instrument.sendPointerSync(e);e.recycle()}
        event(MotionEvent.ACTION_DOWN,coords[0],coords[1],start)
        for(i in 1..12){SystemClock.sleep(12);val f=i/12f;event(MotionEvent.ACTION_MOVE,coords[0]+(coords[2]-coords[0])*f,coords[1]+(coords[3]-coords[1])*f,SystemClock.uptimeMillis())}
        event(if(cancel)MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP,coords[2],coords[3],SystemClock.uptimeMillis());idle()
    }
    @Test fun forcedRefreshWaitsForNetworkCompletionEvenWithCache(){
        seed();val raw=JSONObject(instrument.context.assets.open("live-brisbane.json").bufferedReader().use{it.readText()}).put("fetchedAt",System.currentTimeMillis()/1000)
        val cache=File(ctx.cacheDir,"weather").apply{mkdirs()};File(cache,"-27.47,153.03.json").writeText(raw.toString())
        ActivityScenario.launch(MainActivity::class.java).use{s->s.onActivity{a->var delivered=false;assertNotNull(a.repository.cached(a.model.place));a.repository.refresh(a.model.place,true){_,_->delivered=true};assertFalse("Cached data must not acknowledge a forced network refresh",delivered);a.repository.cancelWeather()}}
    }
    @Test fun legacyTestFlagsCannotActivateSampleWeather(){
        seed();val prefs=ctx.getSharedPreferences("baro-native",Context.MODE_PRIVATE)
        prefs.edit().putBoolean("demo",true).putString("demo_scene","snow").commit()
        ActivityScenario.launch(MainActivity::class.java).use{s->s.onActivity{a->assertFalse(a.model.prefs.contains("demo"));assertFalse(a.model.prefs.contains("demo_scene"));assertNotEquals("demo",a.model.status)}}
    }
    @Test fun bothDirectionsFromBothPagesAndConsecutiveGestures(){
        seed();ActivityScenario.launch(MainActivity::class.java).use{s->idle();for(direction in listOf(-1,1,1,-1,-1,1)){var before=0;s.onActivity{before=it.model.page};swipe(s,direction,.82f);s.onActivity{assertEquals(1-before,it.model.page);assertEquals(direction,it.pager.lastSettleDirection)}}}
    }
    @Test fun cancelledAndVerticalGesturesStayOnPage(){seed();ActivityScenario.launch(MainActivity::class.java).use{s->idle();swipe(s,1,.65f,cancel=true);s.onActivity{assertEquals(0,it.model.page)};swipe(s,1,.65f,vertical=true);s.onActivity{assertEquals(0,it.model.page)}}}
    @Test fun settingsAndDialOwnTheirTouchesAndPersist(){seed(1);ActivityScenario.launch(MainActivity::class.java).use{s->idle();s.onActivity{a->val r=a.pager.observationPage.dashboard.dialRect;val x=r.centerX()*a.resources.displayMetrics.density;val y=r.centerY()*a.resources.displayMetrics.density;val v=a.pager.observationPage.dashboard;val time=SystemClock.uptimeMillis();v.dispatchTouchEvent(MotionEvent.obtain(time,time,MotionEvent.ACTION_DOWN,x,y,0));v.dispatchTouchEvent(MotionEvent.obtain(time,time+150,MotionEvent.ACTION_MOVE,x,y-220,0));v.dispatchTouchEvent(MotionEvent.obtain(time,time+180,MotionEvent.ACTION_UP,x,y-220,0));assertEquals(1,a.model.page);assertNotEquals(0,a.model.purpose);a.settings.open();assertTrue(a.pager.overlayOpen)};swipe(s,-1,.5f);s.onActivity{assertEquals(1,it.model.page);it.settings.close();it.model.fahrenheit=true;it.model.lang="en"};s.recreate();idle();s.onActivity{assertEquals(1,it.model.page);assertTrue(it.model.fahrenheit);assertEquals("en",it.model.lang);assertNotEquals(0,it.model.purpose)}}}
    @Test fun realRecordedApiUnitsNullStalenessAndWrongPlace(){
        val raw=instrument.context.assets.open("live-brisbane.json").bufferedReader().use{it.readText()};val place=Place("Brisbane",-27.47,153.03);val w=WeatherJson.parse(raw,place);assertEquals("Australia/Brisbane",w.place.zone);assertNotNull(w.value("temperature_2m"));assertNotNull(w.value("wind_speed_300hPa"));assertTrue(w.stale(w.fetchedAt+2000))
        val bad=JSONObject(raw);bad.getJSONObject("forecast").getJSONObject("current_units").put("temperature_2m","°F");assertNull(WeatherJson.parse(bad.toString(),place).value("temperature_2m"));assertThrows(IllegalArgumentException::class.java){WeatherJson.parse(raw,Place("London",51.5,0.0))}
        val air=JSONObject(raw);air.getJSONObject("air").getJSONObject("current").put("time",w.validAt-7201);assertNull(WeatherJson.parse(air.toString(),place).aqi)
    }
    @Test fun cacheClearingKeepsPreferencesAndFeedbackDraft(){seed();ActivityScenario.launch(MainActivity::class.java).use{s->s.onActivity{a->a.feedback.draft="Offline draft 保留";a.model.wind="mph";a.repository.clear();assertEquals("Offline draft 保留",a.feedback.draft);assertEquals("mph",a.model.wind);assertEquals(0L,a.repository.bytes())}}}
    @Test fun unconfiguredFeedbackNeverClearsDraft(){seed();ActivityScenario.launch(MainActivity::class.java).use{s->s.onActivity{a->a.feedback.draft="draft";val f=Feedback(a.model.prefs,a.repository.pool,"");f.send{ok,reason->assertFalse(ok);assertEquals("unconfigured",reason);assertEquals("draft",f.draft)}}}}
    @Test fun updateManifestsRejectForeignPackagesHttpAndInvalidHashes(){
        fun manifest()=JSONObject().put("packageName","studio.sundown.weather").put("versionCode",13).put("versionName","test").put("size",100).put("sha256","a".repeat(64)).put("certificateSha256","b".repeat(64)).put("url","https://github.com/dpan538/baro/releases/download/test/test.apk")
        assertEquals(13L,Release.parse(manifest()).code)
        for(bad in listOf(manifest().put("packageName","other"),manifest().put("url","http://github.com/dpan538/baro/releases/download/a/b.apk"),manifest().put("sha256","bad"),manifest().put("url","https://evil.example/p.apk"))){assertThrows(IllegalArgumentException::class.java){Release.parse(bad)}}
    }
    private fun dismissEducation(){
        fun visit(node:android.view.accessibility.AccessibilityNodeInfo?):Boolean {
            if(node==null)return false
            if(node.text?.toString() in listOf("Got it","知道了"))return node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
            for(i in 0 until node.childCount)if(visit(node.getChild(i)))return true
            return false
        }
        visit(instrument.uiAutomation.rootInActiveWindow);SystemClock.sleep(250)
    }
    @Test fun captureNativeBothLanguagesAndOrientations(){
        seed();ActivityScenario.launch(MainActivity::class.java).use{s->idle()
            s.onActivity{it.refresh(true)}
            var ready=false
            for(attempt in 0..59){s.onActivity{ready=it.model.weather!=null&&it.model.status=="ready"};if(ready)break;SystemClock.sleep(500)}
            assertTrue("Live API weather must be loaded before product screenshots",ready)
            s.onActivity{a->java.io.File(ctx.getExternalFilesDir(null),"live-visual-provenance.json").writeText(org.json.JSONObject().put("source","Open-Meteo live API").put("backend",a.repository.backend).put("pixels",if(a.model.block)"block"else "fine").put("validAt",a.model.weather!!.validAt).put("fetchedAt",a.model.weather!!.fetchedAt).toString());val raw=java.io.File(ctx.cacheDir,"weather/"+a.model.place.key+".json");assertTrue(raw.isFile);raw.copyTo(java.io.File(ctx.getExternalFilesDir(null),"live-visual-response.json"),overwrite=true)}
            for(orientation in listOf(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)){
                instrument.uiAutomation.executeShellCommand("wm user-rotation lock ${if(orientation==0)0 else 1}").close();SystemClock.sleep(1500);idle();s.onActivity{assertEquals(orientation==0,it.pager.width>it.pager.height)}
                dismissEducation();val selected=InstrumentationRegistry.getArguments().getString("purposes")?.split(",")?.map{it.toInt().also{value->require(value in 0..11)}}?: (0..11).toList()
                for(lang in listOf("zh","en"))for(variant in listOf(0)+selected.map{it+1}){val page=if(variant==0)0 else 1;val purpose=(variant-1).coerceAtLeast(0);s.onActivity{a->a.model.lang=lang;a.model.page=page;a.model.purpose=purpose;a.pager.requestLayout();a.redraw()};SystemClock.sleep(500);idle();dismissEducation()
                    val image=instrument.uiAutomation.takeScreenshot();assertNotNull(image);assertEquals(orientation==0,image.width>image.height);if(page==0){val pixel=image.getPixel(40,200);assertTrue("Weather paper hidden by a clip or system overlay",android.graphics.Color.red(pixel)>245&&android.graphics.Color.green(pixel)>245)}
                    val tag="${if(page==0)"weather" else "observation"}-${if(orientation==0)"landscape" else "portrait"}-$lang"
                    println("Captured live $tag purpose $purpose")
                    val out=File(ctx.getExternalFilesDir(null),"native-$tag"+(if(purpose>0)"-purpose-$purpose"else "")+".png");out.outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()
                    s.onActivity{a->val art=if(page==0)a.pager.weatherPage.art else a.pager.observationPage.art;assertNull(art.error);assertTrue(art.renderedFrames>0)}
                }
            }
        }
    }
}
