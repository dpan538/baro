package studio.sundown.weather

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import kotlin.math.roundToInt

/** A settled model is insufficient: inspect the actually composed page pixels too. */
class NativePresentationTest {
    @Test fun committedPageActuallyReachesTheDisplay(){
        val i=InstrumentationRegistry.getInstrumentation();val ctx=i.targetContext
        ctx.getSharedPreferences("baro-native",Context.MODE_PRIVATE).edit().clear().putInt("page",0).putString("language","zh").putBoolean("motion",false).commit()
        ActivityScenario.launch(MainActivity::class.java).use{s->
            val diagnostic=NativeReadinessDiagnostics("presentation-${SystemClock.uptimeMillis()}");s.onActivity{diagnostic.observe(it)}
            val deadline=SystemClock.uptimeMillis()+30000;var ready=false
            while(!ready&&SystemClock.uptimeMillis()<deadline){s.onActivity{a->ready=a.model.weather!=null&&a.pager.weatherPage.art.renderedFrames>0};SystemClock.sleep(100)}
            var outcome="";s.onActivity{outcome=diagnostic.save(it)}
            assertTrue("Live/compositor readiness: $outcome",ready);i.waitForIdleSync();SystemClock.sleep(1000)
            var x=0f;var y=0f;var width=0f
            s.onActivity{a->assertTrue(a.pager.width>a.pager.height);val loc=IntArray(2);a.pager.getLocationOnScreen(loc);x=loc[0]+a.pager.width*.5f;y=loc[1]+a.pager.height*.82f;width=a.pager.width.toFloat()}
            val start=SystemClock.uptimeMillis()
            fun send(action:Int,xx:Float){val e=MotionEvent.obtain(start,SystemClock.uptimeMillis(),action,xx,y,0);i.sendPointerSync(e);e.recycle()}
            send(MotionEvent.ACTION_DOWN,x);for(n in 1..18){send(MotionEvent.ACTION_MOVE,x-width*.35f*n/18);SystemClock.sleep(20)};send(MotionEvent.ACTION_UP,x-width*.35f)
            val released=SystemClock.uptimeMillis();SystemClock.sleep(400)
            val anchors=mutableListOf<Triple<Int,Int,Int>>()
            s.onActivity{a->assertEquals(1,a.model.page);assertEquals(0f,a.pager.observationPage.translationX,0f)
                val b=a.pager.observationPage.dashboard;val d=a.resources.displayMetrics.density;val loc=IntArray(2);b.getLocationOnScreen(loc)
                val m=DesignMetrics(a,"observation-landscape-zh-0");m.configure(b.w,b.h,a.root.paddingTop/d)
                val wind=m.rect("ins-wind");val assembly=m.rect("ins-assembly");val wheel=m.rect("ins-wheel")
                for((xx,yy,color)in listOf(Triple(wind.left+wind.width()*.35f,wind.top+wind.height()*.55f,Color.rgb(24,24,23)),Triple(assembly.left+4,assembly.centerY(),Color.rgb(232,232,224)),Triple(wheel.centerX(),wheel.top+50,Color.rgb(14,14,13))))anchors.add(Triple(loc[0]+(xx*d).roundToInt(),loc[1]+(yy*d).roundToInt(),color))
            }
            var presented=false;var last:Bitmap?=null;val until=SystemClock.uptimeMillis()+10000
            while(!presented&&SystemClock.uptimeMillis()<until){
                last?.recycle();val image=i.uiAutomation.takeScreenshot()!!;last=image
                presented=anchors.all{(xx,yy,color)->(-2..2).sumOf{dx->(-2..2).count{dy->val p=image.getPixel(xx+dx,yy+dy);kotlin.math.abs(Color.red(p)-Color.red(color))<=3&&kotlin.math.abs(Color.green(p)-Color.green(color))<=3&&kotlin.math.abs(Color.blue(p)-Color.blue(color))<=3}}>=20}
                if(!presented)SystemClock.sleep(150)
            }
            File(ctx.getExternalFilesDir(null),"presented-page-settled.png").outputStream().use{last!!.compress(Bitmap.CompressFormat.PNG,100,it)};last?.recycle()
            File(ctx.getExternalFilesDir(null),"page-presentation.json").writeText(JSONObject().put("presented",presented).put("releaseToVerifiedCaptureMs",SystemClock.uptimeMillis()-released).put("anchors",anchors.size).put("device","dedicated software-rendered emulator").put("targetHardwarePerformanceClaim",false).toString())
            assertTrue("The real compositor must present the complete committed page",presented)
        }
    }
}
