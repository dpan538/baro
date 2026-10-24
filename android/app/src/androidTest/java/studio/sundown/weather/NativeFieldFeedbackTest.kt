package studio.sundown.weather

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import org.json.JSONObject
import java.io.File
import kotlin.math.abs

/** Targeted field-feedback regressions. Recorded input and mock GPS are test-only;
 * separate product captures require genuine provider data. */
class NativeFieldFeedbackTest {
    private val i=InstrumentationRegistry.getInstrumentation()
    private val ctx get()=i.targetContext
    private fun seed(){
        ctx.getSharedPreferences("baro-native",Context.MODE_PRIVATE).edit().clear().putString("language","zh").putBoolean("motion",false).putBoolean("block",true).commit()
        ctx.getSharedPreferences("sundown-device",Context.MODE_PRIVATE).edit().putBoolean("location_follow",false).commit()
    }
    private fun fixture(a:MainActivity){
        a.repository.cancelWeather();val now=System.currentTimeMillis()/1000
        val original=WeatherJson.parse(i.context.assets.open("live-brisbane.json").bufferedReader().use{it.readText()},a.model.place)
        val shift=now-original.validAt
        a.model.weather=original.copy(fetchedAt=now,validAt=now,hourly=original.hourly.copy(times=original.hourly.times.map{it+shift}));a.model.status="ready";a.redraw()
    }
    private fun await(timeout:Long=15000,condition:()->Boolean){val end=SystemClock.uptimeMillis()+timeout;while(SystemClock.uptimeMillis()<end){if(condition())return;SystemClock.sleep(100)};assertTrue("Timed out waiting for the native state",condition())}
    private fun capture(name:String){val image=i.uiAutomation.takeScreenshot()!!;File(ctx.getExternalFilesDir(null),name).outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()}
    private fun texts(v:View):List<TextView> = (if(v is TextView)listOf(v)else emptyList())+(if(v is ViewGroup)(0 until v.childCount).flatMap{texts(v.getChildAt(it))}else emptyList())
    @Test fun fullBleedWeatherAndReadableObservationSystemChrome(){
        seed();ActivityScenario.launch(MainActivity::class.java).use{s->
            s.onActivity(::fixture);await{var ok=false;s.onActivity{ok=it.pager.weatherPage.art.renderedFrames>0};ok}
            for(rotation in 0..1){
                i.uiAutomation.executeShellCommand("wm user-rotation lock $rotation").close();SystemClock.sleep(1300)
            for(page in 0..1){
                s.onActivity{a->a.model.page=page;a.redraw();assertTrue(WindowInsetsControllerCompat(a.window,a.window.decorView).isAppearanceLightStatusBars)}
                SystemClock.sleep(500)
                var top=0
                s.onActivity{a->
                    val insets=a.window.decorView.rootWindowInsets
                    if(page==0){
                        assertFalse(insets.isVisible(android.view.WindowInsets.Type.statusBars()))
                        val board=a.pager.weatherPage;val art=board.art;assertNull(board.dashboard.frameOutline)
                        assertEquals(0,a.root.paddingTop);assertEquals(board.width,art.right);assertEquals(board.height,art.bottom);assertEquals(0,board.left)
                        if(board.width>board.height)assertEquals(0,art.top)else assertEquals(0,art.left)
                    }else{
                        top=insets.getInsets(android.view.WindowInsets.Type.statusBars()).top
                        assertTrue(top>0);assertTrue(a.pager.observationPage.dashboard.top>=top)
                    }
                }
                val image=i.uiAutomation.takeScreenshot()!!;var light=0;var samples=0
                for(y in 2 until top step 5)for(x in 10 until image.width-10 step 40){val color=image.getPixel(x,y);samples++;if(Color.red(color)>230&&Color.green(color)>230&&Color.blue(color)>225)light++}
                if(page==1)assertTrue("Observation status icons need a bright background",light>samples*.7)
                image.recycle();capture("field-system-${if(rotation==0)"landscape"else"portrait"}-page$page.png")
            }
            }
            i.uiAutomation.executeShellCommand("wm user-rotation lock 0").close();SystemClock.sleep(1300)
        }
    }
    @Test fun styledPlacePickerActuallySearchesAndSelectsACity(){
        seed();ActivityScenario.launch(MainActivity::class.java).use{s->
            s.onActivity{a->fixture(a);a.showPlaces()};SystemClock.sleep(400)
            lateinit var dialog:PlaceDialog
            s.onActivity{a->dialog=MainActivity::class.java.getDeclaredField("placeDialog").apply{isAccessible=true}.get(a) as PlaceDialog
                val root=dialog.window!!.decorView;assertNotNull(root.findViewWithTag<View>("baro-place-dialog"))
                root.findViewWithTag<EditText>("baro-place-query").setText("Brisbane")
                root.findViewWithTag<Button>("baro-place-search").performClick()
            }
            await(25000){var count=0;s.onActivity{count=dialog.window!!.decorView.findViewWithTag<ViewGroup>("baro-place-results").childCount};count>0}
            capture("field-place-search.png")
            s.onActivity{a->val result=dialog.window!!.decorView.findViewWithTag<ViewGroup>("baro-place-results").getChildAt(0);assertTrue(texts(result).any{it.text.toString().let{label->label.contains("Brisbane")||label.contains("布里斯班")}});result.performClick();assertFalse(a.locationEnabled());assertTrue(a.model.placeName().let{name->name.contains("Brisbane")||name.contains("布里斯班")})}
            await(3000){var closed=false;s.onActivity{closed=!dialog.isShowing};closed}
        }
    }
    @Suppress("DEPRECATION")
    @Test fun freshSystemFixUpdatesThePlaceAndSettingsWithoutManualSearch(){
        seed();val manager=ctx.getSystemService(LocationManager::class.java)
        for(permission in listOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION))i.uiAutomation.grantRuntimePermission(ctx.packageName,permission)
        i.uiAutomation.executeShellCommand("appops set ${ctx.packageName} android:mock_location allow").use{android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use{stream->stream.readBytes()}}
        manager.addTestProvider(LocationManager.GPS_PROVIDER,false,false,false,false,true,true,true,Criteria.POWER_LOW,Criteria.ACCURACY_FINE)
        try{
            manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER,true)
            val fix=Location(LocationManager.GPS_PROVIDER).apply{latitude=-33.8688;longitude=151.2093;accuracy=15f;time=System.currentTimeMillis();elapsedRealtimeNanos=SystemClock.elapsedRealtimeNanos()}
            manager.setTestProviderLocation(LocationManager.GPS_PROVIDER,fix)
            ActivityScenario.launch(MainActivity::class.java).use{s->
                s.onActivity{a->fixture(a);a.settings.open();a.setLocation(true)}
                await{var fixed=false;s.onActivity{fixed=it.model.locationStatus=="fix"};fixed}
                s.onActivity{a->assertTrue(a.locationEnabled());assertEquals(fix.latitude,a.model.place.latitude,.0001);assertEquals(fix.longitude,a.model.place.longitude,.0001);assertTrue(texts(a.settings).any{it.text.toString()=="已使用当前位置"})}
                File(ctx.getExternalFilesDir(null),"field-location.json").writeText(JSONObject().put("mockSystemFix",true).put("manualSearchRequired",false).put("settingsStatusRefreshed",true).toString())
            }
        }finally{ctx.getSharedPreferences("sundown-device",Context.MODE_PRIVATE).edit().putBoolean("location_follow",false).commit();manager.removeTestProvider(LocationManager.GPS_PROVIDER);i.uiAutomation.executeShellCommand("appops set ${ctx.packageName} android:mock_location default").close()}
    }
    @Test fun draggingTracksTheFingerAndASecondTouchDoesNotSnapThePage(){
        seed();ActivityScenario.launch(MainActivity::class.java).use{s->
            s.onActivity(::fixture);SystemClock.sleep(300)
            var x=0f;var y=0f;var width=0f
            s.onActivity{a->width=a.pager.width.toFloat();x=width*.5f;y=a.pager.height*.82f}
            var start=SystemClock.uptimeMillis()
            fun send(action:Int,xx:Float){s.onActivity{a->val e=MotionEvent.obtain(start,SystemClock.uptimeMillis(),action,xx,y,0);a.pager.dispatchTouchEvent(e);e.recycle()}}
            send(MotionEvent.ACTION_DOWN,x);send(MotionEvent.ACTION_MOVE,x+width*.22f)
            s.onActivity{a->assertEquals(width*.22f,a.pager.weatherPage.translationX,2f);assertEquals(0,a.model.page)}
            s.onActivity{a->
                val release=MotionEvent.obtain(start,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,x+width*.22f,y,0);a.pager.dispatchTouchEvent(release);release.recycle()
                val animation=TwoPageHost::class.java.getDeclaredField("animator").apply{isAccessible=true}.get(a.pager) as android.animation.ValueAnimator
                animation.currentPlayTime=80
                val before=a.pager.weatherPage.translationX;assertTrue(before>0&&before<width);start=SystemClock.uptimeMillis();val e=MotionEvent.obtain(start,start,MotionEvent.ACTION_DOWN,x,y,0);a.pager.dispatchTouchEvent(e);e.recycle();assertEquals(before,a.pager.weatherPage.translationX,2f)}
            send(MotionEvent.ACTION_CANCEL,x);SystemClock.sleep(500)
            s.onActivity{a->assertEquals(0,a.model.page);assertEquals(0f,a.pager.weatherPage.translationX,.1f);a.settings.open();assertEquals(0f,a.settings.alpha,.01f);assertTrue(a.pager.overlayOpen)}
            SystemClock.sleep(450);s.onActivity{a->assertEquals(1f,a.settings.alpha,.01f);a.settings.close();assertTrue(a.pager.overlayOpen)}
            SystemClock.sleep(400);s.onActivity{assertFalse(it.pager.overlayOpen);assertEquals(View.GONE,it.settings.visibility)}
        }
    }
    @Test fun rainRendersOverTheSameEnvironmentalScene(){
        seed();ActivityScenario.launch(MainActivity::class.java).use{s->
            lateinit var wet:Weather;var at=0L
            s.onActivity{a->
                fixture(a);val original=a.model.weather!!
                wet=original.copy(current=original.current+mapOf("weather_code" to 80.0,"precipitation" to 2.0),hourly=original.hourly.copy(fields=original.hourly.fields+mapOf("weather_code" to original.hourly.times.map{0.0},"is_day" to original.hourly.times.map{1.0},"cloud_cover" to original.hourly.times.map{20.0})))
                at=wet.validAt+900;a.model.weather=wet;a.model.page=1;a.redraw()
            }
            lateinit var rainImage:Bitmap
            fun frame(weather:Weather):Bitmap {
                var previous=0L
                s.onActivity{a->previous=a.pager.observationPage.art.renderedFrames;a.pager.observationPage.art.updateScene(weather,at,true,false)}
                await{var ready=false;s.onActivity{ready=it.pager.observationPage.art.renderedFrames>previous};ready}
                SystemClock.sleep(350);var bitmap:Bitmap?=null;s.onActivity{bitmap=it.pager.observationPage.art.bitmap};return bitmap!!
            }
            rainImage=frame(wet)
            val dry=wet.copy(current=wet.current+mapOf("weather_code" to 0.0,"precipitation" to 0.0))
            assertEquals(wet.scene(at),dry.scene(at));assertEquals("rain",wet.scene(wet.atmosphereTime(at)))
            val dryImage=frame(dry);val a=IntArray(rainImage.width*rainImage.height);val b=IntArray(a.size);rainImage.getPixels(a,0,rainImage.width,0,0,rainImage.width,rainImage.height);dryImage.getPixels(b,0,dryImage.width,0,0,dryImage.width,dryImage.height)
            val changed=a.indices.count{a[it]!=b[it]};assertTrue("The same environment must visibly retain actual rain",changed>a.size*.08)
            for((name,bitmap)in listOf("field-environment-rain.png" to rainImage,"field-environment-dry.png" to dryImage))File(ctx.getExternalFilesDir(null),name).outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            File(ctx.getExternalFilesDir(null),"field-environment.json").writeText(JSONObject().put("controlledInput",true).put("sameEnvironmentScene",true).put("changedPixels",changed).put("totalPixels",a.size).put("rainIndependentOfScene",true).toString())
            rainImage.recycle();dryImage.recycle()
        }
    }
}
