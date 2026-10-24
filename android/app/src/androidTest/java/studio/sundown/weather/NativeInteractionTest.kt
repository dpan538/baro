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
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import kotlin.math.max

/** Window-delivered gestures, including partial travel and cancellation. */
class NativeInteractionTest {
    private val i=InstrumentationRegistry.getInstrumentation()
    private val round=InstrumentationRegistry.getArguments().getString("evidenceRound")?:"regression"
    private fun seed(page:Int){i.targetContext.getSharedPreferences("baro-native",Context.MODE_PRIVATE).edit().clear().putString("language","zh").putBoolean("motion",false).putInt("page",page).putInt("purpose",0).commit()}
    private fun pause(ms:Long=180){i.waitForIdleSync();SystemClock.sleep(ms)}
    private fun capture(name:String){
        if(NativeTestEnvironment.uiOnly)return
        val image=i.uiAutomation.takeScreenshot()!!
        File(i.targetContext.getExternalFilesDir(null),"interaction-$round-$name.png").outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()
    }
    private fun live(s:ActivityScenario<MainActivity>){
        val diagnostic=NativeReadinessDiagnostics("interaction-$round-${SystemClock.uptimeMillis()}")
        s.onActivity{it.repository.clear();it.model.weather=null;it.refresh(true);diagnostic.observe(it)}
        val deadline=SystemClock.uptimeMillis()+30000;var ready=false
        while(!ready&&SystemClock.uptimeMillis()<deadline){s.onActivity{ready=it.model.weather!=null&&it.model.status=="ready"};SystemClock.sleep(100)}
        var outcome="";s.onActivity{outcome=diagnostic.save(it)}
        assertTrue("Real provider response required for interaction captures: $outcome",ready)
        s.onActivity{a->File(i.targetContext.getExternalFilesDir(null),"interaction-$round-live.json").writeText(a.repository.rawFor(a.model.place)!!)}
        pause(1000)
    }
    private class Finger(private val i:android.app.Instrumentation,val x:Float,val y:Float){
        private val start=SystemClock.uptimeMillis()
        fun send(action:Int,xx:Float=x,yy:Float=y){val e=MotionEvent.obtain(start,SystemClock.uptimeMillis(),action,xx,yy,0);i.sendPointerSync(e);e.recycle()}
        fun secondDown(yy:Float){
            val properties=Array(2){n->MotionEvent.PointerProperties().apply{id=n;toolType=MotionEvent.TOOL_TYPE_FINGER}}
            val coords=Array(2){n->MotionEvent.PointerCoords().apply{this.x=x+n*24;this.y=yy;pressure=1f;size=1f}}
            val e=MotionEvent.obtain(start,SystemClock.uptimeMillis(),MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),2,properties,coords,0,0,1f,1f,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0)
            i.sendPointerSync(e);e.recycle()
        }
    }
    @Test fun continuousDialDetentsReversalCancelAndPageOwnership(){
        seed(1);ActivityScenario.launch(MainActivity::class.java).use{s->live(s)
            var x=0f;var y=0f;var tooth=0f;var count=0
            s.onActivity{a->val b=a.pager.observationPage.dashboard;val r=Rect();b.getGlobalVisibleRect(r);val d=a.resources.displayMetrics.density;x=r.left+b.dialRect.centerX()*d;y=r.top+b.dialRect.centerY()*d;tooth=max(38f,(b.dialRect.height()*.70f*Math.toRadians(8.5)).toFloat())*d;count=b.detentFeedbackCount}
            val f=Finger(i,x,y);f.send(MotionEvent.ACTION_DOWN)
            f.send(MotionEvent.ACTION_MOVE,yy=y-tooth*.38f);pause();capture("dial-partial")
            s.onActivity{assertEquals(0,it.model.purpose);assertEquals(1,it.model.page)}
            f.send(MotionEvent.ACTION_MOVE,yy=y-tooth*.66f);pause();capture("dial-crossed")
            s.onActivity{a->assertEquals(1,a.model.purpose);assertEquals(count+1,a.pager.observationPage.dashboard.detentFeedbackCount)}
            for(step in listOf(.54f,.48f,.51f)){f.send(MotionEvent.ACTION_MOVE,yy=y-tooth*step);SystemClock.sleep(25)}
            s.onActivity{assertEquals(1,it.model.purpose)}
            f.send(MotionEvent.ACTION_MOVE,yy=y-tooth*.39f);pause();s.onActivity{assertEquals(0,it.model.purpose)}
            f.send(MotionEvent.ACTION_MOVE,yy=y-tooth*2.8f);pause();capture("dial-three")
            s.onActivity{assertEquals(3,it.model.purpose);assertEquals(1,it.model.page)}
            f.send(MotionEvent.ACTION_CANCEL,yy=y-tooth*2.8f);pause(300)
            s.onActivity{a->assertEquals(0,a.model.purpose);assertFalse(a.pager.observationPage.dashboard.dialDragging);assertFalse(a.pager.observationPage.dashboard.dialSettling)}
            capture("dial-cancelled")
            val multi=Finger(i,x,y);multi.send(MotionEvent.ACTION_DOWN);multi.send(MotionEvent.ACTION_MOVE,yy=y-tooth*1.3f);multi.secondDown(y-tooth*1.3f);multi.send(MotionEvent.ACTION_CANCEL);pause(250)
            s.onActivity{a->assertEquals(0,a.model.purpose);assertEquals(1,a.model.page);assertFalse(a.pager.observationPage.dashboard.dialDragging)}
            val g=Finger(i,x,y);g.send(MotionEvent.ACTION_DOWN)
            for(n in 1..24){g.send(MotionEvent.ACTION_MOVE,yy=y-tooth*3*n/24);SystemClock.sleep(12)}
            g.send(MotionEvent.ACTION_UP,yy=y-tooth*3);pause(400)
            s.onActivity{a->assertEquals(3,a.model.purpose);assertEquals(1,a.model.page);assertFalse((a.pager.observationPage.dashboard as ObservationDashboard).contentAnimating)}
            capture("dial-settled")
        }
    }
    @Test fun pageComponentsTransformDuringTravelAndResetAfterCancelOrCommit(){
        seed(0);ActivityScenario.launch(MainActivity::class.java).use{s->live(s)
            var x=0f;var y=0f;var width=0f
            s.onActivity{a->val p=a.pager;val loc=IntArray(2);p.getLocationOnScreen(loc);x=loc[0]+p.width*.5f;y=loc[1]+p.height*.82f;width=p.width.toFloat()}
            for(cancel in listOf(true,false)){
                val f=Finger(i,x,y);f.send(MotionEvent.ACTION_DOWN)
                for(n in 1..12){f.send(MotionEvent.ACTION_MOVE,x-width*.35f*n/12);SystemClock.sleep(16)}
                pause();s.onActivity{a->assertTrue(a.pager.weatherPage.translationX<0);assertEquals(View.LAYER_TYPE_HARDWARE,a.pager.weatherPage.dashboard.layerType)}
                capture(if(cancel)"page-cancel-travel"else "page-commit-travel")
                f.send(if(cancel)MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP,x-width*.35f);pause(400)
                s.onActivity{a->assertEquals(if(cancel)0 else 1,a.model.page);for(p in listOf(a.pager.weatherPage,a.pager.observationPage)){assertEquals(View.LAYER_TYPE_HARDWARE,p.dashboard.layerType);assertEquals(1f,p.dashboard.scaleX,0f)}}
            }
            capture("page-after-state-commit")
        }
    }
    @Test fun gpuLoopPrewarmsNextPagePausesForTouchAndResumesAfterBackground(){
        seed(0);ActivityScenario.launch(MainActivity::class.java).use{s->live(s)
            s.onActivity{it.model.motion=true;it.redraw()}
            fun count():Long{var n=0L;s.onActivity{n=it.pager.weatherPage.art.renderedFrames};return n}
            fun await(timeout:Long=15000,check:()->Boolean){val end=SystemClock.uptimeMillis()+timeout;while(SystemClock.uptimeMillis()<end){if(check())return;SystemClock.sleep(100)};assertTrue(check())}
            await{var ready=false;s.onActivity{ready=it.pager.observationPage.art.renderedFrames>0};ready}
            val first=count();await{count()>first}
            var x=0f;var y=0f;var width=0f
            s.onActivity{val loc=IntArray(2);it.pager.getLocationOnScreen(loc);width=it.pager.width.toFloat();x=loc[0]+width*.5f;y=loc[1]+it.pager.height*.82f}
            val f=Finger(i,x,y);f.send(MotionEvent.ACTION_DOWN);f.send(MotionEvent.ACTION_MOVE,x-width*.25f)
            // Allow a draw already inside eglSwapBuffers to finish, then require
            // actual quiescence while the finger still owns the transition.
            var stable=count();var lastChange=SystemClock.uptimeMillis()
            await{val n=count();if(n!=stable){stable=n;lastChange=SystemClock.uptimeMillis()};SystemClock.uptimeMillis()-lastChange>=650}
            val held=count();SystemClock.sleep(700);assertEquals(held,count())
            f.send(MotionEvent.ACTION_CANCEL,x-width*.25f)
            await{count()>held}
            s.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            SystemClock.sleep(1200);val paused=count();SystemClock.sleep(700);assertEquals(paused,count())
            s.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED);await{count()>paused}
            s.onActivity{a->assertNull(a.pager.weatherPage.art.error);assertNull(a.pager.observationPage.art.error)
                File(i.targetContext.getExternalFilesDir(null),"gpu-continuity.json").writeText(org.json.JSONObject().put("nextPagePrewarmed",true).put("touchHeldFrames",held).put("backgroundHeldFrames",paused).put("resumedFrames",a.pager.weatherPage.art.renderedFrames).toString(2))
                a.model.motion=false;a.redraw()
            }
        }
    }
    private fun find(v:View,label:String):View?{if(v is TextView&&v.text.toString()==label)return v;if(v is ViewGroup)for(n in 0 until v.childCount)find(v.getChildAt(n),label)?.let{return it};return null}
    @Test fun settingsButtonPressCancelsCleanlyAndSelectionPersists(){
        seed(0);ActivityScenario.launch(MainActivity::class.java).use{s->live(s);s.onActivity{it.settings.open()};pause(300)
            val r=Rect();lateinit var button:View
            s.onActivity{a->button=find(a.settings,"English")!!;assertTrue(button.getGlobalVisibleRect(r))}
            val f=Finger(i,r.centerX().toFloat(),r.centerY().toFloat());f.send(MotionEvent.ACTION_DOWN);pause(90)
            s.onActivity{assertTrue(button.scaleX<1f);assertEquals("zh",it.model.lang)};capture("button-pressed")
            f.send(MotionEvent.ACTION_CANCEL);pause(160);s.onActivity{assertEquals(1f,button.scaleX,0f);assertEquals("zh",it.model.lang)}
            val g=Finger(i,r.centerX().toFloat(),r.centerY().toFloat());g.send(MotionEvent.ACTION_DOWN);SystemClock.sleep(80);g.send(MotionEvent.ACTION_UP);pause(350)
            s.onActivity{assertEquals("en",it.model.lang)};capture("settings-selected")
            s.recreate();pause(300);s.onActivity{assertEquals("en",it.model.lang);assertFalse(it.pager.overlayOpen)}
        }
    }
    @Test fun reducedMotionSettlesDialContentAndButtonsImmediately(){
        i.uiAutomation.executeShellCommand("settings put global animator_duration_scale 0").close();SystemClock.sleep(500)
        try{
            assertFalse(android.animation.ValueAnimator.areAnimatorsEnabled());seed(1)
            ActivityScenario.launch(MainActivity::class.java).use{s->pause();s.onActivity{a->
                val b=a.pager.observationPage.dashboard;b.onKeyDown(android.view.KeyEvent.KEYCODE_DPAD_DOWN,android.view.KeyEvent(0,android.view.KeyEvent.KEYCODE_DPAD_DOWN))
                assertEquals(1,a.model.purpose);assertFalse(b.dialSettling);assertFalse((b as ObservationDashboard).contentAnimating);a.settings.open();assertEquals(1f,a.settings.alpha,0f)
            };pause();s.onActivity{a->val control=find(a.settings,"English")!!;val start=SystemClock.uptimeMillis();control.dispatchTouchEvent(MotionEvent.obtain(start,start,0,10f,10f,0));assertEquals(1f,control.scaleX,0f);control.dispatchTouchEvent(MotionEvent.obtain(start,start+1,3,10f,10f,0));a.settings.close()}}
        }finally{i.uiAutomation.executeShellCommand("settings put global animator_duration_scale 1").close();SystemClock.sleep(500)}
    }
}
