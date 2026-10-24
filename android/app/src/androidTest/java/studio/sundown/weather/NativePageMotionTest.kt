package studio.sundown.weather

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** Real window input: composition stays cached, entry reveals ink, interruptions release it. */
class NativePageMotionTest {
    companion object { @org.junit.BeforeClass @JvmStatic fun environment(){NativeTestEnvironment.install()} }
    private val i=InstrumentationRegistry.getInstrumentation()
    private fun await(check:()->Boolean){val end=SystemClock.uptimeMillis()+6000;while(SystemClock.uptimeMillis()<end){if(check())return;SystemClock.sleep(20)};assertTrue(check())}
    private fun seed(){i.targetContext.getSharedPreferences("baro-native",0).edit().putString("language","zh").putInt("page",0).putInt("purpose",2).putBoolean("motion",false).commit()}
    private fun event(time:Long,action:Int,x:Float,y:Float){val e=MotionEvent.obtain(time,SystemClock.uptimeMillis(),action,x,y,0);i.sendPointerSync(e);e.recycle()}
    private fun swipe(s:ActivityScenario<MainActivity>,direction:Int,release:Boolean=true):Triple<Long,Float,Float>{
        var x=0f;var y=0f;var width=0f
        s.onActivity{val loc=IntArray(2);it.pager.getLocationOnScreen(loc);width=it.pager.width.toFloat();x=loc[0]+width*.5f;y=loc[1]+it.pager.height*.82f}
        val time=SystemClock.uptimeMillis();event(time,MotionEvent.ACTION_DOWN,x,y)
        for(step in 1..10){event(time,MotionEvent.ACTION_MOVE,x+direction*width*.35f*step/10,y);SystemClock.sleep(16)}
        val end=x+direction*width*.35f
        if(release)event(time,MotionEvent.ACTION_UP,end,y)
        return Triple(time,end,y)
    }
    @Test fun bothDirectionsRevealOnlyAfterCommitAndKeepDashboardLayersWarm(){
        seed();ActivityScenario.launch(MainActivity::class.java).use{s->
            await{var ready=false;s.onActivity{ready=it.pager.observationPage.dashboard.redrawRegions.isNotEmpty()};ready}
            val sawEntry=java.util.concurrent.atomic.AtomicBoolean()
            s.onActivity{a->a.root.viewTreeObserver.addOnPreDrawListener{if(a.pager.observationPage.revealing&&a.model.page==1)sawEntry.set(true);true}}
            for(direction in listOf(-1,1)){
                sawEntry.set(false)
                val gesture=swipe(s,direction,false)
                var before=0L
                s.onActivity{a->assertEquals(0,a.model.page);assertFalse(a.pager.observationPage.revealing);before=a.pager.observationPage.dashboard.renderedDraws}
                SystemClock.sleep(180)
                s.onActivity{a->assertEquals(before,a.pager.observationPage.dashboard.renderedDraws);assertTrue(a.pager.presentationRunning)}
                event(gesture.first,MotionEvent.ACTION_UP,gesture.second,gesture.third)
                await{var landed=false;s.onActivity{landed=it.model.page==1};landed}
                assertTrue("A real pre-draw must observe the entry reveal",sawEntry.get())
                s.onActivity{a->for(page in listOf(a.pager.weatherPage,a.pager.observationPage))assertEquals(View.LAYER_TYPE_HARDWARE,page.dashboard.layerType)}
                await{var done=false;s.onActivity{done=!it.pager.presentationRunning&&!it.pager.observationPage.revealing};done}
                swipe(s,-direction)
                await{var done=false;s.onActivity{done=it.model.page==0&&!it.pager.presentationRunning};done}
            }
        }
    }
    @Test fun cancelledAndBackgroundedGesturesDoNotCommitOrRetainPresentation(){
        seed();ActivityScenario.launch(MainActivity::class.java).use{s->
            await{var ready=false;s.onActivity{ready=it.pager.width>0};ready}
            val g=swipe(s,-1,false);event(g.first,MotionEvent.ACTION_CANCEL,g.second,g.third)
            await{var done=false;s.onActivity{done=!it.pager.presentationRunning};done}
            s.onActivity{assertEquals(0,it.model.page);assertFalse(it.pager.observationPage.revealing)}
            // Suspend in the same UI turn as release: input synchronisation on
            // software GPUs can otherwise finish the settle before onPause.
            s.onActivity{a->
                val p=a.pager;val x=p.width*.5f;val y=p.height*.82f;val time=SystemClock.uptimeMillis()
                for((action,xx)in listOf(MotionEvent.ACTION_DOWN to x,MotionEvent.ACTION_MOVE to x+p.width*.35f,MotionEvent.ACTION_UP to x+p.width*.35f)){
                    val e=MotionEvent.obtain(time,time,action,xx,y,0);p.dispatchTouchEvent(e);e.recycle()
                }
                assertTrue(p.presentationRunning);p.renderingEnabled(false);assertEquals(0,a.model.page)
            }
            s.moveToState(Lifecycle.State.CREATED)
            SystemClock.sleep(900)
            s.onActivity{a->assertEquals(0,a.model.page);assertFalse(a.pager.presentationRunning);assertFalse(a.pager.observationPage.revealing);for(page in listOf(a.pager.weatherPage,a.pager.observationPage))assertEquals(View.LAYER_TYPE_NONE,page.dashboard.layerType)}
            s.moveToState(Lifecycle.State.RESUMED)
            s.onActivity{a->assertEquals(0,a.model.page);assertEquals(0f,a.pager.weatherPage.translationX,0f)}
        }
    }
}
