package studio.sundown.weather

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class NativeTransitionTest {
    companion object { @org.junit.BeforeClass @JvmStatic fun environment(){NativeTestEnvironment.install()} }
    private val i=InstrumentationRegistry.getInstrumentation()
    private fun find(root:View,label:String):View?{
        if(root is TextView&&root.text.toString()==label)return root
        if(root is ViewGroup)for(n in 0 until root.childCount)find(root.getChildAt(n),label)?.let{return it}
        return null
    }
    private fun await(check:()->Boolean){val end=SystemClock.uptimeMillis()+5000;while(SystemClock.uptimeMillis()<end){if(check())return;SystemClock.sleep(30)};assertTrue(check())}
    @Test fun repeatedLanguageClicksKeepLastSelectionAndReleaseSnapshots(){
        i.targetContext.getSharedPreferences("baro-native",0).edit().clear().putString("language","zh").putBoolean("motion",false).commit()
        ActivityScenario.launch(MainActivity::class.java).use{s->
            s.onActivity{it.settings.open()}
            await{var ready=false;s.onActivity{ready=(find(it.settings,"English")?.width?:0)>0};ready}
            s.onActivity{a->find(a.settings,"English")!!.performClick();assertEquals("en",a.model.lang);assertTrue(a.pager.weatherPage.dashboard.contentTransition.running)}
            SystemClock.sleep(80)
            s.onActivity{a->find(a.settings,"中文")!!.performClick();find(a.settings,"English")!!.performClick();assertEquals("en",a.model.lang)}
            await{var ready=false;s.onActivity{a->ready=find(a.settings,"LANGUAGE")!=null&&!a.settings.contentTransition.running&&!a.pager.weatherPage.dashboard.contentTransition.running};ready}
            s.onActivity{a->assertTrue(a.pager.overlayOpen);assertEquals("en",a.model.lang);find(a.settings,"中文")!!.performClick()}
            s.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);s.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            await{var ready=false;s.onActivity{ready=find(it.settings,"界面语言")!=null};ready}
            s.onActivity{it.settings.close()}
            await{var done=false;s.onActivity{a->done=a.settings.visibility==View.GONE&&!a.pager.overlayOpen};done}
            SystemClock.sleep(450)
            s.onActivity{a->assertEquals("zh",a.model.lang);assertEquals(View.GONE,a.settings.visibility);assertFalse(a.settings.contentTransition.running);assertFalse(a.pager.weatherPage.dashboard.contentTransition.running)}
        }
    }
    @Test fun purposePointSelectionUpdatesImmediatelyWhileInkBlendsAndPausesCleanly(){
        i.targetContext.getSharedPreferences("baro-native",0).edit().clear().putInt("page",1).putInt("purpose",0).putBoolean("motion",false).commit()
        ActivityScenario.launch(MainActivity::class.java).use{s->
            await{var ready=false;s.onActivity{ready=!it.pager.observationPage.dashboard.dialRect.isEmpty};ready}
            fun tap(offset:Int){s.onActivity{a->
                val board=a.pager.observationPage.dashboard;val r=board.dialRect;val scale=board.coordinateScale
                val x=r.centerX()*scale;val y=(r.centerY()+kotlin.math.sin(Math.toRadians(8.5*offset)).toFloat()*r.height()*.70f)*scale
                val time=SystemClock.uptimeMillis()
                for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)){val e=MotionEvent.obtain(time,time,action,x,y,0);board.dispatchTouchEvent(e);e.recycle()}
                assertTrue(board.contentTransition.running);assertFalse(board.dialDragging);assertEquals(1,a.model.page)
            }}
            tap(1);s.onActivity{assertEquals(1,it.model.purpose)};SystemClock.sleep(80);tap(1);s.onActivity{assertEquals(2,it.model.purpose)}
            await{var settled=false;s.onActivity{settled=!it.pager.observationPage.dashboard.contentTransition.running};settled}
            tap(1);s.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            s.onActivity{assertFalse(it.pager.observationPage.dashboard.contentTransition.running)}
        }
    }
}
