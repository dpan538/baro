package studio.sundown.weather

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.MotionEvent
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
    private fun capture(name:String,s:ActivityScenario<MainActivity>,page:Int=1){
        if(NativeTestEnvironment.uiOnly)return
        val deadline=SystemClock.uptimeMillis()+20000
        while(SystemClock.uptimeMillis()<deadline){
            s.onActivity{a->
                assertEquals(page,a.model.page)
                val p=if(page==0)a.pager.weatherPage else a.pager.observationPage
                assertEquals(0f,p.translationX,.1f);assertNull(p.art.error)
            }
            val image=i.uiAutomation.takeScreenshot();assertNotNull(image)
            // The weather's paper corner and the panorama at this point differ clearly.
            // Checking model.page alone accepted two stale-compositor portrait captures.
            val pixel=image.getPixel(40,200)
            val paper=android.graphics.Color.red(pixel)>245&&android.graphics.Color.green(pixel)>245
            if(paper==(page==0)){
                File(i.targetContext.getExternalFilesDir(null),"small-$name.png").outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle();return
            }
            image.recycle();SystemClock.sleep(250)
        }
        fail("The actual composed page did not match $name within 20 seconds")
    }
    @Test fun bothOrientationsStayOnOneScreenWhileDialOwnsVerticalTouches(){
        ActivityScenario.launch(MainActivity::class.java).use{s->
            s.onActivity{a->a.model.motion=false;a.model.fahrenheit=false;a.model.wind="kmh";a.model.place=Place("Brisbane",-27.47,153.03,"Australia/Brisbane");a.model.block=InstrumentationRegistry.getArguments().getString("pixels")=="block";a.model.page=1;a.model.purpose=0;a.redraw()}
            if(!NativeTestEnvironment.uiOnly)s.onActivity{it.repository.clear();it.model.weather=null;it.refresh(true)}
            var ready=false
            for(attempt in 0..59){s.onActivity{ready=it.model.weather!=null&&(NativeTestEnvironment.uiOnly||it.model.status=="ready")};if(ready)break;SystemClock.sleep(500)}
            assertTrue("This product capture requires real API data",ready)
            if(!NativeTestEnvironment.uiOnly)s.onActivity{a->
                val raw=a.repository.rawFor(a.model.place);assertNotNull(raw)
                File(i.targetContext.getExternalFilesDir(null),"small-live-response.json").writeText(raw!!)
            }
            for(portrait in listOf(false,true)){
                i.uiAutomation.executeShellCommand("wm user-rotation lock ${if(portrait)1 else 0}").close();SystemClock.sleep(1800);i.waitForIdleSync()
                val orientation=if(portrait)"portrait"else "landscape"
                for(lang in listOf("zh","en")){
                    for(purpose in 0..11){
                        s.onActivity{a->a.model.lang=lang;a.model.page=1;a.model.purpose=purpose;a.redraw()};i.waitForIdleSync();SystemClock.sleep(350)
                        s.onActivity{a->
                            val p=a.pager.observationPage;val board=p.dashboard
                            assertEquals(portrait,p.height>p.width)
                            assertEquals("The full dashboard must fit the viewport",p.height,board.height)
                            assertEquals(p.width,board.width);assertEquals(0,p.scrollY)
                            assertFalse(p.canScrollVertically(1));assertFalse(p.canScrollVertically(-1))
                            val d=board.coordinateScale;val dial=board.dialRect
                            assertTrue(dial.top*d>=0&&dial.bottom*d<=p.height)
                            assertTrue(board.artRect.bottom*d<=p.height)
                            assertEquals(1136f,if(portrait)board.w else board.w/1.5f,.01f)
                        }
                        if(purpose in listOf(0,2,11))capture("$orientation-$lang"+(if(purpose==0)""else "-purpose-$purpose"),s)
                    }
                    var x=0f;var y=0f;var endY=0f
                    s.onActivity{a->val p=a.pager.observationPage;val loc=IntArray(2);p.getLocationOnScreen(loc);x=loc[0]+p.width*.3f;y=loc[1]+p.height*.75f;endY=loc[1]+p.height*.25f}
                    drag(x,y,endY);drag(x,endY,y)
                    s.onActivity{a->assertEquals(1,a.model.page);assertEquals(11,a.model.purpose);assertEquals(0,a.pager.observationPage.scrollY)}
                    var oldPurpose=0
                    s.onActivity{a->val p=a.pager.observationPage;val d=p.dashboard.coordinateScale;val r=p.dashboard.dialRect;val loc=IntArray(2);p.getLocationOnScreen(loc);x=loc[0]+r.centerX()*d;y=loc[1]+r.centerY()*d;endY=y-450;oldPurpose=a.model.purpose}
                    drag(x,y,endY)
                    s.onActivity{a->assertEquals(1,a.model.page);assertNotEquals(oldPurpose,a.model.purpose);assertEquals(0,a.pager.observationPage.scrollY)}
                    s.onActivity{a->a.model.page=0;a.redraw()};i.waitForIdleSync();SystemClock.sleep(500);capture("weather-$orientation-$lang",s,0)
                    var distance=0f
                    s.onActivity{a->val p=a.pager;val loc=IntArray(2);p.getLocationOnScreen(loc);x=loc[0]+p.width*.35f;y=loc[1]+p.height*.82f;distance=p.width*.3f}
                    drag(x,y,y,endX=x+distance);s.onActivity{assertEquals(1,it.model.page)}
                    drag(x,y,y,endX=x-distance);s.onActivity{assertEquals(0,it.model.page)}
                }
            }
        }
    }
}
