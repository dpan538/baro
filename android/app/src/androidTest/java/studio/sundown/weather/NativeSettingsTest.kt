package studio.sundown.weather

import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class NativeSettingsTest {
    private val i=InstrumentationRegistry.getInstrumentation()
    private fun seed(){i.targetContext.getSharedPreferences("baro-native",Context.MODE_PRIVATE).edit().clear().putBoolean("motion",false).putBoolean("block",InstrumentationRegistry.getArguments().getString("pixels")=="block").commit()}
    private fun find(root:View,label:String):View? {
        if(root.contentDescription?.toString()==label)return root
        if(root is TextView&&(root.text.toString()==label||label in listOf("°C","°F")&&root.text.toString().startsWith("$label ")))return root
        if(root is ViewGroup)for(n in 0 until root.childCount)find(root.getChildAt(n),label)?.let{return it}
        return null
    }
    private fun tap(s:ActivityScenario<MainActivity>,label:String){
        var rect=Rect()
        s.onActivity{a->val v=find(a.settings,label)?:error("Missing control $label");assertTrue(v.height>=a.dp(56));v.requestRectangleOnScreen(Rect(0,0,v.width,v.height),true)}
        i.waitForIdleSync();SystemClock.sleep(100)
        s.onActivity{a->val v=find(a.settings,label)!!;assertTrue(v.getGlobalVisibleRect(rect));assertTrue(rect.height()>0)}
        val t=SystemClock.uptimeMillis();val down=android.view.MotionEvent.obtain(t,t,0,rect.centerX().toFloat(),rect.centerY().toFloat(),0);val up=android.view.MotionEvent.obtain(t,t+70,1,rect.centerX().toFloat(),rect.centerY().toFloat(),0)
        i.sendPointerSync(down);i.sendPointerSync(up);down.recycle();up.recycle();i.waitForIdleSync();SystemClock.sleep(250)
    }
    @Test fun realSettingsTouchLanguageUnitsPixelMotionAndFooter(){
        seed();ActivityScenario.launch(MainActivity::class.java).use{s->s.onActivity{it.settings.open()};SystemClock.sleep(300)
            tap(s,"English");s.onActivity{assertEquals("en",it.model.lang)}
            tap(s,"Block");s.onActivity{assertTrue(it.model.block)}
            tap(s,"Weather animation");s.onActivity{assertTrue(it.model.motion)}
            tap(s,"Weather animation");s.onActivity{assertFalse(it.model.motion)}
            tap(s,"°F");s.onActivity{assertTrue(it.model.fahrenheit)}
            tap(s,"mph");s.onActivity{assertEquals("mph",it.model.wind)}
            tap(s,"···");tap(s,"Clear weather cache");tap(s,"Feedback")
            s.onActivity{a->assertNotNull(find(a.settings,"Submit feedback"));a.feedback.draft="Retained after settings close";a.settings.close();assertFalse(a.pager.overlayOpen)}
            s.recreate();i.waitForIdleSync();s.onActivity{a->assertTrue(a.model.block);assertFalse(a.model.motion);assertTrue(a.model.fahrenheit);assertEquals("mph",a.model.wind);assertEquals("Retained after settings close",a.feedback.draft)}
        }
    }
    @Test fun closingSettingsCancelsUpdateAndDoesNotReopenCard(){
        seed();ActivityScenario.launch(MainActivity::class.java).use{s->s.onActivity{it.settings.open()};SystemClock.sleep(300);tap(s,"检查更新");s.onActivity{it.settings.close()};SystemClock.sleep(700);s.onActivity{assertEquals(View.GONE,it.settings.visibility);assertFalse(it.pager.overlayOpen)}}
    }
    @Test fun backgroundPausesGpuAndResumeRestarts(){
        seed()
        // Isolate GPU lifecycle from network latency. This recorded provider input
        // exists only in androidTest; live product captures have a separate gate.
        val raw=i.context.assets.open("live-brisbane.json").bufferedReader().use{it.readText()}
        fun prepare(a:MainActivity){a.repository.cancelWeather();a.model.weather=WeatherJson.parse(raw,a.model.place);a.model.motion=true;a.redraw()}
        fun awaitFrame(s:ActivityScenario<MainActivity>,previous:Long){
            val deadline=SystemClock.uptimeMillis()+10000;var advanced=false
            while(SystemClock.uptimeMillis()<deadline){s.onActivity{a->assertNull(a.pager.weatherPage.art.error);advanced=a.pager.weatherPage.art.renderedFrames>previous};if(advanced)return;SystemClock.sleep(100)}
            assertTrue("Native GPU did not present a resumed frame",advanced)
        }
        ActivityScenario.launch(MainActivity::class.java).use{s->
            s.onActivity(::prepare);awaitFrame(s,1)
            s.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            var count=-1L;var stable=0;val deadline=SystemClock.uptimeMillis()+5000
            while(stable<4&&SystemClock.uptimeMillis()<deadline){SystemClock.sleep(250);s.onActivity{a->assertFalse(a.pager.weatherPage.art.active);val now=a.pager.weatherPage.art.renderedFrames;stable=if(now==count)stable+1 else 0;count=now}}
            assertEquals("GPU must quiesce after an in-flight frame",4,stable)
            SystemClock.sleep(500);s.onActivity{assertEquals(count,it.pager.weatherPage.art.renderedFrames)}
            s.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED);s.onActivity(::prepare);awaitFrame(s,count)
        }
    }
    @Test fun destroyedActivityReleasesArtWorkerWithPendingDisposal(){
        seed();val scenario=ActivityScenario.launch(MainActivity::class.java)
        val release=CountDownLatch(1);val entered=CountDownLatch(1)
        lateinit var owner:Thread;lateinit var art:PixelArt;lateinit var worker:android.os.Handler
        try{
            val raw=i.context.assets.open("live-brisbane.json").bufferedReader().use{it.readText()}
            scenario.onActivity{a->a.repository.cancelWeather();a.model.weather=WeatherJson.parse(raw,a.model.place);a.redraw();art=a.pager.weatherPage.art}
            val readyDeadline=SystemClock.uptimeMillis()+10000
            while(!art.isAvailable&&SystemClock.uptimeMillis()<readyDeadline)SystemClock.sleep(100)
            assertTrue(art.isAvailable)
            worker=PixelArt::class.java.getDeclaredField("worker").apply{isAccessible=true}.get(art) as android.os.Handler
            owner=PixelArt::class.java.getDeclaredField("thread").apply{isAccessible=true}.get(art) as Thread
            worker.post{entered.countDown();release.await(30,TimeUnit.SECONDS)}
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            scenario.close();i.waitForIdleSync();i.runOnMainSync{assertFalse(art.isAvailable)}
        }finally{release.countDown();scenario.close()}
        val deadline=SystemClock.uptimeMillis()+5000
        while(owner.isAlive&&SystemClock.uptimeMillis()<deadline)SystemClock.sleep(50)
        assertFalse("Surface destruction must retain queued worker disposal",owner.isAlive)
    }
    @Test fun nativeHttpFeedbackFailureRetainsExactDraftAndIdentity(){
        val health=org.json.JSONObject(NetworkJob().request("http://10.0.2.2:8787/healthz"));assertFalse("This test must never reach a configured public Issue writer",health.getBoolean("feedbackConfigured"))
        seed();val complete=CountDownLatch(1);var result=false
        ActivityScenario.launch(MainActivity::class.java).use{s->s.onActivity{a->a.feedback.draft="Local failure test 草稿";val original=a.model.prefs.getString("feedback_id",null);val sender=Feedback(a.model.prefs,a.repository.pool,"http://10.0.2.2:8787");sender.send{ok,_->result=!ok&&sender.draft=="Local failure test 草稿"&&a.model.prefs.getString("feedback_id",null)==original;complete.countDown()}}
            assertTrue(complete.await(12,TimeUnit.SECONDS));assertTrue(result)
        }
    }
}
