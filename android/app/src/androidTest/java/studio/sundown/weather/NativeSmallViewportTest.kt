package studio.sundown.weather

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import java.io.File

/** Run on the dedicated emulator at the 2.5x logical portrait profile. */
class NativeSmallViewportTest {
    companion object { @org.junit.BeforeClass @JvmStatic fun environment(){NativeTestEnvironment.install()} }
    private val i=InstrumentationRegistry.getInstrumentation()
    private fun drag(x:Float,y:Float,endY:Float,endX:Float=x){
        val start=SystemClock.uptimeMillis()
        fun send(action:Int,yy:Float,xx:Float=x){val e=MotionEvent.obtain(start,SystemClock.uptimeMillis(),action,xx,yy,0);i.sendPointerSync(e);e.recycle()}
        send(MotionEvent.ACTION_DOWN,y)
        for(step in 1..16){SystemClock.sleep(12);send(MotionEvent.ACTION_MOVE,y+(endY-y)*step/16,x+(endX-x)*step/16)}
        send(MotionEvent.ACTION_UP,endY,endX);i.waitForIdleSync();SystemClock.sleep(700)
    }
    private fun capture(name:String){
        if(NativeTestEnvironment.uiOnly)return
        val image=i.uiAutomation.takeScreenshot();assertNotNull(image)
        File(i.targetContext.getExternalFilesDir(null),"small-$name.png").outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()
    }
    @Test fun readablePortraitScrollAndDialRetainSeparateGestureOwnership(){
        ActivityScenario.launch(MainActivity::class.java).use{s->
            s.onActivity{a->a.model.motion=false;a.model.block=InstrumentationRegistry.getArguments().getString("pixels")=="block";a.model.page=1;a.model.purpose=11;a.redraw()}
            if(!NativeTestEnvironment.uiOnly)s.onActivity{it.refresh(true)}
            var ready=false
            for(attempt in 0..59){s.onActivity{ready=it.model.weather!=null&&(NativeTestEnvironment.uiOnly||it.model.status=="ready")};if(ready)break;SystemClock.sleep(500)}
            assertTrue("This product capture requires real API data",ready)
            if(!NativeTestEnvironment.uiOnly)s.onActivity{a->
                val raw=File(i.targetContext.cacheDir,"weather/"+a.model.place.key+".json")
                raw.copyTo(File(i.targetContext.getExternalFilesDir(null),"small-live-response.json"),overwrite=true)
            }
            for(lang in listOf("zh","en")){
                s.onActivity{a->a.model.lang=lang;a.model.purpose=11;a.redraw();a.pager.observationPage.scrollTo(0,0)};i.waitForIdleSync();SystemClock.sleep(500)
                s.onActivity{a->val p=a.pager.observationPage;val d=a.resources.displayMetrics.density;assertTrue(p.width/d in 700f..1135f);assertTrue(p.height/d<=1550);assertTrue(p.dashboard.height>p.height)}
                capture("portrait-$lang-top")
                var x=0f;var y=0f;var endY=0f
                s.onActivity{a->val p=a.pager.observationPage;val loc=IntArray(2);p.getLocationOnScreen(loc);x=loc[0]+p.width*.3f;y=loc[1]+p.height*.78f;endY=loc[1]+p.height*.25f}
                drag(x,y,endY)
                s.onActivity{a->assertEquals(1,a.model.page);assertTrue(a.pager.observationPage.scrollY>0);a.pager.observationPage.fullScroll(View.FOCUS_DOWN)}
                i.waitForIdleSync();SystemClock.sleep(500);capture("portrait-$lang-bottom")
                var oldScroll=0;var oldPurpose=0
                s.onActivity{a->val p=a.pager.observationPage;val d=a.resources.displayMetrics.density;val r=p.dashboard.dialRect;val loc=IntArray(2);p.getLocationOnScreen(loc);x=loc[0]+r.centerX()*d;y=loc[1]+r.centerY()*d-p.scrollY;endY=y-450;assertTrue(y in 450f..(p.height.toFloat()));oldScroll=p.scrollY;oldPurpose=a.model.purpose}
                drag(x,y,endY)
                s.onActivity{a->assertEquals(1,a.model.page);assertNotEquals(oldPurpose,a.model.purpose);assertEquals(oldScroll,a.pager.observationPage.scrollY)}
            }
            var x=0f;var y=0f;var distance=0f
            s.onActivity{a->val p=a.pager;val loc=IntArray(2);p.getLocationOnScreen(loc);x=loc[0]+p.width*.35f;y=loc[1]+p.height*.4f;distance=p.width*.3f}
            drag(x,y,y,endX=x+distance);s.onActivity{assertEquals(0,it.model.page)}
            drag(x,y,y,endX=x-distance);s.onActivity{assertEquals(1,it.model.page)}
        }
    }
}
